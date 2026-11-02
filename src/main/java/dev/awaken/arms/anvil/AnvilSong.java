package dev.awaken.arms.anvil;

import net.minecraft.util.Mth;

/**
 * The War Anvil's song (SPEC §46.2), the same on both sides: its stages, the Longsword's rhythm and marks, and how each
 * act is scored. The armour's Forge Song's rules where they meet (§44.4), so both forges agree; kept here rather than
 * read from the armour module.
 */
public final class AnvilSong {
    private AnvilSong() {}

    public static final int HEAT = 0, SHAPE = 1, QUENCH = 2, HILT = 3;

    // ---- heat --------------------------------------------------------------------------------------------------------

    /** The heat climbs from cold to white in this long; the gold band is where to pull; past BURNT it comes out alone. */
    public static final int HEAT_FULL = 120;
    public static final float BAND_LO = 0.55F, BAND_HI = 0.75F, BURNT = 1.15F;

    public static float heat(float ticks) {
        return Math.max(0F, ticks / HEAT_FULL);
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

    /** The Longsword's: two, then the finisher's pair (its chain's tempo). */
    public static final Rhythm LONGSWORD = new Rhythm(4, "x...x...x.x.....");
    /** The hilt's three taps: guard, grip, pommel. */
    public static final Rhythm HILT_RHYTHM = new Rhythm(3, "x..x..x.");
    public static final int STRIKES = 10, TAPS = 3;
    /** The strike after which the tongs flip the blade over. */
    public static final int FLIP_AFTER = 5;

    /**
     * Where each strike falls on the blade: along it (0 the guard, 1 the point) and across it (-1..1 of its half width),
     * the same every time. The first five work one face from the root out, the second five the other.
     */
    public static final float[][] MARKS = {
            {0.22F, 0.0F}, {0.48F, 0.35F}, {0.74F, -0.3F}, {0.36F, -0.4F}, {0.62F, 0.25F},
            {0.28F, 0.3F}, {0.54F, -0.35F}, {0.82F, 0.0F}, {0.42F, 0.0F}, {0.68F, -0.2F}};

    /** A mark's radius, in blocks on the metal. */
    public static final float MARK = 0.045F;

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

    /** How far the blade has been drawn out: 0 the squat bar, 1 the blade, after so many strikes. */
    public static float drawn(int strikes) {
        return Mth.clamp(strikes / (float) STRIKES, 0F, 1F);
    }

    /**
     * Where the Longsword's mesh is cut while it's made (along it, 0 the pommel, 1 the point): the blade alone until the
     * hilt, then each tap adds its part (the guard and tusks, the grip, the claw and stone).
     */
    public static final float[] HILT_CUT = {0.40F, 0.24F, 0.17F, 0F};
}
