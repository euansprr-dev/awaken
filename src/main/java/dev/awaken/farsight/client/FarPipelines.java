package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.Optional;

/**
 * Far Sight's two pipelines, registered as optional so a driver that can't build them turns Far Sight off instead of
 * failing the resource reload. Neither takes a vertex buffer: every vertex is worked out from its index.
 * <ul>
 *   <li>{@link #TERRAIN}: the rings' columns, quads from one shared index buffer, into the main colour target and Far
 *       Sight's own depth (reversed-Z, the game's convention);</li>
 *   <li>{@link #BAKE}: one triangle over the ring atlases that derives each cell's normal and valley shade (the
 *       fragment-pass implementation of {@link FarGpu}).</li>
 * </ul>
 */
final class FarPipelines {
    private FarPipelines() {}

    static final BindGroupLayout TERRAIN_INPUTS = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("FarDraw", UniformType.UNIFORM_BUFFER)
            .withUniform("HeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ShadeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static final BindGroupLayout BAKE_INPUTS = BindGroupLayout.builder()
            .withUniform("FarBake", UniformType.UNIFORM_BUFFER)
            .withUniform("HeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static final RenderPipeline TERRAIN = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(TERRAIN_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_terrain"))
            .withVertexShader(Awaken.id("core/farsight_terrain"))
            .withFragmentShader(Awaken.id("core/farsight_terrain"))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    static final RenderPipeline BAKE = RenderPipeline.builder()
            .withBindGroupLayout(BAKE_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_bake"))
            .withVertexShader(Awaken.id("core/farsight_bake"))
            .withFragmentShader(Awaken.id("core/farsight_bake"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(Optional.empty())
            .withCull(false)
            .build();

    private static int ok = -1; // -1 unknown, 0 failed, 1 compiled

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(TERRAIN);
        e.registerOptionalPipeline(BAKE);
    }

    /** Whether both pipelines compiled; checked once per resource load (a failed one would recompile every lookup). */
    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(TERRAIN) != null && RenderSystem.getCompiledPipelineNullable(BAKE) != null ? 1 : 0;
            } catch (RuntimeException e) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[farsight] its shaders did not load; Far Sight stays off");
        }
        return ok == 1;
    }

    static void recheck() {
        ok = -1;
    }
}
