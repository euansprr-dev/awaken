package dev.awaken.compat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * What a far-terrain renderer drew last frame, for Lens: its depth texture, clip planes and depth convention. Lens needs
 * it because far terrain is drawn without writing the game's depth, so by the game's depth alone far mountains look
 * like sky (no haze, light shafts shining through them). Any renderer reports the same way (Distant Horizons today,
 * Awaken's own far terrain later): the frame it drew, its planes and convention, and its depth texture when it shares
 * one. Plain state: no companion types here, so Lens can read it whether or not any of them is installed.
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
     * True while the renderer shares its depth with Lens. False means the 1×1 placeholder: nothing handed over yet, a
     * renderer with none to share (Distant Horizons' OpenGL engine), or one that keeps it to itself where it isn't
     * checked (Distant Horizons on Vulkan). Lens's shaders then trust a shared depth only once it proves itself (two
     * different depths where far terrain drew, pass lens_far_probe), since one that reads as one value everywhere
     * can't be told from sky. Without a trusted depth, Lens adds no distance haze while far terrain draws.
     */
    public static boolean readable() {
        return depth != null && depth.real();
    }

    public static float near() { return near; }
    public static float far() { return far; }
    public static boolean reverseZ() { return reverseZ; }
    public static boolean zeroToOne() { return zeroToOne; }

    /**
     * A far renderer outside this package (Awaken's own Far Sight) reports the frame it drew: its depth texture (a
     * GpuTexture or GpuTextureView it keeps until post effects, or null for none), the clip planes and the depth
     * convention it drew with. Called once a frame while it draws.
     */
    public static void report(Object depthTexture, float zNear, float zFar, boolean reverse, boolean zeroToOneNdc) {
        near = zNear;
        far = zFar;
        reverseZ = reverse;
        zeroToOne = zeroToOneNdc;
        pointDepthAt(depthTexture);
        lastFrameNanos = System.nanoTime();
    }

    private static volatile boolean selfHazed;
    private static volatile long selfHazedNanos;

    /**
     * A far renderer that draws its own aerial perspective at real distances (Far Sight 2) says so each frame: Lens then
     * hazes far terrain only as much as the end of the real chunks, and leaves the kilometres past them to it.
     */
    public static void selfHazed(boolean value) {
        selfHazed = value;
        selfHazedNanos = System.nanoTime();
    }

    /** Whether far terrain this frame hazes its own distance (reported in the last fifth of a second). */
    public static boolean selfHazed() {
        return selfHazed && active() && System.nanoTime() - selfHazedNanos < 200_000_000L;
    }

    private static volatile float renderDistanceFogEnd = -1f;

    /**
     * A far renderer that pushes vanilla's render-distance fog out (so the real chunks don't fog into a band where far
     * terrain begins) records the end it replaced, before it changes the fog. Lens scales its distance haze by it.
     */
    public static void renderDistanceFog(float vanillaEnd) {
        renderDistanceFogEnd = vanillaEnd;
    }

    /** Vanilla's render-distance fog end this frame: the recorded one while far terrain draws, else {@code fogEnd}. */
    public static float renderDistanceFogEnd(float fogEnd) {
        float v = renderDistanceFogEnd;
        return v > 0f && active() ? v : fogEnd;
    }

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
