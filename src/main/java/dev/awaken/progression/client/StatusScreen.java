package dev.awaken.progression.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Status screen (K). Four tabs and never more: Status, Truths, Codex, Gate Map. Each tab is a scrolling stack of
 * {@link StatusSections.Section}s contributed by modules.
 */
public final class StatusScreen extends Screen {
    private static StatusSections.Tab lastTab = StatusSections.Tab.STATUS;
    private static final int[] SCROLL = new int[StatusSections.Tab.values().length];

    private StatusSections.Tab tab = lastTab;
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int contentHeight;

    public StatusScreen() {
        super(Component.translatable("screen.awaken.status"));
    }

    @Override
    protected void init() {
        pw = Math.min(560, width - 24);
        ph = Math.min(360, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 104;
        cy = py + 12;
        cw = pw - 116;
        ch = ph - 24;
    }

    private static String label(StatusSections.Tab t) {
        return switch (t) {
            case STATUS -> "Status";
            case TRUTHS -> "Truths";
            case CODEX -> "Codex";
            case GATE_MAP -> "Gate Map";
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xB0050810, 0xD0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Glass.panel(g, px, py, pw, ph, Glass.EDGE, 1f);

        // tabs
        g.text(font, "◇ SIGNAL", px + 12, py + 12, Glass.a(Glass.EDGE, 1f), false);
        int ty = py + 32;
        for (StatusSections.Tab t : StatusSections.Tab.values()) {
            boolean on = t == tab;
            boolean hover = Glass.inside(mouseX, mouseY, px + 8, ty, 86, 18);
            if (on) g.fill(px + 8, ty, px + 94, ty + 18, Glass.a(Glass.EDGE, 0.16f));
            else if (hover) g.fill(px + 8, ty, px + 94, ty + 18, Glass.a(Glass.EDGE, 0.07f));
            if (on) g.verticalLine(px + 8, ty, ty + 17, Glass.a(Glass.EDGE, 1f));
            g.text(font, label(t), px + 16, ty + 5, Glass.a(on ? Glass.TITLE : Glass.LABEL, 1f), false);
            ty += 22;
        }
        g.verticalLine(px + 98, py + 8, py + ph - 8, Glass.a(Glass.EDGE, 0.18f));
        g.text(font, "K to close", px + 12, py + ph - 16, Glass.a(Glass.LABEL, 0.7f), false);

        // content
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy - SCROLL[tab.ordinal()];
        List<StatusSections.Section> sections = StatusSections.of(tab);
        boolean any = false;
        for (StatusSections.Section s : sections) {
            if (!s.visible()) continue;
            any = true;
            g.text(font, s.title(), cx, y, Glass.a(Glass.EDGE, 0.85f), false);
            y += 12;
            int h = s.height(cw);
            if (y + h >= cy && y <= cy + ch) s.render(g, cx, y, cw, mouseX, mouseY, a);
            y += h + 14;
        }
        if (!any) {
            String empty = switch (tab) {
                case CODEX -> "Nothing yet. What you hear, see and dream will gather here.";
                case GATE_MAP -> "No gates detected.";
                default -> "";
            };
            g.text(font, empty, cx, y, Glass.a(Glass.LABEL, 1f), false);
        }
        contentHeight = y + SCROLL[tab.ordinal()] - cy;
        g.disableScissor();

        // scrollbar
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * SCROLL[tab.ordinal()] / Math.max(1, contentHeight - ch);
            g.fill(px + pw - 6, barY, px + pw - 4, barY + barH, Glass.a(Glass.EDGE, 0.5f));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int ty = py + 32;
        for (StatusSections.Tab t : StatusSections.Tab.values()) {
            if (Glass.inside(mx, my, px + 8, ty, 86, 18)) {
                tab = t;
                lastTab = t;
                return true;
            }
            ty += 22;
        }
        if (event.button() == 1 && Glass.inside(mx, my, cx, cy, cw, ch)) {
            int y = cy - SCROLL[tab.ordinal()];
            for (StatusSections.Section s : StatusSections.of(tab)) {
                if (!s.visible()) continue;
                y += 12;
                int h = s.height(cw);
                if (my >= y && my < y + h && s.click(mx, my, cx, y, cw)) return true;
                y += h + 14;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - ch);
        int i = tab.ordinal();
        SCROLL[i] = (int) Math.max(0, Math.min(max, SCROLL[i] - scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.STATUS.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
