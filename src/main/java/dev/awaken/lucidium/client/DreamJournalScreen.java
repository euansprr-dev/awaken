package dev.awaken.lucidium.client;

import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlanner;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * The Dream Journal: choose tonight's intention, write down the dream you just woke from, and read back your dreams
 * and the places the Day Journal remembered.
 */
public final class DreamJournalScreen extends Screen {
    private static final int PAPER = 0xF2E8D8, INK = 0x3A2A4A, FADED = 0x8A7898, ROSE = 0xB0558F, SIGNAL = 0x3C86CC, COVER = 0x4A2357;

    private LucidiumNet.OpenJournal data;
    private int px, py, pw, ph;
    private int scroll;

    public DreamJournalScreen(LucidiumNet.OpenJournal data) {
        super(Component.translatable("screen.awaken.dream_journal"));
        this.data = data;
    }

    void refresh(LucidiumNet.OpenJournal next) {
        this.data = next;
    }

    @Override
    protected void init() {
        pw = Math.min(380, width - 20);
        ph = Math.min(236, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xA0100818, 0xC0100818);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        // a book opened flat: cover edge, two pages, a spine
        g.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, LucidityHud.a(COVER, 1f));
        g.fill(px, py, px + pw, py + ph, LucidityHud.a(PAPER, 1f));
        int mid = px + pw / 2;
        g.fill(mid - 1, py, mid + 1, py + ph, LucidityHud.a(0xD8C8B4, 1f));
        int lx = px + 12, rx = mid + 10, colW = pw / 2 - 22;

        // left page: intention and recall
        g.text(font, title, lx, py + 10, LucidityHud.a(INK, 1f), false);
        g.text(font, Component.translatable("screen.awaken.dream_journal.intention"), lx, py + 30, LucidityHud.a(FADED, 1f), false);
        int by = py + 44;
        for (String id : DreamPlanner.INTENTIONS) {
            boolean on = id.equals(data.intention());
            boolean hover = inside(mouseX, mouseY, lx, by, colW, 16);
            button(g, Component.translatable("screen.awaken.dream_journal." + id).getString(), lx, by, colW, 16, on, hover, ROSE);
            by += 20;
        }
        if (!data.intention().isEmpty()) {
            boolean hover = inside(mouseX, mouseY, lx, by, colW, 14);
            g.text(font, Component.translatable("screen.awaken.dream_journal.clear"), lx + 2, by + 3,
                    LucidityHud.a(hover ? INK : FADED, 1f), false);
        }
        int ry = py + ph - 62;
        g.fill(lx, ry - 6, lx + colW, ry - 5, LucidityHud.a(0xD8C8B4, 1f));
        if (data.canRecall()) {
            boolean hover = inside(mouseX, mouseY, lx, ry, colW, 18);
            button(g, Component.translatable("screen.awaken.dream_journal.recall").getString(), lx, ry, colW, 18, false, hover, SIGNAL);
        } else {
            List<FormattedCharSequence> lines = font.split(Component.translatable("screen.awaken.dream_journal.recall_none"), colW);
            int yy = ry;
            for (FormattedCharSequence l : lines) {
                g.text(font, l, lx, yy, LucidityHud.a(FADED, 1f), false);
                yy += 9;
            }
        }
        g.text(font, Component.translatable("screen.awaken.dream_journal.recall_count", data.recall()), lx, py + ph - 18,
                LucidityHud.a(SIGNAL, 1f), false);

        // right page: dreams, then remembered places
        g.enableScissor(rx - 2, py + 6, rx + colW + 4, py + ph - 6);
        int y = py + 10 - scroll;
        g.text(font, Component.translatable("screen.awaken.dream_journal.dreams"), rx, y, LucidityHud.a(ROSE, 1f), false);
        y += 13;
        if (data.dreams().isEmpty()) {
            g.text(font, Component.translatable("screen.awaken.dream_journal.empty"), rx, y, LucidityHud.a(FADED, 1f), false);
            y += 12;
        }
        for (String entry : data.dreams()) {
            String[] parts = entry.split("\\|", 2);
            g.text(font, parts[0], rx, y, LucidityHud.a(FADED, 1f), false);
            y += 10;
            if (parts.length > 1) {
                for (FormattedCharSequence l : font.split(Component.literal(parts[1]), colW)) {
                    g.text(font, l, rx, y, LucidityHud.a(INK, 1f), false);
                    y += 9;
                }
            }
            y += 5;
        }
        y += 4;
        g.text(font, Component.translatable("screen.awaken.dream_journal.places"), rx, y, LucidityHud.a(ROSE, 1f), false);
        y += 13;
        for (String entry : data.snapshots()) {
            String[] parts = entry.split("\\|", 2);
            String line = parts.length > 1 ? parts[1] : parts[0];
            for (FormattedCharSequence l : font.split(Component.literal("· " + line), colW)) {
                g.text(font, l, rx, y, LucidityHud.a(INK, 0.85f), false);
                y += 9;
            }
        }
        maxScroll = Math.max(0, y + scroll - (py + ph - 10));
        g.disableScissor();
    }

    private int maxScroll;

    private void button(GuiGraphicsExtractor g, String label, int x, int y, int w, int h, boolean on, boolean hover, int accent) {
        g.fill(x, y, x + w, y + h, LucidityHud.a(accent, on ? 0.30f : hover ? 0.16f : 0.06f));
        g.outline(x, y, w, h, LucidityHud.a(accent, on || hover ? 0.95f : 0.45f));
        int tw = font.width(label);
        g.text(font, label, x + (w - tw) / 2, y + (h - 8) / 2, LucidityHud.a(on ? accent : INK, 1f), false);
        if (on) g.text(font, "✦", x + 4, y + (h - 8) / 2, LucidityHud.a(accent, 1f), false);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int lx = px + 12, colW = pw / 2 - 22;
        int by = py + 44;
        for (String id : DreamPlanner.INTENTIONS) {
            if (inside(mx, my, lx, by, colW, 16)) {
                ClientPacketDistributor.sendToServer(new LucidiumNet.JournalAction("intent", id));
                return true;
            }
            by += 20;
        }
        if (!data.intention().isEmpty() && inside(mx, my, lx, by, colW, 14)) {
            ClientPacketDistributor.sendToServer(new LucidiumNet.JournalAction("intent", ""));
            return true;
        }
        if (data.canRecall() && inside(mx, my, lx, py + ph - 62, colW, 18)) {
            ClientPacketDistributor.sendToServer(new LucidiumNet.JournalAction("recall", ""));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - scrollY * 14));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
