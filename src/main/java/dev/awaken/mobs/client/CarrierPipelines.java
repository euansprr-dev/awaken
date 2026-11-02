package dev.awaken.mobs.client;

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
 * The carriers' and shots' light (SPEC §47.2): additive quads on the game's lightning shaders (with their
 * improved-transparency set), depth-tested so walls hide them but writing no depth; and their shade, the same corners
 * alpha-blended under the light, so a crest or a ring still reads on snow or pale sand (light alone only brightens).
 * Optional: if either fails to build, vanilla's lightning type draws them instead (never nothing).
 */
final class CarrierPipelines {
    private CarrierPipelines() {}

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();

    static final RenderPipeline LIGHT = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/mobs_carrier_light"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    static final RenderPipeline SHADE = RenderPipeline.builder(SNIPPET)
            .withLocation(Awaken.id("pipeline/mobs_carrier_shade"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build();
    private static final OitPipelineSet LIGHT_OIT = OitPipelineSet.builder(Awaken.id("mobs_carrier_light"),
            RenderPipeline.builder(SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();

    private static RenderType light, shade;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(LIGHT);
        e.registerOptionalPipeline(SHADE);
        e.registerOitPipelineSet(LIGHT_OIT);
    }

    private static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(LIGHT) != null && RenderSystem.getCompiledPipelineNullable(SHADE) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[mobs] the carriers' light did not build; they draw with vanilla's lightning");
        }
        return ok == 1;
    }

    static RenderType light() {
        if (!ok()) return RenderTypes.lightning();
        if (light == null) light = RenderType.create("awaken_mobs_carrier_light", RenderSetup.builder(LIGHT).setOitPipelines(LIGHT_OIT).createRenderSetup());
        return light;
    }

    /** The shade under the light, or null when it can't be drawn (the light alone then). */
    static RenderType shade() {
        if (!ok()) return null;
        if (shade == null) shade = RenderType.create("awaken_mobs_carrier_shade", RenderSetup.builder(SHADE).createRenderSetup());
        return shade;
    }
}
