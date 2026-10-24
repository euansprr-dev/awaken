package dev.awaken.holo.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for the Holo UI ({@code config/awaken-holo-client.toml}, also in the mod's config screen). Values
 * are read once a tick into plain fields, so frame code never touches the config.
 */
public final class HoloConfig {
    private HoloConfig() {}

    public enum StatusStyle { HOLO, FLAT }

    public enum HudMotion { OFF, SUBTLE, FULL }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<StatusStyle> STATUS_STYLE = B
            .comment("The Status screen (K): HOLO floats it in front of you from your wrist; FLAT is the classic flat screen.",
                    "FLAT is also used automatically if the holo shaders can't load (for example under a shader pack).")
            .defineEnum("statusStyle", StatusStyle.HOLO);
    public static final ModConfigSpec.EnumValue<HudMotion> HUD_MOTION = B
            .comment("How much Awaken's HUD drifts when you turn sharply: OFF, SUBTLE (at most ~2 GUI pixels) or FULL (at most 4).",
                    "It also follows vanilla's Screen Effect Scale, and is off when View Bobbing is off. Vanilla's hotbar never moves.")
            .defineEnum("hudMotion", HudMotion.SUBTLE);
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = B
            .comment("Panels follow your view directly instead of trailing it, and open with a short fade.")
            .define("reducedMotion", false);
    public static final ModConfigSpec SPEC = B.build();

    // cached once a tick
    static StatusStyle statusStyle = StatusStyle.HOLO;
    static HudMotion hudMotion = HudMotion.SUBTLE;
    static boolean reducedMotion;
    /** Overrides for the autopilot (null = use the config). */
    static HudMotion forcedHudMotion;

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-holo-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Holo settings could not be registered; using defaults", e);
        }
    }

    static void refresh() {
        try {
            if (!SPEC.isLoaded()) return;
            statusStyle = STATUS_STYLE.get();
            hudMotion = forcedHudMotion != null ? forcedHudMotion : HUD_MOTION.get();
            reducedMotion = REDUCED_MOTION.get();
        } catch (RuntimeException e) {
            // keep the last good values
        }
    }

    public static StatusStyle statusStyle() {
        return statusStyle;
    }
}
