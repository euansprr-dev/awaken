package dev.awaken.binoculars;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.Spotting;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spotting (SPEC §39.10): with the binoculars up, the centre of the view held on something the player hasn't found for
 * {@link #HOLD} ticks finds it, through its owner's discovery path ({@link Spotting}). The server decides: every
 * {@link #PERIOD} ticks it casts the player's own look (their eyes and view on the server) against what the sources
 * offer, each thing's bounds widened by {@link #SLACK_DEG} degrees at its distance, takes the nearest along the look,
 * and checks a clear line to it through loaded blocks (an unloaded chunk hides it). One missed look is forgiven (a
 * leaf, a sway); a second, or another thing, starts over. The client hears only when a hold starts, ends or finds
 * ({@code awaken:binoculars_spot}) and draws the glass's ring from that. No allocation per look beyond a source's own;
 * standing things are gathered about once a second.
 */
public final class Spotter {
    private Spotter() {}

    public static final int PERIOD = 4;
    /** Ticks the centre must stay on it (2 s). */
    public static final int HOLD = 40;
    /** Looks in a row it may slip off without starting over. */
    public static final int GRACE = 1;
    /** Standing things are gathered this often (ticks). */
    public static final int STANDING_EVERY = 20;
    /** Slack round a thing's own bounds that still counts as the centre on it. */
    public static final double SLACK_DEG = 0.6, SLACK_TAN = Math.tan(Math.toRadians(SLACK_DEG));
    /** The reach: the server's loaded land (view distance), held between these (blocks). */
    public static final double REACH_MIN = 64, REACH_MAX = 512;
    static final int MAX = 64;

    /** What the client is told. */
    public enum Phase { LOST, HOLD, FOUND }

    public enum Result { NOTHING, STARTED, HOLDING, SLIPPED, LOST, FOUND }

    static final class Candidate {
        Spotting.Source source;
        long key;
        double x0, y0, z0, x1, y1, z1;
        Entity entity;
        Object ref;

        /** Holds nothing alive past the hold (an entity, a gate's view). */
        void wipe() {
            source = null;
            entity = null;
            ref = null;
        }
    }

    static final class Watch implements Spotting.Sink {
        final Candidate[] standing = new Candidate[MAX], moving = new Candidate[MAX];
        int nStanding, nMoving;
        int standingAt = Integer.MIN_VALUE;
        Spotting.Source current;
        boolean intoMoving;
        // the hold
        boolean holding;
        Spotting.Source holdSource;
        long holdKey;
        int holdStart, misses;

        Watch() {
            for (int i = 0; i < MAX; i++) {
                standing[i] = new Candidate();
                moving[i] = new Candidate();
            }
        }

        private Candidate next() {
            if (intoMoving) return nMoving < MAX ? moving[nMoving++] : null;
            return nStanding < MAX ? standing[nStanding++] : null;
        }

        @Override
        public void place(long key, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Object ref) {
            Candidate c = next();
            if (c == null) return;
            c.source = current;
            c.key = key;
            c.x0 = minX;
            c.y0 = minY;
            c.z0 = minZ;
            c.x1 = maxX;
            c.y1 = maxY;
            c.z1 = maxZ;
            c.entity = null;
            c.ref = ref;
        }

        @Override
        public void entity(Entity e, Object ref) {
            Candidate c = next();
            if (c == null) return;
            c.source = current;
            c.key = e.getId();
            c.entity = e;
            c.ref = ref;
        }

        void clear() {
            for (int i = 0; i < MAX; i++) {
                standing[i].wipe();
                moving[i].wipe();
            }
            nStanding = nMoving = 0;
            standingAt = Integer.MIN_VALUE;
            holding = false;
            holdSource = null;
        }
    }

    private static final Map<UUID, Watch> WATCH = new HashMap<>();

    /** Every server tick: one look per player whose binoculars are up, every {@link #PERIOD} ticks. */
    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % PERIOD != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (BinocularsRelay.looking(p)) look(p, now, Spotting.sources());
            else drop(p);
        }
    }

    /** The binoculars came down (or the player left): any hold ends. */
    static void drop(ServerPlayer p) {
        Watch w = WATCH.get(p.getUUID());
        if (w == null) return;
        boolean was = w.holding;
        w.clear();
        if (was) send(p, Phase.LOST);
    }

    static void forget(ServerPlayer p) {
        WATCH.remove(p.getUUID());
    }

    /** One look for one player at server tick {@code now}, against {@code sources} (tests pass their own). */
    public static Result look(ServerPlayer p, int now, List<Spotting.Source> sources) {
        Watch w = WATCH.computeIfAbsent(p.getUUID(), u -> new Watch());
        ServerLevel level = p.level();
        if (p.isSpectator() || !p.isAlive() || !Progression.profile(p).awakened() || CityHooks.inCity(level, p.blockPosition())) {
            return lose(p, w);
        }
        double reach = reach(level.getServer());
        if (w.standingAt == Integer.MIN_VALUE || now - w.standingAt >= STANDING_EVERY || now < w.standingAt) {
            w.nStanding = 0;
            w.intoMoving = false;
            for (int i = 0, n = sources.size(); i < n; i++) {
                Spotting.Source s = sources.get(i);
                if (s.moves()) continue;
                w.current = s;
                s.gather(p, reach, w);
            }
            w.standingAt = now;
        }
        w.nMoving = 0;
        w.intoMoving = true;
        for (int i = 0, n = sources.size(); i < n; i++) {
            Spotting.Source s = sources.get(i);
            if (!s.moves()) continue;
            w.current = s;
            s.gather(p, reach, w);
        }
        w.current = null;

        Vec3 eye = p.getEyePosition();
        Vec3 dir = p.getViewVector(1.0F);
        Candidate best = null;
        double bestT = Double.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            Candidate[] list = pass == 0 ? w.standing : w.moving;
            int n = pass == 0 ? w.nStanding : w.nMoving;
            for (int i = 0; i < n; i++) {
                Candidate c = list[i];
                if (c.entity != null) {
                    if (!c.entity.isAlive() || c.entity.level() != level) continue;
                    var b = c.entity.getBoundingBox();
                    c.x0 = b.minX;
                    c.y0 = b.minY;
                    c.z0 = b.minZ;
                    c.x1 = b.maxX;
                    c.y1 = b.maxY;
                    c.z1 = b.maxZ;
                }
                double cx = (c.x0 + c.x1) * 0.5 - eye.x, cy = (c.y0 + c.y1) * 0.5 - eye.y, cz = (c.z0 + c.z1) * 0.5 - eye.z;
                double d = Math.sqrt(cx * cx + cy * cy + cz * cz);
                if (d > reach) continue;
                double slack = d * SLACK_TAN;
                double t = enter(eye, dir, c.x0 - slack, c.y0 - slack, c.z0 - slack, c.x1 + slack, c.y1 + slack, c.z1 + slack, reach);
                if (t < bestT) {
                    bestT = t;
                    best = c;
                }
            }
        }
        if (best != null && !visible(level, eye, dir, best)) best = null;
        if (best != null && CityHooks.inCity(level, BlockPos.containing((best.x0 + best.x1) * 0.5, best.y0, (best.z0 + best.z1) * 0.5))) best = null;

        if (best == null) {
            if (!w.holding) return Result.NOTHING;
            if (++w.misses <= GRACE) return Result.SLIPPED;
            return lose(p, w);
        }
        if (!w.holding || w.holdSource != best.source || w.holdKey != best.key) {
            w.holding = true;
            w.holdSource = best.source;
            w.holdKey = best.key;
            w.holdStart = now;
            w.misses = 0;
            send(p, Phase.HOLD);
            return Result.STARTED;
        }
        w.misses = 0;
        if (now - w.holdStart < HOLD) return Result.HOLDING;
        boolean found = best.source.find(p, best.ref);
        w.holding = false;
        w.holdSource = null;
        // what was found is no longer offered: gather the standing things again at the next look
        w.standingAt = Integer.MIN_VALUE;
        send(p, found ? Phase.FOUND : Phase.LOST);
        return found ? Result.FOUND : Result.LOST;
    }

    private static Result lose(ServerPlayer p, Watch w) {
        if (!w.holding) return Result.NOTHING;
        w.holding = false;
        w.holdSource = null;
        send(p, Phase.LOST);
        return Result.LOST;
    }

    static double reach(MinecraftServer server) {
        return Math.max(REACH_MIN, Math.min(REACH_MAX, server.getPlayerList().getViewDistance() * 16.0));
    }

    /** Where the look enters the box (0 inside it), or MAX_VALUE if it misses it or enters past {@code max}. */
    static double enter(Vec3 o, Vec3 d, double x0, double y0, double z0, double x1, double y1, double z1, double max) {
        double lo = 0, hi = max;
        if (Math.abs(d.x) < 1e-9) {
            if (o.x < x0 || o.x > x1) return Double.MAX_VALUE;
        } else {
            double a = (x0 - o.x) / d.x, b = (x1 - o.x) / d.x;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        if (Math.abs(d.y) < 1e-9) {
            if (o.y < y0 || o.y > y1) return Double.MAX_VALUE;
        } else {
            double a = (y0 - o.y) / d.y, b = (y1 - o.y) / d.y;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        if (Math.abs(d.z) < 1e-9) {
            if (o.z < z0 || o.z > z1) return Double.MAX_VALUE;
        } else {
            double a = (z0 - o.z) / d.z, b = (z1 - o.z) / d.z;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        return lo <= hi ? lo : Double.MAX_VALUE;
    }

    // ---- a clear line --------------------------------------------------------------------------------------------------

    private static final class Sight {
        ServerLevel level;
        Vec3 from, to;
        double x0, y0, z0, x1, y1, z1;
        LevelChunk chunk;
        int cx = Integer.MIN_VALUE, cz;
    }

    private static final Sight SIGHT = new Sight();

    /**
     * A clear line from the eyes to it through loaded blocks, by what you see (glass and water hide nothing, leaves do).
     * Aimed into its bounds where the look meets them, else at their middle; a block inside its bounds is the thing
     * itself, so the line got there.
     */
    static boolean visible(ServerLevel level, Vec3 eye, Vec3 dir, Candidate c) {
        double t = enter(eye, dir, c.x0, c.y0, c.z0, c.x1, c.y1, c.z1, REACH_MAX * 2);
        Vec3 to = t == Double.MAX_VALUE ? new Vec3((c.x0 + c.x1) * 0.5, (c.y0 + c.y1) * 0.5, (c.z0 + c.z1) * 0.5)
                : eye.add(dir.x * (t + 0.05), dir.y * (t + 0.05), dir.z * (t + 0.05));
        Sight s = SIGHT;
        s.level = level;
        s.from = eye;
        s.to = to;
        s.x0 = c.x0 - 0.5;
        s.y0 = c.y0 - 0.5;
        s.z0 = c.z0 - 0.5;
        s.x1 = c.x1 + 0.5;
        s.y1 = c.y1 + 0.5;
        s.z1 = c.z1 + 0.5;
        s.chunk = null;
        s.cx = Integer.MIN_VALUE;
        try {
            return BlockGetter.traverseBlocks(eye, to, s, Spotter::block, x -> Boolean.TRUE);
        } finally {
            s.level = null;
            s.chunk = null;
        }
    }

    private static Boolean block(Sight s, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (cx != s.cx || cz != s.cz) {
            s.cx = cx;
            s.cz = cz;
            s.chunk = s.level.getChunkSource().getChunkNow(cx, cz);
        }
        if (s.chunk == null) return Boolean.FALSE; // past the loaded land: can't be seen from here
        BlockState state = s.chunk.getBlockState(pos);
        if (state.isAir()) return null;
        VoxelShape shape = state.getVisualShape(s.level, pos, CollisionContext.empty());
        if (shape.isEmpty() || shape.clip(s.from, s.to, pos) == null) return null;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        return x >= s.x0 && x <= s.x1 && y >= s.y0 && y <= s.y1 && z >= s.z0 && z <= s.z1;
    }

    // ---- telling the client ---------------------------------------------------------------------------------------------

    static void send(ServerPlayer p, Phase phase) {
        if (p.connection != null && p.connection.hasChannel(BinocularsNet.Spot.TYPE)) {
            PacketDistributor.sendToPlayer(p, new BinocularsNet.Spot(phase.ordinal(), HOLD));
        }
    }

    static void clear() {
        WATCH.clear();
    }
}
