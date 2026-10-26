package dev.awaken.progression.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.net.ProgressionPayloads;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of progression: content sync, the Status screen key and the small level readout. */
public final class ProgressionClient {
    private ProgressionClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ProgressionClient::onPayloads);
        modBus.addListener(ProgressionClient::onLayers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> onTick());
        // the band opened: its tip has done its job (SPEC §27.7)
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.Opening.class, e -> {
            if (e.getNewScreen() instanceof StatusScreen || e.getNewScreen() instanceof dev.awaken.holo.client.HoloStatusScreen) {
                dev.awaken.core.api.TipHooks.client().done(Awaken.id("first/band"));
            }
        });
        CoreSections.register();
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        // In single player the integrated server already filled these shared maps; replacing them here would race it.
        event.register(ProgressionPayloads.ContentSync.TYPE, (msg, ctx) -> {
            if (!Minecraft.getInstance().hasSingleplayerServer()) msg.apply();
        });
        event.register(ProgressionPayloads.OpenBand.TYPE, (msg, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.gui.setScreen(statusOpener.get());
        });
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Awaken.id("level"), ProgressionClient::renderLevel);
    }

    /** What K opens: the flat StatusScreen unless the holo module installs its own (statusStyle = HOLO). */
    private static java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> statusOpener = StatusScreen::new;

    public static void installStatusOpener(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> opener) {
        statusOpener = opener;
    }

    private static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        while (AwakenKeys.STATUS.consumeClick()) {
            if (mc.player == null || mc.gui.screen() != null) continue;
            // the Status is the Holo band's: off the wrist (SPEC §16.4, the Registry ceremony) K finds nothing to open
            if (!dev.awaken.core.api.HoloBand.worn(mc.player)) {
                mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("gui.awaken.wrist.dark"));
                continue;
            }
            mc.gui.setScreen(statusOpener.get());
        }
    }

    /** A quiet level readout above the left end of the hotbar: "LV 12 · C" and a thin XP line. */
    private static void renderLevel(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        Profile p = Progression.profile(mc.player);
        if (!p.awakened() || !dev.awaken.core.api.HoloBand.worn(mc.player)) return; // the band shows it
        int x = g.guiWidth() / 2 - 91;
        int y = g.guiHeight() - 22 - 14;
        String s = "LV " + p.level() + " · " + p.rank().label;
        if (p.unspent() > 0) s += "  +" + p.unspent();
        g.text(mc.font, s, x - mc.font.width(s) - 6, y + 20, Glass.a(p.unspent() > 0 ? Glass.LANTERN : Glass.EDGE, 0.95f), true);
        int bw = mc.font.width(s);
        int bx = x - bw - 6;
        g.fill(bx, y + 30, bx + bw, y + 31, Glass.a(0x0A1020, 0.8f));
        g.fill(bx, y + 30, bx + (int) (bw * Math.min(1, p.xp() / Curve.xpToNext(p.level()))), y + 31, Glass.a(Glass.EDGE, 0.95f));
    }
}
