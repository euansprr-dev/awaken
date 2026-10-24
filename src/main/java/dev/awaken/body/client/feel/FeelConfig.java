package dev.awaken.body.client.feel;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Comfort for moving fast (SPEC §17), in its own file ({@code config/awaken-feel-client.toml}, also in the mod's config
 * screen): how far the camera rolls, how much speed widens the view (on top of vanilla's FOV-effects slider), shake,
 * and reduced motion (no streaks, motes, flashes or tremor; half the roll).
 */
public final class FeelConfig {
    private FeelConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue CAMERA_ROLL = B
            .comment("How far the camera rolls into turns and swings, in degrees (0 = never).")
            .defineInRange("cameraRoll", 5.0, 0.0, 7.5);
    public static final ModConfigSpec.DoubleValue WALL_RUN_ROLL = B
            .comment("How far the camera rolls away from a wall you run along, in degrees (0 = never).")
            .defineInRange("wallRunRoll", 5.0, 0.0, 7.5);
    public static final ModConfigSpec.DoubleValue SPEED_FOV = B
            .comment("How much speed widens the view (0..1, times vanilla's FOV effects slider).")
            .defineInRange("speedFov", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue MOVEMENT_SHAKE = B
            .comment("A tremor at the highest speeds and a jolt on hard landings and sonic booms (also off with Awaken's cameraShake).")
            .define("movementShake", true);
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = B
            .comment("Reduced motion: no light streaks, air motes, flashes or tremor, and half the camera roll.")
            .define("reducedMotion", false);
    public static final ModConfigSpec SPEC = B.build();

    public static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-feel-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Feel settings could not be registered; using defaults", e);
        }
    }

    static float roll() {
        try {
            float r = SPEC.isLoaded() ? CAMERA_ROLL.get().floatValue() : 5F;
            return reduced() ? r * 0.5F : r;
        } catch (RuntimeException e) {
            return 5F;
        }
    }

    /** The wall-run's roll (degrees); half under reduced motion. */
    public static float wallRunRoll() {
        try {
            float r = SPEC.isLoaded() ? WALL_RUN_ROLL.get().floatValue() : 5F;
            return reduced() ? r * 0.5F : r;
        } catch (RuntimeException e) {
            return 5F;
        }
    }

    static float speedFov() {
        try {
            return SPEC.isLoaded() ? SPEED_FOV.get().floatValue() : 1F;
        } catch (RuntimeException e) {
            return 1F;
        }
    }

    static boolean shake() {
        try {
            return (!SPEC.isLoaded() || MOVEMENT_SHAKE.get()) && !reduced();
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Public: the Ascent's renderer thins its streaks and motes under reduced motion. */
    public static boolean reduced() {
        try {
            return SPEC.isLoaded() && REDUCED_MOTION.get();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
