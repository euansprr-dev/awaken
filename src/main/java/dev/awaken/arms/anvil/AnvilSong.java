package dev.awaken.arms.anvil;

import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/**
 * The War Anvil's song (SPEC §46.2), the same on both sides: its stages, the Longsword's rhythm and marks, and how each
 * act is scored. The armour's Forge Song's rules where they meet (§44.4), so both forges agree; kept here rather than
 * read from the armour module.
 */
public final class AnvilSong {
    private AnvilSong() {}

    public static final int HEAT = 0, SHAPE = 1, QUENCH = 2, HILT = 3, KNAP = 4, BIND = 5;
    /** The ranks' acts (§46.9): A's twist before the draw out, C's grind after the quench, B's balance after the hilt, S's starlight for the water. */
    public static final int TWIST = 6, GRIND = 7, BALANCE = 8, STAR = 9;
    /**
     * The picks' own (SPEC §49.4): the eye punched through the head, the temper colours run, the head hung on its haft, the
     * tips steeled (C), the spine threaded (B), the seam poured (A); and TEND, a few quiet taps for work that needs no fire.
     */
    public static final int EYE = 10, COLOURS = 11, HANG = 12, TIPS = 13, SPINE = 14, SEAM = 15, TEND = 16;

    // ---- heat --------------------------------------------------------------------------------------------------------

    /** The heat climbs from cold to white in this long; the gold band is where to pull; past BURNT it comes out alone. */
    public static final int HEAT_FULL = 120;
    /** From memory the blank goes in where the smith knows its heat: the clock starts this far in (0.3), the gold 30 ticks off. */
    public static final float KNOWN_HEAT = 36F;
    public static final float BAND_LO = 0.55F, BAND_HI = 0.75F, BURNT = 1.15F;

    public static float heat(float ticks) {
        return Math.max(0F, ticks / HEAT_FULL);
    }

    /** With the bellows worked (§46.6) the gold band holds this many ticks longer: the heat climbs through it evenly, slower. */
    public static final float BELLOWS_HOLD = 10F;

    public static float heat(float ticks, boolean bellows) {
        float lo = BAND_LO * HEAT_FULL, band = (BAND_HI - BAND_LO) * HEAT_FULL;
        if (!bellows || ticks <= lo) return heat(ticks);
        if (ticks <= lo + band + BELLOWS_HOLD) return BAND_LO + (BAND_HI - BAND_LO) * (ticks - lo) / (band + BELLOWS_HOLD);
        return heat(ticks - BELLOWS_HOLD);
    }

    /** In the gold band is 1, falling off either side. */
    public static float heatScore(float heat) {
        if (heat < BAND_LO) return Mth.clamp(1F - (BAND_LO - heat) / 0.3F, 0F, 1F);
        if (heat > BAND_HI) return Mth.clamp(1F - (heat - BAND_HI) / 0.25F, 0F, 1F);
        return 1F;
    }

    /** Pulled early, the strikes count a little less. */
    public static float earlyFactor(float heat) {
        return heat >= BAND_LO ? 1F : 0.7F + 0.3F * heatScore(heat);
    }

    /** Left in too long, the tip burns. */
    public static boolean burnt(float heat) {
        return heat > BAND_HI + 0.1F;
    }

    // ---- the rhythm --------------------------------------------------------------------------------------------------

    /** Ticks a step and a bar of x (a beat) and . (a rest). */
    public record Rhythm(int step, String bar) {
        public int beats() {
            int n = 0;
            for (int i = 0; i < bar.length(); i++) if (bar.charAt(i) == 'x') n++;
            return n;
        }

        /** Ticks from the stage's start to strike k (from 0): after one bar counted in, the k-th x. */
        public float due(int k) {
            int per = beats(), cycle = k / per, within = k % per, at = 0;
            for (int i = 0, seen = 0; i < bar.length(); i++) {
                if (bar.charAt(i) != 'x') continue;
                if (seen++ == within) {
                    at = i;
                    break;
                }
            }
            return (bar.length() * (1 + cycle) + at) * (float) step;
        }

