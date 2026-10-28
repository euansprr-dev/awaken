package dev.awaken.turbo.packetcap;

import dev.awaken.Awaken;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's switch for the packet cap ({@code config/awaken-packetcap-server.toml}, SPEC §28.9): the machine's own
 * setting (a local config, read at mod load), read into a plain field at load, reload and server start.
 */
public final class PacketCapConfig {
    private PacketCapConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue PACKET_CAP = B
            .comment("The server handles its queued packets for at most a tick's time before each tick; the rest wait for the next",
                    "tick, in order, so a player outrunning world generation can't hold the world's tick for minutes (Turbo, a mixin",
                    "into the packet processor). It turns itself off on the first thing that goes wrong. false: vanilla's, every",
                    "queued packet before each tick.")
            .define("packetCap", true);
    public static final ModConfigSpec SPEC = B.build();

    static void register() {
        try {
            ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.LOCAL, SPEC, Awaken.MODID + "-packetcap-server.toml");
        } catch (RuntimeException e) {
            Awaken.LOG.warn("The packet cap's setting could not be registered; it stays at its default (on)", e);
        }
    }

    static boolean on() {
        return !SPEC.isLoaded() || PACKET_CAP.get();
    }
}
