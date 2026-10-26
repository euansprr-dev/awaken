package dev.awaken.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Diminishing returns for repeatable world XP (plan v2 §4.4, SPEC 4.3). Each source keeps a tally of what it paid
 * recently, in fractions of a level, decaying with a 10-minute time constant. Below its budget a source pays in full;
 * above it each extra bit pays {@code budget / tally}, so grinding one thing levels off at about one budget per
 * 10 minutes while varied play is never cut. Mirrors {@code Budget} in tools/sim/pacing_sim.py.
 *
 * <p>Tallies are not saved: a restart forgets ten minutes of history, which is harmless.
 */
public final class XpBudget {
    private XpBudget() {}

    public static final String KILL = "kill";
    public static final String ORE = "ore";
    public static final String BUILD = "build";
    /** Ores under y 0 and Deepglass (SPEC §28.3): their own budget, so the deep pays whatever the way down took. */
    public static final String DEEP = "deep_mining";

    /** Decay time constant: "per 10 minutes". */
    static final double TAU_TICKS = 20 * 60 * 10;
    /** World XP of out-levelled things tapers by 1 / (1 + levels above / OUTLEVEL). */
    static final double OUTLEVEL = 10;

    /** Budget per ~10 minutes, in fractions of a level. Unknown sources (other modules' keys) get the world budget. */
    public static double budget(String source) {
        Double registered = REGISTERED.get(source);
        if (registered != null) return registered;
        return switch (source) {
            case KILL -> 0.15;
            case ORE -> 0.06;
            case BUILD -> 0.03;
            case DEEP -> 0.08;
            default -> 0.25;
        };
    }

    private static final Map<String, Double> REGISTERED = new java.util.concurrent.ConcurrentHashMap<>();

    /** A module's own source and its budget per ~10 minutes, in fractions of a level (SPEC §21.3: "hunt", the crafts). */
    public static void register(String source, double perTenMinutes) {
        REGISTERED.put(source, perTenMinutes);
    }

    /** The current tally of a source (decayed to now), as a share of its budget: above 1 the source is worn. */
    public static double wear(UUID player, String source, long now) {
        return tally(player, source, now) / budget(source);
    }

    private static final Map<UUID, Map<String, double[]>> TALLY = new HashMap<>();

    /** What a raw grant (fraction of a level) from this source is worth right now; records it in the tally. */
    static double pay(UUID player, String source, double raw, long now) {
        if (raw <= 0) return 0;
        Map<String, double[]> bySource = TALLY.get(player);
        if (bySource == null) TALLY.put(player, bySource = new HashMap<>());
        double[] t = bySource.get(source);
        if (t == null) bySource.put(source, t = new double[]{0, now});
        double tally = t[0] * Math.exp(-Math.max(0, now - t[1]) / TAU_TICKS);
        double b = budget(source);
        double after = tally + raw;
        t[0] = after;
        t[1] = now;
        if (after <= b) return raw;
        // the part below the budget pays in full, the rest pays b/t along the way: the integral is b * ln(after/before)
        double full = Math.max(0, b - tally);
        return full + b * Math.log(after / Math.max(tally, b));
    }

    /** Current tally of a source (decayed to now), for tests and /awaken why. */
    static double tally(UUID player, String source, long now) {
        Map<String, double[]> bySource = TALLY.get(player);
        double[] t = bySource == null ? null : bySource.get(source);
        return t == null ? 0 : t[0] * Math.exp(-Math.max(0, now - t[1]) / TAU_TICKS);
    }

    /** Out-levelled content pays less: 1 / (1 + max(0, level - 1 - contentLevel) / 10). Vanilla mobs are content level 0. */
    public static double taper(int level, int contentLevel) {
        return 1.0 / (1.0 + Math.max(0, level - 1 - contentLevel) / OUTLEVEL);
    }

    static void forget(UUID player) {
        TALLY.remove(player);
    }

    static void forgetAll() {
        TALLY.clear();
    }
}
