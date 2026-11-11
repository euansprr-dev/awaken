package dev.awaken.body.ascent.client;

import static dev.awaken.body.ascent.WingForm.A0;
import static dev.awaken.body.ascent.WingForm.BONE;
import static dev.awaken.body.ascent.WingForm.CASC;
import static dev.awaken.body.ascent.WingForm.COV;
import static dev.awaken.body.ascent.WingForm.COV2;
import static dev.awaken.body.ascent.WingForm.FAN_IN;
import static dev.awaken.body.ascent.WingForm.FAN_OUT;
import static dev.awaken.body.ascent.WingForm.G_PRIM;
import static dev.awaken.body.ascent.WingForm.G_SEC;
import static dev.awaken.body.ascent.WingForm.G_TER;
import static dev.awaken.body.ascent.WingForm.P0;
import static dev.awaken.body.ascent.WingForm.P1;
import static dev.awaken.body.ascent.WingForm.P2;
import static dev.awaken.body.ascent.WingForm.PRIM;
import static dev.awaken.body.ascent.WingForm.SEC;
import static dev.awaken.body.ascent.WingForm.SECS;
import static dev.awaken.body.ascent.WingForm.TER;
import static dev.awaken.body.ascent.WingForm.TERS;
import static dev.awaken.body.ascent.WingForm.ease;
import static dev.awaken.body.ascent.WingForm.flap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.body.ascent.WingForm;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * The wings as feathers of light, in boxes (SPEC §62): draws {@link WingForm}'s wing, one to one with the model Euan
 * approved on the plan's page (docs/movement/src/wings.js). Every feather is a slab one texel thick whose point is
 * steps, a raised shaft down its middle: about 230 boxes a wing, no slant and no curve anywhere. Each face carries its
 * own shade in its colour, as the model's do (the wings are light: nothing in the world shades them).
 * <p>
 * Units are texels (1/16 block). The wing's own frame: x out along the span, y toward the head, z off the back. It is
 * drawn in the body part's model space (x to the player's left, y down, z behind); nothing here allocates.
 */
final class WingFeathers {
    private WingFeathers() {}

    /** A rung's colours: root, mid, outer, tip, shaft, the coverts, the smaller coverts, the bones; and its halo. */
    static final int[] LIGHT = {0xF4FDFF, 0xC4ECFF, 0x86CDFF, 0xEEFCFF, 0xFFFFFF, 0xE2F7FF, 0xF6FEFF, 0xEAFAFF, 0x78C4FF};
    static final int[] SKY = {0xF6FBFF, 0xB8DCFF, 0x7FA8FF, 0xF0F6FF, 0xFFFFFF, 0xDCECFF, 0xF4F9FF, 0xE8F2FF, 0x80AAFF};
    static final int[] GOLD = {0xFFFBEA, 0xFFE9A8, 0xFFC85A, 0xFFF7D6, 0xFFFFFF, 0xFFF1C4, 0xFFFBE6, 0xFFF4CF, 0xFFC460};
    private static final int ROOT = 0, MID = 1, OUTER = 2, TIP = 3, SHAFT = 4, COVC = 5, COV2C = 6, SPAR = 7;
    static final int HALO = 8;

    /** Where the wings root, in texels from the body part's pivot (the neck): out to each side, down the back, off it. */
    static final float ROOT_X = 3.2F, ROOT_Y = 3F, ROOT_Z = 4F, PAIR_Z = 0.9F;

    private static final float D = Mth.DEG_TO_RAD;
    /** A face's shade by the way it looks in its box's own frame: +across, -across, +along, -along, +off the back, -toward it. */
    private static final float[] FACE = {0.80F, 0.80F, 1.0F, 0.62F, 0.93F, 0.72F};

    // a wing's frame while it is drawn: the pair's place and roll, the joints, the box's axes
    private static final float[] JX = new float[4], JY = new float[4], JZ = new float[4];
    private static float side, size, rollC, rollS, rootX, rootY, rootZ, alphaNow;
    private static int lodNow;
    private static float fx, fy, fz, nx, ny, nz, wx, wy, wz;

