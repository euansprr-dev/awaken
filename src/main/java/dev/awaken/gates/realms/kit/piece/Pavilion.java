package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.core.Direction;

import static dev.awaken.gates.realms.kit.Build.stair;
import static dev.awaken.gates.realms.kit.Build.under;

public final class Pavilion extends Piece<Mats> {
    final int cx, gy, cz;

    public Pavilion(int cx, int gy, int cz) {
        this.cx = cx;
        this.gy = gy;
        this.cz = cz;
        box(cx, cz, 16, 16);
    }

    @Override
    public void draw(V v, Mats m) {
        for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
            int dx = x - cx, dz = z - cz, c = Math.max(Math.abs(dx), Math.abs(dz));
            if (c <= 15) v.foot(x, gy - 1, z, m.pale);
            for (int k = 0; k < 3; k++) if (c <= 15 - k * 2) v.set(x, gy + k, z, k == 2 && c < 11 ? c % 4 == 0 ? m.pale : m.hexstone : c == 15 - k * 2 ? m.pale : m.flag);
            boolean post = c == 11 && Math.abs(dx) % 11 == 0 && Math.abs(dz) % 11 == 0;
            if (post) {
                for (int y = gy + 3; y <= gy + 15; y++) v.set(x, y, z, y == gy + 3 || y == gy + 12 ? m.pale : m.timber);
                v.set(x, gy + 11, z, m.gold);
            }
            if (c == 11 && !post) {
                v.set(x, gy + 11, z, ((dx + dz) & 3) == 0 ? m.gold : m.band);
                // the frieze that carries the roof: a band, turned posts, a band
                v.set(x, gy + 12, z, m.planks);
                if (((dx + dz) & 1) == 0) {
                    v.set(x, gy + 13, z, m.fence);
                    v.set(x, gy + 14, z, m.fence);
                }
                v.set(x, gy + 15, z, m.planks);
            }
            for (int tier = 0; tier < 3; tier++) {
                int base = gy + 12 + tier * 5, e = 15 - tier * 4;
                for (int ring = 0; ring < 5; ring++) {
                    int er = e - ring, y = base + ring;
                    if (c != er) continue;
                    boolean ridge = Math.abs(dx) == Math.abs(dz);
                    Direction f = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    v.set(x, y, z, ridge ? m.pale : stair((ring & 1) == 0 ? m.tileDeepStairs : m.tileStairs, f));
                    if (ring == 0) v.set(x, y - 1, z, ridge ? m.gold : under(m.planksStairs, f.getOpposite()));
                }
                if (c < e - 4) for (int y = base; y < base + 5; y++) v.set(x, y, z, y == base + 4 ? (tier == 2 ? m.tileDeep : m.planks) : null);
            }
        }
        for (int s = -1; s <= 1; s += 2) for (int t = -1; t <= 1; t += 2) {
            v.set(cx + s * 11, gy + 10, cz + t * 11 - t, m.chain);
            v.set(cx + s * 11, gy + 9, cz + t * 11 - t, m.hung);
        }
        for (int a = -5; a <= 5; a += 2) for (int c = -5; c <= 5; c += 2) if (Math.abs(a) + Math.abs(c) < 8) {
            v.set(cx + a, gy + 3, cz + c, m.drum);
            if (Math.abs(a) + Math.abs(c) < 3) v.set(cx + a, gy + 4, cz + c, m.candle);
        }
        for (int y = gy + 9; y <= gy + 15; y++) v.set(cx, y, cz, m.chain);
        v.set(cx, gy + 8, cz, m.hung);
    }
}
