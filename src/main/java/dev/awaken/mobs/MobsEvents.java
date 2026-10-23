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
        Hazards.tick(event.getLevel());
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
