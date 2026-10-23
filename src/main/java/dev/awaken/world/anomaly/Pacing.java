package dev.awaken.world.anomaly;

import net.minecraft.util.RandomSource;

/**
 * The anomaly director's numbers (pure, tested): how long between events, how night speeds them up, which kind comes.
 * tools/sim/pacing_sim.py assumes an anomaly every ~9 outdoor minutes; 6–12 minutes by day averages 9, and night
 * runs the clock 1.5× faster (4–8 minutes).
 */
public final class Pacing {
    private Pacing() {}

    public static final int MINUTE = 20 * 60;
    /** No anomaly in the first two minutes after awakening (the first-power cards, the first steps). */
    public static final int GRACE = 2 * MINUTE;
    /** The first one after awakening comes a little sooner: the plan wants a rift in the first 8–20 minutes. */
    public static final int FIRST_MIN = 3 * MINUTE, FIRST_MAX = 6 * MINUTE;
    public static final int NEXT_MIN = 6 * MINUTE, NEXT_MAX = 12 * MINUTE;
    /** When no valid spot is found the event is skipped quietly and tried again a minute later. */
    public static final int RETRY = MINUTE;
    public static final double NIGHT_RATE = 1.5;

    public static double rate(boolean night) {
        return night ? NIGHT_RATE : 1.0;
    }

    public static int firstDelay(RandomSource r) {
        return FIRST_MIN + r.nextInt(FIRST_MAX - FIRST_MIN + 1);
    }

    public static int nextDelay(RandomSource r) {
        return NEXT_MIN + r.nextInt(NEXT_MAX - NEXT_MIN + 1);
    }

    /** The countdown after {@code ticks} of eligible outdoor play. */
    public static double step(double timer, int ticks, boolean night) {
        return timer - ticks * rate(night);
    }

    // ---- which kind ---------------------------------------------------------------------------------------------------

    /** Wandering gates wait until level 3: Accept already makes you level 2, and the Bell Crypt comes first. */
    public static final int GATE_LEVEL = 3;

    /**
     * Weights {rift, wandering gate, procession}. The first anomaly a player ever meets is a rift (the plan's "first rift
     * tear 60 m away"); processions are calm and day only; peaceful worlds get no rifts; the calm event never comes
     * twice in a row.
     */
    public static int[] weights(boolean first, boolean night, boolean peaceful, int level, String lastKind) {
        if (first && !peaceful) return new int[] {1, 0, 0};
        int rift = peaceful ? 0 : night ? 80 : 55;
        int gate = level >= GATE_LEVEL ? (night ? 20 : 15) : 0;
        int calm = night ? 0 : AnomalyKind.SPIRIT_PROCESSION.id.equals(lastKind) ? 0 : 30;
        if (rift + gate + calm == 0) calm = night ? 0 : 30;
        return new int[] {rift, gate, calm};
    }

    /** A kind from the weights, or null when nothing fits (a peaceful night). */
    public static AnomalyKind pick(int[] w, RandomSource r) {
        int total = w[0] + w[1] + w[2];
        if (total <= 0) return null;
        int roll = r.nextInt(total);
        if (roll < w[0]) return AnomalyKind.RIFT_TEAR;
        if (roll < w[0] + w[1]) return AnomalyKind.WANDERING_GATE;
        return AnomalyKind.SPIRIT_PROCESSION;
    }
}
