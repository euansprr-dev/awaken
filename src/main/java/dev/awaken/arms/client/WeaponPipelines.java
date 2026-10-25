package dev.awaken.arms.client;

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

import java.util.HashMap;
import java.util.Map;

/**
 * The weapon shader (SPEC §24 "Weapons"; assets/awaken/shaders/core/arms_weapon.*, mirrored line for line by
 * tools/art/mesh_render.py): vanilla's entity inputs and lights, the weapon's texture as its albedo beside its
 * material map (metal, gloss, emissive, flow), drawn as triangles. Registered as optional: if a shader pack or a
 * driver can't build it, the weapons draw with vanilla's entity cutout instead (the same mesh and albedo, plain light).
 */
final class WeaponPipelines {
    private WeaponPipelines() {}

    static final RenderPipeline WEAPON = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Awaken.id("pipeline/arms_weapon"))
            .withVertexShader(Awaken.id("core/arms_weapon"))
            .withFragmentShader(Awaken.id("core/arms_weapon"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(true) // closed meshes wound outward: their backs are never seen
            .build();

    /**
     * The light round a weapon's stones: additive, depth-tested but writing no depth, on the game's lightning shaders
     * (with their improved-transparency set, as vanilla's own additive light), its falloff in the vertex colours.
     */
    private static final RenderPipeline.Snippet GLOW_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();
    static final RenderPipeline GLOW = RenderPipeline.builder(GLOW_SNIPPET)
            .withLocation(Awaken.id("pipeline/arms_weapon_glow"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build();
    private static final OitPipelineSet GLOW_OIT = OitPipelineSet.builder(Awaken.id("arms_weapon_glow"),
            RenderPipeline.builder(GLOW_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build();

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static RenderType glow;
    private static int ok = -1; // -1 unknown, 0 failed, 1 compiled

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(WEAPON);
        e.registerOptionalPipeline(GLOW);
        e.registerOitPipelineSet(GLOW_OIT);
    }

    /** Whether the weapon shader compiled (checked once per reload; a failed pipeline would recompile on each lookup). */
    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(WEAPON) != null && RenderSystem.getCompiledPipelineNullable(GLOW) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[arms] the weapon shader did not load; weapons draw with vanilla's entity light");
        }
        return ok == 1;
    }

    /** The weapon shader on this texture. */
    static RenderType weapon(Identifier texture) {
        return TYPES.computeIfAbsent(texture, t -> RenderType.create("awaken_arms_weapon",
                RenderSetup.builder(WEAPON).withTexture("Sampler0", t).useLightmap().affectsCrumbling().createRenderSetup()));
    }

    /** The stones' light. */
    static RenderType glow() {
        if (glow == null) glow = RenderType.create("awaken_arms_weapon_glow", RenderSetup.builder(GLOW).setOitPipelines(GLOW_OIT).createRenderSetup());
        return glow;
    }

    /**
     * Vanilla's entity cutout on the same texture: the fallback (it samples only the albedo half the uvs point at; its
     * pipeline draws quads, so each triangle goes in as a quad with its last corner twice).
     */
    static RenderType fallback(Identifier texture) {
        return RenderTypes.entityCutout(texture, false);
    }

    /** After a resource reload the pipelines are rebuilt: look again. */
    static void recheck() {
        ok = -1;
    }
}
