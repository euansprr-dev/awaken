package dev.awaken.arms;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Arms' world options ({@code config/awaken-arms.toml}, synced so every client predicts what the server judges).
 * {@code enabled}: off gives back vanilla melee everywhere (weapons still swing as swords); the rollback switch.
 */
public final class ArmsConfig {
    private ArmsConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Arms: guard, deflect and dodge on every melee weapon, told enemy blows, posture and the weapons' moves (SPEC §24).",
                    "Off: vanilla melee everywhere; Arms weapons swing as plain swords.")
            .define("enabled", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, Awaken.MODID + "-arms.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Arms settings could not be registered; using defaults", e);
        }
    }

    /** Test stand-in (null: read the config). */
    static Boolean enabledOverride;

    public static boolean enabled() {
        if (enabledOverride != null) return enabledOverride;
        try {
            return !SPEC.isLoaded() || ENABLED.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
