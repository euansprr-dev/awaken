package dev.awaken.farsight;

import net.neoforged.bus.api.IEventBus;

/**
 * Far Sight (SPEC §16, plan https://claude.ai/artifact/LvAoTbYB82o6ggcHS73rX1): Awaken's own far-terrain renderer.
 * Stage 0 is client-side ({@code farsight.client}) and predicts from the integrated server's generator; the common
 * side is the ring layout, the column sampler and the worker threads, which stage 2's server-made tiles reuse.
 */
public final class FarSightModule {
    private FarSightModule() {}

    public static void init(IEventBus modBus) {
        FarSightTests.register();
    }
}
