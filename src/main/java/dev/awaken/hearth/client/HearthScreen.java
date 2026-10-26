package dev.awaken.hearth.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "Your Hearth": opens when you use your own Anchor. This class is the chrome (the glass frame with a lantern edge,
 * the header with the tier and the Anchor's position, scrolling and clipping) and hosts the content, which lives in
 * {@link GuideSections} as Ui sections: where you stand, Oren's offer, ROOMS (a card per room: what it gives, a
 * checklist with icons ticked from your radius, the furniture's recipe, the rule, why it isn't recognised), TOWN and
 * NEXT UPGRADE ([Y] or the button builds it). Fits the owner's smallest auto GUI scale (378 × 246). Quiet: toasts wait.
 */
final class HearthScreen extends Screen implements QuietScreen {
    private static final int MAX_W = 520, MAX_H = 340, HEAD = 30, SCROLLBAR = 6, SECTION_GAP = 8;

    private GuideData data;
    private final List<UiSection> sections = GuideSections.all(() -> this.data);
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int scroll, contentHeight;

    HearthScreen(GuideData data) {
        super(Component.translatable("screen.awaken.hearth"));
        this.data = data;
    }

    void update(GuideData d) {
        this.data = d;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 10;
        cy = py + HEAD + 4;
        cw = pw - 20 - SCROLLBAR;
        ch = ph - HEAD - 4 - 8;
    }

    @Override
    public void removed() {
        GuideClient.closed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- chrome -----------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xA0050810, 0xC8050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Glass.panel(g, px, py, pw, ph, Glass.LANTERN, 1f);
        String title = data.home().name().isEmpty() ? "YOUR HEARTH" : data.home().name().toUpperCase(java.util.Locale.ROOT);
        g.text(font, title, px + 12, py + 9, Glass.a(Glass.LANTERN, 1f), false);
        int chipX = px + 12 + font.width(title) + 6;
        int chipW = Glass.chip(g, font, data.tier() <= 0 ? "HEARTHFIRE" : "TIER " + GuideSections.roman(data.tier()), chipX, py + 7, Glass.AMBER);
        String close = "Esc to close";
        int closeX = px + pw - 12 - font.width(close);
        g.text(font, close, closeX, py + 9, Glass.a(Glass.LABEL, 0.7f), false);
        BlockPos anchor = data.anchor();
        String where = anchor.getX() + " " + anchor.getY() + " " + anchor.getZ();
        int whereX = chipX + chipW + 6;
        if (whereX + font.width(where) + 8 < closeX) g.text(font, where, whereX, py + 9, Glass.a(Glass.LABEL, 0.8f), false);
        g.horizontalLine(px + 8, px + pw - 8, py + HEAD - 6, Glass.a(Glass.LANTERN, 0.25f));

        // the content: each section under its title, clipped to the panel, scrolled
        boolean over = mouseX >= cx && mouseY >= cy && mouseX < cx + cw && mouseY < cy + ch;
        int mx = over ? mouseX : -10000, my = over ? mouseY : -10000;
        g.enableScissor(cx - 2, cy, cx + cw + SCROLLBAR, cy + ch);
        int y = cy - scroll;
        for (UiSection s : sections) {
            if (!s.visible()) continue;
            if (!s.title().isEmpty()) {
                Ui t = Ui.draw(g, font, cx, y + 4, cw, mx, my);
                t.text(s.title(), Glass.a(Glass.LANTERN, 0.9f));
            }
            y += titleHeight(s);
            int h = s.height(cw);
            if (y + h >= cy && y <= cy + ch) s.render(g, cx, y, cw, mx, my, a);
            y += h + SECTION_GAP;
        }
        contentHeight = y + scroll - cy;
        g.disableScissor();
        scroll = Math.max(0, Math.min(scroll, contentHeight - ch));
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * scroll / Math.max(1, contentHeight - ch);
            g.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, Glass.a(Glass.LANTERN, 0.5f));
        }
    }

    private static int titleHeight(UiSection s) {
        return s.title().isEmpty() ? 0 : Ui.LINE + 5;
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x(), ey = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && ex >= cx && ey >= cy && ex < cx + cw && ey < cy + ch) {
            int y = cy - scroll;
            for (UiSection s : sections) {
                if (!s.visible()) continue;
                y += titleHeight(s);
                int h = s.height(cw);
                if (ey >= y && ey < y + h && s.click(ex, ey, cx, y, cw)) return true;
                y += h + SECTION_GAP;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, contentHeight - ch), scroll - scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.ACCEPT.matches(event) && data.upgrade().affordable()) {
            GuideClient.send(Guide.Act.UPGRADE);
            return true;
        }
        return super.keyPressed(event);
    }
}
