package dev.awaken.gates.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The Satchel: what you carry out of the gate if you live. Read-only (loot moves into it and out of it, never by hand);
 * it becomes yours at a Rest Shrine or when the Lord dies.
 */
public final class SatchelScreen extends Screen {
    private static final int GLASS = 0xE00F1724, EDGE = 0xFF7FC3FF, SLOT = 0xFF1B2638, SLOT_EDGE = 0xFF2C3B55;

    public SatchelScreen() {
        super(Component.translatable("screen.awaken.satchel"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        List<ItemStack> items = ClientGates.satchel;
        int cols = 9, rows = 3, cell = 20;
        int w = cols * cell + 16, h = rows * cell + 64;
        int x0 = width / 2 - w / 2, y0 = height / 2 - h / 2;
        g.fill(x0, y0, x0 + w, y0 + h, GLASS);
        g.outline(x0, y0, w, h, EDGE);
        g.text(font, title, x0 + 8, y0 + 7, 0xFFFFFFFF, false);
        String xp = ClientGates.satchelXp + " Satchel XP";
        g.text(font, xp, x0 + w - 8 - font.width(xp), y0 + 7, 0xFFFFCF7A, false);
        int gx = x0 + 8, gy = y0 + 22;
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < cols * rows; i++) {
            int cx = gx + (i % cols) * cell, cy = gy + (i / cols) * cell;
            g.fill(cx, cy, cx + cell - 2, cy + cell - 2, SLOT);
            g.outline(cx, cy, cell - 2, cell - 2, SLOT_EDGE);
            if (i < items.size()) {
                ItemStack s = items.get(i);
                g.item(s, cx + 1, cy + 1);
                g.itemDecorations(font, s, cx + 1, cy + 1);
                if (mouseX >= cx && mouseX < cx + cell - 2 && mouseY >= cy && mouseY < cy + cell - 2) hovered = s;
            }
        }
        int ty = gy + rows * cell + 6;
        g.textWithWordWrap(font, Component.translatable("screen.awaken.satchel.hint"), x0 + 8, ty, w - 16, 0xFFB9C7D9);
        if (!hovered.isEmpty()) g.setTooltipForNextFrame(font, hovered.getHoverName(), mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
