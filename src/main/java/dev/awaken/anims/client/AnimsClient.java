package dev.awaken.anims.client;

import dev.awaken.core.api.FirstPerson;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hands, the first-person system (SPEC §24, docs/anims/PLAN.md): installs itself as core.api.FirstPerson's player and
 * owns the hand pass (Hands) and the body drawn in the world in first person (WorldBody).
 */
public final class AnimsClient {
    private AnimsClient() {}

    public static void init(IEventBus modBus) {
        FirstPerson.install(Hands.INSTANCE);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> Hands.tick());
        bus.addListener(RenderFrameEvent.Pre.class, e -> WorldBody.frame());
        bus.addListener(ExtractLevelRenderStateEvent.class, WorldBody::onExtract);
        bus.addListener(SubmitCustomGeometryEvent.class, WorldBody::onSubmit);
        bus.addListener(EventPriority.HIGHEST, RenderHandEvent.class, Hands::first);
        bus.addListener(EventPriority.LOWEST, true, RenderHandEvent.class, Hands::lastOff);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            Hands.reset();
            WorldBody.reset();
        });
    }
}
