package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.Updrafts;
import dev.awaken.body.ascent.Windways;
import dev.awaken.lens.client.LensGlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

/**
 * The Wilds' moving air, seen (SPEC §17.12, §17.6): gusts. Along each windway near you, and up each updraft, long thin
 * lines of pale air that taper to nothing at both ends, waver as they go, now and then curl over themselves, and come
 * and go; brighter round you while a windway holds your glide, a rush of air as it takes you.
 *
 * <p>Soft lines only, no sheet of haze and no texture: each gust is a ribbon turned to face you whose falloff across its
 * width is in its vertices, so it has no edge, and it fades out toward a lane's ends, so nothing is cut off by the
 * islands (Euan, 11 Nov, of the first build: "they look glitchy basically ... they kind of cut off. Near the actual
 * island as well ... rebuild something that kind of looks like wind gusts").
 *
 * <p>Drawn after the level in one pass of its own, tested against the world's depth and writing none (as the gate
 * pillars are): through the level's own passes its quads wrote depth, Lens took each for something solid and left the
 * game's sky behind it, hard-edged dark slabs by night. Under Lens the pass goes into its layer for light
 * ({@link LensGlow}), laid over the finished picture; otherwise straight onto the frame. Nothing allocated per frame.
 */
public final class WindwayRenderer {
    private WindwayRenderer() {}

    /** How far along a lane it's drawn either side of you. */
    static final double VIEW = 320;
    private static final double TAU = Math.PI * 2;
    /** Room for every gust's quads (a near one takes up to 170, a far one 20); past it the rest are left out. */
    private static final int MAX_QUADS = 12288, QUAD_BYTES = 64;

    /**
     * A stream of gusts down a lane: its flow (blocks a tick), one slot every so many blocks, a gust's length, how far
     * from the lane's heart it runs, its half width, its strength, the share that curl, and its own run of chances.
     */
    private record Stream(double flow, double spacing, double lenMin, double lenMax, double radMin, double radMax, double halfMin,
                          double halfMax, double alpha, double curls, double seed) {}

    private static final Stream[] STREAMS = {
            // the lane's body: long lines near its heart, at the air's own pace
            new Stream(1.25, 8.0, 16, 30, 0.5, 3.6, 0.26, 0.36, 0.70, 0.24, 0.0),
            // slower, longer, fainter ones round the outside
            new Stream(0.95, 12.5, 22, 40, 2.6, 5.3, 0.30, 0.46, 0.42, 0.12, 17.31),
            // short quick darts through all of it
            new Stream(1.65, 19.0, 9, 15, 0.3, 4.6, 0.18, 0.24, 0.75, 0.0, 41.77),
    };

    // Across a ribbon, from one edge to the other: where each column of vertices stands, how strong it is, and how much
    // of it is the pale core (the rest is the air's shade, a soft blue-grey flank). Nothing at the edge, so no edge
    // shows; and two tones, because pale air has to read on a blue sky (the core does) and on white haze and cloud
    // alike (the flank does: a white line there is no line at all). Fewer columns further off
    private static final float[] X_NEAR = {-1F, -2 / 3F, -1 / 3F, 0F, 1 / 3F, 2 / 3F, 1F}, W_NEAR = {0F, 0.34F, 0.74F, 1F, 0.74F, 0.34F, 0F},
            K_NEAR = {0F, 0F, 0.55F, 1F, 0.55F, 0F, 0F};
    private static final float[] X_FAR = {-1F, -0.5F, 0F, 0.5F, 1F}, W_FAR = {0F, 0.46F, 1F, 0.46F, 0F}, K_FAR = {0F, 0.12F, 1F, 0.12F, 0F};
    /** This frame's colour for each column (r, g, b in the low bytes, as the vertex holds them). */
    private static final int[] RGB_NEAR = new int[X_NEAR.length], RGB_FAR = new int[X_FAR.length];

    // one gust's line, camera-relative: its points, their strength and half width, and the ribbon's side at each
    private static final int MAX_POINTS = 48;
    private static final float[] PX = new float[MAX_POINTS], PY = new float[MAX_POINTS], PZ = new float[MAX_POINTS],
            PA = new float[MAX_POINTS], PH = new float[MAX_POINTS], SX = new float[MAX_POINTS], SY = new float[MAX_POINTS],
            SZ = new float[MAX_POINTS];

