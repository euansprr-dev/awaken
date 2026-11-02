package dev.awaken.bells.client;

import dev.awaken.bells.BellNet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The bells' client side: the song as it rings, the bells drawn where they hang, and the Bell Book in the Codex. */
public final class BellClient {
    private BellClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(BellNet.Song.TYPE, (m, ctx) -> SongPlayer.start(m, false));
            e.register(BellNet.Page.TYPE, (m, ctx) -> BellBookSection.page = m);
        });
        // the bells you can see: on their hooks at home, and lying where they were won (SPEC §43.16)
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(dev.awaken.bells.BellContent.HOOKS_BE.get(), HooksRenderer::new);
            e.registerBlockEntityRenderer(dev.awaken.bells.BellContent.FOUND_BELL_BE.get(), FoundBellRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(SongPlayer::tick);
        NeoForge.EVENT_BUS.addListener(BellClient::tooltip);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            BellBookSection.forget();
            SongPlayer.stop();
        });
        BellBookSection.register();
    }

    /** What the frame and the beams are for, on their tooltips. */
    private static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
        var s = e.getItemStack();
        String line = s.is(dev.awaken.bells.BellContent.BELL_FRAME_ITEM.get())
                ? "Set it by your fire and hang bells on it. It has three hooks. Your bells ring from it at dawn and dusk."
                : s.is(dev.awaken.bells.BellContent.BELL_BEAM_ITEM.get())
                ? "Four hooks. Lay it across anything at home 8 or more blocks above your fire: a bell hung lower won't carry."
                : s.is(dev.awaken.bells.BellContent.GREAT_BEAM_ITEM.get())
                ? "One hook, for a Great Bell. It has to hang 12 or more blocks above your fire." : null;
        if (line != null) e.getToolTip().add(net.minecraft.network.chat.Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
