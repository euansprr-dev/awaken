package dev.awaken.world.landmark;

import dev.awaken.above.scan.Scanning;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Landmarks (plan v2 §4.3/P2): the Well and the Precursor ruins made findable and worth finding. Discovery when you
 * reach one, a Horizon line once you have a reason to go, the Scanner's sky sweep, faint markers at dusk and at night, and a
 * rumour line for the town. See docs/modules/landmarks.md.
 */
public final class LandmarkModule {
    private LandmarkModule() {}

    /** What the Scanner hears from the Well while you haven't been there. */
    static final String WELL_OMEN = "Something deep calls from below.";

    public static void init(IEventBus modBus) {
        LandmarkContent.register(modBus);
        modBus.addListener(LandmarkNet::register);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (!Landmarks.enabled()) return;
            LandmarkWatch.tick(e.getServer());
            LandmarkMarkers.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            LandmarkMarkers.forget(e.getEntity().getUUID());
            LandmarkHorizon.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> LandmarkMarkers.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            LandmarkMarkers.clear();
            LandmarkHorizon.clear();
        });
        Scanning.addEchoSource(LandmarkModule::echoes);
        Scanning.setVisitedRuins((p, pos) -> Landmarks.reached(p, Landmarks.ruinIdAt(pos)));
        LandmarkHorizon.register();
        LandmarkSpotting.register(); // seen through the binoculars (SPEC §39.10)
        dev.awaken.core.api.WorldHooks.installRumours(p -> Landmarks.enabled()
                ? Landmarks.rumour(p).map(r -> new dev.awaken.core.api.WorldHooks.Rumour(r.id(), r.title(), r.body()))
                : java.util.Optional.empty());
        LandmarkCommands.register();
        LandmarkTests.register();
    }

    /** The Well, for the Scanner's sky sweep, until the player has stood at it. */
    static List<Scanning.Echo> echoes(ServerPlayer p, int range) {
        if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD || Landmarks.reached(p, "well")) return List.of();
        return Landmarks.wellPos(p.level().getServer())
                .filter(w -> Spots.horizontal(p.blockPosition(), w) <= range)
                .map(w -> List.of(new Scanning.Echo(w, WELL_OMEN)))
                .orElse(List.of());
    }
}
