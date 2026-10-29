package dev.awaken.living.population;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The signs herds and packs leave (SPEC §41.3): prints along their real path, droppings where a herd bedded, bones where a
 * pack fed. Laid live by the brains as the bodies move, and for a population without bodies back along its {@link
 * Ledger#where} path near players (a herd you haven't met yet still left its trail this morning), so a trail of prints
 * leads to where the herd is now. World state, the same for everyone, not saved: prints fade in two in-game hours, the rest
 * in a day, and the back-filled ones are laid again from the clock after a restart.
 */
public final class Spoor {
    public enum Kind { PRINTS, DUNG, KILL, BLOOD }

    /**
     * One sign: where (y the ground), the way the animals went (prints; Minecraft yaw), what left it and how many, its
     * population, when it was laid (game time), hooves or paws, and for a kill what was killed.
     */
    public record Mark(int id, Kind kind, double x, double y, double z, float yaw, String species, int pop, int count, long laid,
                       boolean hoofed, String prey, long key) {
        public long age(long now) {
            return now - laid;
        }
    }

    /** How long a sign is seen: prints two in-game hours (six with the Tracks perk), a wounded quarry's blood three, droppings and bones a day. */
    public static final int PRINTS_LIFE = 2400, TRACKS_LIFE = 7200, BLOOD_LIFE = 3600, DUNG_LIFE = 24000, KILL_LIFE = 24000;
    /** Signs kept in one world at most (the oldest go first). */
    static final int CAP = 1024;
    /** A moving herd leaves a patch of prints every this many blocks. */
    static final double PRINT_EVERY = 6;
    /** Back-filled prints: one patch per 400 ticks of a population's path, this far back, within this of a player. */
    static final int BACK_STEP = 400, BACK_STEPS = 6, BACK_REACH = 80;

    /** Animals that leave a cloven or round hoof; everything else a paw. */
    static final Set<String> HOOFED = Set.of("minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:horse", "minecraft:donkey",
            "minecraft:goat", "mossback", "frost_stag", "crag_ram");

    private static final Map<ServerLevel, Spoor> BY_LEVEL = new WeakHashMap<>();

    private final List<Mark> marks = new ArrayList<>();
    private final Set<Long> keys = new HashSet<>();
    private int nextId = 1;

    public static Spoor of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, k -> new Spoor());
    }

    public static boolean hoofed(String species) {
        return HOOFED.contains(species);
    }

    public static int life(Kind kind, boolean tracks) {
        return switch (kind) {
            case PRINTS -> tracks ? TRACKS_LIFE : PRINTS_LIFE;
            case DUNG -> DUNG_LIFE;
            case KILL -> KILL_LIFE;
            case BLOOD -> BLOOD_LIFE;
        };
    }

    public List<Mark> all() {
        return marks;
    }

    public List<Mark> near(double x, double z, double r) {
        List<Mark> out = new ArrayList<>();
        for (Mark m : marks) if (Math.abs(m.x - x) <= r && Math.abs(m.z - z) <= r && (m.x - x) * (m.x - x) + (m.z - z) * (m.z - z) <= r * r) out.add(m);
        return out;
    }

    public Mark find(int id) {
        for (Mark m : marks) if (m.id == id) return m;
        return null;
    }

    /** Lays a sign; a keyed one (back-filled) only once. Returns it, or null when its key was laid already. */
    Mark lay(Kind kind, double x, double y, double z, float yaw, Ledger.Pop pop, int count, long laid, String prey, long key) {
        return lay(kind, x, y, z, yaw, pop.species, pop.id, count, laid, prey, key);
    }

    /** A sign left by anything (a hunted beast's blood, a hunter's kill); {@code pop} -1 for none of the ledger's. */
    public Mark lay(Kind kind, double x, double y, double z, float yaw, String species, int pop, int count, long laid, String prey, long key) {
        if (key != 0 && !keys.add(key)) return null;
        Mark m = new Mark(nextId++, kind, x, y, z, yaw, species, pop, count, laid, hoofed(species), prey, key);
        marks.add(m);
        if (marks.size() > CAP) forget(marks.removeFirst());
        return m;
    }

    private void forget(Mark m) {
        if (m.key != 0) keys.remove(m.key);
    }

    /** Signs past the longest anyone sees them go. */
    void expire(long now) {
        Iterator<Mark> it = marks.iterator();
        while (it.hasNext()) {
            Mark m = it.next();
            if (m.age(now) > life(m.kind, true)) {
                forget(m);
                it.remove();
            }
        }
    }

    /** Tests: a clean slate. */
    void clear() {
        marks.clear();
        keys.clear();
    }

    /** Minecraft's yaw for travel along (dx, dz): the way a print points. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** The ground at (x, z) if its chunk is loaded and it's dry land, else NaN. */
    static double ground(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (level.getChunkSource().getChunkNow(bx >> 4, bz >> 4) == null) return Double.NaN;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        var at = new net.minecraft.core.BlockPos(bx, y, bz);
        if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) return Double.NaN;
        return y;
    }

    // ---- laid live ---------------------------------------------------------------------------------------------------

    /**
     * A brain's second: once its bodies' middle has moved {@link #PRINT_EVERY} blocks on, a patch of prints under the body
     * nearest the middle, pointing the way they went. {@code last} is the brain's own (x, z) of its last patch.
     */
    static void step(ServerLevel level, Ledger.Pop pop, List<? extends net.minecraft.world.entity.Mob> bodies, double mx, double mz, double[] last) {
        if (!Double.isNaN(last[0]) && Math.hypot(mx - last[0], mz - last[1]) < PRINT_EVERY) return;
        boolean first = Double.isNaN(last[0]);
        float yaw = first ? 0 : yaw(mx - last[0], mz - last[1]);
        last[0] = mx;
        last[1] = mz;
        if (first) return; // no way yet
        var at = middle(bodies, mx, mz);
        if (at != null) of(level).lay(Kind.PRINTS, at.getX(), at.getY(), at.getZ(), yaw, pop, bodies.size(), level.getGameTime(), "", 0);
    }

    /** A herd bedded down: its droppings and the flattened ground, once a night, under the body nearest its middle. */
    static void bed(ServerLevel level, Ledger.Pop pop, List<? extends net.minecraft.world.entity.Mob> bodies, double mx, double mz) {
        var at = middle(bodies, mx, mz);
        if (at != null) of(level).lay(Kind.DUNG, mx, at.getY(), mz, 0, pop, bodies.size(), level.getGameTime(), "", 0);
    }

    private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable Mob middle(List<? extends net.minecraft.world.entity.Mob> bodies, double mx, double mz) {
        net.minecraft.world.entity.Mob best = null;
        double bd = Double.MAX_VALUE;
        for (var m : bodies) {
            if (!m.onGround() || m.isInWater()) continue;
            double d = Math.hypot(m.getX() - mx, m.getZ() - mz);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    // ---- back-fill --------------------------------------------------------------------------------------------------

    /**
     * Every 2 s with the ledger's tick: each population near a player laid prints along its path over the last two hours,
     * a herd its droppings at last night's bed, a pack the bones of its last kill; laid now where a player is near enough
     * to find them, keyed so each is laid once. A population with bodies lays its own from when they came.
     */
    void backfill(ServerLevel level, Ledger l, long day, long tod, long now) {
        long abs = day * 24000L + Math.floorMod(tod, 24000L);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            for (Ledger.Pop pop : l.popsNear(p.getBlockX(), p.getBlockZ(), 1)) {
                if (pop.count <= 0 || pop.kind.equals("roost")) continue;
                Long since = l.since(pop.id);
                long b0 = Math.floorDiv(abs, BACK_STEP);
                for (int k = 1; k <= BACK_STEPS; k++) {
                    long t = (b0 - k) * BACK_STEP;
                    if (since != null && t >= since) continue;
                    double[] at = whereAt(l, pop, t), was = whereAt(l, pop, t - BACK_STEP);
                    if (!near(p, at)) continue;
                    double y = ground(level, at[0], at[1]);
                    if (Double.isNaN(y)) continue;
                    float yaw = Math.hypot(at[0] - was[0], at[1] - was[1]) > 0.5 ? yaw(at[0] - was[0], at[1] - was[1]) : (float) Math.floorMod(pop.seed + t, 360L);
                    lay(Kind.PRINTS, at[0], y, at[1], yaw, pop, pop.count, now - (abs - t), "", key(pop.id, Kind.PRINTS, b0 - k));
                }
                if (pop.kind.equals("herd")) {
                    long night = abs >= day * 24000L + 18000 ? day : day - 1, t = night * 24000L + 18000;
                    if (since != null && t >= since || abs - t > DUNG_LIFE) continue;
                    double[] at = whereAt(l, pop, t);
                    if (!near(p, at)) continue;
                    double y = ground(level, at[0], at[1]);
                    if (!Double.isNaN(y)) lay(Kind.DUNG, at[0], y, at[1], 0, pop, pop.count, now - (abs - t), "", key(pop.id, Kind.DUNG, night));
                } else if (pop.herd >= 0 && l.pop(pop.herd) != null) {
                    Ledger.Pop herd = l.pop(pop.herd);
                    long hunt = pop.species.equals(Herds.WOLF) ? 8000 : 18000;
                    long d = abs >= day * 24000L + hunt ? day : day - 1, t = d * 24000L + hunt;
                    if (since != null && t >= since || abs - t > KILL_LIFE) continue;
                    double[] at = whereAt(l, herd, t);
                    if (!near(p, at)) continue;
                    double y = ground(level, at[0], at[1]);
                    if (!Double.isNaN(y)) lay(Kind.KILL, at[0], y, at[1], 0, pop, pop.count, now - (abs - t), herd.species, key(pop.id, Kind.KILL, d));
                }
            }
        }
    }

    private static double[] whereAt(Ledger l, Ledger.Pop pop, long abs) {
        return l.where(pop, Math.floorDiv(abs, 24000L), Math.floorMod(abs, 24000L));
    }

    private static boolean near(ServerPlayer p, double[] at) {
        double dx = at[0] - p.getX(), dz = at[1] - p.getZ();
        return dx * dx + dz * dz <= (double) BACK_REACH * BACK_REACH;
    }

    static long key(int pop, Kind kind, long bucket) {
        return ((long) (pop + 1) << 32) | ((long) kind.ordinal() << 28) | (bucket & 0x0FFFFFFFL);
    }
}
