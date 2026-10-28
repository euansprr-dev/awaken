package dev.awaken.village;

import dev.awaken.living.wild.WildField;
import dev.awaken.terrain.api.CitySite;
import dev.awaken.village.road.RoadNet;
import dev.awaken.village.road.RoadWorld;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.server.level.ServerLevel;

/**
 * What people tame (SPEC §41.1), for the wildness field: the city, every settlement on the grid, the regions' towns and
 * the laid roads, each fully tamed over its own ground and fading out beyond. Read from the seed (the grid, the regions'
 * hearts) and the road index, never from the live villages, so a chunk's wildness is the same whenever it is worked out.
 */
public final class WildTamers {
    private WildTamers() {}

    /** Ground and fade per kind of place: a city's core is its own radius. */
    public static final int CITY_REACH = 520, TOWN_RADIUS = 116, TOWN_REACH = 380, VILLAGE_RADIUS = 60, VILLAGE_REACH = 260,
            HAMLET_RADIUS = 38, HAMLET_REACH = 200, WAYHOUSE_RADIUS = 14, WAYHOUSE_REACH = 120;
    /** A road tames a ribbon: most of the wild gone on it, fading over about 70 blocks either side. */
    public static final int ROAD_REACH = 70;
    public static final double ROAD_STRENGTH = 0.7;
    /** The furthest any settlement on the grid reaches (a village's edge and its fade), and a town region's heart. */
    private static final int GRID_LOOK = (int) (VILLAGE_RADIUS + WildField.REACHES * VILLAGE_REACH),
            TOWN_LOOK = (int) (TOWN_RADIUS + WildField.REACHES * TOWN_REACH);
    /** Places are gathered once per tile of this many blocks (a chunk's sixteen cells share one gathering). */
    private static final int TILE = 64;

    static void register() {
        dev.awaken.living.api.Wildness.addTamer(WildTamers::city);
        dev.awaken.living.api.Wildness.addTamer((level, x, z) -> settlements(level, x, z) * towns(level, x, z));
        dev.awaken.living.api.Wildness.addTamer(WildTamers::roads);
    }

    static double city(ServerLevel level, int x, int z) {
        CitySite s = dev.awaken.terrain.api.CityBasin.current();
        if (s == null) return 1.0;
        return WildField.touch(Math.hypot(x - s.x(), z - s.z()), s.coreRadius(), CITY_REACH);
    }

    static double settlements(ServerLevel level, int x, int z) {
        return keep(x, z, false);
    }

    /** The regions meant to hold a town, tamed round their hearts (the town stands near its heart; the fade is wide enough). */
    static double towns(ServerLevel level, int x, int z) {
        return keep(x, z, true);
    }

