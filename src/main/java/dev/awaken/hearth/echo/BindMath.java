package dev.awaken.hearth.echo;

import dev.awaken.progression.Rank;

/**
 * The numbers behind binding (pure, tested). Chance comes from what fell, your Spirit and the rank gap; the cap from
 * Spirit (SPEC section 3: Echo cap 3 + SPI/6) plus one per role pad at home, since Echoes on duty don't march.
 */
public final class BindMath {
    private BindMath() {}

    public static final String SHADOWBINDER = "awaken:shadowbinder";
    public static final double MIN_CHANCE = 0.05;
    public static final double MAX_CHANCE = 0.95;
    /** Most Echo bodies in the field at once (SPEC: Echoes past 12 merge; we simply don't call more). */
    public static final int MAX_FIELD_BODIES = 12;

    public static double baseChance(String origin) {
        return switch (origin) {
            case "BOSS" -> 0.20;
            case "WARDEN" -> 0.35;
            default -> 0.55;
        };
    }

    /**
     * @param origin     ELITE, WARDEN or BOSS
     * @param spi        the player's Spirit
     * @param playerRank rank ordinal
     * @param mobRank    rank ordinal of the fallen (from its content level)
     * @param perk       {@code Perks.BIND_CHANCE} total
     */
    public static double chance(String origin, int spi, int playerRank, int mobRank, double perk) {
        int gap = playerRank - mobRank;
        double c = baseChance(origin) + (spi - 5) * 0.01 + (gap >= 0 ? 0.10 * gap : 0.15 * gap) + perk;
        return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, c));
    }

    /** Bosses only answer an S-rank (or higher) or a Shadowbinder. */
    public static boolean bossAllowed(int playerRank, String path) {
        return playerRank >= Rank.S.ordinal() || SHADOWBINDER.equals(path);
    }

    /** Echoes that may march with you. */
    public static int fieldCap(int spi) {
        return 3 + Math.max(0, spi) / 6;
    }

    /** Echoes you may hold in total: the field cap plus one per role pad at your Hearth. */
    public static int totalCap(int spi, int pads) {
        return fieldCap(spi) + Math.max(0, pads);
    }

    /** The rank a mob of this content level belongs to: the highest rank whose gate level it reaches. */
    public static int rankOfLevel(int contentLevel) {
        int r = 0;
        for (Rank rank : Rank.values()) if (contentLevel >= rank.contentLevel) r = rank.ordinal();
        return r;
    }
}