        /** The count-in's beats (the first bar's x), in ticks from the stage's start. */
        public float countIn(int k) {
            int seen = 0;
            for (int i = 0; i < bar.length(); i++) if (bar.charAt(i) == 'x' && seen++ == k) return i * (float) step;
            return -1F;
        }

        public int length() {
            return bar.length() * step;
        }
    }

    /** The hilt's three taps: guard, grip, pommel. */
    public static final Rhythm HILT_RHYTHM = new Rhythm(3, "x..x..x.");
    public static final int TAPS = 3;

    /**
     * Where each strike falls on the blade: along it (0 the guard, 1 the point) and across it (-1..1 of its half width),
     * the same every time. The first five work one face from the root out, the second five the other.
     */
    public static final float[][] MARKS = {
            {0.22F, 0.0F}, {0.48F, 0.35F}, {0.74F, -0.3F}, {0.36F, -0.4F}, {0.62F, 0.25F},
            {0.28F, 0.3F}, {0.54F, -0.35F}, {0.82F, 0.0F}, {0.42F, 0.0F}, {0.68F, -0.2F}};

    /** A mark's radius, in blocks on the metal. */
    public static final float MARK = 0.045F;

    /** Across the blade for the marks laid by rule (every style but the Longsword's ten). */
    private static final float[] ACROSS = {0F, 0.35F, -0.3F, -0.4F, 0.25F, 0.3F, -0.35F};

    /**
     * Each style's song at the anvil (SPEC §46.7): its rhythm (from its chain's tempo), how many strikes draw it out
     * (flipped after half), where each falls on the blade, its true note (§46.10: semitones from the ring's A, a Maul
     * low, Daggers high), and its rhythm from memory (the bar's first half: the anvil counts in half a bar, §46.10).
     */
    public record Song(Rhythm rhythm, int strikes, float[][] marks, int note, Rhythm known) {
        Song(int step, String bar, int strikes, int note) {
            this(new Rhythm(step, bar), strikes, layMarks(strikes), note, new Rhythm(step, bar.substring(0, bar.length() / 2)));
        }

        public int flipAfter() {
            return strikes / 2;
        }

        public float[] mark(int k) {
            return marks[Mth.clamp(k, 0, marks.length - 1)];
        }
    }

    /** The Longsword's ten, else the first half on one face and the second on the other, spread along 0.22-0.82. */
    private static float[][] layMarks(int n) {
        if (n == MARKS.length) return MARKS;
        float[][] out = new float[n][];
        int half = (n + 1) / 2;
        for (int k = 0; k < n; k++) {
            int j = k < half ? k : k - half, m = k < half ? half : n - half;
            int step = m % 3 != 0 ? 3 : m % 5 != 0 ? 5 : 7;
            out[k] = new float[]{0.22F + 0.6F * (m <= 1 ? 0.5F : (j * step % m) / (float) (m - 1)), ACROSS[k % ACROSS.length]};
        }
        return out;
    }

    public static final Map<String, Song> SONGS = Map.ofEntries(
            Map.entry("longsword", new Song(4, "x...x...x.x.....", 10, 0)),
            Map.entry("axe", new Song(5, "x.....x.....x...", 8, -5)),
            Map.entry("maul", new Song(4, "x.......x.......", 8, -9)),
            Map.entry("spear", new Song(4, "x..x...x........", 10, 3)),
            Map.entry("greatblade", new Song(5, "x.....x.......x.", 8, -7)),
            Map.entry("rapier", new Song(3, "x..x.x..x..x.x..", 12, 7)),
            Map.entry("daggers", new Song(3, "x.x.x.xx", 14, 10)),
            Map.entry("katana", new Song(4, "x.......xx.x....", 10, 3)),
            Map.entry("twinblades", new Song(3, "x.xx.x.xx.......", 12, 5)),
            Map.entry("glaive", new Song(5, "x....x....xx....", 10, -2)),
            Map.entry("scythe", new Song(5, "x......x.x......", 8, -2)),
            Map.entry("gauntlets", new Song(3, "x.x.x.x.xx......", 14, 5)),
            Map.entry("chainblade", new Song(4, "x..x....x..x.x..", 10, 0)),
            Map.entry("shieldblade", new Song(4, "x...x.x.....x...", 10, 0)),
            Map.entry("warstaff", new Song(4, "x.x...x.x...x...", 10, -5)),
            Map.entry("precursor", new Song(4, "x...x...x...x...", 10, 0)),
            // the picks (§49.4): each form's beat, its head worked either side of the eye. Pitches stay within an octave (Minecraft clamps past it)
            Map.entry("pick_delver", pick(3, "x.x.x...", 9, -2)),
            Map.entry("pick_breaker", pick(6, "x.....x.x.......", 6, -9)),
            Map.entry("pick_mattock", pick(4, "x..x..x.....", 9, -7)),
            Map.entry("pick_sounder", pick(3, "x.x.x...x.x.x...", 12, 10)));

