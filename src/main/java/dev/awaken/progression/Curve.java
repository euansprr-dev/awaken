package dev.awaken.progression;

/**
 * The pacing maths. Mirrors tools/sim/pacing_sim.py exactly; if you change a number here, re-run the simulator
 * and update SPEC section 2.
 */
public final class Curve {
    private Curve() {}

    public static final int POINTS_PER_LEVEL = 3;
    public static final double FLOOR_GROWTH = 1.4;
    public static final double KILL_SHARE = 0.4;
    public static final double BELOW_RANK_FACTOR = 0.3;

    /** XP needed to go from level L to L+1. */
    public static double xpToNext(int level) {
        return 90.0 * Math.pow(Math.max(1, level), 1.45) + 60.0;
    }

    /**
     * Gate floors pay this share of their v2 reward since the Living Wilds made the open world pay (SPEC §21.3); gate
     * loot, cores and hearts are unchanged. 0.94 since SPEC §27.13: floors make back what the Regimen and the daily
     * Directives paid. Mirrors {@code WILDS_TRIM} in tools/sim/pacing_sim.py.
     */
    public static final double WILDS_TRIM = 0.94;

    /** Total XP one cleared floor of a gate of this rank pays (before the story-length multiplier). */
    public static double floorReward(Rank gateRank, int floor) {
        return gateRank.share * WILDS_TRIM * xpToNext(gateRank.contentLevel) * Math.pow(FLOOR_GROWTH, floor - 1);
    }

    /** Mob health multiplier at content level lv (SPEC section 2). */
    public static double mobHealth(int lv) {
        return 1.0 + 0.075 * lv;
    }

    public static double mobDamage(int lv) {
        return 1.0 + 0.04 * lv;
    }

    public static double mobArmor(int lv) {
        return lv / 12.0;
    }

    public static int floorsFor(Rank gateRank, boolean extra) {
        return Math.min(6, 3 + gateRank.ordinal() / 2 + (extra ? 1 : 0));
    }

    public static int breakDays(Rank gateRank, boolean extra) {
        return Math.max(1, 4 - gateRank.ordinal() / 2 + (extra ? 1 : 0));
    }

    public static int modifierBudget(Rank gateRank) {
        return Math.min(6, gateRank.ordinal() + 1);
    }

    public static int modifierMax(Rank gateRank) {
        return switch (gateRank) {
            case E, D -> 1;
            case C, B -> 2;
            default -> 3;
        };
    }
}
