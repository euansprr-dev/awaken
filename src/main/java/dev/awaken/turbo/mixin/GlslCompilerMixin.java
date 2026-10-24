package dev.awaken.turbo.mixin;

import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import dev.awaken.turbo.Turbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Turbo shader optimisation (switch shaderOpt, verified on 26.3): 26.3 compiles Minecraft's GLSL to SPIR-V at
 * shaderc optimisation level 0. On Vulkan this asks for the performance level instead; on OpenGL it leaves the level
 * as it was, so the OpenGL game is unchanged.
 */
@Mixin(GlslCompiler.class)
abstract class GlslCompilerMixin {
    @ModifyArg(method = "createBaseShaderOptions", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_options_set_optimization_level(JI)V"), index = 1)
    private int awaken$optimise(int level) {
        return Turbo.shaderOptimisationLevel(level);
    }
}
