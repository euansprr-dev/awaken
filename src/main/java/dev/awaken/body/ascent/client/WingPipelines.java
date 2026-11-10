package dev.awaken.body.ascent.client;

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
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/**
 * How the feathered wings are drawn (SPEC §62). A wing is about 230 boxes that overlap one another, so its faces must
 * hide each other by depth: vanilla's emissive entity type writes no depth (its faces would draw in the order they
 * were sent, the far side of a feather over the near), and its lit type shades by the sun and the lightmap, which light
 * is not. So the feathers have their own pipeline on the game's entity shaders: unlit and unshaded (the eyes' defines;
 * each face's shade is in its vertex colour, as on the plan's model), alpha-blended for the fade into the mark, writing
 * depth, both sides. Their soft halos are additive quads on the lightning shaders, depth-tested, writing none.
 * Optional: if either fails to build, vanilla types draw them (never nothing).
 */
final class WingPipelines {
    private WingPipelines() {}

    /** A plain white texel: a feather's colour is its vertices'. */
    static final Identifier WHITE = Awaken.id("textures/ascent/feather.png");

    static final RenderPipeline FEATHER = RenderPipeline.builder(RenderPipelines.ENTITY_NO_LIGHTMAP_SNIPPET, RenderPipelines.EYES_SNIPPET)
            .withLocation(Awaken.id("pipeline/ascent_wing_feather"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final RenderPipeline.Snippet HALO_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();

    static final RenderPipeline HALO = RenderPipeline.builder(HALO_SNIPPET)
            .withLocation(Awaken.id("pipeline/ascent_wing_halo"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();

    private static final OitPipelineSet HALO_OIT = OitPipelineSet.builder(Awaken.id("ascent_wing_halo"),
            RenderPipeline.builder(HALO_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();

    private static RenderType feather, halo;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(FEATHER);
        e.registerOptionalPipeline(HALO);
        e.registerOitPipelineSet(HALO_OIT);
    }

    private static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(FEATHER) != null && RenderSystem.getCompiledPipelineNullable(HALO) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[ascent] the wings' own pipelines did not build; they draw with vanilla's types");
        }
        return ok == 1;
    }

    /** The feathers' type; vanilla's translucent entity type if ours didn't build (lit by the world, but there). */
    static RenderType feather() {
        if (feather == null) {
            feather = ok() ? RenderType.create("awaken_ascent_wing_feather", RenderSetup.builder(FEATHER).withTexture("Sampler0", WHITE).createRenderSetup())
                    : RenderTypes.entityTranslucent(WHITE);
        }
        return feather;
    }

    /** The halos' type (position and colour only); vanilla's lightning if ours didn't build. */
    static RenderType halo() {
        if (halo == null) {
            halo = ok() ? RenderType.create("awaken_ascent_wing_halo", RenderSetup.builder(HALO).setOitPipelines(HALO_OIT).createRenderSetup())
                    : RenderTypes.lightning();
        }
        return halo;
    }
}
