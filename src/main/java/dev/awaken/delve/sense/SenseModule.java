package dev.awaken.delve.sense;

import net.neoforged.bus.api.IEventBus;

/**
 * The senses underground (SPEC §28.5; PLAN 8.4): the cave soundscape's sound events and the deep's hum (the client
 * half is {@code delve.client.SenseClient}).
 */
public final class SenseModule {
    private SenseModule() {}

    public static void init(IEventBus modBus) {
    }
}