    /** A pick's song: strikes go out along each arm of the head in turn and never on the eye (0.42-0.58 along). */
    private static Song pick(int step, String bar, int strikes, int note) {
        float[][] marks = new float[strikes][];
        int perArm = (strikes + 1) / 2;
        for (int k = 0; k < strikes; k++) {
            int j = k / 2;
            float out = 0.12F + 0.32F * (perArm <= 1 ? 0.5F : (j * 2 % perArm) / (float) (perArm - 1));
            marks[k] = new float[]{0.5F + (k % 2 == 0 ? out : -out), ACROSS[k % ACROSS.length] * 0.6F};
        }
        return new Song(new Rhythm(step, bar), strikes, marks, note, new Rhythm(step, bar.substring(0, bar.length() / 2)));
    }

    // ---- the picks' acts (§49.4) --------------------------------------------------------------------------------------

    /** The eye: three blows a face, the head turned over between. */
    public static final Rhythm EYE_RHYTHM = new Rhythm(4, "x...x...x...");
    public static final int EYES = 6;
    /** The tips: one aimed blow on each, hard steel, slow. */
    public static final Rhythm TIPS_RHYTHM = new Rhythm(4, "x.......x.......");
    public static final int TIP_BLOWS = 2;
    /** The head rapped down the haft: three, each higher as the wood takes it. */
    public static final Rhythm HANG_RHYTHM = new Rhythm(3, "x..x..x.");
    public static final int HANGS = 3;
    /** Quiet work at the anvil with no fire (working a gift in, dressing, an old pick remade). */
    public static final Rhythm TEND_RHYTHM = new Rhythm(6, "x.x.x...");
    public static final int TEND_BLOWS = 3, MIGRATE_BLOWS = 5;
    /** The spine's thread, samples of the line as the grind's. */
    public static final int SPINE_SAMPLES = 12;

    /** The colours run: an oxide front climbing as the heat is held, from nothing to all the way at this rate. */
    public static final float COLOUR_RATE = 0.05F, COLOUR_END = 2.6F;

    /** Where the oxide has got to after the head has been held in the heat this long. */
    /** The seam's pour, in ticks: as long as it's held, up to this, the molten line runs on from the eye to the points. */
    public static final float SEAM_FULL = 32F;

    public static float colourFront(float held) {
        return Mth.clamp(held * COLOUR_RATE, 0F, COLOUR_END);
    }

    /** The colour it stopped at: 0 straw, 0.25 bronze, 0.5 peacock, 0.75 and past blue. Held too short it's still straw; nothing fails. */
    public static float colour(float held) {
        return Mth.clamp((colourFront(held) - 1F) / 1.6F, 0F, 1F);
    }

    /** Ticks to hold for all the way, and how long it waits for you before going blue by itself. */
    public static final float COLOUR_FULL = COLOUR_END / COLOUR_RATE, COLOUR_LATE = 160F, COLOUR_BLUE = 0.75F;

