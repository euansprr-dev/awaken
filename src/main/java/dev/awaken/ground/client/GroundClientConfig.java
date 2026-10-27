package dev.awaken.ground.client;

import dev.awaken.Awaken;
import dev.awaken.ground.GroundSettings;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the ground's seams (SPEC §31), in their own file ({@code config/awaken-ground-client.toml}). */
public final class GroundClientConfig {
    private GroundClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SEAMLESS = B
            .comment("Seamless ground: grass wraps green down a one-block step instead of showing a dirt face, and grass creeps",
                    "over the edges of paths, dirt, gravel and sand beside it. Off, the ground is drawn exactly as vanilla draws it.",
                    "Chunks redraw when it changes.")
            .define("ground_seamless", true);
    public static final ModConfigSpec.BooleanValue DRIFT = B
            .comment("Lawn colour drift: the grass colour moves a few per cent in patches about 25 blocks across, so a meadow",
                    "is not one flat carpet. Off, grass has its biome's colour everywhere.")
            .define("lawn_drift", true);
    public static final ModConfigSpec SPEC = B.build();

    // read on every block meshed, on worker threads: cached, updated when the file loads or changes
    private static volatile boolean seamless = true, drift = true;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        GroundSettings.set(true, true);                  // the defaults, until the file is read
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-ground-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Ground client settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            seamless = SEAMLESS.get();
            drift = DRIFT.get();
        } catch (RuntimeException e) {
            seamless = true;
            drift = true;
        }
        GroundSettings.set(seamless, drift);
    }

    public static boolean seamless() {
        return seamless;
    }

    public static boolean drift() {
        return drift;
    }

    static void set(boolean seamlessOn, boolean driftOn) {
        seamless = seamlessOn;
        drift = driftOn;
        GroundSettings.set(seamlessOn, driftOn);
        if (!SPEC.isLoaded()) return;
        SEAMLESS.set(seamlessOn);
        DRIFT.set(driftOn);
        SPEC.save();
    }
}
