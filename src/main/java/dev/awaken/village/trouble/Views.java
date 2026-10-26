package dev.awaken.village.trouble;

import dev.awaken.city.CityPlan;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.world.fauna.LairRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The tours' framings of a trouble (SPEC §28.7): an eye and the point it looks at, from the trouble's own places. Every
 * eye is then moved, as little as it takes, to one that sees its mark: not inside a block or a tree, not under an eave,
 * nothing solid between ({@link #clear}).
 */
final class Views {
    private Views() {}

    static Vec3 @Nullable [] frame(Plot.Ctx c, VillageService.Live l, @Nullable Trouble t, String name) {
        Vec3[] v = raw(c, l, t, name);
        return v == null ? null : clear(c, v[0], v[1], v.length > 2 ? v[2] : v[1]);
    }

    private static Vec3 @Nullable [] raw(Plot.Ctx c, VillageService.Live l, @Nullable Trouble t, String name) {
        VillagePlan p = l.plan();
        Vec3 w = TroubleDirector.well(l);
        BlockPos place = t == null || t.place == 0 ? BlockPos.containing(w).offset(80, 0, 0) : BlockPos.of(t.place);
        double toward = Math.atan2(place.getZ() - w.z, place.getX() - w.x);
        switch (name) {
            case "well" -> {
                // across the green toward the well, the way the trouble went behind it, a little above the crowd
                Vec3 eye = TroubleDirector.green(l, toward + Math.PI * 0.82, p.greenR + 4);
                return new Vec3[]{eye.add(0, 3.0, 0), w.add(Math.cos(toward) * 2, 1.1, Math.sin(toward) * 2)};
            }
            case "edge" -> {
                // behind the green's edge (the Watch, the lanterns), out toward the dark the trouble went into
                Vec3 eye = TroubleDirector.green(l, toward, p.greenR - 3.5);
                Vec3 far = new Vec3(w.x + Math.cos(toward) * 40, eye.y + 1, w.z + Math.sin(toward) * 40);
                return new Vec3[]{eye.add(0, 2.2, 0), far, TroubleDirector.green(l, toward, p.greenR + 1.5).add(0, 1.2, 0)};
            }
            case "scouts" -> {
                // the camp's scouts at the treeline, seen from the village's edge
                if (t == null) return null;
                Vec3 at = null;
                int n = (int) t.get("scout_n", 0);
                for (int k = 0; k + 1 < n && at == null; k += 2) {
                    Entity e = c.level().getEntity(new UUID(t.get("s" + k, 0), t.get("s" + (k + 1), 0)));
                    if (e != null && e.isAlive()) at = e.position();
                }
                if (at == null) {
                    BlockPos edge = Burning.edge(c, t, l, 30);
                    if (edge == null) return null;
                    at = Vec3.atBottomCenterOf(edge);
                }
                double a = Math.atan2(w.z - at.z, w.x - at.x);
                double ex = at.x + Math.cos(a + 0.25) * 9, ez = at.z + Math.sin(a + 0.25) * 9;
                return new Vec3[]{new Vec3(ex, ground(c, ex, ez) + 2.2, ez), at.add(0, 1.3, 0)};
            }
            case "trail" -> {
                if (t == null) return null;
                int n = Taken.trailLength(t);
                if (n < 4) return null;
                BlockPos a = Taken.trailPoint(t, n * 2 / 5), b = Taken.trailPoint(t, Math.min(n - 1, n * 2 / 5 + 4));
                double ax = a.getX() + 0.5, az = a.getZ() + 0.5;
                double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ(), len = Math.max(1, Math.hypot(dx, dz));
                double ex = ax - dx / len * 4, ez = az - dz / len * 4;
                double ey = ground(c, ex, ez) + 2.3;
                return new Vec3[]{new Vec3(ex, ey, ez), new Vec3(b.getX() + 0.5, ground(c, b.getX() + 0.5, b.getZ() + 0.5) + 0.2, b.getZ() + 0.5)};
            }
            case "lair" -> {
                double dx = w.x - place.getX(), dz = w.z - place.getZ(), len = Math.max(1, Math.hypot(dx, dz));
                double ex = place.getX() + 0.5 + dx / len * 15, ez = place.getZ() + 0.5 + dz / len * 15;
                return new Vec3[]{new Vec3(ex, ground(c, ex, ez) + 4.5, ez), Vec3.atCenterOf(place).add(0, 0.5, 0)};
            }
            case "nest" -> {
                if (t == null) return null;
                Rescuee r = Taken.rescuee(c, t);
                Vec3 at = r != null ? r.position() : Vec3.atBottomCenterOf(BlockPos.of(t.get("nest", t.place)));
                double dx = w.x - at.x, dz = w.z - at.z, len = Math.max(1, Math.hypot(dx, dz));
                double ex = at.x + dx / len * 3.6 + dz / len * 1.2, ez = at.z + dz / len * 3.6 - dx / len * 1.2;
                return new Vec3[]{new Vec3(ex, at.y + 1.7, ez), at.add(0, 1.0, 0)};
            }
            case "fight" -> {
                // the lair's beast and the one bound in the nest in one frame, from the village's side
                if (t == null) return null;
                Entity beast = beast(c, t);
                Rescuee r = Taken.rescuee(c, t);
                Vec3 b = beast != null ? beast.position() : Vec3.atBottomCenterOf(place);
                Vec3 n = r != null ? r.position() : Vec3.atBottomCenterOf(BlockPos.of(t.get("nest", t.place)));
                Vec3 mid = b.add(n).scale(0.5);
                // across the line between them (the side nearer the village), so neither hides the other
                double along = Math.atan2(n.z - b.z, n.x - b.x), a = along + Math.PI * 0.5;
                if (Math.cos(a) * (w.x - mid.x) + Math.sin(a) * (w.z - mid.z) < 0) a += Math.PI;
                double d = Math.max(6, b.distanceTo(n) + 4);
                double ex = mid.x + Math.cos(a) * d, ez = mid.z + Math.sin(a) * d;
                return new Vec3[]{new Vec3(ex, Math.max(ground(c, ex, ez), mid.y) + 1.8, ez), mid.add(0, 1.0, 0)};
            }
            case "roofs" -> {
                // the roofs alight, from across the village and a little above them
                if (t == null) return null;
                List<Integer> plots = Burning.plots(t);
                if (plots.isEmpty()) return null;
                Vec3 sum = Vec3.ZERO;
                List<Vec3> tops = new ArrayList<>();
                for (int plot : plots) {
                    CityPlan.Plot q = p.plots.get(plot);
                    Vec3 top = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, p.baseY + q.lift() + q.top(), p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
                    tops.add(top);
                    sum = sum.add(top);
                }
                Vec3 mid = sum.scale(1.0 / tops.size());
                double spread = 0;
                for (Vec3 top : tops) spread = Math.max(spread, Math.hypot(top.x - mid.x, top.z - mid.z));
                double a = Math.hypot(w.x - mid.x, w.z - mid.z) < 3 ? toward + Math.PI : Math.atan2(w.z - mid.z, w.x - mid.x);
                double d = spread + 16;
                double ex = mid.x + Math.cos(a + 0.3) * d, ez = mid.z + Math.sin(a + 0.3) * d;
                return new Vec3[]{new Vec3(ex, Math.max(ground(c, ex, ez) + 2.5, mid.y + 1.5), ez), mid.add(0, 0.5, 0)};
            }
            case "line" -> {
                // the bucket line from the well to the burning house's door, from beside it, the roof alight above them
                if (t == null) return null;
                int plot = Burning.plots(t).isEmpty() ? -1 : Burning.plots(t).getFirst();
                if (plot < 0) return null;
                CityPlan.Plot q = p.plots.get(plot);
                Vec3 to = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, w.y, p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
                for (VillagePlan.Building b : p.buildings) if (b.plot() == plot) to = new Vec3(p.ox + b.outX() + 0.5, w.y, p.oz + b.outZ() + 0.5);
                // from behind the well, down the line of people to the burning door, the roof alight over it
                double a = Math.atan2(to.z - w.z, to.x - w.x), len = Math.hypot(to.x - w.x, to.z - w.z);
                double ex = w.x - Math.cos(a + 0.25) * 5, ez = w.z - Math.sin(a + 0.25) * 5;
                Vec3 look = new Vec3(w.x + Math.cos(a) * len * 0.8, p.baseY + q.lift() + Math.max(3, q.eave()) - 1.5, w.z + Math.sin(a) * len * 0.8);
                Vec3 mark = new Vec3(w.x + Math.cos(a) * len * 0.5, w.y + 1.2, w.z + Math.sin(a) * len * 0.5);
                return new Vec3[]{new Vec3(ex, ground(c, ex, ez) + 3.2, ez), look, mark};
            }
            case "ruin", "scaffold", "smoke" -> {
                int plot = worst(c, l, t, name.equals("scaffold"));
                if (plot < 0) return null;
                CityPlan.Plot q = p.plots.get(plot);
                Vec3 mid = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, p.baseY + q.lift() + 3.0, p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
                // from the street before its door (else from the well's side), a little off square
                double a = Math.atan2(w.z - mid.z, w.x - mid.x);
                for (VillagePlan.Building b : p.buildings)
                    if (b.plot() == plot && (b.outX() != b.doorX() || b.outZ() != b.doorZ())) a = Math.atan2(b.outZ() - b.doorZ(), b.outX() - b.doorX());
                double r = Math.max(q.x1() - q.x0(), q.z1() - q.z0()) * 0.5 + (name.equals("smoke") ? 26 : 9);
                double ex = mid.x + Math.cos(a + 0.35) * r, ez = mid.z + Math.sin(a + 0.35) * r;
                Vec3 look = name.equals("smoke") ? mid.add(0, 9, 0) : mid;
                return new Vec3[]{new Vec3(ex, ground(c, ex, ez) + (name.equals("smoke") ? 4.5 : 2.6), ez), look, mid};
            }
            case "belfry" -> {
                // from outside the tower, level with the bell and past it: the bell, the roofs below, the land the ring runs over
                BlockPos bell = BellTrouble.bellPos(l);
                if (bell == null) return null;
                double a = Math.atan2(w.z - bell.getZ(), w.x - bell.getX()) + 0.45;
                Vec3 b = Vec3.atCenterOf(bell);
                Vec3 eye = new Vec3(b.x - Math.cos(a) * 7, b.y + 2.2, b.z - Math.sin(a) * 7);
                return new Vec3[]{eye, new Vec3(b.x + Math.cos(a) * 22, b.y - 6, b.z + Math.sin(a) * 22), b};
            }
            case "bell" -> {
                BlockPos bell = BellTrouble.bellPos(l);
                if (bell == null) return null;
                double a = Math.atan2(w.z - bell.getZ(), w.x - bell.getX());
                Vec3 eye = new Vec3(bell.getX() + 0.5 + Math.cos(a) * 9, 0, bell.getZ() + 0.5 + Math.sin(a) * 9);
                return new Vec3[]{new Vec3(eye.x, ground(c, eye.x, eye.z) + 2.4, eye.z), Vec3.atCenterOf(bell)};
            }
            case "green", "dance" -> {
                Vec3 fire = t != null && t.get("fire", 0) != 0 ? Vec3.atCenterOf(BlockPos.of(t.get("fire", 0))) : w;
                double a = Math.atan2(fire.z - w.z, fire.x - w.x) + Math.PI + 0.4;
                Vec3 eye = name.equals("green") ? TroubleDirector.green(l, a, p.greenR + 4).add(0, 5.5, 0)
                        : new Vec3(fire.x + Math.cos(a) * 6.5, fire.y + 1.9, fire.z + Math.sin(a) * 6.5);
                return new Vec3[]{eye, fire.add(0, name.equals("green") ? 1 : 0.8, 0)};
            }
            default -> {
                return null;
            }
        }
    }

    /** The lair's beast, if it's there. */
    static @Nullable Entity beast(Plot.Ctx c, Trouble t) {
        if (!t.cause.startsWith("lair:")) return null;
        LairRegistry.Entry e = LairRegistry.get(c.level().getServer()).get(Long.parseLong(t.cause.substring(5)));
        if (e == null || e.resident().isEmpty()) return null;
        Entity beast = c.level().getEntity(e.resident().get());
        return beast != null && beast.isAlive() ? beast : null;
    }

    /** The house fire hurt most (the most blocks in the damage record), charred or being rebuilt. */
    private static int worst(Plot.Ctx c, VillageService.Live l, @Nullable Trouble t, boolean rebuilding) {
        VillagePlan p = l.plan();
        TroubleState.Memory mem = c.st().peek(l.key());
        Map<Integer, Integer> count = new HashMap<>();
        for (Villages.Damage d : Villages.damage(c.level().getServer(), l.key())) {
            int plot = p.plotAt(d.pos().getX() - p.ox, d.pos().getZ() - p.oz);
            if (plot >= 0) count.merge(plot, 1, Integer::sum);
        }
        int best = -1, bn = 0;
        for (var e : count.entrySet()) {
            boolean charred = mem != null && mem.charred.contains(e.getKey());
            if (!rebuilding && mem != null && !mem.charred.isEmpty() && !charred) continue;
            if (e.getValue() > bn) {
                bn = e.getValue();
                best = e.getKey();
            }
        }
        if (best < 0 && mem != null && !mem.charred.isEmpty()) best = mem.charred.getFirst();
        return best;
    }

    // ---- an eye that sees ---------------------------------------------------------------------------------------------------

    /**
     * The nearest eye to {@code eye} (turned round the mark, pulled in or out, raised) that sees {@code mark} and looks at
     * {@code at}: room to stand (no block or leaves at it or under it), open sky over it (not under an eave or a tree) and
     * nothing solid between. Failing open sky, the first that sees it; failing that, as given.
     */
    static Vec3[] clear(Plot.Ctx c, Vec3 eye, Vec3 at, Vec3 mark) {
        ServerLevel level = c.level();
        double dx = eye.x - mark.x, dz = eye.z - mark.z;
        double dist = Math.max(2.5, Math.hypot(dx, dz)), base = Math.atan2(dz, dx), up = eye.y - mark.y;
        record Try(double turn, double scale, double lift, double cost) {}
        List<Try> tries = new ArrayList<>();
        for (double turn : new double[]{0, 0.2, -0.2, 0.4, -0.4, 0.65, -0.65, 0.95, -0.95, 1.3, -1.3})
            for (double scale : new double[]{1, 0.8, 1.25, 0.6, 1.5})
                for (double lift : new double[]{0, 1.5, 3, 5, 8})
                    tries.add(new Try(turn, scale, lift, Math.abs(turn) * 4 + Math.abs(scale - 1) * 3 + lift * 0.35));
        tries.sort((a, b) -> Double.compare(a.cost, b.cost));
        // the ground the eye was meant to stand over: a column much higher is a roof or a wall top, not a place to stand
        double meant = level.isLoaded(BlockPos.containing(eye)) ? ground(c, eye.x, eye.z) : mark.y;
        Vec3 narrow = null, seeing = null;
        for (Try k : tries) {
            double a = base + k.turn, r = dist * k.scale;
            double x = mark.x + Math.cos(a) * r, z = mark.z + Math.sin(a) * r;
            if (!level.isLoaded(BlockPos.containing(x, mark.y, z))) continue;
            double g = ground(c, x, z);
            if (g > meant + 2.5) continue;
            double y = Math.max(mark.y + up, g + 1.7) + k.lift;
            Vec3 e = new Vec3(x, y, z);
            if (!room(level, e) || !sees(level, e, mark, r)) continue;
            if (open(level, e)) {
                if (wide(level, e, mark)) return new Vec3[]{e, at};
                if (narrow == null) narrow = e;
            } else if (seeing == null) seeing = e;
        }
        return new Vec3[]{narrow != null ? narrow : seeing != null ? seeing : eye, at};
    }

    /** Nothing to bump into or see through at the eye and down the body under it. */
    private static boolean room(ServerLevel level, Vec3 e) {
        for (double dy : new double[]{0.25, -0.6, -1.5}) {
            BlockPos q = BlockPos.containing(e.x, e.y + dy, e.z);
            BlockState s = level.getBlockState(q);
            if (s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty() || !s.getCollisionShape(level, q).isEmpty()) return false;
        }
        return true;
    }

    /** Nothing close by filling a side of the frame: the sight lines a third of the way to either side of the mark run clear half way. */
    private static boolean wide(ServerLevel level, Vec3 e, Vec3 mark) {
        double dx = mark.x - e.x, dz = mark.z - e.z, len = Math.max(1, Math.hypot(dx, dz));
        double sx = -dz / len * len * 0.33, sz = dx / len * len * 0.33;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 to = new Vec3(mark.x + sx * side, mark.y, mark.z + sz * side);
            HitResult hit = level.clip(new ClipContext(e, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(e) < e.distanceTo(to) * 0.5) return false;
        }
        return true;
    }

    /** Sky over the eye (no roof, eave or crown above it). */
    private static boolean open(ServerLevel level, Vec3 e) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(e.x), (int) Math.floor(e.z)) <= e.y + 0.5;
    }

    /** Nothing between the eye and the mark (what it hits, if anything, is at the mark). */
    private static boolean sees(ServerLevel level, Vec3 e, Vec3 at, double dist) {
        HitResult hit = level.clip(new ClipContext(e, at, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(at) <= Math.max(2.5, dist * 0.15);
    }

    static double ground(Plot.Ctx c, double x, double z) {
        return c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }
}
