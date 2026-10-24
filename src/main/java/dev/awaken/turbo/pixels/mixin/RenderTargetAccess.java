package dev.awaken.turbo.pixels.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Setters for a render target's own textures (protected in 26.3), so Pixels can lend the main target smaller ones for
 * the world pass and give its own back after (PixelsFrame). Getters are public already.
 */
@Mixin(RenderTarget.class)
public interface RenderTargetAccess {
    @Accessor("colorTexture")
    void awaken$setColorTexture(@Nullable GpuTexture texture);

    @Accessor("colorTextureView")
    void awaken$setColorTextureView(@Nullable GpuTextureView view);

    @Accessor("depthTexture")
    void awaken$setDepthTexture(@Nullable GpuTexture texture);

    @Accessor("depthTextureView")
    void awaken$setDepthTextureView(@Nullable GpuTextureView view);
}
