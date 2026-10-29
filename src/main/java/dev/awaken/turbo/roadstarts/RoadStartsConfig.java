package dev.awaken.turbo.roadstarts;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's switch for road starts ({@code config/awaken-roadstarts-server.toml}, SPEC §28.9): the machine's own
 * setting (a local config, read at mod load, so it holds before the first world generates), read into a plain field at
 * load, reload and server start, so world generation never touches the config.
 */
public final class RoadStartsConfig {
    private RoadStartsConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ROAD_STARTS = B
            .comment("A lattice chunk whose roads aren't planned yet waits for them off the overworld's world generation, which",
                    "generates every other chunk meanwhile (Turbo, a mixin into structure starts). It turns itself off on the first",
                    "thing that goes wrong. false: structure starts wait inline, as vanilla's, holding the level's world generation.")
            .define("roadStarts", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.LOCAL, SPEC, Awaken.MODID + "-roadstarts-server.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("Road starts' setting could not be registered; it stays at its default (on)", e);
        }
    }

    static boolean on() {
        return !SPEC.isLoaded() || ROAD_STARTS.get();
    }
}
