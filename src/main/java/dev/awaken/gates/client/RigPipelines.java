package dev.awaken.gates.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * The Gate Rig's light (SPEC §17 "Gate Rig"): an additive render type for the Mirror Hall's beam, the cracks that glow
 * before a row falls and the wells' shimmer. Depth-tested (walls hide it) but writing no depth, so particles and water
 * behind it still draw; its cores are near white, so Lens's bloom (keyed on the brightest channel) takes them. Uses the
 * game's lightning shaders, with their improved-transparency set, like vanilla's own additive light.
 */
public final class RigPipelines {
    private RigPipelines() {}

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();

    public static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/rig_light"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();

    public static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("rig_light"),
            RenderPipeline.builder(SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();

    /**
     * (Gate pillars) additive light without the world's fog: vanilla's render-distance fog counts height as distance
     * (max of the horizontal distance and |dy|), so a column hundreds of blocks tall came out cut off flat at the fog
     * line. The pillar fades itself out instead.
     */
    public static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Awaken.id("pipeline/rig_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();

    /**
     * (Gate pillars) Far Sight's depth and planes: its land isn't in the game's depth, so a pillar behind a far ridge
     * drew over it (Euan, 29 Oct: "showing on top of the cliff here even tho the gate is behind it"). The pillar's own
     * shader hides any fragment where far land is nearer than the pillar really is (it is drawn pulled in along the
     * sight line: UV0.x says how many times further off it really stands). Without these the pillars draw as before.
     */
    static final BindGroupLayout PILLAR_INPUTS = BindGroupLayout.builder()
            .withUniform("GatePillar", UniformType.UNIFORM_BUFFER)
            .withUniform("FarDepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    private static final RenderPipeline.Snippet PILLAR_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(PILLAR_INPUTS)
            .withVertexShader(Awaken.id("core/gate_pillar"))
            .withFragmentShader(Awaken.id("core/gate_pillar"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();

    /** The pillar's haze: coloured air, alpha-blended. */
    public static final RenderPipeline PILLAR_HAZE = RenderPipeline.builder(PILLAR_SNIPPET)
            .withLocation(Awaken.id("pipeline/gate_pillar_haze"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    /** The pillar's core and glow: light added, as {@link #GLOW}. */
    public static final RenderPipeline PILLAR_LIGHT = RenderPipeline.builder(PILLAR_SNIPPET)
            .withLocation(Awaken.id("pipeline/gate_pillar_light"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();

    private static RenderType type;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(LIGHT);
        e.registerOitPipelineSet(LIGHT_OIT);
        e.registerOptionalPipeline(GLOW);
        e.registerOptionalPipeline(PILLAR_HAZE);
        e.registerOptionalPipeline(PILLAR_LIGHT);
    }

    /** The rig's light, or vanilla lightning if the pipeline didn't build (never no light at all). */
    public static RenderType light() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(LIGHT) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
        }
        if (ok == 0) return RenderTypes.lightning();
        if (type == null) type = RenderType.create("awaken_rig_light", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return type;
    }
}
