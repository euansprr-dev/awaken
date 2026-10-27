package dev.awaken.core.client;

import dev.awaken.Awaken;
import dev.awaken.core.api.UiHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Awaken's HUD stands aside while a screen is open (SPEC §12 {@code UiHooks}; Euan, 2026-10-27: the gate's run panel
 * drew over the K menu). The game draws the HUD under every screen and leaves it to the screen's blurred, dimmed
 * background to cover it; the band, the holo inventory and the Atlas draw none (the world stays in view behind them),
 * so every HUD layer stayed sharp on top of them. Every layer in Awaken's namespace is skipped while any screen but chat
 * is open; a scene's layer that must draw over its own screen opts back in ({@link UiHooks#drawsOverScreens}).
 * Vanilla's layers are left as the game draws them.
 */
final class HudUnderScreens {
    private HudUnderScreens() {}

    static void pre(RenderGuiLayerEvent.Pre e) {
        Identifier id = e.getName();
        if (!Awaken.MODID.equals(id.getNamespace())) return;
        Screen s = Minecraft.getInstance().gui.screen();
        if (s == null || s instanceof ChatScreen || UiHooks.drawsOverScreen(id)) return;
        e.setCanceled(true);
    }
}
