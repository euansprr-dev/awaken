package dev.awaken.mobs;

import dev.awaken.core.CoreModule;
import dev.awaken.core.DirectorLog;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.entity.Hazards;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Game-bus and mod-bus listeners of the mobs module. */
final class MobsEvents {
    private MobsEvents() {}

    static void init(IEventBus modBus) {
        modBus.addListener(MobsEvents::creativeTab);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLogout);
        dev.awaken.mobs.entity.FightWatch.init();
        // every kit body (rigs, move tables, fight scripts) read once while the game loads, off the main thread: read
        // when the first boss of a session appeared, it stalled that tick 91 ms (fights_tick_cost, SPEC §46.1)
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> {
            Thread t = new Thread(dev.awaken.mobs.boss.Kits::all, "awaken-kits");
            t.setDaemon(true);
            t.start();
        });
        // and before a world ticks, waited for if it isn't done (all() is synchronized)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) -> dev.awaken.mobs.boss.Kits.all());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (DeferredItem<Item> part : MobTypes.parts().values()) event.accept(part.get());
        for (var egg : MobTypes.eggs().values()) event.accept(egg.get());
    }

    /** Dying to a boss still pays: its tells go into the Codex as Insight. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getSource().getEntity() instanceof AwakenBoss boss && boss.def().tier.countsAsBoss()) {
            Progression.addInsight(sp, boss.def().bossId());
            DirectorLog.log(sp, "mobs", "insight from " + boss.def().id + " (phase " + (boss.phase() + 1) + ")");
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        long t0 = System.nanoTime();
        Hazards.tick(event.getLevel());
        if (!event.getLevel().isClientSide()) dev.awaken.mobs.entity.FightWatch.hazards(System.nanoTime() - t0);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level l) Hazards.clear(l);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Hazards.clear(null);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Mobs.forget(event.getEntity());
    }
}
