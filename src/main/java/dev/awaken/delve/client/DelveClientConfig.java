package dev.awaken.delve.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings for the deep's drawn light (SPEC §28.3), in their own file ({@code config/awaken-delve-client.toml}). */
public final class DelveClientConfig {
    private DelveClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue HELD_LIGHT = B
            .comment("Held light: a torch, lantern or any light-giving block in either hand lights the ground, walls and ceiling",
                    "round whoever holds it (drawn, never placed: nothing is relit). Off, a held light gives no light, as in vanilla.")
            .define("held_light", true);
    public static final ModConfigSpec SPEC = B.build();

    // read every frame: cached, updated when the file loads or changes
    private static volatile boolean heldLight = true;

    static void register(net.neoforged.bus.api.IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-delve-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Delve client settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            heldLight = HELD_LIGHT.get();
        } catch (RuntimeException e) {
            heldLight = true;
        }
    }

    public static boolean heldLight() {
        return heldLight;
    }
}
