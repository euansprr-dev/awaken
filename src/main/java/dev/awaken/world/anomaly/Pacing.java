package dev.awaken.world.anomaly;

import net.minecraft.util.RandomSource;

import java.util.Set;

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
     * Base weights by {@link AnomalyKind} ordinal: rift, wandering gate, procession, fallen star, shimmer grove. Days are
     * calm (rifts under a third, the peaceful kinds nearly half); nights are dangerous (rifts and falling stars with their
     * guardians). With the repeat rule and the novelty boost below, a 60-minute session of about five anomalies sees
     * 3–4 different kinds (the test {@code anomaly_pacing_and_kinds} simulates it).
     */
    static final int[] DAY = {30, 15, 20, 6, 25};
    static final int[] NIGHT = {45, 15, 0, 35, 5};

    /**
     * Weights by {@link AnomalyKind} ordinal. The first anomaly a player ever meets is a rift (the plan's "first rift tear
     * 60 m away"); processions are calm and day only; peaceful worlds get no rifts; wandering gates wait for level 3. The
     * same kind never comes twice running (a rift may, at half weight), and kinds the player has never met ({@code met}
     * holds kind ids) come twice as often, so the first hour shows what the world can do.
     */
    public static int[] weights(boolean first, boolean night, boolean peaceful, int level, String lastKind, Set<String> met) {
        int[] w = new int[AnomalyKind.values().length];
        if (first && !peaceful) {
            w[AnomalyKind.RIFT_TEAR.ordinal()] = 1;
            return w;
        }
        int[] base = night ? NIGHT : DAY;
        System.arraycopy(base, 0, w, 0, w.length);
        if (peaceful) w[AnomalyKind.RIFT_TEAR.ordinal()] = 0;
        if (level < GATE_LEVEL) w[AnomalyKind.WANDERING_GATE.ordinal()] = 0;
        AnomalyKind last = lastKind.isEmpty() ? null : AnomalyKind.byId(lastKind);
        int[] beforeRepeat = w.clone();
        if (last != null) w[last.ordinal()] = last == AnomalyKind.RIFT_TEAR ? w[last.ordinal()] / 2 : 0;
        if (total(w) == 0) w = beforeRepeat; // nothing else fits (a peaceful night): a repeat beats nothing
        for (AnomalyKind k : AnomalyKind.values()) {
            if (w[k.ordinal()] > 0 && !met.contains(k.id)) w[k.ordinal()] *= 2;
        }
        return w;
    }

    /** As above for someone who has met every kind (no novelty boost). */
    public static int[] weights(boolean first, boolean night, boolean peaceful, int level, String lastKind) {
        return weights(first, night, peaceful, level, lastKind, ALL);
    }

    private static final Set<String> ALL = java.util.Arrays.stream(AnomalyKind.values()).map(k -> k.id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    static int total(int[] w) {
        int t = 0;
        for (int v : w) t += v;
        return t;
    }

    /** A kind from the weights (by ordinal), or null when nothing fits. */
    public static AnomalyKind pick(int[] w, RandomSource r) {
        int total = total(w);
        if (total <= 0) return null;
        int roll = r.nextInt(total);
        AnomalyKind[] kinds = AnomalyKind.values();
        for (int i = 0; i < w.length && i < kinds.length; i++) {
            roll -= w[i];
            if (roll < 0) return kinds[i];
        }
        return null;
    }

    /** "rift 30 / gate 15 / ..." for the director's log. */
    static String describe(int[] w) {
        StringBuilder b = new StringBuilder();
        AnomalyKind[] kinds = AnomalyKind.values();
        for (int i = 0; i < w.length && i < kinds.length; i++) {
            if (i > 0) b.append(" / ");
            b.append(kinds[i].id).append(' ').append(w[i]);
        }
        return b.toString();
    }
}
