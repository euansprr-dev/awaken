package dev.awaken.body;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The movement module's world options ({@code config/awaken-body.toml}, synced to every client so prediction follows
 * the server). {@code runnersKit}: everyone moves like a Runner from the first second and ranks improve the moves
 * (SPEC §16.3, §17); off, the Awakened Body's old rule holds (vanilla until the awakening, ranks unlock the moves,
 * no wall-runs or hangs). {@code ascent}: the Ascent's states above the ground (ropes, wings, flight).
 */
public final class BodyConfig {
    private BodyConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue RUNNERS_KIT = B
            .comment("Everyone moves like a Runner from the first second (wall-runs, ledge hangs, slides, rolls); ranks improve the moves.",
                    "Off: vanilla movement until the awakening, ranks unlock the body's moves, and no wall-runs or hangs.")
            .define("runnersKit", true);
    public static final ModConfigSpec.BooleanValue ASCENT = B
            .comment("The Ascent: ropes, wings and flight (the tiers players earn). Off: none of them start, saves keep the tiers.")
            .define("ascent", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SYNCED, SPEC, Awaken.MODID + "-body.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Movement settings could not be registered; using defaults", e);
        }
    }

    /** Test stand-in: GameTests flip the rule and put it back in try/finally (null: read the config). */
    static Boolean kitOverride;

    public static boolean runnersKit() {
        if (kitOverride != null) return kitOverride;
        try {
            return !SPEC.isLoaded() || RUNNERS_KIT.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static boolean ascent() {
        try {
            return !SPEC.isLoaded() || ASCENT.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
