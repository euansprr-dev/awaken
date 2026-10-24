package dev.awaken.terrain;

import net.neoforged.bus.api.IEventBus;

/** Awakened Lands (docs/SPEC.md §15, docs/plan-lands.html): terrain, biomes, trees and living foliage. */
public final class TerrainModule {
    private TerrainModule() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(LandsPack::onAddPackFinders);
        LandsBlocks.init(modBus);
        dev.awaken.terrain.tree.LandsTrees.init(modBus);
        dev.awaken.terrain.hero.HeroWorldgen.init(modBus);
        dev.awaken.terrain.city.CityBasinSetup.init(modBus);
        dev.awaken.terrain.city.CityBasinTests.register();
        TerrainSounds.init(modBus);
        TerrainCommands.register();
        TerrainTests.register();
        BiomeTableTests.register();
        LandsTests.register();
        dev.awaken.terrain.hero.HeroTests.register();
        WindTests.register();
    }
}
