package dev.awaken.above.client;

import dev.awaken.above.AboveSounds;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.net.Panel;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

/**
 * Every Precursor machine's screen, in the AWAKEN glass style: title, power line, rows. Rows can show parts as item
 * icons with "have / need" counts and one button. The server builds the content ({@link Panel}).
 */
public final class PanelScreen extends Screen {
    private static final int ROW_GAP = 4, BUTTON_W = 78, BUTTON_H = 14, PART_W = 44;

    private Panel panel;
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int scroll, contentHeight;

    public PanelScreen(Panel panel) {
        super(panel.title());
        this.panel = panel;
    }

    public Panel panel() {
        return panel;
    }

    /** Refresh with new content from the server, keeping the scroll position. */
    public void update(Panel p) {
        this.panel = p;
    }

    @Override
    protected void init() {
        pw = Math.min(420, width - 24);
        ph = Math.min(320, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 12;
        cy = py + 40;
        cw = pw - 24;
        ch = ph - 40 - 22;
    }

    private int accent() {
        return panel.accent() == 0 ? Glass.EDGE : panel.accent();
    }

    private List<FormattedCharSequence> detailLines(Panel.Row r, int width) {
        if (r.detail().getString().isEmpty()) return List.of();
        List<FormattedCharSequence> lines = font.split(r.detail(), width);
        return lines.size() > 3 ? lines.subList(0, 3) : lines;
    }

    private int textWidth(Panel.Row r) {
        return cw - (r.hasButton() ? BUTTON_W + 8 : 0);
    }

    private int rowHeight(Panel.Row r) {
        if (r.header()) return 14;
        int h = 10 + detailLines(r, textWidth(r)).size() * 9;
        if (!r.parts().isEmpty()) h += 20;
        return Math.max(h, r.hasButton() ? BUTTON_H + 2 : 0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0x90050810, 0xC0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int accent = accent();
        Glass.panel(g, px, py, pw, ph, accent, 1f);
        g.text(font, panel.title(), px + 12, py + 10, Glass.a(Glass.TITLE, 1f), true);
        g.text(font, panel.subtitle(), px + 12, py + 22, Glass.a(Glass.LABEL, 1f), false);
        if (panel.essence() >= 0) {
            String power;
            int col;
            if (panel.reactors() <= 0) {
                power = panel.needsPower() ? "No reactor in range" : "";
                col = Glass.WARN;
            } else {
                power = String.format("◆ %,d essence · %d reactor%s", panel.essence(), panel.reactors(), panel.reactors() == 1 ? "" : "s");
                col = panel.essence() > 0 ? Glass.GOOD : Glass.WARN;
            }
            g.text(font, power, px + pw - 12 - font.width(power), py + 10, Glass.a(col, 1f), false);
        }
        g.horizontalLine(px + 8, px + pw - 8, py + 34, Glass.a(accent, 0.25f));

        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy - scroll;
        ItemStack hovered = ItemStack.EMPTY;
        for (Panel.Row r : panel.rows()) {
            int h = rowHeight(r);
            if (y + h >= cy && y <= cy + ch) {
                if (r.header()) {
                    g.text(font, r.label().getString().toUpperCase(), cx, y + 3, Glass.a(Glass.LANTERN, 0.95f), false);
                } else {
                    int col = r.colour() == 0 ? Glass.TEXT : r.colour();
                    g.text(font, r.label(), cx, y, Glass.a(col, 1f), false);
                    int ty = y + 10;
                    if (!r.parts().isEmpty()) {
                        for (int i = 0; i < r.parts().size(); i++) {
                            ItemStack s = r.parts().get(i);
                            int ix = cx + i * PART_W, iy = ty + 1;
                            g.fill(ix - 1, iy - 1, ix + 17, iy + 17, Glass.a(0x0A1020, 0.7f));
                            g.item(s, ix, iy);
                            int have = i < r.have().size() ? r.have().get(i) : 0;
                            String c = Math.min(have, 999) + "/" + s.getCount();
                            g.text(font, c, ix + 19, iy + 5, Glass.a(have >= s.getCount() ? Glass.GOOD : Glass.WARN, 1f), false);
                            if (Glass.inside(mouseX, mouseY, ix, iy, 16, 16) && mouseY >= cy && mouseY < cy + ch) hovered = s;
                        }
                        ty += 20;
                    }
                    for (FormattedCharSequence line : detailLines(r, textWidth(r))) {
                        g.text(font, line, cx, ty, Glass.a(Glass.LABEL, 1f), false);
                        ty += 9;
                    }
                    if (r.hasButton()) {
                        int bx = cx + cw - BUTTON_W, by = y;
                        boolean hover = Glass.inside(mouseX, mouseY, bx, by, BUTTON_W, BUTTON_H) && mouseY >= cy && mouseY < cy + ch;
                        Glass.button(g, font, r.button().getString(), bx, by, BUTTON_W, BUTTON_H, hover, r.enabled(), accent);
                    }
                }
            }
            y += h + ROW_GAP;
        }
        contentHeight = y + scroll - cy;
        g.disableScissor();
        if (contentHeight > ch) {
            int barH = Math.max(16, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * scroll / Math.max(1, contentHeight - ch);
            g.fill(px + pw - 6, barY, px + pw - 4, barY + barH, Glass.a(accent, 0.5f));
        }
        if (!panel.footer().getString().isEmpty()) {
            String f = font.plainSubstrByWidth(panel.footer().getString(), pw - 24);
            g.text(font, f, px + 12, py + ph - 16, Glass.a(Glass.LABEL, 0.8f), false);
        }
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 1 && Glass.inside(mx, my, cx, cy, cw, ch)) {
            int y = cy - scroll;
            for (Panel.Row r : panel.rows()) {
                int h = rowHeight(r);
                if (!r.header() && r.hasButton() && r.enabled() && Glass.inside(mx, my, cx + cw - BUTTON_W, y, BUTTON_W, BUTTON_H)) {
                    ClientPacketDistributor.sendToServer(new AboveNet.PanelAction(panel.pos(), r.action(), r.index()));
                    if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AboveSounds.UI_CLICK, 1.0f));
                    return true;
                }
                y += h + ROW_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - ch);
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static void open(Panel p) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean close = p.rows().isEmpty() && p.title().getString().isEmpty();
        if (mc.gui.screen() instanceof PanelScreen s && s.panel().pos().equals(p.pos())) {
            if (close) s.onClose();
            else s.update(p);
            return;
        }
        if (!close) mc.gui.setScreen(new PanelScreen(p));
    }

    @Override
    public Component getNarrationMessage() {
        return panel.title();
    }
}
