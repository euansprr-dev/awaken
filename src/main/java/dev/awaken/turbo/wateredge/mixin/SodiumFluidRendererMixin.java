package dev.awaken.turbo.wateredge.mixin;

import dev.awaken.turbo.wateredge.WaterEdge;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium 0.9.3's fluid renderer decides each side face in {@code isSideExposedOffset(level, state, pos, side, height)};
 * a side WaterEdge hides counts as not exposed. Pseudo (Sodium may be absent) and not required: if it doesn't apply,
 * the probe never fires and water meshes as Sodium's own.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer")
abstract class SodiumFluidRendererMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void awaken$probe(CallbackInfo ci) {
        WaterEdge.applied();
    }

    @Inject(method = "isSideExposedOffset", at = @At("HEAD"), cancellable = true, require = 0)
    private void awaken$waterEdge(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction side, float height,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (WaterEdge.hides(pos, side)) cir.setReturnValue(false);
    }
}
