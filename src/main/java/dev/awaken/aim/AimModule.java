package dev.awaken.aim;

import net.neoforged.bus.api.IEventBus;

/**
 * Lock-on (SPEC §40), common half: its rules ({@link AimRules}) and their tests. The lock itself lives on the client
 * ({@code aim.client}); the server's side is the check every press's creature passes ({@code core.api.Aim}) and the
 * fliers it can bring down ({@code core.api.Strikes#ground}).
 */
public final class AimModule {
    private AimModule() {}

    public static void init(IEventBus modBus) {
        AimTests.register();
    }
}
