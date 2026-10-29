package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.gear.HunterTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The Forge Song's rules (SPEC §44.4), the same on both sides: the stages, each set's rhythm and melody, the marks a piece
 * is struck on, how a heat, a strike and a quench are judged, the quality they add up to, and what each piece costs. The
 * server judges with these; the client draws and predicts with them. Times are in game ticks from a stage's start.
 */
public final class ForgeSong {
    private ForgeSong() {}

    // ---- stages ------------------------------------------------------------------------------------------------------

    /** At the Forge (D and up): heat, shape, quench, then fit. By hand (E, at the works table): cut, rivet, fit. */
    public static final int HEAT = 0, SHAPE = 1, QUENCH = 2, CUT = 3, RIVET = 4, DONE = 5;

    public static boolean byHand(ArmourSet set) {
        return set.rank == dev.awaken.progression.Rank.E;
    }

    public static int firstStage(ArmourSet set) {
        return byHand(set) ? CUT : HEAT;
    }

    // ---- heat --------------------------------------------------------------------------------------------------------

    /** The bar climbs from cold to white in this long; the gold band is where to pull it; past BURNT it comes out alone. */
    public static final int HEAT_FULL = 120;
    public static final float BAND_LO = 0.55F, BAND_HI = 0.75F, BURNT = 1.15F;

    public static float heat(float ticks) {
        return Math.max(0F, ticks / HEAT_FULL);
    }

    /** 1 in the gold, falling off either side. */
    public static float heatScore(float heat) {
        if (heat < BAND_LO) return Mth.clamp(1F - (BAND_LO - heat) / 0.3F, 0F, 1F);
        if (heat > BAND_HI) return Mth.clamp(1F - (heat - BAND_HI) / 0.25F, 0F, 1F);
        return 1F;
    }

    /** Pulled early, the strikes count for less (the metal fights the hammer). */
    public static float earlyFactor(float heat) {
        return heat >= BAND_LO ? 1F : 0.7F + 0.3F * heatScore(heat);
    }

    /** Pulled late, the piece keeps a scale flaw: it can't be Flawless. */
    public static boolean flawed(float heat) {
        return heat > BAND_HI + 0.1F;
    }

    // ---- the set's song ---------------------------------------------------------------------------------------------

    /** A rhythm: ticks a step, and a bar of steps where x is a strike and . a rest. The bar plays once to count you in. */
    public record Rhythm(int step, String bar) {
        public int beats() {
            int n = 0;
            for (int i = 0; i < bar.length(); i++) if (bar.charAt(i) == 'x') n++;
            return n;
        }

        /** Ticks from the stage's start to strike k (counting from 0): after the count-in bar, the k-th x. */
        public float due(int k) {
            int per = beats(), cycle = k / per, within = k % per, step = -1;
            for (int i = 0, seen = 0; i < bar.length(); i++) {
                if (bar.charAt(i) != 'x') continue;
                if (seen++ == within) {
                    step = i;
                    break;
                }
            }
            return (bar.length() * (1 + cycle) + step) * (float) this.step;
        }

        /** Whether the step at this many ticks is a rest (a twin's off-beat), and how far the nearest step is. */
        public boolean offBeat(float ticks) {
            int i = Math.round(ticks / step);
            return i >= bar.length() && bar.charAt(i % bar.length()) == '.';
        }

        public float offBy(float ticks) {
            return Math.abs(ticks - Math.round(ticks / step) * (float) step);
        }
    }

    /** Each set's rhythm and melody (MIDI notes; a clean strike plays the next). Slow and heavy for a Lord, quick for a Strider. */
    public record Song(Rhythm rhythm, int[] melody) {}

    private static final Map<ArmourSet, Song> SONGS = new EnumMap<>(ArmourSet.class);

    private static void song(ArmourSet s, int step, String bar, int... melody) {
        SONGS.put(s, new Song(new Rhythm(step, bar), melody));
    }

