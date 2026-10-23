package dev.awaken.lucidium;

import dev.awaken.lucidium.dream.DreamEvents;
import dev.awaken.lucidium.net.LucidiumNet;
import net.neoforged.bus.api.IEventBus;

/**
 * Entry point for the lucidium module: lucid dreams built from the player's own day (SPEC section 9).
 * Day Journal snapshots, dusk dream plans, the Lucidium dimension, lucidity, dream signs, intention, WBTB, manifest,
 * nightmares, mentors and visions. Public API: {@link Lucidium}.
 */
public final class LucidiumModule {
    private LucidiumModule() {}

    public static void init(IEventBus modBus) {
        LucidiumContent.init(modBus);
        LucidiumNet.init(modBus);
        DreamEvents.init(modBus);
        LucidiumCommands.register();
        LucidiumTests.register();
    }
}
