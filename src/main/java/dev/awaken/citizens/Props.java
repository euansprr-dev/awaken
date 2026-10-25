package dev.awaken.citizens;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a citizen holds, as a small code: the body only touches its equipment when the code changes (no per-tick stacks). */
final class Props {
    private Props() {}

    static final int NONE = 0, POLE = 1, HAMMER = 2, TROWEL = 3, SCROLL = 4, HALBERD = 5, LANTERN = 6, BREAD = 7, LAUNDRY = 8,
            BOOK = 9, SATCHEL = 10, BAG = 11, BUCKET = 12;

    static ItemStack stack(int code) {
        Item item = switch (code) {
            case POLE -> CitizensContent.LAMPLIGHTER_POLE.get();
            case HAMMER -> CitizensContent.SMITH_HAMMER.get();
            case TROWEL -> CitizensContent.MASON_TROWEL.get();
            case SCROLL -> CitizensContent.CRIER_SCROLL.get();
            case HALBERD -> CitizensContent.WATCH_HALBERD.get();
            case LANTERN -> CitizensContent.HAND_LANTERN.get();
            case BREAD -> CitizensContent.BREAD_BASKET.get();
            case LAUNDRY -> CitizensContent.LAUNDRY_BASKET.get();
            case BOOK -> CitizensContent.LEDGER_BOOK.get();
            case SATCHEL -> CitizensContent.COURIER_SATCHEL.get();
            case BAG -> CitizensContent.STOLEN_BAG.get();
            case BUCKET -> Items.WATER_BUCKET;
            default -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
