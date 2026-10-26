package dev.awaken.delve.client;

import dev.awaken.Awaken;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

/**
 * A cave place's caption (PLAN 8.4: "THE DELVERS' DEEP · old mine"): quieter than a region's, as a name arriving under
 * the ground should be. The name in spaced capitals, smaller and dimmer, a single hairline under it that draws out from
 * the middle, what the place is in the stone's own grey-violet after it; it rises a little as it fades.
 */
final class CaveHud {
    private CaveHud() {}

    private static String name = "", what = "";
    private static float from = -1000;
    private static int ticks;
    static final float IN = 14, HOLD = 70, OUT = 26;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.HOTBAR, Awaken.id("delve_caption"), CaveHud::render);
    }

    static void tick() {
        ticks++;
    }

    static void show(String placeName, String kindWords) {
        name = placeName.toUpperCase(Locale.ROOT);
        what = kindWords;
        from = ticks;
    }

    static void reset() {
        from = -1000;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float t = ticks + dt.getGameTimeDeltaPartialTick(false);
        float age = t - from;
        if (age < 0 || age > IN + HOLD + OUT) return;
        float a = age < IN ? age / IN : age < IN + HOLD ? 1 : 1 - (age - IN - HOLD) / OUT;
        a = a * a * (3 - 2 * a);
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float scale = Math.max(1.25F, Math.min(1.8F, w / 340F));
        int spacing = 2;
        int nameW = width(font, name, spacing);
        float lift = age > IN + HOLD ? (age - IN - HOLD) * 0.12F : 0;
        int y = (int) (h * 0.30F - lift);
        g.pose().pushMatrix();
        g.pose().translate(w / 2F, y);
        g.pose().scale(scale, scale);
        spaced(g, font, name, -nameW / 2, 0, spacing, argb(0.88F * a, 0xE4E0F0));
        g.pose().popMatrix();
        // one hairline under the name, drawn out from the middle as it arrives
        float open = Mth.clamp(age / (IN * 1.8F), 0, 1);
        int half = (int) ((nameW * scale / 2F + 14) * (1 - (1 - open) * (1 - open)));
        int ly = (int) (y + 9 * scale + 4);
        g.fill(w / 2 - half, ly, w / 2 + half, ly + 1, argb(0.42F * a, 0xB8A8D8));
        HudLook.centered(g, font, what, w / 2, ly + 5, argb(0.78F * a, 0xA89CC0), false);
    }

    private static int width(Font font, String s, int spacing) {
        int tw = 0;
        for (int i = 0; i < s.length(); i++) tw += font.width(String.valueOf(s.charAt(i))) + spacing;
        return Math.max(0, tw - spacing);
    }

    private static void spaced(GuiGraphicsExtractor g, Font font, String s, int x, int y, int spacing, int argb) {
        int px = x;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            HudLook.text(g, font, c, px, y, argb, false);
            px += font.width(c) + spacing;
        }
    }

    private static int argb(float a, int rgb) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
