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

    public static float near() { return near; }
    public static float far() { return far; }
    public static boolean reverseZ() { return reverseZ; }
    public static boolean zeroToOne() { return zeroToOne; }

    /** The texture Lens samples as its far-terrain depth (a 1×1 placeholder until the companion hands its own over). */
    public static AbstractTexture depthTexture() {
        if (depth == null) depth = new ExternalDepth();
        return depth;
    }

    /** Points the depth texture at the companion's (a GpuTexture or GpuTextureView); called when it is (re)created. */
    static void pointDepthAt(Object companionTexture) {
        ((ExternalDepth) depthTexture()).point(companionTexture);
    }

    /** Shows another renderer's texture to a post chain without ever owning (or closing) it. */
    private static final class ExternalDepth extends AbstractTexture {
        private final GpuTexture placeholder;
        private final GpuTextureView placeholderView;
        private GpuTextureView ownView;
        private Object source;

        ExternalDepth() {
            var device = RenderSystem.getDevice();
            placeholder = device.createTexture(() -> "awaken far-terrain depth placeholder", 5, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
            placeholderView = device.createTextureView(placeholder);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            texture = placeholder;
            textureView = placeholderView;
        }

        void point(Object companion) {
            if (companion == source) return;
            source = companion;
            if (ownView != null) {
                ownView.close();
                ownView = null;
            }
            if (companion instanceof GpuTextureView view) {
                texture = view.texture();
                textureView = view;
            } else if (companion instanceof GpuTexture tex) {
                ownView = RenderSystem.getDevice().createTextureView(tex);
                texture = tex;
                textureView = ownView;
            } else {
                texture = placeholder;
                textureView = placeholderView;
            }
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
