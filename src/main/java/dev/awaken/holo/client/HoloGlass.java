package dev.awaken.holo.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/** The bag's glass, for other modules' container screens (SPEC §56): the same panel, glow and light-text as the holo inventory. */
public final class HoloGlass {
    private HoloGlass() {}

    /** Whether the bag is showing as glass right now; when it isn't, chests stay vanilla's too. */
    public static boolean on() {
        return HoloClient.holoInventory();
    }

    public static boolean reducedMotion() {
        return HoloConfig.reducedMotion;
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, float alpha) {
        HoloGuiLook.INSTANCE.panel(g, x, y, w, h, alpha);
    }

    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent, float strength) {
        HoloGuiLook.INSTANCE.glow(g, x, y, w, h, accent, strength);
    }

    public static void text(GuiGraphicsExtractor g, Font font, FormattedCharSequence s, int x, int y, int argb) {
        HoloGuiLook.INSTANCE.text(g, font, s, x, y, argb);
    }
}