    /** A mark for the pick's acts: along the head, then across. The eye's are its middle; the tips' each end. */
    public static float[] pickMark(int stage, int k) {
        return stage == TIPS ? new float[]{k == 0 ? 0.9F : 0.1F, 0F} : new float[]{0.5F, 0F};
    }

    /** A style's song; anything else (a Hunter blade forged alone) the Longsword's. */
    public static Song song(String style) {
        Song s = SONGS.get(style);
        return s != null ? s : SONGS.get("longsword");
    }

    /** How far flat (semitones) the blade rings at the first clean strike; each clean strike rings it a step nearer true. */
    public static final float FLAT = 3F;

    /** The blade's ring after {@code clean} clean strikes of {@code of}: from FLAT under its true note up to it (§46.10). */
    public static float ringPitch(Song song, int clean, int of) {
        float flat = FLAT * (1F - Math.min(1F, clean / (float) Math.max(1, of)));
        return (float) Math.pow(2.0, (song.note() - flat) / 12.0);
    }

    /** The style's true note, as a pitch of the ring's A: the draw from the stone rings it whole. */
    public static float truePitch(Song song) {
        return (float) Math.pow(2.0, song.note() / 12.0);
    }

    /** How many of the first {@code upTo} scores are clean strikes. */
    public static int cleanOf(List<Float> scores, int upTo) {
        int n = 0;
        for (int k = 0; k < Math.min(upTo, scores.size()); k++) if (clean(scores.get(k))) n++;
        return n;
    }

    // ---- strikes -----------------------------------------------------------------------------------------------------

    /** Ticks either side of a beat a strike is taken for it; past it the beat is a miss. */
    public static final float WINDOW = 8F;
    /** How far a client's clock may run behind the server's. */
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

    /** A clean strike: it rings, and moves the metal whole. */
    public static boolean clean(float score) {
        return score >= 0.8F;
    }

    // ---- quench ------------------------------------------------------------------------------------------------------

    /** The dark line climbs from the point a fortieth of the blade a tick; the gold notch is near the guard. */
    public static final float LINE_RATE = 1F / 40F, NOTCH = 0.8F;
    /** Held to the top, it lifts itself; never plunged in this long, it cools in the air. */
    public static final float HOLD_MAX = 40F, PLUNGE_LATE = 160F;

    public static float idealHold() {
        return NOTCH / LINE_RATE;
    }

    /** How far up the blade the cooled line is after a hold of this long (0..1). */
    public static float line(float held) {
        return Mth.clamp(held * LINE_RATE, 0F, 1F);
    }

    /** Lifted within two ticks of the notch is 1; early is soft (to 0.35); late is brittle (to 0.2). */
    public static float quench(float held) {
        float off = held - idealHold();
        if (Math.abs(off) <= 2F) return 1F;
        if (off < 0F) return Mth.clamp(1F - (-off - 2F) / 10F * 0.65F, 0.35F, 1F);
        return Mth.clamp(1F - (off - 2F) / 8F * 0.8F, 0.2F, 1F);
    }

    /** Lifted past the notch's two ticks: a hairline. */
    public static boolean brittle(float held) {
        return held - idealHold() > 2F;
    }

    /** Cooled in the air, never plunged. */
    public static final float AIR = 0.35F;

    // ---- by hand (E): knap and bind ------------------------------------------------------------------------------------

    /** The flake points down the bone or flint edge, struck one by one at your own pace (no less than KNAP_GAP apart). */
    public static final int FLAKES = 8;
    public static final float KNAP_GAP = 6F;
    /** Each flake point along the blade (0 its root, 1 its point) and across it (-1..1): down one edge, then the other. */
    public static final float[][] FLAKE_MARKS = {
            {0.2F, 0.75F}, {0.42F, 0.8F}, {0.64F, 0.75F}, {0.86F, 0.55F}, {0.8F, -0.6F}, {0.58F, -0.8F}, {0.36F, -0.8F}, {0.16F, -0.75F}};

    /** A flake scores its aim alone (the strike's aim curve). */
    public static float knap(float radii) {
        return aim(radii);
    }

