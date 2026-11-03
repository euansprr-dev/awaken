package dev.awaken.kin.client;

import dev.awaken.kin.KinContent;
import dev.awaken.kin.KinNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The kin's client side: their renderers and rigs, the band's Hearth page, the kin plate and the ride line. */
public final class KinClient {
    private KinClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                KinContent.TYPES.forEach((k, t) -> e.registerEntityRenderer(t.get(), ctx -> new KinRenderer(ctx, k))));
        modBus.addListener(KinRenderer::layers);
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(KinNet.Page.TYPE, (m, ctx) -> KinPageSection.page = m);
            e.register(KinNet.Bond.TYPE, (m, ctx) -> KinPlate.onBond(m));
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            KinPageSection.forget();
            KinPlate.reset();
        });
        // the kin plate and the ride line (SPEC §43.17)
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
            KinPlate.tick();
            RideLine.tick();
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent.class, KinPlate::extract);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderNameTagEvent.CanRender.class, KinPlate::nameTag);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent e) -> e.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH, RideLine.LAYER, RideLine::render));
        KinPageSection.register();
        dev.awaken.progression.client.Catalogue.fixed(dev.awaken.progression.client.StatusSections.Shelf.KIN, KinPageSection::slots);
        dev.awaken.kin.works.client.WorksClient.init(modBus);
    }
}
