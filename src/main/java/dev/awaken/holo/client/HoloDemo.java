package dev.awaken.holo.client;

import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * {@code /awaken holo demo}: a VIEW panel with real text, bars and an item icon (mode 1), or the same panel unfolding
 * from the wrist (mode 2). Drawn with {@link Ui} through {@link HoloGraphics}, exactly as a Status section would be.
 */
final class HoloDemo {
    private HoloDemo() {}

    static final int W = 236, H = 132;
    private static final HoloGraphics G = HoloGraphics.create();
    private static final ItemStack FALLBACK = new ItemStack(Items.DIAMOND_SWORD);

    static void show(HoloPanel p, int mode) {
        p.w = W;
        p.h = H;
        p.screenFrac = 0.31f; // the same pixels per unit as the K menu's wider panel
        if (mode == 1) {
            p.anchor = HoloPanel.Anchor.VIEW;
            p.timeline.seek(HoloTimeline.OPEN_MS);
            p.timeline.hold(false);
        } else if (mode == 2) {
            p.anchor = HoloPanel.Anchor.WRIST;
            p.timeline.hold(false);
            p.timeline.seek(0);
        }
    }

    /** Records this frame's content. */
    static void record(HoloPanel p) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        p.canvas.begin(p.w, p.h);
        HoloGraphics g = G.begin(p.canvas, p.w, p.h);
        int x = 12, w = p.w - 24;
        g.text(font, "◇ SIGNAL", x, 11, Glass.a(Glass.EDGE, 1f), false);
        String clock = "HOLO · LIVE";
        g.text(font, clock, x + w - font.width(clock), 11, Glass.a(Glass.LABEL, 0.8f), false);
        g.horizontalLine(x, x + w - 1, 24, Glass.a(Glass.EDGE, 0.22f));
        Ui ui = Ui.draw((GuiGraphicsExtractor) g, font, x, 30, w, -10000, -10000);
        String name = mc.player != null ? mc.player.getName().getString() : "Hunter";
        ui.pair(name, "Lv 12  ·  E-rank", Glass.a(Glass.TITLE, 1f), Glass.a(Glass.AMBER, 1f));
        ui.gap(3);
        float hp = mc.player != null ? mc.player.getHealth() / mc.player.getMaxHealth() : 0.8f;
        ui.pair("Health", Math.round(hp * 100) + "%", Glass.a(Glass.LABEL, 1f), Glass.a(Glass.GOOD, 1f));
        ui.bar(hp, Glass.GOOD);
        ui.pair("Energy", "64 / 80", Glass.a(Glass.LABEL, 1f), Glass.a(Glass.EDGE, 1f));
        ui.bar(0.8, Glass.EDGE);
        ui.pair("Next level", "1 240 / 2 000 XP", Glass.a(Glass.LABEL, 1f), Glass.a(Glass.AMBER, 1f));
        ui.bar(0.62, Glass.AMBER);
        ui.gap(4);
        int iy = ui.y();
        ItemStack held = mc.player != null && !mc.player.getMainHandItem().isEmpty() ? mc.player.getMainHandItem() : FALLBACK;
        g.fill(x, iy, x + 20, iy + 20, Glass.a(0x0A1020, 0.85f));
        g.outline(x, iy, 20, 20, Glass.a(Glass.EDGE, 0.45f));
        g.item(held, x + 2, iy + 2);
        g.text(font, held.getHoverName().getString(), x + 26, iy + 2, Glass.a(Glass.TITLE, 1f), false);
        g.text(font, "Bound to slot 1  ·  ready", x + 26, iy + 11, Glass.a(Glass.LABEL, 1f), false);
    }
}
