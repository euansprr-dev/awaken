package dev.awaken.caves.client;

import dev.awaken.Awaken;
import dev.awaken.caves.skin.Drips;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the Living Deep (SPEC §42.5), in their own file ({@code config/awaken-caves-client.toml}). */
public final class CavesClientConfig {
    private CavesClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue UNDERLIFE = B
            .comment("The underlife: moths that come to your light, beetles and millipedes on the walls, bats roosting on high roofs",
                    "that scatter as you pass, pale fish in still pools, crickets you hear before you see. Drawn on your screen only:",
                    "nothing is saved or sent, and nothing can be hit. Off, the caves are as still as before.")
            .define("underlife", true);
    public static final ModConfigSpec.BooleanValue CAVE_PARTICLES = B
            .comment("Cave particles: drops forming on a stalactite's tip, falling and splashing where they land.",
                    "Off, the stalactites stay dry. (Minimal particles in the game's own settings hides them too.)")
            .define("cave_particles", true);
    public static final ModConfigSpec SPEC = B.build();

    // read every tick: cached, updated when the file loads or changes
    private static volatile boolean underlife = true;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-caves-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Caves client settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            underlife = UNDERLIFE.get();
            Drips.enabled = CAVE_PARTICLES.get();
        } catch (RuntimeException e) {
            underlife = true;
            Drips.enabled = true;
        }
    }

    public static boolean underlife() {
        return underlife;
    }
}
