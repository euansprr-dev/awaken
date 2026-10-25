package dev.awaken.turbo.pixels;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for Pixels ({@code config/awaken-pixels-client.toml}): the world drawn at a lower resolution and
 * rebuilt to the screen's, with menus, HUD and text always at the screen's own resolution (SPEC §18). Read once a tick
 * into plain fields, so frame code never touches the config.
 */
public final class PixelsConfig {
    private PixelsConfig() {}

    /** How the lower-resolution world is rebuilt to the screen's. */
    public enum Upscaler { FSR, SHARP, BILINEAR }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.DoubleValue RENDER_SCALE = B
            .comment("The world's resolution as a share of the screen's, on each axis (1.0 = native, off), on screens under 4K.",
                    "0.75 draws 56% of the pixels, 0.67 draws 45%, 0.5 draws 25%. Menus, the HUD, text and open Holo panels stay native.",
                    "Worth it where the GPU is the limit (far render distances, heavy Looks): try 0.75. Fine block textures soften.")
            .defineInRange("renderScale", 1.0, 0.5, 1.0);
    public static final ModConfigSpec.DoubleValue RENDER_SCALE_4K = B
            .comment("The same on a 4K screen (a game frame of 8 megapixels or more, e.g. 3840x2160): 0.75 by default, where",
                    "Lens and the world's pixels cost the most. Set 1.0 for native.")
            .defineInRange("renderScale4k", 0.75, 0.5, 1.0);
    public static final ModConfigSpec.EnumValue<Upscaler> UPSCALER = B
            .comment("SHARP: a stretch with FSR's contrast-adaptive sharpening in the same pass (best value; the default).",
                    "FSR: AMD FidelityFX FSR 1 (MIT licence), edge-adaptive upscale plus sharpening, two passes: a little crisper",
                    "block edges, about five times SHARP's cost. BILINEAR: a plain stretch (softest, cheapest).")
            .defineEnum("upscaler", Upscaler.SHARP);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-pixels-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Pixels settings could not be registered; the world renders at native resolution", e);
        }
    }
}
