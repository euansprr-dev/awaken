package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jspecify.annotations.Nullable;

/**
 * The hand's own depth, shown to Lens's chain (SPEC §22 "Reach and cover", the hand). While a post effect runs the game
 * draws the hand and what it holds into a depth target of their own ({@code hud_3d_depth}, cleared to 0) and then
 * copies it over the world's depth, where a hand and a wall at arm's length can no longer be told apart. Read before
 * that copy is forgotten, it says exactly which pixels are the hand: any that are not 0. The texture is the game's (or
 * Pixels', which lends its own while it scales the world): never owned or closed here, as
 * {@code compat.client.FarTerrain} shows a far renderer's depth.
 */
final class LensHand extends AbstractTexture {
    private final GpuTexture placeholder;
    private final GpuTextureView placeholderView;
    private @Nullable GpuTextureView source;

    LensHand() {
        var device = RenderSystem.getDevice();
        placeholder = device.createTexture(() -> "awaken lens hand depth placeholder", 5, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
        placeholderView = device.createTextureView(placeholder);
        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        texture = placeholder;
        textureView = placeholderView;
    }

    /** Points at the depth the hand is drawn into this frame; true when there is one (the shaders are told, parameter texel 26). */
    boolean point(@Nullable GpuTextureView view) {
        if (view != null && view.isClosed()) view = null;
        if (view != source) {
            source = view;
            texture = view != null ? view.texture() : placeholder;
            textureView = view != null ? view : placeholderView;
        }
        return view != null;
    }

    @Override
    public GpuTextureView getTextureView() {
        // Pixels makes its hand depth anew when the window changes: never hand a post pass a closed view
        return textureView == null || textureView.isClosed() ? placeholderView : textureView;
    }

    @Override
    protected void releaseTextures() {
        // the hand's depth is not ours to close: only our placeholder
        placeholderView.close();
        placeholder.close();
        texture = null;
        textureView = null;
        source = null;
    }
}
