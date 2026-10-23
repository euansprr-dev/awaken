package dev.awaken.descent;

import dev.awaken.Awaken;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The chain of strata (SPEC section 8, opening ranks from docs/plan.html). Everything here is pure data and maths so
 * it can be used from worldgen threads, the client and GameTests.
 *
 * <p>Depth index: 0 = the surface (the Well), 1..10 = strata, 11+ = the Endless (depth d = index - 10).
 */
public final class Strata {
    private Strata() {}

    public static final int COUNT = 10;
    /** Depth index of Endless depth d is ENDLESS_BASE + d. */
    public static final int ENDLESS_BASE = 10;

    // Rule ids (= the stratum that introduced them). Masks use bit (1 << id).
    public static final int RULE_WIND = 2, RULE_FLOOD = 3, RULE_RISE = 4, RULE_LISTENERS = 5, RULE_MACHINES = 6,
            RULE_TIDES = 7, RULE_ROOMS = 8, RULE_SHADOW = 9, RULE_SEED = 10;

    public record Def(int n, String key, String name, String flavour, String rule, String ruleShort, Rank opensAt, String guardian) {}

    private static final Def[] DEFS = {
            new Def(1, "rim", "The Rim",
                    "Roots hang from the walls and the moss glows where something touched it. The first stratum is gentle.",
                    "None. Learn the Descent: walk the spiral down, light the Waystone at the bottom camp, take the pit.",
                    "No rule. Learn the Descent.", Rank.E, "awaken:guardian_rim"),
            new Def(2, "gardens", "The Hanging Gardens",
                    "A vertical garden. Islands of moss hang in the open dark, trailing glow-vines you can climb.",
                    "Wind gusts. When the wind howls it pushes you toward the drop. Sneak near ledges.",
                    "Wind gusts push you near ledges.", Rank.D, "awaken:guardian_gardens"),
            new Def(3, "choir", "The Drowned Choir",
                    "Flooded halls where something sings under the water. The halls remember a tide.",
                    "The water rises every five minutes, then drains. When the choir sings, get high.",
                    "The water rises every 5 minutes.", Rank.D, "awaken:guardian_choir"),
            new Def(4, "orchard", "The Bone Orchard",
                    "Ribs the size of bridges arch over the pit. Bone trees grow from the floor below.",
                    "The dead rise once. What you kill here gets up again, one time.",
                    "The dead rise once.", Rank.C, "awaken:guardian_orchard"),
            new Def(5, "silent", "The Silent Halls",
                    "Long quiet halls. The things that live here are blind. They hunt by ear.",
                    "Sound draws the Listeners. Sprinting, fighting and breaking blocks are loud. Sneaking is silent.",
                    "Sound draws the Listeners. Sneak.", Rank.C, "awaken:guardian_silent"),
            new Def(6, "foundry", "The Precursor Foundry",
                    "Machine halls built by the Precursors. They are not dead, only waiting for power.",
                    "Machines wake. The pit is sealed until all five conduits around it are lit. Each conduit you touch flips its neighbours.",
                    "Light the five conduits to open the way.", Rank.B, "awaken:guardian_foundry"),
            new Def(7, "inverted", "The Inverted Sea",
                    "A sea below and a sea above. The tide here does not move the water. It moves you.",
                    "Gravity flips with the tides. When the tide turns you float up; when it ebbs you fall softly. Be on solid ground when it ends.",
                    "Gravity flips with the tides.", Rank.B, "awaken:guardian_inverted"),
            new Def(8, "dreaming", "The Dreaming Stratum",
                    "Rooms that remember being other rooms. The spiral is the only thing that stays.",
                    "Rooms change when nobody is looking at them.",
                    "Rooms change when you're not looking.", Rank.A, "awaken:guardian_dreaming"),
            new Def(9, "court", "The Hollow Court",
                    "A court of black stone around an empty throne. The floor remembers your footsteps.",
                    "Your shadow fights you. Every few minutes it stands up, with your face and your weapon.",
                    "Your shadow fights you.", Rank.S, "awaken:guardian_court"),
            new Def(10, "seed", "The Seed",
                    "The bottom. Something vast sleeps in a shell of light, wired into the walls of the world.",
                    "The Seed. The end of the Descent, and the way into the Endless.",
                    "The Seed.", Rank.S, ""),
    };

