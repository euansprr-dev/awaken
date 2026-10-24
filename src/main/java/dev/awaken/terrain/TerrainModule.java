package dev.awaken.terrain;

import net.neoforged.bus.api.IEventBus;

/** Awakened Lands (docs/SPEC.md §15, docs/plan-lands.html): terrain, biomes, trees and living foliage. */
public final class TerrainModule {
    private TerrainModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(LandsPack::onAddPackFinders);
        TerrainCommands.register();
        TerrainTests.register();
        BiomeTableTests.register();
    }
}
