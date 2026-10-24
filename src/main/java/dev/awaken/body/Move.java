package dev.awaken.body;

import dev.awaken.progression.Rank;
import org.jspecify.annotations.Nullable;

/**
 * The Awakened Body's moves, in unlock order (SPEC §13). The ordinal is the network id and the saved bit, so new moves
 * only ever go on the end.
 */
public enum Move {
    ROLL(Rank.E, "roll", "Soft landing", "roll on landing", "Land sprinting or crouching to roll: falls hurt less."),
    TACTICAL_SPRINT(Rank.E, "sprint", "Tactical sprint", "double-tap sprint", "Double-tap sprint to run harder for 3 s. A swing or a cast ends it."),
    SLIDE(Rank.D, "slide", "Slide", "crouch while sprinting", "Crouch while sprinting to slide low and fast. Jump out to keep the speed."),
    MANTLE(Rank.C, "mantle", "Mantle", "jump at a ledge", "Jump at a ledge up to chest height to pull yourself over it.");

    private static final Move[] VALUES = values();

    public final Rank rank;
    /** Command and lang key. */
    public final String key;
    public final String title;
    /** The toast's short line. */
    public final String hint;
    /** The Status screen's line. */
    public final String how;

    Move(Rank rank, String key, String title, String hint, String how) {
        this.rank = rank;
        this.key = key;
        this.title = title;
        this.hint = hint;
        this.how = how;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static @Nullable Move byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    public static @Nullable Move byKey(String key) {
        for (Move m : VALUES) if (m.key.equals(key)) return m;
        return null;
    }
}
