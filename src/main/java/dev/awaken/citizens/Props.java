package dev.awaken.citizens;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What a citizen holds, as a small code: the body only touches its equipment when the code changes (no per-tick stacks). */
final class Props {
    private Props() {}

    static final int NONE = 0, POLE = 1, HAMMER = 2, TROWEL = 3, SCROLL = 4, HALBERD = 5, LANTERN = 6, BREAD = 7, LAUNDRY = 8,
            BOOK = 9, SATCHEL = 10, BAG = 11, BUCKET = 12,
            // the Registry ceremony (SPEC §16.4): what Bram hands over, by item id (other modules' items)
            HOLO_BAND = 13, LICENCE = 14;

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
            case HOLO_BAND -> byId("holo_band");
            case LICENCE -> byId("hunters_licence");
            default -> null;
        };
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static @org.jspecify.annotations.Nullable Item byId(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.awaken.Awaken.id(path)).orElse(null);
    }

    /** A prop by name (CitizenHooks.stage): "holo_band", "licence", "book", "scroll"; anything else is none. */
    static int named(String name) {
        return switch (name) {
            case "holo_band" -> HOLO_BAND;
            case "licence" -> LICENCE;
            case "book" -> BOOK;
            case "scroll" -> SCROLL;
            default -> NONE;
        };
    }
}
