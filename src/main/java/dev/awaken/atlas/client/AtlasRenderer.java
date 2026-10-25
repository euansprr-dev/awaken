package dev.awaken.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Draws the Atlas into its own colour and depth texture and blits it into the GUI, like vanilla's picture-in-picture
 * renderers, but with its own render pass ({@link AtlasPainter}), so it draws the cached slice buffers instead of
 * re-submitting geometry every frame (spike S5). When nothing moves it re-blits the last picture.
 */
final class AtlasRenderer extends PictureInPictureRenderer<AtlasRenderState> {
    private GpuTexture color, depth;
    private GpuTextureView colorView, depthView;
    private final AtlasPainter painter = new AtlasPainter();
    private AtlasRenderState last;
    private long lastDraw, lastMove;
    // the picture while the view moves: a smaller target, blitted up
    private GpuTexture low, lowDepth;
    private GpuTextureView lowView, lowDepthView;
    private boolean lowShown;
    static final float LIVE_SCALE = 0.6f;
    static final long SETTLE_NANOS = 110_000_000L;
    /** How often drifting clouds redraw the picture (dev: {@code /atlas drift <hz>}). */
    static long DRIFT_NANOS = 33_000_000L;

    /** Same picture apart from the clouds' clock. */
    private static boolean sameView(AtlasRenderState a, AtlasRenderState b) {
        return a.x0() == b.x0() && a.y0() == b.y0() && a.x1() == b.x1() && a.y1() == b.y1() && a.yaw() == b.yaw() && a.tilt() == b.tilt()
                && a.zoom() == b.zoom() && a.cx() == b.cx() && a.cy() == b.cy() && a.cz() == b.cz() && a.night() == b.night() && a.lod() == b.lod();
    }

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
        long now = System.nanoTime();
        boolean moved = last == null || !sameView(s, last);
        if (moved && last != null) lastMove = now;
        // the view is moving (an orbit, a drag, a zoom): draw every frame, or the table lags the mouse, but at 60% of
        // the resolution (the picture is in motion; the full-size one at Retina scale cost +2.2 ms orbiting). A tenth
        // of a second after it stops, one full-resolution picture. Still: only the clouds drift, redrawn about 30 times
        // a second (well under a pixel a step); nothing moving, the last picture is still right
        boolean live = now - lastMove < SETTLE_NANOS;
        GpuTextureView shown;
        boolean redraw;
        if (live) {
            int lw = Math.max(1, Math.round(w * LIVE_SCALE)), lh = Math.max(1, Math.round(h * LIVE_SCALE));
            if (low == null || low.getWidth(0) != lw || low.getHeight(0) != lh) lowTextures(device, lw, lh);
            redraw = moved || !lowShown || s.animate() && now - lastDraw >= DRIFT_NANOS;
            if (redraw) painter.paint(device, s, low, lowView, lowDepth, lowDepthView, lw, lh, guiScale * LIVE_SCALE, true, true);
            lowShown = true;
            shown = lowView;
        } else {
            redraw = resized || moved || lowShown || s.age() < 1.5f || s.animate() && now - lastDraw >= DRIFT_NANOS;
            if (redraw) painter.paint(device, s, color, colorView, depth, depthView, w, h, guiScale, true, false);
            lowShown = false;
            shown = colorView;
        }
        if (redraw) {
            lastDraw = now;
            last = s;
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(shown, RenderSystem.getSamplerCache().getRepeat(live ? FilterMode.LINEAR : FilterMode.NEAREST)),
                s.pose(), s.x0(), s.y0(), s.x1(), s.y1(), 0.0F, 1.0F, 1.0F, 0.0F, -1, s.scissorArea(), null));
        AtlasState.stats.frame(System.nanoTime() - t0, redraw);
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

    private void lowTextures(GpuDevice device, int w, int h) {
        closeLow();
        low = device.createTexture(() -> "UI atlas texture (moving)", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        lowView = device.createTextureView(low);
        GpuFormat depthFormat = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
        lowDepth = device.createTexture(() -> "UI atlas depth (moving)", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT,
                depthFormat, w, h, 1, 1);
        lowDepthView = device.createTextureView(lowDepth);
        lowShown = false;
    }

    private void closeLow() {
        if (lowView != null) lowView.close();
        if (low != null) low.close();
        if (lowDepthView != null) lowDepthView.close();
        if (lowDepth != null) lowDepth.close();
        low = lowDepth = null;
        lowView = lowDepthView = null;
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
        closeLow();
        painter.close();
        super.close();
    }
}
