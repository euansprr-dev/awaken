package dev.awaken.farsight;

import net.neoforged.bus.api.IEventBus;

/**
 * Far Sight (SPEC §16, plan https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1): Awaken's own far-terrain renderer.
 * The client draws ({@code farsight.client}); the common side is the ring layout, the predictor (column sampler and
 * worker threads) and the generated layer (real chunks summed on the server tick and cached on disk), which stage 2's
 * server-made tiles reuse.
 */
public final class FarSightModule {
    private FarSightModule() {}

    public static void init(IEventBus modBus) {
        FarCollector.init();
        FarLandmarks.register(FarLandmarks::gates);
        FarSightTests.register();
    }
}