    public static Def def(int n) {
        return DEFS[Math.max(1, Math.min(COUNT, n)) - 1];
    }

    // ---- dimensions -------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Level>[] KEYS = new ResourceKey[COUNT + 1];
    public static final ResourceKey<Level> ENDLESS = ResourceKey.create(Registries.DIMENSION, Awaken.id("endless"));

    static {
        for (int n = 1; n <= COUNT; n++) KEYS[n] = ResourceKey.create(Registries.DIMENSION, Awaken.id("stratum_" + n));
    }

    public static ResourceKey<Level> key(int n) {
        return KEYS[n];
    }

    /** Stratum number of a dimension (1..10), 0 for the Endless or anything else. */
    public static int stratumOf(ResourceKey<Level> dim) {
        for (int n = 1; n <= COUNT; n++) if (KEYS[n] == dim || KEYS[n].equals(dim)) return n;
        return 0;
    }

    public static boolean isDescent(ResourceKey<Level> dim) {
        return dim.equals(ENDLESS) || stratumOf(dim) > 0;
    }

    /** Depth index for a position (0 = not in the Descent). */
    public static int depthAt(ResourceKey<Level> dim, BlockPos pos) {
        int n = stratumOf(dim);
        if (n > 0) return n;
        if (dim.equals(ENDLESS)) {
            int d = endlessCellAt(pos.getX(), pos.getZ());
            return d > 0 ? ENDLESS_BASE + d : 0;
        }
        return 0;
    }

    public static ResourceKey<Level> dimensionOf(int depth) {
        return depth > ENDLESS_BASE ? ENDLESS : KEYS[Math.max(1, Math.min(COUNT, depth))];
    }

    // ---- the Endless: one cell per depth, 1024 blocks apart, 64 per row ------------------------------------------------

    public static final int CELL = 1024;

    public static int endlessCellAt(int x, int z) {
        int cx = Math.floorDiv(x + CELL / 2, CELL), cz = Math.floorDiv(z + CELL / 2, CELL);
        if (cx < 0 || cx >= 64 || cz < 0) return 0;
        return 1 + cx + 64 * cz;
    }

    public static int cellCenterX(int d) {
        return Math.floorMod(d - 1, 64) * CELL;
    }

    public static int cellCenterZ(int d) {
        return Math.floorDiv(d - 1, 64) * CELL;
    }

    /** Centre (local origin) of the stratum or Endless cell for a depth index, as x/z. */
    public static int centerX(int depth) {
        return depth > ENDLESS_BASE ? cellCenterX(depth - ENDLESS_BASE) : 0;
    }

    public static int centerZ(int depth) {
        return depth > ENDLESS_BASE ? cellCenterZ(depth - ENDLESS_BASE) : 0;
    }

    /** Generator form of an Endless depth (1..9). Seedless on purpose: the biome source and the client agree on it. */
    public static int endlessForm(int d) {
        return 1 + (int) Math.floorMod(mix(d * 0x9E3779B97F4A7C15L + 0x51ED27L), 9L);
    }

    /** Generator form for a depth index. */
    public static int formOf(int depth) {
        return depth > ENDLESS_BASE ? endlessForm(depth - ENDLESS_BASE) : Math.max(1, Math.min(COUNT, depth));
    }

    // ---- rules --------------------------------------------------------------------------------------------------------

