package dev.awaken.citizens;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Carillon's people (docs/SPEC.md §16.5; Milestone 3, "Alive"): 240 citizens on a ledger with a day each, bodies only
 * near players, the work that changes the city you see, the city's events, and a first step of recognition (who has
 * seen you, and who says your name).
 */
public final class CitizensModule {
    private CitizensModule() {}

    public static void init(IEventBus modBus) {
        CitizensContent.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(CitizensContent.CITIZEN.get(), CitizenBody.createAttributes().build()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> Citizens.onTickPre(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> Citizens.onTickPost(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> Citizens.stop());
    }
}
