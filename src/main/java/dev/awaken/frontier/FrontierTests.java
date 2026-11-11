package dev.awaken.frontier;

import dev.awaken.core.api.FarLanding;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.region.RegionMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Far Landing's checks (SPEC §67), one per mechanism and all pure: the landing is fair and the same every time on
 * twenty seeds; the land under rules 4 is measured from the landing (E round it, the city's ring kept) while rules 3 is
 * untouched; the skiff and its jetty fit their footprint in every facing, with the helm on the deck.
 */
final class FrontierTests {
    private FrontierTests() {}

    static final String[] LANDS = {"plains", "forest", "desert", "river", "jagged_peaks", "meadow", "swamp", "ocean", "birch_forest", "taiga"};

    static void register() {
        AwakenTests.register("frontier_landing_fair", 100, h -> {
            for (long seed = 1; seed <= 20; seed++) {
                long sd = seed;
                FarLanding.Sampler s = new FarLanding.Sampler() {
                    @Override
                    public String biome(int x, int z) {
                        // land in 400-block cells, picked by the seed: a mix of fair and unfair ground, with a sea to the west
                        if (x < -4000) return "ocean";
                        long k = (Math.floorDiv(x, 400) * 341873128712L + Math.floorDiv(z, 400) * 132897987541L) ^ sd * 0x9E3779B97F4A7C15L;
                        k ^= k >>> 29;
                        return LANDS[(int) Math.floorMod(k, LANDS.length)];
                    }

                    @Override
                    public int top(int x, int z) {
                        return 70 + (int) Math.floorMod((x / 64) * 31 + (z / 64) * 17 + sd, 9);
                    }

                    @Override
                    public int seaLevel() {
                        return 62;
                    }
                };
                int[] a = FarLanding.find(seed, 100, -50, s), b = FarLanding.find(seed, 100, -50, s);
                h.assertTrue(a[0] == b[0] && a[1] == b[1], "seed " + seed + ": the same landing every time");
                double d = Math.hypot(a[0] - 100, a[1] + 50);
                h.assertTrue(d >= FarLanding.MIN - 2 && d <= FarLanding.MAX + 2, "seed " + seed + ": 5 to 7 km out, not " + Math.round(d));
                String land = s.biome(a[0], a[1]);
                h.assertTrue(!List.of("ocean", "river", "desert", "jagged_peaks", "swamp").contains(land), "seed " + seed + ": fair ground, not " + land);
            }
            h.succeed();
        });
        AwakenTests.register("frontier_levels_rules_four", 200, h -> {
            RegionMap.Probe plain = (x, z) -> RegionMap.Land.PLAIN;
            int lx = 6000, lz = 0;
            RegionMap far = new RegionMap(4245L, 0, 0, plain, 4, lx, lz);
            h.assertTrue(far.farLanding() && far.landingX() == lx, "a rules 4 map measures from its landing");
            // E land round the landing, to the safe line, in every direction
            int top = 0;
            for (int k = 0; k < 24; k++) {
                double ang = k * Math.PI / 12;
                for (int r = 0; r <= 1300; r += 260) top = Math.max(top, far.levelAt(lx + (int) (Math.cos(ang) * r), lz + (int) (Math.sin(ang) * r)));
            }
            h.assertTrue(top <= 9, "E land to 1,300 round the landing, not level " + top);
            // the city keeps its ring: 8 or 9 just past the Commons
            int ring = far.levelAt(850, 0);
            h.assertTrue(ring >= 8 && ring <= 9, "the city's calm ring: level " + ring);
            // rules 3 ignores a landing: the same land as a map made without one
            RegionMap three = new RegionMap(4245L, 0, 0, plain, 3), threeWith = new RegionMap(4245L, 0, 0, plain, 3, lx, lz);
            h.assertTrue(!threeWith.farLanding(), "rules 3 has no far landing");
            for (int i = 0; i < 40; i++) {
                int x = (i * 1237) % 9000 - 4500, z = (i * 3571) % 9000 - 4500;
                h.assertTrue(three.levelAt(x, z) == threeWith.levelAt(x, z), "rules 3 unchanged at " + x + ", " + z);
            }
            // far from both, the land still climbs: somewhere 3 to 8 km past the landing is harder than E
            int past = 0;
            for (int i = 0; i < 10; i++)
                for (int j = 0; j < 10; j++) past = Math.max(past, far.levelAt(lx + 3000 + i * 500, lz - 2500 + j * 500));
            h.assertTrue(past > 9, "the land gets harder past the landing: " + past);
            h.succeed();
        });
        AwakenTests.register("frontier_skiff_shape", 40, h -> {
            BlockPos origin = new BlockPos(1000, 90, -2000);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Set<BlockPos> taken = new HashSet<>();
                for (List<Skiff.Part> parts : List.of(Skiff.skiff(), Skiff.jetty(), Skiff.landingExtras())) {
                    for (Skiff.Part part : parts) {
                        BlockPos at = Skiff.at(origin, facing, part.x(), part.y(), part.z());
                        h.assertTrue(taken.add(at), facing + ": two parts at " + part.x() + ", " + part.y() + ", " + part.z());
                        h.assertTrue(Skiff.inFootprint(origin, facing, at), facing + ": a part outside the footprint at " + part.x() + ", " + part.z());
                    }
                }
                BlockPos stand = Skiff.at(origin, facing, Skiff.STAND);
                h.assertTrue(Skiff.onDeck(origin, facing, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5), facing + ": the helm's stand is on the deck");
                // the bow points the way it faces: the bow light six ahead of the berth's middle
                h.assertTrue(Skiff.at(origin, facing, 0, 0, 6).equals(origin.relative(facing, 6)), facing + ": the bow points " + facing);
            }
            h.assertTrue(Flights.compass(0, -100).equals("north") && Flights.compass(100, 0).equals("east"), "the compass reads the way Minecraft does");
            h.succeed();
        });
    }
}
