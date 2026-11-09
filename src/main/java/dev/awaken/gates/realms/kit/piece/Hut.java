package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.realms.kit.N.hexMetric;

/** A six-sided wax-folk house with a stepped dome; warm walls, small glowing windows, a real room inside. */
public final class Hut extends Piece<Mats> {
    final int cx, gy, cz, r, wall, dome;
    final double door;
    final long seed;

    public Hut(int cx, int cz, int r, double door, long seed, Land land) {
        this.cx = cx;
        this.cz = cz;
        this.gy = land.ground(cx, cz);
        this.r = r;
        this.door = door;
        this.seed = seed;
        wall = 4 + r / 3;
        dome = r + 1;
        box(cx, cz, r + 3, r + 3);
    }

    @Override
    public void draw(V v, Mats m) {
        for (int x = ax(v); x <= bx(v); x++) for (int z = az(v); z <= bz(v); z++) {
            double dx = x - cx, dz = z - cz, hd = hexMetric(dx, dz), a = Math.atan2(dz, dx);
            double sec = Math.PI / 3, s = Math.floor(a / sec + 0.5), phi = a - s * sec, t = Math.hypot(dx, dz) * Math.sin(phi);
            double side = r * 1.155 / 2;
            boolean corner = Math.abs(t) > side - 1.3;
            if (hd > r + 2.6) continue;
            // a plinth that goes down to whatever is really under it
            for (int y = gy + 1; y < gy + wall + dome + 6; y++) if (hd <= r + 1.3) v.set(x, y, z, null);
            v.foot(x, gy - 1, z, m.pale);
            v.set(x, gy, z, hd > r + 1.3 ? m.paleSlab : m.pale);
            if (hd > r + 1.2) continue;
            if (hd <= r + 1.3) v.set(x, gy + 1, z, hd > r ? m.paleSlab : m.planks);
            for (int y = gy + 2; y <= gy + wall; y++) {
                int ly = y - gy - 1;
                if (hd > r) continue;
                if (hd > r - 1.4) {
                    BlockState b = corner ? m.timber : ly <= 1 ? m.brick : m.plaster;
                    boolean doorSide = Math.abs(((a - door + Math.PI * 3) % (Math.PI * 2)) - Math.PI) < 0.45;
                    if (doorSide && Math.abs(t) < 1.6 && ly <= 3) {
                        v.set(x, y, z, null);
                        continue;
                    }
                    if (!corner && Math.abs(t) < 0.9 && ly >= 2 && ly <= 3) b = m.window;
                    else if (!corner && Math.abs(t) < 1.9 && ly >= 1 && ly <= 4) b = m.timber;
                    else if (ly == wall - 2 && !corner) b = m.band;
                    v.set(x, y, z, b);
                } else v.set(x, y, z, null);
            }
            int cy = gy + wall + 1;
            if (hd <= r + 1.2) v.set(x, cy, z, hd > r ? m.planksSlab : m.timber);
            if (hd > r + 0.2) continue;
            for (int y = cy + 1; y <= cy + dome; y++) {
                double h = (y - cy) / (double) dome;
                double rr = r * Math.sqrt(Math.max(0, 1 - h * h));
                if (hd > rr) continue;
                if (hd > rr - 1.7 || h > 0.7) {
                    BlockState b = (y & 1) == 0 ? m.tileDeep : m.tile;
                    if (corner && h < 0.85) b = m.pale;
                    if (h > 0.25 && h < 0.5 && !corner && Math.abs(t) < 1) b = m.window;
                    v.set(x, y, z, b);
                }
            }
            if (hd < 1.2) {
                v.set(x, cy + dome + 1, z, m.gold);
                v.set(x, cy + dome + 2, z, m.candle);
            }
        }
        v.set(cx, gy + 3, cz, m.light);
        v.set(cx, gy + 2, cz, m.drum);
        v.set(cx, gy + 3, cz, m.candle);
    }
}
