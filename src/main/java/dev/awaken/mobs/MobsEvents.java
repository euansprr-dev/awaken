package dev.awaken.mobs;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.Tabs;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.entity.Hazards;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
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
        // a creature's parts and its egg: the wild's with the wild's beasts, the rest with the gates' (bosses in bestiary order)
        Tabs.fill(Tabs.Shelf.BOSS_TROPHIES, (p, out) -> bestiary(out, false, true));
        Tabs.fill(Tabs.Shelf.BEAST_PARTS, (p, out) -> bestiary(out, true, true));
        Tabs.fill(Tabs.Shelf.GATE_CREATURES, (p, out) -> bestiary(out, false, false));
        Tabs.fill(Tabs.Shelf.WILD_CREATURES, (p, out) -> bestiary(out, true, false));
        Tabs.icon(Tabs.Tab.CREATURES, () -> {
            for (var d : dev.awaken.mobs.def.Bestiary.all())
                if (d.tier == dev.awaken.mobs.def.Tier.LORD && MobTypes.eggs().containsKey(d.id)) return MobTypes.eggs().get(d.id).get().getDefaultInstance();
            return net.minecraft.world.item.ItemStack.EMPTY;
        });
        NeoForge.EVENT_BUS.addListener(MobsEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(MobsEvents::onLogout);
        // the finishing blow's truce (SPEC §67.4): a beaten body harms nobody, and its striker is safe while its ending plays
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, dev.awaken.mobs.entity.Finishers::onIncomingDamage);
        dev.awaken.mobs.entity.FightWatch.init();
        dev.awaken.mobs.entity.FairGuard.init();
        // every kit body (rigs, move tables, fight scripts) read once while the game loads, off the main thread: read
        // when the first boss of a session appeared, it stalled that tick 91 ms (fights_tick_cost, SPEC §47.1)
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> {
            Thread t = new Thread(dev.awaken.mobs.boss.Kits::all, "awaken-kits");
            t.setDaemon(true);
            t.start();
        });
        // and before a world ticks, waited for if it isn't done (all() is synchronized)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent e) -> dev.awaken.mobs.boss.Kits.all());
    }

    private static void bestiary(net.minecraft.world.item.CreativeModeTab.Output out, boolean wild, boolean parts) {
        for (var d : dev.awaken.mobs.def.Bestiary.all()) {
            if (d.isFauna() != wild) continue;
            if (!parts) {
                DeferredItem<?> egg = MobTypes.eggs().get(d.id);
                if (egg != null) out.accept(egg.get());
                continue;
            }
            for (var part : d.parts) {
                DeferredItem<Item> item = MobTypes.parts().get(part.id());
                if (item != null) out.accept(item.get());
            }
        }
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
        dev.awaken.mobs.entity.Carriers.tick(event.getLevel());
        if (!event.getLevel().isClientSide()) dev.awaken.mobs.entity.FightWatch.hazards(System.nanoTime() - t0);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level l) {
            Hazards.clear(l);
            dev.awaken.mobs.entity.Carriers.clear(l);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        Hazards.clear(null);
        dev.awaken.mobs.entity.Carriers.clear(null);
        dev.awaken.mobs.entity.ToldBite.reset(false);
        dev.awaken.mobs.entity.Finishers.clear();
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Mobs.forget(event.getEntity());
        dev.awaken.mobs.entity.FairGuard.forget(event.getEntity().getUUID());
        dev.awaken.mobs.entity.Finishers.forget(event.getEntity().getUUID());
    }
}
