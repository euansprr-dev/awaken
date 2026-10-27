package dev.awaken.gear.premium.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
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
 * The armour shader (the premium test, Level 2): the weapons' shader (arms_weapon) with armour's inputs. A model part sends its
 * tint (here the rim colour, with the set's level in the alpha) and its overlay instead of a weapon mesh's accent and state; the
 * material map is in the texture's right half, as for the weapons. Optional like theirs: if it can't build, the same model draws with
 * vanilla's armour render type.
 */
final class PremiumPipelines {
    private PremiumPipelines() {}

    static final RenderPipeline ARMOUR = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Awaken.id("pipeline/arms_armour"))
            .withVertexShader(Awaken.id("core/arms_armour"))
            .withFragmentShader(Awaken.id("core/arms_armour"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false) // a hood is seen from inside, as vanilla's armour is
            .build();

    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
    private static int ok = -1; // -1 unknown, 0 failed, 1 compiled

    static void onRegister(RegisterRenderPipelinesEvent e) {
        e.registerOptionalPipeline(ARMOUR);
    }

    /** Whether the armour shader compiled (checked once per reload). */
    static boolean ok() {
        if (ok < 0) {
            try {
                ok = RenderSystem.getCompiledPipelineNullable(ARMOUR) != null ? 1 : 0;
            } catch (RuntimeException ex) {
                ok = 0;
            }
            if (ok == 0) Awaken.LOG.warn("[gear] the armour shader did not load; Level 2 draws with vanilla's armour render type");
        }
        return ok == 1;
    }

    static void recheck() {
        ok = -1;
        TYPES.clear();
    }

    /** The armour shader on this texture. */
    static RenderType armour(Identifier texture) {
        return TYPES.computeIfAbsent(texture, t -> RenderType.create("awaken_armour",
                RenderSetup.builder(ARMOUR).withTexture("Sampler0", t).useLightmap().affectsCrumbling().createRenderSetup()));
    }

    /** The fallback: vanilla's armour render type on the same texture. */
    static RenderType plain(Identifier texture) {
        return RenderTypes.armorCutoutNoCull(texture);
    }
}
