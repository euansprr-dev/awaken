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
    MANTLE(Rank.C, "mantle", "Mantle", "jump at a ledge", "Jump at a ledge up to chest height to pull yourself over it."),
    // ---- the Runner's Kit (SPEC §16.3; spike S3: on for everyone, ranks ignored) ----
    WALL_RUN(Rank.E, "wallrun", "Wall-run", "sprint and jump along a wall", "Sprint and hold jump along a wall, high enough off the ground, to run on it."),
    WALL_JUMP(Rank.E, "walljump", "Wall jump", "jump during a wall-run", "Jump during a wall-run to kick off; land on another wall to run again."),
    LEDGE_HANG(Rank.E, "hang", "Ledge hang", "jump at a high ledge", "Jump toward a ledge above your head, or sneak forward off a high edge, to hang."),
    SHIMMY(Rank.E, "shimmy", "Shimmy", "left or right while hanging", "Hold left or right while hanging to move along the edge."),
    LEDGE_JUMP(Rank.E, "ledgejump", "Ledge jump", "jump while hanging", "Jump while hanging: up, sideways with left or right, or back off the wall."),
    // ---- Wall Stride (SPEC §17.2) ----
    WALL_CLIMB(Rank.E, "wallclimb", "Wall climb", "jump at a wall, holding forward (Wall Stride)",
            "With Wall Stride, jump straight at a wall holding forward to run a few steps up it, into a ledge if one is in reach. Jump to kick back off."),
    // ---- the grip on a climb line (SPEC §17.23) ----
    GRIP(Rank.E, "grip", "Pipe grip", "walk or jump into a pipe, rope or ladder",
            "Grip a pipe, rope or ladder: forward climbs, Shift slides down, Jump with back kicks off the wall, Jump with left or right swings out.");

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

    /** A Runner's Kit move (the spike's moves: everyone has them). */
    public boolean runner() {
        return ordinal() >= WALL_RUN.ordinal();
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
