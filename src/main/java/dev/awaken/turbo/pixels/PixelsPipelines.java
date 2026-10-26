package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The upscalers, as full-screen passes on vanilla's screen quad (both graphics APIs). Registered optional: a pipeline
 * that fails to build turns its upscaler off (FSR and SHARP fall back to bilinear, bilinear to native) instead of failing the
 * resource reload.
 */
final class PixelsPipelines {
    private PixelsPipelines() {}

    /** RCAS strength: AMD's recommended 0.2 stops below the maximum, exp2(-0.2). */
    static final float SHARPNESS = 0.87f;

    static final RenderPipeline BILINEAR = screen("bilinear").build();
    /** One pass: bilinear, sharpened with RCAS from a ring one world pixel apart. */
    static final RenderPipeline SHARP = screen("sharp").withShaderDefine("SHARPNESS", SHARPNESS).build();
    /** FSR 1 pass 1: edge-adaptive spatial upsampling, from the world's resolution to the screen's. */
    static final RenderPipeline EASU = screen("easu").build();
    /** FSR 1 pass 2: robust contrast-adaptive sharpening, at the screen's resolution. */
    static final RenderPipeline RCAS = screen("rcas").withShaderDefine("SHARPNESS", SHARPNESS).build();

    static final BindGroupLayout STEADY_INPUTS = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HandSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HistorySampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("PixelsSteady", UniformType.UNIFORM_BUFFER)
            .build();
    /** The steady pass (PixelsSteady): last frame's image carried over, held to this frame's, at the world's resolution. */
    static final RenderPipeline STEADY = RenderPipeline.builder()
            .withLocation(Awaken.id("pipeline/pixels_steady"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Awaken.id("core/pixels_steady"))
            .withBindGroupLayout(STEADY_INPUTS)
            .withColorTargetState(new ColorTargetState(java.util.Optional.empty(), GpuFormat.RGB10A2_UNORM, ColorTargetState.WRITE_ALL))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private static RenderPipeline.Builder screen(String name) {
        return RenderPipeline.builder()
                .withLocation(Awaken.id("pipeline/pixels_" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Awaken.id("core/pixels_" + name))
                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
    }

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(BILINEAR);
        e.registerOptionalPipeline(SHARP);
        e.registerOptionalPipeline(EASU);
        e.registerOptionalPipeline(RCAS);
        e.registerOptionalPipeline(STEADY);
    }

    // -1 unknown, 0 failed, 1 compiled; looked up once per resource load (a failed one would recompile on every lookup)
    private static int bilinearOk = -1, sharpOk = -1, fsrOk = -1, steadyOk = -1;

    static boolean bilinearOk() {
        if (bilinearOk < 0) bilinearOk = compiled(BILINEAR) ? 1 : 0;
        return bilinearOk == 1;
    }

    static boolean sharpOk() {
        if (sharpOk < 0) sharpOk = compiled(SHARP) ? 1 : 0;
        return sharpOk == 1;
    }

    static boolean fsrOk() {
        if (fsrOk < 0) fsrOk = compiled(EASU) && compiled(RCAS) ? 1 : 0;
        return fsrOk == 1;
    }

    /** The steady pass. */
    static boolean steadyOk() {
        if (steadyOk < 0) steadyOk = compiled(STEADY) ? 1 : 0;
        return steadyOk == 1;
    }

    /** After a resource reload the pipelines are rebuilt: look again. */
    static void recheck() {
        bilinearOk = sharpOk = fsrOk = steadyOk = -1;
    }

    private static boolean compiled(RenderPipeline p) {
        try {
            if (RenderSystem.getCompiledPipelineNullable(p) != null) return true;
        } catch (RuntimeException ignored) {
            // logged below
        }
        Awaken.LOG.warn("[pixels] pipeline {} did not compile", p.getLocation());
        return false;
    }
}
