package dev.awaken.storage.client;

import dev.awaken.Awaken;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Storage's client settings (SPEC §56), in their own file ({@code config/awaken-storage-client.toml}). */
final class StorageConfig {
    private StorageConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue KEYS = B
            .comment("The Tidy, Pin and Undo buttons on the bag and chests, their keys, and Alt-click to pin.",
                    "Off, none of them appear. Chests still show in glass while the bag does, and pins you've set are kept.")
            .define("tidy_keys", true);
    private static final ModConfigSpec SPEC = B.build();

    private static volatile boolean keys = true;

    static void register(IEventBus modBus) {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-storage-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Storage settings could not be registered; using defaults", e);
        }
        modBus.addListener(ModConfigEvent.class, e -> {
            if (e.getConfig().getSpec() == SPEC) refresh();
        });
    }

    private static void refresh() {
        try {
            keys = KEYS.get();
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Storage settings could not be read; using defaults", e);
        }
    }

    static boolean keys() {
        return keys;
    }
}
