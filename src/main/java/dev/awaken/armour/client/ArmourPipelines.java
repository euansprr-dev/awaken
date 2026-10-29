package dev.awaken.armour.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.awaken.Awaken;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The armour shader (assets/awaken/shaders/core/armour.*; the weapons' look, tools/art/mesh_render.py mirrors it): the
 * set's texture as albedo beside its material map (metal, gloss, emissive, flow), vanilla's entity inputs and lights,
 * drawn as triangles. Optional: if a shader pack or a driver can't build it, armour draws with vanilla's entity cutout
 * (the same mesh and albedo, plain light).
 */
final class ArmourPipelines {
    private ArmourPipelines() {}

    static final RenderPipeline ARMOUR = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Awaken.id("pipeline/armour"))
            .withVertexShader(Awaken.id("core/armour"))
            .withFragmentShader(Awaken.id("core/armour"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(true) // closed boxes wound outward: their insides are never seen
            .build();

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static int ok = -1; // -1 unknown, 0 failed, 1 compiled

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(ARMOUR);
    }

    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(ARMOUR) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[armour] the armour shader did not load; armour draws with vanilla's entity light");
        }
        return ok == 1;
    }

    static RenderType armour(Identifier texture) {
        return TYPES.computeIfAbsent(texture, t -> RenderType.create("awaken_armour",
                RenderSetup.builder(ARMOUR).withTexture("Sampler0", t).useLightmap().affectsCrumbling().createRenderSetup()));
    }

    /** Vanilla's entity cutout on the same texture (quads: each triangle goes in with its last corner twice). */
    static RenderType fallback(Identifier texture) {
        return RenderTypes.entityCutout(texture, false);
    }

    static void recheck() {
        ok = -1;
    }
}
