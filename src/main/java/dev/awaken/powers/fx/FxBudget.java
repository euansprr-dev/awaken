package dev.awaken.powers.fx;

/**
 * How much of an effect to build: the "power effects" client setting (full / reduced / minimal) times the vanilla
 * particle setting (all / decreased / minimal) times distance. Pure maths, shared so tests can check the caps.
 */
public final class FxBudget {
    private FxBudget() {}

    public enum Level { FULL, REDUCED, MINIMAL }

    /** Hard cap on live sprites at each level (all effects together). */
    public static int spriteCap(Level level) {
        return switch (level) {
            case FULL -> 3072;
            case REDUCED -> 1536;
            case MINIMAL -> 512;
        };
    }

    /** Hard cap on live meshes (rings, domes, beams, trails...). */
    public static int shapeCap(Level level) {
        return switch (level) {
            case FULL -> 192;
            case REDUCED -> 128;
            case MINIMAL -> 64;
        };
    }

    /** Hard cap on live debris chunks. */
    public static int debrisCap(Level level) {
        return switch (level) {
            case FULL -> 160;
            case REDUCED -> 64;
            case MINIMAL -> 0;
        };
    }

    /** Most sprites one effect may spawn, whatever its recipe asks for. */
    public static final int PER_EFFECT = 220;

    /** Beyond this many blocks from the camera an effect is not built at all. */
    public static final double CULL_DISTANCE = 72;

    /**
     * Density factor in (0, 1].
     *
     * @param particleStatus vanilla setting: 0 all, 1 decreased, 2 minimal
     * @param distance       blocks from the camera to the effect
     */
    public static float factor(Level level, int particleStatus, double distance) {
        float f = switch (level) {
            case FULL -> 1f;
            case REDUCED -> 0.45f;
            case MINIMAL -> 0.15f;
        };
        f *= switch (particleStatus) {
            case 1 -> 0.6f;
            case 2 -> 0.3f;
            default -> 1f;
        };
        if (distance > 24) f *= (float) Math.max(0.25, 1 - (distance - 24) / 64);
        return f;
    }

    /** Scaled count for a layer that would spawn {@code base} sprites at full; never more than the per-effect cap. */
    public static int count(int base, float factor) {
        if (base <= 0) return 0;
        int n = Math.round(base * factor);
        // a layer that exists at all keeps at least one sprite unless the budget is nearly nothing
        if (n == 0 && factor >= 0.1f) n = 1;
        return Math.min(PER_EFFECT, n);
    }

    /** Screen feel (impact frames, flashes, FOV punches) is off at minimal and softened at reduced. */
    public static float screen(Level level) {
        return switch (level) {
            case FULL -> 1f;
            case REDUCED -> 0.5f;
            case MINIMAL -> 0f;
        };
    }

    /** Afterimages are the most expensive layer (a whole player model each). */
    public static int afterimageCap(Level level) {
        return switch (level) {
            case FULL -> 12;
            case REDUCED -> 4;
            case MINIMAL -> 0;
        };
    }
}
