package dev.awaken.progression;

/**
 * Standard counter ids. Modules may add their own ids freely (namespaced "awaken:..."); list the important ones here
 * so content authors can find them. Streak counters are reset by the module that owns them.
 */
public final class Counters {
    private Counters() {}

    // Pseudo counters used to index level/rank/stat conditions.
    public static final String LEVEL = "awaken:level";
    public static final String RANK = "awaken:rank";
    public static final String STATS = "awaken:stats";

    // Core activity
    public static final String KILLS = "awaken:kills";
    public static final String KILLS_UNARMED = "awaken:kills_unarmed";
    public static final String KILLS_OVERWORLD = "awaken:kills_overworld";
    public static final String DEATHS = "awaken:deaths";
    public static final String ORES_MINED = "awaken:ores_mined";
    public static final String BLOCKS_PLACED = "awaken:blocks_placed";
    public static final String SPRINT_CM = "awaken:sprint_cm";
    public static final String FALL_SURVIVED_MAX = "awaken:fall_survived_max";
    public static final String WOLVES_TAMED = "awaken:wolves_tamed";
    public static final String SECRETS_FOUND = "awaken:secrets_found";
    public static final String DAMAGE_TAKEN = "awaken:damage_taken";

    // Gates (owned by gates)
    public static final String GATES_CLEARED = "awaken:gates_cleared";
    public static final String FLOORS_CLEARED = "awaken:floors_cleared";
    public static final String FLOOR_STREAK_NO_EARLY_EXTRACT = "awaken:floor_streak_no_early_extract";
    public static final String BOSS_FLOORS_NO_DAMAGE = "awaken:boss_floors_no_damage";
    public static final String GATES_NO_HIT = "awaken:gates_no_hit";
    public static final String BREAKS_HELD_AT_HEARTH = "awaken:breaks_held_at_hearth";
    public static final String TWISTS_SEEN = "awaken:twists_seen";

    // Powers (owned by powers): one counter per family "awaken:power_uses/<family>", plus:
    public static final String FLOW_TICKS = "awaken:flow_ticks";
    public static final String DASHES = "awaken:dashes";
    public static final String DASH_CHAIN_AIRBORNE_BEST = "awaken:dash_chain_airborne_best";
    public static final String BLOCKS_PLACED_IN_COMBAT = "awaken:blocks_placed_in_combat";
    public static final String FORCE_INTERACTIONS = "awaken:force_interactions";

    // Spirit (veil / lucidium)
    public static final String VEIL_TICKS = "awaken:veil_ticks";
    public static final String MEDITATE_TICKS = "awaken:meditate_ticks";
    public static final String LUCID_DREAMS = "awaken:lucid_dreams";
    public static final String DREAMS = "awaken:dreams";
    public static final String REALITY_CHECKS = "awaken:reality_checks";
    public static final String NIGHTMARES_BEATEN = "awaken:nightmares_beaten";
    public static final String SPIRITS_SPOKEN = "awaken:spirits_spoken";

    // Hearth / Echoes / Above / Descent
    public static final String ECHOES_BOUND = "awaken:echoes_bound";
    public static final String MACHINES_BUILT = "awaken:machines_built";
    public static final String MACHINES_SCANNED = "awaken:machines_scanned";
    public static final String DEPTH_RECORD = "awaken:depth_record";
    public static final String TRAINING_BEST_LAPS = "awaken:training_laps";
    public static final String DIRECTIVES_KEPT_STREAK = "awaken:directives_kept_streak";

    // Teaching (progression/Lessons): "awaken:lesson/<lesson>" = steps done, "awaken:lesson/<lesson>/<step>" = its card
    // (1 sent, 2 yes, 3 no); "awaken:goal/<level-road step>" = that step's goal met.
    public static final String LESSON_PREFIX = "awaken:lesson/";
    public static final String GOAL_PREFIX = "awaken:goal/";
}
