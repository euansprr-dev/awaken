package dev.awaken.core.api;

/**
 * The fair-damage rule's numbers (SPEC §47.14): what a fight may take from a player who belongs in it. The reference is
 * the weakest player who belongs: the rank's first level, a sixth of every level's three points in VIT, the rank's
 * Hunter set, Normal. Every source is tuned against that player's health at the fight's content level, so it grows with
 * the rank and a player under the rank takes a larger share (a rank gap may be deadly; E walking into S should be).
 */
public final class FairShare {
    private FairShare() {}

    /** The reference player's max health at a content level: 20, plus 0.6 a VIT point at half a point a level. */
    public static float health(int contentLevel) {
        return 20.0F + 0.3F * Math.max(0, contentLevel - 1);
    }

    /** A Volatile burst's damage at its very centre, before armour, as a share of {@link #health} (none at its reach). */
    public static final float BURST_PEAK = 0.40F;
    /** An elite's burst: this much harder, and its reach this much wider. */
    public static final float BURST_ELITE_PEAK = 1.15F, BURST_ELITE_REACH = 1.3F;
    /** A Volatile body bursts this long after it falls (1.25 s): its reach is drawn on the ground, filling to the blast. */
    public static final int BURST_FUSE = 25;

    /** A burst's peak damage (before armour) for a body at this content level. */
    public static float burstPeak(int contentLevel, boolean elite) {
        return BURST_PEAK * health(contentLevel) * (elite ? BURST_ELITE_PEAK : 1.0F);
    }
}
