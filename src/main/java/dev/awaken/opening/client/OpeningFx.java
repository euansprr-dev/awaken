package dev.awaken.opening.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The opening's light in the world: the column falling into Choir Square and standing there through the run (visible
 * from anywhere in the city), the shockwaves that cross the roofs (the light landing, the pulse that drops the Bell
 * Bridge, the arrival's bursts), the portal blooming at the arrival, the Runner's eye on a hold, and the Assessment
 * Lens's shard with its scan. Fixed pools; a frame allocates only a few small vectors while something is drawn.
 * <p>Drawn after the level, camera-relative, in a pass of its own on the main target (depth-tested, no depth written, no
 * fog), as the gate pillars are (SPEC §7): inside the level the column's translucent body read to Lens as solid, a
 * pale slab with hard sides and a flat top over Choir Square where its clouds and sky gave way (Euan, from the Roost at
 * noon: "the light pillar is STILL not fixed"), and the fog cut its light off flat. Its haze is alpha-blended (it
 * carries the column against a daylight sky); everything else is light added (Lens blooms it).
 */
final class OpeningFx {
    private OpeningFx() {}

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

    /** The light added, without the world's fog (it counts height as distance and cut the column off flat). */
    static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Awaken.id("pipeline/opening_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();

    static void registerPipelines(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(GLOW);
    }

    /** The key glyph over the Runner's eye: text, so it stays in the level's own pass. */
    static void submit(SubmitCustomGeometryEvent e) {
        if (glyph == null || eyeAt == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        glyphText(e.getPoseStack(), e.getSubmitNodeCollector(), mc);
    }

    /** Room for every quad of a frame (the column ~1300, a wide ring ~320, twelve of them at most); past it, left out. */
    private static final int MAX_QUADS = 16384;
    private static @Nullable MappableRingBuffer ring;
    /** Where the next vertex goes in the mapped buffer, and where it ends. */
    private static long at, end;

    static void afterLevel(RenderLevelStageEvent.AfterLevel e) {
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
        float t = OpeningClient.ticks() + pt;
        // 0.4 at noon, 1 at night: by day the haze carries the column (light added vanishes against the sky)
        float day = 0.4F + 0.6F * mc.level.getSkyDarken() / 11F;
        // the far plane is at least four render distances out (Camera.update): every vertex stays well inside it
        float far = mc.options.getEffectiveRenderDistance() * 16 * 4 * 0.85F;
        if (ring == null) ring = new MappableRingBuffer(() -> "awaken opening light", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, MAX_QUADS * 64);
        GpuBuffer buf = ring.currentBuffer();
        int hazeQuads, allQuads;
        try (GpuBufferSlice.MappedView view = buf.map(false, true)) {
            long base = MemoryUtil.memAddress(view.data());
            at = base;
            end = base + (long) MAX_QUADS * 64;
            try {
                if (columnOn) column(t, day, far, true);
                if (lens != null) lensFacets(t);
                hazeQuads = (int) ((at - base) / 64);
                if (columnOn) column(t, day, far, false);
                draw(t);
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[opening] fx failed", ex);
                hazeQuads = 0;
                at = base;
            }
            allQuads = (int) ((at - base) / 64);
        }
        if (allQuads > 0) {
            var glow = RenderSystem.getCompiledPipelineNullable(GLOW);
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indices = quadIndex.getBuffer(allQuads * 6);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "awaken opening light",
                    main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(e.getModelViewMatrix())));
                pass.setIndexBuffer(indices, quadIndex.type());
                pass.setVertexBuffer(0, buf.slice());
                if (hazeQuads > 0) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.DEBUG_QUADS));
                    pass.drawIndexed(hazeQuads * 6, 1, 0, 0, 0);
                }
                if (allQuads > hazeQuads) {
                    pass.setPipeline(glow != null ? glow : RenderSystem.getCompiledPipeline(RenderPipelines.DEBUG_QUADS));
                    pass.drawIndexed((allQuads - hazeQuads) * 6, 1, 0, hazeQuads * 4, 0);
                }
            }
        }
        ring.rotate();
    }

    private static boolean ringsLive() {
        long now = OpeningClient.ticks();
        for (int i = 0; i < RINGS; i++) if (rLife[i] > 0 && now - rFrom[i] <= rLife[i]) return true;
        return false;
    }

    /** The light added beside the column's: the rings, the eye, the shard. */
    private static void draw(float t) {
        for (int i = 0; i < RINGS; i++) {
            if (rLife[i] <= 0) continue;
            float age = t - rFrom[i];
            if (age < 0 || age > rLife[i]) continue;
            float r = Math.min(rMax[i], r0[i] + rSpeed[i] * age);
            float a = 1 - age / rLife[i];
            int argb = scaleAlpha(rColour[i], a * a);
            flatRing(rx[i], ry[i], rz[i], r, Math.max(0.6F, r * 0.035F), argb);
        }
        if (eyeAt != null) runnersEye(t);
        if (lens != null) lensShard(t);
    }

    /**
     * The column, from the sky to the square, beating slowly: a near-white core and a blue glow (light added), and a wide
     * haze (alpha-blended: by day, lit air paler than the sky). Each is a ribbon turned to the camera about the column's
     * axis with a bell-shaped falloff across it, so it melts into the sky with no edge; each thins to nothing with height
     * and fades out as the camera comes inside its width, so from the square only the core is left, a soft line.
     */
    private static void column(float t, float day, float far, boolean haze) {
        float since = t - fellAt;
        double baseY = column.y;
        // falling: the bottom races down from the sky in eight ticks
        double bottom = since < 8 ? TOP - (TOP - baseY) * Mth.clamp(since / 8F, 0, 1) : baseY;
        float beat = 0.82F + 0.18F * Mth.sin(t * 0.13F) + surge + bloom * 0.8F;
        float land = since >= 8 && since < 30 ? 1 - (since - 8) / 22F : 0;
        // the column widens a little with distance so it reads from the far side of the city
        double vx = column.x - cx, vz = column.z - cz, d = Math.sqrt(vx * vx + vz * vz);
        float wide = (float) Mth.clamp(d / 120.0, 1.0, 2.6);
        if (d < 1e-4) return;
        float rxv = (float) (-vz / d), rzv = (float) (vx / d);
        // its top stays inside the far plane
        double top = Math.min(TOP, cy + Math.sqrt(Math.max(0, far * far - d * d)));
        float h = (float) (top - bottom);
        if (h < 4) return;
        float bx = (float) (column.x - cx), by = (float) (bottom - cy), bz = (float) (column.z - cz);
        float foot = since < 8 ? 2F : 6F;
        if (haze) {
            float tint = 1.15F - day;
            int air = airy(0x8FC8FF, day);
            ribbon(NEAR, bx, by, bz, rxv, rzv, h, (5.5F + land * 5 + bloom * 4) * wide, air, Math.min(0.6F, 0.5F * tint * beat), t, 1F, 0.6F, foot);
            ribbon(NEAR, bx, by, bz, rxv, rzv, h, (13F + bloom * 7) * wide, air, Math.min(0.25F, 0.18F * tint * beat), t, 0.4F, 0.45F, foot);
            return;
        }
        ribbon(NEAR, bx, by, bz, rxv, rzv, h, (1.2F + land * 3 + bloom * 2) * wide * 0.7F, 0xFFF4FAFF, Math.min(1F, (0.6F + 0.4F * day) * beat), t, 0.6F, 1F, foot);
        ribbon(NEAR, bx, by, bz, rxv, rzv, h, (4F + land * 7 + bloom * 4.5F) * wide, airy(0x8FC8FF, day), Math.min(0.8F, 0.35F * day * beat + land * 0.3F), t, 1F, 0.85F, foot);
        if (since >= 8) {
            // where it meets the ground: a pool of light and a flare
            float pool = 4.5F + 1.2F * Mth.sin(t * 0.21F) + land * 10 + bloom * 9;
            disc(column.x, baseY + 0.08, column.z, pool, argb(Math.min(1F, 0.5F * beat), 0xCFE8FF));
            flare(column.x, baseY + 1.2, column.z, 3.5F + land * 14 + bloom * 6, argb(Math.min(0.8F, 0.5F * beat + land), 0xFFFFFF));
            // motes rising along it
            for (int i = 0; i < 26; i++) {
                float k = (i * 0.618034F + t * 0.006F * (1 + (i % 3))) % 1F;
                double y = baseY + k * 90;
                double ang = i * 2.39996 + t * 0.03;
                double rr = 1.2 + (i % 5) * 0.7 + bloom * 3;
                flare(column.x + Math.cos(ang) * rr, y, column.z + Math.sin(ang) * rr, 0.25F + (i % 4) * 0.08F, argb(0.7F * (1 - k), 0xDDEEFF));
            }
        }
    }

    /** Rows up the column (denser near the foot, where it is seen from close by). */
    private static final float[] ROWS = {0F, 0.004F, 0.012F, 0.025F, 0.045F, 0.07F, 0.1F, 0.14F, 0.19F, 0.25F, 0.32F, 0.4F, 0.5F, 0.6F,
            0.7F, 0.8F, 0.9F, 1F};
    /** The falloff across a ribbon, (1 - x²)³ at x = i / 6 from the middle out: bell-shaped, nothing and flat at the edge. */
    private static final float[] NEAR = new float[7];

    static {
        for (int i = 0; i <= 6; i++) {
            float x = i / 6F, v = 1F - x * x;
            NEAR[i] = v * v * v;
        }
    }

    /**
     * One soft ribbon up the column: per row, the bell's columns from the middle out on each side, fading in over the
     * foot, thinning to nothing at {@code reach} of the height (no slope there, so no top shows), rising bands of light.
     */
    private static void ribbon(float[] bell, float bx, float by, float bz, float rx, float rz, float h, float half, int argb,
                               float strength, float t, float shimmer, float reach, float footLen) {
        int n = bell.length - 1;
        // from inside or beside it, it would hang across the view as a sheet: it fades out as the camera comes within
        // twice its half-width
        float near = Mth.clamp(((float) Math.sqrt(bx * bx + bz * bz) - half * 0.5F) / (half * 1.5F), 0F, 1F);
        strength *= near * near * (3F - 2F * near);
        if (strength < 0.004F) return;
        float pa = 0;
        for (int i = 0; i < ROWS.length; i++) {
            float f = ROWS[i];
            if (i > 0 && ROWS[i - 1] >= reach) break;
            float y = by + f * h;
            float foot = Mth.clamp(f * h / footLen, 0F, 1F), up = Mth.clamp(1F - f / reach, 0F, 1F);
            float bands = 1F - shimmer * 0.22F * (0.5F + 0.5F * Mth.sin(f * h * 0.08F - t * 0.09F));
            float a = strength * foot * up * up * (3F - 2F * up) * bands;
            if (i > 0) {
                if (at + (long) n * 128 > end) return;
                float y0 = by + ROWS[i - 1] * h;
                for (int j = 0; j < n; j++) {
                    float x0 = half * j / n, x1 = half * (j + 1) / n;
                    int b0 = scaleAlpha(argb, pa * bell[j]), b1 = scaleAlpha(argb, a * bell[j]);
                    int o0 = scaleAlpha(argb, pa * bell[j + 1]), o1 = scaleAlpha(argb, a * bell[j + 1]);
                    for (int side = -1; side <= 1; side += 2) {
                        float ix = rx * x0 * side, iz = rz * x0 * side, ox = rx * x1 * side, oz = rz * x1 * side;
                        put(bx + ix, y0, bz + iz, b0);
                        put(bx + ox, y0, bz + oz, o0);
                        put(bx + ox, y, bz + oz, o1);
                        put(bx + ix, y, bz + iz, b1);
                    }
                }
            }
            pa = a;
        }
    }

    /** By day the column's glow is lit air, its colour lifted toward white (paler than the sky); at night its own. */
    private static int airy(int c, float day) {
        float lift = 0.55F * Mth.clamp((1F - day) / 0.6F, 0F, 1F);
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return 0xFF000000 | (int) (r + (255 - r) * lift) << 16 | (int) (g + (255 - g) * lift) << 8 | (int) (b + (255 - b) * lift);
    }

    /** One vertex, camera-relative: position and colour (r, g, b, a bytes), as POSITION_COLOR lays them out. */
    private static void put(float x, float y, float z, int argb) {
        MemoryUtil.memPutFloat(at, x);
        MemoryUtil.memPutFloat(at + 4, y);
        MemoryUtil.memPutFloat(at + 8, z);
        MemoryUtil.memPutInt(at + 12, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
        at += 16;
    }

    /** The bronze pulse on the next hold: twice, soft, then gone (2.5 s); the key glyph floats over it. */
    private static void runnersEye(float t) {
        float age = t - eyeFrom;
        if (age > 60 || eyeAt == null) return;
        float pulse = Mth.sin(Math.min(1, age / 60F) * Mth.PI * 2.5F);
        float a = Math.max(0, pulse) * Mth.clamp((60 - age) / 12F, 0, 1);
        Vec3 p = eyeAt;
        flare(p.x, p.y, p.z, 0.9F + 0.6F * a, argb(0.9F * a, 0xE0B070));
        flatRing(p.x, p.y - 0.45, p.z, 0.6F + age * 0.03F, 0.08F, argb(0.8F * a, 0xE0B070));
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

    /**
     * The shard's facets, alpha-blended (the haze's pass), each shaded as if lit from above so it reads as cut crystal: as
     * light added, front and back faces summed to a flat white diamond at noon.
     */
    private static void lensFacets(float t) {
        Vec3 c = lens;
        if (c == null) return;
        double bob = Math.sin(t * 0.07) * 0.08;
        float glow = 0.55F + lensGlow * 0.45F;
        double s = 0.42, ang = t * 0.04;
        Vec3 top = c.add(0, s * 1.6 + bob, 0), bot = c.add(0, -s * 1.6 + bob, 0);
        Vec3[] ring = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = ang + i * Math.PI / 2;
            ring[i] = c.add(Math.cos(a) * s, bob, Math.sin(a) * s);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 r1 = ring[i], r2 = ring[(i + 1) % 4];
            // the face's turn toward a light up and to the north-west: the upper faces bright, the lower in shade
            double mx = (r1.x + r2.x) * 0.5 - c.x, mz = (r1.z + r2.z) * 0.5 - c.z;
            float lit = (float) (0.5 + 0.5 * (-mx - mz) / (Math.sqrt(mx * mx + mz * mz) * Math.sqrt(2) + 1e-6));
            tri(top, r1, r2, argb(0.62F * glow, mix(0x9CC4EE, 0xF2F8FF, 0.35F + 0.65F * lit)));
            tri(bot, r2, r1, argb(0.62F * glow, mix(0x46689C, 0xA8CCF0, lit)));
        }
    }

    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** The shard of the Choir over its pedestal: a turning octahedron of light; the scan sweeps head to foot. */
    private static void lensShard(float t) {
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
        int edge = argb(0.9F * glow, 0xEAF4FF);
        for (int i = 0; i < 4; i++) {
            Vec3 r1 = ring[i], r2 = ring[(i + 1) % 4];
            beam(top, r1, 0.03F, edge);
            beam(bot, r1, 0.03F, edge);
            beam(r1, r2, 0.03F, edge);
        }
        flare(c.x, c.y + bob, c.z, 1.4F + lensGlow * 2.5F, argb(0.3F * glow, 0x9ED2FF));
        // the scan: a ring of light sweeping down over whoever stands to be read, twice
        float age = t - scanFrom;
        if (age >= 0 && age < 90) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                float k = (age % 45F) / 45F;
                double py = mc.player.getY() + 2.0 - k * 2.1;
                double px = Mth.lerp(pt, mc.player.xo, mc.player.getX()), pz = Mth.lerp(pt, mc.player.zo, mc.player.getZ());
                flatRing(px, py, pz, 0.75F, 0.07F, argb(0.85F * (1 - Math.abs(k - 0.5F)), 0xBFE3FF));
                beam(c.add(0, bob, 0), new Vec3(px, py, pz), 0.05F, argb(0.35F, 0xBFE3FF));
            }
        }
    }

    // ---- primitives -------------------------------------------------------------------------------------------------

    private static final float[] QX = new float[4], QY = new float[4], QZ = new float[4];
    private static final int[] QC = new int[4];
    private static int qn;

    /** A quad's corner, camera-relative; a whole quad goes in at once (the pass draws both faces: no culling). */
    private static void v(double x, double y, double z, int argb) {
        QX[qn] = (float) (x - cx);
        QY[qn] = (float) (y - cy);
        QZ[qn] = (float) (z - cz);
        QC[qn] = argb;
        if (++qn < 4) return;
        qn = 0;
        if (at + 64 > end) return;
        for (int i = 0; i < 4; i++) put(QX[i], QY[i], QZ[i], QC[i]);
    }

    private static void tri(Vec3 a, Vec3 b, Vec3 c, int argb) {
        v(a.x, a.y, a.z, argb);
        v(b.x, b.y, b.z, argb);
        v(c.x, c.y, c.z, argb);
        v(c.x, c.y, c.z, argb);
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
    private static void beam(Vec3 a, Vec3 b, float width, int argb) {
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
        v(a.x, a.y, a.z, argb);
        v(b.x, b.y, b.z, argb);
        v(b.x + nx, b.y + ny, b.z + nz, r);
        v(a.x + nx, a.y + ny, a.z + nz, r);
        v(a.x - nx, a.y - ny, a.z - nz, r);
        v(b.x - nx, b.y - ny, b.z - nz, r);
        v(b.x, b.y, b.z, argb);
        v(a.x, a.y, a.z, argb);
    }

    /** A soft round glow facing the camera. */
    private static void flare(double px, double py, double pz, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rr = rim(argb);
        int n = r < 1.0F ? 6 : r < 4F ? 10 : 14;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            v(px, py, pz, argb);
            v(px, py, pz, argb);
            v(px + RIGHT.x * c1 + UP.x * s1, py + RIGHT.y * c1 + UP.y * s1, pz + RIGHT.z * c1 + UP.z * s1, rr);
            v(px + RIGHT.x * c0 + UP.x * s0, py + RIGHT.y * c0 + UP.y * s0, pz + RIGHT.z * c0 + UP.z * s0, rr);
        }
    }

    /** A flat glowing disc on the ground (bright middle). */
    private static void disc(double px, double py, double pz, float r, int argb) {
        int rr = rim(argb);
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            v(px, py, pz, argb);
            v(px, py, pz, argb);
            v(px + Math.cos(a1) * r, py, pz + Math.sin(a1) * r, rr);
            v(px + Math.cos(a0) * r, py, pz + Math.sin(a0) * r, rr);
        }
    }

    /** A flat ring (a shockwave): bright on its circle, soft either side. */
    private static void flatRing(double px, double py, double pz, float r, float half, int argb) {
        if (r <= 0.01F || (argb >>> 24) == 0) return;
        int rr = rim(argb);
        int n = Math.max(24, Math.min(160, (int) (r * 2)));
        float ri = Math.max(0, r - half), ro = r + half;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            v(px + c0 * ri, py, pz + s0 * ri, rr);
            v(px + c1 * ri, py, pz + s1 * ri, rr);
            v(px + c1 * r, py, pz + s1 * r, argb);
            v(px + c0 * r, py, pz + s0 * r, argb);
            v(px + c0 * r, py, pz + s0 * r, argb);
            v(px + c1 * r, py, pz + s1 * r, argb);
            v(px + c1 * ro, py, pz + s1 * ro, rr);
            v(px + c0 * ro, py, pz + s0 * ro, rr);
        }
    }
}
