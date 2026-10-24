package dev.awaken.turbo.pixels.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.awaken.turbo.pixels.PixelsHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pixels' two moments in {@code GameRenderer.render()}: just before the world is drawn, and just after its post effects
 * (Lens first), before the GUI. No NeoForge event fires at either (FrameGraphSetupEvent comes after the level's own
 * targets are sized). Neither injection is required: if one doesn't apply, Pixels never swaps (PixelsFrame waits to
 * see both run in one frame).
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Shadow @Final private RenderTarget mainRenderTarget;
    @Shadow @Final private RenderTarget hud3DTarget;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"), require = 0)
    private void awaken$pixelsBeforeLevel(CallbackInfo ci) {
        PixelsHooks.beforeLevel(this.mainRenderTarget, this.hud3DTarget);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER), require = 0)
    private void awaken$pixelsAfterPostEffects(CallbackInfo ci) {
        PixelsHooks.afterPostEffects(this.mainRenderTarget, this.hud3DTarget);
    }
}
