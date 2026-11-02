package dev.awaken.delve.api;

import dev.awaken.delve.PickVerses;
import net.minecraft.world.item.ItemStack;

/** The Delver's Pick for other modules (SPEC §34.4): how far it has woken. Enchanting gives it room by verse (§45.14). */
public final class Pick {
    private Pick() {}

    /** Its verse: 0 asleep to 6 (VI · Call); -1 when the stack isn't the Delver's Pick. */
    public static int verse(ItemStack s) {
        return PickVerses.verse(s);
    }
}
