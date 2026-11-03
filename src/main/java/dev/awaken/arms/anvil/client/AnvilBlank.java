package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.arms.client.WeaponDraw;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The work as it's made (SPEC §46.3), the weapon's own mesh reshaped corner by corner as it's drawn, so no second model
 * is needed: a square bar drawn out strike by strike into its own blade ({@link AnvilShape#corner}), at A twisted along
 * its length (the hot middle turning, the ends held), and its guard's pieces left off until the hilt's first tap. Drawn
 * in the work's own design frame ({@link AnvilScene.Lay#frame}). A twisted bar is cut into slabs along its length first,
 * so its faces spiral rather than fold; the cuts on an edge two triangles share land on the same points, so nothing opens.
 */
final class AnvilBlank implements WeaponDraw.Corners {
    /** Slabs a twisted blank is cut into from its foot to its point. */
    private static final int SLABS = 24;
    /** A corner's values: design position, design normal, uv, along (0..255). */
    private static final int V = 9;
    private static final float C45 = Mth.SQRT_OF_TWO / 2F;

    private @Nullable WeaponMesh mesh;
    private AnvilShape shape = AnvilShape.FALLBACK;
    private float mid, drawn, twist;
    private int light, cut;
    private boolean noGuard;

    private final float[] tri = new float[3 * V], ca = new float[8 * V], cb = new float[8 * V];
    private final Vector3f p = new Vector3f();
    private int r, g, b, overlay;
    private boolean ours;

    /** This frame's work: its mesh, how it lies (drawn out, twisted), its light, and from where along it it's drawn (0..1). */
    AnvilBlank set(WeaponMesh mesh, AnvilScene.Lay lay, int light, float from) {
        this.mesh = mesh;
        this.shape = lay.shape;
        this.mid = lay.mid;
        this.drawn = Mth.clamp(lay.drawn, 0F, 1F);
        this.twist = lay.twist;
        this.light = light;
        this.cut = Math.round(Math.max(0F, from) * 255);
        // the blade alone: the guard's pieces too wait for the hilt
        this.noGuard = from >= shape.cut[0] - 1e-4F;
        return this;
    }

    @Override
    public void emit(PoseStack.Pose pose, VertexConsumer buffer, int overlay, boolean ours) {
        WeaponMesh mesh = this.mesh;
        if (mesh == null) return;
        int accent = ours ? mesh.accent() : 0xFFFFFF;
        this.r = accent >> 16 & 255;
        this.g = accent >> 8 & 255;
        this.b = accent & 255;
        this.overlay = overlay;
        this.ours = ours;
        float[] pos = mesh.pos(), uv = mesh.uv();
        byte[] n = mesh.normal(), along = mesh.along();
        boolean[] guard = noGuard ? shape.guard : null;
        float step = (shape.tip - shape.foot) / SLABS;
        for (int i = 0, t = 0, count = mesh.vertices(); i < count; i += 3, t++) {
            if (cut > 0 && (along[i] & 255) + (along[i + 1] & 255) + (along[i + 2] & 255) < cut * 3) continue;
            if (guard != null && t < guard.length && guard[t]) continue;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int c = 0; c < 3; c++) {
                int s = i + c, o = c * V;
                AnvilScene.ITEM.transformPosition(pos[s * 3], pos[s * 3 + 1], pos[s * 3 + 2], p);
                tri[o] = p.x;
                tri[o + 1] = p.y;
                tri[o + 2] = p.z;
                // the item's -45° turned back, as ITEM does
                float nx = n[s * 3] / 127F, ny = n[s * 3 + 1] / 127F;
                tri[o + 3] = (nx - ny) * C45;
                tri[o + 4] = (nx + ny) * C45;
                tri[o + 5] = n[s * 3 + 2] / 127F;
                tri[o + 6] = uv[s * 2];
                tri[o + 7] = uv[s * 2 + 1];
                tri[o + 8] = along[s] & 255;
                lo = Math.min(lo, p.y);
                hi = Math.max(hi, p.y);
            }
            int k0 = twist == 0F ? 0 : Mth.floor((lo - shape.foot) / step), k1 = twist == 0F ? 0 : Mth.floor((hi - shape.foot) / step);
            if (k0 == k1) {
                triangle(buffer, pose, tri, 0, V, 2 * V);
                continue;
            }
            for (int k = k0; k <= k1; k++) {
                // the slab's piece: above its floor (none for the lowest) and below its ceiling (none for the highest)
                System.arraycopy(tri, 0, ca, 0, 3 * V);
                int m = 3;
                if (k > k0) m = clip(ca, m, cb, shape.foot + k * step, true);
                else System.arraycopy(ca, 0, cb, 0, m * V);
                if (k < k1) m = clip(cb, m, ca, shape.foot + (k + 1) * step, false);
                else System.arraycopy(cb, 0, ca, 0, m * V);
                for (int j = 1; j + 1 < m; j++) triangle(buffer, pose, ca, 0, j * V, (j + 1) * V);
            }
        }
    }

    /**
     * The polygon {@code in} ({@code n} corners) cut at height {@code at}, the part above it (or below): out, its corners
     * counted. A cut on an edge is made from the edge's lower end, so both triangles sharing it cut it at the same point.
     */
    private static int clip(float[] in, int n, float[] out, float at, boolean above) {
        int m = 0;
        for (int j = 0; j < n; j++) {
            int p = j * V, q = (j + 1 == n ? 0 : j + 1) * V;
            float dp = above ? in[p + 1] - at : at - in[p + 1], dq = above ? in[q + 1] - at : at - in[q + 1];
            if (dp >= 0F) System.arraycopy(in, p, out, m++ * V, V);
            if (dp > 0F && dq < 0F || dp < 0F && dq > 0F) {
                int a = in[p + 1] <= in[q + 1] ? p : q, b = a == p ? q : p;
                float k = (at - in[a + 1]) / (in[b + 1] - in[a + 1]);
                for (int c = 0; c < V; c++) out[m * V + c] = in[a + c] + (in[b + c] - in[a + c]) * k;
                m++;
            }
        }
        return m;
    }

    private void triangle(VertexConsumer buffer, PoseStack.Pose pose, float[] v, int a, int b, int c) {
        corner(buffer, pose, v, a);
        corner(buffer, pose, v, b);
        corner(buffer, pose, v, c);
        if (!ours) corner(buffer, pose, v, c); // vanilla's pipeline draws quads
    }

    private void corner(VertexConsumer buffer, PoseStack.Pose pose, float[] v, int o) {
        float y = v[o + 1];
        shape.corner(v[o], y, v[o + 2], mid, drawn, p);
        float a = shape.turnAt(y, twist);
        shape.turn(p, a, drawn);
        float nx = v[o + 3], nz = v[o + 5];
        if (a != 0F) {
            float c = Mth.cos(a), s = Mth.sin(a), x = nx;
            nx = x * c - nz * s;
            nz = x * s + nz * c;
        }
        buffer.addVertex(pose, p.x, p.y, p.z)
                .setColor(r, g, b, ours ? Math.round(v[o + 8]) : 255)
                .setUv(v[o + 6], v[o + 7])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, v[o + 4], nz);
    }
}
