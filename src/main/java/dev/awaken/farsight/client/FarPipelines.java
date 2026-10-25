package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
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
 * Far Sight's pipelines, registered as optional so a driver that can't build them turns Far Sight off instead of
 * failing the resource reload. Neither takes a vertex buffer: every vertex is worked out from its index.
 * <ul>
 *   <li>{@link #TERRAIN}: the rings as continuous meshes, triangles from one index buffer per piece width, into the main
 *       colour target and Far Sight's own depth (reversed-Z, the game's convention);</li>
 *   <li>{@link #BAKE}: one triangle over the ring atlases that derives each corner's normal, valley shade and sunlight
 *       (the fragment-pass implementation of {@link FarGpu});</li>
 *   <li>{@link #MARKS}: landmarks ({@link dev.awaken.farsight.FarLandmarks}) as glowing columns.</li>
 * </ul>
 */
final class FarPipelines {
    private FarPipelines() {}

    static final BindGroupLayout TERRAIN_INPUTS = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("FarDraw", UniformType.UNIFORM_BUFFER)
            .withUniform("HeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("InfoSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("CrownSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenHeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenColorSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenInfoSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenCrownSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ShadeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static final BindGroupLayout BAKE_INPUTS = BindGroupLayout.builder()
            .withUniform("FarBake", UniformType.UNIFORM_BUFFER)
            .withUniform("HeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenHeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    static final RenderPipeline TERRAIN = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(TERRAIN_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_terrain"))
            .withVertexShader(Awaken.id("core/farsight_terrain"))
            .withFragmentShader(Awaken.id("core/farsight_terrain"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    static final BindGroupLayout MARK_INPUTS = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("FarMarks", UniformType.UNIFORM_BUFFER)
            .build();

    /** Landmarks: glowing columns over far terrain, tested against Far Sight's depth, never writing it. */
    static final RenderPipeline MARKS = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(MARK_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_marks"))
            .withVertexShader(Awaken.id("core/farsight_marks"))
            .withFragmentShader(Awaken.id("core/farsight_marks"))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
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
        e.registerOptionalPipeline(MARKS);
    }

    /** Whether both pipelines compiled; checked once per resource load (a failed one would recompile every lookup). */
    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(TERRAIN) != null && RenderSystem.getCompiledPipelineNullable(BAKE) != null
                        && RenderSystem.getCompiledPipelineNullable(MARKS) != null ? 1 : 0;
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