    static {
        song(ArmourSet.WILDHIDE, 4, "x.x.xx..", 69, 72, 74, 76, 74, 72, 69, 67);
        song(ArmourSet.BELLGUARD, 6, "x..x..x.x...", 60, 67, 72, 67, 64, 67, 72, 79);
        song(ArmourSet.ROOTWARDEN, 6, "x...x.x...x.", 62, 65, 69, 67, 65, 62, 60, 62);
        song(ArmourSet.AMBERWING, 4, "x.xx.x.x", 71, 74, 76, 79, 76, 74, 71, 69);
        song(ArmourSet.BEASTBOUND, 5, "x..x..x.x...", 57, 60, 62, 60, 57, 55, 57, 64);
        song(ArmourSet.CLEFTRUNNER, 3, "x.x.x.xx", 74, 76, 79, 81, 79, 76, 74, 71);
        song(ArmourSet.LANTERNLIGHT, 6, "x...x...x.x.", 64, 68, 71, 76, 71, 68, 64, 71);
        song(ArmourSet.BLADEWARD, 4, "x.x..x.xx...", 69, 72, 76, 74, 72, 69, 68, 69);
        song(ArmourSet.HORNLORD, 6, "x..x..xx....", 52, 55, 59, 57, 55, 52, 50, 52);
        song(ArmourSet.GATEWROUGHT, 4, "x.xx.x..x.x.", 66, 69, 73, 71, 69, 66, 64, 66);
        song(ArmourSet.VAULTBREAKER, 6, "x...x..x.x..", 55, 59, 62, 67, 62, 59, 55, 50);
        song(ArmourSet.FROSTWALKER, 4, "x.x.x..x", 76, 79, 83, 81, 79, 76, 74, 76);
        song(ArmourSet.SUNSHARD, 5, "x..x.x..x...", 67, 71, 74, 79, 74, 71, 67, 74);
        song(ArmourSet.SHARDCROWN, 6, "x..x.x...x..", 61, 64, 68, 66, 64, 61, 59, 61);
        song(ArmourSet.VEILWOVEN, 6, "x...x.x.", 63, 67, 70, 74, 70, 67, 63, 70);
        song(ArmourSet.DEEPCARAPACE, 6, "x..x...x.x..", 50, 53, 57, 55, 53, 50, 48, 50);
        song(ArmourSet.CLOUDRUNNER, 3, "x.x.xx.x", 77, 81, 84, 81, 79, 77, 76, 77);
        song(ArmourSet.TIDEBREAKER, 4, "x.xx..x.x...", 65, 68, 72, 70, 68, 65, 63, 65);
        song(ArmourSet.HOLLOWHEART, 7, "x...x..x....", 49, 52, 56, 54, 52, 49, 47, 49);
        song(ArmourSet.LORDSCALE, 7, "x...x...x.x.....", 48, 51, 55, 53, 51, 48, 46, 48);
        song(ArmourSet.LEVIATHAN, 7, "x..x...x.x......", 46, 50, 53, 50, 48, 46, 45, 46);
        song(ArmourSet.GALEWING, 3, "x.x.x.x.xx..", 79, 83, 86, 84, 83, 79, 78, 79);
        song(ArmourSet.HALO_CHOIR, 6, "x..x..x..xx.", 72, 76, 79, 84, 79, 76, 72, 67);
        song(ArmourSet.DREAMSTEEL, 4, "x.x..xx.x...", 70, 73, 77, 75, 73, 70, 68, 70);
        song(ArmourSet.SOVEREIGN, 5, "x..x.x..xx.x..x.", 59, 62, 66, 64, 62, 59, 57, 59);
        song(ArmourSet.STARFALL, 6, "x...x..xx...", 60, 64, 67, 72, 76, 72, 67, 64);
        song(ArmourSet.RIFTWALKER, 3, "x.xx.x.x..x.", 75, 78, 82, 80, 78, 75, 73, 75);
        song(ArmourSet.NIGHTCOURT, 4, "x..x.xx.x...", 62, 65, 69, 68, 65, 62, 61, 62);
        song(ArmourSet.SIGNALS_ECHO, 5, "x.x..x.x..x.", 73, 76, 80, 85, 80, 76, 73, 80);
    }

    public static Song song(ArmourSet set) {
        return SONGS.get(set);
    }

    /** A melody note as the bell's pitch (the bell is C5; folded into the octaves a sound can be pitched to). */
    public static float notePitch(int note) {
        while (note < 60) note += 12;
        while (note >= 84) note -= 12;
        return (float) Math.pow(2, (note - 72) / 12.0);
    }

    // ---- strikes -----------------------------------------------------------------------------------------------------

    /** Strikes a piece takes at the anvil; rivets by hand are fewer. */
    public static int strikes(ArmorType piece, boolean byHand) {
        int n = switch (piece) {
            case HELMET -> 8;
            case CHESTPLATE -> 12;
            case LEGGINGS -> 10;
            default -> 6;
        };
        return byHand ? n / 2 : n;
    }

