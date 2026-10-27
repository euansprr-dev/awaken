package dev.awaken.ground;

import dev.awaken.Awaken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The Soft Land (docs/terrain-immersion/PLAN.md, SPEC §31): the land's seams, steps and detail. This is P0, seamless
 * ground: grass that wraps down a one-block step, grass creeping onto paths and dirt, and a lawn whose colour drifts in
 * patches. All of it is the client's drawing ({@code ground.client}); the rules about which sides and edges it touches
 * live here so tests can hold them.
 */
public final class GroundModule {
    private GroundModule() {}

    /** The data pack every worldgen part of the Soft Land lives in (P1-P5), behind the feature flag of the same name: new worlds only. */
    public static final String PACK_ID = "mod/awaken:datapacks/awakened_ground";

    public static void init(IEventBus modBus) {
        modBus.addListener(AddPackFindersEvent.class, e -> e.addPackFinders(Awaken.id("datapacks/awakened_ground"), PackType.SERVER_DATA,
                Component.literal("Awakened Ground"), PackSource.BUILT_IN, false, Pack.Position.TOP));
        GroundTests.register();
    }
}
