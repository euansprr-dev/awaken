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

    /** A blueprint for a gate being made, or empty when it should be floors. */
    public static Optional<String> choose(MinecraftServer server, GatePlan plan, boolean scripted, Set<String> seen) {
        // a twist is still a floors thing until its Realm form is built (PLAN: inner Realm, sealed Mouth, events at sites)
        if (level(server) == null || scripted || plan.ritual() || plan.leaperTrial() || plan.skybornTrial() || plan.twist() != dev.awaken.gates.content.Twist.NONE) return Optional.empty();
        String f = forced;
        if (f != null) {
            forced = null;
            return Blueprints.get(f).isPresent() ? Optional.of(f) : Optional.empty();
        }
        if (!AwakenConfig.GATE_REALMS.get()) return Optional.empty();
        return Blueprints.pick(plan.theme(), plan.rank(), seen, plan.seed());
    }

    /** Puts a new gate's Realm in place: a cell, and the plan where worldgen threads can read it. */
    public static void attach(MinecraftServer server, GateRecord g, String blueprint) {
        int cell = RealmData.get(server).allocate();
        g.realm = Optional.of(new RealmRef(blueprint, g.plan.seed(), RealmPlan.CURRENT_VERSION, cell));
        publish(g);
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