    /** Ticks either side of a strike's beat it is taken for that beat; past it the beat is a miss. */
    public static final float WINDOW = 8F;
    /** How far a client's clock may run behind the server's (a press made in time, received a little late). */
    public static final float LAG = 10F;

    /** On the beat is 1, falling to 0 six ticks off. */
    public static float timing(float off) {
        float a = Math.abs(off);
        return a <= 1.5F ? 1F : Mth.clamp(1F - (a - 1.5F) / 4.5F, 0F, 1F);
    }

    /** Aim in mark radii from the mark's centre: inside half a radius is 1, two radii out is 0. */
    public static float aim(float radii) {
        return radii <= 0.5F ? 1F : Mth.clamp(1F - (radii - 0.5F) / 1.5F, 0F, 1F);
    }

    public static float strike(float off, float radii) {
        return 0.6F * timing(off) + 0.4F * aim(radii);
    }

    /** A clean strike: one that plays its note and moves the metal whole. */
    public static boolean clean(float score) {
        return score >= 0.8F;
    }

    /**
     * Where each strike lands on the work (x across, z along, both -1..1): the piece's outline, struck side to side so
     * the metal is drawn out evenly. The same every time for a piece, so a song can be learned.
     */
    public static float[][] marks(ArmorType piece, int n) {
        float[][] m = new float[n][2];
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5F) / n;
            int side = i % 2 == 0 ? 1 : -1;
            switch (piece) {
                case HELMET -> { // round the dome, then its crown
                    double a = Math.PI * (0.15 + 0.7 * (i / 2) / Math.max(1, (n - 1) / 2));
                    m[i][0] = side * (float) Math.sin(a) * 0.62F;
                    m[i][1] = (float) -Math.cos(a) * 0.62F;
                }
                case CHESTPLATE -> { // down the two sides of the breast, the keel last
                    int pair = i / 2, pairs = n / 2;
                    float z = -0.75F + 1.5F * pair / Math.max(1, pairs - 1);
                    float w = 0.62F - 0.22F * Math.max(0F, z);
                    m[i][0] = side * w;
                    m[i][1] = z;
                }
                case LEGGINGS -> { // two long rows, the greaves
                    m[i][0] = side * 0.45F;
                    m[i][1] = -0.8F + 1.6F * (i / 2) / Math.max(1, n / 2 - 1);
                }
                default -> { // the two sabatons, toe then heel
                    m[i][0] = side * 0.42F;
                    m[i][1] = -0.6F + 1.2F * (i / 2) / Math.max(1, n / 2 - 1);
                }
            }
            if (m[i][1] != m[i][1]) m[i][1] = t; // never NaN
        }
        return m;
    }

    /** A mark's radius on the work, in its -1..1 units. */
    public static final float MARK = 0.16F;

    // ---- quench and cut ----------------------------------------------------------------------------------------------

    /** Ticks after the quench stage starts that the cue comes (a little later each piece, so it is listened for). */
    public static int quenchCue(long seed) {
        return 24 + (int) Math.floorMod(seed, 12L);
    }

    /** Plunged within four ticks of the cue is 1; early hisses (0.35); late cools in the air. */
    public static float quench(float off) {
        if (off < 0F) return 0.35F;
        if (off <= 4F) return 1F;
        return Math.max(0.2F, 1F - (off - 4F) / 16F);
    }

    /** Past the cue by this long, the work is plunged for you. */
    public static final float QUENCH_LATE = 24F;

    // ---- quality -----------------------------------------------------------------------------------------------------

    public enum Quality {
        PLAIN("Plain", 0, 0xFFB8C0CC), FINE("Fine", 1, 0xFF9FE6D6), MASTERWORK("Masterwork", 2, 0xFFFFCF7A), FLAWLESS("Flawless", 3, 0xFFFFF2C2);

        public final String label;
        /** The Temper a piece leaves the Forge with (the Hearth's Temper goes on from there). */
        public final int temper;
        public final int colour;

        Quality(String label, int temper, int colour) {
            this.label = label;
            this.temper = temper;
            this.colour = colour;
        }

        public static Quality of(int i) {
            return values()[Mth.clamp(i, 0, 3)];
        }
    }

    /** At the Forge: heat a fifth, the strikes most (less if pulled early), the quench the rest. */
    public static float forgeScore(float heat, float strikeMean, float quench) {
        return 0.2F * heatScore(heat) + 0.65F * strikeMean * earlyFactor(heat) + 0.15F * quench;
    }

    /** By hand: the cut and the rivets. */
    public static float handScore(float cut, float rivetMean) {
        return 0.35F * cut + 0.65F * rivetMean;
    }

    public static Quality quality(float score, boolean flaw) {
        Quality q = score >= 0.9F ? Quality.FLAWLESS : score >= 0.75F ? Quality.MASTERWORK : score >= 0.55F ? Quality.FINE : Quality.PLAIN;
        return flaw && q == Quality.FLAWLESS ? Quality.MASTERWORK : q;
    }

    /** A helper at the anvil: each clean off-beat adds a little; three make the piece twin-struck. */
    public static final float TWIN_BONUS = 0.01F, TWIN_MAX = 0.05F;
    public static final int TWIN_STRIKES = 3;

    // ---- what a piece costs ------------------------------------------------------------------------------------------

    /** One thing a piece takes: a tag (the Hunter tiers' hide, core, bindings and metal) or one item. */
    public record Need(@Nullable TagKey<Item> tag, @Nullable Identifier item, int count) {}

    /** Today's Hunter recipes, moved from the crafting grid to the Forge (SPEC §44.4): helmet, chestplate, leggings, boots. */
    private static final Map<HunterTier, String[]> HUNTER_COSTS = new EnumMap<>(Map.of(
            HunterTier.E, new String[]{"hide 4, ess 1", "hide 5, bind 2, ess 1", "hide 4, bind 2, ess 1", "hide 2, bind 2, ess 1"},
            HunterTier.D, new String[]{"hide 2, core 1, metal 2, ess 1", "hide 4, core 1, metal 3, ess 1", "hide 2, core 1, metal 2, ess 2", "hide 2, core 1, metal 2, ess 1"},
            HunterTier.C, new String[]{"hide 2, core 1, metal 2, ess 1", "hide 2, core 1, metal 3, heart 1, ess 2", "hide 2, core 1, metal 2, ess 2", "hide 2, core 1, metal 2, ess 1"},
            HunterTier.B, new String[]{"core 1, metal 2, amethyst 1, ess 2", "hide 2, core 1, metal 3, heart 1, amethyst 1, ess 1", "hide 2, core 1, metal 2, amethyst 1, ess 2", "hide 2, core 1, metal 2, amethyst 1, ess 1"},
            HunterTier.A, new String[]{"hide 1, core 2, metal 2, ess 1", "hide 2, core 2, metal 3, heart 1, ess 1", "hide 2, core 1, metal 2, ess 2", "hide 2, core 1, metal 2, ess 1"},
            HunterTier.S, new String[]{"core 2, metal 2, heart 1, ess 1", "core 2, bind 2, metal 3, heart 1, ess 1", "core 2, bind 2, metal 2, ess 1", "core 2, metal 2, ess 1"}));

    /** What a piece takes, or null where the set isn't made yet (its pattern comes in a later phase). */
    public static @Nullable List<Need> cost(ArmourSet set, ArmorType piece) {
        if (set.hunter == null) return null;
        String line = HUNTER_COSTS.get(set.hunter)[slot(piece)];
        List<Need> out = new ArrayList<>();
        for (String part : line.split(", ")) {
            String[] kv = part.split(" ");
            int n = Integer.parseInt(kv[1]);
            HunterTier t = set.hunter;
            out.add(switch (kv[0]) {
                case "hide" -> new Need(t.hideTag(), null, n);
                case "core" -> new Need(t.coreTag(), null, n);
                case "bind" -> new Need(t.bindTag(), null, n);
                case "metal" -> new Need(t.metalTag(), null, n);
                case "heart" -> new Need(null, Awaken.id("lord_heart"), n);
                case "amethyst" -> new Need(null, Identifier.withDefaultNamespace("amethyst_shard"), n);
                default -> new Need(null, Awaken.id("essence_shard"), n);
            });
        }
        return out;
    }

    public static final ArmorType[] PIECES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    public static int slot(ArmorType piece) {
        return switch (piece) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            default -> 3;
        };
    }

    public static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Awaken.id(path));
    }

    /** Forging from memory (a piece once made at Masterwork): one heat and one strike, about 6 s, always Fine. */
    public static final Quality FROM_MEMORY = Quality.FINE;
}
