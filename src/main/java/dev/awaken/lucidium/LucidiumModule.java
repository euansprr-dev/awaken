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
        dev.awaken.core.api.DreamHooks.install(new dev.awaken.core.api.DreamHooks.Access() {
            @Override public boolean isDreaming(net.minecraft.server.level.ServerPlayer p) { return Lucidium.isDreaming(p); }
            @Override public boolean replay(net.minecraft.server.level.ServerPlayer p, int index) { return Lucidium.replay(p, index); }
            @Override public int recordedCount(net.minecraft.server.level.ServerPlayer p) { return Lucidium.dreams(p).size(); }
        });
        LucidiumContent.init(modBus);
        LucidiumNet.init(modBus);
        DreamEvents.init(modBus);
        LucidiumCommands.register();
        dev.awaken.lucidium.dream.DreamLessons.register(); // the first lucid dream taught in play (level road flag)
        LucidiumTests.register();
        dev.awaken.lucidium.dream.TogetherDreamTests.register();
    }
}
