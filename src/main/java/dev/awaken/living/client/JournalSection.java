package dev.awaken.living.client;

import dev.awaken.living.LivingNet;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The Journal (SPEC §21.0.7), a section of the K menu's Status tab until the holo tab exists: only the chains the player
 * found and took (the tracked one first) and the bounties they took. Nothing undiscovered is ever listed.
 */
final class JournalSection implements StatusSections.Section {
    static List<LivingNet.JournalEntry> entries = List.of();
    static final int ROW = 22;

    static void register() {
        StatusSections.add(StatusSections.Tab.JOURNAL, new JournalSection());
    }

    @Override
    public String title() {
        return "JOURNAL · CHAINS"; // beside the System's filed finds ("JOURNAL"): one Journal, two headings (SPEC §27.8)
    }

    @Override
    public int order() {
        return -20; // the Journal page: what you're doing out there, under NOW
    }

    @Override
    public boolean visible() {
        return LivingClient.shown && !entries.isEmpty();
    }

    @Override
    public int height(int width) {
        return entries.size() * ROW + 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int row = y;
        for (LivingNet.JournalEntry e : entries) {
            int accent = e.bounty() ? 0xE7C27A : e.tracked() ? 0x9FE6D6 : 0x7FC3FF;
            g.fill(x, row + 1, x + 2, row + ROW - 4, 0xFF000000 | accent);
            String title = (e.bounty() ? "BOUNTY · " : "") + e.title() + (e.tracked() ? "  · TRACKED" : "");
            HudLook.text(g, font, font.plainSubstrByWidth(title, width - 8), x + 7, row + 1, 0xFF000000 | 0xEAF7FF, false);
            HudLook.text(g, font, font.plainSubstrByWidth(e.line(), width - 8), x + 7, row + 11, 0xFF8FA7C4, false);
            row += ROW;
        }
    }
}
