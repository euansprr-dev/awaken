package dev.awaken.mobs.client;

import dev.awaken.mobs.client.anim.HeroRenderer;
import dev.awaken.mobs.client.anim.WildRenderer;
import dev.awaken.mobs.client.model.AwModels;
import dev.awaken.mobs.entity.AwakenMob;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

import java.util.function.BiConsumer;

/** Client entry point for the mobs module: renderers and model layers for every creature. Only loaded on the client. */
public final class MobsClient {
    private MobsClient() {}

    public static void init(IEventBus modBus) {
        dev.awaken.mobs.client.kit.KitConfig.register();
        modBus.addListener(AwRenderers::layers);
        modBus.addListener(AwRenderers::renderers);
        modBus.addListener(RitualRenderers::renderers);
        modBus.addListener(SeepParticles::register);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, MobsClient::looks);
    }

    /**
     * NeoForge clears a render state's data right after extractRenderState (RenderStateExtensions), so the look our
     * renderers put there never reached the model: no life layer (SPEC §23), tint, glow or flash. This puts the same
     * look back after the clear. Bosses (their own renderers, and AwMobRenderer's) are left as they are for now.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void looks(RegisterRenderStateModifiersEvent e) {
        BiConsumer<Entity, EntityRenderState> back = (entity, state) -> {
            if (entity instanceof AwakenMob m && !AwMobRenderer.boss(m.def())) state.setRenderData(AwModels.LOOK, m.lastLook());
        };
        e.registerEntityModifier((Class) HeroRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) WildRenderer.class, (BiConsumer) back);
        e.registerEntityModifier((Class) AwMobRenderer.class, (BiConsumer) back);
    }
}
