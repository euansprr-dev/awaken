package dev.awaken.atlas.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.atlas.Discovery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the Atlas into its own colour and depth texture and blits it into the GUI, like vanilla's picture-in-picture
 * renderers, but with its own render pass, so it draws the cached slice buffers instead of re-submitting geometry every
 * frame (spike S5). Per frame it writes only the clouds, the holo grid and the run's thread. When nothing moves it
 * re-blits the last picture. Every upload (slices, puff sheet, ring buffers) is recorded before the pass opens.
 */
final class AtlasRenderer extends PictureInPictureRenderer<AtlasRenderState> {
    private static final float DEPTH = 30000f;
    private static final int GRID_LINES = 2 * (2 * 10 + 1);
    private static final int TRAIL_QUADS = 600;

    private GpuTexture color, depth;
    private GpuTextureView colorView, depthView;
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("Atlas");
    private final MappableRingBuffer cloudRing = new MappableRingBuffer(() -> "Atlas clouds",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, AtlasClouds.MAX_PUFFS * 4 * AtlasClouds.VERTEX);
    private final MappableRingBuffer lineRing = new MappableRingBuffer(() -> "Atlas grid and thread",
            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, (GRID_LINES + TRAIL_QUADS * 2) * 4 * 16);
    private final Matrix4f base = new Matrix4f();
    private final Vector3f a = new Vector3f(), b = new Vector3f();
    private AtlasRenderState last;

    @Override
    public Class<AtlasRenderState> getRenderStateClass() {
        return AtlasRenderState.class;
    }

    @Override
    protected void renderToTexture(AtlasRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        // unused: prepare() runs its own pass
    }

    @Override
    protected String getTextureLabel() {
        return "atlas";
    }

    @Override
    public boolean canBeReusedFor(AtlasRenderState state, int w, int h) {
        return color == null || (color.getWidth(0) == w && color.getHeight(0) == h);
    }

    @Override
    public void prepare(AtlasRenderState s, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale) {
        long t0 = System.nanoTime();
        int w = (s.x1() - s.x0()) * guiScale, h = (s.y1() - s.y0()) * guiScale;
        if (w <= 0 || h <= 0) return;
        GpuDevice device = RenderSystem.getDevice();
        boolean resized = color == null || color.getWidth(0) != w || color.getHeight(0) != h;
        if (resized) textures(device, w, h);
        // nothing moved and nothing drifts: the last picture is still right
        boolean redraw = resized || s.animate() || !s.equals(last);
        if (redraw) {
            draw(device, s, w, h, guiScale);
            last = s;
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(colorView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                s.pose(), s.x0(), s.y0(), s.x1(), s.y1(), 0.0F, 1.0F, 1.0F, 0.0F, -1, s.scissorArea(), null));
        AtlasState.stats.frame(System.nanoTime() - t0, redraw);
    }

