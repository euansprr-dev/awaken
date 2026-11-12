package dev.awaken.enchant.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Enchanting's settings ({@code config/awaken-enchant-client.toml}). */
public final class EnchantClientConfig {
    private EnchantClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue CLASSIC = B
            .comment("Use the old book of two pages and the result card at an Inscribing Table, instead of choosing among the pages on the desk.")
            .define("classicTable", false);
    private static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-enchant-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Enchanting's settings could not be registered; using defaults", e);
        }
    }

    /** The old book and card (kept for one build, as the Hall kept its old screens). */
    public static boolean classic() {
        return SPEC.isLoaded() && CLASSIC.get();
    }
}
