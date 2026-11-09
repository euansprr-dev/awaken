package dev.awaken.storage.client;

import dev.awaken.holo.client.HoloGlass;
import dev.awaken.holo.ui.InventoryWells;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/** A chest in the bag's glass (SPEC §56): the same panel and wells, with every slot where vanilla puts it. */
final class ChestLook {
    private ChestLook() {}

    private static final int WELL = 0x040A16;
    private static final float WELL_FILL = 0.58f;

    /** The glass, the wells and a light under the slot the cursor's on. */
    static void background(GuiGraphicsExtractor g, AbstractContainerScreen<?> s, Inventory inv, @Nullable Slot over, float k) {
        int left = s.getLeftPos(), top = s.getTopPos();
        HoloGlass.panel(g, left, top, s.getImageWidth(), s.getImageHeight(), k);
        g.pose().pushMatrix();
        g.pose().translate(left, top);
        UiCanvas c = GuiCanvas.of(g);
        int packY = Integer.MAX_VALUE, barY = Integer.MIN_VALUE;
        for (Slot slot : s.getMenu().slots) {
            if (!slot.isActive()) continue;
            InventoryWells.Kind kind = InventoryWells.kind(slot);
            if (kind == InventoryWells.Kind.MAIN) packY = Math.min(packY, slot.y);
            if (kind == InventoryWells.Kind.HOTBAR) barY = Math.max(barY, slot.y);
            float edge = kind == InventoryWells.Kind.HOTBAR ? 0.32f : 0.22f;
            if (InventoryWells.selected(slot, inv)) edge = 0.85f;
            well(c, slot.x - 1, slot.y - 1, edge, k);
        }
        // the same two hairlines as the bag, parting what's stored from what you carry
        if (packY != Integer.MAX_VALUE) c.hLine(7, s.getImageWidth() - 8, packY - 13, Glass.a(Glass.EDGE, 0.14f * k));
        if (barY != Integer.MIN_VALUE) c.hLine(7, s.getImageWidth() - 8, barY - 3, Glass.a(Glass.EDGE, 0.10f * k));
        g.pose().popMatrix();
        if (over != null) HoloGlass.glow(g, left + over.x - 1, top + over.y - 1, 18, 18, Glass.EDGE, 0.55f * k);
    }

    private static void well(UiCanvas c, int x, int y, float edge, float k) {
        c.fill(x + 1, y + 1, x + 17, y + 17, Glass.a(WELL, WELL_FILL * k));
        c.outline(x, y, 18, 18, Glass.a(Glass.EDGE, edge * k));
        c.hLine(x + 1, x + 16, y + 1, Glass.a(Glass.EDGE, edge * 0.35f * k));
    }
}