    /**
     * One pair of wings in the pose at {@code o} in {@code p}, its size and roll from {@code pair}, the {@code index}th
     * pair on this back. {@code first}: for your own eyes, only the outer wing (never the bones at the camera).
     */
    static void pair(PoseStack.Pose pose, VertexConsumer vc, float[] p, int o, float[] pair, int index, int[] pal, float alpha, int lod, boolean first) {
        wing(pose, vc, 1, p, o, pair, index, pal, alpha, lod, first);
        wing(pose, vc, -1, p, o, pair, index, pal, alpha, lod, first);
    }

    private static void root(int s, float[] pair, int index) {
        side = s;
        size = pair[0];
        // rolled about the body's front-to-back axis at the root (negative lifts the wing), as the model's group is
        rollC = Mth.cos(-s * pair[1] * D);
        rollS = Mth.sin(-s * pair[1] * D);
        rootX = s * ROOT_X;
        rootY = ROOT_Y;
        rootZ = ROOT_Z + index * PAIR_Z;
    }

    private static void wing(PoseStack.Pose pose, VertexConsumer vc, int s, float[] p, int o, float[] pair, int index, int[] pal, float alpha, int lod,
                             boolean first) {
        root(s, pair, index);
        alphaNow = alpha;
        lodNow = lod;
        float p0 = p[o + P0], p1 = p[o + P1], p2 = p[o + P2], c = p[o + CASC];
        // the bones: rigid, each turned by the flap up to its joint
        JX[0] = JY[0] = JZ[0] = 0;
        for (int i = 0; i < 3; i++) {
            dir(p[o + A0 + i] * D, (i == 0 ? p0 : i == 1 ? p0 + p1 : p0 + p1 + p2) * D);
            JX[i + 1] = JX[i] + fx * BONE[i];
            JY[i + 1] = JY[i] + fy * BONE[i];
            JZ[i + 1] = JZ[i] + fz * BONE[i];
            if (!first) box(pose, vc, JX[i], JY[i], JZ[i], 0, -0.5F, 2, BONE[i] + 1, 2, 0, pal[SPAR]);
        }
        if (!first && lod == 0) {
            // the knuckles at the elbow and the wrist: square to the wing, not to either bone
            fx = 0;
            fy = 1;
            fz = 0;
            nx = 0;
            ny = 0;
            nz = 1;
            wx = 1;
            wy = 0;
            wz = 0;
            for (int i = 1; i <= 2; i++) box(pose, vc, JX[i], JY[i], JZ[i], 0, -1.2F, 2.4F, 2.4F, 2.4F, 0, pal[SPAR]);
        }
        float ter = p[o + TER], sec = p[o + SEC], fanIn = p[o + FAN_IN], fanOut = p[o + FAN_OUT];
        if (!first) {
            for (int i = WingForm.first(G_TER, lod); i < TERS.length; i += WingForm.step(G_TER, lod)) {
                float t = i / (float) (TERS.length - 1), ci = ease((c - 0.05F * t) / 0.6F), sp = 0.12F + 0.86F * t;
                feather(pose, vc, 0, sp, Mth.lerp(ci, -90, ter + (t - 0.5F) * 8), flap(p0, p1, p2, sp), i * 0.16F - 0.4F, Mth.lerp(ci, 0.8F, 1),
                        TERS[i], WingForm.width(G_TER, lod), pal[ROOT], pal[MID], pal[OUTER], pal[TIP], pal[SHAFT]);
            }
        }
        for (int i = first ? 4 : WingForm.first(G_SEC, lod); i < SECS.length; i += WingForm.step(G_SEC, lod)) {
            float t = i / (float) (SECS.length - 1), ci = ease((c - 0.1F - 0.15F * t) / 0.6F), sp = 0.04F + 0.94F * t;
            feather(pose, vc, 1, sp, Mth.lerp(ci, -90, sec + t * (fanIn - sec) * 0.55F), flap(p0, p1, p2, 1 + sp), 0.5F + i * 0.16F, Mth.lerp(ci, 0.8F, 1),
                    SECS[i], WingForm.width(G_SEC, lod), pal[ROOT], pal[MID], pal[OUTER], pal[TIP], pal[SHAFT]);
        }
        for (int i = WingForm.first(G_PRIM, lod); i < PRIM.length; i += WingForm.step(G_PRIM, lod)) {
            float t = i / (float) (PRIM.length - 1), ci = ease((c - 0.25F - 0.3F * t) / 0.45F), sp = 0.08F + 0.92F * t;
            float open = Mth.lerp((float) Math.pow(t, 0.85), fanIn, fanOut);
            feather(pose, vc, 2, sp, Mth.lerp(ci, -97 + 9 * t, open), flap(p0, p1, p2, 2 + sp), 1.9F + i * 0.16F, Mth.lerp(ci, 0.66F, 1), PRIM[i],
                    WingForm.width(G_PRIM, lod), pal[ROOT], pal[MID], pal[OUTER], pal[TIP], pal[SHAFT]);
        }
        if (lod > 0) return;
        for (int row = 0; row < 2; row++) {
            int n = row == 0 ? COV : COV2;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1), ci = ease((c - 0.2F * t) / 0.6F);
                int b = t < 0.3F ? 0 : t < 0.68F ? 1 : 2;
                if (first && b < 2) continue;
                float f = b == 0 ? t / 0.3F : b == 1 ? (t - 0.3F) / 0.38F : (t - 0.68F) / 0.32F;
                float sp = row == 0 ? 0.06F + 0.9F * f : 0.1F + 0.86F * f;
                float base = b == 0 ? ter : b == 1 ? sec : Mth.lerp(f * (row == 0 ? 0.7F : 0.6F), fanIn, fanOut);
                float off = (row == 0 ? 3.6F : 4.7F) + (i % 2) * 0.2F;
                // the larger coverts shade toward the flight feathers' blue at their tips; the smaller stay pale
                if (row == 0) {
                    feather(pose, vc, b, sp, Mth.lerp(ci, -90, base), flap(p0, p1, p2, b + sp), off, 1, WingForm.covert(0, i), 3, pal[COV2C], pal[COVC],
                            pal[COVC], pal[MID], pal[SHAFT]);
                } else {
                    feather(pose, vc, b, sp, Mth.lerp(ci, -90, base), flap(p0, p1, p2, b + sp), off, 1, WingForm.covert(1, i), 3, pal[COV2C], pal[COV2C],
                            pal[COVC], pal[COVC], pal[SHAFT]);
                }
            }
        }
    }

    /** Sets the box's axes (f along, n off the back, w across) for a direction {@code an} in the wing's plane, flapped by {@code ph} (radians). */
    private static void dir(float an, float ph) {
        float c = Mth.cos(-side * ph), s = Mth.sin(-side * ph);
        float x = side * Mth.cos(an), y = Mth.sin(an);
        // turned about the body's long axis: x' = x c, z' = -x s; the normal (0, 0, 1) turns with it
        fx = x * c;
        fy = y;
        fz = -x * s;
        nx = s;
        ny = 0;
        nz = c;
        wx = fy * nz - fz * ny;
        wy = fz * nx - fx * nz;
        wz = fx * ny - fy * nx;
    }

    /** One feather on bone {@code b} at {@code sp} along it, pointing {@code ang} degrees in the wing's plane. */
    private static void feather(PoseStack.Pose pose, VertexConsumer vc, int b, float sp, float ang, float flapDeg, float off, float lenK, int len, float width,
                                int root, int mid, int outer, int tip, int shaft) {
        float px = Mth.lerp(sp, JX[b], JX[b + 1]), py = Mth.lerp(sp, JY[b], JY[b + 1]), pz = Mth.lerp(sp, JZ[b], JZ[b + 1]);
        dir(ang * D, flapDeg * D);
        px += nx * off;
        py += ny * off;
        pz += nz * off;
        int a = Math.max(2, Math.round(len * 0.42F)), bb = Math.max(a + 1, len - 4);
        box(pose, vc, px, py, pz, 0, 0, width, a * lenK, 1, 0, root);
        box(pose, vc, px, py, pz, 0, a * lenK, width, (bb - a) * lenK, 1, 0, mid);
        if (len - 1 > bb) box(pose, vc, px, py, pz, 0, bb * lenK, Math.max(1, width - 1), (len - 1 - bb) * lenK, 1, 0, outer);
        box(pose, vc, px, py, pz, 0, (len - 1) * lenK, Math.max(1, width - 2), lenK, 1, 0, tip);
        if (lodNow == 0) {
            float sl = Math.max(1, len - 4);
            box(pose, vc, px, py, pz, 0, ((len - 4) / 2F + 0.5F - sl / 2F) * lenK, 0.6F, sl * lenK, 0.3F, 0.62F, shaft);
        }
    }

    /**
     * A box in the current axes (w across, f along, n off the back) at (ox, oy, oz): centred on w at {@code cw} and on
     * n at {@code cn}, from {@code v0} along f for {@code len}. Six faces, each shaded as the model's are.
     */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, float ox, float oy, float oz, float cw, float v0, float width, float len, float thick,
                            float cn, int rgb) {
        float hw = width / 2, ht = thick / 2;
        float w0 = cw - hw, w1 = cw + hw, f1 = v0 + len, n0 = cn - ht, n1 = cn + ht;
        face(pose, vc, ox, oy, oz, w1, v0, n0, w1, f1, n0, w1, f1, n1, w1, v0, n1, rgb, FACE[0], wx, wy, wz);
        face(pose, vc, ox, oy, oz, w0, v0, n1, w0, f1, n1, w0, f1, n0, w0, v0, n0, rgb, FACE[1], -wx, -wy, -wz);
        face(pose, vc, ox, oy, oz, w0, f1, n0, w0, f1, n1, w1, f1, n1, w1, f1, n0, rgb, FACE[2], fx, fy, fz);
        face(pose, vc, ox, oy, oz, w0, v0, n1, w0, v0, n0, w1, v0, n0, w1, v0, n1, rgb, FACE[3], -fx, -fy, -fz);
        face(pose, vc, ox, oy, oz, w0, v0, n1, w1, v0, n1, w1, f1, n1, w0, f1, n1, rgb, FACE[4], nx, ny, nz);
        face(pose, vc, ox, oy, oz, w1, v0, n0, w0, v0, n0, w0, f1, n0, w1, f1, n0, rgb, FACE[5], -nx, -ny, -nz);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer vc, float ox, float oy, float oz, float aw, float af, float an, float bw, float bf, float bn,
                             float cw, float cf, float cn, float dw, float df, float dn, int rgb, float shade, float qx, float qy, float qz) {
        int r = (int) (((rgb >> 16) & 255) * shade), g = (int) (((rgb >> 8) & 255) * shade), b = (int) ((rgb & 255) * shade);
        int col = (int) (255 * alphaNow) << 24 | r << 16 | g << 8 | b;
        vert(pose, vc, ox, oy, oz, aw, af, an, col, 0, 0, qx, qy, qz);
        vert(pose, vc, ox, oy, oz, bw, bf, bn, col, 0, 1, qx, qy, qz);
        vert(pose, vc, ox, oy, oz, cw, cf, cn, col, 1, 1, qx, qy, qz);
        vert(pose, vc, ox, oy, oz, dw, df, dn, col, 1, 0, qx, qy, qz);
    }

    /** A point (w, f, n) off (ox, oy, oz) in the wing's frame, out to the body part's model space, and written. */
    private static void vert(PoseStack.Pose pose, VertexConsumer vc, float ox, float oy, float oz, float w, float f, float n, int col, float u, float v,
                             float qx, float qy, float qz) {
        float x = ox + wx * w + fx * f + nx * n, y = oy + wy * w + fy * f + ny * n, z = oz + wz * w + fz * f + nz * n;
        // the pair's size and roll about its root, then the root's place; model space is x left, y down, z behind
        float rx = (x * rollC - y * rollS) * size, ry = (x * rollS + y * rollC) * size, rz = z * size;
        float tx = qx * rollC - qy * rollS, ty = qx * rollS + qy * rollC;
        vc.addVertex(pose, (rootX + rx) / 16F, (rootY - ry) / 16F, (rootZ + rz) / 16F).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, tx, -ty, qz);
    }

    private static void upright(float x, float y, float z, float alpha) {
        side = 1;
        size = 1;
        rollC = 1;
        rollS = 0;
        rootX = x;
        rootY = y;
        rootZ = z;
        alphaNow = alpha;
        lodNow = 0;
    }

    /**
     * The mark between the shoulder blades (worn from the day the wings are given): a dark plate and a lit core that
     * breathes ({@code pulse} about 1).
     */
    static void mount(PoseStack.Pose pose, VertexConsumer vc, int core, float alpha, float pulse) {
        upright(0, 3.5F, 2, alpha);
        // across the back, up it, off it
        wx = 1;
        wy = 0;
        wz = 0;
        fx = 0;
        fy = 1;
        fz = 0;
        nx = 0;
        ny = 0;
        nz = 1;
        box(pose, vc, 0, 0, 0, 0, -2.5F, 7, 5, 2, 1, 0x1C222B);
        float c = 0.8F * pulse;
        box(pose, vc, 0, 0, 0, 0, -c, 2 * c, 2 * c, 0.6F, 2.2F, core);
    }

    /** The Sovereign's halo: fourteen small boxes on a ring over the head, turned by {@code turn} radians. */
    static void ring(PoseStack.Pose pose, VertexConsumer vc, float turn, float alpha) {
        upright(0, -14.5F, 0, alpha);
        for (int i = 0; i < 14; i++) {
            float a = turn + i / 14F * Mth.TWO_PI, cs = Mth.cos(a), sn = Mth.sin(a);
            // each box lies along the ring: across is the tangent, along is up, off is out from the middle
            wx = -sn;
            wy = 0;
            wz = cs;
            fx = 0;
            fy = 1;
            fz = 0;
            nx = cs;
            ny = 0;
            nz = sn;
            box(pose, vc, cs * 7, 0, sn * 7, 0, -0.5F, 2, 1, 1.4F, 0, (i & 1) == 1 ? 0xFFE9A8 : 0xFFFBEA);
        }
    }

    // ---- the soft light round the bones (additive; a disc a bone, facing the eye) --------------------------------------

    private static final Matrix3f INV = new Matrix3f();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    /**
     * Which way is right and up for the eye, in the model space about to be drawn: the camera's own axes taken back
     * through the pose's turn (its normal matrix is that turn).
     */
    static void faceEye(PoseStack.Pose pose, Quaternionfc camera) {
        INV.set(pose.normal()).invert();
        camera.transform(1, 0, 0, RIGHT);
        camera.transform(0, 1, 0, UP);
        INV.transform(RIGHT).normalize();
        INV.transform(UP).normalize();
    }

    /**
     * The halos of one pair in the pose at {@code o}: three soft discs along each wing, behind the bones' joints, sized
     * and lit with how open the wing is ({@code glow} 0..1 by what the wings are doing).
     */
    static void halos(PoseStack.Pose pose, VertexConsumer vc, float[] p, int o, float[] pair, int index, int rgb, float glow, float alpha) {
        float c = p[o + CASC], p0 = p[o + P0], p1 = p[o + P1], p2 = p[o + P2];
        for (int s = 1; s >= -1; s -= 2) {
            root(s, pair, index);
            float jx = 0, jy = 0, jz = 0;
            for (int i = 0; i < 3; i++) {
                dir(p[o + A0 + i] * D, (i == 0 ? p0 : i == 1 ? p0 + p1 : p0 + p1 + p2) * D);
                jx += fx * BONE[i];
                jy += fy * BONE[i];
                jz += fz * BONE[i];
                float y = jy - 7 * c;
                float rx = (jx * rollC - y * rollS) * size, ry = (jx * rollS + y * rollC) * size, rz = jz * size;
                float half = (30 + i * 10) * (0.5F + 0.5F * c) * size / 32F;
                disc(pose, vc, (rootX + rx) / 16F, (rootY - ry) / 16F, (rootZ + rz) / 16F, half, half * 1.25F, rgb,
                        (0.1F + 0.14F * glow) * (0.3F + 0.7F * c) * alpha);
            }
        }
    }

    /** The glow in the Sovereign's ring. */
    static void ringGlow(PoseStack.Pose pose, VertexConsumer vc, float alpha) {
        disc(pose, vc, 0, -14.5F / 16F, 0, 15 / 16F, 6 / 16F, GOLD[HALO], 0.35F * alpha);
    }

    /** A soft disc facing the eye, as the model's halo is painted: bright in the middle, a third of that a third of the way out, nothing at the rim. */
    private static void disc(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float rw, float rh, int rgb, float alpha) {
        int mid = (int) (255 * Mth.clamp(alpha * 0.9F, 0, 1)) << 24 | rgb, in = (int) (255 * Mth.clamp(alpha * 0.32F, 0, 1)) << 24 | rgb;
        for (int i = 0; i < 8; i++) {
            float a0 = i / 8F * Mth.TWO_PI, a1 = (i + 1) / 8F * Mth.TWO_PI;
            // the middle: a sliver of a quad (two of its corners at the centre)
            vc.addVertex(pose, x, y, z).setColor(mid);
            vc.addVertex(pose, x, y, z).setColor(mid);
            rim(pose, vc, x, y, z, rw * 0.35F, rh * 0.35F, a0, in);
            rim(pose, vc, x, y, z, rw * 0.35F, rh * 0.35F, a1, in);
            // the falloff out to the rim
            rim(pose, vc, x, y, z, rw * 0.35F, rh * 0.35F, a1, in);
            rim(pose, vc, x, y, z, rw * 0.35F, rh * 0.35F, a0, in);
            rim(pose, vc, x, y, z, rw, rh, a0, rgb);
            rim(pose, vc, x, y, z, rw, rh, a1, rgb);
        }
    }

    private static void rim(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float rw, float rh, float a, int col) {
        float c = Mth.cos(a) * rw, s = Mth.sin(a) * rh;
        vc.addVertex(pose, x + RIGHT.x * c + UP.x * s, y + RIGHT.y * c + UP.y * s, z + RIGHT.z * c + UP.z * s).setColor(col);
    }

    /**
     * Where a wing's tip is in the body part's model space (blocks), for the motes it sheds: the end of the third bone
     * and half its longest feather on, in the pose at {@code o}.
     */
    static void tip(float[] p, int o, int s, float[] pair, int index, float[] out) {
        root(s, pair, index);
        float p0 = p[o + P0], p1 = p[o + P1], p2 = p[o + P2];
        float jx = 0, jy = 0, jz = 0;
        for (int i = 0; i < 3; i++) {
            dir(p[o + A0 + i] * D, (i == 0 ? p0 : i == 1 ? p0 + p1 : p0 + p1 + p2) * D);
            jx += fx * BONE[i];
            jy += fy * BONE[i];
            jz += fz * BONE[i];
        }
        jx += side * 8;
        float rx = (jx * rollC - jy * rollS) * size, ry = (jx * rollS + jy * rollC) * size, rz = jz * size;
        out[0] = (rootX + rx) / 16F;
        out[1] = (rootY - ry) / 16F;
        out[2] = (rootZ + rz) / 16F;
    }
}
