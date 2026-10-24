package dev.awaken.mobs.client;

import dev.awaken.mobs.Ritual;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderers for the Ritual of the Bell's cast (the Seated One and its statues). */
final class RitualRenderers {
    private RitualRenderers() {}

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Ritual.SEATED_ONE.get(), NoopRenderer::new);
        e.registerEntityRenderer(Ritual.STATUE.get(), NoopRenderer::new);
    }
}
