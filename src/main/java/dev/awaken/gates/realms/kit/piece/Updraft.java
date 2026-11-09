package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

import static dev.awaken.gates.realms.kit.N.hexMetric;

/** A vent in the ground and the warm air that stands over it: you can ride it up the cliffs. */
public final class Updraft extends Piece<Mats> {
    final int x, gy, z, top;

    public Updraft(int x, int z, int top, Land land) {
        this.x = x;
        this.z = z;
        this.top = top;
        gy = land.ground(x, z);
        box(x, z, 4, 4);
    }

    @Override
    public void draw(V v, Mats m) {
        for (int a = ax(v); a <= bx(v); a++) for (int c = az(v); c <= bz(v); c++) {
            double d = hexMetric(a - x, c - z);
            if (d > 3.6) continue;
            v.foot(a, gy, c, m.deep);
            v.set(a, gy, c, d > 2.2 ? m.hexstone : d > 1.2 ? m.deep : m.glow);
            v.set(a, gy + 1, c, d > 2.4 ? m.stoneSlab : null);
            if (d <= 2.4) for (int y = gy + 1; y <= top; y++) if (v.get(a, y, c) == null) v.set(a, y, c, m.updraft);
        }
    }
}
