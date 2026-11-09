package dev.awaken.storage;

import net.minecraft.world.entity.player.Player;

/** Storage's face to other modules (SPEC §56). */
public final class StorageApi {
    private StorageApi() {}

    /** Whether this Inventory slot is pinned: Tidy and "Empty my bag" leave it alone. */
    public static boolean pinned(Player p, int inventorySlot) {
        return p.getData(StorageModule.PINS).has(inventorySlot);
    }

    /** Sets a pin directly (the keystroke path goes through {@code StorageNet.PinAsk}). */
    public static void setPinned(Player p, int inventorySlot, boolean on) {
        BagPins pins = p.getData(StorageModule.PINS);
        if (pins.has(inventorySlot) != on) p.setData(StorageModule.PINS, pins.toggled(inventorySlot));
    }
}
