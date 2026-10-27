package dev.awaken.village.world;

import dev.awaken.Awaken;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The first hamlet, placed on purpose (SPEC §28.6, PLAN 5.2): up the road north of Carillon (or of a classic world's
 * spawn), 760-1,060 blocks out past the Wayfarer camp (450). At the server's start, after terrain has chosen the city's
 * site and before any chunk generates, the spots along that road are tried nearest the nominal first (straight up the
 * road, then a little to either side) until the land takes a hamlet; the settlement grid then puts its start there.
 * The same seed and land always find the same spot.
 */
public final class FirstHamlet {
    private FirstHamlet() {}

    static final int[] OUT = {820, 760, 880, 940, 1000, 1060};
    static final int[] SIDE = {0, -96, 96, -192, 192};

    public static void find(MinecraftServer server) {
        SettlementGrid.firstFound = null;
        LevelStem stem = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return;
        long seed = stem.seedOverride().orElse(server.getWorldGenSettings().options().seed());
        long t0 = System.currentTimeMillis();
        RandomState rs = RandomState.create(server.registryAccess().lookupOrThrow(Registries.NOISE), seed, gen.generatorSettings().value());
        var type = stem.type().value();
        LevelHeightAccessor heights = LevelHeightAccessor.create(type.minY(), type.height());
        GenTerrain t = GenTerrain.of(gen, heights, rs);
        int[] centre = SettlementGrid.centre();
        VillagePlan[] found = new VillagePlan[1];
        SettlementGrid.Anchor a = search(seed, centre, t, found);
        SettlementGrid.firstFound = a;
        // the plan the search just made is the one world generation would make (a pure function of anchor and land):
        // kept, so the structure start doesn't plan it again inside generation (5.5 s on the worldgen queue, 26 Oct)
        if (found[0] != null) Plans.put(found[0]);
        if (a != null) Awaken.LOG.info("[village] the first hamlet's spot: {} {} ({} blocks up the road, {} to the side) found in {} ms", a.x(), a.z(),
                centre[1] - a.z(), a.x() - centre[0], System.currentTimeMillis() - t0);
        else Awaken.LOG.info("[village] no spot up the road takes the first hamlet ({} ms): the nominal one stands", System.currentTimeMillis() - t0);
    }

    /** The studio: where this server found the first hamlet (or null), and to put another there for a while. */
    public static SettlementGrid.@org.jspecify.annotations.Nullable Anchor found() {
        return SettlementGrid.firstFound;
    }

    public static void use(SettlementGrid.@org.jspecify.annotations.Nullable Anchor a) {
        SettlementGrid.firstFound = a;
    }

    /**
     * The first spot up the road (nearest the nominal one first) whose land takes a hamlet, or null. North is the camp's
     * road; where the land north is sea or mountain the other roads out are tried (east, west, south), then a little
     * further out.
     */
    public static SettlementGrid.@org.jspecify.annotations.Nullable Anchor search(long seed, int[] centre, dev.awaken.village.plan.Terrain t) {
        return search(seed, centre, t, new VillagePlan[1]);
    }

    /** The most a creature's land may be at the first hamlet (SPEC §21.10): the first place a level-3 player goes. */
    public static final int MAX_LEVEL = 12;

    /**
     * As {@link #search}, handing back the winning spot's plan in {@code found[0]}. The land's level is checked too
     * (SPEC §21.10: nothing the first thread sends you to is a trap): the spots at level {@link #MAX_LEVEL} or less are
     * tried first, then, only if none takes a hamlet, any. The level reads the region map with no biome tilt, which moves
     * a land by a couple of levels at most.
     */
    static SettlementGrid.@org.jspecify.annotations.Nullable Anchor search(long seed, int[] centre, dev.awaken.village.plan.Terrain t, VillagePlan[] found) {
        var map = new dev.awaken.living.region.RegionMap(seed, centre[0], centre[1], (x, z) -> dev.awaken.living.region.RegionMap.Land.PLAIN);
        SettlementGrid.Anchor a = search(seed, centre, t, found, map, MAX_LEVEL);
        return a != null ? a : search(seed, centre, t, found, map, Integer.MAX_VALUE);
    }

    private static SettlementGrid.@org.jspecify.annotations.Nullable Anchor search(long seed, int[] centre, dev.awaken.village.plan.Terrain t, VillagePlan[] found,
                                                                             dev.awaken.living.region.RegionMap map, int maxLevel) {
        SettlementGrid.Anchor nominal = SettlementGrid.nominalFirst(seed, centre);
        int[][] roads = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int[] road : roads) {
                for (int out : OUT) {
                    int far = out + pass * 240;
                    for (int side : SIDE) {
                        int x = centre[0] + road[0] * far + (road[0] == 0 ? 6 + side : 0), z = centre[1] + road[1] * far + (road[1] == 0 ? side : 0);
                        boolean nominalSpot = pass == 0 && road[1] == -1 && out == OUT[0] && side == 0;
                        SettlementGrid.Anchor a = nominalSpot ? nominal : new SettlementGrid.Anchor("first", x, z, VillagePlan.Kind.HAMLET, true, nominal.seed());
                        if (maxLevel != Integer.MAX_VALUE && map.levelAt(a.x(), a.z()) > maxLevel) continue;
                        // a site, and room for a proper hamlet there: four homes at least (plan() is null without a site, so the
                        // site isn't read twice)
                        VillagePlan p = VillagePlanner.plan(Plans.request(a), t);
                        if (p != null && p.buildings.stream().filter(b -> b.use().equals("house")).count() >= 4) {
                            found[0] = p;
                            return a;
                        }
                    }
                }
            }
        }
        return null;
    }
}
