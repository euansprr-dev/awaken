package dev.awaken.gates.realms.kit.piece;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;

import static dev.awaken.gates.realms.kit.N.lerp;
import static dev.awaken.gates.realms.kit.N.rnd;

/** A gnarled old tree, grown from the ground, that frames the view from the hill. */
public final class Tree extends Piece<Mats> {
    final int x, z, gy, h;
    final long seed;

    public Tree(int x, int z, int h, long seed, Land land) {
        this.x = x;
        this.z = z;
        this.h = h;
        this.seed = seed + land.ctx.seed();
        gy = land.ground(x, z);
        box(x, z, h, h);
        mayOverlap = true;
    }

    private void limb(V v, Mats m, double ax, double ay, double az, double bx, double by, double bz, double r0, double r1) {
        int n = (int) (Math.hypot(bx - ax, Math.hypot(by - ay, bz - az)) * 2) + 1;
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, r = lerp(r0, r1, t);
            double cx = lerp(ax, bx, t), cy = lerp(ay, by, t), cz = lerp(az, bz, t);
            int a = (int) Math.ceil(r);
            for (int p = -a; p <= a; p++) for (int q = -a; q <= a; q++) for (int s = -a; s <= a; s++)
                if (p * p + q * q + s * s <= r * r + 0.25) v.set((int) Math.floor(cx) + p, (int) Math.floor(cy) + q, (int) Math.floor(cz) + s, m.timber);
        }
    }

    private void leaves(V v, Mats m, double cx, double cy, double cz, double r) {
        int a = (int) Math.ceil(r);
        for (int p = -a; p <= a; p++) for (int q = -a / 2; q <= a / 2; q++) for (int s = -a; s <= a; s++) {
            double d = p * p + q * q * 3.2 + s * s;
            if (d > r * r || rnd(p + (long) cx, s + (long) cz, q + seed) < 0.18) continue;
            int X = (int) Math.floor(cx) + p, Y = (int) Math.floor(cy) + q, Z = (int) Math.floor(cz) + s;
            if (v.get(X, Y, Z) == null) v.set(X, Y, Z, m.canopy);
        }
    }

    @Override
    public void draw(V v, Mats m) {
        limb(v, m, x, gy - 2, z, x + 1, gy + h * 0.55, z - 1, 2.2, 1.2);
        int boughs = 5;
        for (int i = 0; i < boughs; i++) {
            double a = i * 2.399 + rnd(seed, i, 1) * 0.8, len = h * (0.34 + 0.2 * rnd(seed, i, 2)), up = h * (0.1 + 0.22 * rnd(seed, i, 3));
            double sx = x + 1, sy = gy + h * (0.42 + 0.12 * i / boughs), sz = z - 1, ex = sx + Math.cos(a) * len, ez = sz + Math.sin(a) * len, ey = sy + up;
            limb(v, m, sx, sy, sz, ex, ey, ez, 1.1, 0.5);
            leaves(v, m, ex, ey + 1, ez, 6 + 3 * rnd(seed, i, 4));
        }
        leaves(v, m, x + 1, gy + h * 0.72, z - 1, 8);
    }
}
