package dev.awaken.turbo.pixels;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for Pixels ({@code config/awaken-pixels-client.toml}): the world drawn at a lower resolution and
 * rebuilt to the screen's, with menus, HUD and text always at the screen's own resolution (SPEC §16). Read once a tick
 * into plain fields, so frame code never touches the config.
 */
public final class PixelsConfig {
    private PixelsConfig() {}

    /** How the lower-resolution world is rebuilt to the screen's. */
    public enum Upscaler { FSR, SHARP, BILINEAR }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue RENDER_SCALE = B
            .comment("The world's resolution as a share of the screen's, on each axis (1.0 = native, off).",
                    "0.75 draws 56% of the pixels, 0.67 draws 45%, 0.5 draws 25%. Menus, the HUD and text stay native.")
            .defineInRange("renderScale", 1.0, 0.5, 1.0);
    public static final ModConfigSpec.EnumValue<Upscaler> UPSCALER = B
            .comment("FSR: edge-adaptive upscale plus contrast-adaptive sharpening, two passes (AMD FidelityFX FSR 1, MIT licence).",
                    "SHARP: a stretch with FSR's sharpening in the same pass (about a third of FSR's cost).",
                    "BILINEAR: a plain stretch (softest, cheapest).")
            .defineEnum("upscaler", Upscaler.FSR);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-pixels-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Pixels settings could not be registered; the world renders at native resolution", e);
        }
    }
}
