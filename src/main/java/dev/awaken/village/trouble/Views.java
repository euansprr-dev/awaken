package dev.awaken.village.trouble;

import dev.awaken.city.CityPlan;
import dev.awaken.village.VillageService;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The tours' framings of a trouble (SPEC §28.7): an eye and the point it looks at, from the trouble's own places. */
final class Views {
    private Views() {}

    static Vec3 @Nullable [] frame(Plot.Ctx c, VillageService.Live l, @Nullable Trouble t, String name) {
        VillagePlan p = l.plan();
        Vec3 w = TroubleDirector.well(l);
        BlockPos place = t == null || t.place == 0 ? BlockPos.containing(w).offset(80, 0, 0) : BlockPos.of(t.place);
        double toward = Math.atan2(place.getZ() - w.z, place.getX() - w.x);
        switch (name) {
            case "well" -> {
                // across the green toward the well, the way the trouble went behind it
                Vec3 eye = TroubleDirector.green(l, toward + Math.PI * 0.82, p.greenR + 4);
                return new Vec3[]{eye.add(0, 2.4, 0), w.add(Math.cos(toward) * 2, 1.1, Math.sin(toward) * 2)};
            }
            case "edge" -> {
                // behind the green's edge (the Watch, the lanterns), out toward the dark the trouble went into
                Vec3 eye = TroubleDirector.green(l, toward, p.greenR - 3.5);
                Vec3 far = new Vec3(w.x + Math.cos(toward) * 40, eye.y + 1, w.z + Math.sin(toward) * 40);
                return new Vec3[]{eye.add(0, 2.2, 0), far};
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
            case "roofs", "line" -> {
                if (t == null) return null;
                int plot = Burning.plots(t).isEmpty() ? -1 : Burning.plots(t).getFirst();
                if (plot < 0) return null;
                CityPlan.Plot q = p.plots.get(plot);
                Vec3 mid = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, p.baseY + q.lift() + (name.equals("roofs") ? q.top() : 1.5), p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
                double a = Math.atan2(mid.z - w.z, mid.x - w.x);
                Vec3 eye = name.equals("roofs") ? TroubleDirector.green(l, a + 0.5, Math.max(2, p.greenR - 2)).add(0, 3.2, 0)
                        : new Vec3(w.x + Math.cos(a + 1.1) * 4, w.y + 2.4, w.z + Math.sin(a + 1.1) * 4);
                return new Vec3[]{eye, name.equals("roofs") ? mid : mid.add(w).scale(0.5)};
            }
            case "ruin", "scaffold" -> {
                TroubleState.Memory mem = c.st().peek(l.key());
                int plot = mem == null || mem.charred.isEmpty() ? -1 : mem.charred.getFirst();
                if (plot < 0 && t != null && t.kind.equals(TroubleKind.REBUILD.id)) {
                    var dmg = dev.awaken.village.api.Villages.damage(c.level().getServer(), l.key());
                    if (!dmg.isEmpty()) plot = p.plotAt(dmg.getFirst().pos().getX() - p.ox, dmg.getFirst().pos().getZ() - p.oz);
                }
                if (plot < 0) return null;
                CityPlan.Plot q = p.plots.get(plot);
                Vec3 mid = new Vec3(p.ox + (q.x0() + q.x1()) / 2.0 + 0.5, p.baseY + q.lift() + 2.5, p.oz + (q.z0() + q.z1()) / 2.0 + 0.5);
                double a = Math.atan2(w.z - mid.z, w.x - mid.x);
                double r = Math.max(q.x1() - q.x0(), q.z1() - q.z0()) * 0.5 + 8;
                double ex = mid.x + Math.cos(a + 0.35) * r, ez = mid.z + Math.sin(a + 0.35) * r;
                return new Vec3[]{new Vec3(ex, ground(c, ex, ez) + 3.4, ez), mid};
            }
            case "belfry" -> {
                BlockPos bell = BellTrouble.bellPos(l);
                if (bell == null) return null;
                // from just under the bell, out over the roofs and the land past them
                double a = Math.atan2(w.z - bell.getZ(), w.x - bell.getX()) + 0.6;
                Vec3 eye = new Vec3(bell.getX() + 0.5 - Math.cos(a) * 1.4, bell.getY() + 0.9, bell.getZ() + 0.5 - Math.sin(a) * 1.4);
                return new Vec3[]{eye, new Vec3(eye.x + Math.cos(a) * 40, eye.y - 12, eye.z + Math.sin(a) * 40)};
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

    static double ground(Plot.Ctx c, double x, double z) {
        return c.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }
}
