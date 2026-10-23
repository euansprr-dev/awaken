package dev.awaken.world.landmark;

/**
 * How strongly the landmark markers show (pure, tested; the client draws them). Ruins send up a light shaft at dusk
 * that lingers faintly through the night; the Well's pillars glow at night; by day nothing, so the overworld keeps its
 * vanilla look. Up close a marker fades out (you're there).
 */
public final class MarkerLight {
    private MarkerLight() {}

    /** Light shaft over a ruin, 0..1, by time of day (0..23999). */
    public static float ruinShaft(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 11000) return 0F;
        if (t < 12000) return (t - 11000) / 1000F;
        if (t < 13600) return 1F;
        if (t < 15000) return 1F - 0.7F * (t - 13600) / 1400F;
        if (t < 22300) return 0.3F;
        if (t < 23300) return 0.3F * (1F - (t - 22300) / 1000F);
        return 0F;
    }

    /** Glow on the Well's four pillars, 0..1, by time of day: night only. */
    public static float wellGlow(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        if (t < 12000) return 0F;
        if (t < 13200) return (t - 12000) / 1200F;
        if (t < 22600) return 1F;
        if (t < 23500) return 1F - (t - 22600) / 900F;
        return 0F;
    }

    /** Nothing within 20 blocks of the camera, full from 40. */
    public static float distanceFade(double distance) {
        if (distance <= 20) return 0F;
        if (distance >= 40) return 1F;
        return (float) ((distance - 20) / 20);
    }
}
