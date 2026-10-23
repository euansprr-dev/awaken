package dev.awaken.world;

import net.neoforged.bus.api.IEventBus;

/** Entry point for the world module (the Awakened layer). See docs/SPEC.md and docs/modules/fauna.md. */
public final class WorldModule {
    private WorldModule() {}

    public static void init(IEventBus modBus) {
        dev.awaken.world.fauna.FaunaModule.init(modBus);
        dev.awaken.world.anomaly.AnomalyModule.init(modBus);
    }
}
