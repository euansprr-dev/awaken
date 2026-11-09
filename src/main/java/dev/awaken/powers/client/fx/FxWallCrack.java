package dev.awaken.powers.client.fx;

import dev.awaken.powers.fx.FxStyle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.powers.client.fx.FxWorld.R;
import static dev.awaken.powers.client.fx.FxWorld.alpha;

/**
 * A wall a thrown body hit (SPEC §50.3): the game's own break marks spread over the real blocks round the impact, healing a stage at
 * a time, with chunks of that wall thrown out and dust along its face. Purely a picture: the blocks are not touched.
 */
final class FxWallCrack {
    private FxWallCrack() {}

    private static final int SLOTS = 64, ID_BASE = 0x7A5A0000;
    /** Ticks each stage of the marks holds before healing by one. */
    private static final int HEAL = 7;
    private static final BlockPos[] POS = new BlockPos[SLOTS];
    private static final int[] STAGE = new int[SLOTS], NEXT = new int[SLOTS];
    private static int cursor;

    static void at(ClientLevel level, double x, double y, double z, float nx, float ny, float nz, float power, FxStyle st) {
        Direction out = Direction.getApproximateNearest(nx, ny, nz);
        Direction u = out.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Direction v = out.getAxis() == Direction.Axis.Y ? Direction.EAST : out.getClockWise();
        BlockPos hit = BlockPos.containing(x - out.getStepX() * 0.1, y - out.getStepY() * 0.1, z - out.getStepZ() * 0.1);
        int reach = power >= 1 ? 2 : 1;
        BlockState wall = level.getBlockState(hit);
        for (int a = -reach; a <= reach; a++) {
            for (int b = -reach; b <= reach; b++) {
                int dist = Math.abs(a) + Math.abs(b);
                if (dist > reach + 1) continue;
                BlockPos p = hit.relative(u, a).relative(v, b);
                if (level.getBlockState(p).isAir()) continue;
                mark(level, p, Mth.clamp(Math.round((power >= 1 ? 8 : 6) - dist * 1.6f + R.s()), 1, 9));
            }
        }
        if (!wall.isAir()) {
            for (int i = FxWorld.n((int) (12 * power)); i > 0; i--) {
                float spread = 0.8f * power;
                double px = x + (u.getStepX() + v.getStepX()) * R.s() * spread, py = y + (u.getStepY() + v.getStepY()) * R.s() * spread,
                        pz = z + (u.getStepZ() + v.getStepZ()) * R.s() * spread;
                FxDebris.spawn(wall, px + nx * 0.2, py + ny * 0.2, pz + nz * 0.2, nx * R.f(0.08f, 0.3f) + R.s() * 0.08, 0.1f + R.f(0, 0.25f), nz * R.f(0.08f, 0.3f) + R.s() * 0.08,
                        R.f(0.12f, 0.3f), 26 + R.i(10), (float) FxWorld.groundY(px, py, pz));
            }
        }
        FxRecipes.flare(x, y, z, 1.1f * power, st, 5);
        FxRecipes.ring(x + nx * 0.1, y + ny * 0.1, z + nz * 0.1, 0.2f, 2.4f * power, 0.5f, 0.08f, 9, alpha(st.mid, 0.8f), alpha(st.edge, 0), nx, ny, nz);
        FxRecipes.smokePuffs(x + nx * 0.4, y + ny * 0.4, z + nz * 0.4, FxWorld.n((int) (7 * power)), 0.7f * power, 0.9f, st, 0.03f, 24);
    }

    private static void mark(ClientLevel level, BlockPos p, int stage) {
        int i = cursor;
        cursor = (cursor + 1) % SLOTS;
        if (POS[i] != null) level.destroyBlockProgress(ID_BASE + i, POS[i], -1);
        POS[i] = p;
        STAGE[i] = stage;
        NEXT[i] = FxWorld.ticks + HEAL + 18;
        level.destroyBlockProgress(ID_BASE + i, p, stage);
    }

    static void tick(ClientLevel level) {
        for (int i = 0; i < SLOTS; i++) {
            if (POS[i] == null || FxWorld.ticks < NEXT[i]) continue;
            if (--STAGE[i] < 0) {
                level.destroyBlockProgress(ID_BASE + i, POS[i], -1);
                POS[i] = null;
            } else {
                NEXT[i] = FxWorld.ticks + HEAL;
                level.destroyBlockProgress(ID_BASE + i, POS[i], STAGE[i]);
            }
        }
    }

    static void clear() {
        java.util.Arrays.fill(POS, null);
    }
}
