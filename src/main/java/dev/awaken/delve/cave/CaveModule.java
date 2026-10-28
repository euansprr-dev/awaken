package dev.awaken.delve.cave;

import dev.awaken.core.AwakenNet;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Caves worth entering (SPEC §28.5; PLAN section 8): the cave places (sinkholes, the Delvers' mines, seeps, chutes,
 * grottos) as structures joined to the natural caves, what the server knows of them at run time (the place you are in,
 * its name, a seep's level and heart), the Delvers' logs, map and pick, the joined Crystal Nest and the lair line's depth
 * word, and the Horizon's "Sinkhole · a way down".
 */
public final class CaveModule {
    private CaveModule() {}

    public static void init(IEventBus modBus) {
        CaveContent.register(modBus);
        modBus.addListener(CaveNet::register);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ChunkEvent.Load e) -> CavePlaces.onChunkLoad(e));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) CavePlaces.tick(p);
        });
        bus.addListener(CavePlaces::onDeath);
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> CavePlaces.onUse(e));
        bus.addListener((net.neoforged.neoforge.event.entity.player.ItemFishedEvent e) -> CavePlaces.onFished(e));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) AwakenNet.send(p, new CaveNet.Logs(CaveItems.read(p)));
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CavePlaces.forget(e.getEntity().getUUID()));
        bus.addListener((ServerStoppedEvent e) -> {
            CavePlaces.clear();
            CaveHorizon.clear();
        });
        CaveItems.register();
        CavePlaces.register();
        MouthPointer.register();
        CaveHorizon.register();
        CaveCommands.register();
        CaveTests.register();
    }
}
