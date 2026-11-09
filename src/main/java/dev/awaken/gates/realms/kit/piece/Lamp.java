package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

/** A road or village lamp: a post on its own footing, from the real ground, wherever that turned out to be. */
public final class Lamp extends Piece<Mats> {
    final int x, y, z, h;

    public Lamp(int x, int z, int h, Land land) {
        this.x = x;
        this.z = z;
        this.h = h;
        y = land.ground(x, z);
        box(x, z, 1, 1);
    }

    @Override
    public void draw(V v, Mats m) {
        v.foot(x, y, z, m.pale);
        v.set(x, y + 1, z, m.deep);
        for (int i = 2; i < h; i++) v.set(x, y + i, z, m.fence);
        v.set(x, y + h, z, m.deep);
        for (int d = -1; d <= 1; d += 2) {
            v.set(x + d, y + h, z, m.fence);
            v.set(x + d, y + h - 1, z, m.hung);
            v.set(x, y + h, z + d, m.fence);
            v.set(x, y + h - 1, z + d, m.hung);
        }
        v.set(x, y + h + 1, z, m.glow);
        v.set(x, y + h + 2, z, m.stoneSlab);
    }
}
