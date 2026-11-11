package dev.awaken.lucidium.trial;

import org.jspecify.annotations.Nullable;

/**
 * The dreams worth having (SPEC §9.1, docs/dreams/PLAN.md §5.6): each dream is one trial with one goal line and a clock.
 * A Run teaches a move your body can already do (the course is built around it); a Room has one dream rule and hides a
 * real place; the Chase puts your week's fear behind you; the Duel is your Path's mentor. The id is saved (the ledger,
 * the plan), so ids never change.
 */
public enum Trial {
    FIRST("first", Kind.RUN, "The first dream", "Follow the road to the end before the dream fades.",
            "", "", "", 75, ""),
    WALL("wall", Kind.RUN, "The wall", "Get to the door at the end before the dream fades.",
            "This dream remembers the wall-run.", "wallrun,walljump", "RUNNER", 0,
            "Sprint and hold jump along a wall to run on it. Jump to kick off it to the next wall."),
    LEDGE("ledge", Kind.RUN, "The ledge", "Get to the door at the end before the dream fades.",
            "This dream remembers the ledge.", "hang,shimmy,ledgejump", "RUNNER", 0,
            "Jump at a ledge over your head to hang from it. Hold left or right to move along it, and jump to climb out."),
    VAULT("vault", Kind.RUN, "Over and under", "Get to the door at the end before the dream fades.",
            "This dream remembers the vault and the slide.", "vault,slide", "RUNNER", 0,
            "Run at a fence or a wall to vault it. Crouch while you sprint to slide under a beam."),
    CLIMB("climb", Kind.RUN, "The climb", "Get to the door at the top before the dream fades.",
            "This dream remembers the climb.", "wallclimb,grip", "RUNNER", 0,
            "Jump at a wall holding forward to run up it. Walk into a pipe or a ribbon and hold forward to climb it."),
    ROPE("rope", Kind.RUN, "The rope", "Get to the door at the end before the dream fades.",
            "This dream remembers the rope.", "rope", "TETHER", 0,
            "Hold the rope key to swing from an anchor. Let go of it as the rope glows on the way up, and you fly the furthest."),
    LEAP("leap", Kind.RUN, "The leap", "Get to the door at the end before the dream fades.",
            "This dream remembers the leap.", "leap", "LEAPER", 0,
            "Jump again in the air to leap higher, and dash forward before you land."),
    WINGS("wings", Kind.RUN, "The wings", "Fly through the rings to the door before the dream fades.",
            "This dream remembers the wings.", "wings", "WINGS", 0,
            "Open your wings in a fall to glide. Dive to gain speed, then pull up."),
    WRONG("wrong", Kind.ROOM, "What's wrong here", "Three things in this place are wrong. Look at each one and press H.",
            "", "", "", 180, ""),
    LOOK_AWAY("look_away", Kind.ROOM, "Look away", "The bell moves whenever you look away from it. Catch it.",
            "", "", "", 180, ""),
    HUSH("hush", Kind.ROOM, "Hush", "Something is asleep next to the bell. Take the bell without waking it.",
            "", "", "", 180, ""),
    LONG_FALL("long_fall", Kind.ROOM, "The long fall", "You're falling. Find a way to land.",
            "", "", "", 120, ""),
    FOLD("fold", Kind.ROOM, "The fold", "This hallway keeps coming back. Find the way out of it.",
            "", "", "", 180, ""),
    NO_DOOR("no_door", Kind.ROOM, "No door", "There's no door. Make one.",
            "", "", "", 180, ""),
    CHASE("chase", Kind.CHASE, "The chase", "Your fear is coming after you. Get to the door.",
            "", "", "", 120, ""),
    DUEL("duel", Kind.DUEL, "The duel", "Your mentor is waiting. Fight until they give in.",
            "", "", "", 150, "");

    public enum Kind { RUN, ROOM, CHASE, DUEL }

    /** Saved id. */
    public final String id;
    public final Kind kind;
    /** The Dream Journal's name for it. */
    public final String title;
    /** The goal line at the top of the screen once you take control. */
    public final String goal;
    /** The small line under it (a Run's move), or "". */
    public final String sub;
    /** The body moves a Run remembers (Move keys, comma-separated; an Ascent tier's name for the rope, leap and wings), or "". */
    public final String move;
    /** The Ascent track a Run's mastery goes to, or "". */
    public final String track;
    /** A Room's (and the first Run's) fixed clock in seconds; 0: from the course's par. */
    public final int seconds;
    /** How to do a Run's moves awake, for the morning card ("" for the rest). */
    public final String how;

    Trial(String id, Kind kind, String title, String goal, String sub, String move, String track, int seconds, String how) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.goal = goal;
        this.sub = sub;
        this.move = move;
        this.track = track;
        this.seconds = seconds;
        this.how = how;
    }

    /** The body moves a Run remembers. */
    public java.util.List<String> moves() {
        return move.isEmpty() ? java.util.List.of() : java.util.List.of(move.split(","));
    }

    private static final Trial[] VALUES = values();

    public static @Nullable Trial byId(String id) {
        for (Trial t : VALUES) if (t.id.equals(id)) return t;
        return null;
    }

    public boolean run() {
        return kind == Kind.RUN;
    }

    /** The Runs, in the order they come to wait (each needs the one before it finished, and its move). */
    public static final Trial[] RUNS = {WALL, LEDGE, VAULT, CLIMB, ROPE, LEAP, WINGS};

    /** What a Run's move is called on the morning card and in THE BODY's line ("the wall-run"). */
    public String moveName() {
        return switch (this) {
            case WALL -> "the wall-run";
            case LEDGE -> "the ledge";
            case VAULT -> "the vault and the slide";
            case CLIMB -> "the climb";
            case ROPE -> "the rope";
            case LEAP -> "the leap";
            case WINGS -> "the wings";
            default -> "";
        };
    }

    /** The morning's line for a Run's move: what your body remembers and how to do it, in plain words. */
    public String remembers() {
        return switch (this) {
            case WALL -> "Your body remembers the wall-run. Sprint and hold jump along a wall to run on it; jump again to kick off to the next wall.";
            case LEDGE -> "Your body remembers the ledge. Jump at a high edge to hang, hold left or right to move along it, jump to climb or leap.";
            case VAULT -> "Your body remembers the vault and the slide. Run at a fence or wall to go over it; crouch while sprinting to slide under.";
            case CLIMB -> "Your body remembers the climb. Jump at a wall holding forward to run up it; walk into a pipe or rope to grip it.";
            case ROPE -> "Your body remembers the rope. Let go at the top of a swing to fly furthest.";
            case LEAP -> "Your body remembers the leap. Jump again in the air, and dash before you land.";
            case WINGS -> "Your body remembers the wings. Dive to gather speed, then pull up to glide.";
            default -> "";
        };
    }
}
