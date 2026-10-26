package dev.awaken.terrain.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the living land, in their own file ({@code config/awaken-terrain-client.toml}). */
public final class TerrainClientConfig {
    private TerrainClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    /** Leaves and plants together; the key keeps its first name ({@code waving_leaves}) so older settings still apply. */
    public static final ModConfigSpec.BooleanValue WAVING = B
            .comment("Leaves, grass and every plant sway in the wind (kelp and seagrass in a slow current). Needs Sodium",
                    "0.9.3-beta.1 (Awaken's terrain shader is written for that version only and steps aside for any other, for",
                    "Iris and for the vanilla renderer). Changing it reloads resources.")
            .define("waving_leaves", true);

    /** How many leaves fall: the cap alive at once, spots tried per tick, and a spawn chance multiplier. */
    public enum LeafAmount {
        OFF(0, 0, 0F), LOW(60, 6, 0.5F), NORMAL(150, 12, 1F), HIGH(300, 18, 1.6F);

        final int cap, attempts;
        final float rate;

        LeafAmount(int cap, int attempts, float rate) {
            this.cap = cap;
            this.attempts = attempts;
            this.rate = rate;
        }
    }

    public static final ModConfigSpec.EnumValue<LeafAmount> FALLING_LEAVES = B
            .comment("Leaves drifting down from trees (and motes in glowing forests), carried by the wind: OFF, LOW (60 at most),",
                    "NORMAL (150) or HIGH (300). Fewer while frames run slow. Works with or without Sodium.")
            .defineEnum("falling_leaves", LeafAmount.NORMAL);
    public static final ModConfigSpec.BooleanValue LEAF_RUSTLE = B
            .comment("Wind in the leaves: a rustle under trees that rises with gusts, and deeper wind up high.")
            .define("leaf_rustle", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-terrain-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Terrain client settings could not be registered; using defaults", e);
        }
    }

    static boolean waving() {
        try {
            return !SPEC.isLoaded() || WAVING.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static LeafAmount fallingLeaves() {
        try {
            return SPEC.isLoaded() ? FALLING_LEAVES.get() : LeafAmount.NORMAL;
        } catch (RuntimeException e) {
            return LeafAmount.NORMAL;
        }
    }

    static void setFallingLeaves(LeafAmount amount) {
        if (!SPEC.isLoaded()) return;
        FALLING_LEAVES.set(amount);
        FALLING_LEAVES.save();
    }

    /** The setting {@code leaf_rustle}: the world sound Conductor (core.audio) plays leaves and high wind only while on. */
    public static boolean leafRustle() {
        try {
            return !SPEC.isLoaded() || LEAF_RUSTLE.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void setWaving(boolean on) {
        if (!SPEC.isLoaded()) return;
        WAVING.set(on);
        WAVING.save();
    }
}
