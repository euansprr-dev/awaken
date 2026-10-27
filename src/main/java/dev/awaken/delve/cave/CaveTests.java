package dev.awaken.delve.cave;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.api.Regions;
import dev.awaken.mobs.SeepHearts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The cave places' GameTests (SPEC §28.5), one per mechanism: every place has a route that needs no rope, both ways
 * (in to its end and back out), within the Ascent's numbers, over several seeds and turns, and what it costs a chunk to
 * build and a candidate to find; a seep's rule: five levels over the region, its heart rooted when a player comes, and
 * quiet once the heart falls.
 */
final class CaveTests {
    private CaveTests() {}

    static void register() {
        AwakenTests.register("delve_cave_routes", 200, CaveTests::routes);
        AwakenTests.registerIsolated("delve_seep", 600, CaveTests::seep);
    }

    /** Where each kind is laid for the test (a sinkhole's rim, every other's floor). */
    private static int testY(CaveKind k) {
        return switch (k) {
            case SINKHOLE -> 72;
            case MINE -> 64;
            case SEEP -> -30;
            case CHUTE -> 24;
            case GROTTO -> 12;
        };
    }

    static Grid grid(Spec s) {
        BoundingBox b = s.box();
        Grid g = new Grid(b.minX() - 2, Math.max(-64, b.minY() - 2), b.minZ() - 2, b.maxX() + 2, b.maxY() + 2, b.maxZ() + 2);
        if (s.kind() == CaveKind.SINKHOLE) {
            // open ground at the rim: the sky over it
            for (int x = g.minX(); x <= g.maxX(); x++) for (int z = g.minZ(); z <= g.maxZ(); z++) for (int y = s.p(Sinkhole.RIM); y <= g.maxY(); y++)
                g.set(x, y, z, Blocks.AIR.defaultBlockState());
        }
        CaveStaging.cave(s, g);
        return g;
    }

