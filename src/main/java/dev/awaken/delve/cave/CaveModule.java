package dev.awaken.delve.cave;

import net.neoforged.bus.api.IEventBus;

/**
 * Caves worth entering (SPEC §28.5; PLAN section 8): the cave places (sinkholes, the Delvers' mines, seeps, chutes,
 * grottos) as structures joined to the natural caves, what the server knows of them at run time (the place you are in,
 * its name, a seep's level and heart), the joined Crystal Nest and the lair line's depth word.
 */
public final class CaveModule {
    private CaveModule() {}

    public static void init(IEventBus modBus) {
    }
}
