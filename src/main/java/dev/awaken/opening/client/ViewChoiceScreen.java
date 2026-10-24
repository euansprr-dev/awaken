package dev.awaken.opening.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/**
 * Into your eyes (SPEC §16.4): at the end of the first sky shot, two cards ask how you'll see Carillon, first person or
 * third, over the landing behind your shoulder. The choice is remembered; the View key (Toggle Perspective, F5) flips
 * between the two at any time and skips the front view. Click a card or press 1 / 2; Esc keeps first person.
 */
final class ViewChoiceScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final int BRONZE = 0xFFC9A46A, BRONZE_DIM = 0x80C9A46A, INK = 0xE80C1018, INK_ON = 0xF0141B26,
            TITLE = 0xFFF2EBDD, LABEL = 0xFFB9B2A4, DIM = 0xFF7D776C;

    private final Consumer<Boolean> chosen;
    private int hovered = -1, age;
    private boolean done;

    ViewChoiceScreen(Consumer<Boolean> chosen) {
        super(Component.literal("How will you see Carillon?"));
        this.chosen = chosen;
    }

    private int cardW() {
        return Math.min(150, (width - 48) / 2);
    }

    private int cardH() {
        return 64;
    }

    private int cardX(int i) {
        int w = cardW();
        return width / 2 + (i == 0 ? -w - 8 : 8);
    }

    private int cardY() {
        return (int) (height * 0.62);
    }

    private int cardAt(double x, double y) {
        for (int i = 0; i < 2; i++) {
            if (x >= cardX(i) && x < cardX(i) + cardW() && y >= cardY() && y < cardY() + cardH()) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // the landing stays in view: only the lower half darkens under the cards
        g.fillGradient(0, height / 2, width, height, 0x00050608, 0xB0050608);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        float in = Mth.clamp((age + a) / 10F, 0, 1);
        int alpha = (int) (in * 255);
        hovered = cardAt(mouseX, mouseY);
        int ty = cardY() - 30;
        g.centeredText(font, Component.literal("HOW WILL YOU SEE CARILLON?"), width / 2, ty, withAlpha(TITLE, alpha));
        g.centeredText(font, Component.literal("You can change it any time with the View key ("
                + minecraft.options.keyTogglePerspective.getTranslatedKeyMessage().getString() + ")."), width / 2, ty + 12, withAlpha(LABEL, alpha));
        card(g, 0, "IN YOUR EYES", "First person", "1", alpha);
        card(g, 1, "OVER YOUR SHOULDER", "Third person", "2", alpha);
    }

    private void card(GuiGraphicsExtractor g, int i, String big, String small, String key, int alpha) {
        int x = cardX(i), y = cardY(), w = cardW(), h = cardH();
        boolean on = hovered == i;
        g.fill(x, y, x + w, y + h, withAlpha(on ? INK_ON : INK, Math.min(alpha, on ? 240 : 232)));
        int edge = on ? BRONZE : BRONZE_DIM;
        g.fill(x, y, x + w, y + 1, withAlpha(edge, alpha));
        g.fill(x, y + h - 1, x + w, y + h, withAlpha(edge, alpha));
        g.fill(x, y, x + 1, y + h, withAlpha(edge, alpha / 2));
        g.fill(x + w - 1, y, x + w, y + h, withAlpha(edge, alpha / 2));
        // a small mark: an eye (first) or a figure seen from behind (third)
        int cx = x + w / 2, cy = y + 18;
        if (i == 0) {
            g.fill(cx - 7, cy - 1, cx + 7, cy + 1, withAlpha(BRONZE, alpha));
            g.fill(cx - 2, cy - 3, cx + 2, cy + 3, withAlpha(BRONZE, alpha));
        } else {
            g.fill(cx - 2, cy - 6, cx + 2, cy - 2, withAlpha(BRONZE, alpha));
            g.fill(cx - 4, cy - 1, cx + 4, cy + 6, withAlpha(BRONZE, alpha));
        }
        g.centeredText(font, Component.literal(big), cx, y + 32, withAlpha(on ? TITLE : LABEL, alpha));
        g.centeredText(font, Component.literal(small), cx, y + 44, withAlpha(DIM, alpha));
        g.text(font, key, x + 5, y + 4, withAlpha(on ? BRONZE : DIM, alpha), false);
    }

    private static int withAlpha(int argb, int alpha) {
        int a = (argb >>> 24) * Math.clamp(alpha, 0, 255) / 255;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int i = cardAt(event.x(), event.y());
            if (i >= 0) {
                pick(i == 0);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == InputConstants.KEY_1 || k == InputConstants.KEY_2) {
            pick(k == InputConstants.KEY_1);
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(boolean first) {
        if (done) return;
        done = true;
        chosen.accept(first);
        onClose();
    }

    @Override
    public void onClose() {
        if (!done) {
            done = true;
            chosen.accept(true);
        }
        super.onClose();
    }

    @Override
    public void tick() {
        age++;
        // tours answer for themselves (first person) once the cards have been seen and shot
        if (AUTOPILOT && age == 30) SkyShot.shot("m2_sky_g_question");
        if (AUTOPILOT && age >= 50) pick(true);
    }

    private static final boolean AUTOPILOT = System.getProperty("awaken.autopilot") != null;

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
