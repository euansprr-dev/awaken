package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.api.MobHooks;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.signal.Signal;
import net.neoforged.bus.api.IEventBus;

/**
 * Entry point for the mobs module: every hostile creature and boss of AWAKEN. Registers one entity type, spawn egg
 * and loot table per bestiary def, installs the real {@link MobHooks.Spawner}, and wires the /awaken commands and
 * GameTests. See docs/modules/mobs.md for the bestiary.
 */
public final class MobsModule {
    private MobsModule() {}

    public static void init(IEventBus modBus) {
        Awaken.LOG.info("[mobs] bestiary: {} creatures", Bestiary.all().size());
        MobTypes.init(modBus);
        MobSounds.init(modBus);
        MobLoot.init(modBus);
        MobsEvents.init(modBus);
        MobHooks.install(new MobsSpawner());
        Signal.onAction("mobs", Mobs::onSignalAction);
        MobsCommands.register();
        MobsTests.register();
    }
}
