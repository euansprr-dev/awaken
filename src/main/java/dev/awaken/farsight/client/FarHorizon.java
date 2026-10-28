package dev.awaken.farsight.client;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.farsight.Rings;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The cached horizon (SPEC §20, frame-budget build A, 2026-10-26). While the eye stays put, everything Far Sight draws
 * past the live rings' window (ring {@link #split} out: the far land, its trees, City Sight's far chunks) is drawn into
 * a panorama of {@link #SLICES} flat slices around the eye, colour and depth, and each frame lays it onto the screen in
 * one pass that writes Far Sight's depth as the rings would (Lens hazes by it; the live rings, City Sight's near chunks
 * and the landmarks sort against it). A slice is served only while it is exact:
 * <ul>
 *   <li>the eye where it was drawn: parallax is too big to follow a moving eye (30 blocks sideways moves a ridge 3 km out
 *       by about 12 pixels on a 1710-pixel-tall world), so moving draws everything live, as before;</li>
 *   <li>the same ring windows and far data: a patch or a City Sight chunk arriving stales the slices it shows in;</li>
 *   <li>the same light and air: sun, weather, mist and the fog's colour (which turns with the view at dawn and dusk).</li>
 * </ul>
 * Anything not served (a slice being redrawn, the screen's stretched corners where a slice texel would be magnified
 * past {@link #MAX_MAG}, the whole view while moving) is drawn live, so nothing shows that the live path wouldn't.
 * A slice holds {@link #DENSITY} times the screen centre's pixels per radian, so the composite minifies nearly
 * everywhere: the far land keeps its sharpness. The panorama is at most {@link #BUDGET} bytes.
 */
final class FarHorizon implements AutoCloseable {
    static final int SLICES = 24;
    static final double STEP = 2 * Math.PI / SLICES;
    /** A slice's half width in tan, and how much an elevation's tan grows at a slice's edge. */
    static final float HALF = (float) Math.tan(STEP / 2), EDGE = (float) Math.sqrt(1 + HALF * HALF);
    /** A slice's pixels per tan over the screen centre's; the most a slice texel is magnified before the live draw takes the pixel. */
    static final float DENSITY = 1.5f, MAX_MAG = 1.6f;
    static final long BUDGET = 100L << 20;
    /** Slices drawn in one frame at most. */
    static final int MAX_RENDERS = 2;
    /** Frames the eye stays put before a panorama is begun (a walk's pauses would only cost slices). */
    static final int STILL_FRAMES = 15;
    /** A slice older than this many frames is redrawn, one every {@link #REFRESH_EVERY} frames. */
    static final int REFRESH_FRAMES = 600, REFRESH_EVERY = 8;
    /**
     * A slice whose land changed is drawn again only once it has been quiet this many frames: while the generated layer
     * or City Sight fills in (a new world, the countryside) it stays live, never redrawn again and again (it was over a
     * hundred slices a second in a new world's countryside, costlier than drawing it live).
     */
    static final int SETTLE_FRAMES = 30;
    /** The eye may move this many pixels' worth of parallax at the panorama's nearest land before it's dropped. */
    static final float PARALLAX_PX = 0.25f;
    /** The composite's depth, a hair nearer than the rings' own, so the same surface drawn live behind it loses. */
    static final float BIAS = 2e-4f;
    /** The cone's margin (radians): the projection's own tilt (view bobbing) and a box's sides between its corners. */
    static final float CONE_MARGIN = 0.035f;
    /** The band's rows' margin on screen (NDC): the projection's own tilt (view bobbing). */
    static final float ROW_MARGIN = 0.08f;

    /** Measuring (autopilot {@code farsight horizon on|off|setting}): null follows the client setting. */
    static Boolean forced;
    /** The first ring the panorama holds (autopilot {@code farsight horizon split <n>}). */
    static int split = 2;

    static boolean enabled() {
        if (forced != null) return forced;
        return !AwakenConfig.Client.SPEC.isLoaded() || AwakenConfig.Client.FAR_SIGHT_HORIZON.get();
    }

    // what the light and air were when a slice was drawn: the look vector (FarSightClient#look), and how far each may drift
    static final int LOOK = 26;
    private static final float[] TOLERANCE = {
            0.012f, 0.012f, 0.012f, 0.006f,          // sun direction (about 0.7 degrees), strength
            0.006f, 0.006f, 0.006f, 0.006f,          // sun colour, golden hour
            1e-6f, 0.006f, 0.5f, 0.5f,               // haze, rain, mist floor, depth
            1e-5f, 0.006f, 2f, 2f,                   // mist density, cover, Far Sight's fog start, end
            0.006f, 0.006f, 0.006f, 0.004f,          // the fog's colour (about 1.5/255 each); the sky's light
            2f, 2f, 0f, 0f,                          // the weather fog's start, end; debug view; parts off
            0f, 0f};                                 // gamma; a sight effect (counts frames: never the same)

    // the panorama: one row of slices, colour and depth
    private GpuTexture colour, depth;
    private GpuTextureView colourView, depthView;
    private int atlasW, atlasH, sliceW;
    private final MappableRingBuffer frames, projections, composite;
    private final int frameStride, projStride;
    private final Matrix4f[] sliceRot = new Matrix4f[SLICES];
    private final Matrix4f proj = new Matrix4f(), cull = new Matrix4f(), strip = new Matrix4f();
    private final Vector4f clear = new Vector4f(0f);

    // the panorama in hand: its eye, its band, its density, the ring it starts at and the windows it was drawn in
    private boolean held;
    private double eyeX, eyeY, eyeZ, lastX = Double.NaN, lastY, lastZ;
    private float vLo, vHi, density, screenDensity, eps, ratio;
    /** City Sight's chunks are the panorama's from this far (blocks, horizontally): past the real chunks' reach. */
    private float cityFrom;
    /** The screen rows the band covers this frame (NDC y, low and high): the composite draws over those only. */
    private float rowLo = -1f, rowHi = 1f;
    private int ring, still, realWithin, mainW, mainH;
    private final int[] winX = new int[Rings.RINGS], winZ = new int[Rings.RINGS];
    /** The live window (ring {@link #ring} - 1's), blocks from the camera: inside it everything stays live. */
    private float wx0, wz0, wx1, wz1;

    private final boolean[] ok = new boolean[SLICES];
    /** When each slice was drawn, and when what it shows last changed (a patch, a City Sight chunk). */
    private final long[] made = new long[SLICES], changedAt = new long[SLICES];
    private final float[][] looks = new float[SLICES][LOOK];
    /** This frame's slices served, and the ones drawn this frame. */
    private int served;
    private final int[] renders = new int[MAX_RENDERS];
    private int renderCount;
    /**
     * The view's axis (world) and the cone about it inside which a slice holds enough pixels (cos of its half angle;
     * -2 when that's the whole screen): the screen's pixels per radian grow as 1 / cos² off the axis, so its corners
     * may need more than a slice holds, and what reaches past the cone is drawn live.
     */
    private float axisX, axisY, axisZ, coneCos = -2f;
    private long frame;
    final FarRenderer.View[] views = {new FarRenderer.View(), new FarRenderer.View()};
    private boolean broken;

    // status: frames served and drawn live, slices drawn, panoramas begun, since the last status
    private int framesServed, framesLive, slicesDrawn, begun, dropEye, dropView, dropWindows, dropBand, staled, slicePieces, sliceTrees;

    FarHorizon() {
        int ubo = RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();
        frameStride = align(FarRenderer.FRAME_SIZE, ubo);
        projStride = align(64, ubo);
        frames = new MappableRingBuffer(() -> "awaken far horizon frames", 130, frameStride * MAX_RENDERS);
        projections = new MappableRingBuffer(() -> "awaken far horizon projections", 130, projStride * MAX_RENDERS);
        composite = new MappableRingBuffer(() -> "awaken far horizon composite", 130, COMPOSITE_SIZE);
        for (int i = 0; i < SLICES; i++) {
            double psi = (i + 0.5) * STEP;
            sliceRot[i] = new Matrix4f().setLookAlong((float) Math.sin(psi), 0f, (float) -Math.cos(psi), 0f, 1f, 0f);
        }
    }

    static final int COMPOSITE_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putIVec4().putVec4().get();

    private static int align(int n, int a) {
        a = Math.max(16, a);
        return (n + a - 1) / a * a;
    }

    /**
     * Before the screen's City Sight plan and the draw: decides what the panorama serves this frame and which slices it
     * draws. {@code pixelsPerTan} is the screen centre's pixels per unit of tan (its height over 2 tan(fov/2)).
     */
    void prepare(FarRenderer renderer, FarField field, double camX, double camY, double camZ, Matrix4fc viewRotation, float pixelsPerTan,
            float fovDegrees, float aspect, int realWithin, int mainW, int mainH, float[] look) {
        frame++;
        served = 0;
        renderCount = 0;
        // through the binoculars (SPEC §39.8) the panorama, sized for the normal view, would be soft or huge: the far land draws live
        boolean on = enabled() && !broken && FarPipelines.horizonOk() && dev.awaken.core.api.CameraHooks.zoom() <= 1.05;
        // (a thousandth of a block: a still eye's float noise, never parallax)
        boolean moved = Double.isNaN(lastX) || Math.abs(camX - lastX) > 1e-3 || Math.abs(camY - lastY) > 1e-3 || Math.abs(camZ - lastZ) > 1e-3;
        lastX = camX;
        lastY = camY;
        lastZ = camZ;
        still = moved ? 0 : still + 1;
        if (!on) {
            drop();
            return;
        }
        // the first ring held: past the real chunks' reach, so the chunk mask never changes under a slice
        int s = Math.max(1, split);
        while (s < Rings.RINGS && (!field.valid[s - 1] || inner(renderer, s) < realWithin + 48)) s++;
        if (held) {
            double dx = camX - eyeX, dy = camY - eyeY, dz = camZ - eyeZ;
            boolean eye = dx * dx + dy * dy + dz * dz <= eps * eps;
            boolean view = s == ring && realWithin == this.realWithin && mainW == this.mainW && mainH == this.mainH
                    && Math.abs(pixelsPerTan / screenDensity - 1f) < 0.25f;
            boolean windows = true;
            for (int r = Math.max(0, ring - 1); windows && r < Rings.RINGS; r++) {
                windows = field.winX[r] == winX[r] && field.winZ[r] == winZ[r];
            }
            if (!eye) dropEye++;
            else if (!view) dropView++;
            else if (!windows) dropWindows++;
            if (!eye || !view || !windows) drop();
        }
        if (s >= Rings.RINGS) return;
        if (!held) {
            if (still < STILL_FRAMES) {
                framesLive++;
                return;
            }
            begin(renderer, field, s, camX, camY, camZ, pixelsPerTan, realWithin, mainW, mainH);
            if (!held) return;
        }
        // which slices can serve: drawn, and under the same light and air
        int servable = 0;
        for (int i = 0; i < SLICES; i++) if (ok[i] && close(looks[i], look)) servable |= 1 << i;
        // what to draw: stale slices in view first (nearest the view's middle), then stale ones out of view, then the oldest
        float yaw = (float) Math.atan2(-viewRotation.m02(), viewRotation.m22());
        double half = Math.atan(Math.tan(Math.toRadians(fovDegrees) / 2) * aspect) + STEP;
        for (int pass = 0; pass < 2 && renderCount < MAX_RENDERS; pass++) {
            for (int n = 0; n < SLICES && renderCount < MAX_RENDERS; n++) {
                // slices in order of how far their middle is from the view's
                int i = nearest(yaw, n);
                if ((servable & 1 << i) != 0 || frame - changedAt[i] < SETTLE_FRAMES) continue;
                double off = Math.abs(wrap((i + 0.5) * STEP - yaw));
                boolean inView = off <= half;
                if (inView == (pass == 0)) {
                    renders[renderCount++] = i;
                    // out of view: one a frame
                    if (pass == 1) break;
                }
            }
        }
        if (renderCount == 0 && frame % REFRESH_EVERY == 0) {
            int oldest = -1;
            for (int i = 0; i < SLICES; i++) if (oldest < 0 || made[i] < made[oldest]) oldest = i;
            if (frame - made[oldest] > REFRESH_FRAMES) renders[renderCount++] = oldest;
        }
        served = servable;
        for (int k = 0; k < renderCount; k++) {
            served |= 1 << renders[k];
            ok[renders[k]] = true;
            made[renders[k]] = frame;
            System.arraycopy(look, 0, looks[renders[k]], 0, LOOK);
        }
        // where the screen needs more than a slice holds, the cached zone is drawn live (the composite leaves it)
        axisX = -viewRotation.m02();
        axisY = -viewRotation.m12();
        axisZ = -viewRotation.m22();
        float len = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        axisX /= len;
        axisY /= len;
        axisZ /= len;
        float holds = density * MAX_MAG / pixelsPerTan;
        coneCos = maxScreenDensity(pixelsPerTan, fovDegrees, aspect) <= density * MAX_MAG ? -2f
                : (float) Math.cos(Math.atan(Math.sqrt(Math.max(0.0, holds - 1.0))) - CONE_MARGIN);
        rows(viewRotation, fovDegrees);
        if (served != 0) framesServed++;
        else framesLive++;
        slicesDrawn += renderCount;
    }

    /**
     * The screen rows the band covers (NDC y): its top and bottom bearings every half slice round, and the one the view
     * faces (where a circle of one elevation peaks on screen), less a margin for the view's bob; the whole screen when
     * none lies ahead.
     */
    private void rows(Matrix4fc v, float fovDegrees) {
        float scale = 1f / (float) Math.tan(Math.toRadians(fovDegrees) / 2), lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        float yaw = (float) Math.atan2(-v.m02(), v.m22());
        for (int k = 0; k <= SLICES * 2; k++) {
            double a = k == SLICES * 2 ? yaw : k * STEP / 2;
            float x = (float) Math.sin(a), z = (float) -Math.cos(a);
            for (int e = 0; e < 2; e++) {
                float y = e == 0 ? vLo : vHi;
                float vy = v.m01() * x + v.m11() * y + v.m21() * z, vz = v.m02() * x + v.m12() * y + v.m22() * z;
                if (vz > -1e-3f) continue;
                float ndc = vy / -vz * scale;
                lo = Math.min(lo, ndc);
                hi = Math.max(hi, ndc);
            }
        }
        if (lo > hi) {
            rowLo = -1f;
            rowHi = 1f;
        } else {
            rowLo = Math.max(-1f, lo - ROW_MARGIN);
            rowHi = Math.min(1f, hi + ROW_MARGIN);
        }
    }

    /** The nearest distance from the camera to ring s - 1's window's edge (where ring s starts). */
    private static float inner(FarRenderer renderer, int s) {
        float ox = renderer.originX(s - 1), oz = renderer.originZ(s - 1), ext = Rings.SIZE * Rings.cellSize(s - 1);
        return Math.min(Math.min(-ox, ox + ext), Math.min(-oz, oz + ext));
    }

    private void begin(FarRenderer renderer, FarField field, int s, double camX, double camY, double camZ, float pixelsPerTan, int realWithin,
            int mainW, int mainH) {
        // the band: from the lowest to the highest ground the held rings have, seen from the nearest edge (and the farthest)
        // City Sight's chunks from past the real chunks' reach (they needn't follow the rings: at the Roost the city lies
        // within ring 1's window), the far land from ring s; the band and the eye's leeway from the nearer of the two
        cityFrom = realWithin + 48f;
        float near = Math.min(inner(renderer, s), cityFrom), far = Rings.reach(Rings.RINGS - 1) * 1.5f;
        long range = renderer.range(field, s);
        if (range == Long.MIN_VALUE) return;
        // up to the world's top and the tallest treetops over it, so land learnt later never rises past the band (it
        // started the panorama over a few times a second while the generated layer filled in)
        float lo = (int) (range >> 32), hi = Math.max((int) range, renderer.maxY() + 32) + 72f;
        float tLo = (float) ((lo - camY) / (lo < camY ? near : far)), tHi = (float) ((hi - camY) / (hi > camY ? near : far));
        vLo = (tLo < 0 ? tLo * EDGE : tLo) - 0.002f;
        vHi = (tHi > 0 ? tHi * EDGE : tHi) + 0.002f;
        // pixels per tan: the screen centre's times DENSITY, less if that passes the budget or the widest texture
        float d = pixelsPerTan * DENSITY;
        int maxW = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
        d = Math.min(d, (maxW - SLICES) / (SLICES * 2f * HALF));
        double bytes = (double) SLICES * 2 * HALF * d * (vHi - vLo) * d * 8;
        if (bytes > BUDGET) d *= (float) Math.sqrt(BUDGET / bytes);
        int w = (int) Math.ceil(2 * HALF * d), h = Math.min(maxW, (int) Math.ceil((vHi - vLo) * d / 32) * 32);
        if (w < 8 || h < 8) return;
        try {
            if (colour == null || w * SLICES != atlasW || h > atlasH || h < atlasH * 2 / 3) {
                free();
                atlasW = w * SLICES;
                atlasH = h;
                var device = RenderSystem.getDevice();
                colour = device.createTexture(() -> "awaken far horizon colour", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                        GpuFormat.RGBA8_UNORM, atlasW, atlasH, 1, 1);
                depth = device.createTexture(() -> "awaken far horizon depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                        GpuFormat.D32_FLOAT, atlasW, atlasH, 1, 1);
                colourView = device.createTextureView(colour);
                depthView = device.createTextureView(depth);
                Awaken.LOG.info("[farsight] cached horizon: {} slices of {}x{} from ring {} and City Sight past {} m, {} MB", SLICES, w, atlasH, s, Math.round(near),
                        ((long) atlasW * atlasH * 8) >> 20);
            }
        } catch (RuntimeException e) {
            fail(e);
            return;
        }
        sliceW = w;
        density = Math.min(d, atlasH / (vHi - vLo));
        screenDensity = pixelsPerTan;
        ratio = density / pixelsPerTan;
        eps = PARALLAX_PX * near / (density * MAX_MAG);
        eyeX = camX;
        eyeY = camY;
        eyeZ = camZ;
        ring = s;
        this.realWithin = realWithin;
        this.mainW = mainW;
        this.mainH = mainH;
        for (int r = 0; r < Rings.RINGS; r++) {
            winX[r] = field.winX[r];
            winZ[r] = field.winZ[r];
        }
        float ox = renderer.originX(s - 1), oz = renderer.originZ(s - 1), ext = Rings.SIZE * Rings.cellSize(s - 1);
        wx0 = ox;
        wz0 = oz;
        wx1 = ox + ext;
        wz1 = oz + ext;
        java.util.Arrays.fill(ok, false);
        java.util.Arrays.fill(made, frame);
        java.util.Arrays.fill(changedAt, frame - SETTLE_FRAMES);
        held = true;
        begun++;
    }

    private void drop() {
        if (held) java.util.Arrays.fill(ok, false);
        held = false;
    }

    /**
     * A far patch arrived (ring, patch): the slices it shows in are drawn again, or, if its land seen from its own
     * distance leaves the band the panorama was sized for (a deep sea floor near the held rings' edge), the panorama
     * starts over. (Its lowest ground against the band's lowest ground started it over ten times a second in a new
     * world's countryside, for far land that lay well inside.)
     */
    void patch(FarField field, int r, int px, int pz, double camX, double camZ) {
        if (!held || r < ring) return;
        int slot = Rings.slot(px, pz), pb = Rings.patchBlocks(r);
        float lo = (field.lo[r][slot] & 0xFFFF) - dev.awaken.farsight.FarColumns.HEIGHT_BASE - 32 - (float) eyeY;
        float hi = field.top(r, slot) - dev.awaken.farsight.FarColumns.HEIGHT_BASE + 32 + 72 - (float) eyeY;
        float ax = (float) ((double) px * pb - camX), az = (float) ((double) pz * pb - camZ), bx = ax + pb, bz = az + pb;
        float nx = Math.max(0, Math.max(ax, -bx)), nz = Math.max(0, Math.max(az, -bz)), d = Math.max(1f, (float) Math.sqrt(nx * nx + nz * nz));
        if (lo < 0 && lo / d * EDGE < vLo || hi > 0 && hi / d * EDGE > vHi) {
            dropBand++;
            drop();
            return;
        }
        changed(ax, az, bx, bz);
    }

    /** Far land changed there (blocks from the camera): the slices it shows in are drawn again. */
    void changed(float ax, float az, float bx, float bz) {
        if (!held || ax >= wx0 && bx <= wx1 && az >= wz0 && bz <= wz1) return;
        int[] span = span(ax, az, bx, bz);
        for (int n = 0, i = span[0]; n <= span[1]; n++, i = (i + 1) % SLICES) {
            ok[i] = false;
            changedAt[i] = frame;
        }
        staled++;
    }

    /** Whether a City Sight chunk (blocks from the camera) is the panorama's: wholly past the real chunks' reach. */
    boolean cachedCity(float ax, float az, float bx, float bz) {
        if (!held) return false;
        float nx = Math.max(0, Math.max(ax, -bx)), nz = Math.max(0, Math.max(az, -bz));
        return nx * nx + nz * nz >= cityFrom * cityFrom;
    }

    /** A City Sight chunk's blocks changed (a mesh up, a dissolve's step, one let go): its slices are drawn again. */
    void cityChanged(float ax, float az, float bx, float bz) {
        if (!cachedCity(ax, az, bx, bz)) return;
        int[] span = span(ax, az, bx, bz);
        for (int n = 0, i = span[0]; n <= span[1]; n++, i = (i + 1) % SLICES) {
            ok[i] = false;
            changedAt[i] = frame;
        }
        staled++;
    }

    /**
     * Whether the panorama draws what it holds in this box (blocks from the camera, not around it) this frame, so the
     * live draw leaves it out: every slice it spans is served, and all of it lies in the cone where a slice holds enough
     * pixels (a cone is convex, so its corners decide).
     */
    boolean serves(float ax, float ay, float az, float bx, float by, float bz) {
        if (served == 0) return false;
        if (coneCos > -1f) {
            for (int c = 0; c < 8; c++) {
                float x = (c & 1) == 0 ? ax : bx, y = (c & 2) == 0 ? ay : by, z = (c & 4) == 0 ? az : bz;
                if (x * axisX + y * axisY + z * axisZ < coneCos * (float) Math.sqrt(x * x + y * y + z * z)) return false;
            }
        }
        int[] span = span(ax, az, bx, bz);
        for (int n = 0, i = span[0]; n <= span[1]; n++, i = (i + 1) % SLICES) if ((served & 1 << i) == 0) return false;
        return true;
    }

    /** Whether a ring is the panorama's this frame (its pieces drawn live only where a slice isn't served). */
    boolean holdsRing(int r) {
        return held && r >= ring;
    }

    private final int[] spanOut = new int[2];

    /** The slices a rectangle (blocks from the camera, not around it) spans: the first and how many more. */
    private int[] span(float ax, float az, float bx, float bz) {
        double a0 = Math.atan2(ax, -az), lo = 0, hi = 0;
        for (int c = 1; c < 4; c++) {
            double a = wrap(Math.atan2(c == 1 || c == 3 ? bx : ax, -(c >= 2 ? bz : az)) - a0);
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
        }
        int first = Math.floorMod((int) Math.floor((a0 + lo) / STEP), SLICES), last = Math.floorMod((int) Math.floor((a0 + hi) / STEP), SLICES);
        spanOut[0] = first;
        spanOut[1] = Math.floorMod(last - first, SLICES);
        return spanOut;
    }

    private static double wrap(double a) {
        a = (a + Math.PI) % (2 * Math.PI);
        if (a < 0) a += 2 * Math.PI;
        return a - Math.PI;
    }

    /** The n-th slice by how near its middle lies to the yaw (0 the nearest). */
    private static int nearest(float yaw, int n) {
        int base = Math.floorMod((int) Math.floor(yaw / STEP), SLICES);
        double frac = yaw / STEP - Math.floor(yaw / STEP);
        // alternate right and left of the slice the view looks into
        int k = (n + 1) / 2;
        boolean right = (n & 1) == 1 ? frac >= 0.5 : frac < 0.5;
        return Math.floorMod(n == 0 ? base : base + (right ? k : -k), SLICES);
    }

    private static boolean close(float[] a, float[] b) {
        for (int i = 0; i < LOOK; i++) if (Math.abs(a[i] - b[i]) > TOLERANCE[i]) return false;
        return true;
    }

    /** The most screen pixels per tan anywhere on screen (its corners: radially f (1 + x² + y²)). */
    private static float maxScreenDensity(float f, float fovDegrees, float aspect) {
        double ty = Math.tan(Math.toRadians(fovDegrees) / 2), tx = ty * aspect;
        return (float) (f * (1 + tx * tx + ty * ty));
    }

    /**
     * Plans this frame's slices (after the screen's City Sight plan, whose levels they share): each slice's rings, trees
     * and City Sight chunks in the cached zone, in its own frustum.
     */
    void planSlices(FarRenderer renderer, FarField field, VoxelLayer voxels, double camX, double camY, double camZ, int realWithin, boolean zeroToOne) {
        for (int k = 0; k < renderCount; k++) {
            sliceProjection(zeroToOne);
            cull.set(proj).mul(sliceRot[renders[k]]);
            FarRenderer.View v = views[k];
            v.frustum.set(cull, false);
            renderer.planView(v, field, ring);
            voxels.planSlice(k, renderer, v.frustum, this, camX, camY, camZ, realWithin);
        }
    }

    private void sliceProjection(boolean zeroToOne) {
        proj.setFrustum(-HALF, HALF, vLo, vHi, 1f, FarRenderer.FAR, zeroToOne);
    }

    /**
     * Draws this frame's slices (before the screen's pass, outside any pass): each slice's region cleared, then its rings,
     * City Sight and trees through a viewport on it, with the screen's frame block but the slice's rotation.
     */
    void render(FarRenderer renderer, FarField field, VoxelLayer voxels, ByteBuffer frameBytes, GpuBuffer draws, GpuBuffer treeCommands,
            GpuTextureView lightmap, boolean zeroToOne) {
        if (renderCount == 0) return;
        try {
            GpuBuffer fb = frames.currentBuffer(), pb = projections.currentBuffer();
            try (var view = fb.map(false, true)) {
                ByteBuffer d = view.data();
                for (int k = 0; k < renderCount; k++) {
                    int at = k * frameStride;
                    d.put(at, frameBytes, 0, FarRenderer.FRAME_SIZE);
                    sliceRot[renders[k]].get(at, d);
                    // FarView: the screen's footprint per slice pixel (so the columns are as wide as on screen), drawing a slice
                    d.putFloat(at + FarRenderer.FRAME_SIZE - 16, ratio).putFloat(at + FarRenderer.FRAME_SIZE - 12, 1f);
                }
            }
            // each slice through the pass's own viewport (the whole atlas), its projection squeezed onto its strip: the
            // Vulkan backend's setViewport leaves the depth range at 0..0, so every slice drew at depth 0 (no land)
            try (var view = pb.map(false, true)) {
                for (int k = 0; k < renderCount; k++) {
                    sliceProjection(zeroToOne);
                    float scale = sliceW / (float) atlasW, offset = (2f * renders[k] * sliceW + sliceW) / atlasW - 1f;
                    strip.identity().m00(scale).m30(offset).mul(proj).get(k * projStride, view.data());
                }
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (int k = 0; k < renderCount; k++) {
                int i = renders[k], x0 = i * sliceW;
                slicePieces += views[k].pieces;
                sliceTrees += views[k].treeDraws;
                encoder.clearColorAndDepthTextures(colour, clear, depth, 0.0, x0, 0, sliceW, atlasH, 0);
                GpuBufferSlice frame = fb.slice((long) k * frameStride, FarRenderer.FRAME_SIZE), projection = pb.slice((long) k * projStride, 64);
                try (RenderPass pass = encoder.createRenderPass(() -> "awaken far horizon slice", colourView, Optional.empty(), depthView, OptionalDouble.empty())) {
                    pass.enableScissor(x0, 0, sliceW, atlasH);
                    renderer.terrain(pass, views[k], k + 1, field, projection, frame, draws, lightmap, voxels, 0, Rings.RINGS - 1);
                    voxels.drawSlice(k, pass, projection, frame, lightmap, renderer.sectionsView);
                    renderer.trees(pass, views[k], k + 1, field, projection, frame, treeCommands, lightmap, voxels);
                }
            }
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** First in the screen's pass (its depth just cleared): the served slices onto the screen, colour and Far Sight's depth. */
    void composite(RenderPass pass, GpuBuffer frame, float a, float b, boolean zeroToOne, float pixelsPerTan, int width, int height) {
        if (served == 0 || broken) return;
        try {
            GpuBuffer u = composite.currentBuffer();
            try (var view = u.map(false, true)) {
                ByteBuffer d = view.data();
                d.putFloat(0, HALF).putFloat(4, vLo).putFloat(8, vHi).putFloat(12, SLICES);
                d.putFloat(16, sliceW).putFloat(20, atlasH).putFloat(24, atlasW).putFloat(28, density * MAX_MAG);
                d.putFloat(32, a).putFloat(36, b).putFloat(40, zeroToOne ? 1f : 0f).putFloat(44, BIAS);
                d.putFloat(48, pixelsPerTan).putFloat(52, width).putFloat(56, height).putFloat(60, 0f);
                d.putInt(64, served).putInt(68, 0).putInt(72, 0).putInt(76, 0);
                boolean whole = FarSightClient.debug == 6;
                d.putFloat(80, whole ? -1f : rowLo).putFloat(84, whole ? 1f : rowHi).putFloat(88, 0f).putFloat(92, 0f);
            }
            pass.setPipeline(RenderSystem.getCompiledPipeline(FarPipelines.HORIZON));
            pass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
            pass.setUniform("FarFrame", frame);
            pass.setUniform("Horizon", u);
            pass.setUniform("HorizonColorSampler", colourView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setUniform("HorizonDepthSampler", depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            // a quad over the band's rows (the screen over and under it has nothing of the panorama's)
            pass.draw(6, 1, 0, 0);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** The frame isn't drawn after all: the slices planned for it stay to be drawn. */
    void cancel() {
        for (int k = 0; k < renderCount; k++) ok[renders[k]] = false;
        renderCount = 0;
        served = 0;
    }

    /** After the frame's passes: the ring buffers move on. */
    void endFrame() {
        frames.rotate();
        projections.rotate();
        composite.rotate();
    }

    boolean serving() {
        return served != 0;
    }

    int renderCount() {
        return renderCount;
    }

    private void fail(RuntimeException e) {
        if (broken) return;
        broken = true;
        drop();
        Awaken.LOG.warn("[farsight] cached horizon off after an error; Far Sight draws everything live", e);
    }

    /** Status: whether it's on, the frames served and live, slices drawn and panoramas begun since the last call, its memory. */
    String status() {
        String s = String.format(java.util.Locale.ROOT,
                " horizon=%s ring=%d served=%d live=%d slices=%d (%.1f pieces, %.1f tree runs each) begun=%d (dropped: eye %d, view %d, windows %d, band %d; staled %d) held=%s band=[%.3f %.3f] density=%.0f eps=%.3f vram=%dMB",
                !enabled() ? "off" : broken ? "broken" : "on", ring, framesServed, framesLive, slicesDrawn, slicesDrawn == 0 ? 0f : slicePieces / (float) slicesDrawn,
                slicesDrawn == 0 ? 0f : sliceTrees / (float) slicesDrawn, begun, dropEye, dropView, dropWindows, dropBand, staled, held, vLo, vHi, density, eps,
                memoryBytes() >> 20);
        framesServed = framesLive = slicesDrawn = begun = dropEye = dropView = dropWindows = dropBand = staled = slicePieces = sliceTrees = 0;
        return s;
    }

    long memoryBytes() {
        return colour == null ? 0 : (long) atlasW * atlasH * 8;
    }

    private void free() {
        if (colourView != null) colourView.close();
        if (depthView != null) depthView.close();
        if (colour != null) colour.close();
        if (depth != null) depth.close();
        colour = depth = null;
        colourView = depthView = null;
        atlasW = atlasH = 0;
    }

    @Override
    public void close() {
        free();
        frames.close();
        projections.close();
        composite.close();
    }
}
