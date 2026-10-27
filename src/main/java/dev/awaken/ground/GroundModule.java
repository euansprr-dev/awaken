package dev.awaken.ground;

import net.neoforged.bus.api.IEventBus;

/**
 * The Soft Land (docs/terrain-immersion/PLAN.md, SPEC §31): the land's seams, steps and detail. This is P0, seamless
 * ground: grass that wraps down a one-block step, grass creeping onto paths and dirt, and a lawn whose colour drifts in
 * patches. All of it is the client's drawing ({@code ground.client}); the rules about which sides and edges it touches
 * live here so tests can hold them.
 */
public final class GroundModule {
    private GroundModule() {}

    public static void init(IEventBus modBus) {
        GroundTests.register();
    }
}
