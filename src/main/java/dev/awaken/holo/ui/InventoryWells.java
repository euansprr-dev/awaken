package dev.awaken.holo.ui;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;

/**
 * The holo inventory's wells (docs/modules/holo.md "The inventory"): the projected socket drawn under every active slot
 * of the open menu in place of vanilla's printed slot squares. A well is 18×18 around the slot's 16×16 item square
 * (the crafting result 26×26, as vanilla's), placed from the slot's own position, so a slot another mod adds gets
 * one too. The kind picks the well's emphasis (the hotbar a little brighter, the result lit while it holds
 * something). Side-agnostic and allocation-free: drawn every frame, checked by GameTests on a real player's menu.
 */
public final class InventoryWells {
    private InventoryWells() {}

    public enum Kind { HOTBAR, MAIN, ARMOR, OFFHAND, CRAFT, RESULT, OTHER }

    public static Kind kind(Slot s) {
        if (s instanceof ResultSlot) return Kind.RESULT;
        if (s.container instanceof CraftingContainer) return Kind.CRAFT;
        if (s instanceof ArmorSlot) return Kind.ARMOR;
        if (s.container instanceof Inventory) {
            int i = s.getContainerSlot();
            if (i < Inventory.SELECTION_SIZE) return Kind.HOTBAR;
            if (i < Inventory.INVENTORY_SIZE) return Kind.MAIN;
            if (i == Inventory.SLOT_OFFHAND) return Kind.OFFHAND;
        }
        return Kind.OTHER;
    }

    /** How far the well reaches past the 16×16 item square on each side. */
    public static int pad(Kind k) {
        return k == Kind.RESULT ? 5 : 1;
    }

    /** The well's left edge, panel units (the slot's own coordinates are relative to the panel's corner). */
    public static int x(Slot s, Kind k) {
        return s.x - pad(k);
    }

    public static int y(Slot s, Kind k) {
        return s.y - pad(k);
    }

    public static int size(Kind k) {
        return 16 + 2 * pad(k);
    }

    /** Whether this slot is the hotbar slot in the player's hand. */
    public static boolean selected(Slot s, Inventory inv) {
        return kind(s) == Kind.HOTBAR && s.container == inv && s.getContainerSlot() == inv.getSelectedSlot();
    }
}
