package dev.awaken.ground.wild;

/**
 * The lawn's colour by wildness (SPEC §41.3), pure so the tests and Far Sight can share it: tamed land a touch greener (mown,
 * watered), pasture as the biome made it, wild land warmer and deep wild warmer still (grass gone to seed). Small shifts on
 * top of the biome's colour and the lawn's drift ({@code LawnDrift}), never a new colour.
 */
public final class WildLawn {
    private WildLawn() {}

    /** The most each end moves the colour: tamed greener, deep wild warmer. */
    public static final float GREENER = 0.06F, WARMER = 0.11F;

    public static int shift(int rgb, double w) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        if (w < 0.2) {
            float k = GREENER * (float) ((0.2 - w) / 0.2);
            r = clamp(r * (1F - 0.8F * k));
            g = clamp(g * (1F + 0.35F * k));
        } else if (w > 0.45) {
            float k = WARMER * (float) Math.min(1.0, (w - 0.45) / 0.45);
            r = clamp(r * (1F + 1.1F * k) + 6F * k);
            g = clamp(g * (1F + 0.15F * k));
            b = clamp(b * (1F - 1.2F * k));
        }
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static int clamp(float v) {
        return Math.clamp(Math.round(v), 0, 255);
    }
}