    /** The cord: four turns of the mouse round the grip, as evenly as you can. A hold this short for the four scores less. */
    public static final int TURNS = 4;
    public static final float BIND_MIN = 24F;

    /** How even the turns were: each turn's time against their mean, within an eighth is 1, half off is 0. */
    public static float evenness(float[] turns, int n) {
        if (n <= 0) return 0F;
        float mean = 0F;
        for (int i = 0; i < n; i++) mean += turns[i];
        mean /= n;
        if (mean <= 0F) return 0F;
        float score = 0F;
        for (int i = 0; i < n; i++) score += Mth.clamp(1F - (Math.abs(turns[i] - mean) / mean - 0.125F) / 0.375F, 0F, 1F);
        return score / n;
    }

    /** What the server takes of a bind: its evenness, times the turns wound of four, less for a hold too quick to be real. */
    public static float bind(float evenness, int turns, float held) {
        return Mth.clamp(evenness, 0F, 1F) * Mth.clamp(turns / (float) TURNS, 0F, 1F) * Mth.clamp(held / BIND_MIN, 0F, 1F);
    }

    /** By hand: 0.6 knap + 0.4 bind. */
    public static float handScore(float knap, float bind) {
        return 0.6F * knap + 0.4F * bind;
    }

    // ---- the ranks' acts (§46.9) -----------------------------------------------------------------------------------------

    /** A, the twist: the bar clamped in the anvil, three even turns of the mouse; a hold this short for the three scores less. */
    public static final int TWISTS = 3;
    public static final float TWIST_MIN = 30F;

    /** A twist this good shows its ripples in the finished steel (cosmetic only). */
    public static final float RIPPLES = 0.5F;

    public static float twist(float evenness, int turns, float held) {
        return Mth.clamp(evenness, 0F, 1F) * Mth.clamp(turns / (float) TWISTS, 0F, 1F) * Mth.clamp(held / TWIST_MIN, 0F, 1F);
    }

    /**
     * C, the grind: two passes on the wheel, one an edge, the wheel turning while use is held. Every GRIND_GAP ticks of it
     * the client says how far the edge is off the line, in the band's half-widths; PASS of them make a pass.
     */
    public static final int PASS = 10, GRIND_SAMPLES = 2 * PASS;
    public static final float GRIND_GAP = 5F;

    /** Where the line is across the band (-1..1) at t ticks into the act: it drifts, slowly, two waves at once (both sides the same). */
    public static float grindLine(float t) {
        return 0.55F * Mth.sin(t * 0.045F) + 0.25F * Mth.sin(t * 0.11F + 1.3F);
    }

    /** On the line is 1; a half-width off is 0.5; a full one, nothing. */
    public static float grind(float off) {
        return Mth.clamp(1F - Math.abs(off), 0F, 1F);
    }

    /**
     * B, the balance: the blade across the horn tips toward its heavy end; the Deepglass weight slides along it (0 the
     * guard, 1 the pommel's end) and it settles level at its point, the same on both sides for this work (read from the
     * stage's start). Settled in BALANCE_QUICK is whole, by BALANCE_SLOW half; left BALANCE_ALONE it settles itself.
     */
    public static final float BALANCE_QUICK = 60F, BALANCE_SLOW = 200F, BALANCE_ALONE = 240F, LEVEL = 0.06F;

    public static float balancePoint(long stageStart) {
        long h = stageStart * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        return 0.25F + 0.5F * ((h >>> 40) & 0xFFFF) / 65535F;
    }

    /** How quickly it settled, and how near the point the weight was (a hair off still settles, slowly). */
    public static float balance(float ticks, float off) {
        float quick = ticks <= BALANCE_QUICK ? 1F : Mth.clamp(1F - 0.5F * (ticks - BALANCE_QUICK) / (BALANCE_SLOW - BALANCE_QUICK), 0.3F, 1F);
        return quick * Mth.clamp(1F - Math.max(0F, Math.abs(off) - LEVEL) / 0.2F, 0.5F, 1F);
    }

