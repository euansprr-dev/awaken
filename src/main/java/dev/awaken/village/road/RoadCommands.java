package dev.awaken.village.road;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code /awaken village road ...} (game masters, tours; SPEC §28.9):
 * <pre>
 * info            the road nearest you: its ends, its class, its length, its bridges and waymarkers
 * plan [radius]   lays the roads within a radius of you now (they're laid as the land generates otherwise)
 * camp            stands you on the road north out of the Wayfarer camp, looking up it
 * walk &lt;points&gt;   moves you that many points (four blocks each) along the nearest road, toward its far end
 * marker          stands you before the nearest waymarker, reading it
 * </pre>
 * and the framings {@code /awaken village view} uses for towns, mining towns and wayhouses.
 */
public final class RoadCommands {
    private RoadCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("road")
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("plan").executes(c -> plan(c.getSource(), 900))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 4000)).executes(c -> plan(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("camp").executes(c -> camp(c.getSource())))
                .then(Commands.literal("walk").then(Commands.argument("points", IntegerArgumentType.integer(-2000, 2000))
                        .executes(c -> walk(c.getSource(), IntegerArgumentType.getInteger(c, "points")))))
                .then(Commands.literal("marker").executes(c -> marker(c.getSource())))
                .then(Commands.literal("watch").executes(c -> watch(c.getSource())));
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var n = RoadNet.nearest(p.getX(), p.getZ(), 96);
        if (n.isEmpty()) {
            src.sendFailure(Component.literal("No road laid within 96 of you (" + RoadNet.stats() + ")."));
            return 0;
        }
        Road r = n.get().road();
        String msg = String.format(Locale.ROOT, "%s %s: %s to %s, %d blocks, %d points (you're at %d), %d bridged, %d waymarkers | %s",
                r.cls() == Road.HIGHROAD ? "highroad" : "lane", r.key(), r.from().name(), r.to().name(), (int) r.between(0, r.points() - 1), r.points(),
                n.get().point(), Highroads.bridged(r.flags()), r.markers().size(), RoadNet.stats());
        src.sendSuccess(() -> Component.literal(msg), false);
        Awaken.LOG.info("[road] info {}", msg);
        return 1;
    }

    private static int plan(CommandSourceStack src, int radius) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        RoadWorld w = RoadWorld.get();
        if (w == null) {
            src.sendFailure(Component.literal("No roads in this world (it was made without the Awakened Villages pack)."));
            return 0;
        }
        long t0 = System.nanoTime();
        int n = 0;
        for (int x = p.getBlockX() - radius; x <= p.getBlockX() + radius; x += 256)
            for (int z = p.getBlockZ() - radius; z <= p.getBlockZ() + radius; z += 256) n += RoadNet.roadsNear(w, x, z, 200).size();
        int laid = RoadNet.laid().size();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        src.sendSuccess(() -> Component.literal(laid + " roads laid near you (" + ms + " ms) | " + RoadNet.stats()), false);
        Awaken.LOG.info("[road] planned within {} of {}: {} roads in {} ms (seen {})", radius, p.blockPosition().toShortString(), laid, ms, n);
        return 1;
    }

    /**
     * On the road north out of the camp (Carillon's north port), at its sixth point, looking up it; where the land north
     * gave it no way, Carillon's road east or south, else the road nearest the player.
     */
    private static int camp(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        RoadWorld w = RoadWorld.get();
        if (w == null) return 0;
        for (int port = 0; port < 3; port++) {
            List<Road> rs = RoadNet.port(w, port);
            if (!rs.isEmpty()) return stand(src, p, rs.getFirst(), 6, 1);
        }
        RoadNet.highroadsNear(w, p.getBlockX(), p.getBlockZ(), 400);
        var n = RoadNet.nearest(p.getX(), p.getZ(), 1200);
        if (n.isEmpty()) {
            src.sendFailure(Component.literal("No road out of Carillon or near you in this world."));
            return 0;
        }
        return stand(src, p, n.get().road(), Math.min(6, n.get().road().points() - 1), 1);
    }

    /** Stands you a dozen blocks ahead of the nearest caravan on its road, looking back at it coming. */
    private static int watch(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.world.entity.Entity best = null;
        double bd = 400 * 400;
        for (net.minecraft.world.entity.Entity e : ((ServerLevel) p.level()).getAllEntities()) {
            if (!(e instanceof net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader) || e.getCustomName() == null
                    || !e.getCustomName().getString().equals("Caravan Master")) continue;
            double d = e.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            src.sendFailure(Component.literal("No caravan within 400 of you."));
            return 0;
        }
        var n = RoadNet.nearest(best.getX(), best.getZ(), 24);
        double ex, ez, ey;
        if (n.isPresent()) {
            Road r = n.get().road();
            // ahead of it: the way it's walking is the way its nav is pointed; else the road's own way
            var vel = best.getDeltaMovement();
            int dir = r.points() > 1 && vel.horizontalDistanceSqr() > 1e-6 ? (int) Math.signum((r.x()[Math.min(r.points() - 1, n.get().point() + 1)] - r.x()[n.get().point()]) * vel.x
                    + (r.z()[Math.min(r.points() - 1, n.get().point() + 1)] - r.z()[n.get().point()]) * vel.z) : 1;
            int i = Math.clamp(n.get().point() + (dir == 0 ? 1 : dir) * 4, 0, r.points() - 1);
            ex = r.x()[i] + 2.5;
            ez = r.z()[i] + 0.5;
            ey = r.y()[i] + 2.6;
        } else {
            ex = best.getX() + 10;
            ez = best.getZ();
            ey = best.getY() + 2.6;
        }
        double dx = best.getX() - ex, dz = best.getZ() - ez, dy = best.getY() + 1 - ey;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.teleportTo((ServerLevel) p.level(), ex, ey - 1.62, ez, Set.of(), yaw, pitch, true);
        src.sendSuccess(() -> Component.literal("Watching the caravan"), false);
        return 1;
    }

    private static int walk(CommandSourceStack src, int points) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var n = RoadNet.nearest(p.getX(), p.getZ(), 96);
        if (n.isEmpty()) {
            src.sendFailure(Component.literal("No road near you."));
            return 0;
        }
        Road r = n.get().road();
        int i = Math.clamp(n.get().point() + points, 0, r.points() - 1);
        return stand(src, p, r, i, points >= 0 ? 1 : -1);
    }

    /**
     * Stands a player on a road's point looking a dozen points along it in {@code dir}: at standing height, or (one who
     * may fly) held two blocks over it, so the verge's grass doesn't fill the view.
     */
    static int stand(CommandSourceStack src, ServerPlayer p, Road r, int i, int dir) {
        int j = Math.clamp(i + dir * 12, 0, r.points() - 1);
        boolean fly = p.getAbilities().mayfly && !p.isSpectator();
        if (fly) {
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        }
        double x = r.x()[i], z = r.z()[i], y = r.y()[i] + 1 + (fly ? 2 : 0);
        double dx = r.x()[j] - x, dz = r.z()[j] - z, dy = r.y()[j] - r.y()[i];
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1, Math.hypot(dx, dz))));
        p.teleportTo((ServerLevel) p.level(), x, y, z, Set.of(), yaw, Math.clamp(pitch + 4, -30, 30), true);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s, point %d of %d: %s to %s", r.key(), i, r.points(), r.from().name(), r.to().name())), false);
        return 1;
    }

    private static int marker(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Road.Marker best = null;
        Road of = null;
        double bd = 400;
        for (Road r : RoadNet.laid())
            for (Road.Marker m : r.markers()) {
                double d = Math.hypot(m.x() - p.getX(), m.z() - p.getZ());
                if (d < bd) {
                    bd = d;
                    best = m;
                    of = r;
                }
            }
        if (best == null) {
            src.sendFailure(Component.literal("No waymarker within 400 of you."));
            return 0;
        }
        // in front of the post, on the road side, reading its arms
        int wi = Math.clamp(best.point(), 0, of.points() - 1);
        double rx = of.x()[wi] - best.x(), rz = of.z()[wi] - best.z(), l = Math.max(1e-3, Math.hypot(rx, rz));
        double ex = best.x() + 0.5 + rx / l * 4.5, ez = best.z() + 0.5 + rz / l * 4.5;
        float yaw = (float) Math.toDegrees(Math.atan2(-(best.x() + 0.5 - ex), best.z() + 0.5 - ez));
        p.teleportTo((ServerLevel) p.level(), ex, best.y() + 1, ez, Set.of(), yaw, -8, true);
        Road.Marker m = best;
        src.sendSuccess(() -> Component.literal("Waymarker: " + m.arms().stream().map(a -> a.name() + (a.distance() > 0 ? " " + a.distance() : "")).toList()), false);
        return 1;
    }

    // ---- framings of towns, mining towns and wayhouses (VillageCommands.view) ------------------------------------------------

    static final int[] SX = {0, 1, 0, -1}, SZ = {-1, 0, 1, 0};

    /** A framing: the eye and what it looks at (plan coordinates; y over the street level), or null where the place has none. */
    public static double @Nullable [] frame(VillagePlan p, String name) {
        switch (name) {
            case "gate", "gate_out" -> {
                if (p.gates.isEmpty()) return null;
                int[] g = p.gates.getFirst();
                double ox = SX[g[3]], oz = SZ[g[3]];
                if (name.equals("gate")) return new double[]{g[0] + 0.5 + ox * 24, g[2] + 3.4, g[1] + 0.5 + oz * 24, g[0] + 0.5, g[2] + 4, g[1] + 0.5};
                return new double[]{g[0] + 0.5 - ox * 9, g[2] + 2.8, g[1] + 0.5 - oz * 9, g[0] + 0.5 + ox * 20, g[2] + 3, g[1] + 0.5 + oz * 20};
            }
            case "market" -> {
                // from the square's edge between the main streets, across the stalls to the bell tower
                VillagePlan.Bell b = p.bell;
                double tx = b != null ? b.bx() : 0, tz = b != null ? b.bz() : 0;
                double a = Math.atan2(-tz, -tx);
                return new double[]{Math.cos(a) * (p.greenR + 1) + 0.5, 3.0, Math.sin(a) * (p.greenR + 1) + 0.5, tx + 0.5, 5, tz + 0.5};
            }
            case "wall" -> {
                if (p.towers.isEmpty() || p.wallRing == null) return null;
                // on the walk, looking along it to the next tower
                int[] t = p.towers.get(p.towers.size() / 2);
                double a = Math.atan2(t[1], t[0]) - 0.22;
                double r = ring(p, a);
                return new double[]{Math.cos(a) * r + 0.5, t[2] + 9 + 1.62, Math.sin(a) * r + 0.5, t[0] + 0.5, t[2] + 10, t[1] + 0.5};
            }
            case "pass" -> {
                lay(p);
                return pass(p);
            }
            case "head" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int back = (m.side() + 2) & 3;
                int perp = (m.side() + 1) & 3;
                return new double[]{m.x() + 0.5 + SX[back] * 13 + SX[perp] * 6, m.lift() + 4.5, m.z() + 0.5 + SZ[back] * 13 + SZ[perp] * 6,
                        m.x() + 0.5, m.lift() + 6, m.z() + 0.5};
            }
            case "shaft" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int back = (m.side() + 2) & 3;
                return new double[]{m.x() + 0.5 + SX[back] * 2.6, m.lift() + 2.6, m.z() + 0.5 + SZ[back] * 2.6, m.x() + 0.5, m.level1() + 1, m.z() + 0.5};
            }
            case "level1" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int[] l = m.along(Math.max(3, m.landing() - 3)), w = m.along(m.winze());
                return new double[]{l[0] + 0.5, m.level1() + 2.62, l[1] + 0.5, w[0] + 0.5, m.level1() + 1.5, w[1] + 0.5};
            }
            case "winze" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int[] a = m.along(m.winze() - 3), w = m.along(m.winze());
                return new double[]{a[0] + 0.5, m.level1() + 2.62, a[1] + 0.5, w[0] + 0.5, m.level2() + 1, w[1] + 0.5};
            }
            case "face" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int[] a = m.along2(Math.max(2, m.reach2() - 7)), f = m.along2(m.reach2() + 1);
                return new double[]{a[0] + 0.5, m.level2() + 2.62, a[1] + 0.5, f[0] + 0.5, m.level2() + 2, f[1] + 0.5};
            }
            case "cavern" -> {
                VillagePlan.Mine m = p.mine;
                if (m == null) return null;
                int[] f = m.along2(m.reach2() + 2);
                return new double[]{f[0] + 0.5, m.level2() + 2.62, f[1] + 0.5, m.cavernX() + 0.5, m.cavernY(), m.cavernZ() + 0.5};
            }
            case "stable" -> {
                for (VillagePlan.Building b : p.buildings) {
                    if (!b.use().equals("barn")) continue;
                    CityPlan.Plot q = p.plots.get(b.plot());
                    double mx = (q.x0() + q.x1()) / 2.0, mz = (q.z0() + q.z1()) / 2.0;
                    double a = Math.atan2(mz, mx);
                    return new double[]{Math.cos(a) * 3 + 0.5, 3.2, Math.sin(a) * 3 + 0.5, mx + 0.5, q.lift() + 3, mz + 0.5};
                }
                return null;
            }
            case "marker" -> {
                // the waymarker on a road out of this place (the one nearest it), read from the road
                lay(p);
                Road best = null;
                Road.Marker bm = null;
                double bd = Double.MAX_VALUE;
                for (Road r : RoadNet.laid()) {
                    boolean ours = r.from().settlement().equals(p.key) || r.to().settlement().equals(p.key);
                    if (!ours) continue;
                    for (Road.Marker m : r.markers()) {
                        double d = Math.hypot(m.x() - p.ox, m.z() - p.oz);
                        if (d < bd) {
                            bd = d;
                            best = r;
                            bm = m;
                        }
                    }
                }
                if (bm == null) return null;
                int wi = Math.clamp(bm.point(), 0, best.points() - 1);
                double rx = best.x()[wi] - bm.x(), rz = best.z()[wi] - bm.z(), l = Math.max(1e-3, Math.hypot(rx, rz));
                double ex = bm.x() + 0.5 + rx / l * 4.5 + rz / l * 2, ez = bm.z() + 0.5 + rz / l * 4.5 - rx / l * 2;
                return new double[]{ex - p.ox, bm.y() + 2.4 - p.baseY, ez - p.oz, bm.x() + 0.5 - p.ox, bm.y() + 3 - p.baseY, bm.z() + 0.5 - p.oz};
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The highroads through a place laid now (a framing needs them before the land there has generated); not the lanes
     * round it, whose own roads would cascade over the regions about.
     */
    static void lay(VillagePlan p) {
        RoadWorld w = RoadWorld.get();
        if (w != null) RoadNet.highroadsNear(w, p.ox, p.oz, p.r + 40);
    }

    static double ring(VillagePlan p, double a) {
        float[] ring = p.wallRing;
        double deg = (Math.toDegrees(a) + 360) % 360;
        return ring[(int) Math.floor(deg) % 360];
    }

    /**
     * The pass: the cairn on a road into the town where it first comes into view, looking down at it; where no road crests
     * a rise, a point on the road out of its first gate 160 along, raised, looking back at the town.
     */
    static double @Nullable [] pass(VillagePlan p) {
        for (Road r : RoadNet.laid()) {
            boolean ours = r.from().settlement().equals(p.key) || r.to().settlement().equals(p.key);
            if (!ours) continue;
            for (Road.Marker m : r.markers()) {
                if (!m.cairn()) continue;
                int wi = Math.clamp(m.point(), 0, r.points() - 1);
                return new double[]{r.x()[wi] - p.ox, r.y()[wi] + 3.2 - p.baseY, r.z()[wi] - p.oz, 0.5, 6, 0.5};
            }
        }
        for (Road r : RoadNet.laid()) {
            boolean from = r.from().settlement().equals(p.key), to = r.to().settlement().equals(p.key);
            if (!from && !to) continue;
            int i = from ? Math.min(r.points() - 1, 40) : Math.max(0, r.points() - 41);
            return new double[]{r.x()[i] - p.ox, r.y()[i] + 12 - p.baseY, r.z()[i] - p.oz, 0.5, 4, 0.5};
        }
        return null;
    }
}
