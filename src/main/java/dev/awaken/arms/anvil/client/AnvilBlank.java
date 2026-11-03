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
 * its length (the hot middle turning, the ends held). Until the hilt's first tap it's the blade alone, cut clean across at
 * its foot and capped there, its guard's pieces left with the hilt; while it's still mostly bar a solid core fills it, so
 * a head's or a fork's insides squared onto the bar never show as holes. Drawn in the work's own design frame
 * ({@link AnvilScene.Lay#frame}). A twisted bar is cut into slabs along its length first, so its faces spiral rather than
 * fold; the cuts on an edge two triangles share land on the same points, so nothing opens.
 */
final class AnvilBlank implements WeaponDraw.Corners {
    /** Slabs a twisted blank is cut into from its foot to its point. */
    private static final int SLABS = 24;
    /** A corner's values: design position, design normal, uv, along (0..255). */
    static final int V = 9;
    private static final float C45 = Mth.SQRT_OF_TWO / 2F;
    /** The core's side, of the bar's (just inside the mesh's own faces), and the drawn-out span it shrinks away over. */
    private static final float CORE = 0.97F, CORE_FROM = 0.08F, CORE_TO = 0.3F;
    /** The core's square: its edges round it as signs across and through, and each side's outward normal. */
    private static final float[] SX = {-1F, 1F, 1F, -1F}, SZ = {-1F, -1F, 1F, 1F}, NX = {0F, 1F, 0F, -1F}, NZ = {-1F, 0F, 1F, 0F};

    private @Nullable WeaponMesh mesh;
    private AnvilShape shape = AnvilShape.FALLBACK;
    private float mid, drawn, twist;
    private int light, cut;
    private boolean blade;

    private final float[] tri = new float[3 * V], cr = new float[4 * V], pt = new float[3 * V], ca = new float[8 * V], cb = new float[8 * V];
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
        this.blade = from >= shape.cut[0] - 1e-4F;
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
        // the blade alone: the blank's own triangles, cut at its foot
        boolean[] blank = blade ? shape.blank : null;
        float foot = shape.foot;
        for (int i = 0, t = 0, count = mesh.vertices(); i < count; i += 3, t++) {
            if (blank != null) {
                if (t >= blank.length || !blank[t]) continue;
            } else if (cut > 0 && (along[i] & 255) + (along[i + 1] & 255) + (along[i + 2] & 255) < cut * 3) continue;
            float lo = Float.MAX_VALUE;
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
            }
            if (blank == null || lo >= foot) {
                piece(buffer, pose, tri);
                continue;
            }
            int m = clip(tri, 3, cr, foot, true);
            for (int j = 1; j + 1 < m; j++) {
                System.arraycopy(cr, 0, pt, 0, V);
                System.arraycopy(cr, j * V, pt, V, 2 * V);
                piece(buffer, pose, pt);
            }
        }
        if (blank != null) {
            float[] cap = shape.cap;
            for (int o = 0; o < cap.length; o += 3 * V) {
                System.arraycopy(cap, o, pt, 0, 3 * V);
                piece(buffer, pose, pt);
            }
        }
        if (blade) core(buffer, pose);
    }

    /** A triangle of the work ({@code t}, three corners), cut into the twist's slabs where it spans more than one. */
    private void piece(VertexConsumer buffer, PoseStack.Pose pose, float[] t) {
        float lo = Math.min(t[1], Math.min(t[V + 1], t[2 * V + 1])), hi = Math.max(t[1], Math.max(t[V + 1], t[2 * V + 1]));
        float step = (shape.tip - shape.foot) / SLABS;
        int k0 = twist == 0F ? 0 : Mth.floor((lo - shape.foot) / step), k1 = twist == 0F ? 0 : Mth.floor((hi - shape.foot) / step);
        if (k0 == k1) {
            triangle(buffer, pose, t, 0, V, 2 * V);
            return;
        }
        for (int k = k0; k <= k1; k++) {
            // the slab's piece: above its floor (none for the lowest) and below its ceiling (none for the highest)
            System.arraycopy(t, 0, ca, 0, 3 * V);
            int m = 3;
            if (k > k0) m = clip(ca, m, cb, shape.foot + k * step, true);
            else System.arraycopy(ca, 0, cb, 0, m * V);
            if (k < k1) m = clip(cb, m, ca, shape.foot + (k + 1) * step, false);
            else System.arraycopy(cb, 0, ca, 0, m * V);
            for (int j = 1; j + 1 < m; j++) triangle(buffer, pose, ca, 0, j * V, (j + 1) * V);
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
        vertex(buffer, pose, y, v[o + 3], v[o + 4], v[o + 5], v[o + 6], v[o + 7], v[o + 8]);
    }

    /** {@link #p}, drawn out, turned by the twist at design height y (with its normal) and written. */
    private void vertex(VertexConsumer buffer, PoseStack.Pose pose, float y, float nx, float ny, float nz, float u, float v, float along) {
        float a = shape.turnAt(y, twist);
        shape.turn(p, a, drawn);
        if (a != 0F) {
            float c = Mth.cos(a), s = Mth.sin(a), x = nx;
            nx = x * c - nz * s;
            nz = x * s + nz * c;
        }
        buffer.addVertex(pose, p.x, p.y, p.z)
                .setColor(r, g, b, ours ? Math.round(along) : 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // ---- the core ----------------------------------------------------------------------------------------------------

    /**
     * The bar's solid core, just inside its faces: a head's or a fork's insides squared onto the bar leave gaps between
     * its faces, and this fills them. It shrinks away as the bar is drawn out, gone by a third of the way. One texel's
     * colour, the blade's middle; its heat front runs up it as the blade's does.
     */
    private void core(VertexConsumer buffer, PoseStack.Pose pose) {
        float k = Mth.clamp((drawn - CORE_FROM) / (CORE_TO - CORE_FROM), 0F, 1F);
        float s = shape.side * CORE * (1F - k * k * (3F - 2F * k));
        if (s < 0.05F) return;
        float y0 = shape.foot + 0.05F, y1 = shape.tip - 0.05F;
        int slabs = twist == 0F ? 1 : SLABS;
        for (int j = 0; j < slabs; j++) {
            float ya = Mth.lerp(j / (float) slabs, y0, y1), yb = Mth.lerp((j + 1) / (float) slabs, y0, y1);
            // round the four sides, each from one edge of the square to the next
            for (int f = 0; f < 4; f++) {
                int e = (f + 1) & 3;
                quad(buffer, pose, s, SX[e], SZ[e], ya, SX[f], SZ[f], ya, SX[f], SZ[f], yb, SX[e], SZ[e], yb, NX[f], 0F, NZ[f]);
            }
        }
        quad(buffer, pose, s, -1F, -1F, y0, 1F, -1F, y0, 1F, 1F, y0, -1F, 1F, y0, 0F, -1F, 0F);
        quad(buffer, pose, s, -1F, 1F, y1, 1F, 1F, y1, 1F, -1F, y1, -1F, -1F, y1, 0F, 1F, 0F);
    }

    /** One face of the core: four corners as signs across and through at design heights, wound to face out. */
    private void quad(VertexConsumer buffer, PoseStack.Pose pose, float s, float x0, float z0, float y0, float x1, float z1, float y1,
                      float x2, float z2, float y2, float x3, float z3, float y3, float nx, float ny, float nz) {
        coreCorner(buffer, pose, s, x0, z0, y0, nx, ny, nz);
        coreCorner(buffer, pose, s, x1, z1, y1, nx, ny, nz);
        coreCorner(buffer, pose, s, x2, z2, y2, nx, ny, nz);
        if (ours) {
            coreCorner(buffer, pose, s, x0, z0, y0, nx, ny, nz);
            coreCorner(buffer, pose, s, x2, z2, y2, nx, ny, nz);
        }
        coreCorner(buffer, pose, s, x3, z3, y3, nx, ny, nz);
    }

    private void coreCorner(VertexConsumer buffer, PoseStack.Pose pose, float s, float sx, float sz, float y, float nx, float ny, float nz) {
        float ax = Mth.lerp(drawn, shape.barX, shape.midX), az = Mth.lerp(drawn, shape.barZ, shape.midZ);
        p.set(ax + sx * s, mid + (y - mid) * Mth.lerp(drawn, AnvilShape.BAR_LONG, 1F), az + sz * s);
        float along = Mth.lerp((y - shape.foot) / (shape.tip - shape.foot), shape.footAlong, 255F);
        vertex(buffer, pose, y, nx, ny, nz, shape.coreU, shape.coreV, along);
    }
}
