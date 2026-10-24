package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the living land, in their own file ({@code config/awaken-terrain-client.toml}). */
public final class TerrainClientConfig {
    private TerrainClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue WAVING_LEAVES = B
            .comment("Leaves sway in the wind. Needs Sodium 0.9.3-beta.1 (Awaken's terrain shader is written for that version",
                    "only and steps aside for any other, for Iris and for the vanilla renderer). Changing it reloads resources.")
            .define("waving_leaves", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-terrain-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Terrain client settings could not be registered; using defaults", e);
        }
    }

    static boolean wavingLeaves() {
        try {
            return !SPEC.isLoaded() || WAVING_LEAVES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void setWavingLeaves(boolean on) {
        if (!SPEC.isLoaded()) return;
        WAVING_LEAVES.set(on);
        WAVING_LEAVES.save();
    }
}
