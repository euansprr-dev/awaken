package dev.awaken.living.client;

import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The crafts in the K menu's Status tab (SPEC §21.9): each craft's level, a bar to the next, and its next perk; the
 * perks it has open, by name, under it.
 */
final class CraftsSection implements StatusSections.Section {
    static final int ROW = 31;

    static void register() {
        StatusSections.add(StatusSections.Tab.POWERS, new CraftsSection());
    }

    @Override
    public String title() {
        return "CRAFTS";
    }

    @Override
    public int order() {
        return 160;
    }

    @Override
    public boolean visible() {
        return LivingClient.shown && Minecraft.getInstance().player != null;
    }

    @Override
    public int height(int width) {
        return Craft.values().length * ROW + 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Font font = Minecraft.getInstance().font;
        var s = Crafts.state(p);
        int row = y;
        for (Craft c : Craft.values()) {
            int lv = s.level(c);
            String head = c.title + "  Lv " + lv;
            HudLook.text(g, font, head, x, row + 1, 0xFF000000 | c.colour, false);
            // the bar to the next level
            int bx = x + 92, bw = Math.max(40, width - 92 - 4);
            double into = Craft.intoLevel(s.xp(c)), need = Craft.toNext(lv);
            float k = lv >= Craft.MAX_LEVEL ? 1 : (float) Math.min(1, into / need);
            g.fill(bx, row + 4, bx + bw, row + 7, 0x66101820);
            g.fill(bx, row + 4, bx + (int) (bw * k), row + 7, 0xDD000000 | c.colour);
            // the next perk, or what it has
            Craft.Perk next = null;
            StringBuilder open = new StringBuilder();
            for (Craft.Perk perk : c.perks) {
                if (lv >= perk.level()) open.append(open.isEmpty() ? "" : " · ").append(perk.title());
                else if (next == null) next = perk;
            }
            // what it has on one line (cut with a mark if it runs long), the next perk on its own below
            String has = open.isEmpty() ? "no perks yet" : open.toString();
            if (font.width(has) > width - 4) has = font.plainSubstrByWidth(has, width - 4 - font.width("…")) + "…";
            HudLook.text(g, font, has, x + 4, row + 11, 0xFF8FA7C4, false);
            if (next != null) HudLook.text(g, font, "next: " + next.title() + " at " + next.level(), x + 4, row + 20, 0xFF5E7590, false);
            row += ROW;
        }
    }
}
