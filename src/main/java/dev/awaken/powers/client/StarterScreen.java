package dev.awaken.powers.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Starter;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Choose your first power": three cards side by side (icon, name in the family colour, family, one line of what it
 * does). Click a card or press 1 / 2 / 3. Esc closes it for now; the Now tracker and the next login bring it back.
 * Sized to fit the smallest auto GUI scale (378 × 246 GUI px) and doesn't pause the game. With the System's projected
 * look ({@link HudLook}, the holo module) the panel and its three cards are projected glass that scan in, the hovered
 * card lit in its family colour; layout, clicks and keys are the same.
 */
final class StarterScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299;
    private static final int CARD_H = 104;

    private final List<PowerType> cards = new ArrayList<>();
    private int px, py, pw, ph, cw;
    private int hovered = -1;
    private @Nullable String picked;
    private int pickedAge;
    private long openedAt;

    StarterScreen(List<String> ids) {
        super(Component.translatable("screen.awaken.first_power"));
        for (String id : ids) {
            PowerType t = PowerRegistry.get(id);
            if (t != null) cards.add(t);
        }
    }

    @Override
    protected void init() {
        pw = Math.min(372, width - 16);
        ph = Math.min(196, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cw = (pw - 16 - 2 * 6) / Math.max(1, cards.size());
        if (openedAt == 0) openedAt = System.nanoTime();
    }

    private int cardX(int i) {
        return px + 8 + i * (cw + 6);
    }

    private int cardY() {
        return py + 30;
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < cards.size(); i++) {
            int x = cardX(i), y = cardY();
            if (mx >= x && mx < x + cw && my >= y && my < y + CARD_H) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (HudLook.holo()) g.fillGradient(0, 0, width, height, 0x50050810, 0x78050810); // light: the world shows through
        else g.fillGradient(0, 0, width, height, 0x90050810, 0xB0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (HudLook.holo()) {
            projected(g, mouseX, mouseY);
            return;
        }
        g.fill(px, py, px + pw, py + ph, 0xE80F1724);
        g.outline(px, py, pw, ph, 0xB07FC3FF);
        g.centeredText(font, title, px + pw / 2, py + 8, TITLE);
        g.centeredText(font, Component.literal("The Signal offers three. Take one now; more come later."), px + pw / 2, py + 18, LABEL);

        hovered = picked == null ? cardAt(mouseX, mouseY) : -1;
        for (int i = 0; i < cards.size(); i++) drawCard(g, i, cards.get(i));

        // the hovered card's whole description, under the cards
        int dy = cardY() + CARD_H + 6;
        PowerType focus = hovered >= 0 ? cards.get(hovered) : null;
        if (picked != null) {
            g.centeredText(font, Component.literal("Waking it up…"), px + pw / 2, dy + 4, LANTERN);
        } else if (focus != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(focus.description), pw - 20);
            for (int l = 0; l < Math.min(3, lines.size()); l++) g.text(font, lines.get(l), px + 10, dy + l * 10, LABEL, false);
        }
        g.centeredText(font, Component.literal("Click a card or press 1 · 2 · 3     Esc: later"), px + pw / 2, py + ph - 12, DIM);
    }

    /** The projected look: the panel scans in, then the cards one after another; the hovered one lights up. */
    private void projected(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        float ms = (System.nanoTime() - openedAt) / 1e6f;
        float reveal = Math.min(1f, ms / 320f);
        HudLook.card(g, px, py, pw, ph, EDGE, 1f, reveal, 0f);
        g.enableScissor(px, py, px + pw, py + (int) Math.ceil(ph * (1 - Math.pow(1 - reveal, 3))));
        HudLook.centered(g, font, title.getString(), px + pw / 2, py + 8, TITLE, true);
        HudLook.centered(g, font, "The Signal offers three. Take one now; more come later.", px + pw / 2, py + 18, LABEL, true);
        hovered = picked == null ? cardAt(mouseX, mouseY) : -1;
        for (int i = 0; i < cards.size(); i++) {
            float r = Math.min(1f, Math.max(0f, (ms - 160f - i * 70f) / 260f));
            if (r > 0) projectedCard(g, i, cards.get(i), r);
        }
        int dy = cardY() + CARD_H + 6;
        PowerType focus = hovered >= 0 ? cards.get(hovered) : null;
        if (picked != null) {
            HudLook.centered(g, font, "Waking it up…", px + pw / 2, dy + 4, LANTERN, true);
        } else if (focus != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(focus.description), pw - 20);
            for (int l = 0; l < Math.min(3, lines.size()); l++) HudLook.text(g, font, lines.get(l), px + 10, dy + l * 10, LABEL, false);
        }
        HudLook.centered(g, font, "Click a card or press 1 · 2 · 3     Esc: later", px + pw / 2, py + ph - 12, DIM, true);
        g.disableScissor();
    }

    private void projectedCard(GuiGraphicsExtractor g, int i, PowerType t, float reveal) {
        int x = cardX(i), y = cardY();
        boolean on = i == hovered || t.id.toString().equals(picked);
        int family = 0xFF000000 | t.family.colour;
        HudLook.card(g, x, y, cw, CARD_H, on ? family : 0x5A7090, 1f, reveal, 0f);
        if (on) HudLook.glow(g, x, y, cw, CARD_H, family, 1f);
        if (reveal < 0.6f) return; // the words come once the scan has passed them
        HudLook.text(g, font, String.valueOf(i + 1), x + 4, y + 4, on ? LANTERN : DIM, false);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), x + cw / 2 - 12, y + 8, 24, 24);
        HudLook.centered(g, font, t.name, x + cw / 2, y + 38, family, true);
        HudLook.centered(g, font, t.family.label, x + cw / 2, y + 49, DIM, true);
        String blurb = Starter.BLURB.getOrDefault(t.id.toString(), "");
        List<FormattedCharSequence> lines = font.split(Component.literal(blurb), cw - 10);
        for (int l = 0; l < Math.min(4, lines.size()); l++) {
            FormattedCharSequence line = lines.get(l);
            HudLook.text(g, font, line, x + (cw - font.width(line)) / 2, y + 63 + l * 10, TITLE, false);
        }
    }

    private void drawCard(GuiGraphicsExtractor g, int i, PowerType t) {
        int x = cardX(i), y = cardY();
        boolean on = i == hovered || t.id.toString().equals(picked);
        int family = 0xFF000000 | t.family.colour;
        g.fill(x, y, x + cw, y + CARD_H, on ? 0xF01A2436 : 0xE0101622);
        g.outline(x, y, cw, CARD_H, on ? family : 0xFF3A4A60);
        g.text(font, String.valueOf(i + 1), x + 4, y + 4, on ? LANTERN : DIM, false);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PowerHud.icon(t), x + cw / 2 - 12, y + 8, 24, 24);
        g.centeredText(font, Component.literal(t.name), x + cw / 2, y + 38, family);
        g.centeredText(font, Component.literal(t.family.label), x + cw / 2, y + 49, DIM);
        String blurb = Starter.BLURB.getOrDefault(t.id.toString(), "");
        List<FormattedCharSequence> lines = font.split(Component.literal(blurb), cw - 10);
        for (int l = 0; l < Math.min(4, lines.size()); l++) {
            FormattedCharSequence line = lines.get(l);
            g.text(font, line, x + (cw - font.width(line)) / 2, y + 63 + l * 10, TITLE, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && picked == null) {
            int i = cardAt(event.x(), event.y());
            if (i >= 0) {
                pick(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        int i = k == InputConstants.KEY_1 ? 0 : k == InputConstants.KEY_2 ? 1 : k == InputConstants.KEY_3 ? 2 : -1;
        if (i >= 0 && i < cards.size() && picked == null) {
            pick(i);
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(int i) {
        picked = cards.get(i).id.toString();
        StarterClient.choose(picked);
    }

    @Override
    public void tick() {
        // the server answers with Done (which closes this); if it never does, don't hang here
        if (picked != null && ++pickedAge > 60) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