    private static double keep(int x, int z, boolean towns) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return 1.0;
        double keep = 1.0;
        for (Place p : tile(w, x, z)) {
            if (p.town != towns) continue;
            double d = Math.hypot(p.x - x, p.z - z);
            if (d <= p.look) keep *= WildField.touch(d, p.radius, p.reach);
        }
        return keep;
    }

    /** A settlement or a town region's heart: its own ground, its fade, and how far to look for it. */
    private record Place(int x, int z, int radius, int reach, int look, boolean town) {}

    private record Tile(RoadWorld w, int tx, int tz, Place[] places) {}

    private static final ThreadLocal<Tile> LAST = new ThreadLocal<>();

    /** Every place whose look could reach the tile holding (x, z): pure from the seed, so any thread gathers the same. */
    private static Place[] tile(RoadWorld w, int x, int z) {
        int tx = Math.floorDiv(x, TILE), tz = Math.floorDiv(z, TILE);
        Tile t = LAST.get();
        if (t != null && t.w == w && t.tx == tx && t.tz == tz) return t.places;
        int cx = tx * TILE + TILE / 2, cz = tz * TILE + TILE / 2, half = (int) Math.ceil(TILE * 0.7072);
        java.util.List<Place> out = new java.util.ArrayList<>();
        for (SettlementGrid.Anchor a : SettlementGrid.gridNear(w.seed, cx, cz, GRID_LOOK + half, new int[]{w.cx, w.cz})) {
            out.add(switch (a.kind()) {
                case HAMLET -> place(a, HAMLET_RADIUS, HAMLET_REACH);
                case VILLAGE, MINING -> place(a, VILLAGE_RADIUS, VILLAGE_REACH);
                case TOWN -> place(a, TOWN_RADIUS, TOWN_REACH);
                case WAYHOUSE -> place(a, WAYHOUSE_RADIUS, WAYHOUSE_REACH);
            });
        }
        int look = TOWN_LOOK + half, pitch = w.regions.pitch();
        for (int i = Math.floorDiv(cx - look, pitch) - 1; i <= Math.floorDiv(cx + look, pitch) + 1; i++)
            for (int j = Math.floorDiv(cz - look, pitch) - 1; j <= Math.floorDiv(cz + look, pitch) + 1; j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - cx, h[1] - cz) <= look && RoadNet.townCell(w, i, j))
                    out.add(new Place(h[0], h[1], TOWN_RADIUS, TOWN_REACH, TOWN_LOOK, true));
            }
        Place[] places = out.toArray(new Place[0]);
        LAST.set(new Tile(w, tx, tz, places));
        return places;
    }

    private static Place place(SettlementGrid.Anchor a, int radius, int reach) {
        return new Place(a.x(), a.z(), radius, reach, (int) (radius + WildField.REACHES * reach), false);
    }

    static double roads(ServerLevel level, int x, int z) {
        double reach = WildField.REACHES * ROAD_REACH;
        double d = RoadNet.distanceWithin(x + 0.5, z + 0.5, reach);
        return d < 0 ? 1.0 : WildField.touch(d, 0, ROAD_REACH, ROAD_STRENGTH);
    }

    /**
     * People tame the land (SPEC §41.1): over a roads' world made for a seed, a settlement's own ground and a town region's
     * heart read Tamed, and open land far from every settlement and town reads Wild or Deep.
     */
    static void test(net.minecraft.gametest.framework.GameTestHelper h) {
        long seed = 4242L;
        var reg = h.getLevel().registryAccess();
        var ns = reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset), ns);
        var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, ns.value());
        RoadWorld had = RoadWorld.get();
        ServerLevel level = h.getLevel();
        try {
            RoadWorld w = RoadWorld.of(seed, 0, 0, gen, rs, net.minecraft.world.level.LevelHeightAccessor.create(-64, 384), false);
            RoadWorld.use(w);
            var anchors = SettlementGrid.gridNear(seed, 5000, 0, 3000, new int[]{0, 0});
            h.assertTrue(!anchors.isEmpty(), "settlements on the grid out there");
            for (SettlementGrid.Anchor a : anchors.subList(0, Math.min(4, anchors.size()))) {
                h.assertTrue(settlements(level, a.x(), a.z()) == 0, "a settlement's own ground is fully tamed");
                h.assertTrue(dev.awaken.living.api.Wildness.land(level, a.x(), a.z()) < dev.awaken.living.api.Wildness.TAMED, "and reads Tamed");
            }
            int towns = 0, open = 0;
            for (int i = -4; i <= 4; i++)
                for (int j = -4; j <= 4; j++) {
                    int[] heart = w.regions.heart(i, j);
                    if (RoadNet.townCell(w, i, j)) {
                        towns++;
                        h.assertTrue(towns(level, heart[0], heart[1]) == 0, "a town region's heart is tamed");
                    } else if (!w.regions.swallowed(i, j) && settlements(level, heart[0], heart[1]) > 0.99
                            && towns(level, heart[0], heart[1]) > 0.99) {
                        open++;
                        h.assertTrue(dev.awaken.living.api.Wildness.land(level, heart[0], heart[1]) >= dev.awaken.living.api.Wildness.WILD,
                                "open land far from people is wild: " + heart[0] + "," + heart[1]);
                    }
                }
            h.assertTrue(towns > 0 && open > 0, "the check found towns (" + towns + ") and open land (" + open + ")");
            long t0 = System.nanoTime();
            for (int k = 0; k < 256; k++) dev.awaken.living.api.Wildness.land(level, 4000 + (k >> 4) * 160 + (k & 3) * 4, -3000 + (k >> 4) * 96 + (k >> 2 & 3) * 4);
            dev.awaken.Awaken.LOG.info("[wild] {} towns, {} open regions checked; a chunk's record costs {} ms", towns, open,
                    String.format("%.3f", (System.nanoTime() - t0) / 1e6 / 16));
        } finally {
            RoadWorld.use(had);
        }
        h.succeed();
    }
}
