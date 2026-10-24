package dev.awaken.terrain.client;

import net.neoforged.bus.api.IEventBus;

/** Client side of the living land (SPEC 15). For now: waving leaves under Sodium, the spike in docs/spikes. */
public final class TerrainClient {
    private TerrainClient() {}

    public static void init(IEventBus modBus) {
        TerrainClientConfig.register();
        WavingLeaves.init(modBus);
    }
}
