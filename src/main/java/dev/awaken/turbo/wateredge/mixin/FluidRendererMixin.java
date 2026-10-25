package dev.awaken.turbo.wateredge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.awaken.turbo.wateredge.WaterEdge;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves out the water sides that look into a chunk not yet received (WaterEdge). Wraps tesselate's face checks; not
 * required: if it doesn't apply, water meshes as vanilla's.
 */
@Mixin(FluidRenderer.class)
abstract class FluidRendererMixin {
    @WrapOperation(method = "tesselate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/FluidRenderer;shouldRenderFace(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            require = 0)
    private boolean awaken$waterEdge(FluidState fluid, BlockState self, Direction side, BlockState other, Operation<Boolean> original,
                                     @Local(argsOnly = true) BlockAndTintGetter level, @Local(argsOnly = true) BlockPos pos) {
        return original.call(fluid, self, side, other) && !WaterEdge.hides(level, pos, side);
    }
}
