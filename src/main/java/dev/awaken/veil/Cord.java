package dev.awaken.veil;

import net.minecraft.util.Mth;

/**
 * The numbers of the Veil (SPEC sections 3 and 9). Pure functions so they can be tested and shown on the Status screen.
 * <pre>
 * cord radius  = 32 + 6 x SPI + Perks.CORD_LENGTH          (cap 1024 blocks)
 * Veil time    = 30 s + 3 s x SPI
 * Awareness    = 16 + 2 x PER + SPI + Perks.SECRET_GLOW_RANGE (cap 160 blocks): the spirit's render distance
 * secret sense = 4 + PER / 4 + Perks.SECRET_GLOW_RANGE     (awake, not projecting)
 * </pre>
 */
public final class Cord {
    private Cord() {}

    public static final int BASE_RADIUS = 32;
    public static final int RADIUS_PER_SPI = 6;
    public static final int MAX_RADIUS = 1024;
    public static final int BASE_SECONDS = 30;
    public static final int SECONDS_PER_SPI = 3;
    public static final int MIN_AWARENESS = 16;
    public static final int MAX_AWARENESS = 160;

    /** Tension at which the Signal starts warning and the cord turns amber, then red. */
    public static final double WARN = 0.85;
    public static final double DANGER = 0.95;

    public static int radius(int spi, double cordPerk) {
        double r = BASE_RADIUS + RADIUS_PER_SPI * Math.max(0, spi) + Math.max(0, cordPerk);
        return (int) Mth.clamp(r, BASE_RADIUS, MAX_RADIUS);
    }

    public static int timeTicks(int spi) {
        return (BASE_SECONDS + SECONDS_PER_SPI * Math.max(0, spi)) * 20;
    }

    public static int awareness(int per, int spi, double glowPerk) {
        double a = MIN_AWARENESS + 2.0 * Math.max(0, per) + Math.max(0, spi) + Math.max(0, glowPerk);
        return (int) Mth.clamp(a, MIN_AWARENESS, MAX_AWARENESS);
    }

    public static int secretSense(int per, double glowPerk) {
        return (int) Math.min(32, 4 + Math.max(0, per) / 4 + Math.max(0, glowPerk));
    }

    /** 0 at the anchor, 1 at the edge of the cord. */
    public static double tension(double distance, int radius) {
        return radius <= 0 ? 1.0 : Math.max(0.0, distance / radius);
    }

    /** The point a spirit is pulled back to when it strays past the edge. */
    public static double pullBackDistance(int radius) {
        return Math.max(1.0, radius - 1.5);
    }
}
