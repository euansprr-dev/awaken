package dev.awaken.living.combat;

/**
 * How hard a land weighs on you (SPEC §21.10, decision 11): pure rules, read by the client for the tint and the sound bed.
 * The gap is the land's level under you minus your own. Under {@link #FAINT} nothing; from there the strength climbs to a
 * little under half at {@link #STRONG} (the ember edge begins to read) and to everything at {@link #FULL}.
 */
public final class DreadRules {
    private DreadRules() {}

    public static final int FAINT = 5, STRONG = 10, FULL = 20;

    /** 0..1 for a gap in levels. */
    public static float strength(int gap) {
        if (gap < FAINT) return 0F;
        if (gap < STRONG) return 0.15F + 0.30F * (gap - FAINT) / (STRONG - FAINT);
        if (gap < FULL) return 0.45F + 0.55F * (gap - STRONG) / (FULL - STRONG);
        return 1F;
    }
}
