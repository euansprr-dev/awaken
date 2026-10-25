package dev.awaken.wilds;

import dev.awaken.core.AwakenConfig;
import dev.awaken.wilds.arrival.Arrival;
import dev.awaken.wilds.ward.WardEvents;
import dev.awaken.wilds.ward.WardFx;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Wilds (SPEC §17, docs/plan-ascent.html §6): a world built to be crossed. This first part holds what doesn't wait
 * on the movement tiers: Precursor wards ({@code ward}), how you arrived ({@code arrival}, behind
 * {@code core.api.ArrivalHooks}), biome discoveries, and the sites the Horizon line names come from their own modules
 * through {@code core.api.HorizonHooks}. Spires, windways and chases join this package later.
 */
public final class WildsModule {
    private WildsModule() {}

    /** The wilds setting (on when the config isn't loaded yet, as in GameTests). Wards have their own. */
    public static boolean on() {
        try {
            return AwakenConfig.WILDS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static void init(IEventBus modBus) {
        WildsContent.register(modBus);
        modBus.addListener(WardFx::register);
        WardEvents.init();
        Arrival.init();
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, e -> BiomeDiscovery.tick(e.getServer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> BiomeDiscovery.forget(e.getEntity().getUUID()));
        bus.addListener(ServerStoppingEvent.class, e -> BiomeDiscovery.clear());
        WildsCommands.register();
        WildsTests.register();
    }
}
