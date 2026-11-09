package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

public final class Pylon extends Piece<Mats> {
    final int cx, cz, y0, h;

    public Pylon(int cx, int cz, int h, Land land) {
        this.cx = cx;
        this.cz = cz;
        this.h = h;
        y0 = land.ground(cx, cz) + 1;
        box(cx, cz, 6, 6);
        mayOverlap = true;
    }

    @Override
    public void draw(V v, Mats m) {
        for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
            int a = Math.abs(x - cx), c = Math.abs(z - cz), e = Math.max(a, c);
            if (e <= 5) v.foot(x, y0 - 1, z, m.deep);
            for (int y = y0; y <= y0 + h; y++) {
                int ly = y - y0, r = ly < 2 ? 5 : ly < 4 ? 4 : ly > h - 3 ? 5 : 3;
                if (e > r) continue;
                if (e < r - 1 && ly < h) continue;
                boolean corner = a == c;
                boolean panel = !corner && e == r && Math.min(a, c) < 2 && ly % 8 > 1 && ly % 8 < 7 && ly > 3 && ly < h - 3;
                v.set(x, y, z, ly >= h - 2 ? m.deep : corner ? m.gold : panel ? m.glow : (ly & 3) == 0 ? m.deep : m.stoneA);
            }
            if (e <= 2) v.set(x, y0 + h + 1, z, e == 2 ? m.fence : m.shroom);
            if (e == 0) v.set(x, y0 + h + 2, z, m.shroom);
            else if (e == 2 && a == c) v.set(x, y0 + h + 2, z, m.fence);
            if (e <= 2) v.set(x, y0 + h + 3, z, e == 2 ? m.brickSlab : m.deep);
        }
    }
}
