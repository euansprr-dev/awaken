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
        PlacementTests.register();
        LandsTests.register();
        dev.awaken.terrain.hero.HeroTests.register();
        WindTests.register();
        // the second pair of Awakened biomes: Glass Steppe and Shattered Highlands, and their landmarks
        dev.awaken.terrain.glass.GlassLands.init(modBus);
        dev.awaken.terrain.fault.FaultLands.init(modBus);
        dev.awaken.terrain.landmark.LandmarkWorldgen.init(modBus);
        dev.awaken.terrain.landmark.LandmarkCommands.register();
        dev.awaken.terrain.landmark.LandmarkTests.register();
        // the third pair: Frost Spires and Mirror Marsh (their landmark and hero tree live with the others)
        dev.awaken.terrain.frost.FrostLands.init(modBus);
        dev.awaken.terrain.mire.MireLands.init(modBus);
        dev.awaken.terrain.landmark.Lands3Tests.register();
        // Awakened Alpine (Far Sight 2): erosion, snow lines and mountain forests for new worlds
        dev.awaken.terrain.alpine.AlpineLands.init(modBus);
    }
}
