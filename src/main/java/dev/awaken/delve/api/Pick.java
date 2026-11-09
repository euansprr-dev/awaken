package dev.awaken.delve.api;

import dev.awaken.delve.PickVerses;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForm;
import net.minecraft.world.item.ItemStack;

/**
 * The Delver's Pick and the forged picks for other modules (SPEC §34.4, §49): how far an old one has woken, and what any
 * pick can do. Ask {@link #has} rather than reading an item's own data, so old picks and forged ones answer alike.
 */
public final class Pick {
    private Pick() {}

    /**
     * What a pick can do, in the order the old pick woke them (a gift's ordinal is the verse that gave it). A gift's bit in
     * {@link dev.awaken.delve.forged.PickForged#gifts()} is 1 shifted by its ordinal.
     */
    public enum Gift {
        HUM("It hums when its head points at metal in the rock"),
        KINDLE("Sneak and strike bare rock: an ember sticks in the wall and lights the cave for five minutes"),
        SOUNDING("Stand still, sneak and tap rock: a cave sounds hollow from its side, and a lode answers"),
        WIDE_SEAM("True strikes come easier: the seam is wider"),
        LONG_SOUNDING("Its tap carries twice as far through the rock"),
        HUSH("Your strikes make no sound to creatures, and you mine at full speed while sneaking"),
        CALL("When you break a lode's heart, the open blocks near it answer and their ore flies to you");

        public final String line;

        Gift(String line) {
            this.line = line;
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    /** Its verse: 0 asleep to 6 (VI · Call); -1 when the stack isn't the old Delver's Pick. */
    public static int verse(ItemStack s) {
        return PickVerses.verse(s);
    }

    /** Whether this pick, old or forged, can do this. Anything that isn't one of ours can't. */
    public static boolean has(ItemStack s, Gift g) {
        int verse = PickVerses.verse(s);
        if (verse >= 0) return verse >= g.ordinal();
        ForgedPickItem i = ForgedPicks.item(s);
        if (i == null) return false;
        boolean bit = (ForgedPicks.data(s).gifts() & g.bit()) != 0;
        return switch (g) {
            case HUM -> i.form.hum > 0;
            case WIDE_SEAM -> i.tier >= 2 || bit;
            case CALL -> i.tier >= 5 || bit;
            default -> bit;
        };
    }

    /** How far it hums toward a lode; 0 when it doesn't. */
    public static int humReach(ItemStack s) {
        if (PickVerses.verse(s) >= 0) return PickForm.DELVER.hum;
        ForgedPickItem i = ForgedPicks.item(s);
        return i == null ? 0 : i.form.hum;
    }

    /** Whether it's one of the forged picks. */
    public static boolean forged(ItemStack s) {
        return ForgedPicks.is(s);
    }

    /** Its form's id ("delver"); null when it isn't forged. */
    public static @org.jspecify.annotations.Nullable String form(ItemStack s) {
        ForgedPickItem i = ForgedPicks.item(s);
        return i == null ? null : i.form.id;
    }

    /** Its smith's quality, 0 Plain to 3 Flawless; -1 when it isn't forged. */
    public static int quality(ItemStack s) {
        return ForgedPicks.is(s) ? ForgedPicks.data(s).quality() : -1;
    }

    /** The enchantment room it was promised beyond its quality's (an old pick's room, kept); 0 when it isn't forged. */
    public static int room(ItemStack s) {
        return ForgedPicks.is(s) ? ForgedPicks.data(s).room() : 0;
    }
}
