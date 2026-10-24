package dev.awaken.turbo.pixels;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The upscalers, as full-screen passes on vanilla's screen quad (both graphics APIs). Registered optional: a pipeline
 * that fails to build turns its upscaler off (FSR falls back to bilinear, bilinear to native) instead of failing the
 * resource reload.
 */
final class PixelsPipelines {
    private PixelsPipelines() {}

    /** RCAS strength: AMD's recommended 0.2 stops below the maximum, exp2(-0.2). */
    static final float SHARPNESS = 0.87f;

    static final RenderPipeline BILINEAR = screen("bilinear").build();
    /** FSR 1 pass 1: edge-adaptive spatial upsampling, from the world's resolution to the screen's. */
    static final RenderPipeline EASU = screen("easu").build();
    /** FSR 1 pass 2: robust contrast-adaptive sharpening, at the screen's resolution. */
    static final RenderPipeline RCAS = screen("rcas").withShaderDefine("SHARPNESS", SHARPNESS).build();

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
        e.registerOptionalPipeline(EASU);
        e.registerOptionalPipeline(RCAS);
    }

    // -1 unknown, 0 failed, 1 compiled; looked up once per resource load (a failed one would recompile on every lookup)
    private static int bilinearOk = -1, fsrOk = -1;

    static boolean bilinearOk() {
        if (bilinearOk < 0) bilinearOk = compiled(BILINEAR) ? 1 : 0;
        return bilinearOk == 1;
    }

    static boolean fsrOk() {
        if (fsrOk < 0) fsrOk = compiled(EASU) && compiled(RCAS) ? 1 : 0;
        return fsrOk == 1;
    }

    /** After a resource reload the pipelines are rebuilt: look again. */
    static void recheck() {
        bilinearOk = fsrOk = -1;
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
