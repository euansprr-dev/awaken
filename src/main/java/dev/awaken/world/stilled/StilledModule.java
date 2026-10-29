package dev.awaken.world.stilled;

import dev.awaken.citizens.api.Settlements;
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
        StilledCommands.register();
        StilledTests.register();
    }
}
