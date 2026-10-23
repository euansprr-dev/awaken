package dev.awaken.powers.client.fx;

import dev.awaken.Awaken;
import dev.awaken.powers.fx.FxBudget;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for power effects, in their own file ({@code config/awaken-power-fx-client.toml}, also in the mod's
 * config screen). "Power effects" thins or simplifies everything we draw; camera shake still follows the core
 * {@code cameraShake} setting and the vanilla particle setting thins sprites further.
 */
public final class PowerFxConfig {
    private PowerFxConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<FxBudget.Level> POWER_EFFECTS = B
            .comment("Power effects: FULL (everything), REDUCED (about half the sprites, softer screen feel, few afterimages),",
                    "MINIMAL (a few sprites and the shapes that show where a power hits; no screen flashes or afterimages).")
            .defineEnum("powerEffects", FxBudget.Level.FULL);
    public static final ModConfigSpec.BooleanValue CAST_ANIMATIONS = B
            .comment("Arms and body move when you or others cast powers (third and first person).")
            .define("castAnimations", true);
    public static final ModConfigSpec.BooleanValue SCREEN_FLASHES = B
            .comment("Brief flashes and impact frames on big hits and ultimates (off also under MINIMAL).")
            .define("screenFlashes", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-power-fx-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Power FX settings could not be registered; using defaults", e);
        }
    }

    static FxBudget.Level level() {
        try {
            return SPEC.isLoaded() ? POWER_EFFECTS.get() : FxBudget.Level.FULL;
        } catch (RuntimeException e) {
            return FxBudget.Level.FULL;
        }
    }

    static boolean animations() {
        try {
            return !SPEC.isLoaded() || CAST_ANIMATIONS.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static boolean flashes() {
        try {
            return (!SPEC.isLoaded() || SCREEN_FLASHES.get()) && level() != FxBudget.Level.MINIMAL;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Vanilla particle setting as 0 all, 1 decreased, 2 minimal. */
    static int particleStatus() {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 0;
            case DECREASED -> 1;
            case MINIMAL -> 2;
        };
    }
}
