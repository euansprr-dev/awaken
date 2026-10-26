package dev.awaken.gates.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.awaken.Awaken;
import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

/**
 * Light pillars over every known gate, coloured by rank, visible up to 1500 blocks. Pillars beyond a safe distance are
 * drawn at a clamped distance along the view ray and scaled down by the same factor, so they look exactly as far away as
 * they are but never fall past the far plane or into the fog.
 */
public final class Pillars {
    private Pillars() {}

    private record Draw(double dx, double dy, double dz, float scale, int height, int core, int glow, float solid, float halo) {}

    private static final ContextKey<List<Draw>> KEY = new ContextKey<>(Awaken.id("gate_pillars"));
    public static final int HEIGHT = 900;

    static void onExtract(ExtractLevelRenderStateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        List<GatePayloads.Beacon> beacons = ClientGates.beacons();
        if (beacons.isEmpty()) return;
        Vec3 cam = e.getCamera().position();
        double clamp = Math.max(48, mc.options.getEffectiveRenderDistance() * 16 * 0.7);
        List<Draw> out = new ArrayList<>();
        for (GatePayloads.Beacon b : beacons) {
            // the beam rises from the top of the vortex, which grows with rank
            var size = dev.awaken.gates.GateVortex.size(b.rank());
            double bx = b.pos().getX() + 0.5, by = b.pos().getY() + size.centreY() + size.ry() * 1.08, bz = b.pos().getZ() + 0.5;
            double rx = bx - cam.x, ry = by - cam.y, rz = bz - cam.z;
            double dist = Math.sqrt(rx * rx + rz * rz + ry * ry);
            if (dist > GatePayloads.BEACON_RANGE + 100) continue;
            float k = dist > clamp ? (float) (clamp / dist) : 1F;
            float widen = (float) Math.max(1.0, Math.sqrt(rx * rx + rz * rz) / 96.0);
            // off screen (behind the camera, say): no pass of its own on the main target this frame, which on a tiled
            // GPU costs a load and store of colour and depth at 4K. The box holds the widest ribbon (the haze, 16 × halo)
            double r = 0.32 * widen * 16 * k + 2, px = cam.x + rx * k, py = cam.y + ry * k, pz = cam.z + rz * k;
            if (!e.getFrustum().isVisible(new net.minecraft.world.phys.AABB(px - r, py - 2, pz - r, px + r, py + HEIGHT * k, pz + r))) continue;
            int colour = ClientGates.rankColour(b.rank());
            int core = colour, glow = colour;
            if (b.rank() == 6) {
                core = 0xFF0C0A08;
                glow = 0xFFD4AF37;
            }
            if (b.broken()) {
                core = 0xFF7A1018;
                glow = 0xFF5A5A5A;
            }
            if ((b.flags() & GatePayloads.Beacon.RED) != 0 && !b.broken()) glow = 0xFFFF2030;
            out.add(new Draw(rx * k - 0.5, ry * k, rz * k - 0.5, k, HEIGHT, core, glow, 0.22F * widen, 0.32F * widen));
        }
        e.getRenderState().setRenderData(KEY, out);
    }

