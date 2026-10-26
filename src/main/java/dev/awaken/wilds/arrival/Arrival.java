package dev.awaken.wilds.arrival;

import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.core.api.ArrivalHooks.Way;
import dev.awaken.core.api.WardHooks;
import dev.awaken.wilds.WildsContent;
import dev.awaken.wilds.WildsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How you arrived (SPEC §17 "The Wilds"), behind {@link ArrivalHooks}. Every tick the server reads each player's
 * movement into the quarter second being filled, keeping the most telling way ({@link Way} order: building outranks
 * everything); each quarter second goes into a 20-second {@link ArrivalLog}. Movement systems add their own states
 * through {@link ArrivalHooks#report}.
 *
 * <p>Sites judge a visit, not a moment: walking into a ward ({@link WardHooks}) freezes the verdict from the 20 seconds
 * that led there, and anything built while inside spoils it until the player has left for {@link #VISIT_GRACE}. So
 * towering up, waiting 20 s and then opening the chest still reads as a tower. Outside any ward, the window itself is
 * the verdict. A visit also knows whether the Ascent's line carried the player ({@link Way#LINE}, on the way in or
 * since): what a climb site's payoff answers (SPEC §17.29). The history is short on purpose and never saved; the open
 * visit is, for {@link #VISIT_KEPT}, so a relog in a ward keeps a tower a tower ({@link Visit}).
 */
public final class Arrival implements ArrivalHooks.Tracker {
    /** Ticks per sample. */
    static final int SAMPLE = 5;
    /** Ticks between ward checks, staggered per player. */
    static final int VISIT_EVERY = 10;
    /** Out of a ward this long, a visit is over (a step back out the door doesn't reset it). */
    static final int VISIT_GRACE = 100;
    /** Under this many blocks a tick, a player on the ground is standing still. */
    static final double MOVED = 0.03;
    /** A visit saved at logout comes back with the player this long after (ten minutes), if they are still in its ward. */
    static final long VISIT_KEPT = 20 * 60 * 10;

    static final class State {
        final ArrivalLog log = new ArrivalLog();
        final PlacedBlocks placed = new PlacedBlocks();
        /** The highest way ordinal seen in the sample being filled. */
        int slot;
        long powerUntil = Long.MIN_VALUE, ascentUntil = Long.MIN_VALUE, lineUntil = Long.MIN_VALUE;
        double lastX, lastZ;
        boolean seen;
        /** A BUILT (LINE) sample went into the log since the last visit check. */
        boolean builtSince, lineSince;
        @Nullable String visit;
        boolean visitByMovement, visitLine;
        long visitSeen;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    static final Arrival INSTANCE = new Arrival();

    private Arrival() {}

    public static void init() {
        ArrivalHooks.install(INSTANCE);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        bus.addListener(BlockEvent.EntityPlaceEvent.class, Arrival::onPlace);
        bus.addListener(BreakBlockEvent.class, e -> {
            State s = e.getPlayer() == null ? null : STATES.get(e.getPlayer().getUUID());
            if (s != null && !e.isCanceled()) s.placed.remove(e.getPos().asLong());
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) keep(p);
            STATES.remove(e.getEntity().getUUID());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) resume(p, p.level().getGameTime());
        });
        bus.addListener(ServerStoppingEvent.class, e -> STATES.clear());
    }

    static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> new State());
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    private static void tick(MinecraftServer server) {
        // GameTests step their own players (Arrival.step), so mock players from other tests never fill a history
        if (server instanceof GameTestServer || !WildsModule.on()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            step(p, state(p), p.level().getGameTime());
        }
    }

    /** One tick of one player: read the way, close the sample every {@link #SAMPLE} ticks, check wards now and then. */
    static void step(ServerPlayer p, State s, long now) {
        Way w = classify(p, s, now);
        if (w.ordinal() > s.slot) s.slot = w.ordinal();
        if (Math.floorMod(now, (long) SAMPLE) == 0) {
            Way sample = ArrivalLog.WAYS[s.slot];
            s.log.push(sample);
            if (sample == Way.BUILT) s.builtSince = true;
            else if (sample == Way.LINE) s.lineSince = true;
            s.slot = 0;
        }
        if (Math.floorMod(now + p.getUUID().hashCode(), (long) VISIT_EVERY) == 0) visit(p, s, now);
    }

    static Way classify(ServerPlayer p, State s, long now) {
        double dx = p.getX() - s.lastX, dz = p.getZ() - s.lastZ;
        boolean moved = s.seen && dx * dx + dz * dz > MOVED * MOVED;
        s.lastX = p.getX();
        s.lastZ = p.getZ();
        s.seen = true;
        if (onPlaced(p, s, now)) return Way.BUILT;
        if (s.lineUntil > now) return Way.LINE;
        if (s.ascentUntil > now) return Way.ASCENT;
        if (s.powerUntil > now) return Way.POWER;
        if (p.isPassenger()) return Way.RIDE;
        if (p.onClimbable()) return Way.CLIMB;
        if (p.isInWater() || p.isSwimming()) return Way.SWIM;
        if (p.isFallFlying() || !p.onGround()) return Way.AIRBORNE;
        if (!moved) return Way.STILL;
        return p.isSprinting() ? Way.SPRINT : Way.WALK;
    }

    /** Standing on (any corner of the feet), climbing or standing in a block this player placed. Allocation-free. */
    static boolean onPlaced(ServerPlayer p, State s, long now) {
        if (s.placed.isEmpty()) return false;
        double x = p.getX(), z = p.getZ(), hw = p.getBbWidth() / 2 - 0.001;
        int feet = Mth.floor(p.getY());
        if (p.onGround()) {
            int under = Mth.floor(p.getY() - 0.2);
            for (int i = 0; i < 4; i++) {
                int bx = Mth.floor(x + ((i & 1) == 0 ? -hw : hw)), bz = Mth.floor(z + ((i & 2) == 0 ? -hw : hw));
                if (s.placed.has(BlockPos.asLong(bx, under, bz), now)) return true;
            }
        }
        // a ladder, a vine or scaffolding they put up: climbing inside the block at the feet
        return p.onClimbable() && s.placed.has(BlockPos.asLong(Mth.floor(x), feet, Mth.floor(z)), now);
    }

    /** Opens, keeps or ends the player's visit to the ward they stand in. */
    static void visit(ServerPlayer p, State s, long now) {
        var ward = WardHooks.at(p.level(), p.blockPosition());
        if (ward.isPresent()) {
            String id = ward.get().id();
            if (!id.equals(s.visit) || now - s.visitSeen > VISIT_GRACE) {
                s.visit = id;
                s.visitByMovement = !s.log.contains(Way.BUILT) && s.slot != Way.BUILT.ordinal();
                s.visitLine = s.visitByMovement && (s.log.contains(Way.LINE) || s.slot == Way.LINE.ordinal());
            } else if (s.builtSince || s.slot == Way.BUILT.ordinal()) {
                s.visitByMovement = false;
                s.visitLine = false;
            } else if (s.lineSince || s.slot == Way.LINE.ordinal()) {
                // the line carried them on from inside (a plaza walked onto, then the rope up): still by movement only
                s.visitLine = s.visitByMovement;
            }
            s.visitSeen = now;
        } else if (s.visit != null && now - s.visitSeen > VISIT_GRACE) {
            s.visit = null;
        }
        s.builtSince = false;
        s.lineSince = false;
    }

    // ---- a visit kept over a relog ---------------------------------------------------------------------------------

    /** Logging out inside a visit: it is saved with the player (a tower stays a tower over a relog). */
    static void keep(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.visit == null) {
            if (p.hasData(WildsContent.VISIT)) p.removeData(WildsContent.VISIT);
            return;
        }
        p.setData(WildsContent.VISIT, new Visit(Visit.SCHEMA, s.visit, s.visitByMovement, s.visitLine, p.level().getGameTime()));
    }

    /** Logging in: the saved visit comes back if it is under {@link #VISIT_KEPT} old and they stand in its ward still. */
    static void resume(ServerPlayer p, long now) {
        Visit v = p.getExistingDataOrNull(WildsContent.VISIT);
        if (v == null) return;
        p.removeData(WildsContent.VISIT);
        if (now - v.at() > VISIT_KEPT || now < v.at()) return;
        var ward = WardHooks.at(p.level(), p.blockPosition());
        if (ward.isEmpty() || !ward.get().id().equals(v.ward())) return;
        State s = state(p);
        s.visit = v.ward();
        s.visitByMovement = v.byMovement();
        s.visitLine = v.byLine();
        s.visitSeen = now;
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        state(p).placed.add(e.getPos().asLong(), p.level().getGameTime());
    }

    // ---- ArrivalHooks.Tracker -------------------------------------------------------------------------------------

    @Override
    public void report(ServerPlayer p, Way way, int ticks) {
        State s = state(p);
        long until = p.level().getGameTime() + Math.max(1, ticks);
        switch (way) {
            case POWER -> s.powerUntil = Math.max(s.powerUntil, until);
            case ASCENT -> s.ascentUntil = Math.max(s.ascentUntil, until);
            case LINE -> s.lineUntil = Math.max(s.lineUntil, until);
            default -> {
                if (way.ordinal() > s.slot) s.slot = way.ordinal();
            }
        }
    }

    @Override
    public boolean byMovement(ServerPlayer p) {
        return !WildsModule.on() || verdict(p, p.level().getGameTime());
    }

    /** The open visit's verdict, else the window's: nothing built in the last 20 s. No record at all: nothing built. */
    static boolean verdict(ServerPlayer p, long now) {
        State s = STATES.get(p.getUUID());
        if (s == null) return true;
        visit(p, s, now);
        if (s.visit != null) return s.visitByMovement;
        return !s.log.contains(Way.BUILT) && s.slot != Way.BUILT.ordinal();
    }

    @Override
    public boolean byLine(ServerPlayer p) {
        return !WildsModule.on() || lineVerdict(p, p.level().getGameTime());
    }

    /** The open visit's line, else the window's: the line in it and nothing built. No record at all: no line. */
    static boolean lineVerdict(ServerPlayer p, long now) {
        State s = STATES.get(p.getUUID());
        if (s == null) return false;
        visit(p, s, now);
        if (s.visit != null) return s.visitByMovement && s.visitLine;
        return !s.log.contains(Way.BUILT) && s.slot != Way.BUILT.ordinal() && (s.log.contains(Way.LINE) || s.slot == Way.LINE.ordinal());
    }

    @Override
    public List<Way> history(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? List.of() : s.log.toList();
    }

    /** For commands: the open visit, if any ("ruin:3,-7 by movement"). */
    public static @Nullable String describeVisit(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.visit == null) return null;
        return s.visit + (s.visitByMovement ? s.visitLine ? " by the line" : " by movement" : " built");
    }
}
