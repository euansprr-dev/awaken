package dev.awaken.city;

import dev.awaken.core.api.CityHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Carillon's GameTests (prefix {@code city_}), only where breakage would be silent: the plan's hash, chunk-local
 * painting, civic protection, door reachability on the street graph (with the Bell Bridge down too), the roofs' way
 * back to the street, and the saved graphs' round trip.
 */
final class CityTests {
    private CityTests() {}

    /**
     * The plan's SHA-256. A changed plan changes every new world's city and its graphs: regenerate it on purpose
     * ({@code python3 -I tools/city/plan.py}), then update this pin in the same commit.
     */
    static final String PLAN_SHA256 = "711a4c1d60b48e293827b00ee5b23e1fb003e77bc93cabc1fae562faa8e981a0";

    /** A city placed at (x, z) for tests (carved, so no plate fill is asked of a GameTest world). */
    static CityLayout layoutAt(int x, int z) {
        List<int[]> river = new ArrayList<>();
        for (int[] p : CityBasin.RIVER) river.add(new int[]{x + p[0], z + p[1]});
        CitySite site = new CitySite(x, z, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, CityBasin.STREET_Y, river,
                CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, true);
        return new CityLayout(CityPlan.get(), site);
    }

    static void register() {
        AwakenTests.register("city_plan_hash", 40, h -> {
            CityPlan p = CityPlan.get();
            h.assertTrue(p.sha256.equals(PLAN_SHA256), "the plan's hash is pinned (got " + p.sha256 + "); a plan change must be deliberate");
            h.assertTrue(p.plots.size() >= 700 && p.plots.size() <= 900, "700-900 plots: " + p.plots.size());
            long enter = p.plots.stream().filter(CityPlan.Plot::enter).count();
            h.assertTrue(enter >= 60, "at least 60 enterable buildings: " + enter);
            h.assertTrue(p.landmarks.size() >= 12, "about a dozen landmarks: " + p.landmarks.size());
            for (CityPlan.Plot q : p.plots) {
                h.assertTrue(q.x1() - q.x0() < 14 && q.z1() - q.z0() < 14, "plot " + q.id() + " is at most 14x14");
            }
            for (CityPlan.Part q : p.parts) {
                h.assertTrue(q.x1() - q.x0() < 26 && q.z1() - q.z0() < 26, "part " + q.landmark() + "/" + q.kind() + " fits its pieces");
            }
            // the river the city builds its quays on is the one terrain carves
            h.assertTrue(p.river.size() == CityBasin.RIVER.size(), "the plan's river has terrain's points");
            for (int i = 0; i < p.river.size(); i++) {
                h.assertTrue(p.river.get(i)[0] == CityBasin.RIVER.get(i)[0] && p.river.get(i)[1] == CityBasin.RIVER.get(i)[1],
                        "river point " + i + " matches CityBasin.RIVER");
            }
            h.assertTrue(p.bridges.stream().anyMatch(b -> b.id().equals("bell_bridge") && b.breakable()), "the Bell Bridge is marked breakable");
            h.succeed();
        });

        AwakenTests.register("city_chunk_alone_matches_context", 200, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            List<long[]> samples = new ArrayList<>();
            // the hard cases first: the spire, a wall tower, the Bell Bridge, the Roost, a crane, a wheel, a gate
            int[][] at = {{0, -46}, {(int) (240 * Math.cos(Math.toRadians(7.5))), (int) (240 * Math.sin(Math.toRadians(7.5)))},
                    {-72, -10}, {-190, -146}, {-174, -133}, {-58, -26}, {-63, 118}, {0, -238}, {-150, -62}, {0, -150}, {30, 30}};
            for (int[] a : at) samples.add(new long[]{a[0] >> 4, a[1] >> 4});
            for (int i = 0; i < 40; i++) {
                long hsh = CityPainter.hash(77, i, 5);
                samples.add(new long[]{Math.floorMod(hsh, 30) - 15, Math.floorMod(hsh >>> 20, 30) - 15});
            }
            CityBuffer alone = new CityBuffer(16, 16);
            CityBuffer region = new CityBuffer(48, 48);
            for (long[] s : samples) {
                int cx = (int) s[0], cz = (int) s[1];
                // paint something else first, so a buffer that keeps stale cells would show
                CityPainter.paintChunk(l, cx + 3, cz - 2, seed, alone);
                CityPainter.paintChunk(l, cx, cz, seed, alone);
                region.reset(((cx - 1) << 4) - l.cx, ((cz - 1) << 4) - l.cz);
                java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int id : l.pieces(cx + dx, cz + dz)) ids.add(id);
                CityPainter.paint(l, seed, region, ids.stream().mapToInt(Integer::intValue).toArray());
                for (int ry = CityBuffer.YMIN; ry <= CityBuffer.YMAX; ry++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            BlockState a = alone.cells[alone.index(lx, ry, lz)];
                            BlockState b = region.cells[region.index(lx + 16, ry, lz + 16)];
                            if (a != b) {
                                h.fail("chunk " + cx + "," + cz + " painted alone differs from in context at local " + lx + " " + ry + " "
                                        + lz + ": " + a + " vs " + b);
                                return;
                            }
                        }
                    }
                }
            }
            h.succeed();
        });

        AwakenTests.register("city_protection", 200, h -> {
            BlockPos rel = new BlockPos(0, 1, 0);
            BlockPos abs = h.absolutePos(rel);
            CityLayout before = City.install(layoutAt(abs.getX(), abs.getZ()));
            AwakenTests.Live live = AwakenTests.survivalPlayer(h, "city-test", rel.above());
            try {
                h.setBlock(rel, Blocks.STONE_BRICKS);
                Level level = h.getLevel();
                h.assertTrue(CityHooks.inCity(level, abs) && CityHooks.isCivic(level, abs), "the test block is in the city and civic");
                h.assertTrue("market".equals(CityHooks.district(level, abs)), "Choir Square is in the market ward: " + CityHooks.district(level, abs));
                boolean broke = live.player().gameMode.destroyBlock(abs);
                h.assertFalse(broke, "a survival player can't break a civic block");
                h.assertTrue(h.getBlockState(rel).is(Blocks.STONE_BRICKS), "the block is still there");
                BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, abs.above());
                h.assertTrue(EventHooks.onBlockPlace(live.player(), snap, Direction.UP), "placing in the city is refused");
                live.player().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                h.assertFalse(EventHooks.onBlockPlace(live.player(), snap, Direction.UP), "creative players may build");
                live.player().setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                // far outside the wall the world is the player's own
                City.install(layoutAt(abs.getX() + 2000, abs.getZ()));
                h.assertFalse(CityHooks.isCivic(level, abs), "outside the city nothing is civic");
                h.assertFalse(EventHooks.onBlockPlace(live.player(), snap, Direction.UP), "placing outside the city is fine");
            } finally {
                live.leave();
                City.install(before);
            }
            h.succeed();
        });

        AwakenTests.register("city_doors_reachable", 100, h -> {
            CityPlan p = CityPlan.get();
            CityPlan.Graph g = p.streetGraph;
            int square = nearest(g, p.squareX, p.squareZ);
            for (String without : new String[]{"", "bell_bridge"}) {
                boolean[] seen = reach(g, square, without);
                int doors = 0, lost = 0;
                String first = "";
                for (int i = 0; i < g.nodes(); i++) {
                    if (!g.nkind()[i].equals("door") && !g.nkind()[i].equals("landmark")) continue;
                    doors++;
                    if (!seen[i]) {
                        if (lost++ == 0) first = g.nkind()[i] + " " + g.nref()[i] + " at " + g.nx()[i] + "," + g.nz()[i];
                    }
                }
                h.assertTrue(doors >= p.plots.size() - 10, "nearly every plot has a door on the graph: " + doors);
                h.assertTrue(lost == 0, lost + " doors can't be reached from Choir Square"
                        + (without.isEmpty() ? "" : " with the Bell Bridge down") + "; first: " + first);
            }
            h.succeed();
        });

        AwakenTests.register("city_roofs_near_street", 100, h -> {
            CityPlan p = CityPlan.get();
            CityPlan.Graph r = p.roofGraph;
            int n = r.nodes();
            // time from each roof to the street: Dijkstra from the street links back over the roof edges
            double[] t = new double[n];
            Arrays.fill(t, Double.MAX_VALUE);
            List<List<double[]>> back = new ArrayList<>();
            for (int i = 0; i < n; i++) back.add(new ArrayList<>());
            PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
            for (int e = 0; e < r.edges(); e++) {
                int a = r.ea()[e], b = r.eb()[e];
                if (b < 0) {
                    if (r.ecost()[e] < t[a]) {
                        t[a] = r.ecost()[e];
                        pq.add(new double[]{t[a], a});
                    }
                } else back.get(b).add(new double[]{a, r.ecost()[e]});
            }
            while (!pq.isEmpty()) {
                double[] top = pq.poll();
                int u = (int) top[1];
                if (top[0] > t[u]) continue;
                for (double[] in : back.get(u)) {
                    int v = (int) in[0];
                    double nt = t[u] + in[1];
                    if (nt < t[v]) {
                        t[v] = nt;
                        pq.add(new double[]{nt, v});
                    }
                }
            }
            int far = 0;
            double worst = 0;
            String which = "";
            for (int i = 0; i < n; i++) {
                if (t[i] > worst) {
                    worst = t[i];
                    which = "roof of plot " + r.nref()[i] + " at " + r.nx()[i] + "," + r.nz()[i];
                }
                if (t[i] > 30) far++;
            }
            h.assertTrue(n == p.plots.size(), "one roof node per plot");
            h.assertTrue(far == 0, far + " roofs are more than 30 s from the street; worst " + (worst == Double.MAX_VALUE ? "unreachable" : worst + " s") + ": " + which);
            h.succeed();
        });

        AwakenTests.register("city_graphs_saved", 60, h -> {
            CityLayout l = layoutAt(1234, -567);
            CityData d = CityData.of(l, 12.5);
            Tag tag = CityData.CODEC.encodeStart(NbtOps.INSTANCE, d).getOrThrow();
            CityData back = CityData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.schemaVersion == CityData.SCHEMA && back.planHash.equals(l.plan.sha256), "schema and plan hash survive");
            h.assertTrue(back.siteX == 1234 && back.siteZ == -567 && back.pregenSeconds == 12.5, "the site survives");
            h.assertTrue(back.street.equals(d.street) && back.roof.equals(d.roof), "both graphs survive the round trip");
            h.assertTrue(d.street.nodes() == l.plan.streetGraph.nodes() && d.roof.edges() == l.plan.roofGraph.edges(), "nothing dropped");
            h.assertTrue(back.tags.contains("bell_bridge"), "the Bell Bridge's edges keep their tag");
            // the start and the square sit where the hooks say
            CityLayout before = City.install(l);
            try {
                BlockPos start = City.start(l);
                h.assertTrue(l.inCity(start.getX(), start.getZ()), "the start is inside the wall");
                h.assertTrue(CityHooks.square(h.getLevel()) != null && CityHooks.square(h.getLevel()).getX() == 1234, "square() is the square");
            } finally {
                City.install(before);
            }
            h.succeed();
        });

        AwakenTests.register("city_pack_new_worlds_only", 20, h -> {
            var server = h.getLevel().getServer();
            var pack = server.getPackRepository().getPack(CityModule.PACK_ID);
            h.assertTrue(pack != null, "the Carillon pack is offered");
            var wants = pack.getRequestedFeatures();
            h.assertFalse(wants.isSubsetOf(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS), "it needs a flag old worlds lack");
            h.assertTrue(server.getPackRepository().getSelectedIds().contains(pack.getId()), "a new world loads it by default");
            h.assertTrue(City.flagged(server), "a new world records awaken:carillon");
            // the city feature is placed only through the pack
            var placed = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                    .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, dev.awaken.Awaken.id("city")));
            h.assertTrue(placed.isPresent(), "the pack carries the city's placed feature");
            h.succeed();
        });

        AwakenTests.register("city_pregen_drives_chunks", 400, h -> {
            // the pregen's own chunk driver on 7x7 chunks far from everything: all generated to FULL, tickets released
            int cx = 6000 + (int) (System.nanoTime() & 255), cz = 6000;
            List<Long> keys = new ArrayList<>();
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) keys.add(net.minecraft.world.level.ChunkPos.pack(cx + x, cz + z));
            long[] order = keys.stream().mapToLong(Long::longValue).toArray();
            CityPregen.Result r = CityPregen.run(h.getLevel(), order, new net.minecraft.world.level.ChunkPos(cx, cz));
            h.assertTrue(r.chunks() == 49 && r.ok() == 49 && r.failed() == 0, "49 chunks generated to FULL: " + r);
            h.succeed();
        });

        // Floating Elder Wood inside the city (M4): the sweep that clears the trees' spill left every bark block ("*_wood")
        // alone, because the city's own trunks are bark, so a cut elder tree left its limbs hanging. The lands' bark is the
        // trees' now; the city's trunks, timber and leaves are still its own; and a limb that rests on nothing is found.
        AwakenTests.register("city_sweep_takes_elder_wood", 40, h -> {
            for (var b : List.of(dev.awaken.terrain.LandsBlocks.ELDER_WOOD, dev.awaken.terrain.LandsBlocks.ELDER_LOG, dev.awaken.terrain.LandsBlocks.LUMEN_WOOD)) {
                h.assertTrue(CityWriter.vegetation(b.get().defaultBlockState()), b.getId() + " is the trees' to sweep");
            }
            for (BlockState own : new BlockState[]{CityBlocks.OAK_TRUNK, CityBlocks.BIRCH_TRUNK, CityBlocks.CHERRY_TRUNK, CityBlocks.DARK_WOOD,
                    CityBlocks.SPRUCE_WOOD, CityBlocks.POST[0], CityBlocks.BEAM_X[1], CityBlocks.OAK_LEAVES}) {
                h.assertTrue(!CityWriter.vegetation(own), own + " is the city's own and stays");
            }
            h.assertTrue(CityWriter.vegetation(Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)),
                    "leaves that rot are swept");
            // a trunk on the ground with a limb touching it corner to corner, and a limb with leaves that rests on nothing
            it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blocks = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
            byte wood = CityTrees.WOOD, held = (byte) (CityTrees.WOOD | CityTrees.GROUNDED);
            blocks.put(BlockPos.asLong(0, 64, 0), held);
            blocks.put(BlockPos.asLong(0, 65, 0), wood);
            blocks.put(BlockPos.asLong(1, 66, 1), wood);
            for (int i = 0; i < 3; i++) blocks.put(BlockPos.asLong(8 + i, 80, 8), wood);
            blocks.put(BlockPos.asLong(11, 81, 8), CityTrees.LEAF);
            int[] seen = new int[3];
            int n = CityTrees.groups(blocks, c -> true, (cells, grounded, clipped) -> {
                seen[grounded ? 0 : 1] += cells.size();
                if (clipped) seen[2]++;
            });
            h.assertTrue(n == 2 && seen[0] == 3 && seen[1] == 4 && seen[2] == 0, "two groups, the trunk's 3 held and the limb's 4 hanging: " + n + " " + Arrays.toString(seen));
            h.succeed();
        });

        // dev studio: with a request file in the game folder, paint the whole city and dump it for tools/city/render.py
        AwakenTests.register("city_studio_dump", 400, h -> {
            java.nio.file.Path req = h.getLevel().getServer().getServerDirectory().resolve("city-dump.request");
            if (java.nio.file.Files.exists(req)) {
                try {
                    long t0 = System.nanoTime();
                    CityDump.write(h.getLevel().getServer().getServerDirectory().resolve("city-dump.bin"), layoutAt(0, 0), 20261024L);
                    dev.awaken.Awaken.LOG.info("[city] studio dump in {} ms", (System.nanoTime() - t0) / 1_000_000);
                } catch (java.io.IOException e) {
                    h.fail("dump failed: " + e);
                    return;
                }
            }
            h.succeed();
        });
    }

    static int nearest(CityPlan.Graph g, double x, double z) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < g.nodes(); i++) {
            double d = Math.hypot(g.nx()[i] - x, g.nz()[i] - z);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    static boolean[] reach(CityPlan.Graph g, int from, String withoutTag) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < g.nodes(); i++) adj.add(new ArrayList<>());
        for (int e = 0; e < g.edges(); e++) {
            if (!withoutTag.isEmpty() && g.etag()[e].equals(withoutTag)) continue;
            adj.get(g.ea()[e]).add(g.eb()[e]);
            adj.get(g.eb()[e]).add(g.ea()[e]);
        }
        boolean[] seen = new boolean[g.nodes()];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        seen[from] = true;
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int v : adj.get(u)) {
                if (!seen[v]) {
                    seen[v] = true;
                    q.add(v);
                }
            }
        }
        return seen;
    }
}
