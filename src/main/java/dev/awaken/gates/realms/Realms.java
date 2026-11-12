package dev.awaken.gates.realms;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateState;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.Set;

/** The door between today's gate records and Realms (SPEC §50.7): which gate gets one, where it is, and when it goes. */
public final class Realms {
    private Realms() {}

    /** Tests and tours: gives the next gate this blueprint however the switch is set. */
    public static String forced;

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(RealmsModule.REALMS);
    }

    public static boolean isRealm(GateRecord g) {
        return g != null && g.realm.isPresent();
    }

    public static boolean inRealm(int x, int z) {
        return RealmCells.cellAt(x, z) >= 0;
    }

    /**
     * The Leaper's Gauntlet as a place of its own (SPEC §17.10, the plan of 11 Nov): the trial's gate opens onto this Realm
     * once it is there and marked ready. Until then the trial is the old course in a Foundry floor.
     */
    public static final String LEAPER_TRIAL = "awaken:leapers_gauntlet";

    /** A blueprint for a gate being made, or empty when it should be floors. */
    public static Optional<String> choose(MinecraftServer server, GatePlan plan, boolean scripted, Set<String> seen) {
        if (level(server) != null && plan.leaperTrial() && Blueprints.get(LEAPER_TRIAL).filter(Blueprint::ready).isPresent()) return Optional.of(LEAPER_TRIAL);
        // a twist is still a floors thing until its Realm form is built (PLAN: inner Realm, sealed Mouth, events at sites)
        if (level(server) == null || scripted || plan.ritual() || plan.leaperTrial() || plan.skybornTrial() || plan.twist() != dev.awaken.gates.content.Twist.NONE) return Optional.empty();
        String f = forced;
        if (f != null) {
            forced = null;
            return Blueprints.get(f).isPresent() ? Optional.of(f) : Optional.empty();
        }
        return roll(plan, seen);
    }

    /** What a gate found in the world rolls under today's switches, once the checks above have let it be a Realm. */
    static Optional<String> roll(GatePlan plan, Set<String> seen) {
        if (!AwakenConfig.GATE_REALMS.get()) return Optional.empty();
        return Blueprints.pick(plan.theme(), plan.rank(), seen, plan.seed(), AwakenConfig.GATE_OPEN_LANDS.get());
    }

    /** Whether this gate's Realm is a land under the open sky. */
    public static boolean openLand(GateRecord g) {
        return g.realm.flatMap(r -> Blueprints.get(r.blueprint())).map(Blueprints::openLand).orElse(false);
    }

    /**
     * Whether this gate's Realm is an open land that is switched off: open lands are off and it isn't one brought back, or
     * it is but was drawn before its fix (an older plan version, SPEC §50.9), so it would be the land Euan didn't enjoy.
     */
    public static boolean openLandOff(GateRecord g) {
        if (!openLand(g) || AwakenConfig.GATE_OPEN_LANDS.get()) return false;
        RealmRef r = g.realm.orElseThrow();
        return !Blueprints.back(r.blueprint()) || r.version() < RealmPlan.OPEN_LANDS_FIXED;
    }

    /** Puts a new gate's Realm in place: a cell, and the plan where worldgen threads can read it. */
    public static void attach(MinecraftServer server, GateRecord g, String blueprint) {
        int cell = RealmData.get(server).allocate();
        g.realm = Optional.of(new RealmRef(blueprint, g.plan.seed(), planVersion(), cell));
        publish(g);
    }

    /**
     * The plan version a new gate is drawn at: the beats (SPEC §72) only with gateBeats on and the Runner's kit in the game,
     * since every beat asks for one of its moves. A gate keeps the version it was drawn at for as long as it stands.
     */
    public static int planVersion() {
        return AwakenConfig.GATE_BEATS.get() && dev.awaken.body.BodyConfig.runnersKit() ? RealmPlan.CURRENT_VERSION : RealmPlan.BEFORE_BEATS;
    }

    private static boolean publish(GateRecord g) {
        RealmRef ref = g.realm.orElseThrow();
        Optional<Blueprint> bp = Blueprints.get(ref.blueprint());
        if (bp.isEmpty()) {
            Awaken.LOG.warn("[realms] gate {} wants a Realm that isn't loaded: {}", g.id, ref.blueprint());
            return false;
        }
        RealmCells.put(ref.cell(), RealmPlan.of(ref.blueprint(), bp.get(), g.plan.rank(), ref.seed(), ref.version()), g.id);
        return true;
    }

    /** After a restart every standing Realm's plan goes back up before anything can ask for its chunks. */
    public static void onStarted(MinecraftServer server) {
        RealmCells.clear();
        RealmRun.reset();
        for (GateRecord g : GateRegistry.get(server).all()) if (g.state != GateState.RELEASED && g.realm.isPresent()) publish(g);
    }

    public static RealmPlan plan(GateRecord g) {
        return g.realm.map(r -> RealmCells.plan(r.cell())).orElse(null);
    }

    /** Where you arrive in world coordinates. */
    public static BlockPos entrance(GateRecord g) {
        RealmRef r = g.realm.orElseThrow();
        Land.Site m = RealmCells.plan(r.cell()).land().mouth();
        return world(r.cell(), m.x(), m.y(), m.z());
    }

    public static float entranceYaw(GateRecord g) {
        return RealmCells.plan(g.realm.orElseThrow().cell()).land().mouthYaw();
    }

    public static BlockPos world(int cell, int x, int y, int z) {
        return new BlockPos(x + RealmCells.centerX(cell), y, z + RealmCells.centerZ(cell));
    }

    /** The gate is gone: its plan goes, and the land is erased once nothing is loaded. */
    public static void release(MinecraftServer server, GateRecord g) {
        RealmRef r = g.realm.orElse(null);
        if (r == null) return;
        ServerLevel level = level(server);
        RealmPlan plan = RealmCells.plan(r.cell());
        int half = plan == null ? 1024 : plan.land().halfSize();
        RealmCells.remove(r.cell());
        RealmData d = RealmData.get(server);
        d.forget(g.id);
        if (level == null) {
            d.freeCell(r.cell());
            return;
        }
        int size = (half * 2 + 16) >> 4, cx = (RealmCells.centerX(r.cell()) - half) >> 4, cz = (RealmCells.centerZ(r.cell()) - half) >> 4;
        dev.awaken.core.RegionRecycler.eraseWhenUnloaded(level, dev.awaken.core.RegionRecycler.square(cx, cz, size), () -> d.freeCell(r.cell()));
    }
}
