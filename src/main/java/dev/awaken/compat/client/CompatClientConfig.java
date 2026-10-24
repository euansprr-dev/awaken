package dev.awaken.compat.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the companion plugins, in their own file ({@code config/awaken-compat-client.toml}). */
public final class CompatClientConfig {
    private CompatClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SPOILER_GUARD = B
            .comment("Recipe viewers (JEI) list only the Awaken items you have discovered, and look-at tooltips (Jade) keep",
                    "back what you haven't found yet. Creative and spectator modes always see everything.")
            .define("spoiler_guard", true);
    public static final ModConfigSpec.BooleanValue SYSTEM_STYLE = B
            .comment("Jade tooltips on Awaken blocks and creatures wear the System's glass instead of your Jade theme.")
            .define("jade_system_style", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-compat-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Compat client settings could not be registered; using defaults", e);
        }
    }

    public static boolean spoilerGuard() {
        try {
            return !SPEC.isLoaded() || SPOILER_GUARD.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean systemStyle() {
        try {
            return !SPEC.isLoaded() || SYSTEM_STYLE.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