    /** Rule mask of a depth index. Strata have their own rule; the Endless stacks more of them the deeper you go. */
    public static int rulesOf(int depth) {
        if (depth <= 0) return 0;
        if (depth <= COUNT) return depth == 1 ? 0 : 1 << depth;
        int d = depth - ENDLESS_BASE;
        int form = endlessForm(d);
        int mask = form >= 2 && form <= 9 ? 1 << form : 0;
        int wanted = Math.min(3, 1 + d / 4);
        int[] behavioural = {RULE_WIND, RULE_FLOOD, RULE_RISE, RULE_LISTENERS, RULE_TIDES, RULE_SHADOW};
        long h = mix(d * 0xC2B2AE3D27D4EB4FL + 77);
        for (int tries = 0; Integer.bitCount(mask) < wanted && tries < 24; tries++) {
            h = mix(h + tries);
            mask |= 1 << behavioural[(int) Math.floorMod(h, (long) behavioural.length)];
        }
        return mask;
    }

    public static boolean has(int mask, int rule) {
        return (mask & (1 << rule)) != 0;
    }

    public static String ruleName(int rule) {
        return switch (rule) {
            case RULE_WIND -> "Wind gusts";
            case RULE_FLOOD -> "Rising water";
            case RULE_RISE -> "The dead rise once";
            case RULE_LISTENERS -> "The Listeners";
            case RULE_MACHINES -> "Machines wake";
            case RULE_TIDES -> "Gravity tides";
            case RULE_ROOMS -> "Shifting rooms";
            case RULE_SHADOW -> "Your shadow";
            case RULE_SEED -> "The Seed";
            default -> "None";
        };
    }

    // ---- names, levels, gating ----------------------------------------------------------------------------------------

    public static String label(int depth) {
        if (depth <= 0) return "The Well";
        if (depth <= COUNT) return "Stratum " + depth + " · " + def(depth).name();
        return "The Endless · depth " + (depth - ENDLESS_BASE);
    }

    public static String shortName(int depth) {
        if (depth <= 0) return "The Well";
        if (depth <= COUNT) return def(depth).name();
        return "Endless " + (depth - ENDLESS_BASE);
    }

    /** Content level for mobs (SPEC section 2): stratum n uses the content level of rank index min(6, (n+1)/2). */
    public static int contentLevel(int depth) {
        if (depth <= 0) return Rank.E.contentLevel;
        if (depth <= COUNT) return Rank.of(Math.min(6, (depth + 1) / 2)).contentLevel;
        return Rank.SOVEREIGN.contentLevel + 4 * (depth - ENDLESS_BASE);
    }

    /** Depth record value (counter awaken:depth_record): strata count as their number, the Endless as 10 + depth. */
    public static int recordValue(int depth) {
        return Math.max(0, depth);
    }

    public enum Access { OPEN, SEALED_RANK, SEALED_LEVEL, SEALED_SEED }

    /**
     * Whether a player may enter a depth. Stratum 10 is the Sovereign trial (SPEC 4.4: "SOVEREIGN after the Seed"), so
     * it opens at S-rank once the player has reached the Sovereign level. The Endless opens after the Seed.
     */
    public static Access access(Rank rank, int level, boolean seedCleared, int depth) {
        if (depth <= 1) return Access.OPEN;
        if (depth > COUNT) return seedCleared ? Access.OPEN : Access.SEALED_SEED;
        Def d = def(depth);
        if (rank.ordinal() < d.opensAt().ordinal()) return Access.SEALED_RANK;
        if (depth == COUNT && rank != Rank.SOVEREIGN && level < Rank.SOVEREIGN.levelRequired) return Access.SEALED_LEVEL;
        return Access.OPEN;
    }

    public static String sealedMessage(Access a, int depth) {
        return switch (a) {
            case SEALED_RANK -> label(depth) + " is sealed. It opens at " + def(depth).opensAt().label + "-rank.";
            case SEALED_LEVEL -> label(depth) + " is sealed. It opens at S-rank, level " + Rank.SOVEREIGN.levelRequired + ".";
            case SEALED_SEED -> "The Endless is sealed. It opens after the Seed.";
            case OPEN -> "";
        };
    }

    // ---- hashing (shared by worldgen and rules) -----------------------------------------------------------------------

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, int x, int y, int z) {
        return mix(seed ^ mix(x * 0x632BE59BD9B4E019L + y * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL));
    }

    /** Uniform in [0, 1). */
    public static double hash01(long seed, int x, int y, int z) {
        return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }
}
