package dev.awaken.anims.client.glass;

import dev.awaken.core.api.UiHooks;
import dev.awaken.holo.client.HoloGlass;
import dev.awaken.holo.ui.InventoryWells;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * A vanilla container screen in the band's glass (SPEC §65.9), the look storage gave chests (§56): the same panel, a
 * well under every slot where vanilla puts it, a light under the slot the cursor is on, the labels in the glass's own
 * colours, a fade in over 120 ms. Only the drawing: the screen underneath is vanilla's own class, so every click, key
 * and other mod behaves as it always has.
 */
final class GlassLook {
    private GlassLook() {}

    private static final int WELL = 0x040A16;

    static float alpha(long opened) {
        return Math.min(1f, (System.nanoTime() - opened) / 120_000_000f);
    }

    static void background(GuiGraphicsExtractor g, AbstractContainerScreen<?> s, Inventory inv, int mx, int my, float k) {
        UiHooks.setFocus(s, k);
        int left = s.getLeftPos(), top = s.getTopPos();
        HoloGlass.panel(g, left, top, s.getImageWidth(), s.getImageHeight(), k);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        UiCanvas c = GuiCanvas.of(g);
        int packY = Integer.MAX_VALUE, barY = Integer.MIN_VALUE;
        Slot over = null;
        for (Slot slot : s.getMenu().slots) {
            if (!slot.isActive()) continue;
            InventoryWells.Kind kind = InventoryWells.kind(slot);
            if (kind == InventoryWells.Kind.MAIN) packY = Math.min(packY, slot.y);
            if (kind == InventoryWells.Kind.HOTBAR) barY = Math.max(barY, slot.y);
            float edge = kind == InventoryWells.Kind.HOTBAR ? 0.32f : 0.22f;
            if (InventoryWells.selected(slot, inv)) edge = 0.85f;
            c.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, Glass.a(WELL, 0.58f * k));
            c.outline(slot.x - 1, slot.y - 1, 18, 18, Glass.a(Glass.EDGE, edge * k));
            if (mx - left >= slot.x - 1 && mx - left < slot.x + 17 && my - top >= slot.y - 1 && my - top < slot.y + 17) over = slot;
        }
        // the same two hairlines as the bag, parting what's here from what you carry
        if (packY != Integer.MAX_VALUE) c.hLine(7, s.getImageWidth() - 8, packY - 13, Glass.a(Glass.EDGE, 0.14f * k));
        if (barY != Integer.MIN_VALUE) c.hLine(7, s.getImageWidth() - 8, barY - 3, Glass.a(Glass.EDGE, 0.10f * k));
        g.pose().popMatrix();
        if (over != null) HoloGlass.glow(g, left + over.x - 1, top + over.y - 1, 18, 18, Glass.EDGE, 0.55f * k);
    }

    static void labels(GuiGraphicsExtractor g, Font font, Component title, int tx, int ty, @Nullable Component inventory, int ix, int iy, float k) {
        HoloGlass.text(g, font, title.getVisualOrderText(), tx, ty, Glass.a(Glass.EDGE, k));
        if (inventory != null) HoloGlass.text(g, font, inventory.getVisualOrderText(), ix, iy, Glass.a(Glass.LABEL, k));
    }
}
