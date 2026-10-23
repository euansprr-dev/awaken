package dev.awaken.powers;

/**
 * Pure power maths (no game state), shared by server, client HUD and GameTests.
 * Energy: pool 100 + 5*INT, regen 4/s + 0.08*INT/s, paused 1 s after spending (SPEC section 3).
 * Damage: base x (1 + 0.075*level) so a power keeps the same hits-to-kill against on-level mobs, whose health grows
 * by the same 7.5% per content level (SPEC section 2); then x (1 + 1.5% per point of the scaling stat above 5),
 * x (1 + 12% per power level above 1), x variant.
 */
public final class PowerMath {
    private PowerMath() {}

    public static final int REGEN_PAUSE_TICKS = 20;
    public static final double MAX_COOLDOWN_REDUCTION = 0.5;
    /** No single power hit takes more than this share of a boss's max health. */
    public static final float BOSS_HIT_CAP = 0.2f;

    public static float maxEnergy(int intStat) {
        return 100f + 5f * intStat;
    }

    /** Energy per second. regenPerk is the {@code awaken:energy_regen} multiplier (0.2 = +20%). */
    public static float regenPerSecond(int intStat, double regenPerk) {
        return (float) ((4.0 + 0.08 * intStat) * (1.0 + regenPerk));
    }

    public static float regenPerTick(int intStat, double regenPerk) {
        return regenPerSecond(intStat, regenPerk) / 20f;
    }

    /** Cooldown in ticks after the {@code awaken:cooldown_reduction} perk (capped at 50%), variant and power level. */
    public static int cooldown(int base, float variantMul, int powerLevel, double cdrPerk) {
        double cdr = Math.max(0, Math.min(MAX_COOLDOWN_REDUCTION, cdrPerk));
        double levelMul = 1.0 - 0.04 * (Math.max(1, powerLevel) - 1);
        return Math.max(1, (int) Math.round(base * variantMul * levelMul * (1.0 - cdr)));
    }

    public static float cost(float base, float variantMul) {
        return base * variantMul;
    }

    public static double levelScale(int playerLevel) {
        return 1.0 + 0.075 * Math.max(1, playerLevel);
    }

    public static double statScale(int stat) {
        return 1.0 + 0.015 * Math.max(0, stat - 5);
    }

    public static double powerLevelScale(int powerLevel) {
        return 1.0 + 0.12 * (Math.max(1, powerLevel) - 1);
    }

    public static float damage(float base, int playerLevel, int stat, int powerLevel, float variantMul) {
        return (float) (base * levelScale(playerLevel) * statScale(stat) * powerLevelScale(powerLevel) * variantMul);
    }

    /** Flow length in ticks: 4 s + 0.05 s per AGI point, times (1 + flow_duration perk), times variant. */
    public static int flowTicks(int agi, double flowPerk, float variantMul) {
        return (int) Math.round((80 + agi) * (1.0 + flowPerk) * variantMul);
    }

    public static float capBossHit(float amount, float maxHealth) {
        return Math.min(amount, maxHealth * BOSS_HIT_CAP);
    }
}
