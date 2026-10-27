package dev.awaken.atlas.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.atlas.Discovery;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Paints one picture of the Atlas into a colour and depth texture: the cached slice buffers (the land prints in by
 * slice when the table opens, and new land rises where it appears), the holo grid, the run's thread, the city's lights
 * at night and the clouds with their shadows. Per frame it writes only those overlays; the land is drawn from buffers
 * built on the mesh worker. Used by the table view's picture-in-picture renderer and by the wrist glance. Every upload
 * is recorded before the pass opens (spike S5: anything recorded inside it ends the frame).
 */
final class AtlasPainter {
    private static final float DEPTH = 30000f;
    private static final int GRID_LINES = 2 * (2 * 10 + 1);
    private static final int TRAIL_QUADS = 600, LIGHTS = 6000;

    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Atlas");
    private final MappableRingBuffer cloudRing = new MappableRingBuffer(() -> "Atlas clouds",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, AtlasClouds.MAX_QUADS * 4 * AtlasClouds.VERTEX);
    private final MappableRingBuffer lineRing = new MappableRingBuffer(() -> "Atlas grid and thread",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, (AtlasClouds.SEA_QUADS + GRID_LINES + TRAIL_QUADS * 2) * 4 * 16);
    private final MappableRingBuffer lightRing = new MappableRingBuffer(() -> "Atlas lights",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, LIGHTS * 4 * 24);
    private final Matrix4f base = new Matrix4f();
    private final Vector3f a = new Vector3f(), b = new Vector3f();
    private static GpuTexture glow;
    private static GpuTextureView glowView;
    /** The tops' samplers: mipmapped and anisotropic (the table is tilted), blocks kept crisp close in (sharp) or not. */
    private static GpuSampler sharp, soft;
    // this frame's drawn slices and their transforms
    private AtlasMesh.Slice[] drawList = new AtlasMesh.Slice[64];
    private GpuBufferSlice[] drawTransforms = new GpuBufferSlice[64];

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    /** A soft round light, white, for lamps and lit windows at night (tinted per vertex). */
    private static GpuTextureView glow() {
        if (glowView == null) {
            GpuDevice device = RenderSystem.getDevice();
            int n = 32;
            try (NativeImage img = new NativeImage(n, n, false)) {
                for (int y = 0; y < n; y++)
                    for (int x = 0; x < n; x++) {
                        float dx = (x + 0.5f) / n * 2 - 1, dy = (y + 0.5f) / n * 2 - 1, d = (float) Math.sqrt(dx * dx + dy * dy);
                        float core = Math.max(0f, 1f - d * 3.2f), halo = (float) Math.pow(Math.max(0f, 1f - d), 2.2);
                        int alpha = Math.min(255, (int) ((core * 0.9f + halo * 0.55f) * 255));
                        img.setPixel(x, y, (alpha << 24) | 0xFFFFFF);
                    }
                glow = device.createTexture(() -> "Atlas glow", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, n, n, 1, 1);
                glowView = device.createTextureView(glow);
                device.createCommandEncoder().writeToTexture(glow, img);
            }
        }
        return glowView;
    }

