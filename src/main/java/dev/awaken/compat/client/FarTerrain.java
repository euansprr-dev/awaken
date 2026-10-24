package dev.awaken.compat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * What a far-terrain mod drew last frame, for Lens: its clip planes, depth convention and depth texture. Lens needs it
 * because Distant Horizons draws far terrain without writing the game's depth, so by the game's depth alone far
 * mountains look like sky (no haze, light shafts shining through them). Plain state: no companion types here, so Lens
 * can read it whether or not Distant Horizons is installed.
 */
public final class FarTerrain {
    private FarTerrain() {}

    static volatile long lastFrameNanos;
    static volatile float near = 16f, far = 4096f;
    static volatile boolean reverseZ, zeroToOne;
    private static ExternalDepth depth;

    /** True while far terrain was drawn in the last fifth of a second. */
    public static boolean active() {
        return lastFrameNanos != 0 && System.nanoTime() - lastFrameNanos < 200_000_000L;
    }

    /**
     * True while Lens samples the companion's own depth. False means the 1×1 placeholder (no texture handed over yet,
     * or a renderer that has none to share, such as Distant Horizons' OpenGL engine): Lens then can't tell far terrain
     * from sky and keeps distance haze off while far terrain draws.
     */
    public static boolean readable() {
        return depth != null && depth.real();
    }

    public static float near() { return near; }
    public static float far() { return far; }
    public static boolean reverseZ() { return reverseZ; }
    public static boolean zeroToOne() { return zeroToOne; }

    /** The texture Lens samples as its far-terrain depth (a 1×1 placeholder until the companion hands its own over). */
    public static AbstractTexture depthTexture() {
        if (depth == null) depth = new ExternalDepth();
        return depth;
    }

    /**
     * Points the depth texture at the companion's: a GpuTextureView, a GpuTexture, or an array holding them (Distant
     * Horizons 3.3.4 hands back {GpuTexture, GpuTextureView, GpuSampler} as one reused Object[]). Cheap when nothing
     * changed, so it can run every frame; true when Lens now reads a real depth texture.
     */
    static boolean pointDepthAt(Object companionTexture) {
        return ((ExternalDepth) depthTexture()).point(companionTexture);
    }

    /**
     * The clip planes of the companion's projection, from its depth row (z_ndc = m23 / distance - m22) and convention.
     * Distant Horizons reports a near plane in its render event that is not the one in its matrix (it clamps the
     * matrix's to 7.5 blocks), so the matrix is the truth.
     */
    static void planesFrom(float m22, float m23, boolean reverse, boolean zeroToOneNdc) {
        float zNear = reverse ? 1f : zeroToOneNdc ? 0f : -1f, zFar = reverse ? (zeroToOneNdc ? 0f : -1f) : 1f;
        float n = m23 / (zNear + m22), f = m23 / (zFar + m22);
        if (Float.isFinite(n) && Float.isFinite(f) && n > 0f && f > n) {
            near = n;
            far = f;
        }
    }

    /** Shows another renderer's texture to a post chain without ever owning (or closing) it. */
    private static final class ExternalDepth extends AbstractTexture {
        private final GpuTexture placeholder;
        private final GpuTextureView placeholderView;
        private GpuTextureView ownView;
        // what the companion handed over last time (its texture and view), to notice a resize without allocating
        private GpuTexture sourceTexture;
        private GpuTextureView sourceView;

        ExternalDepth() {
            var device = RenderSystem.getDevice();
            placeholder = device.createTexture(() -> "awaken far-terrain depth placeholder", 5, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
            placeholderView = device.createTextureView(placeholder);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            texture = placeholder;
            textureView = placeholderView;
        }

        boolean real() {
            return textureView != null && textureView != placeholderView && !textureView.isClosed();
        }

        boolean point(Object companion) {
            GpuTexture tex = null;
            GpuTextureView view = null;
            if (companion instanceof Object[] parts) {
                for (Object p : parts) {
                    if (view == null && p instanceof GpuTextureView v) view = v;
                    else if (tex == null && p instanceof GpuTexture t) tex = t;
                }
            } else if (companion instanceof GpuTextureView v) {
                view = v;
            } else if (companion instanceof GpuTexture t) {
                tex = t;
            }
            if (view != null && view.isClosed()) view = null;
            if (tex != null && tex.isClosed()) tex = null;
            if (view == sourceView && tex == sourceTexture) return real();
            sourceView = view;
            sourceTexture = tex;
            if (ownView != null) {
                ownView.close();
                ownView = null;
            }
            if (view != null) {
                texture = view.texture();
                textureView = view;
            } else if (tex != null) {
                ownView = RenderSystem.getDevice().createTextureView(tex);
                texture = tex;
                textureView = ownView;
            } else {
                texture = placeholder;
                textureView = placeholderView;
            }
            return real();
        }

        @Override
        public GpuTextureView getTextureView() {
            // the companion recreates its texture on a resize: never hand a post pass a closed view
            return textureView == null || textureView.isClosed() ? placeholderView : textureView;
        }

        @Override
        protected void releaseTextures() {
            // the companion's texture is not ours to close: only the view we made and our placeholder
            if (ownView != null) ownView.close();
            ownView = null;
            placeholderView.close();
            placeholder.close();
            texture = null;
            textureView = null;
        }
    }
}
