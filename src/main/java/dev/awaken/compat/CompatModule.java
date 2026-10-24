package dev.awaken.compat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Optional companions from the Awakened Pack (docs/plan-pack.html). Each integration class is only loaded when its mod
 * is present, so Awaken runs exactly the same without any of them. The spoiler guard's record ({@link Discovered}) is
 * plain Awaken state and always runs, so a recipe viewer installed later finds the player's discoveries already there.
 */
public final class CompatModule {
    private CompatModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(CompatModule::setup);
        Discovered.init(modBus);
        if (ModList.get().isLoaded("xaeroworldmap")) WorldMaps.init();
        CompatCommands.register();
        CompatTests.register();
    }

    private static void setup(FMLCommonSetupEvent e) {
        // after every mod is constructed, so the companion's API is ready
        if (ModList.get().isLoaded("distanthorizons")) e.enqueueWork(DistantHorizonsCompat::init);
    }
}
