package dev.awaken.farsight.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
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
            .withUniform("ChunkSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("VoxelSampler", UniformType.COMBINED_IMAGE_SAMPLER)
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

    static final BindGroupLayout TREE_INPUTS = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("Trees", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
            .withUniform("TreeSlots", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_SINT)
            .withUniform("ShadeSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("GenHeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HeightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ChunkSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("VoxelSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    /** Far forests: opaque voxel trees from the tree buffer, after the terrain, into the same targets. */
    static final RenderPipeline TREES = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(TREE_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_trees"))
            .withVertexShader(Awaken.id("core/farsight_trees"))
            .withFragmentShader(Awaken.id("core/farsight_trees"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    static final BindGroupLayout VOXEL_INPUTS = BindGroupLayout.builder()
            .withUniform("FarFrame", UniformType.UNIFORM_BUFFER)
            .withUniform("FarVoxel", UniformType.UNIFORM_BUFFER)
            .withUniform("Quads", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
            .withUniform("Chunks", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_SINT)
            .withUniform("Looks", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .withUniform("AtlasSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("LightSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("ChunkSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    /** City Sight (Far Sight 3): real chunks' blocks from their pulled quads, after the terrain, into the same targets. */
    static final RenderPipeline VOXELS = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(VOXEL_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_voxels"))
            .withVertexShader(Awaken.id("core/farsight_voxels"))
            .withFragmentShader(Awaken.id("core/farsight_voxels"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    /** City Sight without discard, for chunks clear of the render distance's edge and of a level's fade (see VoxelLayer#EDGED). */
    static final RenderPipeline VOXELS_PLAIN = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(VOXEL_INPUTS)
            .withLocation(Awaken.id("pipeline/farsight_voxels_plain"))
            .withVertexShader(Awaken.id("core/farsight_voxels"))
            .withFragmentShader(Awaken.id("core/farsight_voxels"))
            .withShaderDefine("NO_DISCARD")
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
        e.registerOptionalPipeline(TREES);
        e.registerOptionalPipeline(VOXELS);
        e.registerOptionalPipeline(VOXELS_PLAIN);
    }

    /** Whether both pipelines compiled; checked once per resource load (a failed one would recompile every lookup). */
    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(TERRAIN) != null && RenderSystem.getCompiledPipelineNullable(BAKE) != null
                        && RenderSystem.getCompiledPipelineNullable(MARKS) != null && RenderSystem.getCompiledPipelineNullable(TREES) != null
                        && RenderSystem.getCompiledPipelineNullable(VOXELS) != null && RenderSystem.getCompiledPipelineNullable(VOXELS_PLAIN) != null ? 1 : 0;
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
