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

    /** The hang lines' movement tag (SPEC §16.9). */
    static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> LINES =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_lines"));

    /**
     * The plan's SHA-256. A changed plan changes every new world's city and its graphs: regenerate it on purpose
     * ({@code python3 -I tools/city/plan.py}), then update this pin in the same commit.
     */
    static final String PLAN_SHA256 = "575dde8a3fb33f52b8615c71187b339d041a107587b72f3022997dba8b999625";

    /** A city placed at (x, z) for tests (carved, so no plate fill is asked of a GameTest world). */
    static CityLayout layoutAt(int x, int z) {
        List<int[]> river = new ArrayList<>();
        for (int[] p : CityBasin.RIVER) river.add(new int[]{x + p[0], z + p[1]});
        CitySite site = new CitySite(x, z, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, CityBasin.STREET_Y, river,
                CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, true);
        return new CityLayout(CityPlan.get(), site);
    }

    static void register() {
        // TEMPORARY (W3): the whole city's painted cells as one number, before and after the grammar's generalisation
        AwakenTests.register("city_paint_fingerprint_tmp", 2400, h -> {
            CityLayout l = layoutAt(0, 0);
            CityBuffer buf = new CityBuffer(16, 16);
            long hash = 1469598103934665603L;
            long cells = 0;
            for (long ck : l.chunks) {
                int cx = net.minecraft.world.level.ChunkPos.getX(ck), cz = net.minecraft.world.level.ChunkPos.getZ(ck);
                CityPainter.paintChunk(l, cx, cz, 20261024L, buf);
                for (int i = 0; i < buf.cells.length; i++) {
                    BlockState s = buf.cells[i];
                    int id = s == null ? -1 : net.minecraft.world.level.block.Block.getId(s);
                    hash = (hash ^ id) * 1099511628211L;
                    if (s != null) cells++;
                }
                for (boolean p : buf.plate) hash = (hash ^ (p ? 1 : 0)) * 1099511628211L;
            }
            dev.awaken.Awaken.LOG.info("[city] PAINT FINGERPRINT {} chunks {} cells {}", Long.toHexString(hash), l.chunks.length, cells);
            h.succeed();
        });
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
            // each finished chunk is held by the timed city_hold ticket, so the city isn't unloaded before the player joins
            var tickets = h.getLevel().getDataStorage().computeIfAbsent(net.minecraft.world.level.TicketStorage.TYPE);
            String held = tickets.getTicketDebugString(keys.get(24), false);
            h.assertTrue(held.contains("awaken:city_hold"), "the centre chunk is held by city_hold: " + held);
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

        // ---- Milestone 4 (B1): the facade grammar's climbing grid --------------------------------------------------

        // every street face has a climb line (a drainpipe, a trellis, a hoist rope) at most 12 apart, from the street to the eave
        AwakenTests.register("city_climb_lines", 200, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            CityPainter.Ctx c = new CityPainter.Ctx(l, seed);
            CityBuffer buf = new CityBuffer(24, 24);
            int runs = 0, lines = 0;
            for (CityPlan.Plot p : l.plan.plots) {
                CityGrammar.Facade f = CityGrammar.facadeOf(c, p);
                boolean painted = false;
                for (int sd = 0; sd < 4; sd++) {
                    int len = f.up.length(sd), a = -1;
                    for (int t = 0; t <= len; t++) {
                        boolean street = t < len && f.upClass(sd, t) == CityGrammar.F_STREET;
                        if (street && a < 0) a = t;
                        if (street || a < 0) continue;
                        int b = t - 1;
                        int run0 = a;
                        a = -1;
                        if (b - run0 + 1 < 3) continue;
                        if (!painted) {
                            paintAround(l, seed, p.x0() - 5, p.z0() - 5, buf);
                            painted = true;
                        }
                        runs++;
                        // every cell of the face within 6 of a line: lines at most 12 apart, at most 6 from each end
                        int first = -1, last = -1, gap = 0;
                        for (int u = run0; u <= b; u++) {
                            if (!climbColumn(f, buf, sd, u, p)) continue;
                            lines++;
                            if (first < 0) first = u;
                            else gap = Math.max(gap, u - last);
                            last = u;
                        }
                        String where = "plot " + p.id() + " (" + CityPlan.DISTRICTS[p.district()] + ") side " + sd + " columns " + run0 + "-" + b;
                        h.assertTrue(first >= 0, "no climb line on the street face of " + where);
                        h.assertTrue(first - run0 <= 6 && b - last <= 6 && gap <= 12,
                                "climb lines too sparse on " + where + " (first " + (first - run0) + " in, gap " + gap + ", last " + (b - last) + " from the end)");
                    }
                }
            }
            h.assertTrue(runs > 500, "street faces found: " + runs);
            dev.awaken.Awaken.LOG.info("[city] climb lines: {} street faces, {} climb lines", runs, lines);
            h.succeed();
        });

        // the grid: in a climbing face's outside plane every hold sits in an odd row (with the row over it clear), the
        // floor courses are there, and running faces have nothing proud in them
        AwakenTests.register("city_grid_rows", 200, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            CityPainter.Ctx c = new CityPainter.Ctx(l, seed);
            CityBuffer buf = new CityBuffer(24, 24);
            int cells = 0, courses = 0, missing = 0;
            String firstMissing = "";
            for (CityPlan.Plot p : l.plan.plots) {
                if ((p.id() & 1) == 1) continue; // a sample: every other plot
                CityGrammar.Facade f = CityGrammar.facadeOf(c, p);
                paintAround(l, seed, p.x0() - 5, p.z0() - 5, buf);
                int g = p.lift(), eave = p.eave();
                for (int sd = 0; sd < 4; sd++) {
                    int len = f.up.length(sd);
                    for (int t = 0; t < len; t++) {
                        int fc = f.upClass(sd, t);
                        if (fc == CityGrammar.F_NONE) continue;
                        int[] o = f.outsideCell(f.up, sd, t);
                        // (an inside corner, or a neighbour's jetty: by design neither face puts anything there)
                        if (CityGrammar.shared(l.plan, p, sd, o[0], o[1]) || CityGrammar.underForeignJetty(l.plan, p, o[0], o[1])) continue;
                        String at = "plot " + p.id() + " (" + CityPlan.DISTRICTS[p.district()] + ") side " + sd + " column " + t;
                        for (int r = 2; r < eave; r++) {
                            BlockState s = buf.get(o[0], g + r, o[1]);
                            // (trees grown against a wall are the plan's greenery, not the facade)
                            if (s == null || s.isAir() || s.is(net.minecraft.tags.BlockTags.CLIMBABLE) || s.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                            if (g + r <= l.plan.lift(o[0], o[1])) continue;
                            boolean lip = lip(s) && clearOver(buf.get(o[0], g + r + 1, o[1]));
                            if (fc < CityGrammar.F_BACK) {
                                // (a neighbour's face turning the corner into this one may reach in: only this face's own pieces count)
                                boolean doorHood = r == 3 && sd == p.front();
                                h.assertTrue(!lip || doorHood || !facesAs(s, sd), "a hold in a running face, row " + r + " at " + at + ": " + s);
                                continue;
                            }
                            cells++;
                            h.assertTrue(!lip || (r & 1) == 1, "a hold in an even row (" + r + ") at " + at + ": " + s);
                        }
                        if (fc < CityGrammar.F_BACK || p.storeys() < 2) continue;
                        for (int r = 9; r < eave; r += 4) {
                            if (g + r <= l.plan.lift(o[0], o[1])) continue;
                            BlockState s = buf.get(o[0], g + r, o[1]);
                            courses++;
                            if (s != null && (lip(s) || s.is(net.minecraft.tags.BlockTags.CLIMBABLE) || s.is(dev.awaken.city.kit.CityKit.ROPE.get()))) continue;
                            if (missing++ == 0) firstMissing = "row " + r + " at " + at + ": " + s;
                        }
                    }
                }
            }
            h.assertTrue(cells > 20000 && courses > 5000, "outside-plane cells checked: " + cells + ", course cells: " + courses);
            h.assertTrue(missing * 100 <= courses, missing + " of " + courses + " floor-course cells have no hold (more than 1%); first " + firstMissing);
            dev.awaken.Awaken.LOG.info("[city] grid: {} outside cells, {} course cells, {} without a hold (first {})", cells, courses, missing, firstMissing);
            h.succeed();
        });

        // the grid holds for the Runner's own hang (body.RunnerRules.findEdge, the code the free-run uses): a body jumping
        // at any hold of a climbing face's window stack from 1.8 below it catches that hold, and from the street the
        // row-3 hood is caught at the top of a standing jump
        AwakenTests.register("city_grid_catchable", 200, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            CityPainter.Ctx c = new CityPainter.Ctx(l, seed);
            CityBuffer buf = new CityBuffer(24, 24);
            net.minecraft.world.level.BlockGetter view = new net.minecraft.world.level.BlockGetter() {
                @Override
                public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos p) {
                    return null;
                }

                @Override
                public BlockState getBlockState(BlockPos p) {
                    BlockState s = buf.get(p.getX(), p.getY(), p.getZ());
                    return s == null ? Blocks.AIR.defaultBlockState() : s;
                }

                @Override
                public net.minecraft.world.level.material.FluidState getFluidState(BlockPos p) {
                    return getBlockState(p).getFluidState();
                }

                @Override
                public int getHeight() {
                    return CityBuffer.H;
                }

                @Override
                public int getMinY() {
                    return CityBuffer.YMIN;
                }
            };
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            var ctx = net.minecraft.world.phys.shapes.CollisionContext.empty();
            int holds = 0, caught = 0, hoods = 0, hoodsCaught = 0, lineTies = 0;
            String first = "";
            java.util.Map<String, Integer> misses = new java.util.TreeMap<>();
            for (CityPlan.Plot p : l.plan.plots) {
                if (p.id() % 3 != 0 || p.storeys() < 2) continue;
                CityGrammar.Facade f = CityGrammar.facadeOf(c, p);
                boolean painted = false;
                int g = p.lift();
                for (int sd = 0; sd < 4; sd++) {
                    if (f.jetty[sd]) continue; // a jettied face is climbed at its ends
                    int len = f.up.length(sd);
                    int ax = sd == CityPlan.N || sd == CityPlan.S ? 2 : 0, sign = sd == CityPlan.N || sd == CityPlan.W ? 1 : -1;
                    for (int t = 1; t < len - 1; t++) {
                        if (f.upClass(sd, t) < CityGrammar.F_BACK || !f.win(sd, t, len, 1)) continue;
                        int[] o = f.outsideCell(f.up, sd, t);
                        if (CityGrammar.shared(l.plan, p, sd, o[0], o[1]) || CityGrammar.underForeignJetty(l.plan, p, o[0], o[1])) continue;
                        if (!painted) {
                            paintAround(l, seed, p.x0() - 5, p.z0() - 5, buf);
                            painted = true;
                        }
                        // the face of the outside cell toward the street, and a body just in front of it
                        double face = switch (sd) {
                            case CityPlan.N -> o[1];
                            case CityPlan.S -> o[1] + 1;
                            case CityPlan.W -> o[0];
                            default -> o[0] + 1;
                        };
                        double body = face - sign * 0.33;
                        double along = (ax == 2 ? o[0] : o[1]) + 0.5;
                        for (int r = 3; r < p.eave(); r += 2) {
                            BlockState s = buf.get(o[0], g + r, o[1]);
                            if (s == null || !lip(s) || !clearOver(buf.get(o[0], g + r + 1, o[1]))) continue;
                            if (g + r <= l.plan.lift(o[0], o[1])) continue;
                            double top = g + r + 1;
                            double py = r == 3 ? g + 1 + 1.25 : top - 1.8;
                            double px = ax == 0 ? body : along, pz = ax == 0 ? along : body;
                            var e = dev.awaken.body.RunnerRules.findEdge(view, ctx, m, px, py, pz, ax, sign, dev.awaken.body.RunnerRules.HANG_REACH,
                                    dev.awaken.body.RunnerRules.HANG_LO, dev.awaken.body.RunnerRules.HANG_HI, 0, 0.2);
                            boolean ok = e != null && Math.abs(e.top() - top) < 0.01;
                            // (a hood is caught "from the street" only where the street in front is level with the house:
                            // on a retaining wall's edge or below a flight it is a hold like the others, out of a jump's reach)
                            if (r == 3 && l.plan.lift(o[0], o[1]) != g) continue;
                            // (M4 B3a: a hang line tied at this course stands where the body would hang; the line is the
                            // hold there, by design (bible §3.7: "each end meets a box or a course"), counted apart)
                            BlockState inFront = buf.get(o[0] + CityGrammar.STEP[sd][0], g + r, o[1] + CityGrammar.STEP[sd][1]);
                            if (!ok && r > 3 && inFront != null && inFront.is(LINES)) {
                                lineTies++;
                                continue;
                            }
                            if (r == 3) {
                                hoods++;
                                if (ok) hoodsCaught++;
                            } else {
                                holds++;
                                if (ok) caught++;
                            }
                            if (!ok) {
                                String why = dev.awaken.body.RunnerRules.whyNot(view, ctx, m, px, py, pz, ax, sign, dev.awaken.body.RunnerRules.HANG_REACH,
                                        dev.awaken.body.RunnerRules.HANG_LO, dev.awaken.body.RunnerRules.HANG_HI).replaceAll("[-0-9.]+", "#");
                                misses.merge("row " + (r == 3 ? "3" : r % 4 == 1 ? "course" : "box") + ": " + s.getBlock().getDescriptionId() + ": " + why, 1, Integer::sum);
                            }
                            if (!ok && first.isEmpty())
                                first = "row " + r + " of plot " + p.id() + " (" + CityPlan.DISTRICTS[p.district()] + ") side " + sd + " column " + t + ": " + s
                                        + " -> " + dev.awaken.body.RunnerRules.whyNot(view, ctx, m, px, py, pz, ax, sign, dev.awaken.body.RunnerRules.HANG_REACH,
                                        dev.awaken.body.RunnerRules.HANG_LO, dev.awaken.body.RunnerRules.HANG_HI);
                        }
                    }
                }
            }
            dev.awaken.Awaken.LOG.info("[city] catchable: {}/{} holds, {}/{} hoods from the street, {} holds where a line ties on; misses by kind {}",
                    caught, holds, hoodsCaught, hoods, lineTies, misses);
            h.assertTrue(holds > 2000 && hoods > 200, "holds checked: " + holds + ", hoods " + hoods);
            h.assertTrue(lineTies * 100 <= holds * 2, lineTies + " holds taken by lines' ties (more than 2% of " + holds + ")");
            h.assertTrue(caught * 100 >= holds * 98, (holds - caught) + " of " + holds + " holds the Runner can't catch; first " + first);
            h.assertTrue(hoodsCaught * 100 >= hoods * 95, (hoods - hoodsCaught) + " of " + hoods + " hoods out of reach from the street; first " + first);
            h.succeed();
        });

        // the kit's climbables climb, and the movement tags (SPEC §16.9) hold none of the "no" language
        AwakenTests.register("city_kit_tags", 20, h -> {
            for (var blk : List.of(dev.awaken.city.kit.CityKit.DRAINPIPE, dev.awaken.city.kit.CityKit.TRELLIS, dev.awaken.city.kit.CityKit.ROPE)) {
                h.assertTrue(blk.get().defaultBlockState().is(net.minecraft.tags.BlockTags.CLIMBABLE), blk.getId() + " is climbable");
            }
            int total = 0;
            for (String name : new String[]{"runner_ledges", "runner_climbs", "runner_lines", "runner_soft", "runner_swings"}) {
                var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id(name));
                for (var holder : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    BlockState s = holder.value().defaultBlockState();
                    total++;
                    h.assertFalse(s.is(net.minecraft.tags.BlockTags.FENCES) || s.is(net.minecraft.tags.BlockTags.WALLS)
                            || s.is(net.minecraft.tags.BlockTags.FENCE_GATES), name + " holds a \"no\" block: " + holder.getRegisteredName());
                }
            }
            h.assertTrue(dev.awaken.city.kit.CityKit.FLOWER_BOX.get().defaultBlockState().is(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_ledges"))), "window boxes are ledges");
            h.assertTrue(total >= 15, "the runner tags are filled: " + total);
            h.succeed();
        });

        // ---- Milestone 4 (B2): the relief, roofs that chain, crossings, named spots ------------------------------------

        // the relief stays walkable: along every street-graph edge, the painted ground climbs by steps a body walks up
        // (0.6 at most from one cell's edge to the next: a stair's half step, never a block), on flights, the bowl's rings,
        // the Terrace's stairs and the bridges' decks alike
        AwakenTests.register("city_relief_steps", 400, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            CityPlan p = l.plan;
            CityPlan.Graph g = p.streetGraph;
            it.unimi.dsi.fastutil.longs.LongOpenHashSet chunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            for (int e = 0; e < g.edges(); e++) {
                if (p.moves.get(g.emove()[e]).equals("door")) continue;
                float ax = g.nx()[g.ea()[e]], az = g.nz()[g.ea()[e]], bx = g.nx()[g.eb()[e]], bz = g.nz()[g.eb()[e]];
                int n = Math.max(2, (int) (Math.hypot(bx - ax, bz - az) * 4));
                for (int k = 0; k <= n; k++) {
                    int x = (int) Math.floor(ax + (bx - ax) * k / n), z = (int) Math.floor(az + (bz - az) * k / n);
                    chunks.add(net.minecraft.world.level.ChunkPos.pack(x >> 4, z >> 4));
                }
            }
            BlockState[] ground = new BlockState[p.size * p.size];
            CityBuffer buf = new CityBuffer(16, 16);
            for (long key : chunks) {
                var cp = net.minecraft.world.level.ChunkPos.unpack(key);
                CityPainter.paintChunk(l, cp.x(), cp.z(), seed, buf);
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++) {
                        int x = buf.px0 + lx, z = buf.pz0 + lz;
                        if (p.inRaster(x, z)) ground[(z - p.origin) * p.size + (x - p.origin)] = buf.get(x, p.lift(x, z), z);
                    }
            }
            int pairs = 0, bad = 0;
            String first = "";
            for (int e = 0; e < g.edges(); e++) {
                if (p.moves.get(g.emove()[e]).equals("door")) continue;
                float ax = g.nx()[g.ea()[e]], az = g.nz()[g.ea()[e]], bx = g.nx()[g.eb()[e]], bz = g.nz()[g.eb()[e]];
                int n = Math.max(2, (int) (Math.hypot(bx - ax, bz - az) * 4));
                int px = Integer.MIN_VALUE, pz = 0;
                for (int k = 0; k <= n; k++) {
                    int x = (int) Math.floor(ax + (bx - ax) * k / n), z = (int) Math.floor(az + (bz - az) * k / n);
                    byte kind = p.kind(x, z);
                    boolean open = CityPlan.walkable(kind) && kind != CityPlan.LOWQUAY || CityPlan.water(kind);
                    if (!open) {
                        px = Integer.MIN_VALUE;
                        continue;
                    }
                    if (px != Integer.MIN_VALUE && (x != px || z != pz) && (x == px || z == pz)) {
                        int d = x > px ? CityPlan.E : x < px ? CityPlan.W : z > pz ? CityPlan.S : CityPlan.N;
                        BlockState a = ground[(pz - p.origin) * p.size + (px - p.origin)], b = ground[(z - p.origin) * p.size + (x - p.origin)];
                        if (a != null && b != null && !a.isAir() && !b.isAir()) {
                            pairs++;
                            if (!stepOk(p.lift(px, pz), a, d, p.lift(x, z), b)) {
                                if (bad++ == 0) first = "from " + px + "," + pz + " (" + a + ") to " + x + "," + z + " (" + b + ")";
                            }
                        }
                    }
                    px = x;
                    pz = z;
                }
            }
            dev.awaken.Awaken.LOG.info("[city] relief steps: {} cell-to-cell steps on {} chunks, {} too tall (first {})", pairs, chunks.size(), bad, first);
            h.assertTrue(pairs > 10000, "steps checked: " + pairs);
            h.assertTrue(bad == 0, bad + " steps along the streets are taller than a stair's half step; first " + first);
            h.succeed();
        });

        // roofs that chain (look bible §3.4): every roof is within a storey of a neighbour (from anywhere on it, at most 4 up
        // onto the next eave and 6 down onto the next roof; neighbours share a wall or face it across a sprint-jump of 4)
        // or has a soft way off (water, or open ground beside it 6 or less under its eave); along a row of houses on one
        // level, neighbouring eaves stand within a storey. Houses alone in their gardens are counted (pipes take you down).
        AwakenTests.register("city_roofs_chain", 60, h -> {
            CityPlan p = CityPlan.get();
            int alone = 0, bad = 0, rows = 0;
            String first = "";
            for (CityPlan.Plot a : p.plots) {
                boolean any = false, ok = false;
                for (CityPlan.Plot b : p.plots) {
                    if (b == a) continue;
                    int gx = Math.max(b.x0() - a.x1() - 1, a.x0() - b.x1() - 1), gz = Math.max(b.z0() - a.z1() - 1, a.z0() - b.z1() - 1);
                    if (gx >= 0 && gz >= 0 || Math.max(gx, gz) > 4) continue;
                    any = true;
                    if ((b.lift() + b.eave()) - (a.lift() + a.top()) <= 4 && (a.lift() + a.eave()) - (b.lift() + b.top()) <= 6) ok = true;
                    boolean row = Math.max(gx, gz) == 0 && a.front() == b.front() && Math.abs(a.lift() - b.lift()) <= 2
                            && ((a.front() == CityPlan.N || a.front() == CityPlan.S) ? gx == 0 : gz == 0);
                    if (row && a.id() < b.id() && Math.abs((a.lift() + a.eave()) - (b.lift() + b.eave())) > 4) rows++;
                }
                if (!any) alone++;
                if (any && !ok && !softLanding(p, a)) {
                    if (bad++ == 0) first = "plot " + a.id() + " (" + CityPlan.DISTRICTS[a.district()] + ", " + a.storeys() + " storeys on " + a.lift() + ")";
                }
            }
            dev.awaken.Awaken.LOG.info("[city] roofs: {} plots, {} alone in their gardens, {} without a neighbour in reach, {} rows out of step",
                    p.plots.size(), alone, bad, rows);
            h.assertTrue(bad == 0, bad + " roofs have no neighbour within a storey and no soft way off; first " + first);
            h.assertTrue(rows == 0, rows + " neighbours in a row stand more than a storey apart");
            h.assertTrue(alone * 10 < p.plots.size(), "most houses have a neighbour in reach: " + alone + " alone");
            h.succeed();
        });

        // a line across every avenue and the Ring at most every 48 blocks wherever houses line it (look bible §3.5): an
        // enclosed walkway between two upper floors, or a hang line's site (B3 hangs it), from the plan's own geometry
        AwakenTests.register("city_avenue_crossings", 60, h -> {
            CityPlan p = CityPlan.get();
            java.util.Map<String, List<CityPlan.Street>> groups = new java.util.LinkedHashMap<>();
            for (int i = 0; i < p.streets.size(); i++) {
                CityPlan.Street s = p.streets.get(i);
                if (s.rank() == 1 || s.name().equals("The Ring"))
                    groups.computeIfAbsent(s.name().isEmpty() ? "avenue " + i : s.name(), k -> new ArrayList<>()).add(s);
            }
            int stretches = 0, checked = 0;
            for (var en : groups.entrySet()) {
                List<double[]> samples = new ArrayList<>();   // chainage, x, z, lined
                double ch = 0;
                CityPlan.Street prev = null;
                for (CityPlan.Street s : en.getValue()) {
                    if (prev != null) ch += Math.hypot(s.ax() - prev.bx(), s.az() - prev.bz());
                    double L = Math.hypot(s.bx() - s.ax(), s.bz() - s.az()), ux = (s.bx() - s.ax()) / Math.max(L, 1e-9), uz = (s.bz() - s.az()) / Math.max(L, 1e-9);
                    for (int k = 0; k <= (int) L; k++) {
                        double x = s.ax() + ux * k, z = s.az() + uz * k;
                        boolean lined = facadeAcross(p, x, z, -uz, ux, s.width() / 2 + 2) || facadeAcross(p, x, z, uz, -ux, s.width() / 2 + 2);
                        samples.add(new double[]{ch + k, x, z, lined ? 1 : 0});
                    }
                    ch += L;
                    prev = s;
                }
                List<Double> at = new ArrayList<>();
                for (CityPlan.Crossing c : p.crossings) {
                    if (!c.street().equals(en.getKey())) continue;
                    double cx = (c.ax() + c.bx()) / 2.0, cz = (c.az() + c.bz()) / 2.0, best = Double.MAX_VALUE, bc = 0;
                    for (double[] sm : samples) {
                        double d = Math.hypot(sm[1] - cx, sm[2] - cz);
                        if (d < best) {
                            best = d;
                            bc = sm[0];
                        }
                    }
                    if (best < 8) at.add(bc);
                }
                int gapless = 0;
                double start = Double.NaN, end = Double.NaN;
                for (int k = 0; k <= samples.size(); k++) {
                    boolean lined = k < samples.size() && samples.get(k)[3] > 0;
                    if (lined) {
                        if (Double.isNaN(start)) start = samples.get(k)[0];
                        end = samples.get(k)[0];
                        gapless = 0;
                    } else if (k == samples.size() || ++gapless >= 20) {
                        if (!Double.isNaN(start)) {
                            stretches++;
                            double last = start;
                            List<Double> in = new ArrayList<>();
                            for (double a : at) if (a >= start - 1 && a <= end + 1) in.add(a);
                            java.util.Collections.sort(in);
                            in.add(end);
                            for (double a : in) {
                                checked++;
                                h.assertTrue(a - last <= 50, en.getKey() + ": " + (int) (a - last) + " blocks without a line across, from "
                                        + (int) last + " to " + (int) a + " along it (houses line it from " + (int) start + " to " + (int) end + ")");
                                last = a;
                            }
                        }
                        start = Double.NaN;
                    }
                }
            }
            long walkways = p.crossings.stream().filter(c -> c.kind().equals("walkway")).count();
            long parts = p.parts.stream().filter(q -> q.kind().equals("walkway")).count();
            h.assertTrue(walkways == parts && walkways > 0, "every walkway is built (a landmark part): " + walkways + " sites, " + parts + " parts");
            for (CityPlan.Crossing c : p.crossings) {
                if (!c.kind().equals("walkway")) continue;
                CityPlan.Plot a = p.plots.get(c.plotA()), b = p.plots.get(c.plotB());
                h.assertTrue(Math.abs(c.ary() - (a.lift() + a.eave())) <= 4 && Math.abs(c.ary() - (b.lift() + b.eave())) <= 4,
                        "a walkway's roof is within a storey of both eaves: plots " + a.id() + ", " + b.id());
            }
            dev.awaken.Awaken.LOG.info("[city] crossings: {} ({} walkways) over {} lined stretches, {} gaps checked", p.crossings.size(), walkways, stretches, checked);
            h.succeed();
        });

        // the named spots other modules ask for (SPEC §16.9, decision 29) resolve in a Carillon world: the gatehouse yard,
        // the quartermaster's and the bounty post's places in it, the Association board's place, and the cellar under the
        // old bell (painted: room to stand on a floor; the cellar a room with a way down to it from the bell square)
        AwakenTests.register("city_spots", 200, h -> {
            BlockPos abs = h.absolutePos(new BlockPos(0, 1, 0));
            CityLayout l = layoutAt(abs.getX(), abs.getZ());
            CityLayout before = City.install(l);
            long seed = 20261024L;
            try {
                Level level = h.getLevel();
                CityBuffer buf = new CityBuffer(24, 24);
                for (String id : new String[]{"high_gate_yard", "bounty_post", "quartermaster", "association_board", "bellwatch_cellar"}) {
                    BlockPos s = CityHooks.spot(level, id);
                    h.assertTrue(s != null, id + " resolves");
                    h.assertTrue(l.inCity(s.getX(), s.getZ()), id + " lies in the city: " + s);
                    int px = s.getX() - l.cx, ry = s.getY() - l.streetY, pz = s.getZ() - l.cz;
                    if (id.equals("association_board")) {
                        // (the atrium is hollowed by the opening's stage; the spot stands in its north wall's row, on the Terrace's floor)
                        h.assertTrue(ry == l.plan.terraceLift + 1 && l.plan.parts.stream().anyMatch(q -> q.kind().equals("assoc_centre")
                                && px > q.x0() && px < q.x1() && pz > q.z0() && pz < q.z1()), "the board's spot is in the Association's atrium: " + s);
                        continue;
                    }
                    paintAround(l, seed, px - 12, pz - 12, buf);
                    BlockState at = buf.get(px, ry, pz), over = buf.get(px, ry + 1, pz), under = buf.get(px, ry - 1, pz);
                    h.assertTrue(at != null && at.isAir() && over != null && over.isAir(), id + " has room to stand: " + at + " / " + over);
                    h.assertTrue(under != null && !under.isAir() && !under.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty(),
                            id + " stands on a floor: " + under);
                }
                h.assertTrue(CityHooks.spot(level, "nowhere") == null, "an unknown spot is null");
                // the cellar: 9 x 9 x 5 of air round its spot, under the Knoll's top; steps down from the bell square
                int[] c = l.plan.spots.get("bellwatch_cellar");
                paintAround(l, seed, c[0] - 12, c[2] - 12, buf);
                int air = 0;
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int ry = 1; ry <= 5; ry++) {
                    BlockState s = buf.get(c[0] + dx, ry, c[2] + dz);
                    if (s != null && (s.isAir() || s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty())) air++;
                }
                h.assertTrue(air >= 9 * 9 * 5 - 4, "the cellar is a room: " + air + " of 405 cells open");
                h.assertTrue(l.plan.lift(c[0], c[2]) >= 8, "the cellar lies under the Knoll's top");
                int stairs = 0;
                for (int dx = 0; dx <= 12; dx++) for (int dz = -6; dz <= 6; dz++) for (int ry = 1; ry <= 8; ry++) {
                    BlockState s = buf.get(c[0] + dx, ry, c[2] + dz);
                    if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) stairs++;
                }
                h.assertTrue(stairs >= 14, "steps lead down to it: " + stairs);
            } finally {
                City.install(before);
            }
            h.succeed();
        });

        // a hanging ladder holds while its column reaches a ladder fixed to a wall, and goes (dropping a ladder) when that does
        AwakenTests.register("city_hanging_ladder", 40, h -> {
            BlockPos wall = new BlockPos(1, 3, 2), top = new BlockPos(1, 3, 1);
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH);
            BlockState hung = dev.awaken.city.kit.CityKit.HANGING_LADDER.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.NORTH);
            h.setBlock(wall, Blocks.STONE);
            h.setBlock(top, ladder);
            h.setBlock(top.below(), hung);
            h.setBlock(top.below(2), hung);
            h.assertTrue(h.getBlockState(top.below(2)).is(dev.awaken.city.kit.CityKit.HANGING_LADDER.get()), "two ladders hang under a fixed one");
            h.assertTrue(hung.canSurvive(h.getLevel(), h.absolutePos(top.below(2))), "the lower one holds through the one above it");
            h.assertTrue(!hung.canSurvive(h.getLevel(), h.absolutePos(new BlockPos(3, 3, 1))), "a ladder alone in the air does not hold");
            h.assertTrue(hung.is(net.minecraft.tags.BlockTags.CLIMBABLE), "a hanging ladder climbs");
            h.setBlock(wall, Blocks.AIR);
            h.succeedWhen(() -> h.assertTrue(h.getBlockState(top).isAir() && h.getBlockState(top.below()).isAir()
                    && h.getBlockState(top.below(2)).isAir(), "with the wall gone the whole ladder comes down"));
        });

        // ---- Milestone 4 (B3a): the street-life layer ------------------------------------------------------------------

        // the lines: every avenue crossing site B2 marked is hung (tools/city/props.py) and its rope is painted on the site;
        // the lanes' lines are painted where the plan puts them; the movement tags (SPEC §16.9) hold the street's pieces:
        // runner_lines the hang line, runner_soft the awning and the hay cart, and both are in the painted Market
        AwakenTests.register("city_street_lines", 400, h -> {
            CityLayout l = layoutAt(0, 0);
            long seed = 20261024L;
            CityPlan p = l.plan;
            var lines = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_lines"));
            var soft = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_soft"));
            var swings = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, dev.awaken.Awaken.id("runner_swings"));
            h.assertTrue(dev.awaken.city.kit.StreetKit.HANG_LINE.get().defaultBlockState().is(lines), "the hang line is in runner_lines");
            h.assertTrue(dev.awaken.city.kit.StreetKit.AWNING.get().defaultBlockState().is(soft)
                    && dev.awaken.city.kit.StreetKit.HAY_CART.get().defaultBlockState().is(soft), "awnings and hay carts are in runner_soft");
            h.assertTrue(dev.awaken.city.kit.StreetKit.SIGN_BRACKET.get().defaultBlockState().is(swings), "sign brackets are in runner_swings");
            CityBuffer buf = new CityBuffer(40, 40);
            int sites = 0, cells = 0, held = 0;
            String first = "";
            for (CityPlan.Crossing c : p.crossings) {
                if (!c.kind().equals("line")) continue;
                sites++;
                h.assertTrue(c.hung() != null, "the line site on " + c.street() + " at " + (int) c.ax() + ", " + (int) c.az() + " is hung");
                int[] hb = c.hung();
                paintAround(l, seed, Math.min(hb[0], hb[2]) - 10, Math.min(hb[1], hb[3]) - 10, buf);
                for (int x = Math.min(hb[0], hb[2]); x <= Math.max(hb[0], hb[2]); x++) {
                    for (int z = Math.min(hb[1], hb[3]); z <= Math.max(hb[1], hb[3]); z++) {
                        BlockState s = buf.get(x, hb[4], z);
                        cells++;
                        if (s != null && (s.is(lines) || s.is(dev.awaken.city.kit.StreetKit.LINE_POLE.get()))) held++;
                        else if (first.isEmpty()) first = c.street() + " at " + x + ", " + hb[4] + ", " + z + ": " + s;
                    }
                }
            }
            h.assertTrue(sites >= 13, "the plan's line sites: " + sites);
            h.assertTrue(held * 100 >= cells * 90, (cells - held) + " of " + cells + " cells of the hung sites carry no line; first " + first);
            // the lanes' lines: a sample of one in four, at least nine in ten of their cells hold the rope (a jettied
            // course or a tree's crown takes the odd end cell)
            int lc = 0, lh = 0, n = 0;
            for (CityPlan.Prop pr : p.props) {
                if (!pr.kind().equals("line") || (n++ & 3) != 0) continue;
                paintAround(l, seed, pr.x0() - 10, pr.z0() - 10, buf);
                for (int x = pr.x0(); x <= pr.x1(); x++)
                    for (int z = pr.z0(); z <= pr.z1(); z++) {
                        BlockState s = buf.get(x, pr.ry(), z);
                        lc++;
                        if (s != null && (s.is(lines) || s.is(dev.awaken.city.kit.StreetKit.LINE_POLE.get()))) lh++;
                    }
            }
            h.assertTrue(n > 100, "lines in the plan: " + n);
            h.assertTrue(lh * 100 >= lc * 85, (lc - lh) + " of " + lc + " sampled lane-line cells carry no line");
            // soft landings in the Market: awnings over its shopfronts, hay carts at the towers
            int softs = 0;
            CityBuffer buf16 = new CityBuffer(16, 16);
            for (int cx = -3; cx <= 2; cx++)
                for (int cz = -3; cz <= 2; cz++) {
                    CityPainter.paintChunk(l, (l.cx >> 4) + cx, (l.cz >> 4) + cz, seed, buf16);
                    for (BlockState s : buf16.cells) if (s != null && s.is(soft)) softs++;
                }
            h.assertTrue(softs > 60, "soft landings in the Market's middle: " + softs);
            dev.awaken.Awaken.LOG.info("[city] street lines: {} sites, {}/{} cells held; lanes {}/{} sampled; {} soft blocks in the Market",
                    sites, held, cells, lh, lc, softs);
            h.succeed();
        });

        // lit windows switch with the time of day (a state check): dark at noon, about half the rooms lit at 20:00, a few
        // at 3:00 (night owls), and the switch itself lights and darkens a window in the world
        AwakenTests.register("city_lit_windows", 40, h -> {
            int n = 0, noon = 0, eve = 0, night = 0;
            for (int i = 0; i < 400; i++)
                for (int k = 0; k < 30; k++) {
                    n++;
                    if (CityLights.wanted(i, k, k % 3, 6000)) noon++;
                    if (CityLights.wanted(i, k, k % 3, 14000)) eve++;
                    if (CityLights.wanted(i, k, k % 3, 21000)) night++;
                }
            h.assertTrue(noon == 0, noon + " windows lit at noon");
            h.assertTrue(eve * 100 >= n * 35 && eve * 100 <= n * 65, eve + " of " + n + " windows lit at 20:00 (want 35-65%)");
            h.assertTrue(night > 0 && night * 100 < n * 10, night + " of " + n + " windows lit at 3:00 (want a few)");
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            var level = h.getLevel();
            level.setBlock(at, dev.awaken.city.kit.CityKit.LIT_WINDOW.get().defaultBlockState(), 3);
            h.assertTrue(CityLights.set(level, at, true) && level.getBlockState(at).getValue(dev.awaken.city.kit.KitBlocks.LitWindow.LIT), "the window lights");
            h.assertTrue(!CityLights.set(level, at, true), "a lit window is left alone");
            h.assertTrue(CityLights.set(level, at, false) && !level.getBlockState(at).getValue(dev.awaken.city.kit.KitBlocks.LitWindow.LIT), "the window goes dark");
            level.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!CityLights.set(level, at, true), "a window replaced by anything else is never touched");
            // what finding a house's windows costs (one every other tick while houses near a player are unknown): logged,
            // never asserted (timings on shared CI machines are noise)
            double[] cost = CityLights.measure(layoutAt(0, 0), level.getSeed(), 60);
            h.assertTrue(cost[3] > 0, "the houses' painters make lit windows");
            dev.awaken.Awaken.LOG.info("[city] lit windows: {} ms a house on average, {} ms the worst, over {} houses ({} windows)",
                    String.format(java.util.Locale.ROOT, "%.3f", cost[0]), String.format(java.util.Locale.ROOT, "%.3f", cost[1]),
                    (int) cost[2], (int) cost[3]);
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

    /** Paints the 24x24 area at plan (x0, z0) exactly as the world would (every piece touching it, in id order). */
    static void paintAround(CityLayout l, long seed, int x0, int z0, CityBuffer buf) {
        buf.reset(x0, z0);
        java.util.TreeSet<Integer> ids = new java.util.TreeSet<>();
        for (int cx = (l.cx + x0) >> 4; cx <= (l.cx + x0 + buf.w - 1) >> 4; cx++)
            for (int cz = (l.cz + z0) >> 4; cz <= (l.cz + z0 + buf.d - 1) >> 4; cz++)
                for (int id : l.pieces(cx, cz)) ids.add(id);
        CityPainter.paint(l, seed, buf, ids.stream().mapToInt(Integer::intValue).toArray());
    }

    /**
     * Whether a body walks (steps up at most 0.6, the vanilla step) from ground block a (at row la) to ground block b (at
     * row lb), crossing a's edge on side d and b's opposite edge. Stairs offer their half or full height on each edge.
     */
    static boolean stepOk(int la, BlockState a, int d, int lb, BlockState b) {
        double[] ha = edgeHeights(a, d), hb = edgeHeights(b, (d + 2) & 3);
        if (ha == null || hb == null) return true; // (an obstacle standing in the ground's row: walked round, not up)
        for (double x : ha) for (double y : hb) if (Math.abs((lb + y) - (la + x)) <= 0.601) return true;
        return false;
    }

    private static final Direction[] SIDE = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** The heights a block's top offers along its edge on side s (0 N, 1 E, 2 S, 3 W), or null for an obstacle. */
    static double[] edgeHeights(BlockState s, int side) {
        if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock
                && s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM) {
            Direction f = s.getValue(net.minecraft.world.level.block.StairBlock.FACING);
            int F = java.util.Arrays.asList(SIDE).indexOf(f), L = (F + 3) & 3, R = (F + 1) & 3, B = (F + 2) & 3;
            double[] low = {0.5}, high = {1.0}, both = {0.5, 1.0};
            return switch (s.getValue(net.minecraft.world.level.block.StairBlock.SHAPE)) {
                case STRAIGHT -> side == F ? high : side == B ? low : both;
                case OUTER_LEFT -> side == F || side == L ? both : low;
                case OUTER_RIGHT -> side == F || side == R ? both : low;
                case INNER_LEFT -> side == F || side == L ? high : both;
                case INNER_RIGHT -> side == F || side == R ? high : both;
            };
        }
        var shape = s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return new double[]{0.0};
        double top = shape.max(Direction.Axis.Y);
        return top > 1.01 ? null : new double[]{top};
    }

    /** As the plan generator's facade_across: a plot met walking from (x, z) along (nx, nz) over open street within reach. */
    static boolean facadeAcross(CityPlan p, double x, double z, double nx, double nz, double reach) {
        for (int k = 1; k <= (int) (reach * 2); k++) {
            byte c = p.kind((int) Math.floor(x + nx * k * 0.5), (int) Math.floor(z + nz * k * 0.5));
            if (c == CityPlan.PLOT) return true;
            if (c != CityPlan.AVENUE && c != CityPlan.STREET && c != CityPlan.LANE && c != CityPlan.ALLEY && c != CityPlan.RING
                    && c != CityPlan.STAIR && c != CityPlan.PLAZA) return false;
        }
        return false;
    }

    /** A soft way off a roof: water within 3 of the house, or open ground beside it at most 6 under its eave. */
    static boolean softLanding(CityPlan p, CityPlan.Plot a) {
        int e = a.lift() + a.eave();
        for (int x = a.x0() - 3; x <= a.x1() + 3; x++) {
            for (int z = a.z0() - 3; z <= a.z1() + 3; z++) {
                byte k = p.kind(x, z);
                if (CityPlan.water(k)) return true;
                boolean beside = x >= a.x0() - 1 && x <= a.x1() + 1 && z >= a.z0() - 1 && z <= a.z1() + 1;
                if (beside && (CityPlan.walkable(k) || k == CityPlan.YARD || k == CityPlan.GARDEN || k == CityPlan.FREE) && e - p.lift(x, z) <= 6)
                    return true;
            }
        }
        return false;
    }

    /** A climb line at column u of a side: climbable from the ground in front of it (or a step up) to under the eave. */
    static boolean climbColumn(CityGrammar.Facade f, CityBuffer buf, int sd, int u, CityPlan.Plot p) {
        int[] o = f.outsideCell(f.up, sd, u);
        int g = p.lift();
        int low = Math.max(1, f.plan.lift(o[0], o[1]) - g + 1);
        int r = low;
        if (!climbable(buf.get(o[0], g + r, o[1]))) r++;
        if (!climbable(buf.get(o[0], g + r, o[1]))) return false;
        while (r < p.eave() - 1 && climbable(buf.get(o[0], g + r + 1, o[1]))) r++;
        return r >= p.eave() - 1;
    }

    static boolean climbable(BlockState s) {
        return s != null && s.is(net.minecraft.tags.BlockTags.CLIMBABLE);
    }

    /** A block whose collision top is its own top (a hold, given room over it); fences and walls (taller) are not. */
    static boolean lip(BlockState s) {
        var shape = s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) return false;
        double top = shape.max(net.minecraft.core.Direction.Axis.Y);
        return top > 0.99 && top < 1.01;
    }

    /** Whether a proud piece belongs to a face on side sd (a stair faces the wall, a wall piece faces away; no facing: yes). */
    static boolean facesAs(BlockState s, int sd) {
        Direction d = s.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? s.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : null;
        if (d == null && s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
            // a beam end points out of the face it belongs to
            Direction.Axis ax = s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS);
            return ax == Direction.Axis.Y || ax == ((sd & 1) == 0 ? Direction.Axis.Z : Direction.Axis.X);
        }
        if (d == null) return true;
        Direction out = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[sd];
        return s.getBlock() instanceof net.minecraft.world.level.block.StairBlock ? d == out.getOpposite() : d == out;
    }

    /** Room for the hands over a hold: nothing in the outer half of the cell above it (empty, or no collision). */
    static boolean clearOver(BlockState s) {
        if (s == null || s.isAir()) return true;
        var shape = s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return shape.isEmpty();
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
