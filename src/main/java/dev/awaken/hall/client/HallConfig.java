package dev.awaken.hall.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The Hall's settings ({@code config/awaken-hall-client.toml}). */
public final class HallConfig {
    private HallConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue CLASSIC = B
            .comment("Use the old Armoury, Fitting Stand and War Anvil windows instead of the Hall of Arms.")
            .define("classicScreens", false);
    private static final ModConfigSpec.BooleanValue CLICK = B
            .comment("Forge with one click rather than holding the button down.")
            .define("clickToCommit", false);
    private static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-hall-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Hall settings could not be registered; using defaults", e);
        }
    }

    public static boolean classic() {
        return SPEC.isLoaded() && CLASSIC.get();
    }

    static boolean clickToCommit() {
        return SPEC.isLoaded() && CLICK.get();
    }
}
