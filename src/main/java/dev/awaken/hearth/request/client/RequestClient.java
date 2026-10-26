package dev.awaken.hearth.request.client;

import dev.awaken.Awaken;
import dev.awaken.progression.client.StatusSections;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client side of Anchor requests: the Status screen section. Found by FML on the client only (no common code refers to
 * it), so the hearth module's one init line stays the only wiring.
 */
@EventBusSubscriber(modid = Awaken.MODID, value = Dist.CLIENT)
public final class RequestClient {
    private RequestClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> StatusSections.add(StatusSections.Tab.JOURNAL, new RequestSection()));
    }
}
