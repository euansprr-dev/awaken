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

    /**
     * The feathers under the video setting Improved Transparency: there every blended type is drawn through its own set
     * of three pipelines, and a type without one throws as it draws. The game's own pairing for its unlit entity type
     * (the eyes), both sides.
     */
    private static final OitPipelineSet FEATHER_OIT = OitPipelineSet.builder(Awaken.id("ascent_wing_feather"),
            RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withCull(false)).build();

    /**
     * The same feathers on a position and a colour alone: 16 bytes a vertex where the entity format takes 36 (a feather
     * takes no light, its texture is one white texel and its shade is in its colour, so the rest was sent for nothing).
     * The game's lightning shaders, alpha-blended, writing depth, both sides; in fog they thin toward nothing where the
     * entity shader tints toward the fog's colour.
     */
    static final RenderPipeline FEATHER_LEAN = RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
            .withLocation(Awaken.id("pipeline/ascent_wing_feather_lean"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    private static final OitPipelineSet FEATHER_LEAN_OIT = OitPipelineSet.builder(Awaken.id("ascent_wing_feather_lean"),
            RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET).withCull(false)).build();

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

    private static final RenderType[] FEATHERS = new RenderType[3], LEAN = new RenderType[3];
    /** Which of the two the feathers are drawn through (/awaken wings set probe_lean). */
    static boolean lean;
    private static RenderType halo;
    private static int ok = -1;

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(FEATHER);
        e.registerOptionalPipeline(HALO);
        e.registerOptionalPipeline(FEATHER_LEAN);
        e.registerOitPipelineSet(FEATHER_OIT);
        e.registerOitPipelineSet(FEATHER_LEAN_OIT);
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

    private static int leanOk = -1;

    private static boolean leanOk() {
        if (leanOk < 0) {
            try {
                leanOk = RenderSystem.getCompiledPipelineNullable(FEATHER_LEAN) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                leanOk = 0;
            }
            if (leanOk == 0) Awaken.LOG.warn("[ascent] the wings' lean pipeline did not build; they draw with the entity format");
        }
        return leanOk == 1;
    }

    /**
     * The feathers' type for one pair of wings (0 to 2); vanilla's translucent entity type if ours didn't build (lit by
     * the world, but there). A type a pair, the same in everything but name, so each pair is its own draw: the game joins
     * consecutive geometry of one type into a single draw, and the Sovereign's three pairs in one draw (33,600 vertices)
     * came out as garbage on the tour's GPU (a white flood, stray triangles), where two pairs (22,200) were whole.
     */
    static RenderType feather(int pair) {
        int i = Math.max(0, Math.min(FEATHERS.length - 1, pair));
        if (lean && ok() && leanOk()) {
            if (LEAN[i] == null) {
                LEAN[i] = RenderType.create("awaken_ascent_wing_feather_lean_" + i,
                        RenderSetup.builder(FEATHER_LEAN).setOitPipelines(FEATHER_LEAN_OIT).createRenderSetup());
            }
            return LEAN[i];
        }
        if (FEATHERS[i] == null) {
            FEATHERS[i] = ok() ? RenderType.create("awaken_ascent_wing_feather_" + i,
                    RenderSetup.builder(FEATHER).withTexture("Sampler0", WHITE).setOitPipelines(FEATHER_OIT).createRenderSetup())
                    : RenderTypes.entityTranslucent(WHITE);
        }
        return FEATHERS[i];
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
