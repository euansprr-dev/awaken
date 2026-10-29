package dev.awaken.village;

import dev.awaken.living.wild.WildField;
import dev.awaken.terrain.api.CitySite;
import dev.awaken.village.road.Holdfasts;
import dev.awaken.village.road.RoadNet;
import dev.awaken.village.road.RoadWorld;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.server.level.ServerLevel;

/**
 * What people tame (SPEC §41.1), for the wildness field: the city, the holdfasts' towns and the laid roads, each fully
 * tamed over its own ground and fading out beyond (Hearths tame their own land in the hearth module). Every other
 * settlement stands empty (SPEC §43.1), so the wild grows back into it. Read from the seed (the regions' hearts, the
 * holdfasts' choice) and the road index, never from the live villages, so a chunk's wildness is the same whenever it is
 * worked out.
 */
public final class WildTamers {
    private WildTamers() {}

    /** Ground and fade per kind of place: a city's core is its own radius. */
    public static final int CITY_REACH = 520, TOWN_RADIUS = 116, TOWN_REACH = 380;
    /** A road tames a ribbon: most of the wild gone on it, fading over about 70 blocks either side. */
    public static final int ROAD_REACH = 70;
    public static final double ROAD_STRENGTH = 0.7;
    /** The furthest a holdfast region's heart reaches (its town's edge and its fade). */
    private static final int TOWN_LOOK = (int) (TOWN_RADIUS + WildField.REACHES * TOWN_REACH);
    /** Places are gathered once per tile of this many blocks (a chunk's sixteen cells share one gathering). */
    private static final int TILE = 64;

    static void register() {
        dev.awaken.living.api.Wildness.addTamer(WildTamers::city);
        dev.awaken.living.api.Wildness.addTamer(WildTamers::towns);
        dev.awaken.living.api.Wildness.addTamer(WildTamers::roads);
    }

    static double city(ServerLevel level, int x, int z) {
        CitySite s = dev.awaken.terrain.api.CityBasin.current();
        if (s == null) return 1.0;
        return WildField.touch(Math.hypot(x - s.x(), z - s.z()), s.coreRadius(), CITY_REACH);
    }

    /** The holdfasts' regions, tamed round their hearts (the town stands near its heart; the fade is wide enough). */
    static double towns(ServerLevel level, int x, int z) {
        RoadWorld w = RoadWorld.get();
        if (w == null) return 1.0;
        double keep = 1.0;
        for (Place p : tile(w, x, z)) {
            double d = Math.hypot(p.x - x, p.z - z);
            if (d <= p.look) keep *= WildField.touch(d, p.radius, p.reach);
        }
        return keep;
    }

    /** A holdfast region's heart: its own ground, its fade, and how far to look for it. */
    private record Place(int x, int z, int radius, int reach, int look) {}

    private record Tile(RoadWorld w, int tx, int tz, Place[] places) {}

    private static final ThreadLocal<Tile> LAST = new ThreadLocal<>();

    /** Every place whose look could reach the tile holding (x, z): pure from the seed, so any thread gathers the same. */
    private static Place[] tile(RoadWorld w, int x, int z) {
        int tx = Math.floorDiv(x, TILE), tz = Math.floorDiv(z, TILE);
        Tile t = LAST.get();
        if (t != null && t.w == w && t.tx == tx && t.tz == tz) return t.places;
        int cx = tx * TILE + TILE / 2, cz = tz * TILE + TILE / 2, half = (int) Math.ceil(TILE * 0.7072);
        java.util.List<Place> out = new java.util.ArrayList<>();
        int look = TOWN_LOOK + half, pitch = w.regions.pitch();
        for (int i = Math.floorDiv(cx - look, pitch) - 1; i <= Math.floorDiv(cx + look, pitch) + 1; i++)
            for (int j = Math.floorDiv(cz - look, pitch) - 1; j <= Math.floorDiv(cz + look, pitch) + 1; j++) {
                int[] h = w.regions.heart(i, j);
                if (Math.hypot(h[0] - cx, h[1] - cz) <= look && RoadNet.townCell(w, i, j) && Holdfasts.is(w, i, j))
                    out.add(new Place(h[0], h[1], TOWN_RADIUS, TOWN_REACH, TOWN_LOOK));
            }
        Place[] places = out.toArray(new Place[0]);
        LAST.set(new Tile(w, tx, tz, places));
        return places;
    }

    static double roads(ServerLevel level, int x, int z) {
        double reach = WildField.REACHES * ROAD_REACH;
        double d = RoadNet.distanceWithin(x + 0.5, z + 0.5, reach);
        return d < 0 ? 1.0 : WildField.touch(d, 0, ROAD_REACH, ROAD_STRENGTH);
    }

    /**
     * People tame the land (SPEC §41.1, §43.1): over a roads' world made for a seed, a holdfast region's heart reads
     * Tamed, while an empty settlement's ground and an empty town region's heart stay as wild as the land round them.
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
        RoadNet.clear();
        try {
            RoadWorld w = RoadWorld.of(seed, 0, 0, gen, rs, net.minecraft.world.level.LevelHeightAccessor.create(-64, 384), false);
            RoadWorld.use(w);
            var anchors = SettlementGrid.gridNear(seed, 5000, 0, 3000, new int[]{0, 0});
            h.assertTrue(!anchors.isEmpty(), "settlements on the grid out there");
            int towns = 0, held = 0;
            for (int i = -8; i <= 8; i++)
                for (int j = -8; j <= 8; j++) {
                    if (!RoadNet.townCell(w, i, j)) continue;
                    int[] heart = w.regions.heart(i, j);
                    if (Holdfasts.is(w, i, j)) {
                        held++;
                        h.assertTrue(towns(level, heart[0], heart[1]) == 0, "a holdfast region's heart is tamed");
                    } else if (towns(level, heart[0], heart[1]) > 0.99) {
                        towns++;
                    }
                }
            h.assertTrue(held > 0 && towns > 0, "the check found holdfasts (" + held + ") and empty town regions (" + towns + ")");
            int open = 0;
            for (SettlementGrid.Anchor a : anchors.subList(0, Math.min(4, anchors.size()))) {
                if (towns(level, a.x(), a.z()) < 0.99 || RoadNet.distanceWithin(a.x() + 0.5, a.z() + 0.5, 200) >= 0) continue;
                open++;
                h.assertTrue(dev.awaken.living.api.Wildness.land(level, a.x(), a.z()) >= dev.awaken.living.api.Wildness.TAMED,
                        "an empty settlement's own ground isn't tamed: " + a.key());
            }
            dev.awaken.Awaken.LOG.info("[wild] {} holdfasts, {} empty town regions, {} empty settlements off the roads checked", held, towns, open);
            long t0 = System.nanoTime();
            for (int k = 0; k < 256; k++) dev.awaken.living.api.Wildness.land(level, 4000 + (k >> 4) * 160 + (k & 3) * 4, -3000 + (k >> 4) * 96 + (k >> 2 & 3) * 4);
            dev.awaken.Awaken.LOG.info("[wild] a chunk's record costs {} ms", String.format("%.3f", (System.nanoTime() - t0) / 1e6 / 16));
        } finally {
            RoadWorld.use(had);
            RoadNet.clear();
        }
        h.succeed();
    }
}