    /**
     * The pillar itself (Euan, 2026-10-26: vanilla's beacon beam read as "a weird boxed outline" over Carillon, its halo a
     * flat translucent square column; then, of our first ribbons, "the outline of the main light is still there and
     * blocky"): soft light, never a box. Ribbons turned to face the camera about the pillar's axis, each with a bell-shaped
     * falloff across its width (several columns per side, the last at nothing with no slope, so no edge shows; a straight
     * ramp from the middle to the edge read as a hard-edged slab): a near-white core, the rank's glow, a wide haze. All
     * fade in at the foot and thin out with height to nothing well under the far plane, without the world's fog (it
     * counts height as distance, and cut the column off flat at the fog line; its blend also greyed the haze), and each
     * fades out as the camera comes inside its width. The core and glow are light added, full at
     * night; the haze is coloured air, alpha-blended, which carries the pillar against a daylight sky.
     * <p>Drawn after the level, in a pass of its own on the main target (depth-tested, no depth written), not through the
     * level's translucent passes: with improved transparency on, their composite writes the nearest translucent depth
     * into the main depth wherever a translucent shows, however faint, so the whole quad of every ribbon read as solid to
     * Lens, which left its clouds out there (the Far Sight tour's frames at the Roost: a hard-edged slab of plain sky
     * around the core). After the level, the pillar is sky to Lens and its clouds pass in front. Nothing allocated.
     */
    static void onAfterLevel(RenderLevelStageEvent.AfterLevel e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float time = (e.getLevelRenderState().gameTime % 24000) + e.getLevelRenderState().worldPartialTicks;
        float dark = mc.level == null ? 1F : mc.level.getSkyDarken() / 11F;
        float day = 0.4F + 0.6F * dark; // 0.4 at noon, 1 at night
        // the far plane is at least four render distances out (Camera.update): every vertex stays well inside it
        float far = mc.options.getEffectiveRenderDistance() * 16 * 4 * 0.85F;
        GpuBuffer buf = ring.currentBuffer();
        int hazeQuads, allQuads;
        try (GpuBufferSlice.MappedView view = buf.map(false, true)) {
            at = MemoryUtil.memAddress(view.data());
            end = at + (long) MAX_QUADS * 64;
            for (Draw d : draws) pillar(d, time, day, far, false);
            hazeQuads = quads(view);
            for (Draw d : draws) pillar(d, time, day, far, true);
            allQuads = quads(view);
        }
        if (allQuads > 0) {
            var glow = RenderSystem.getCompiledPipelineNullable(RigPipelines.GLOW);
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indices = quadIndex.getBuffer(allQuads * 6);
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "awaken gate pillars",
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

    /** Room for every pillar's quads (a far pillar takes ~400, a near one ~800); past it the rest are left out. */
    private static final int MAX_QUADS = 24576;
    private static final MappableRingBuffer ring = new MappableRingBuffer(() -> "awaken gate pillars",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, MAX_QUADS * 64);
    /** Where the next vertex goes in the mapped buffer, and where it ends. */
    private static long at, end;

    private static int quads(GpuBufferSlice.MappedView view) {
        return (int) ((at - MemoryUtil.memAddress(view.data())) / 64);
    }

    /** One vertex: position and colour (r, g, b, a bytes), as POSITION_COLOR lays them out. */
    private static void vertex(float x, float y, float z, int argb) {
        MemoryUtil.memPutFloat(at, x);
        MemoryUtil.memPutFloat(at + 4, y);
        MemoryUtil.memPutFloat(at + 8, z);
        MemoryUtil.memPutInt(at + 12, (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
        at += 16;
    }

    /** Rows up the column (denser near the foot, where it is seen from close by). */
    private static final float[] ROWS = {0F, 0.004F, 0.012F, 0.025F, 0.045F, 0.07F, 0.1F, 0.14F, 0.19F, 0.25F, 0.32F, 0.4F, 0.5F, 0.6F,
            0.7F, 0.8F, 0.9F, 1F};
    /** The falloff across a ribbon, (1 - x²)³ at x = i / n from the middle out: bell-shaped, nothing and flat at the edge. */
    private static final float[] NEAR = bell(6), FAR = bell(3);

    private static float[] bell(int n) {
        float[] w = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float x = i / (float) n, v = 1F - x * x;
            w[i] = v * v * v;
        }
        return w;
    }

    private static void pillar(Draw d, float time, float day, float far, boolean light) {
        float k = d.scale();
        float bx = (float) d.dx() + 0.5F * k, by = (float) d.dy(), bz = (float) d.dz() + 0.5F * k;
        // the ribbons turn about the pillar's axis to face the camera (the camera is at the origin)
        float tx = -bx, tz = -bz, tl = (float) Math.sqrt(tx * tx + tz * tz);
        if (tl < 1e-4F) return;
        float rx = -tz / tl, rz = tx / tl;
        // its top stays inside the far plane (vanilla's beam, and our first pillar, were cut off flat there)
        float h = Math.min(d.height() * k, (float) Math.sqrt(Math.max(0F, far * far - tl * tl)) - by);
        if (h < 8F) return;
        float[] bell = k < 0.999F ? FAR : NEAR;
        float breath = 0.9F + 0.1F * Mth.sin(time * 0.045F + bx * 0.01F);
        if (light) {
            // the core: thin and hot, light added (Lens's bloom takes it); stronger at night; it reaches highest
            ribbon(bell, bx, by, bz, rx, rz, h, d.solid() * 1.4F * k, brighten(d.core()), (0.55F + 0.45F * day) * breath, time, 0.6F, 1F);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 3.5F * k, airy(d.glow(), day), 0.35F * day * breath, time, 1.0F, 0.8F);
        } else {
            // the glow and the haze in the rank's colour: by day they carry the pillar, at night the light does (pillar
            // tour: added light vanished against a noon sky); they thin out sooner than the core
            float tint = 1.15F - day;
            int air = airy(d.glow(), day);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 6F * k, air, 0.5F * tint * breath, time, 1.0F, 0.65F);
            ribbon(bell, bx, by, bz, rx, rz, h, d.halo() * 16F * k, air, 0.18F * tint, time, 0.4F, 0.5F);
        }
    }

    /**
     * One soft ribbon: per row, the bell's columns from the middle out on each side. {@code reach}: the share of the
     * height where it has thinned to nothing (with no slope there either, so no top shows).
     */
    private static void ribbon(float[] bell, float bx, float by, float bz, float rx, float rz, float h, float half,
                               int argb, float strength, float time, float shimmer, float reach) {
        int n = bell.length - 1;
        // from inside or beside a ribbon it would hang across the view as a sheet (Euan, at Carillon and under the
        // Wayfarers' canopy at dusk: planes and a cone in the sky): it fades out as the camera comes within twice its
        // half-width, so up close only the core is left, a soft line
        float cx = bx, cz = bz, near = Mth.clamp((float) (Math.sqrt(cx * cx + cz * cz) - half * 0.5F) / (half * 1.5F), 0F, 1F);
        strength *= near * near * (3F - 2F * near);
        if (strength < 0.004F) return;
        float pa = 0;
        for (int i = 0; i < ROWS.length; i++) {
            float f = ROWS[i];
            if (i > 0 && ROWS[i - 1] >= reach) break;
            float y = by + f * h;
            // fade in over the foot (the first few blocks), thin out with height; rising bands of light
            float foot = Mth.clamp(f * h / 6F, 0F, 1F), up = Mth.clamp(1F - f / reach, 0F, 1F);
            float bands = 1F - shimmer * 0.22F * (0.5F + 0.5F * Mth.sin(f * h * 0.08F - time * 0.09F));
            float a = strength * foot * up * up * (3F - 2F * up) * bands;
            if (i > 0) {
                float y0 = by + ROWS[i - 1] * h;
                if (at + (long) n * 128 > end) return;
                for (int j = 0; j < n; j++) {
                    float x0 = half * j / n, x1 = half * (j + 1) / n;
                    int b0 = col(argb, pa * bell[j]), b1 = col(argb, a * bell[j]), o0 = col(argb, pa * bell[j + 1]), o1 = col(argb, a * bell[j + 1]);
                    for (int side = -1; side <= 1; side += 2) {
                        float ix = rx * x0 * side, iz = rz * x0 * side, ox = rx * x1 * side, oz = rz * x1 * side;
                        vertex(bx + ix, y0, bz + iz, b0);
                        vertex(bx + ox, y0, bz + oz, o0);
                        vertex(bx + ox, y, bz + oz, o1);
                        vertex(bx + ix, y, bz + iz, b1);
                    }
                }
            }
            pa = a;
        }
    }

    /** The colour with the strength as its alpha (the additive blend weighs the colour by it). */
    private static int col(int rgb, float a) {
        return Mth.clamp((int) (a * 255F), 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    /**
     * By day the glow is lit air, paler than the sky behind it: the rank's colour lifted toward white (CI tour: a C-rank
     * haze in its own pale blue, darker than a noon sky, blended away to nothing and left a thread); at night its own.
     */
    private static int airy(int c, float day) {
        float lift = 0.55F * Mth.clamp((1F - day) / 0.6F, 0F, 1F);
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return 0xFF000000 | (int) (r + (255 - r) * lift) << 16 | (int) (g + (255 - g) * lift) << 8 | (int) (b + (255 - b) * lift);
    }

    /** Toward white: the core burns hotter than its glow (a dark core, the S-rank's, stays dark). */
    private static int brighten(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        if (r + g + b < 120) return c;
        return 0xFF000000 | Math.min(255, r + (255 - r) * 2 / 3) << 16 | Math.min(255, g + (255 - g) * 2 / 3) << 8 | Math.min(255, b + (255 - b) * 2 / 3);
    }
}
