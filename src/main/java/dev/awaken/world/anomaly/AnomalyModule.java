package dev.awaken.world.anomaly;

import dev.awaken.progression.Progression;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Anomalies (plan v2 §4.3): small events that come to the player in the overworld so there is always something to do
 * within a short walk. See docs/modules/anomalies.md.
 */
public final class AnomalyModule {
    private AnomalyModule() {}

    public static void init(IEventBus modBus) {
        AnomalyContent.register(modBus);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> AnomalyDirector.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> AnomalyDirector.recover(e.getServer()));
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, Stars::onDeath);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, Stars::onJoin);
        Progression.onAwakened(AnomalyDirector::onAwakened);
        AnomalyNow.register();
        AnomalyCommands.register();
        AnomalyTests.register();
    }
}
