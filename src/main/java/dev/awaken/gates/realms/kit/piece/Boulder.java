package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

import static dev.awaken.gates.realms.kit.N.hexMetric;

public final class Boulder extends Piece<Mats> {
    final int x, y, z, r, h;
    final long seed;

    public Boulder(int x, int z, int r, int h, long seed, Land land) {
        this.x = x;
        this.y = land.ground(x, z);
        this.z = z;
        this.r = r;
        this.h = h;
        this.seed = seed;
        box(x, z, r + 1, r + 1);
    }

    @Override
    public void draw(V v, Mats m) {
        for (int a = ax(v); a <= bx(v); a++) for (int c = az(v); c <= bz(v); c++) {
            double hd = hexMetric(a - x, c - z);
            if (hd > r) continue;
            int top = y + h - (hd > r - 1 ? 1 : 0);
            for (int k = y - 2; k <= top; k++) v.set(a, k, c, k == top ? (hd > r - 1.4 ? m.pale : m.stoneA) : k % 3 == 0 ? m.deep : m.stoneA);
            v.foot(a, y - 3, c, m.deep);
            if (hd <= r - 1 && hd > r - 2.2) v.set(a, top + 1, c, m.stoneSlab);
            for (int k = top + 2; k < top + 5; k++) v.set(a, k, c, null);
        }
    }
}
