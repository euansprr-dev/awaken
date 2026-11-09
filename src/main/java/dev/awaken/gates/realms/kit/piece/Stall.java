package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.realms.kit.N.rnd;

/** A market stall, or a heap of a family's stores; every stack starts from the ground. */
public final class Stall extends Piece<Mats> {
    final int x, y, z;
    final long seed;

    public Stall(int x, int z, long seed, Land land) {
        this.x = x;
        this.z = z;
        this.seed = seed;
        y = land.ground(x, z);
        box(x, z, 5, 5);
    }

    private void post(V v, Mats m, int a, int c, int top) {
        v.foot(x + a, y, z + c, m.pale);
        for (int k = 1; k <= top; k++) v.set(x + a, y + k, z + c, m.fence);
    }

    @Override
    public void draw(V v, Mats m) {
        int kind = (int) (rnd(seed, 1, 1) * 4);
        if (kind == 0) {
            for (int a = -3; a <= 3; a += 6) for (int c = -2; c <= 2; c += 4) post(v, m, a, c, 4);
            for (int a = -3; a <= 3; a++) for (int c = -2; c <= 2; c++) v.set(x + a, y + 5, z + c, (c & 2) == 0 ? (a & 1) == 0 ? m.tileSlab : m.tileDeepSlab : (a & 1) == 0 ? m.planksSlab : m.tileSlab);
            for (int a = -2; a <= 2; a++) {
                v.foot(x + a, y, z, m.pale);
                v.set(x + a, y + 1, z, m.barrel);
                v.set(x + a, y + 2, z, (a & 1) == 0 ? m.drum : m.candle);
            }
            v.set(x, y + 4, z, m.hung);
        } else {
            for (int i = 0; i < 9; i++) {
                int a = (int) (rnd(seed, i, 2) * 8) - 4, c = (int) (rnd(seed, i, 3) * 8) - 4, tall = (int) (rnd(seed, i, 4) * 3);
                BlockState s = rnd(seed, i, 5) < 0.4 ? m.drum : rnd(seed, i, 5) < 0.75 ? m.barrel : m.planks;
                v.foot(x + a, y, z + c, m.pale);
                for (int k = 1; k <= tall + 1; k++) v.set(x + a, y + k, z + c, s);
                if (rnd(seed, i, 6) < 0.5) v.set(x + a, y + tall + 2, z + c, m.candle);
            }
        }
    }
}
