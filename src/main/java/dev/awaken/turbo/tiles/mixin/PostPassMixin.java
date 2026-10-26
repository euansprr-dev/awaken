package dev.awaken.turbo.tiles.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.turbo.tiles.Tiles;
import net.minecraft.client.renderer.PostPass;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Optional;

/**
 * Tiles' hold on how a post pass opens its render pass (the frame-graph lambda in {@code PostPass.addToFrame}, read
 * against 26.3.0.95 as {@code lambda$addToFrame$1}): Lens's passes open cleared and without depth. Not required: if it
 * doesn't apply, passes open as vanilla's.
 */
@Mixin(PostPass.class)
abstract class PostPassMixin {
    @Shadow @Final private String name;

    @ModifyArg(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), index = 2, require = 0)
    private Optional<Vector4fc> awaken$tilesClear(Optional<Vector4fc> clear) {
        return Tiles.clear(this.name, clear);
    }

    @ModifyArg(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), index = 3, require = 0)
    private @Nullable GpuTextureView awaken$tilesDepth(@Nullable GpuTextureView depth) {
        return Tiles.depth(this.name, depth);
    }
}
