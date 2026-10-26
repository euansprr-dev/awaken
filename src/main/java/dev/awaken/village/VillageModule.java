package dev.awaken.village;

import dev.awaken.village.world.Plans;
import dev.awaken.village.world.VillageWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Living villages (Between the Gates W3; SPEC §28.6, docs/modules/village.md): AWAKEN's own hamlets and villages in
 * new worlds (vanilla's are switched off by the Awakened Lands pack), planned in code on the settlement grid, painted by
 * the city's grammar with interiors, and lived in by the people engine's settlements: households and village jobs,
 * speech, trade in emeralds, requests on the notice board, standing, a bed at the inn and a scribe. Saved state and the
 * {@code Villages} API (§12) are what W4's troubles build on.
 */
public final class VillageModule {
    private VillageModule() {}

    public static void init(IEventBus modBus) {
        VillageWorldgen.init(modBus);
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> Plans.clear());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Plans.clear());
        VillageCommands.register();
        VillageTests.register();
    }
}
