package dev.awaken.kin;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The kin (SPEC §43.5, §43.6; JOURNEY parts 2 and 3): the land's own beasts, Kinship, the wild sites and their rumours,
 * homes and the beasts' work, and the Hearth page. Stage 2 brings the Lampwing, Burrowbun, Mossback and Pebblepup; stage 5
 * the second eight and the works they run ({@link dev.awaken.kin.works}).
 */
public final class KinModule {
    private KinModule() {}

    public static void init(IEventBus modBus) {
        KinContent.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> KinContent.TYPES.forEach((k, t) -> e.put(t.get(), KinBeast.attributes(k).build())));
        modBus.addListener(KinNet::register);
        dev.awaken.kin.works.WorksModule.init(modBus);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> KinHelp.placed(e));
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> KinHelp.used(e));
        bus.addListener((BreakBlockEvent e) -> KinHelp.broken(e));
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> KinHelp.interact(e));
        bus.addListener((MobSpawnEvent.PositionCheck e) -> KinHome.spawnCheck(e));
        bus.addListener((net.neoforged.neoforge.event.level.BlockDropsEvent e) -> KinHome.heartDrops(e));
        bus.addListener((ServerTickEvent.Post e) -> {
            var server = e.getServer();
            KinWatch.tick(server);
            KinHelp.tick(server);
            KinSpawner.tick(server);
            if (server.getTickCount() % 100 == 13)
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.isSpectator()) continue;
                    KinHome.lessonTick(p);
                    KinHome.daily(p);
                }
        });
        bus.addListener((ServerStoppedEvent e) -> {
            KinWatch.serverStopped();
            KinHelp.serverStopped();
            KinWorks.serverStopped();
        });
        dev.awaken.core.api.Destinations.doneWhen("kin", KinPage::trackDone);
        dev.awaken.core.api.Shown.register("hearth", KinPage::shown);
        KinCommands.register();
        KinTests.register();
    }
}
