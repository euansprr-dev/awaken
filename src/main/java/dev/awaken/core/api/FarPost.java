package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Far Landing's post (SPEC §67): in a world played far from Carillon, the city's errands come to you. People write
 * ({@link #letter}): the card comes to you wherever you are and a copy waits in the skiff's hold. What you'd have handed
 * someone in the city goes in the hold at the landing's mooring ({@link #hold}), and a keepsake goes home through it
 * ({@link #keepsake}). The frontier module installs the letters; living installs the errands; world installs the
 * keepsakes. Each default does nothing, so a world without the skiff behaves exactly as before.
 */
public final class FarPost {
    private FarPost() {}

    /** Shows a letter and keeps a copy (frontier). */
    public interface Letters {
        void letter(ServerPlayer p, String from, String text);

        /** Only the hold's copy (the card was shown some other way: an offer, say). */
        void keep(ServerPlayer p, String from, String text);
    }

    /** City people's waiting steps, handed in through the hold: how many moved; and Hale's board, at the mooring (living). */
    public interface Errands {
        int hold(ServerPlayer p);

        boolean board(ServerPlayer p);

        /** Every few seconds while a player who has flown out is on the land, away from the city: who writes first. */
        default void out(ServerPlayer p) {}
    }

    /** A keepsake held at the hold: sent home to its family, who write back; false when it isn't one (world). */
    public interface Keepsakes {
        boolean send(ServerPlayer p, ItemStack held);
    }

    /** Whether the skiff will take this player yet: Ilse has given them her coal, or the story is off (living). */
    public interface Ready {
        boolean ready(ServerPlayer p);
    }

    /** One of Carillon's four bells was rung for a player out on the land: a letter, and for two of them an invitation (frontier). */
    public interface CityBells {
        void rung(ServerPlayer p, String bellId);
    }

    private static CityBells cityBells = (p, id) -> {};

    public static void installCityBells(CityBells c) {
        cityBells = c;
    }

    public static void cityBell(ServerPlayer p, String bellId) {
        cityBells.rung(p, bellId);
    }

    private static Letters letters = new Letters() {
        @Override
        public void letter(ServerPlayer p, String from, String text) {}

        @Override
        public void keep(ServerPlayer p, String from, String text) {}
    };
    private static Errands errands = new Errands() {
        @Override
        public int hold(ServerPlayer p) {
            return 0;
        }

        @Override
        public boolean board(ServerPlayer p) {
            return false;
        }
    };
    private static Keepsakes keepsakes = (p, held) -> false;
    private static Ready ready = p -> true;

    public static void installReady(Ready r) {
        ready = r;
    }

    public static boolean ready(ServerPlayer p) {
        return ready.ready(p);
    }

    public static void installLetters(Letters l) {
        letters = l;
    }

    public static void installErrands(Errands e) {
        errands = e;
    }

    public static void installKeepsakes(Keepsakes k) {
        keepsakes = k;
    }

    /** A letter from {@code from} ("Bram", "Oren at the Forge"), in their own words. */
    public static void letter(ServerPlayer p, String from, String text) {
        letters.letter(p, from, text);
    }

    public static void keepLetter(ServerPlayer p, String from, String text) {
        letters.keep(p, from, text);
    }

    public static void out(ServerPlayer p) {
        errands.out(p);
    }

    public static int hold(ServerPlayer p) {
        return errands.hold(p);
    }

    public static boolean board(ServerPlayer p) {
        return errands.board(p);
    }

    public static boolean keepsake(ServerPlayer p, ItemStack held) {
        return keepsakes.send(p, held);
    }
}
