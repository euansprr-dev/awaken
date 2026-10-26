package dev.awaken.compat.client;

import dev.awaken.Awaken;
import dev.awaken.compat.Look.Tier;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenConfig.Client.FarSight;
import dev.awaken.turbo.pixels.PixelsConfig;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The Look page's presets and the settings they set (SPEC §14, Looks; Euan, 2026-10-26: "different options that I can
 * easily switch between ... where I can see them all at once"). A preset is one click for the Look (Lens), Far Sight's
 * reach, the render distance, the render scale on a 4K screen and Pixels' steady pass; each of those is also a row of
 * its own on the page, so any mix works, and the page names the preset the settings match (or none: custom).
 */
public final class LookPresets {
    private LookPresets() {}

    public enum Preset {
        PERFORMANCE("PERFORMANCE", "Most fps", Tier.SWIFT, FarSight.NEAR, 12, 0.75, false),
        CLOSE_UP("CLOSE-UP", "Sharpest up close", Tier.ULTRA, FarSight.OFF, 12, 1.0, true),
        BALANCED("BALANCED", "Cinematic, 16 km", Tier.CINEMATIC, FarSight.FULL, 16, 0.75, true),
        VISTA("VISTA", "Longest view", Tier.CINEMATIC, FarSight.FULL, 24, 0.75, true);

        public final String label, blurb;
        public final Tier tier;
        public final FarSight far;
        public final int renderDistance;
        public final double scale4k;
        public final boolean steady;

        Preset(String label, String blurb, Tier tier, FarSight far, int renderDistance, double scale4k, boolean steady) {
            this.label = label;
            this.blurb = blurb;
            this.tier = tier;
            this.far = far;
            this.renderDistance = renderDistance;
            this.scale4k = scale4k;
            this.steady = steady;
        }
    }

    static final int[] DISTANCES = {8, 12, 16, 20, 24, 32};
    static final double[] SCALES = {1.0, 0.85, 0.75, 0.67};

    /** Sets everything the preset holds, now and saved. False when its Look can't run here. */
    static boolean apply(Preset p) {
        if (!LookSwitch.apply(p.tier)) return false;
        setFar(p.far);
        setDistance(p.renderDistance);
        setScale4k(p.scale4k);
        setSteady(p.steady);
        Awaken.LOG.info("[look] preset {}: {}, Far Sight {}, {} chunks, 4K scale {}, steady {}", p, p.tier, p.far, p.renderDistance, p.scale4k, p.steady);
        return true;
    }

    /** For the autopilot's {@code lookpreset} line. */
    public static boolean applyFromAutopilot(Preset p) {
        return apply(p);
    }

    /** The preset the current settings match, or null (custom). */
    static @Nullable Preset current() {
        Tier tier = LookSwitch.current();
        for (Preset p : Preset.values()) {
            if (p.tier == tier && p.far == far() && p.renderDistance == distance() && Math.abs(p.scale4k - scale4k()) < 0.01
                    && p.steady == steady()) return p;
        }
        return null;
    }

    static FarSight far() {
        return AwakenConfig.Client.FAR_SIGHT.get();
    }

    static void setFar(FarSight f) {
        AwakenConfig.Client.FAR_SIGHT.set(f);
        AwakenConfig.Client.FAR_SIGHT.save();
    }

    static int distance() {
        return Minecraft.getInstance().options.renderDistance().get();
    }

    static void setDistance(int chunks) {
        var options = Minecraft.getInstance().options;
        options.renderDistance().set(chunks);
        options.save();
    }

    static double scale4k() {
        return PixelsConfig.RENDER_SCALE_4K.get();
    }

    static void setScale4k(double scale) {
        PixelsConfig.RENDER_SCALE_4K.set(scale);
        PixelsConfig.RENDER_SCALE_4K.save();
    }

    static boolean steady() {
        return PixelsConfig.STEADY.get();
    }

    static void setSteady(boolean on) {
        PixelsConfig.STEADY.set(on);
        PixelsConfig.STEADY.save();
    }

    static boolean temporal() {
        return AwakenConfig.Client.LENS_TEMPORAL.get();
    }

    static void setTemporal(boolean on) {
        AwakenConfig.Client.LENS_TEMPORAL.set(on);
        AwakenConfig.Client.LENS_TEMPORAL.save();
    }

    /** The next value in a cycle (step +1 or -1), wrapping; the nearest when the current one isn't in it. */
    static int next(int[] values, int current, int step) {
        int at = 0;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - current) < Math.abs(values[at] - current)) at = i;
        return values[Math.floorMod(at + (values[at] == current ? step : 0), values.length)];
    }

    static double next(double[] values, double current, int step) {
        int at = 0;
        for (int i = 0; i < values.length; i++) if (Math.abs(values[i] - current) < Math.abs(values[at] - current)) at = i;
        return values[Math.floorMod(at + (Math.abs(values[at] - current) < 0.01 ? step : 0), values.length)];
    }
}