    // updraft vents seen this frame (UpdraftRenderer hands each in as the level is drawn)
    private static final int MAX_VENTS = 24;
    private static final int[] VX = new int[MAX_VENTS], VY = new int[MAX_VENTS], VZ = new int[MAX_VENTS];
    private static int vents;

    private static @Nullable MappableRingBuffer ring;
    /** Where the next vertex goes in the mapped buffer, and where it ends. */
    private static long at, end;
    private static double cx, cy, cz, time;
    /** The way the camera looks. */
    private static double fx, fy, fz;
    /** How strong the air is drawn this hour. */
    private static float tintA;
    private static Windways.@Nullable Way riding;
    private static boolean broken;
    private static long heldSince = -1;

    /** AscentMoves, each glide tick: the lane holding the glide (null: none). The rush of air as one takes you. */
    static void riding(Windways.@Nullable Way way) {
        Minecraft mc = Minecraft.getInstance();
        if (way != null && riding == null && mc.player != null && mc.level != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), AscentContent.UPDRAFT.get(),
                    SoundSource.PLAYERS, 0.7F, 1.35F, false);
            heldSince = mc.level.getGameTime();
        } else if (way != null && mc.player != null && mc.level != null && (mc.level.getGameTime() - heldSince) % 70 == 69) {
            // the stream's breath while it carries you
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), AscentContent.WIND.get(),
                    SoundSource.PLAYERS, 0.35F, 1.2F, false);
        }
        riding = way;
    }

    /** UpdraftRenderer, for each vent the level draws this frame: its column is drawn with the lanes, after the level. */
    static void vent(BlockPos pos) {
        if (vents >= MAX_VENTS) return;
        VX[vents] = pos.getX();
        VY[vents] = pos.getY();
        VZ[vents] = pos.getZ();
        vents++;
    }

    static void onAfterLevel(RenderLevelStageEvent.AfterLevel e) {
        if (!broken) {
            try {
                draw(e);
            } catch (RuntimeException ex) {
                broken = true;
                Awaken.LOG.error("Windway rendering failed and is off for this session", ex);
            }
        }
        vents = 0;
    }

    private static void draw(RenderLevelStageEvent.AfterLevel e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (riding != null && AscentMoves.state() != dev.awaken.body.ascent.AscentState.GLIDE) riding = null;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        List<Windways.Way> ways = mc.level.dimension() == Level.OVERWORLD ? Windways.clientWays() : List.of();
        boolean any = vents > 0;
        for (int i = 0; i < ways.size() && !any; i++) any = ways.get(i).reach(cx, cz) <= VIEW + Windways.RADIUS;
        // nothing near: no buffer mapped and no pass opened (a pass on a 4K target is a trip through memory by itself)
        if (!any) return;
        // (as a double: a long and a float add as a float, which drops the part-tick in any world more than a day old)
        time = (double) state.gameTime + state.worldPartialTicks;
        Matrix4fc view = e.getModelViewMatrix();
        fx = -view.m02();
        fy = -view.m12();
        fz = -view.m22();
        // the core pale mint-white by day, a dimmer sea-green by night, when white lines on a dark sky would glare; its
        // flank a blue-grey by day (darker than haze and cloud, lost on the blue sky), a faint teal glow by night
        float dark = Math.clamp(mc.level.getSkyDarken() / 11F, 0F, 1F);
        float cr = 244 - 106 * dark, cg = 255 - 57 * dark, cb = 250 - 58 * dark;
        float sr = 104 - 56 * dark, sg = 146 - 56 * dark, sb = 204 - 98 * dark;
        for (int i = 0; i < K_NEAR.length; i++) RGB_NEAR[i] = rgb(sr, sg, sb, cr, cg, cb, K_NEAR[i]);
        for (int i = 0; i < K_FAR.length; i++) RGB_FAR[i] = rgb(sr, sg, sb, cr, cg, cb, K_FAR[i]);
        tintA = 1F - 0.18F * dark;

        if (ring == null) {
            ring = new MappableRingBuffer(() -> "awaken windways", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, MAX_QUADS * QUAD_BYTES);
        }
        GpuBuffer buf = ring.currentBuffer();
        int quads;
        try (GpuBufferSlice.MappedView mapped = buf.map(false, true)) {
            long start = MemoryUtil.memAddress(mapped.data());
            at = start;
            end = start + (long) MAX_QUADS * QUAD_BYTES;
            for (int i = 0; i < ways.size(); i++) lane(ways.get(i));
            for (int i = 0; i < vents; i++) column(VX[i], VY[i], VZ[i]);
            quads = (int) ((at - start) / QUAD_BYTES);
        }
        if (quads > 0) {
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indices = quadIndex.getBuffer(quads * 6);
            // position and colour, alpha-blended, tested against depth and writing none, both sides, no fog (the gusts
            // fade themselves out by distance): the game's own plain quads
            try (RenderPass pass = LensGlow.open(() -> "awaken windways", main)) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(view)));
                pass.setIndexBuffer(indices, quadIndex.type());
                pass.setVertexBuffer(0, buf.slice());
                pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.DEBUG_QUADS));
                pass.drawIndexed(quads * 6, 1, 0, 0, 0);
            }
        }
        ring.rotate();
    }

    /** One lane's gusts within {@link #VIEW} of the camera. */
    private static void lane(Windways.Way w) {
        if (w.reach(cx, cz) > VIEW + Windways.RADIUS) return;
        double len = w.length();
        if (len < 1) return;
        double ux = (w.bx() - w.ax()) / len, uy = (w.by() - w.ay()) / len, uz = (w.bz() - w.az()) / len;
        // a frame round the axis: n1 level and to the side, n2 up-ish
        double hl = Math.sqrt(ux * ux + uz * uz);
        double n1x = hl < 1e-6 ? 1 : -uz / hl, n1z = hl < 1e-6 ? 0 : ux / hl;
        double n2x = -n1z * uy, n2y = n1z * ux - n1x * uz, n2z = n1x * uy;
        double here = Math.max(0, Math.min(len, w.along(cx, cy, cz)));
        double s0 = Math.max(0, here - VIEW), s1 = Math.min(len, here + VIEW);
        boolean held = w == riding;
        for (Stream st : STREAMS) {
            // the whole stream slides along; each gust keeps its slot k (and so its look) as it goes
            double shift = time * st.flow;
            long k0 = (long) Math.floor((s0 - shift - st.lenMax - 4) / st.spacing), k1 = (long) Math.ceil((s1 - shift + 4) / st.spacing);
            for (long k = k0; k <= k1; k++) {
                double kk = k + st.seed;
                double h1 = frac(kk * 0.6180339887), h2 = frac(kk * 0.4142135624), h3 = frac(kk * 0.7320508076),
                        h4 = frac(kk * 0.2360679775), h5 = frac(kk * 0.1415926536), h6 = frac(kk * 0.7182818285);
                if (h4 < 0.18) continue; // gaps: the stream's uneven
                double length = st.lenMin + (st.lenMax - st.lenMin) * h3;
                // each drifts a little against its neighbours, so the stream is never a belt
                double tail = k * st.spacing + shift + 2.5 * Math.sin(time * (0.02 + 0.02 * h5) + h6 * TAU);
                if (tail + length < s0 || tail > s1) continue;
                double mid = tail + length / 2;
                // it comes and goes as it travels
                double life = smooth((Math.sin((mid / (80 + 70 * h5) + h6) * TAU) + 0.35) / 0.8);
                if (life <= 0.01) continue;
                double ang0 = h1 * TAU + time * 0.004 * (h2 < 0.5 ? 1 : -1);
                double rad0 = st.radMin + (st.radMax - st.radMin) * Math.sqrt(h2);
                double c0 = Math.cos(ang0) * rad0, d0 = Math.sin(ang0) * rad0;
                double mx = w.ax() + ux * mid + n1x * c0 + n2x * d0 - cx, my = w.ay() + uy * mid + n2y * d0 - cy,
                        mz = w.az() + uz * mid + n1z * c0 + n2z * d0 - cz;
                double dist = Math.sqrt(mx * mx + my * my + mz * mz);
                if (dist > VIEW + length) continue;
                // wholly behind the camera
                if (mx * fx + my * fy + mz * fz < -(length * 0.5 + 8)) continue;
                double a = st.alpha * tintA * life * (1 - smooth((dist - VIEW * 0.55) / (VIEW * 0.45))) * (held && dist < 40 ? 1.3 : 1);
                if (a < 0.015) continue;
                int lod = dist < 44 ? 0 : dist < 130 ? 1 : 2;
                int n = lod == 0 ? 14 : lod == 1 ? 8 : 5;
                // wider with distance, so a far gust is still a line and not a shimmer
                double half = (st.halfMin + (st.halfMax - st.halfMin) * h6) * 1.3 * (1 + dist / 110);
                double twist = (h3 - 0.5) * 0.06, wob = 0.25 + 0.45 * h2, phase = h6 * TAU + time * 0.045;
                // a curl: over a short stretch the line runs on ahead, rises, comes back over itself and carries on
                boolean curl = lod < 2 && h5 > 1 - st.curls;
                double loop = 0.9 + 0.9 * h3, span = 3.2 * loop, at0 = length * (0.42 + 0.25 * h1) - span / 2;
                double angC = h1 * TAU + 1.7, cc = Math.cos(angC), sc = Math.sin(angC);
                int extra = !curl ? 0 : lod == 0 ? 14 : 8;
                int count = 0, i = 0, j = 0;
                while (i <= n || j < extra) {
                    // the line's own points, and between them the curl's, in order along it
                    double fi = i <= n ? i / (double) n : 2, fj = j < extra ? (at0 + span * (j + 0.5) / extra) / length : 2;
                    double f;
                    if (fi <= fj) {
                        f = fi;
                        i++;
                    } else {
                        f = fj;
                        j++;
                    }
                    if (count >= MAX_POINTS) break;
                    double t = f * length, s = tail + t;
                    double ang = ang0 + twist * t, rad = rad0 + wob * Math.sin(phase + t * 0.19);
                    double oc = Math.cos(ang) * rad, od = Math.sin(ang) * rad + wob * 0.5 * Math.cos(phase * 0.7 + t * 0.13);
                    double on = 0;
                    if (curl) {
                        double q = (t - at0) / span;
                        if (q > 0 && q < 1) {
                            double phi = TAU * smooth(q);
                            on = loop * Math.sin(phi);
                            double lift = loop * (1 - Math.cos(phi));
                            oc += cc * lift;
                            od += sc * lift;
                        }
                    }
                    double x = w.ax() + ux * (s + on) + n1x * oc + n2x * od - cx, y = w.ay() + uy * (s + on) + n2y * od - cy,
                            z = w.az() + uz * (s + on) + n1z * oc + n2z * od - cz;
                    PX[count] = (float) x;
                    PY[count] = (float) y;
                    PZ[count] = (float) z;
                    // tapering to nothing at its tail and its head; rising out of the lane's start and gone over its
                    // last stretch, where it sets you down; never across the eye
                    double near = smooth((Math.sqrt(x * x + y * y + z * z) - 1.5) / 4);
                    PA[count] = (float) (a * smooth(f / 0.3) * smooth((1 - f) / 0.38) * smooth(s / 40) * smooth((len - s) / 48) * near);
                    PH[count] = (float) (half * (0.3 + 0.7 * Math.sin(Math.PI * f)));
                    count++;
                }
                ribbon(count, lod);
            }
        }
    }

    /** An updraft's column (SPEC §17.6): the same gusts rising up it, winding round as they go, gone where the lift is gone. */
    private static void column(int bx, int by, int bz) {
        double ox = bx + 0.5 - cx, oy = by + 1 - cy, oz = bz + 0.5 - cz;
        double flat = Math.sqrt(ox * ox + oz * oz);
        if (flat > 160) return;
        double seen = 1 - smooth((flat - 100) / 60);
        int lod = flat < 40 ? 0 : flat < 90 ? 1 : 2;
        int n = lod == 0 ? 10 : lod == 1 ? 6 : 4;
        double top = Updrafts.HEIGHT;
        // (a vent's own run of chances, from where it stands)
        double site = frac(bx * 0.3183 + bz * 0.7071 + by * 0.1123) * 97;
        for (int k = 0; k < 14; k++) {
            double kk = k + site;
            double h1 = frac(kk * 0.6180339887), h2 = frac(kk * 0.4142135624), h3 = frac(kk * 0.7320508076), h5 = frac(kk * 0.1415926536);
            double length = 7 + 6 * h3, speed = 0.3 + 0.2 * h2;
            double y0 = ((time * speed + h5 * (top + length)) % (top + length)) - length;
            double ang0 = h1 * TAU + time * 0.006 * (k % 2 == 0 ? 1 : -1), rad0 = Updrafts.RADIUS * 0.8 * Math.sqrt(0.06 + 0.94 * h2);
            double wind = (k % 2 == 0 ? 1 : -1) * (0.10 + 0.08 * h5), half = (0.20 + 0.12 * h3) * 1.3 * (1 + flat / 110);
            for (int i = 0; i <= n; i++) {
                double f = i / (double) n, y = y0 + length * f;
                double ang = ang0 + wind * y, rad = rad0 * (1 - 0.25 * Math.max(0, y) / top);
                double x = ox + Math.cos(ang) * rad, z = oz + Math.sin(ang) * rad;
                PX[i] = (float) x;
                PY[i] = (float) (oy + y);
                PZ[i] = (float) z;
                // rising out of the vent, full while the lift is, gone where it is gone
                double lift = y <= Updrafts.FULL ? 1 : Math.max(0, 1 - (y - Updrafts.FULL) / (top - Updrafts.FULL));
                double near = smooth((Math.sqrt(x * x + (oy + y) * (oy + y) + z * z) - 1.5) / 4);
                PA[i] = (float) (0.6 * tintA * seen * smooth(f / 0.3) * smooth((1 - f) / 0.38) * smooth(y / 2.5) * lift * near);
                PH[i] = (float) (half * (0.3 + 0.7 * Math.sin(Math.PI * f)));
            }
            ribbon(n + 1, lod);
        }
    }

    /**
     * The line in the scratch arrays as a ribbon turned to face the camera: across it, columns from one edge to the other
     * whose strength falls from the middle to nothing.
     */
    private static void ribbon(int count, int lod) {
        if (count < 2) return;
        float[] xs = lod == 0 ? X_NEAR : X_FAR, ws = lod == 0 ? W_NEAR : W_FAR;
        int[] rgbs = lod == 0 ? RGB_NEAR : RGB_FAR;
        float lx = 0F, ly = 1F, lz = 0F;
        for (int i = 0; i < count; i++) {
            int a = Math.max(0, i - 1), b = Math.min(count - 1, i + 1);
            float tx = PX[b] - PX[a], ty = PY[b] - PY[a], tz = PZ[b] - PZ[a];
            // side: along the line × toward the camera (the camera is the origin)
            float sx = ty * PZ[i] - tz * PY[i], sy = tz * PX[i] - tx * PZ[i], sz = tx * PY[i] - ty * PX[i];
            float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl > 1e-5F) {
                lx = sx / sl;
                ly = sy / sl;
                lz = sz / sl;
            }
            // (a point whose line runs straight at the eye has no side of its own: its neighbour's)
            SX[i] = lx * PH[i];
            SY[i] = ly * PH[i];
            SZ[i] = lz * PH[i];
        }
        for (int i = 0; i + 1 < count; i++) {
            if (PA[i] <= 0.004F && PA[i + 1] <= 0.004F) continue;
            if (at + (long) (xs.length - 1) * QUAD_BYTES > end) return;
            for (int c = 0; c + 1 < xs.length; c++) {
                vertex(i, xs[c], ws[c], rgbs[c]);
                vertex(i, xs[c + 1], ws[c + 1], rgbs[c + 1]);
                vertex(i + 1, xs[c + 1], ws[c + 1], rgbs[c + 1]);
                vertex(i + 1, xs[c], ws[c], rgbs[c]);
            }
        }
    }

    /** One vertex: point i of the line, moved across the ribbon by x of its half width, at w of its strength, in its column's colour. */
    private static void vertex(int i, float x, float w, int rgb) {
        MemoryUtil.memPutFloat(at, PX[i] + SX[i] * x);
        MemoryUtil.memPutFloat(at + 4, PY[i] + SY[i] * x);
        MemoryUtil.memPutFloat(at + 8, PZ[i] + SZ[i] * x);
        int alpha = Math.clamp(Math.round(PA[i] * w * 255F), 0, 255);
        // r, g, b, a bytes
        MemoryUtil.memPutInt(at + 12, alpha << 24 | rgb);
        at += 16;
    }

    /** The shade's colour to the core's by k, packed as a vertex holds it (r in the low byte). */
    private static int rgb(float sr, float sg, float sb, float cr, float cg, float cb, float k) {
        int r = Math.clamp(Math.round(sr + (cr - sr) * k), 0, 255), g = Math.clamp(Math.round(sg + (cg - sg) * k), 0, 255),
                b = Math.clamp(Math.round(sb + (cb - sb) * k), 0, 255);
        return b << 16 | g << 8 | r;
    }

    /** 0 to 1, eased at both ends. */
    private static double smooth(double v) {
        if (v <= 0) return 0;
        if (v >= 1) return 1;
        return v * v * (3 - 2 * v);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }
}
