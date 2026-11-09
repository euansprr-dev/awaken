package dev.awaken.mobs.entity;

import dev.awaken.mobs.client.life.LegRig;
import dev.awaken.mobs.client.life.LegSurface;
import dev.awaken.mobs.client.life.Legs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Every rig's feet beside real blocks (SPEC §23.1, "On real ground"; Euan 9 Nov, the Drip Lurker's legs down the face of a
 * gravel step). The body stands as close to the blocks as its hips allow, walks along them and stops. The foot and the
 * line of the leg are checked here on their own, against the cells, not through the code that placed them.
 */
final class LegBlockTrial {
    private LegBlockTrial() {}

    private interface Cells {
        boolean solid(int x, int y, int z);
    }

    private record Scene(String name, boolean corner, Cells cells) {}

    private static final Scene[] SCENES = {
            new Scene("wall", false, (x, y, z) -> y < 0 || x >= 0),
            new Scene("two-block drop", false, (x, y, z) -> x < 0 ? y < 0 : y < -2),
            new Scene("step down", false, (x, y, z) -> x < 0 ? y < 0 : y < -1),
            new Scene("kerb", false, (x, y, z) -> y < 0 || x >= 0 && y < 1),
            new Scene("corner", true, (x, y, z) -> y < 0 || x >= 0 || z >= 0),
    };

    /** What the whole run found, for the log. */
    static final class Tally {
        int rigs, inside, through, stretched, restless;
        double worst;

        @Override
        public String toString() {
            return rigs + " rigs on " + SCENES.length + " scenes: feet in a block " + inside + ", legs through a block " + through + ", legs over reach " + stretched
                    + " (worst " + String.format(java.util.Locale.ROOT, "%.2f", worst) + "x), feet still stepping at rest " + restless;
        }
    }

    /** Null if this rig's feet are right beside every scene; otherwise what's wrong. */
    static @Nullable String run(LegRig rig, float stride, Tally tally) {
        tally.rigs++;
        double most = 0;
        for (int i = 0; i < rig.n; i++) most = Math.max(most, Math.max(Math.abs(rig.hipX[i]), Math.abs(rig.hipZ[i])) / 16.0);
        double margin = most + 0.15; // every hip over open ground, just short of the blocks
        StringBuilder out = null;
        for (Scene s : SCENES) {
            String fault = scene(rig, s, -margin, s.corner ? -margin : 0.5, stride, tally);
            if (fault == null) continue;
            if (out == null) out = new StringBuilder();
            else out.append(", ");
            out.append(s.name).append(": ").append(fault);
        }
        return out == null ? null : out.toString();
    }

    private static @Nullable String scene(LegRig rig, Scene s, double x, double startZ, float stride, Tally tally) {
        Cells c = s.cells;
        BlockGetter world = new World(c);
        Legs feet = new Legs(rig, 7, new LegSurface(() -> world));
        float dt = 1.0F / 60.0F;
        int settle = 30, walk = s.corner ? 0 : 120, rest = 180, frames = settle + walk + rest;
        double z = startZ;
        int inside = 0, through = 0, far = 0, restless = 0;
        double worst = 0;
        for (int k = 0; k < frames; k++) {
            boolean walking = k >= settle && k < settle + walk;
            if (walking) z += stride * dt;
            feet.update(x, 0, z, 0, 1, dt, true, stride, 0, false);
            boolean lastSecond = k >= frames - 60;
            for (int i = 0; i < rig.n; i++) {
                if (feet.stepping(i)) {
                    if (lastSecond) restless++;
                    continue;
                }
                double fx = feet.footX(i), fy = feet.footY(i), fz = feet.footZ(i);
                if (at(c, fx, fy + 0.02, fz)) {
                    inside++;
                    continue;
                }
                if (!lastSecond) continue; // on the move a leg re-checks itself in turn; settled, it must be right
                double hx = x + rig.hipX[i] / 16.0, hy = (24.0 - rig.hipY[i]) / 16.0, hz = z - rig.hipZ[i] / 16.0;
                if (crosses(c, hx, hy, hz, fx, fy, fz)) through++;
                if (!rig.rigid(i) && Math.abs(fy) > 0.1) {
                    double d = Math.sqrt((fx - hx) * (fx - hx) + (fy - hy) * (fy - hy) + (fz - hz) * (fz - hz));
                    double ratio = d / (rig.reach(i) / 16.0);
                    worst = Math.max(worst, ratio);
                    if (ratio > 1.05) far++;
                }
            }
        }
        tally.inside += inside;
        tally.through += through;
        tally.stretched += far;
        tally.restless += restless;
        tally.worst = Math.max(tally.worst, worst);
        if (inside + through + far + restless == 0) return null;
        return "in a block " + inside + ", through a block " + through + ", over reach " + far + ", stepping at rest " + restless;
    }

    private static boolean at(Cells c, double x, double y, double z) {
        return c.solid(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    /** Along the leg, a tenth of a block at a time, leaving the first and last fifth of a block (hip and ankle). */
    private static boolean crosses(Cells c, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (double d = 0.2; d < len - 0.2; d += 0.1) {
            double t = d / len;
            if (at(c, x0 + dx * t, y0 + dy * t, z0 + dz * t)) return true;
        }
        return false;
    }

    private record World(Cells cells) implements BlockGetter {
        private static final BlockState STONE = Blocks.STONE.defaultBlockState(), AIR = Blocks.AIR.defaultBlockState();

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return cells.solid(pos.getX(), pos.getY(), pos.getZ()) ? STONE : AIR;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }
}
