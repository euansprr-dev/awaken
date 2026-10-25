package dev.awaken.living.client;

import dev.awaken.progression.Progression;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Almanac (SPEC §21.6): the happenings a player has witnessed, in the Codex tab, counted the Codex's way
 * ("HAPPENINGS WITNESSED · 4 / ??"). Only what you've seen is named.
 */
final class AlmanacSection implements StatusSections.Section {
    static final String PREFIX = "awaken:discovered/happening/";
    static final int ROW = 12;

    static void register() {
        StatusSections.add(StatusSections.Tab.CODEX, new AlmanacSection());
    }

    private static List<String> seen() {
        var mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (var e : Progression.profile(mc.player).counters().entrySet()) {
            if (e.getValue() > 0 && e.getKey().startsWith(PREFIX)) out.add(e.getKey().substring(PREFIX.length()));
        }
        out.sort(null);
        return out;
    }

    @Override
    public String title() {
        return "HAPPENINGS WITNESSED · " + seen().size() + " / ??";
    }

    @Override
    public int order() {
        return 460;
    }

    @Override
    public boolean visible() {
        return LivingClient.shown && !seen().isEmpty();
    }

    @Override
    public int height(int width) {
        int n = seen().size();
        return ((n + 1) / 2) * ROW + 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        List<String> list = seen();
        int col = width / 2;
        for (int i = 0; i < list.size(); i++) {
            String key = "happening.awaken." + list.get(i);
            String name = net.minecraft.network.chat.Component.translatableWithFallback(key, list.get(i).replace('_', ' ')).getString();
            int cx = x + (i % 2) * col, cy = y + (i / 2) * ROW;
            HudLook.text(g, font, "◇ " + font.plainSubstrByWidth(name, col - 12), cx, cy, 0xFFD9E4F2, false);
        }
    }
}
