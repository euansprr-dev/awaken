package dev.awaken.wildlife;

/**
 * The wild's small life (SPEC "Wild life"): twelve bodies (hero rigs, client entities) and seven kinds of insect (drawn
 * in one pass from a sprite sheet), with how far from the player each lives and how many may be near at once.
 */
public final class WildKinds {
    private WildKinds() {}

    public static final int SONGBIRD = 0, WOODPECKER = 1, CROW = 2, OWL = 3, RAPTOR = 4, GULL = 5, DUCK = 6, HERON = 7,
            SQUIRREL = 8, RABBIT = 9, FOX = 10, FROG = 11;
    /** Kinds below this are bodies (WildCritter); the rest are insects. */
    public static final int BODIES = 12;
    public static final int BUTTERFLY = 12, BEE = 13, DRAGONFLY = 14, FIREFLY = 15, MOTH = 16, GNATS = 17, STRIDER = 18;
    public static final int COUNT = 19;

    public static final String[] NAMES = {"songbird", "woodpecker", "crow", "owl", "raptor", "gull", "duck", "heron",
            "squirrel", "rabbit", "fox", "frog", "butterfly", "bee", "dragonfly", "firefly", "moth", "gnats", "strider"};
    /** Each body's rig (tools/art/specs/wild_fauna.py). */
    public static final String[] RIGS = {"wild_songbird", "wild_woodpecker", "wild_crow", "wild_owl", "wild_raptor",
            "wild_gull", "wild_duck", "wild_heron", "wild_squirrel", "wild_rabbit", "wild_fox", "wild_frog"};

    /**
     * How far from the player each kind is made (blocks, horizontal): small things only where they can be seen, a buzzard
     * or a gull far out. Dropped past this plus {@link #SLACK}.
     */
    public static final double[] REACH = {40, 40, 64, 52, 120, 80, 56, 64, 32, 40, 56, 22,
            28, 16, 28, 40, 24, 24, 16};
    public static final double SLACK = 16;
    /** At most this many of each near the player. */
    public static final int[] CAP = {9, 2, 4, 2, 2, 6, 6, 2, 3, 4, 1, 4,
            14, 10, 8, 64, 12, 5, 10};
    /** Bodies and insect instances in all (a gnat swarm is one instance). */
    public static final int MAX_BODIES = 18, MAX_INSECTS = 140;

    public static boolean body(int kind) {
        return kind >= 0 && kind < BODIES;
    }

    public static boolean flies(int kind) {
        return kind <= HERON;
    }
}
