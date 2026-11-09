package dev.awaken.hall.client;

import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.net.ArmourPayloads;
import org.jspecify.annotations.Nullable;

/** What the server said of a suit, read the way the Hall needs it. The client decides nothing here. */
final class SuitState {
    private SuitState() {}

    static final int NONE = 0, UNFOUND = 1, OVER = 2, SHORT = 3, READY = 4, MADE = 5;

    static ArmourPayloads.@Nullable Offer offer(ArmourPayloads.@Nullable Offers o, ArmourSet set, int slot) {
        if (o == null) return null;
        int want = set.ordinal() * 4 + slot;
        for (ArmourPayloads.Offer one : o.offers()) if (one.piece() == want) return one;
        return null;
    }

    /** One piece: not offered at all; pattern not found; known but above your rank; short of materials; everything's in your pack; yours. */
    static int of(ArmourPayloads.@Nullable Offers o, ArmourSet set, int slot) {
        ArmourPayloads.Offer one = offer(o, set, slot);
        if (one == null) return NONE;
        int f = one.flags();
        if ((f & ArmourPayloads.NO_PATTERN) != 0) return UNFOUND;
        if ((f & ArmourPayloads.OWNED) != 0) return MADE;
        if ((f & ArmourPayloads.ABOVE) != 0) return OVER;
        return (f & ArmourPayloads.AFFORD) != 0 ? READY : SHORT;
    }

    static boolean hidden(ArmourPayloads.@Nullable Offers o, ArmourSet set) {
        int s = of(o, set, 0);
        return s == UNFOUND || s == NONE;
    }

    static int count(ArmourPayloads.@Nullable Offers o, ArmourSet set, int state) {
        int n = 0;
        for (int i = 0; i < 4; i++) if (of(o, set, i) == state) n++;
        return n;
    }

    static boolean above(ArmourPayloads.@Nullable Offers o, ArmourSet set) {
        ArmourPayloads.Offer one = offer(o, set, 0);
        return one != null && (one.flags() & ArmourPayloads.ABOVE) != 0;
    }

    static boolean tracked(ArmourPayloads.@Nullable Offers o, ArmourSet set) {
        return o != null && o.tracked().equals(set.id);
    }

    /** The word beside a piece on the figure. */
    static String word(int state) {
        return switch (state) {
            case MADE -> "You have it";
            case READY -> "Ready to forge";
            case SHORT -> "Needs more";
            case OVER -> "Opens later";
            default -> "Pattern not found";
        };
    }

    static int colour(int state) {
        return switch (state) {
            case MADE, READY -> Stage.GOOD;
            case SHORT -> Stage.BAD;
            default -> Stage.DIM;
        };
    }
}