    private static GpuSampler topSampler(int lod) {
        if (sharp == null) {
            GpuDevice device = RenderSystem.getDevice();
            int aniso = Math.clamp(device.getDeviceInfo().limits().maxAnisotropy(), 1, 8);
            sharp = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, aniso, OptionalDouble.empty());
            soft = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, aniso, OptionalDouble.empty());
        }
        // close in a texel is 1.8 pixels or more: nearest keeps each block's edge; further out it is a pixel or less
        return lod <= 1 ? sharp : soft;
    }

    /** Paints {@code s} into the given targets, {@code w}×{@code h} pixels; {@code px} multiplies line widths. */
    void paint(GpuDevice device, AtlasRenderState s, GpuTexture color, GpuTextureView colorView, GpuTexture depth, GpuTextureView depthView,
               int w, int h, float guiScale, boolean thread, float cloudLevel) {
        AtlasState atlas = AtlasState.get();
        long now = System.nanoTime();
        // finished slices go up first: an upload inside the pass would end the frame
        atlas.mesh.zones = atlas.zoneView();
        atlas.mesh.shown = s.lod();
        atlas.mesh.update(s.cx(), s.cz(), s.lod(), Discovery.CLIENT, now);
        CommandEncoder enc = device.createCommandEncoder();
        enc.clearColorAndDepthTextures(color, new Vector4f(0f), depth, 0.0);
        projection.setupOrtho(-DEPTH, DEPTH, w, h, true);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        float px = s.zoom() * guiScale;
        AtlasRenderState.view(base, w, h, px, s.yaw(), s.tilt());

        GpuBuffer cloudBuf = cloudRing.currentBuffer();
        int cloudQuads, shadowQuads;
        try (GpuBufferSlice.MappedView view = cloudBuf.map(false, true)) {
            cloudQuads = atlas.clouds.write(MemoryUtil.memAddress(view.data()), Discovery.CLIENT, atlas.revealedAt, atlas.store, base, px, s.tilt(),
                    cloudLevel, s.cx(), s.cy(), s.cz(), s.time(), s.night(), now);
            shadowQuads = atlas.clouds.shadows();
        }
        GpuBuffer lineBuf = lineRing.currentBuffer();
        int seaQuads, gridQuads, trailQuads;
        try (GpuBufferSlice.MappedView view = lineBuf.map(false, true)) {
            long p = MemoryUtil.memAddress(view.data());
            seaQuads = atlas.clouds.sea(p, base, cloudLevel); // the cloud floor first, then the grid and the thread
            p += (long) seaQuads * 64;
            gridQuads = grid(p, s, 0.75f * guiScale);
            trailQuads = thread ? trail(p + (long) gridQuads * 64, s, atlas, guiScale) : 0;
        }
        var map = atlas.mesh.slices[s.lod()];
        int blocks = AtlasMesh.S << AtlasMesh.STEP_SHIFT[s.lod()];
        double r = (AtlasMesh.WINDOW + 0.75) * blocks, r2 = r * r;
        GpuBuffer lightBuf = lightRing.currentBuffer();
        int lightQuads = 0;
        if (s.night() > 0.05f && s.lod() <= 2) {
            try (GpuBufferSlice.MappedView view = lightBuf.map(false, true)) {
                lightQuads = lights(MemoryUtil.memAddress(view.data()), s, map, blocks, r2, px, now, w, h);
            }
        }
        int maxQuads = Math.max(Math.max(cloudQuads, lightQuads), Math.max(seaQuads, gridQuads + trailQuads));
        for (AtlasMesh.Slice sl : map.values()) if (sl.quads > maxQuads) maxQuads = sl.quads;
        RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = quadIndex.getBuffer(Math.max(1, maxQuads) * 6);
        IndexType indexType = quadIndex.type();
        GpuTextureView puff = atlas.clouds.texture();
        GpuTextureView glow = lightQuads > 0 ? glow() : null;

        float night = s.night();
        Vector4f tint = new Vector4f(1f + (0.40f - 1f) * night, 1f + (0.48f - 1f) * night, 1f + (0.74f - 1f) * night, 1f);
        try (RenderPass pass = enc.createRenderPass(() -> "Atlas", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setIndexBuffer(indices, indexType);
            if ((AtlasScreen.prof & AtlasScreen.PROF_NO_CLOUDS) == 0) { // the cloud floor under everything, never written to depth
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.OVERLAY));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), tint));
                pass.setVertexBuffer(0, lineBuf.slice(0, (long) seaQuads * 64));
                pass.drawIndexed(seaQuads * 6, 1, 0, 0, 0);
            }
            int drawn = 0;
            for (AtlasMesh.Slice sl : map.values()) {
                if (sl.buffer == null || sl.view == null || (AtlasScreen.prof & AtlasScreen.PROF_NO_LAND) != 0) continue;
                double ox = sl.x0() - s.cx(), oz = sl.z0() - s.cz();
                double mx = ox + blocks / 2.0, mz = oz + blocks / 2.0;
                if (mx * mx + mz * mz > r2 || !onScreen(ox, oz, blocks, s.cy(), w, h)) continue;
                float rise = rise(s, sl, mx, mz, r, now);
                if (rise <= 0.001f) continue;
                Matrix4f mv = new Matrix4f(base).translate((float) ox, (float) (AtlasMesh.BASE_Y - s.cy()), (float) oz)
                        .scale(1f, rise, 1f).translate(0f, AtlasMesh.Y_REF - AtlasMesh.BASE_Y, 0f);
                if (drawn == drawList.length) {
                    drawList = java.util.Arrays.copyOf(drawList, drawn * 2);
                    drawTransforms = java.util.Arrays.copyOf(drawTransforms, drawn * 2);
                }
                drawList[drawn] = sl;
                drawTransforms[drawn++] = RenderSystem.getDynamicUniforms().writeTransform(mv, tint);
            }
            // the tops, each slice with its own texture, then every slice's sides
            if (drawn > 0) {
                GpuSampler sampler = topSampler(s.lod());
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.TERRAIN_TOPS));
                for (int i = 0; i < drawn; i++) {
                    AtlasMesh.Slice sl = drawList[i];
                    if (sl.tops == 0) continue;
                    pass.setUniform("DynamicTransforms", drawTransforms[i]);
                    pass.setUniform("Sampler0", sl.view, sampler);
                    pass.setVertexBuffer(0, sl.buffer.slice(0, (long) sl.tops * 4 * AtlasMesh.Builder.TOP_VERTEX));
                    pass.drawIndexed(sl.tops * 6, 1, 0, 0, 0);
                }
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.TERRAIN));
                for (int i = 0; i < drawn; i++) {
                    AtlasMesh.Slice sl = drawList[i];
                    int sides = sl.quads - sl.tops;
                    if (sides <= 0) continue;
                    long at = (long) sl.tops * 4 * AtlasMesh.Builder.TOP_VERTEX;
                    pass.setUniform("DynamicTransforms", drawTransforms[i]);
                    pass.setVertexBuffer(0, sl.buffer.slice(at, (long) sides * 4 * AtlasMesh.Builder.SIDE_VERTEX));
                    pass.drawIndexed(sides * 6, 1, 0, 0, 0);
                }
                java.util.Arrays.fill(drawList, 0, drawn, null);
                java.util.Arrays.fill(drawTransforms, 0, drawn, null);
            }
            AtlasState.stats.slicesDrawn = drawn;
            if (gridQuads + trailQuads > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.OVERLAY));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
                pass.setVertexBuffer(0, lineBuf.slice((long) seaQuads * 64, (long) (gridQuads + trailQuads) * 64));
                pass.drawIndexed((gridQuads + trailQuads) * 6, 1, 0, 0, 0);
            }
            if (lightQuads > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.GLOW));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
                pass.setUniform("Sampler0", glow, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setVertexBuffer(0, lightBuf.slice(0, (long) lightQuads * 4 * 24));
                pass.drawIndexed(lightQuads * 6, 1, 0, 0, 0);
            }
            if (cloudQuads > 0 && (AtlasScreen.prof & AtlasScreen.PROF_NO_CLOUDS) == 0) { // shadows first (on the ground), then the puffs far to near
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.CLOUDS));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
                pass.setUniform("Sampler0", puff, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                // the shadows stay while the view moves: dropping them there made them blink at every orbit and zoom
                pass.setVertexBuffer(0, cloudBuf.slice(0, (long) cloudQuads * 4 * AtlasClouds.VERTEX));
                pass.drawIndexed(cloudQuads * 6, 1, 0, 0, 0);
            }
            AtlasState.stats.puffs = cloudQuads - shadowQuads;
        }
        cloudRing.rotate();
        lineRing.rotate();
        lightRing.rotate();
    }

    /** Whether any of a slice's box (from the base to 200 over it) lands in the picture: off-screen slices cost nothing. */
    private boolean onScreen(double ox, double oz, int blocks, double cy, int w, int h) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            base.transformPosition((float) (ox + ((i & 1) == 0 ? 0 : blocks)), (float) ((i & 4) == 0 ? AtlasMesh.BASE_Y - cy : 260 - cy),
                    (float) (oz + ((i & 2) == 0 ? 0 : blocks)), a);
            minX = Math.min(minX, a.x);
            maxX = Math.max(maxX, a.x);
            minY = Math.min(minY, a.y);
            maxY = Math.max(maxY, a.y);
        }
        return maxX >= 0 && minX <= w && maxY >= 0 && minY <= h;
    }

    /** The land prints in from the centre when the table opens; a slice that just appeared rises on its own. */
    private static float rise(AtlasRenderState s, AtlasMesh.Slice sl, double mx, double mz, double r, long now) {
        float delay = (float) (Math.sqrt(mx * mx + mz * mz) / r) * 0.45f;
        float rise = smooth((s.age() - delay) / 0.30f);
        if (sl.builtAt != 0) rise = Math.min(rise, smooth((now - sl.builtAt) / 4.0e8f));
        return rise;
    }

    /** Lamps and lit windows at night: warm soft lights over every lit column in the drawn slices. */
    private int lights(long p, AtlasRenderState s, it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<AtlasMesh.Slice> map, int blocks,
                       double r2, float px, long now, int w, int h) {
        int n = 0;
        float night = s.night();
        float size = Math.max(2.5f, 2.6f * px * (s.lod() == 0 ? 1f : 2f)) * 0.5f;
        double r = Math.sqrt(r2);
        for (AtlasMesh.Slice sl : map.values()) {
            int[] lights = sl.lights;
            if (sl.buffer == null || lights.length == 0) continue;
            double ox = sl.x0() - s.cx(), oz = sl.z0() - s.cz(), mx = ox + blocks / 2.0, mz = oz + blocks / 2.0;
            if (mx * mx + mz * mz > r2) continue;
            float rise = rise(s, sl, mx, mz, r, now);
            if (rise < 0.95f || !onScreen(ox, oz, blocks, s.cy(), w, h)) continue;
            for (int i = 0; i + 2 < lights.length && n < LIGHTS; i += 3) {
                base.transformPosition((float) (ox + lights[i] + 0.5), (float) (lights[i + 1] + 1.4 - s.cy()), (float) (oz + lights[i + 2] + 0.5), a);
                // flicker a little, each light on its own phase
                float f = 0.85f + 0.15f * (float) Math.sin(s.time() * 3.1f + (lights[i] * 7 + lights[i + 2] * 13));
                float z = a.z + 3f;
                // a wide warm halo that pools on the roofs round it, then the bright lamp itself
                for (int layer = 0; layer < 2 && n < LIGHTS; layer++) {
                    float hw = layer == 0 ? size * 3.2f : size * 1.1f;
                    int alpha = Math.min(255, (int) (night * (layer == 0 ? 110 : 255) * f));
                    p = gvtx(p, a.x - hw, a.y - hw, z, 0, 0, alpha);
                    p = gvtx(p, a.x + hw, a.y - hw, z, 1, 0, alpha);
                    p = gvtx(p, a.x + hw, a.y + hw, z, 1, 1, alpha);
                    p = gvtx(p, a.x - hw, a.y + hw, z, 0, 1, alpha);
                    n++;
                }
            }
        }
        return n;
    }

    private static long gvtx(long p, float x, float y, float z, float u, float v, int alpha) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        MemoryUtil.memPutFloat(p + 12, u);
        MemoryUtil.memPutFloat(p + 16, v);
        MemoryUtil.memPutByte(p + 20, (byte) 255);
        MemoryUtil.memPutByte(p + 21, (byte) 196);
        MemoryUtil.memPutByte(p + 22, (byte) 120);
        MemoryUtil.memPutByte(p + 23, (byte) alpha);
        return p + 24;
    }

    /** Faint holo grid lines on the base plane, ~1.5 px wide, fading toward the rim. View space. */
    private int grid(long p, AtlasRenderState s, float halfWidthPx) {
        int n = 0, step = 32 << AtlasMesh.STEP_SHIFT[s.lod()], gr = step * 10;
        float y = (float) (AtlasMesh.BASE_Y - s.cy());
        double gx0 = Math.floor((s.cx() - gr) / step) * step, gz0 = Math.floor((s.cz() - gr) / step) * step;
        int alphaBase = (int) (30 - 10 * s.night());
        for (int axis = 0; axis < 2; axis++) {
            for (int i = 0; i <= 2 * 10; i++) {
                float off = (float) ((axis == 0 ? gx0 : gz0) + (long) i * step - (axis == 0 ? s.cx() : s.cz()));
                float fade = 1f - Math.min(1f, Math.abs(off) / gr);
                if (fade <= 0f) continue;
                if (axis == 0) {
                    base.transformPosition(off, y, -gr, a);
                    base.transformPosition(off, y, gr, b);
                } else {
                    base.transformPosition(-gr, y, off, a);
                    base.transformPosition(gr, y, off, b);
                }
                if (ribbon(p + (long) n * 64, halfWidthPx, 127, 195, 255, (int) (alphaBase * fade))) n++;
            }
        }
        return n;
    }

    /** The opening run's path as a glowing bronze thread on the roofs: a wide soft band and a bright core. */
    private int trail(long p, AtlasRenderState s, AtlasState atlas, float guiScale) {
        int[] t = atlas.trail;
        if (t.length < 6) return 0;
        int points = t.length / 3, upto = Math.max(1, (int) Math.ceil(atlas.trailProgress() * (points - 1)));
        float glow = atlas.trailGlow();
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            float half = (pass == 0 ? 7f : 2.2f) * guiScale;
            int alpha = (int) ((pass == 0 ? 130 : 255) * glow);
            int rr = 255, gg = pass == 0 ? 160 : 214, bb = pass == 0 ? 60 : 120;
            for (int i = 0; i < upto && i < TRAIL_QUADS; i++) {
                int j = i * 3, k = (i + 1) * 3;
                base.transformPosition((float) (t[j] - s.cx()), (float) (t[j + 1] + 0.6 - s.cy()), (float) (t[j + 2] - s.cz()), a);
                base.transformPosition((float) (t[k] - s.cx()), (float) (t[k + 1] + 0.6 - s.cy()), (float) (t[k + 2] - s.cz()), b);
                a.z += 4f;
                b.z += 4f; // a little toward the viewer, so roofs at the same height don't swallow it
                if (ribbon(p + (long) n * 64, half, rr, gg, bb, alpha)) n++;
            }
        }
        return n;
    }

    /** One screen-space band from a to b, {@code half} pixels either side. */
    private boolean ribbon(long p, float half, int r, int g, int bl, int alpha) {
        float dx = b.x - a.x, dy = b.y - a.y, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return false;
        float nx = -dy / len * half, ny = dx / len * half;
        p = vtx(p, a.x + nx, a.y + ny, a.z, r, g, bl, alpha);
        p = vtx(p, b.x + nx, b.y + ny, b.z, r, g, bl, alpha);
        p = vtx(p, b.x - nx, b.y - ny, b.z, r, g, bl, alpha);
        vtx(p, a.x - nx, a.y - ny, a.z, r, g, bl, alpha);
        return true;
    }

    private static long vtx(long p, float x, float y, float z, int r, int g, int b, int alpha) {
        MemoryUtil.memPutFloat(p, x);
        MemoryUtil.memPutFloat(p + 4, y);
        MemoryUtil.memPutFloat(p + 8, z);
        MemoryUtil.memPutByte(p + 12, (byte) r);
        MemoryUtil.memPutByte(p + 13, (byte) g);
        MemoryUtil.memPutByte(p + 14, (byte) b);
        MemoryUtil.memPutByte(p + 15, (byte) alpha);
        return p + 16;
    }

    void close() {
        projectionBuffer.close();
        cloudRing.close();
        lineRing.close();
        lightRing.close();
    }
}
