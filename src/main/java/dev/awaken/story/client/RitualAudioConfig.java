package dev.awaken.story.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Personal mix, on top of the normal Hostile/Ambient sliders. */
final class RitualAudioConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.DoubleValue EFFECTS = B.comment("Ritual effects volume.").defineInRange("effects", 1.0, 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue AMBIENCE = B.comment("Ritual room atmosphere volume.").defineInRange("ambience", 1.0, 0.0, 1.0);
    private static final ModConfigSpec.DoubleValue IMPACTS = B.comment("Ritual impact camera strength; cameraShake off or reduced motion disables it.").defineInRange("impactMotion", 0.7, 0.0, 1.0);
    private static final ModConfigSpec SPEC = B.build();
    static void init() {
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-ritual-audio-client.toml");
    }
    private static float value(ModConfigSpec.DoubleValue v, float fallback) {
        try { return v.get().floatValue(); } catch (RuntimeException ex) { return fallback; }
    }
    static float effects() { return value(EFFECTS, 1); }
    static float ambience() { return value(AMBIENCE, 1); }
    static float motion() { return value(IMPACTS, 0.7F); }
}
