package dev.awaken.turbo.tiles.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.turbo.tiles.Tiles;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Optional;

/**
 * Tiles' hold on how a post pass opens its render pass (the frame-graph lambda in {@code PostPass.addToFrame}, read
 * against 26.3.0.95 as {@code lambda$addToFrame$1}): Lens's passes open cleared and without depth. Not required: if it
 * doesn't apply, passes open as vanilla's.
 */
@Mixin(PostPass.class)
abstract class PostPassMixin {
    @Shadow @Final private String name;
    @Shadow @Final private Identifier outputTargetId;

    // straight out: the copy of swap into the main target is left out once the composite drew into Pixels' texture
    @Inject(method = "lambda$addToFrame$1", at = @At("HEAD"), cancellable = true, require = 0)
    private void awaken$tilesSkip(CallbackInfo ci) {
        if (Tiles.skip(this.name, this.outputTargetId)) ci.cancel();
    }

    @ModifyArg(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), index = 1, require = 0)
    private GpuTextureView awaken$tilesColour(GpuTextureView colour) {
        return Tiles.colour(this.name, this.outputTargetId, colour);
    }

    // Lens's live uniform block (lens.client.LensBlock) in place of the chain's zeros
    @ModifyArgs(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;)V"), require = 0)
    private void awaken$tilesUniform(Args args) {
        String uniform = args.get(0);
        args.set(1, Tiles.uniform(this.name, uniform, args.<com.mojang.renderpearl.api.buffers.GpuBuffer>get(1)));
    }

    @ModifyArg(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), index = 2, require = 0)
    private Optional<Vector4fc> awaken$tilesClear(Optional<Vector4fc> clear) {
        return Tiles.clear(this.name, clear);
    }

    @ModifyArg(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), index = 3, require = 0)
    private @Nullable GpuTextureView awaken$tilesDepth(@Nullable GpuTextureView depth) {
        return Tiles.depth(this.name, depth);
    }
}
