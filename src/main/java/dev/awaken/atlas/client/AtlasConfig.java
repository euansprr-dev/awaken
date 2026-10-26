package dev.awaken.atlas.client;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for the Atlas ({@code config/awaken-atlas-client.toml}, also in the mod's config screen; SPEC §30.5).
 * Read once a tick into plain fields.
 */
public final class AtlasConfig {
    private AtlasConfig() {}

    public enum MapOpens { SCENE, TABLE }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<MapOpens> MAP_OPENS = B
            .comment("How M and the band's Map page open the map: SCENE pulls the camera back over your shoulder and the band",
                    "projects the map in front of you; TABLE opens the flat table at once. Shift+M (or M twice) always opens the table.")
            .defineEnum("mapOpens", MapOpens.SCENE);
    public static final ModConfigSpec.BooleanValue CLEAR_CACHE = B
            .comment("Turn on to clear the Atlas's saved picture of the land on this computer (it fills in again as you explore).",
                    "It turns itself off once done.")
            .define("clearAtlasCache", false);
    public static final ModConfigSpec SPEC = B.build();

    private static MapOpens mapOpens = MapOpens.SCENE;

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SPEC, Awaken.MODID + "-atlas-client.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Atlas settings could not be registered; using defaults", e);
        }
    }

    static void refresh() {
        try {
            if (!SPEC.isLoaded()) return;
            mapOpens = MAP_OPENS.get();
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (CLEAR_CACHE.get() && p != null) { // in a world: the cache is the world's
                CLEAR_CACHE.set(false);
                CLEAR_CACHE.save();
                AtlasState.get().clearCache();
                p.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("atlas.awaken.cleared"));
            }
        } catch (RuntimeException e) {
            // keep the last good values
        }
    }

    static boolean opensAsTable() {
        return mapOpens == MapOpens.TABLE;
    }
}
