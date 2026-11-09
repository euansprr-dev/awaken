package dev.awaken.hall.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The moment something is made (SPEC §52): a hit of light a quarter of a second long, then its name in the Hall's blackletter
 * and how it came out, over whichever screen finished it. The details stay on that screen's own card.
 */
public final class HallMoment {
    private HallMoment() {}

    private static final float HIT = .25F, HOLD = 2.6F, OUT = .6F;
    private static @Nullable Component title;
    private static String line = "";
    private static int colour;
    private static long at;
    private static @Nullable Stage stage;

    public static void show(String name, String how, int rgb) {
        if (HallConfig.classic()) return;
        title = Stage.blackletter(name);
        line = how;
        colour = rgb & 0xFFFFFF;
        at = System.nanoTime();
        HallSounds.landed();
        Stage.say(name + ". " + how);
    }

    public static void draw(GuiGraphicsExtractor g, int width, int height) {
        Component name = title;
        if (name == null) return;
        float t = (System.nanoTime() - at) / 1_000_000_000F;
        if (t > HIT + HOLD + OUT) {
            title = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        g.nextStratum();
        if (t < HIT && !mc.options.hideLightningFlash().get()) {
            float hit = 1F - t / HIT;
            g.fill(0, 0, width, height, (int) (hit * hit * .62F * 255F) << 24 | 0xFFE9C8);
        }
        if (stage == null) stage = new Stage(mc.font);
        Stage s = stage;
        s.begin(g, width, height);
        float in = Math.min(1F, t / .14F), a = in * (t > HIT + HOLD ? 1F - (t - HIT - HOLD) / OUT : 1F);
        float size = 5F;
        while (size > 2F && s.titleWidth(name, size) > 1520F) size -= 1F;
        float y = 150F + (1F - in) * (1F - in) * 26F, wide = s.titleWidth(name, size);
        s.glow(854F, y + 13F * size, wide / 2F + 150F, 26F * size + 60F, colour, .5F * a);
        s.titleCentre(name, 854F, y, size, Stage.argb(0xF4F6FB, a));
        s.centre(line, 854F, y + 26F * size + 10F, 4F, Stage.argb(colour, a));
        s.endBare();
    }
}