    /** S, the starlight: the light's ring closes on the point STAR_AT ticks after the column comes down; press as it does. */
    public static final float STAR_AT = 50F, STAR_ALONE = 110F;

    public static float star(float off) {
        float a = Math.abs(off);
        return a <= 3F ? 1F : Mth.clamp(1F - (a - 3F) / 17F, 0.3F, 1F);
    }

    /** The ranks' acts (twist, grind, balance, starlight; -1 where not done) each take a tenth of the score (the armour's rule, §44.4). */
    public static float withActs(float score, float... acts) {
        int k = 0;
        float sum = 0F;
        for (float a : acts) {
            if (a < 0F) continue;
            k++;
            sum += Mth.clamp(a, 0F, 1F);
        }
        return score * (1F - 0.1F * k) + 0.1F * sum;
    }

    // ---- a helper ----------------------------------------------------------------------------------------------------

    /** Anyone else's strike on an off-beat (this far from any beat, this far apart): each adds a little; three make it twin-struck. */
    public static final float HELP_OFF = 2F, HELP_GAP = 6F;
    public static final float TWIN_BONUS = 0.01F, TWIN_MAX = 0.05F;
    public static final int TWIN_STRIKES = 3;

    public static float helped(float score, int helps) {
        return score + Math.min(TWIN_MAX, helps * TWIN_BONUS);
    }

    // ---- quality -----------------------------------------------------------------------------------------------------

    public enum Quality {
        PLAIN("Plain", 0, 0xFFB8C0CC), FINE("Fine", 1, 0xFF9FE6D6), MASTERWORK("Masterwork", 2, 0xFFFFCF7A), FLAWLESS("Flawless", 3, 0xFFFFF2C2);

        public final String label;
        /** The Temper a weapon leaves the anvil with, before the Forge's cap. */
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

    public static final int NO_FLAW = 0, BURNT_TIP = 1, BRITTLE = 2;

    /** Heat a fifth, the strikes most (less if pulled early), the quench and the hilt the rest. */
    public static float score(float heat, float strikeMean, float quench, float hilt) {
        return 0.2F * heatScore(heat) + 0.55F * strikeMean * earlyFactor(heat) + 0.15F * quench + 0.1F * hilt;
    }

    /** A reforging (§46.8) is judged as a forging without the hilt: the heat, the strikes and the quench, over their 0.9. */
    public static float reforgeScore(float heat, float strikeMean, float quench) {
        return (score(heat, strikeMean, quench, 0F)) / 0.9F;
    }

    public static Quality quality(float score, boolean flaw) {
        Quality q = score >= 0.9F ? Quality.FLAWLESS : score >= 0.75F ? Quality.MASTERWORK : score >= 0.55F ? Quality.FINE : Quality.PLAIN;
        return flaw && q == Quality.FLAWLESS ? Quality.MASTERWORK : q;
    }

    /** The Temper it leaves with: its quality's, then Oren's Mark's levels, all held to the Forge's limit. */
    public static int temper(Quality q, int mark, int cap) {
        return Math.max(0, Math.min(cap, q.temper + Math.max(0, mark)));
    }

    // ---- the draw ----------------------------------------------------------------------------------------------------

    /** Hold use this long to draw it out of the stone. */
    public static final int DRAW_HOLD = 30;

    // ---- how the work looks ------------------------------------------------------------------------------------------

    /** How far the blade has been drawn out: 0 the squat bar, 1 the blade, after so many strikes of all it takes. */
    public static float drawn(int strikes, int of) {
        return Mth.clamp(strikes / (float) Math.max(1, of), 0F, 1F);
    }

    /**
     * Where a weapon's mesh is cut while it's made (along it, 0 the pommel, 1 the point): the blade alone until the hilt,
     * then each tap adds its part (the guard, the grip, the pommel). The swords' own are ArmsHilts.CUTS; this is for the
     * rest, first set by hand for the Longsword.
     */
    public static final float[] HILT_CUT = {0.40F, 0.24F, 0.17F, 0F};
}