    private static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private void draw(GpuDevice device, AtlasRenderState s, int w, int h, int guiScale) {
        AtlasState atlas = AtlasState.get();
        long now = System.nanoTime();
        // finished slices go up first: an upload inside the pass would end the frame
        atlas.mesh.update(s.cx(), s.cz(), s.lod(), Discovery.CLIENT, now);
        CommandEncoder enc = device.createCommandEncoder();
        enc.clearColorAndDepthTextures(color, new Vector4f(0f), depth, 0.0);
        projection.setupOrtho(-DEPTH, DEPTH, w, h, true);
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        float px = s.zoom() * guiScale;
        AtlasRenderState.view(base, w, h, px, s.yaw(), s.tilt());

        GpuBuffer cloudBuf = cloudRing.currentBuffer();
        int cloudQuads;
        try (GpuBufferSlice.MappedView view = cloudBuf.map(false, true)) {
            cloudQuads = atlas.clouds.write(MemoryUtil.memAddress(view.data()), Discovery.CLIENT, atlas.revealedAt, base, px, s.tilt(), s.lod(),
                    s.cx(), s.cz(), s.time(), s.night(), now);
        }
        GpuBuffer lineBuf = lineRing.currentBuffer();
        int gridQuads, trailQuads;
        try (GpuBufferSlice.MappedView view = lineBuf.map(false, true)) {
            long p = MemoryUtil.memAddress(view.data());
            gridQuads = grid(p, s, 0.75f * guiScale);
            trailQuads = trail(p + (long) gridQuads * 4 * 16, s, atlas, guiScale);
        }
        int maxQuads = Math.max(cloudQuads, gridQuads + trailQuads);
        var map = atlas.mesh.slices[s.lod()];
        for (AtlasMesh.Slice sl : map.values()) if (sl.quads > maxQuads) maxQuads = sl.quads;
        RenderSystem.AutoStorageIndexBuffer quadIndex = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = quadIndex.getBuffer(Math.max(1, maxQuads) * 6);
        IndexType indexType = quadIndex.type();
        GpuTextureView puff = atlas.clouds.texture();

        float night = s.night();
        Vector4f tint = new Vector4f(1f + (0.40f - 1f) * night, 1f + (0.48f - 1f) * night, 1f + (0.74f - 1f) * night, 1f);
        int blocks = AtlasMesh.S << AtlasMesh.STEP_SHIFT[s.lod()];
        double r = (AtlasMesh.WINDOW + 0.75) * blocks, r2 = r * r;
        try (RenderPass pass = enc.createRenderPass(() -> "Atlas", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setIndexBuffer(indices, indexType);
            pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.TERRAIN));
            int drawn = 0;
            for (AtlasMesh.Slice sl : map.values()) {
                if (sl.buffer == null) continue;
                double ox = sl.x0() - s.cx(), oz = sl.z0() - s.cz();
                double mx = ox + blocks / 2.0, mz = oz + blocks / 2.0;
                if (mx * mx + mz * mz > r2) continue;
                // the land prints in from the centre when the table opens, and new land rises where it appears
                float delay = (float) (Math.sqrt(mx * mx + mz * mz) / r) * 0.45f;
                float rise = smooth((s.age() - delay) / 0.30f);
                if (sl.builtAt != 0) rise = Math.min(rise, smooth((now - sl.builtAt) / 4.0e8f));
                if (rise <= 0.001f) continue;
                Matrix4f mv = new Matrix4f(base).translate((float) ox, (float) (AtlasMesh.BASE_Y - s.cy()), (float) oz)
                        .scale(1f, rise, 1f).translate(0f, AtlasMesh.Y_REF - AtlasMesh.BASE_Y, 0f);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(mv, tint));
                pass.setVertexBuffer(0, sl.buffer.slice());
                pass.drawIndexed(sl.quads * 6, 1, 0, 0, 0);
                drawn++;
            }
            AtlasState.stats.slicesDrawn = drawn;
            if (gridQuads + trailQuads > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.OVERLAY));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
                pass.setVertexBuffer(0, lineBuf.slice(0, (long) (gridQuads + trailQuads) * 4 * 16));
                pass.drawIndexed((gridQuads + trailQuads) * 6, 1, 0, 0, 0);
            }
            if (cloudQuads > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(AtlasPipelines.CLOUDS));
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f()));
                pass.setUniform("Sampler0", puff, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.setVertexBuffer(0, cloudBuf.slice(0, (long) cloudQuads * 4 * AtlasClouds.VERTEX));
                pass.drawIndexed(cloudQuads * 6, 1, 0, 0, 0);
            }
            AtlasState.stats.puffs = cloudQuads;
        }
        cloudRing.rotate();
        lineRing.rotate();
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
                if (ribbon(p + (long) n * 64, halfWidthPx, 127, 195, 255, (int) (alphaBase * fade), (int) (alphaBase * fade))) n++;
            }
        }
        return n;
    }

    /** The opening run's path as a glowing bronze thread on the roofs: a wide soft band and a bright core. */
    private int trail(long p, AtlasRenderState s, AtlasState atlas, int guiScale) {
        int[] t = atlas.trail;
        if (t.length < 6) return 0;
        float drawn = atlas.trailProgress(); // 0..1 while it draws itself, 1 after
        int points = t.length / 3, upto = Math.max(1, (int) Math.ceil(drawn * (points - 1)));
        float glow = atlas.trailGlow();
        int n = 0;
        for (int pass = 0; pass < 2 && n < TRAIL_QUADS * 2; pass++) {
            float half = (pass == 0 ? 3.5f : 1.1f) * guiScale;
            int alpha = (int) ((pass == 0 ? 60 : 230) * glow);
            int rr = pass == 0 ? 224 : 255, gg = pass == 0 ? 168 : 222, bb = pass == 0 ? 106 : 160;
            for (int i = 0; i < upto && i < TRAIL_QUADS; i++) {
                int j = i * 3, k = (i + 1) * 3;
                base.transformPosition((float) (t[j] - s.cx()), (float) (t[j + 1] + 0.6 - s.cy()), (float) (t[j + 2] - s.cz()), a);
                base.transformPosition((float) (t[k] - s.cx()), (float) (t[k + 1] + 0.6 - s.cy()), (float) (t[k + 2] - s.cz()), b);
                a.z += 4f;
                b.z += 4f; // a little toward the viewer, so roofs at the same height don't swallow it
                if (ribbon(p + (long) n * 64, half, rr, gg, bb, alpha, alpha)) n++;
            }
        }
        return n;
    }

    /** One screen-space band from a to b, {@code half} pixels either side. */
    private boolean ribbon(long p, float half, int r, int g, int bl, int alphaA, int alphaB) {
        float dx = b.x - a.x, dy = b.y - a.y, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return false;
        float nx = -dy / len * half, ny = dx / len * half;
        p = vtx(p, a.x + nx, a.y + ny, a.z, r, g, bl, alphaA);
        p = vtx(p, b.x + nx, b.y + ny, b.z, r, g, bl, alphaB);
        p = vtx(p, b.x - nx, b.y - ny, b.z, r, g, bl, alphaB);
        vtx(p, a.x - nx, a.y - ny, a.z, r, g, bl, alphaA);
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

    private void textures(GpuDevice device, int w, int h) {
        closeTextures();
        color = device.createTexture(() -> "UI atlas texture", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        colorView = device.createTextureView(color);
        GpuFormat depthFormat = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
        depth = device.createTexture(() -> "UI atlas depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
                depthFormat, w, h, 1, 1);
        depthView = device.createTextureView(depth);
        last = null;
        Awaken.LOG.debug("[atlas] panel textures {}x{}", w, h);
    }

    private void closeTextures() {
        if (colorView != null) colorView.close();
        if (color != null) color.close();
        if (depthView != null) depthView.close();
        if (depth != null) depth.close();
        color = depth = null;
        colorView = depthView = null;
    }

    @Override
    public void close() {
        closeTextures();
        projectionBuffer.close();
        cloudRing.close();
        lineRing.close();
        super.close();
    }
}
