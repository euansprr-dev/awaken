package dev.awaken.opening.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The opening's light in the world, drawn camera-relative as additive meshes (vanilla's lightning render type, so Lens
 * blooms them): the column falling into Choir Square and standing there through the run (visible from anywhere in the
 * city), the shockwaves that cross the roofs (the light landing, the pulse that drops the Bell Bridge, the arrival's
 * bursts), the portal blooming at the arrival, the Runner's eye on a hold, and the Assessment Lens's shard with its scan.
 * Fixed pools; a frame allocates only a few small vectors while something is drawn.
 */
final class OpeningFx {
    private OpeningFx() {}

    private static @Nullable RenderType add, glass;
    private static double cx, cy, cz;
    private static float pt;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), LOOK = new Vector3f();

    /** The world's top, where the column comes from. */
    private static final double TOP = 318;

    // ---- the column ---------------------------------------------------------------------------------------------------

    /** Where the light stands (the gate's place in the square), whether it's up, when it fell, how bright it beats. */
    static Vec3 column = Vec3.ZERO;
    static boolean columnOn;
    static long fellAt = -1000;
    /** A momentary surge (a toll, a tremor, the pulse), decaying. */
    static float surge;
    /** The arrival: the portal's bloom (0..1) and the pull (0..1). */
    static float bloom, bloomTarget, pull;

    static void fall(Vec3 at) {
        column = at;
        columnOn = true;
        fellAt = OpeningClient.ticks();
        ring(at.add(0, 0.6, 0), 0F, 2.2F, 150F, 0xFF9ED2FF, 60);
    }

    // ---- rings: flat shockwaves across the roofs ----------------------------------------------------------------------

    private static final int RINGS = 12;
    private static final double[] rx = new double[RINGS], ry = new double[RINGS], rz = new double[RINGS];
    private static final float[] r0 = new float[RINGS], rSpeed = new float[RINGS], rMax = new float[RINGS];
    private static final int[] rColour = new int[RINGS], rLife = new int[RINGS];
    private static final long[] rFrom = new long[RINGS];

    /** A ring of light widening from a point: radius r0, speed (blocks a tick), out to rMax, over life ticks. */
    static void ring(Vec3 at, float start, float speed, float max, int argb, int life) {
        int best = 0;
        for (int i = 0; i < RINGS; i++) {
            if (rLife[i] <= 0 || OpeningClient.ticks() - rFrom[i] > rLife[i]) {
                best = i;
                break;
            }
            if (rFrom[i] < rFrom[best]) best = i;
        }
        rx[best] = at.x;
        ry[best] = at.y;
        rz[best] = at.z;
        r0[best] = start;
        rSpeed[best] = speed;
        rMax[best] = max;
        rColour[best] = argb;
        rLife[best] = life;
        rFrom[best] = OpeningClient.ticks();
    }

    // ---- the Runner's eye ------------------------------------------------------------------------------------------

    static @Nullable Vec3 eyeAt;
    static long eyeFrom = -1000;
    static @Nullable String glyph;

    static void eye(Vec3 at, @Nullable String key) {
        eyeAt = at;
        eyeFrom = OpeningClient.ticks();
        glyph = key;
    }

    // ---- the Assessment Lens ----------------------------------------------------------------------------------------

    static @Nullable Vec3 lens;
    static long scanFrom = -1000;
    static float lensGlow;

    static void reset() {
        columnOn = false;
        surge = 0;
        bloom = bloomTarget = pull = 0;
        for (int i = 0; i < RINGS; i++) rLife[i] = 0;
        eyeAt = null;
        glyph = null;
        lens = null;
        lensGlow = 0;
    }

    static void tick() {
        surge = Math.max(0, surge * 0.9F - 0.004F);
        bloom += (bloomTarget - bloom) * 0.06F;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    static void submit(SubmitCustomGeometryEvent e) {
        boolean any = columnOn || lens != null || eyeAt != null && OpeningClient.ticks() - eyeFrom < 60 || ringsLive();
        if (!any) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        LOOK.set(0, 0, -1).rotate(CAM);
        if (add == null) add = RenderTypes.lightning();
        if (glass == null) glass = RenderTypes.debugQuads();
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        // the column's body: translucent and unfogged, so it reads against a bright dawn sky from across the city
        // (additive light vanishes against the sky, and the lightning type fades with the fog)
        if (columnOn) out.submitCustomGeometry(ps, glass, (pose, vc) -> {
            try {
                columnBody(vc, OpeningClient.ticks() + pt);
            } catch (RuntimeException ex) {
                dev.awaken.Awaken.LOG.error("[opening] fx failed", ex);
            }
        });
        out.submitCustomGeometry(ps, add, (pose, vc) -> {
            try {
                draw(vc);
            } catch (RuntimeException ex) {
                dev.awaken.Awaken.LOG.error("[opening] fx failed", ex);
            }
        });
        if (glyph != null && eyeAt != null) glyphText(ps, out, mc);
    }

    private static boolean ringsLive() {
        long now = OpeningClient.ticks();
        for (int i = 0; i < RINGS; i++) if (rLife[i] > 0 && now - rFrom[i] <= rLife[i]) return true;
        return false;
    }

    private static void draw(VertexConsumer vc) {
        long now = OpeningClient.ticks();
        float t = now + pt;
        if (columnOn) column(vc, t);
        for (int i = 0; i < RINGS; i++) {
            if (rLife[i] <= 0) continue;
            float age = t - rFrom[i];
            if (age < 0 || age > rLife[i]) continue;
            float r = Math.min(rMax[i], r0[i] + rSpeed[i] * age);
            float a = 1 - age / rLife[i];
            int argb = scaleAlpha(rColour[i], a * a);
            flatRing(vc, rx[i], ry[i], rz[i], r, Math.max(0.6F, r * 0.035F), argb);
        }
        if (eyeAt != null) runnersEye(vc, t);
        if (lens != null) lensShard(vc, t);
    }

    /** The column: a white core, a blue glow and a wide haze, from the sky to the square, beating slowly. */
    private static void column(VertexConsumer vc, float t) {
        float since = t - fellAt;
        double baseY = column.y;
        // falling: the bottom races down from the sky in eight ticks
        double bottom = since < 8 ? TOP - (TOP - baseY) * Mth.clamp(since / 8F, 0, 1) : baseY;
        float beat = 0.82F + 0.18F * Mth.sin(t * 0.13F) + surge + bloom * 0.8F;
        float land = since >= 8 && since < 30 ? 1 - (since - 8) / 22F : 0;
        // the column widens a little with distance so it reads from the far side of the city
        double d = Math.sqrt((column.x - cx) * (column.x - cx) + (column.z - cz) * (column.z - cz));
        float far = (float) Mth.clamp(d / 120.0, 1.0, 2.6);
        Vec3 a = new Vec3(column.x, bottom, column.z), b = new Vec3(column.x, TOP, column.z);
        beam(vc, a, b, (2.4F + land * 6 + bloom * 4) * far * 0.7F, argb(Math.min(1F, 0.85F * beat), 0xF4FAFF));
        beam(vc, a, b, (8F + land * 14 + bloom * 9) * far, argb(Math.min(0.6F, 0.30F * beat), 0x8FC8FF));
        beam(vc, a, b, (26F + bloom * 14) * far, argb(Math.min(0.12F, 0.07F * beat), 0x6FA8FF));
        if (since >= 8) {
            // where it meets the ground: a pool of light and a flare
            float pool = 4.5F + 1.2F * Mth.sin(t * 0.21F) + land * 10 + bloom * 9;
            disc(vc, column.x, baseY + 0.08, column.z, pool, argb(Math.min(1F, 0.5F * beat), 0xCFE8FF));
            flare(vc, column.x, baseY + 1.2, column.z, 3.5F + land * 14 + bloom * 6, argb(Math.min(0.8F, 0.5F * beat + land), 0xFFFFFF));
            // motes rising along it
            for (int i = 0; i < 26; i++) {
                float k = (i * 0.618034F + t * 0.006F * (1 + (i % 3))) % 1F;
                double y = baseY + k * 90;
                double ang = i * 2.39996 + t * 0.03;
                double rr = 1.2 + (i % 5) * 0.7 + bloom * 3;
                flare(vc, column.x + Math.cos(ang) * rr, y, column.z + Math.sin(ang) * rr, 0.25F + (i % 4) * 0.08F,
                        argb(0.7F * (1 - k), 0xDDEEFF));
            }
        }
    }

    /** The column's body (translucent pass): a pale core and a wide blue glow, soft at the edges, faded at the top. */
    private static void columnBody(VertexConsumer vc, float t) {
        float since = t - fellAt;
        double baseY = column.y;
        double bottom = since < 8 ? TOP - (TOP - baseY) * Mth.clamp(since / 8F, 0, 1) : baseY;
        float beat = 0.85F + 0.15F * Mth.sin(t * 0.13F) + surge * 0.6F + bloom * 0.5F;
        double d = Math.sqrt((column.x - cx) * (column.x - cx) + (column.z - cz) * (column.z - cz));
        float far = (float) Mth.clamp(d / 120.0, 1.0, 2.6);
        // camera-facing across the column
        double vx = column.x - cx, vz = column.z - cz;
        double vl = Math.sqrt(vx * vx + vz * vz);
        double nx = vl < 1e-6 ? 1 : -vz / vl, nz = vl < 1e-6 ? 0 : vx / vl;
        ribbon(vc, bottom, nx, nz, (3.0F + bloom * 3) * far, argb(Math.min(0.9F, 0.62F * beat), 0xF0F8FF));
        ribbon(vc, bottom, nx, nz, (11F + bloom * 7) * far, argb(Math.min(0.45F, 0.2F * beat), 0xA6D2FF));
    }

    /** A vertical ribbon on the column, bright on its axis and clear at its edges, thinning toward the sky. */
    private static void ribbon(VertexConsumer vc, double bottom, double nx, double nz, float width, int argb) {
        double h = width * 0.5;
        int r = rim(argb);
        int top = scaleAlpha(argb, 0.35F);
        double y0 = bottom, y1 = TOP;
        // two halves: centre to each edge
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double ex = nx * h * sgn, ez = nz * h * sgn;
            vt(vc, column.x, y0, column.z, argb);
            vt(vc, column.x, y1, column.z, top);
            vt(vc, column.x + ex, y1, column.z + ez, rim(top));
            vt(vc, column.x + ex, y0, column.z + ez, r);
        }
    }

    /** Single winding (the translucent pass draws both faces itself). */
    private static void vt(VertexConsumer vc, double x, double y, double z, int argb) {
        vc.addVertex((float) (x - cx), (float) (y - cy), (float) (z - cz)).setColor(argb);
    }

    /** The bronze pulse on the next hold: twice, soft, then gone (2.5 s); the key glyph floats over it. */
    private static void runnersEye(VertexConsumer vc, float t) {
        float age = t - eyeFrom;
        if (age > 60 || eyeAt == null) return;
        float pulse = Mth.sin(Math.min(1, age / 60F) * Mth.PI * 2.5F);
        float a = Math.max(0, pulse) * Mth.clamp((60 - age) / 12F, 0, 1);
        Vec3 p = eyeAt;
        flare(vc, p.x, p.y, p.z, 0.9F + 0.6F * a, argb(0.9F * a, 0xE0B070));
        flatRing(vc, p.x, p.y - 0.45, p.z, 0.6F + age * 0.03F, 0.08F, argb(0.8F * a, 0xE0B070));
    }

    private static void glyphText(PoseStack ps, SubmitNodeCollector out, Minecraft mc) {
        float age = OpeningClient.ticks() + pt - eyeFrom;
        if (age > 90 || eyeAt == null || glyph == null) return;
        float a = Mth.clamp(age / 8F, 0, 1) * Mth.clamp((90 - age) / 15F, 0, 1);
        Font font = mc.font;
        String text = "[" + glyph.toUpperCase(java.util.Locale.ROOT) + "]";
        float scale = 0.03F;
        ps.pushPose();
        ps.translate(eyeAt.x - cx, eyeAt.y + 0.9 - cy, eyeAt.z - cz);
        ps.rotate(CAM); // a name tag's billboard
        ps.scale(scale, -scale, scale);
        int ia = (int) (a * 255);
        out.submitText(ps, -font.width(text) / 2F, 0, Component.literal(text).getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH,
                LightCoordsUtil.FULL_BRIGHT, (ia << 24) | 0xF2DDB0, (ia / 3) << 24, 0);
        ps.popPose();
    }

    /** The shard of the Choir over its pedestal: a turning octahedron of light; the scan sweeps head to foot. */
    private static void lensShard(VertexConsumer vc, float t) {
        Vec3 c = lens;
        if (c == null) return;
        double bob = Math.sin(t * 0.07) * 0.08;
        float glow = 0.55F + lensGlow * 0.45F;
        double s = 0.42;
        double ang = t * 0.04;
        Vec3 top = c.add(0, s * 1.6 + bob, 0), bot = c.add(0, -s * 1.6 + bob, 0);
        Vec3[] ring = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = ang + i * Math.PI / 2;
            ring[i] = c.add(Math.cos(a) * s, bob, Math.sin(a) * s);
        }
        int face = argb(0.55F * glow, 0xB8D8FF), edge = argb(0.9F * glow, 0xEAF4FF);
        for (int i = 0; i < 4; i++) {
            Vec3 r1 = ring[i], r2 = ring[(i + 1) % 4];
            tri(vc, top, r1, r2, face);
            tri(vc, bot, r2, r1, face);
            beam(vc, top, r1, 0.03F, edge);
            beam(vc, bot, r1, 0.03F, edge);
            beam(vc, r1, r2, 0.03F, edge);
        }
        flare(vc, c.x, c.y + bob, c.z, 1.4F + lensGlow * 2.5F, argb(0.45F * glow, 0x9ED2FF));
        // the scan: a ring of light sweeping down over whoever stands to be read, twice
        float age = t - scanFrom;
        if (age >= 0 && age < 90) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                float k = (age % 45F) / 45F;
                double py = mc.player.getY() + 2.0 - k * 2.1;
                double px = Mth.lerp(pt, mc.player.xo, mc.player.getX()), pz = Mth.lerp(pt, mc.player.zo, mc.player.getZ());
                flatRing(vc, px, py, pz, 0.75F, 0.07F, argb(0.85F * (1 - Math.abs(k - 0.5F)), 0xBFE3FF));
                beam(vc, c.add(0, bob, 0), new Vec3(px, py, pz), 0.05F, argb(0.35F, 0xBFE3FF));
            }
        }
    }

    // ---- primitives -------------------------------------------------------------------------------------------------

    private static final float[] QX = new float[4], QY = new float[4], QZ = new float[4];
    private static final int[] QC = new int[4];
    private static int qn;

    /** Quads in both windings: they read from above, below and behind. */
    private static void v(VertexConsumer vc, double x, double y, double z, int argb) {
        QX[qn] = (float) (x - cx);
        QY[qn] = (float) (y - cy);
        QZ[qn] = (float) (z - cz);
        QC[qn] = argb;
        if (++qn < 4) return;
        qn = 0;
        for (int i = 0; i < 4; i++) vc.addVertex(QX[i], QY[i], QZ[i]).setColor(QC[i]);
        for (int i = 3; i >= 0; i--) vc.addVertex(QX[i], QY[i], QZ[i]).setColor(QC[i]);
    }

    private static void tri(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, int argb) {
        v(vc, a.x, a.y, a.z, argb);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, c.x, c.y, c.z, argb);
        v(vc, c.x, c.y, c.z, argb);
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int scaleAlpha(int argb, float k) {
        int a = (int) ((argb >>> 24) * Mth.clamp(k, 0, 1));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int rim(int argb) {
        return argb & 0x00FFFFFF;
    }

    /** A ribbon between two points turned to the camera, bright on its line, nothing at its edges. */
    private static void beam(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        if (width <= 0.001F || (argb >>> 24) == 0) return;
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        // the view toward the beam's nearest point (a long column: the part in front of the camera)
        double len2 = dx * dx + dy * dy + dz * dz;
        double k = len2 < 1e-9 ? 0 : Mth.clamp(((cx - a.x) * dx + (cy - a.y) * dy + (cz - a.z) * dz) / len2, 0, 1);
        double vx = a.x + dx * k - cx, vy = a.y + dy * k - cy, vz = a.z + dz * k - cz;
        double nx = dy * vz - dz * vy, ny = dz * vx - dx * vz, nz = dx * vy - dy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9) return;
        double s = width * 0.5 / nl;
        nx *= s;
        ny *= s;
        nz *= s;
        int r = rim(argb);
        v(vc, a.x, a.y, a.z, argb);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, b.x + nx, b.y + ny, b.z + nz, r);
        v(vc, a.x + nx, a.y + ny, a.z + nz, r);
        v(vc, a.x - nx, a.y - ny, a.z - nz, r);
        v(vc, b.x - nx, b.y - ny, b.z - nz, r);
        v(vc, b.x, b.y, b.z, argb);
        v(vc, a.x, a.y, a.z, argb);
    }

    /** A soft round glow facing the camera. */
    private static void flare(VertexConsumer vc, double px, double py, double pz, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rr = rim(argb);
        int n = r < 1.0F ? 6 : r < 4F ? 10 : 14;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            v(vc, px, py, pz, argb);
            v(vc, px, py, pz, argb);
            v(vc, px + RIGHT.x * c1 + UP.x * s1, py + RIGHT.y * c1 + UP.y * s1, pz + RIGHT.z * c1 + UP.z * s1, rr);
            v(vc, px + RIGHT.x * c0 + UP.x * s0, py + RIGHT.y * c0 + UP.y * s0, pz + RIGHT.z * c0 + UP.z * s0, rr);
        }
    }

    /** A flat glowing disc on the ground (bright middle). */
    private static void disc(VertexConsumer vc, double px, double py, double pz, float r, int argb) {
        int rr = rim(argb);
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            v(vc, px, py, pz, argb);
            v(vc, px, py, pz, argb);
            v(vc, px + Math.cos(a1) * r, py, pz + Math.sin(a1) * r, rr);
            v(vc, px + Math.cos(a0) * r, py, pz + Math.sin(a0) * r, rr);
        }
    }

    /** A flat ring (a shockwave): bright on its circle, soft either side. */
    private static void flatRing(VertexConsumer vc, double px, double py, double pz, float r, float half, int argb) {
        if (r <= 0.01F || (argb >>> 24) == 0) return;
        int rr = rim(argb);
        int n = Math.max(24, Math.min(160, (int) (r * 2)));
        float ri = Math.max(0, r - half), ro = r + half;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            v(vc, px + c0 * ri, py, pz + s0 * ri, rr);
            v(vc, px + c1 * ri, py, pz + s1 * ri, rr);
            v(vc, px + c1 * r, py, pz + s1 * r, argb);
            v(vc, px + c0 * r, py, pz + s0 * r, argb);
            v(vc, px + c0 * r, py, pz + s0 * r, argb);
            v(vc, px + c1 * r, py, pz + s1 * r, argb);
            v(vc, px + c1 * ro, py, pz + s1 * ro, rr);
            v(vc, px + c0 * ro, py, pz + s0 * ro, rr);
        }
    }
}
