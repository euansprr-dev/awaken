package dev.awaken.together.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** How this player wants the party drawn (SPEC §70.2), in {@code awaken-together-client.toml}. */
final class TogetherClientConfig {
    private TogetherClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue STRIP = B
            .comment("The party strip, the arrows on the screen's edge and other players' pings.")
            .define("strip", true);
    private static final ModConfigSpec.BooleanValue SMALL = B
            .comment("A small strip: names and health only.")
            .define("small", false);
    private static final ModConfigSpec.BooleanValue PING_SOUND = B
            .comment("A soft sound when someone of your party points at something.")
            .define("ping_sound", true);
    private static final ModConfigSpec.BooleanValue STILL = B
            .comment("Markers appear at their full size at once instead of opening out.")
            .define("still_markers", false);
    private static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-together-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Together's client settings could not be registered; using defaults", e);
        }
    }

    private static boolean on(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    static boolean strip() { return on(STRIP); }
    static boolean small() { return on(SMALL); }
    static boolean pingSound() { return on(PING_SOUND); }
    static boolean reduced() { return on(STILL); }
}
