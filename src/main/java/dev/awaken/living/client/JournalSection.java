package dev.awaken.living.client;

import dev.awaken.living.LivingNet;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The Journal (SPEC §21.0.7), the Journal page's lead (SPEC §30.6): only the chains the player found and took and the
 * bounties they took; nothing undiscovered is ever listed. The tracked chain is the page's hero (TRACKING: its name
 * large, the step you're on); the rest follow under ALSO ON, one line each.
 */
final class JournalSection implements StatusSections.Section {
    static List<LivingNet.JournalEntry> entries = List.of();
    static final int HERO = 34, ROW = 20, ALSO = 12;

    static void register() {
        StatusSections.add(StatusSections.Tab.JOURNAL, new JournalSection());
    }

    private static LivingNet.JournalEntry tracked() {
        for (LivingNet.JournalEntry e : entries) if (e.tracked()) return e;
        return null;
    }

    @Override
    public String title() {
        return tracked() != null ? "TRACKING" : "CHAINS"; // beside the System's filed finds (LOG): one Journal (SPEC §27.8)
    }

    @Override
    public int order() {
        return -40; // the Journal page's lead: what you're doing out there, then NOW
    }

    @Override
    public boolean visible() {
        return LivingClient.shown && !entries.isEmpty();
    }

    @Override
    public int height(int width) {
        boolean hero = tracked() != null;
        int rest = entries.size() - (hero ? 1 : 0);
        return (hero ? HERO : 0) + (hero && rest > 0 ? ALSO : 0) + rest * ROW + 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int row = y;
        LivingNet.JournalEntry hero = tracked();
        if (hero != null) {
            // the hero: a lit bar, the chain's name, and the step you're on
            g.fill(x, row, x + width, row + HERO - 4, Glass.a(Glass.HORIZON, 0.07f));
            g.fill(x, row, x + 2, row + HERO - 4, 0xFF000000 | Glass.HORIZON);
            HudLook.text(g, font, font.plainSubstrByWidth(hero.title(), width - 10), x + 7, row + 3, 0xFFFFFFFF, false);
            String step = "▸ " + hero.line();
            HudLook.text(g, font, font.plainSubstrByWidth(step, width - 10), x + 7, row + 16, 0xFF000000 | Glass.TEXT, false);
            row += HERO;
            if (entries.size() > 1) {
                float m = StatusSections.micro();
                g.pose().pushMatrix();
                g.pose().translate(x, row + 1);
                g.pose().scale(m, m);
                g.text(font, "ALSO ON", 0, 0, 0xFF000000 | Glass.LABEL, false);
                g.pose().popMatrix();
                row += ALSO;
            }
        }
        for (LivingNet.JournalEntry e : entries) {
            if (e == hero) continue;
            int accent = e.bounty() ? 0xE7C27A : 0x7FC3FF;
            g.fill(x, row + 1, x + 2, row + ROW - 3, 0xFF000000 | accent);
            String title = (e.bounty() ? "BOUNTY · " : "") + e.title();
            HudLook.text(g, font, font.plainSubstrByWidth(title, width - 8), x + 7, row + 1, 0xFF000000 | 0xEAF7FF, false);
            float s = StatusSections.small();
            g.pose().pushMatrix();
            g.pose().translate(x + 7, row + 11);
            g.pose().scale(s, s);
            g.text(font, font.plainSubstrByWidth(e.line(), (int) ((width - 8) / s)), 0, 0, 0xFF000000 | Glass.LABEL, false);
            g.pose().popMatrix();
            row += ROW;
        }
    }
}
