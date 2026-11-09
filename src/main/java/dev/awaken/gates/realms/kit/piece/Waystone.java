package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

import static dev.awaken.gates.realms.kit.N.hexMetric;

/** A Waystone: a stepped hex plinth, a waist-high stone you lay a hand on, two small pillars with their candles. */
public final class Waystone extends Piece<Mats> {
    public final int x, gy, z;

    public Waystone(int x, int z, Land land) {
        this.x = x;
        this.z = z;
        gy = land.ground(x, z);
        box(x, z, 5, 5);
        mayOverlap = true;
    }

    @Override
    public void draw(V v, Mats m) {
        for (int a = ax(v); a <= bx(v); a++) for (int c = az(v); c <= bz(v); c++) {
            double d = hexMetric(a - x, c - z);
            if (d > 4.4) continue;
            v.foot(a, gy, c, m.pale);
            v.set(a, gy, c, d > 3.2 ? m.flag : ((a + c) & 1) == 0 ? m.hexstone : m.flag);
            if (d <= 2.2) v.set(a, gy + 1, c, d > 1.2 ? m.paleSlab : m.pale);
            for (int y = gy + 2; y < gy + 8; y++) if (d <= 2.6 && v.get(a, y, c) != null) v.set(a, y, c, null);
        }
        v.set(x, gy + 2, z, m.shrine);
        for (int s = -1; s <= 1; s += 2) {
            int px = x + s * 3, pz = z - s;
            v.foot(px, gy, pz, m.pale);
            v.set(px, gy + 1, pz, m.pale);
            v.set(px, gy + 2, pz, m.timber);
            v.set(px, gy + 3, pz, m.gold);
            v.set(px, gy + 4, pz, m.candle);
        }
    }
}
