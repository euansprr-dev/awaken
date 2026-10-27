package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.TestLand;
import dev.awaken.village.TestMountain;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Roads and towns' GameTests (prefix {@code village_}, SPEC §28.9), one per mechanism: a road laid over the land and
 * painted ({@code village_roads}); a road held high over low land stands on stepped banks ({@code village_road_banks});
 * one held high over a valley crosses on a viaduct of piers and arches ({@code village_road_viaduct});
 * a town, a wayhouse and a mining town planned ({@code village_town_plan}). The Deep Mine's is with the troubles'
 * ({@code village_deep_mine}).
 */
public final class RoadTests {
    private RoadTests() {}

    /**
     * SPEC §21.12: a new world is the wide world (regions 1,600 apart). The GameTest server's own world keeps 900-block regions,
     * so this opens a roads' world over a fresh generator the way the bench does and checks the network still forms on the
     * bigger grid: the first town is found, a highroad joins its region to a neighbour, and the settlement grid is thinner.
     */
    private static void wideRoads(GameTestHelper h) {
        long seed = 4242L;
        var reg = h.getLevel().registryAccess();
        var ns = reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset), ns);
        var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, ns.value());
        var heights = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
        RoadWorld had = RoadWorld.get();
        var hadFirst = dev.awaken.village.world.FirstHamlet.found();
        try {
            RoadNet.clear();
            dev.awaken.village.world.Plans.clear();
            var t = dev.awaken.village.world.GenTerrain.of(gen, heights, rs);
            var site = dev.awaken.terrain.city.SitePicker.pick(gen, rs, heights, false);
            int[] centre = {site.x(), site.z()};
            dev.awaken.village.world.FirstHamlet.use(dev.awaken.village.world.FirstHamlet.search(seed, centre, t));
            RoadWorld w = RoadWorld.of(seed, centre[0], centre[1], gen, rs, heights, true);
            RoadWorld.use(w);
            h.assertTrue(w.regions.rules() == 3 && w.regions.pitch() == 1600, "the roads' world is the wide world: rules " + w.regions.rules());
            int[] ft = w.firstTown;
            h.assertTrue(ft != null, "the first town is found among the bigger regions");
            int laid = 0;
            int[][] tries = {{ft[0], ft[1], 0}, {ft[0], ft[1], 1}, {ft[0] - 1, ft[1], 0}, {ft[0], ft[1] - 1, 1}};
            for (int[] tr : tries) {
                if (w.regions.swallowed(tr[0], tr[1]) || w.regions.swallowed(tr[0] + (tr[2] == 0 ? 1 : 0), tr[1] + (tr[2] == 1 ? 1 : 0))) continue;
                for (Road r : RoadNet.trunk(w, tr[0], tr[1], tr[2])) laid += r.points();
                if (laid > 0) break;
            }
            h.assertTrue(laid > 0, "a highroad joins the first town's region to a neighbour");
            // the grid keeps about half its settlements: a 3,000 circle 5,000 out holds about 31 (the old grid about 62)
            int n = dev.awaken.village.world.SettlementGrid.gridNear(seed, centre[0] + 5000, centre[1], 3000, centre).size();
            h.assertTrue(n >= 15 && n <= 45, "the wide world's settlement grid is thinner: " + n);
            Awaken.LOG.info("[road] wide world: first town region {},{}; a highroad of {} points; {} grid settlements within 3,000 of a point 5,000 out", ft[0], ft[1], laid, n);
        } finally {
            RoadPlanners.settle();
            RoadNet.clear();
            dev.awaken.village.world.Plans.clear();
            dev.awaken.village.world.FirstHamlet.use(hadFirst);
            RoadWorld.use(had);
        }
        h.succeed();
    }

    public static void register() {
        AwakenTests.registerIsolated("village_roads", 600, RoadTests::roads);
        AwakenTests.registerIsolated("village_road_banks", 200, RoadTests::banks);
        AwakenTests.registerIsolated("village_road_viaduct", 200, RoadTests::viaduct);
        AwakenTests.register("village_town_plan", 400, RoadTests::towns);
        AwakenTests.register("village_wide_roads", 1200, RoadTests::wideRoads);
        AwakenTests.register("village_march_ranks", 20, h -> {
            // a march post writes the land's rank range plainly (SPEC §21.10)
            String[] want = {"1–9", "10–24", "25–44", "45–69", "70–99", "100+"};
            for (int r = 0; r < want.length; r++) h.assertTrue(Waymarks.rankRange(r).equals(want[r]), "rank " + r + " reads " + Waymarks.rankRange(r));
            // a straight road east whose land climbs 1 a 4 blocks: E, then D at level 10, then C at 25; one post for each rise, none for a flicker
            int n = 120;
            float[] x = new float[n], z = new float[n];
            int[] y = new int[n];
            for (int i = 0; i < n; i++) x[i] = i * 4;
            var end = new Road.End("A", Road.ENTRY, "a", 0, 0, 0);
            var road = new Road("t", Road.HIGHROAD, x, z, y, new byte[n], new byte[n], end, new Road.End("B", Road.ENTRY, "b", 480, 0, 0), java.util.List.of(), 0, 0);
            var posts = Waymarks.marches(road, (px, pz) -> 1 + px / 4 / 2 + (px / 4 == 60 ? 20 : 0), (px, pz) -> "Black Glades"); // the level 20 spike at one point is a flicker, not a rank
            h.assertTrue(posts.size() >= 2 && posts.stream().allMatch(m -> m.arms().size() == 1 && m.arms().get(0).name().startsWith("Black Glades · Lv ")), "a post for each rise: " + posts.size());
            h.assertTrue(posts.stream().anyMatch(m -> m.arms().get(0).name().endsWith("10–24")) && posts.stream().anyMatch(m -> m.arms().get(0).name().endsWith("25–44")), "D and C posts");
            h.succeed();
        });
        // the roads' bench lays a whole area over the real noise: only when asked for by name (-Ptests=roadbench_)
        if (System.getProperty("awaken.tests", "").contains("roadbench_")) AwakenTests.register("roadbench_area", 1200, RoadTests::bench);
    }

    /** {@link RoadBench} for the seed, radius and cold distance in road-bench.request (or 26102610 1600 2400), written to road-bench.txt. */
    private static void bench(GameTestHelper h) {
        java.nio.file.Path dir = h.getLevel().getServer().getServerDirectory();
        try {
            java.nio.file.Path ask = dir.resolve("road-bench.request");
            String[] a = java.nio.file.Files.exists(ask) ? java.nio.file.Files.readString(ask).trim().split("\\s+") : new String[0];
            long seed = a.length > 0 && !a[0].isEmpty() ? Long.parseLong(a[0]) : 26102610L;
            int radius = a.length > 1 ? Integer.parseInt(a[1]) : 1600, far = a.length > 2 ? Integer.parseInt(a[2]) : 2400;
            java.nio.file.Files.writeString(dir.resolve("road-bench.txt"), RoadBench.run(h.getLevel(), seed, radius, far));
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.error("[road] bench failed", e);
            h.fail("road bench: " + e);
            return;
        }
        h.succeed();
    }

    /**
     * A road across the test valley and its river: found, following the land (never more than two a point, cuttings and
     * banks small), bridged where it crosses water, the same twice; cut into legs whose structure starts reach all of them;
     * painted into the world (the carriageway's paving, a slab's half step, the waymarker leaving the first place with the
     * next place's name on its arm).
     */
    private static void roads(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        TestLand land = TestLand.at(31L, o.getX(), o.getY(), o.getZ());
        RoadPlanner.Keep none = (x, z) -> false;
        int ax = o.getX() + 4, az = o.getZ() - 60, bx = o.getX() + 30, bz = o.getZ() + 170;
        RoadPlanner.Request q = new RoadPlanner.Request(ax, az, land.top(ax, az), 0, 1, bx, bz, land.top(bx, bz), 0, -1, none);
        long t0 = System.nanoTime();
        RoadPlanner.Path a = RoadPlanner.plan(q, land), b = RoadPlanner.plan(q, land);
        long ns = System.nanoTime() - t0;
        h.assertTrue(a != null, "a road crosses the valley: " + RoadPlanner.WHY.get());
        h.assertTrue(java.util.Arrays.equals(a.x(), b.x()) && java.util.Arrays.equals(a.y(), b.y()) && java.util.Arrays.equals(a.flags(), b.flags()),
                "the same ends over the same land make the same road");
        int bridged = 0, worstStep = 0, worstCut = 0;
        for (int i = 0; i < a.points(); i++) {
            if (i > 0) worstStep = Math.max(worstStep, Math.abs(a.y()[i] - a.y()[i - 1]));
            int x = Math.round(a.x()[i]), z = Math.round(a.z()[i]);
            if ((a.flags()[i] & RoadPlanner.BRIDGE) != 0) {
                bridged++;
                continue;
            }
            if (land.water(x, z) == 0 && (a.flags()[i] & (RoadPlanner.CAUSEWAY | RoadPlanner.BOARDWALK)) == 0)
                worstCut = Math.max(worstCut, Math.abs(a.y()[i] - land.top(x, z)));
        }
        h.assertTrue(bridged >= 2, "it bridges the river: " + bridged + " points");
        h.assertTrue(worstStep <= 2, "never more than two blocks a point: " + worstStep);
        h.assertTrue(worstCut <= 7, "cuttings and banks stay small: " + worstCut);
        // the road with its ends and waymarkers, cut into legs
        RoadNet.Entry from = new RoadNet.Entry(ax, az, a.y()[0], 0, 1, "Testby", "test:a", Road.ENTRY, VillagePlan.Kind.VILLAGE.ordinal());
        RoadNet.Entry to = new RoadNet.Entry(bx, bz, a.y()[a.points() - 1], 0, -1, "Otherwick", "test:b", Road.ENTRY, VillagePlan.Kind.TOWN.ordinal());
        Road r = Highroads.lay(null, "test:road", Road.HIGHROAD, from, to, none, land);
        h.assertTrue(r != null && !r.markers().isEmpty(), "laid, with its waymarkers");
        List<Road.Leg> legs = r.legs();
        int covered = 0;
        for (Road.Leg leg : legs) {
            int cx = (leg.startX() << 4) + 8, cz = (leg.startZ() << 4) + 8;
            for (int i = leg.i0(); i <= leg.i1(); i++)
                h.assertTrue(Math.abs(r.x()[i] - cx) <= 108 && Math.abs(r.z()[i] - cz) <= 108, "leg " + leg.index() + "'s point " + i + " within its start's reach");
            h.assertTrue(Math.floorMod(leg.startX(), Road.LATTICE) == 0 && Math.floorMod(leg.startZ(), Road.LATTICE) == 0, "legs start on the lattice");
            covered += leg.i1() - leg.i0();
        }
        h.assertTrue(covered == r.points() - 1, "the legs cover the road: " + covered + " of " + (r.points() - 1));
        // paint the first leg into the world: the paving, the waymarker and its words
        RoadPiece piece = new RoadPiece(legs.getFirst());
        RoadPaint p = piece.paint();
        long p0 = System.nanoTime();
        int chunks = 0;
        for (int cx = (p.ox + p.rx0) >> 4; cx <= (p.ox + p.rx0 + p.rw) >> 4; cx++)
            for (int cz = (p.oz + p.rz0) >> 4; cz <= (p.oz + p.rz0 + p.rd) >> 4; cz++) {
                level.getChunk(cx, cz);
                SettlementPaint.repaint(p, level, cx, cz);
                for (Road.Marker m : r.markers()) if (m.point() <= legs.getFirst().i1()) RoadMarks.words(level.getChunk(cx, cz), level.registryAccess(), p, m);
                chunks++;
            }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks);
        BlockPos road = BlockPos.containing(r.x()[2], r.y()[2], r.z()[2]);
        BlockState pave = level.getBlockState(road);
        h.assertTrue(!pave.isAir() && level.getBlockState(road.above()).isAir() || level.getBlockState(road.above()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock,
                "the carriageway is paved and clear over: " + pave);
        Road.Marker leaving = r.markers().stream().filter(m -> m.point() <= legs.getFirst().i1()).findFirst().orElse(null);
        h.assertTrue(leaving != null, "a waymarker where it leaves Testby");
        BlockPos post = new BlockPos(leaving.x(), leaving.y() + 3, leaving.z());
        h.assertTrue(level.getBlockState(post).is(net.minecraft.tags.BlockTags.LOGS) && level.getBlockState(post.above()).is(net.minecraft.world.level.block.Blocks.LANTERN),
                "its post and its lantern: " + level.getBlockState(post) + ", " + level.getBlockState(post.above()));
        boolean named = false;
        for (Road.Arm arm : leaving.arms()) {
            BlockPos sign = post.offset(RoadMarks.SX[arm.side()], 0, RoadMarks.SZ[arm.side()]);
            if (level.getBlockEntity(sign) instanceof SignBlockEntity s)
                named |= s.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).stream().anyMatch(c -> c.getString().contains("Other") || c.getString().contains("wick"));
        }
        h.assertTrue(named, "an arm names the next place");
        // a long name keeps every word, each line within the board (a wider line loses its end)
        for (String nm : new String[]{"The Sleeping Rest", "The Laughing Goose", "Kettleminsterbury"}) {
            String[] ls = RoadMarks.split(nm);
            boolean fits = ls.length <= 3;
            for (String l : ls) fits &= RoadMarks.width(l) <= RoadMarks.LINE;
            h.assertTrue(fits && String.join(" ", ls).replace("- ", "").equals(nm), "the board holds " + nm + ": " + java.util.Arrays.toString(ls));
        }
        // in worldgen a sign has no level yet: its words go in without one
        var loose = new net.minecraft.world.level.block.entity.HangingSignBlockEntity(BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState());
        RoadMarks.write(loose, level.registryAccess(), RoadMarks.text(leaving.arms().getFirst(), net.minecraft.world.item.DyeColor.BLACK));
        h.assertTrue(loose.isWaxed() && loose.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).hasMessage(false)
                        && loose.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK).hasMessage(false),
                "a sign with no level takes its words, both faces, waxed");
        Awaken.LOG.info(String.format(Locale.ROOT, "[road] test road: %d points, %d bridged, worst step %d, worst cut %d, laid in %.1f ms (twice), %d legs, "
                + "painted %.2f ms a chunk", a.points(), bridged, worstStep, worstCut, ns / 1e6, legs.size(), paintNs / 1e6));
        h.succeed();
    }

    /**
     * A highroad held 30 over a flat floor, a beard's crust left under its middle (SPEC §28.9): it stands on its rock all
     * the way to the floor, through the crust; its banks step down to the floor, no face taller than 10, with turf on
     * their ledges and the land's layered rock in their faces (the old fill was one sheer wall 30 high).
     */
    private static void viaduct(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int ox = o.getX(), oz = o.getZ(), floor = o.getY(), roadY = floor + 24;
        BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = ox - 36; x <= ox + 36; x++) {
            for (int z = oz - 8; z <= oz + 48; z++) {
                for (int y = floor - 4; y < floor; y++) level.setBlock(at.set(x, y, z), stone, 2);
                for (int y = floor; y <= roadY + 6; y++) level.setBlock(at.set(x, y, z), air, 2);
            }
        }
        // the planner: land at the road's height at the ends, the floor between
        int n = 14;
        float[] xs = new float[n], zs = new float[n];
        int[] ys = new int[n], top = new int[n];
        for (int i = 0; i < n; i++) {
            // the line on a block edge: far out on the test grid (x near -15 million) a float can't hold the half
            xs[i] = ox;
            zs[i] = oz - 8 + 4 * i;
            ys[i] = roadY;
            top[i] = i < 2 || i > 11 ? roadY : floor - 1;
        }
        byte[] fl = new byte[n];
        RoadPlanner.viaducts(ys, top, fl);
        int piers = 0;
        for (int i = 0; i < n; i++) {
            h.assertTrue(((fl[i] & RoadPlanner.VIADUCT) != 0) == (i >= 1 && i <= 12), "the viaduct runs from abutment to abutment: point " + i);
            if ((fl[i] & RoadPlanner.PIER) != 0) piers++;
        }
        h.assertTrue((fl[1] & fl[12] & RoadPlanner.PIER) != 0 && piers == 5, "piers on the abutments and evenly between: " + piers);
        RoadPaint p = new RoadPaint(0, xs, zs, ys, fl, new byte[n], 0, n - 1, Road.HIGHROAD, List.of());
        for (int cx = (p.ox + p.rx0) >> 4; cx <= (p.ox + p.rx0 + p.rw) >> 4; cx++)
            for (int cz = (p.oz + p.rz0) >> 4; cz <= (p.oz + p.rz0 + p.rd) >> 4; cz++) SettlementPaint.repaint(p, level, cx, cz);
        java.util.Set<net.minecraft.world.level.block.Block> kinds = new java.util.HashSet<>();
        for (int i : new int[]{4, 7}) {
            int z = oz - 8 + 4 * i;
            for (int y = floor; y < roadY; y++) {
                BlockState b = level.getBlockState(at.set(ox, y, z));
                h.assertTrue(!b.isAir(), "a pier stands from the floor to the deck: air at " + (y - floor) + " under point " + i);
                kinds.add(b.getBlock());
            }
            for (int y = floor; y < roadY - 1; y++)
                h.assertTrue(!level.getBlockState(at.set(ox + 4, y, z)).isAir(), "a rib runs up the pier's face: air at " + (y - floor));
            h.assertTrue(!level.getBlockState(at.set(ox + 5, floor + 1, z)).isAir() && level.getBlockState(at.set(ox + 5, floor + 2, z)).isAir(),
                    "a footing steps out round the pier's foot");
            h.assertTrue(level.getBlockState(at.set(ox + 3, roadY + 2, z)).is(net.minecraft.world.level.block.Blocks.LANTERN), "a lantern over the pier");
        }
        // the arch between points 4 and 7: open under its crown, three of deck over it
        for (int y = floor; y <= roadY - 4; y++) h.assertTrue(level.getBlockState(at.set(ox, y, oz + 14)).isAir(), "the arch is open: a block at " + (y - floor));
        for (int y = roadY - 3; y < roadY; y++) h.assertTrue(!level.getBlockState(at.set(ox, y, oz + 14)).isAir(), "the deck over the crown at " + (y - roadY));
        for (int z = oz + 9; z <= oz + 27; z++) {
            for (int side = -4; side <= 3; side += 7)
                h.assertTrue(level.getBlockState(at.set(ox + side, roadY + 1, z)).is(net.minecraft.tags.BlockTags.WALLS), "a parapet along the edge at " + side + ", " + (z - oz));
            for (int x = ox - 4; x <= ox + 3; x++)
                for (int y = floor; y < roadY; y++) {
                    BlockState b = level.getBlockState(at.set(x, y, z));
                    h.assertTrue(!b.is(net.minecraft.world.level.block.Blocks.DIRT) && !b.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK), "no raw earth under the deck");
                }
        }
        h.assertTrue(kinds.size() >= 3, "dressed stone of a few kinds, not one: " + kinds);
        Awaken.LOG.info("[road] viaduct: {} piers, pier stone {}", piers, kinds);
        h.succeed();
    }

    private static void banks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int ox = o.getX(), oz = o.getZ(), floor = o.getY(), roadY = floor + 30;
        BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = ox - 36; x <= ox + 36; x++) {
            for (int z = oz - 8; z <= oz + 48; z++) {
                for (int y = floor - 4; y < floor; y++) level.setBlock(at.set(x, y, z), stone, 2);
                for (int y = floor; y <= roadY + 6; y++) level.setBlock(at.set(x, y, z), air, 2);
            }
        }
        for (int x = ox - 4; x <= ox + 4; x++)
            for (int z = oz + 16; z <= oz + 24; z++) for (int y = roadY - 5; y <= roadY - 4; y++) level.setBlock(at.set(x, y, z), stone, 2);
        int n = 14;
        float[] xs = new float[n], zs = new float[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = ox + 0.5f;
            zs[i] = oz - 8 + 4 * i;
            ys[i] = roadY;
        }
        RoadPaint p = new RoadPaint(0, xs, zs, ys, new byte[n], new byte[n], 0, n - 1, Road.HIGHROAD, List.of());
        for (int cx = (p.ox + p.rx0) >> 4; cx <= (p.ox + p.rx0 + p.rw) >> 4; cx++)
            for (int cz = (p.oz + p.rz0) >> 4; cz <= (p.oz + p.rz0 + p.rd) >> 4; cz++) SettlementPaint.repaint(p, level, cx, cz);
        int worst = 0, bankCols = 0, turf = 0;
        java.util.Set<net.minecraft.world.level.block.Block> rocks = new java.util.HashSet<>();
        for (int z = oz + 12; z <= oz + 28; z++) {
            for (int x = ox - 3; x <= ox + 3; x++)
                for (int y = floor; y < roadY; y++)
                    h.assertTrue(!level.getBlockState(at.set(x, y, z)).isAir(), "the road stands on its rock to the floor: air at " + (x - ox) + ", " + (y - floor) + ", " + (z - oz));
            for (int side = -1; side <= 1; side += 2) {
                int last = roadY;
                for (int dx = 4; dx <= 34; dx++) {
                    int x = ox + side * dx, top = floor - 1;
                    for (int y = roadY; y >= floor; y--) {
                        BlockState s = level.getBlockState(at.set(x, y, z));
                        if (s.isAir()) continue;
                        top = y;
                        if (top > floor - 1) {
                            bankCols++;
                            if (s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) turf++;
                        }
                        break;
                    }
                    for (int y = floor; y < top; y++) {
                        var b = level.getBlockState(at.set(x, y, z)).getBlock();
                        if (b == net.minecraft.world.level.block.Blocks.STONE || b == net.minecraft.world.level.block.Blocks.ANDESITE || b == net.minecraft.world.level.block.Blocks.TUFF
                                || b == net.minecraft.world.level.block.Blocks.GRANITE || b == net.minecraft.world.level.block.Blocks.CALCITE) rocks.add(b);
                    }
                    worst = Math.max(worst, last - top);
                    last = top;
                }
                h.assertTrue(last == floor - 1, "the bank comes down to the floor by 34 out: " + (last - floor + 1) + " high there");
            }
        }
        h.assertTrue(worst <= 10, "no face of the bank taller than 10: " + worst);
        h.assertTrue(turf * 5 >= bankCols, "turf on the ledges: " + turf + " of " + bankCols + " columns");
        h.assertTrue(rocks.size() >= 3, "the land's layered rock, not one stone: " + rocks);
        Awaken.LOG.info("[road] banks: worst face {}, turf {} of {} columns, rock {}", worst, turf, bankCols, rocks);
        h.succeed();
    }

    /**
     * A walled town on the test valley (25-45 homes, 60-120 people, its wall with towers and two gates or more, the market
     * square's stalls, the inn, the hall, the scribe, the smithy and the bell tower; every house inside the wall, fields out
     * past it; the same plan and the same blocks twice); a wayhouse (the inn, its stable and paddock of horses, four ways
     * out); and a mining town under the test mountain (its head at the foot, its two levels, its winze, its face, the
     * cavern behind it, all inside its square).
     */
    private static void towns(GameTestHelper h) {
        TestLand land = new TestLand(7);
        long t0 = System.nanoTime();
        VillagePlan town = VillagePlanner.plan(req("test:town", 4711L, VillagePlan.Kind.TOWN), land);
        long ns = System.nanoTime() - t0;
        h.assertTrue(town != null && town.kind == VillagePlan.Kind.TOWN, "a town fits the test valley: " + VillagePlanner.WHY.get());
        VillagePlan again = VillagePlanner.plan(req("test:town", 4711L, VillagePlan.Kind.TOWN), land);
        h.assertTrue(again != null && text(town).equals(text(again)), "the same request over the same land makes the same town");
        long homes = town.buildings.stream().filter(b -> b.use().equals("house")).count();
        h.assertTrue(homes >= 25 && homes <= 45, "25-45 homes: " + homes);
        h.assertTrue(town.people.size() >= 60 && town.people.size() <= 120, "60-120 people: " + town.people.size());
        h.assertTrue(town.gates.size() >= 2 && town.towers.size() >= 6, "walls with gates and towers: " + town.gates.size() + " gates, " + town.towers.size() + " towers");
        for (String use : new String[]{"inn", "hall", "scribe", "smithy"})
            h.assertTrue(town.buildings.stream().anyMatch(b -> b.use().equals(use)), "the town has its " + use);
        long stalls = town.props.stream().filter(pr -> pr.kind().equals("stall")).count();
        h.assertTrue(stalls >= 3 && town.bell != null && town.bell.x1() - town.bell.x0() == 4, "a market of stalls and the bell tower: " + stalls + " stalls");
        for (CityPlan.Plot q : town.plots) {
            double mx = (q.x0() + q.x1()) / 2.0, mz = (q.z0() + q.z1()) / 2.0;
            double deg = (Math.toDegrees(Math.atan2(mz, mx)) + 360) % 360;
            if (q.use().equals("barn") || q.use().equals("granary") || q.use().equals("mill")) continue;
            h.assertTrue(Math.hypot(mx, mz) < town.wallRing[(int) deg % 360] - 3, "every house inside the wall: plot " + q.id());
        }
        for (VillagePlan.Field f : town.fields) {
            double mx = (f.x0() + f.x1()) / 2.0, mz = (f.z0() + f.z1()) / 2.0;
            double deg = (Math.toDegrees(Math.atan2(mz, mx)) + 360) % 360;
            h.assertTrue(Math.hypot(mx, mz) > town.wallRing[(int) deg % 360], "fields lie outside the wall");
        }
        int maxLights = 0;
        long p0 = System.nanoTime();
        long[] chunks = town.chunks();
        for (long c : chunks) {
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            h.assertTrue(SettlementPaint.fingerprint(town, cx, cz, 42L) == SettlementPaint.fingerprint(again, cx, cz, 42L), "chunk " + cx + "," + cz + " paints the same twice");
            maxLights = Math.max(maxLights, SettlementPaint.census(town, cx, cz, 42L)[1]);
        }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks.length * 3L);
        h.assertTrue(maxLights <= 10, "a handful of lights a chunk at most: " + maxLights);
        // the wayhouse
        VillagePlan way = VillagePlanner.plan(req("test:way", 911L, VillagePlan.Kind.WAYHOUSE), land);
        h.assertTrue(way != null && way.buildings.stream().anyMatch(b -> b.use().equals("inn")) && way.buildings.stream().anyMatch(b -> b.use().equals("barn")),
                "a wayhouse: the inn and its stable: " + VillagePlanner.WHY.get());
        h.assertTrue(way.pens.stream().anyMatch(pn -> pn.beast().equals("horse")), "its paddock of horses: " + way.pens);
        h.assertTrue(way.exitList.size() >= 3, "three or four ways out of its crossroads: " + way.exitList.size());
        // the mining town under the mountain
        TestMountain mtn = TestMountain.at(8181L, 0, 72, 0);
        VillagePlan mine = mtn.plan("test:mine", 8182L);
        h.assertTrue(mine != null && mine.kind == VillagePlan.Kind.MINING && mine.mine != null, "a mining town at the mountain's foot: " + VillagePlanner.WHY.get());
        VillagePlan.Mine m = mine.mine;
        h.assertTrue(mine.buildings.stream().anyMatch(b -> b.use().equals("assay")), "its assayer's store");
        h.assertTrue(m.level1() < m.lift() - 20 && m.level2() < m.level1() - 6, "two levels under the head: " + m);
        int[][] reach = {m.along(m.winze()), m.along2(m.reach2()), {m.cavernX(), m.cavernZ()}};
        for (int[] c : reach) h.assertTrue(Math.abs(c[0]) <= mine.r && Math.abs(c[1]) <= mine.r, "the workings stay inside the town's square: " + c[0] + "," + c[1]);
        h.assertTrue(mine.people.stream().filter(pp -> pp.job().equals("miner")).count() >= 3, "miners to work it");
        Awaken.LOG.info(String.format(Locale.ROOT, "[road] test towns: %s planned in %.1f ms, %d chunks at %.2f ms a chunk, at most %d lights | %s | %s",
                town, ns / 1e6, chunks.length, paintNs / 1e6, maxLights, way, mine));
        h.succeed();
    }

    static VillagePlanner.Request req(String key, long seed, VillagePlan.Kind kind) {
        return new VillagePlanner.Request(key, seed, 0, 0, kind, false, -124, -124, 139, 139, (x, z) -> false);
    }

    static String text(VillagePlan p) {
        StringBuilder sb = new StringBuilder(p.toString());
        for (CityPlan.Plot q : p.plots) sb.append('|').append(q);
        for (VillagePlan.Street s : p.streets) sb.append('|').append(s.rank()).append(java.util.Arrays.toString(s.x())).append(java.util.Arrays.toString(s.lift()));
        for (int[] t : p.towers) sb.append('|').append(java.util.Arrays.toString(t));
        for (VillagePlan.Person q : p.people) sb.append('|').append(q);
        return sb.toString();
    }

    /** Unused land reader for tests that need none. */
    static final Terrain FLAT = new TestLand(1);
}
