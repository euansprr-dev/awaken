package dev.awaken.city;

import dev.awaken.Awaken;
import net.neoforged.fml.ModList;

/**
 * Holds Distant Horizons' own world generation while the city pregenerates (G0: DH generating alongside made the pregen
 * 39 s instead of 11-28), through its API override of {@code enableDistantWorldGeneration}; the player's own setting
 * comes back when the override is cleared. Without DH this does nothing; DH's classes load only when it is present.
 */
final class CityDh {
    private CityDh() {}

    static boolean hold() {
        if (!ModList.get().isLoaded("distanthorizons")) return false;
        try {
            return Api.set(false);
        } catch (RuntimeException | LinkageError e) {
            Awaken.LOG.warn("[city] could not hold Distant Horizons' generation: {}", e.toString());
            return false;
        }
    }

    static void release() {
        try {
            Api.clear();
        } catch (RuntimeException | LinkageError e) {
            Awaken.LOG.warn("[city] could not release Distant Horizons' generation: {}", e.toString());
        }
    }

    /** Only touched when DH is loaded. */
    private static final class Api {
        static boolean set(boolean on) {
            var configs = com.seibel.distanthorizons.api.DhApi.Delayed.configs;
            if (configs == null) return false;
            return configs.worldGenerator().enableDistantWorldGeneration().setValue(on);
        }

        static void clear() {
            var configs = com.seibel.distanthorizons.api.DhApi.Delayed.configs;
            if (configs != null) configs.worldGenerator().enableDistantWorldGeneration().clearValue();
        }
    }
}