    private static void routes(GameTestHelper h) {
        List<String> fails = new ArrayList<>();
        StringBuilder costs = new StringBuilder();
        for (CaveKind k : CaveKind.values()) {
            double worst = 0, sum = 0;
            int chunks = 0;
            for (int seed = 1; seed <= 4; seed++) {
                Spec s = CaveStaging.spec(k, 0, testY(k), 0, seed & 3, 0x5EEDL * seed + k.ordinal());
                Grid g = grid(s);
                // the whole place, one chunk at a time as worldgen builds it: what a chunk costs
                BoundingBox b = s.box();
                for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
                    for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                        Clip clip = new Clip(g, cx << 4, cz << 4);
                        long t0 = System.nanoTime();
                        s.build(clip);
                        double ms = (System.nanoTime() - t0) / 1e6;
                        worst = Math.max(worst, ms);
                        sum += ms;
                        chunks++;
                    }
                }
                int[] start = g.mark("start"), goal = g.mark("goal");
                Routes.Result in = Routes.find(g, start, goal);
                Routes.Result out = Routes.find(g, goal, start);
                if (!in.found()) fails.add(k.id + " seed " + seed + " in: " + Routes.describe(in));
                if (!out.found()) fails.add(k.id + " seed " + seed + " back out: " + Routes.describe(out));
                if (!in.found() || !out.found()) dump(g, k, seed, in.found() ? out : in);
                if (k == CaveKind.SINKHOLE && !diveLands(g, s)) fails.add("sinkhole seed " + seed + ": the dive from the lip misses the pool");
                if (k == CaveKind.MINE) {
                    for (String m : new String[]{"office", "stope", "incline", "crawl", "bridge"}) {
                        Routes.Result r = Routes.find(g, start, g.mark(m));
                        if (!r.found()) fails.add("mine seed " + seed + " to its " + m + ": " + Routes.describe(r));
                    }
                }
            }
            costs.append(String.format(Locale.ROOT, "%s %.2f ms a chunk (worst %.2f); ", k.id, sum / Math.max(1, chunks), worst));
        }
        dev.awaken.Awaken.LOG.info("[delve] cave places' build cost: {}", costs);
        h.assertTrue(fails.isEmpty(), "every place has a route with no rope, both ways: " + String.join("; ", fails));
        // a candidate's search, in a real overworld (the test server's own world is flat): the generator's base columns,
        // the lodes beside it; every kind must find sites, or no place would ever generate
        ServerLevel level = h.getLevel();
        StringBuilder sites = new StringBuilder();
        List<String> none = new ArrayList<>();
        Overworld ow = new Overworld(level, 0x5EED_CAFEL);
        for (CaveKind k : CaveKind.values()) {
            long t0 = System.nanoTime();
            int found = 0, cols = 0, n = 40;
            for (int i = 0; i < n; i++) {
                ChunkPos c = new ChunkPos(i * 53 - 900, i * 47 - 700);
                if (CaveSites.find(ow.context(c), k, false) != null) found++;
                cols += CaveSites.lastSampled;
            }
            sites.append(String.format(Locale.ROOT, "%s %.2f ms a candidate (%d columns, %d of %d found); ", k.id, (System.nanoTime() - t0) / (n * 1e6), cols / n, found, n));
            if (found == 0) none.add(k.id);
        }
        dev.awaken.Awaken.LOG.info("[delve] cave places' site search (vanilla overworld): {}", sites);
        h.assertTrue(none.isEmpty(), "every kind finds sites in a real overworld: none for " + none);
        h.succeed();
    }

    /** A failing place's slices round where the search stopped, for reading without the game (run/delve-routes-*.txt). */
    private static void dump(Grid g, CaveKind k, int seed, Routes.Result r) {
        try {
            java.nio.file.Path out = java.nio.file.Path.of("delve-routes-" + k.id + "-" + seed + ".txt");
            StringBuilder sb = new StringBuilder(r.why()).append('\n').append("x from ").append(g.minX()).append(", z from ").append(g.minZ()).append('\n');
            int y0 = r.stuck() == null ? g.minY() : r.stuck()[1] - 3;
            for (int y = Math.max(g.minY(), y0); y <= Math.min(g.maxY(), y0 + 6); y++) {
                sb.append("y ").append(y).append('\n');
                for (int z = g.minZ(); z <= g.maxZ(); z++) {
                    for (int x = g.minX(); x <= g.maxX(); x++) {
                        var b = g.get(x, y, z);
                        char ch = Routes.reached(g, r.seen(), x, y, z) ? 'o' : b.isAir() ? '.' : Routes.water(b) ? '~' : Routes.climbable(b) ? 'H'
                                : b.getBlock() instanceof net.minecraft.world.level.block.StairBlock ? '/' : b.getBlock() instanceof net.minecraft.world.level.block.SlabBlock ? '_'
                                : b.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET) ? ',' : b.is(net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE) ? 'S' : '#';
                        sb.append(ch);
                    }
                    sb.append('\n');
                }
            }
            java.nio.file.Files.writeString(out, sb.toString());
        } catch (java.io.IOException ignored) {
            // only a debugging aid
        }
    }

    /** The column under the lip (and a step in from it) falls into the pool's water. */
    private static boolean diveLands(Grid g, Spec s) {
        int[] d = g.mark("dive");
        if (d == null) return false;
        double a = Math.atan2(s.z() + 0.5 - d[2] - 0.5, s.x() + 0.5 - d[0] - 0.5);
        int sx = (int) Math.floor(d[0] + 0.5 + Math.cos(a) * 1.2), sz = (int) Math.floor(d[2] + 0.5 + Math.sin(a) * 1.2);
        for (int y = d[1]; y > g.minY(); y--) {
            var b = g.get(sx, y, sz);
            if (b.isAir()) continue;
            return Routes.water(b);
        }
        return false;
    }

    /** The vanilla overworld's own generator and noise for a seed, beside the test server's flat world. */
    private static final class Overworld {
        final ServerLevel level;
        final long seed;
        final net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator gen;
        final net.minecraft.world.level.levelgen.RandomState rs;
        final net.minecraft.world.level.biome.Climate.Sampler sampler;
        final net.minecraft.world.level.LevelHeightAccessor height = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);

        Overworld(ServerLevel level, long seed) {
            this.level = level;
            this.seed = seed;
            var ra = level.registryAccess();
            var settings = ra.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                    .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
            var preset = ra.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset), settings);
            rs = net.minecraft.world.level.levelgen.RandomState.create(ra.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, settings.value());
            sampler = rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build());
        }

        Structure.GenerationContext context(ChunkPos c) {
            return new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(), sampler, rs,
                    level.getServer().getStructureTemplateManager(), seed, c, height, b -> true);
        }
    }

    static Structure.GenerationContext context(ServerLevel level, ChunkPos c) {
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        return new Structure.GenerationContext(level.registryAccess(), gen, gen.getBiomeSource(), rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()), rs,
                level.getServer().getStructureTemplateManager(), level.getSeed(), c, level, b -> true);
    }

    /** A view of a grid that writes one chunk's columns only, as worldgen's postProcess does. */
    private record Clip(Grid g, int x0, int z0) implements Sink {
        @Override
        public int minX() {
            return x0;
        }

        @Override
        public int maxX() {
            return x0 + 15;
        }

        @Override
        public int minZ() {
            return z0;
        }

        @Override
        public int maxZ() {
            return z0 + 15;
        }

        @Override
        public int minY() {
            return g.minY();
        }

        @Override
        public int maxY() {
            return g.maxY();
        }

        @Override
        public net.minecraft.world.level.block.state.BlockState get(int x, int y, int z) {
            return g.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
            g.set(x, y, z, state);
        }

        @Override
        public void mark(String what, int x, int y, int z) {
            g.mark(what, x, y, z);
        }
    }

    // ---- a seep ------------------------------------------------------------------------------------------------------

    private static void seep(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 1, 0));
        Spec s = CaveStaging.spec(CaveKind.SEEP, o.getX(), o.getY(), o.getZ(), 0, 0xC0FFEEL);
        BoundingBox box = s.box();
        // rock round it first (the test plot is open ground), then the seep
        CavePiece.LevelSink sink = new CavePiece.LevelSink(level, box, level.getRandom());
        Carve.fill(sink, box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), Blocks.DEEPSLATE.defaultBlockState());
        CaveStaging.cave(s, sink);
        var marks = CavePiece.build(level, s, box);
        CavePlaces.stage(s);
        CaveData data = CaveData.get(level);
        data.wake(s.id());
        BlockPos inside = marks.get("goal");
        h.assertTrue(inside != null && CavePlaces.at(level, inside) != null, "the seep is known where it stands");
        int live = Regions.creatureLevel(level, inside, Regions.Role.HUNTER);
        h.assertTrue(CavePlaces.levelExtra(level, inside) == CavePlaces.SEEP_LEVELS, "a live seep is five levels over its region");
        BlockPos far = inside.offset(80, 0, 0);
        h.assertTrue(CavePlaces.levelExtra(level, far) == 0, "outside the seep, no extra");
        BlockPos socket = marks.get("socket");
        var live1 = AwakenTests.survivalPlayer(h, "Delver", new BlockPos(0, 2, 0));
        var p = live1.player();
        p.teleportTo(level, inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
        h.startSequence()
                .thenWaitUntil(() -> {
                    CavePlaces.live(level, p, s, false);
                    h.assertTrue(SeepHearts.at(level, socket) != null, "the heart is rooted at its socket once someone comes");
                })
                .thenExecute(() -> {
                    LivingEntity heart = SeepHearts.at(level, socket);
                    h.assertTrue(heart != null, "the heart lives");
                    heart.kill(level);
                    h.assertTrue(data.quiet(s.id()), "its death quiets the seep");
                    h.assertTrue(CavePlaces.levelExtra(level, inside) == 0, "a quiet seep is no harder than its region");
                    int quiet = Regions.creatureLevel(level, inside, Regions.Role.HUNTER);
                    // the test plot can land far enough out that the seep's five run into the level cap
                    int expect = Math.min(dev.awaken.living.region.RegionMap.MAX_LEVEL, quiet + CavePlaces.SEEP_LEVELS);
                    h.assertTrue(live == expect, "the creature level falls by five, or to under the cap (" + live + " to " + quiet + ")");
                    CavePlaces.live(level, p, s, true);
                    live1.leave();
                    CavePlaces.STAGED.remove(s);
                })
                .thenSucceed();
    }
}
