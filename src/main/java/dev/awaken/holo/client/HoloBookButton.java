package dev.awaken.holo.client;

import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;

/**
 * The recipe book's button on the holo inventory: a glass key with an open book traced in light, lit under the cursor.
 * Vanilla's own button stays the one that is pressed (it toggles the book, moves the inventory and itself), and this
 * key follows it wherever it goes, so the recipe book behaves exactly as before.
 */
final class HoloBookButton extends Button {
    private final Button vanilla;
    private final HoloInventoryScreen screen;

    HoloBookButton(Button vanilla, HoloInventoryScreen screen) {
        super(vanilla.getX(), vanilla.getY(), vanilla.getWidth(), vanilla.getHeight(), vanilla.getMessage(), b -> {}, DEFAULT_NARRATION);
        this.vanilla = vanilla;
        this.screen = screen;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        vanilla.onPress(input);
        setPosition(vanilla.getX(), vanilla.getY());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        setPosition(vanilla.getX(), vanilla.getY());
        float k = screen.faceAlpha();
        if (k <= 0.01f) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean lit = isHoveredOrFocused();
        if (lit) HoloGuiLook.INSTANCE.glow(g, x, y, w, h, Glass.EDGE, 0.5f * k);
        UiCanvas c = GuiCanvas.of(g);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, Glass.a(0x040A16, 0.58f * k));
        c.outline(x, y, w, h, Glass.a(Glass.EDGE, (lit ? 0.80f : 0.32f) * k));
        // an open book: two pages meeting at the spine, two lines of text on each
        int line = Glass.a(0xCFEAFF, (lit ? 1f : 0.85f) * k), text = Glass.a(Glass.EDGE, 0.60f * k);
        int px = x + (w - 12) / 2, py = y + (h - 9) / 2;
        c.outline(px, py, 6, 9, line);
        c.outline(px + 6, py, 6, 9, line);
        for (int i = 0; i < 2; i++) {
            c.hLine(px + 2, px + 3, py + 3 + 2 * i, text);
            c.hLine(px + 8, px + 9, py + 3 + 2 * i, text);
        }
    }
}
