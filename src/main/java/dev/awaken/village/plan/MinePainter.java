package dev.awaken.village.plan;

import dev.awaken.city.SettlementPaint.Canvas;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.village.plan.VillageBlocks.*;

/**
 * A mining town's shaft head above ground (SPEC §28.9): a plank floor round the shaft's mouth with a rail on three sides
 * (open on the town's side, where the ladder goes down), a timber headframe over it (four posts nine high, girts and a
 * cap), the sheave wheel at its top with the rope running down the middle of the shaft, the winch's drum and its crank
 * on the town side, lanterns on the frame. The workings below are the mine piece's.
 */
final class MinePainter {
    private MinePainter() {}

    static void head(VillagePlan p, Canvas c, VillagePlan.Mine m) {
        if (Math.abs(m.x() - c.x0() - 8) > 14 || Math.abs(m.z() - c.z0() - 8) > 14) return;
        VillageBlocks.Wood w = VillagePainter.wood(p.family);
        int g = m.lift(), hx = m.x(), hz = m.z();
        int back = (m.side() + 2) & 3; // the town's side
        boolean ax = m.side() == 1 || m.side() == 3; // the workings run along x
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int x = hx + dx, z = hz + dz;
                boolean shaft = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (shaft) {
                    c.column(x, g - 3, g + 12, z, AIR);
                    continue;
                }
                c.set(x, g, z, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? COBBLE : w.planks());
                c.column(x, g + 1, g + 12, z, AIR);
                boolean frame = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                if (frame) {
                    c.column(x, g + 1, g + 9, z, w.post());
                    continue;
                }
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                // the rail round the mouth, open where the ladder is
                int mx = VillagePlanner.Work.STEP_X[back], mz = VillagePlanner.Work.STEP_Z[back];
                boolean gap = dx == mx * 2 && dz == mz * 2;
                if (ring && !gap) c.set(x, g + 1, z, w.fence()[railMask(dx, dz)]);
                // girts between the posts at five and nine
                if (ring) {
                    BlockState girt = Math.abs(dx) == 2 ? w.beamZ() : w.beamX();
                    c.set(x, g + 5, z, girt);
                    c.set(x, g + 9, z, girt);
                }
            }
        }
        // the cap over the frame and the sheave on it, its rope down the shaft's middle
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) if (Math.abs(dx) == 2 || Math.abs(dz) == 2) c.set(hx + dx, g + 10, hz + dz, w.slab());
        for (int a = -2; a <= 2; a++) {
            for (int dy = -2; dy <= 2; dy++) {
                double d = Math.hypot(a, dy);
                int x = ax ? hx + a : hx, z = ax ? hz : hz + a;
                if (d >= 1.5 && d <= 2.5) c.set(x, g + 12 + dy, z, w.planks());
                else if (a == 0 && dy == 0) c.set(x, g + 12, z, w.post());
            }
        }
        // the axle across the frame's top
        for (int k = -2; k <= 2; k++) {
            if (k == 0) continue;
            int x = ax ? hx : hx + k, z = ax ? hz + k : hz;
            c.set(x, g + 12, z, ax ? w.beamZ() : w.beamX());
        }
        c.column(hx, g + 1, g + 9, hz, CHAIN);
        // the winch on the town's side: its drum along the rail, a crank at its end, a lantern by it
        int bx = hx + VillagePlanner.Work.STEP_X[back] * 3, bz = hz + VillagePlanner.Work.STEP_Z[back] * 3;
        int lx = ax ? 0 : 1, lz = ax ? 1 : 0;
        c.set(bx + lx, g + 1, bz + lz, ax ? w.beamZ() : w.beamX());
        c.set(bx - lx, g + 1, bz - lz, ax ? w.beamZ() : w.beamX());
        c.set(bx + lx * 2, g + 1, bz + lz * 2, GRINDSTONE);
        c.set(bx - lx * 2, g + 1, bz - lz * 2, w.post());
        c.set(bx - lx * 2, g + 2, bz - lz * 2, LANTERN);
        // lanterns hung from the girts at the frame's front
        c.set(hx + VillagePlanner.Work.STEP_X[back] * 2, g + 4, hz + VillagePlanner.Work.STEP_Z[back] * 2, LANTERN_HANGING);
        c.set(hx + VillagePlanner.Work.STEP_X[m.side()] * 2, g + 4, hz + VillagePlanner.Work.STEP_Z[m.side()] * 2, LANTERN_HANGING);
    }

    /** A rail's mask (1 N, 2 E, 4 S, 8 W) for a cell of the ring round the mouth. */
    static int railMask(int dx, int dz) {
        if (Math.abs(dx) == 2) return 1 | 4;
        return 2 | 8;
    }

    static final BlockState LANTERN = of("minecraft:lantern[hanging=false]"), LANTERN_HANGING = of("minecraft:lantern[hanging=true]");
}
