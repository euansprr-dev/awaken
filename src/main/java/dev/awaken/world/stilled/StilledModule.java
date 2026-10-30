package dev.awaken.world.stilled;

import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.api.Sources;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The stone people (SPEC §43.3): the Stilled sat down along the roads and at the empty settlements' edges, what they
 * carry, and the chests they left behind. Nothing breaks either (only {@code /awaken stilled remove}).
 */
public final class StilledModule {
    private StilledModule() {}

    public static void init(IEventBus modBus) {
        StilledContent.register(modBus);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            // GameTests place their own: a test server's players stand in test plots, not on the roads
            if (e.getServer() instanceof GameTestServer) return;
            StilledPlacer.tick(e.getServer());
            LeftChests.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> StilledPlacer.serverStopped());
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, e -> {
            if (e.getState().is(StilledContent.STILLED.get()) || e.getState().is(StilledContent.LEFT_CHEST.get())) e.setCanceled(true);
        });
        Settlements.onShown(Stilled::shown);
        dev.awaken.core.api.Destinations.doneWhen("stilled", Stilled::has);
        Sources.declare("a Stilled hands it over when you read them (world.stilled.Stilled)", "stilled_letter", "stilled_key", "stilled_keepsake");
        Sources.declare("sat down along the roads and at empty settlements' edges as players come near (world.stilled.StilledPlacer)", "stilled");
        Sources.declare("stands by an empty settlement's well when a key's holder comes near (world.stilled.LeftChests)", "left_chest");
        Sources.declare("the first of the six old relics you haven't had from one, in a left chest (world.stilled.LeftChests)",
                "shepherds_lantern", "lumen_tonic", "wrens_hook", "caravan_writ", "herdcaller_horn", "pells_spurs");
        StilledCommands.register();
        StilledTests.register();
    }
}
