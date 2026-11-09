package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

import static dev.awaken.gates.realms.kit.N.rnd;

/** Something left out in the land: a cache on a worn stone, with what someone lit beside it long ago. */
public final class Find extends Piece<Mats> {
    final int x, gy, z;
    final long seed;

    public Find(int x, int gy, int z, long seed) {
        this.x = x;
        this.z = z;
        this.seed = seed;
        this.gy = gy;
        box(x, z, 2, 2);
    }

    @Override
    public void draw(V v, Mats m) {
        for (int a = -1; a <= 1; a++) for (int c = -1; c <= 1; c++) {
            if (rnd(a + 3, c + 3, seed) < 0.3 && (a != 0 || c != 0)) continue;
            v.foot(x + a, gy, z + c, m.worn);
            v.set(x + a, gy, z + c, (a == 0 && c == 0) ? m.pale : m.worn);
            if (v.get(x + a, gy + 1, z + c) != null) v.set(x + a, gy + 1, z + c, null);
        }
        v.set(x, gy + 1, z, m.cache);
        int cx = rnd(seed, 1, 2) < 0.5 ? 1 : -1, cz = rnd(seed, 2, 3) < 0.5 ? 1 : -1;
        if (v.solid(x + cx, gy, z + cz)) v.set(x + cx, gy + 1, z + cz, m.candle);
    }
}
