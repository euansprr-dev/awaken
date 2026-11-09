package dev.awaken.hall.client;

import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.client.WeaponDraw;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** How to stand a weapon upright: meshes lie along the item's diagonal, and the Hall shows them point up. */
final class Meshes {
    private Meshes() {}

    /** The mesh, its turn about z, the point to turn it round, the bounds' middle after turning, and its length. */
    record Up(WeaponMesh mesh, float angle, float mx, float my, float mz, float cx, float cy, float cz, float length) {}

    private static final Map<String, Up> UP = new HashMap<>();

    static @Nullable Up up(String id) {
        Up cached = UP.get(id);
        if (cached != null) return cached;
        WeaponMesh m = WeaponDraw.mesh(id);
        if (m == null) return null;
        Up u = measure(m);
        UP.put(id, u);
        return u;
    }

    static void forget() {
        UP.clear();
    }

    private static Up measure(WeaponMesh m) {
        float[] p = m.pos();
        byte[] al = m.along();
        int n = m.vertices(), nh = 0, nt = 0;
        float hx = 0, hy = 0, tx = 0, ty = 0, mx = 0, my = 0, mz = 0;
        for (int i = 0; i < n; i++) {
            float x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            mx += x;
            my += y;
            mz += z;
            float a = (al[i] & 255) / 255F;
            if (a < 0.12F) {
                hx += x;
                hy += y;
                nh++;
            } else if (a > 0.88F) {
                tx += x;
                ty += y;
                nt++;
            }
        }
        mx /= n;
        my /= n;
        mz /= n;
        float angle = nh == 0 || nt == 0 ? 0F : (float) Math.atan2(tx / nt - hx / nh, ty / nt - hy / nh);
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float x = p[i * 3] - mx, y = p[i * 3 + 1] - my, z = p[i * 3 + 2] - mz;
            float rx = c * x - s * y, ry = s * x + c * y;
            x0 = Math.min(x0, rx);
            x1 = Math.max(x1, rx);
            y0 = Math.min(y0, ry);
            y1 = Math.max(y1, ry);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        return new Up(m, angle, mx, my, mz, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, y1 - y0);
    }
}
