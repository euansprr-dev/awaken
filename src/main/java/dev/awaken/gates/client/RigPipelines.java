package dev.awaken.gates.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
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

    /** (Gate pillars) the same, alpha-blended: a colour that shows against a bright daytime sky, where light added can't. */
    public static final RenderPipeline HAZE = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/rig_haze"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();

    public static final OitPipelineSet HAZE_OIT = OitPipelineSet.builder(Awaken.id("rig_haze"), RenderPipeline.builder(SNIPPET)).build();

    private static RenderType type, hazeType;
    private static int ok = -1, hazeOk = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(LIGHT);
        e.registerOitPipelineSet(LIGHT_OIT);
        e.registerOptionalPipeline(HAZE);
        e.registerOitPipelineSet(HAZE_OIT);
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

    /** The haze (alpha-blended), or vanilla's debug quads if the pipeline didn't build. */
    public static RenderType haze() {
        if (hazeOk < 0) {
            try {
                hazeOk = RenderSystem.getCompiledPipelineNullable(HAZE) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                hazeOk = 0;
            }
        }
        if (hazeOk == 0) return RenderTypes.debugQuads();
        if (hazeType == null) hazeType = RenderType.create("awaken_rig_haze", RenderSetup.builder(HAZE).setOitPipelines(HAZE_OIT).sortOnUpload().createRenderSetup());
        return hazeType;
    }
}
