package dev.awaken.storage;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Storage (SPEC §56): the one list of sorts, Tidy, Pins, chests in the bag's glass. */
public final class StorageModule {
    private StorageModule() {}

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(Sorts::onTags);
        StorageTests.register();
    }
}
