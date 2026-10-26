package dev.awaken.turbo.tiles.mixin;

import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceDescriptor;
import dev.awaken.turbo.tiles.Tiles;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tiles' hold on how a post chain makes its targets each frame ({@code PostChain.addToFrame}): Lens's chains get theirs
 * without depth and without a clear pass of their own. Not required: if it doesn't apply, the chain's targets are as
 * vanilla makes them.
 */
@Mixin(PostChain.class)
abstract class PostChainMixin {
    @Shadow @Final private Identifier id;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyArg(method = "addToFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;createInternal(Ljava/lang/String;Lcom/mojang/blaze3d/resource/ResourceDescriptor;)Lcom/mojang/blaze3d/resource/ResourceHandle;"), index = 1, require = 0)
    private ResourceDescriptor awaken$tilesInternal(ResourceDescriptor descriptor) {
        return Tiles.internal(this.id, descriptor);
    }

    @ModifyArg(method = "addToFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PostChain;getOrCreatePersistentTarget(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/resource/RenderTargetDescriptor;)Lcom/mojang/blaze3d/pipeline/RenderTarget;"), index = 1, require = 0)
    private RenderTargetDescriptor awaken$tilesPersistent(RenderTargetDescriptor descriptor) {
        return Tiles.persistent(this.id, descriptor);
    }
}
