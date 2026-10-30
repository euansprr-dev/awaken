package dev.awaken.terrain.hero;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.LandsBlocks;
import dev.awaken.terrain.mire.MireBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The hero trees: their blueprints and what it costs to build them, a full build in its box (the Matriarch in shallow
 * water, never hanging a tendril into it), and their sites.
 */
public final class HeroTests {
    private HeroTests() {}

    public static void register() {
        // Every blueprint from eight seeds is the tree the art direction describes, and what building one costs. A batch
        // of its own: in the shared one the chunk workers generate round every other test while this one times CPU work
        // on the same cores (a Colossus measured 8.3 ms at its fastest there on CI, 5 ms alone).
        AwakenTests.registerIsolated("terrain_hero_blueprints", 100, h -> {
            StringBuilder log = new StringBuilder();
            for (HeroTree tree : HeroTree.values()) {
                double total = 0, worst = 0, best = Double.MAX_VALUE;
                int seeds = 8;
                for (long warm = 101; warm <= 103; warm++) tree.build(warm); // the JIT first, then the clock
                for (long seed = 1; seed <= seeds; seed++) {
                    long t0 = System.nanoTime();
                    Blueprint bp = tree.build(seed * 1_000_003L);
                    double ms = (System.nanoTime() - t0) / 1e6;
                    total += ms;
                    worst = Math.max(worst, ms);
                    best = Math.min(best, ms);
                    Shape s = shape(bp);
                    String what = tree.getSerializedName() + " seed " + seed + " (" + s + ")";
                    h.assertTrue(s.loose == 0, what + ": loose leaves left in the blueprint");
                    h.assertTrue(s.disconnected == 0, what + ": " + s.disconnected + " cells not joined to the ground");
                    switch (tree) {
                        case ELDER_COLOSSUS -> {
                            h.assertTrue(s.top >= 55 && s.top <= 100, what + " top " + s.top); // an umbrella: the crown top sits near the trunk's height
                            h.assertTrue(s.trunkTop < s.top - 4, what + ": the trunk (" + s.trunkTop + ") ends inside the crown (" + s.top + ")");
                            h.assertTrue(s.width12 >= 6 && s.width12 <= 10, what + " trunk " + s.width12 + " across");
                            h.assertTrue(s.rootReach >= 9 && s.rootReach <= 20, what + " roots reach " + s.rootReach);
                            h.assertTrue(s.leafReach >= 24 && s.leafReach < bp.extent, what + " canopy reaches " + s.leafReach);
                            h.assertTrue(s.leaves >= 8000 && s.gold > 0 && s.gold < s.leaves, what + " leaves " + s.leaves + ", gold " + s.gold);
                        }
                        case LUMEN_SPIRE -> {
                            h.assertTrue(s.trunkTop >= 40 && s.trunkTop <= 55, what + " trunk top " + s.trunkTop);
                            h.assertTrue(s.leafReach >= 9 && s.leafReach <= bp.extent, what + " crown reaches " + s.leafReach);
                            h.assertTrue(s.vines >= 60 && s.tips >= 15, what + " vines " + s.vines + " (" + s.tips + " tips)");
                        }
                        case WEEPING_MATRIARCH -> {
                            // low and wide: a weeping dome about 40 across over a short trunk, a cage of prop roots under it
                            h.assertTrue(s.trunkTop >= 16 && s.trunkTop <= 32, what + " trunk top " + s.trunkTop);
                            h.assertTrue(s.top <= 40, what + " top " + s.top);
                            h.assertTrue(s.width12 >= 4 && s.width12 <= 9, what + " trunk " + s.width12 + " across");
                            h.assertTrue(s.rootReach >= 6 && s.rootReach <= 13, what + " prop roots reach " + s.rootReach);
                            h.assertTrue(s.leafReach >= 16 && s.leafReach < bp.extent, what + " crown reaches " + s.leafReach);
                            h.assertTrue(s.leafReach * 2 > s.top, what + " is wider (" + s.leafReach * 2 + ") than tall (" + s.top + ")");
                            h.assertTrue(s.vines >= 300 && s.tips >= 60, what + " tendrils " + s.vines + " (" + s.tips + " tips)");
                        }
                    }
                    h.assertTrue(s.persistent * 5 <= s.leaves, what + ": " + s.persistent + " of " + s.leaves + " leaves too far from wood");
                    if (seed == 1) log.append(String.format(" %s: top %d, %d across, roots %d, canopy %d, %d leaves (%d persistent), %d vines, %d cells;",
                            tree.getSerializedName(), s.trunkTop, s.width12, s.rootReach, s.leafReach, s.leaves, s.persistent, s.vines, s.cells));
                }
                log.append(String.format(" %s blueprint %.2f ms fastest, %.2f avg, %.2f worst;", tree.getSerializedName(), best, total / seeds, worst));
                // other work on the machine only ever adds time: the fastest seed is the honest cost. The 8 ms budget
                // is the M4's; GitHub's 2-core runners run about 2x slower, so CI gets 3x headroom (still a regression guard)
                double budget = AwakenTests.cpuBudget(8);
                h.assertTrue(best <= budget, tree + " blueprint costs " + best + " ms at its fastest (budget " + budget + ")");
            }
            Awaken.LOG.info("[terrain] hero blueprints:{}", log);
            h.succeed();
        });

        // A whole Colossus on a slope and a whole Spire on flat ground, chunk by chunk as worldgen does: everything inside
        // the reserved box, standing on the ground, nothing loose; the cost per chunk is logged.
        // Far from every other test (thousands of blocks out, its own chunks), and it waits for the light engine before it
        // ends: a Colossus is ten thousand blocks of light updates, and tests that read light levels (dark rooms,
        // torchlight) must not run in its shadow or behind its backlog.
        AwakenTests.registerIsolated("terrain_hero_builds_in_box", 1200, h -> {
            ServerLevel level = h.getLevel();
            StringBuilder log = new StringBuilder();
            HeroTree[] trees = HeroTree.values();
            BlockPos[] origins = new BlockPos[trees.length];
            HeroPiece[] pieces = new HeroPiece[trees.length];
            int[] placedBy = new int[trees.length];
            h.startSequence()
                    .thenExecute(() -> {
                        for (int n = 0; n < trees.length; n++) {
                            HeroTree tree = trees[n];
                            BlockPos origin = origins[n] = h.absolutePos(new BlockPos(0, 2, 0)).offset(6000 + 400 * n, 0, 6000);
                            int cx0 = origin.getX() >> 4, cz0 = origin.getZ() >> 4;
                            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced(cx0 + dx, cz0 + dz, true);
                            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) level.getChunk(cx0 + dx, cz0 + dz);
                            // a slope under the Colossus's roots (they must wall down to it), level ground under the Spire, shallow
                            // water under the Matriarch (its foot is the first block over the ground, as its site search gives)
                            switch (tree) {
                                case ELDER_COLOSSUS -> ground(level, origin, 20, true);
                                case LUMEN_SPIRE -> ground(level, origin, 12, false);
                                case WEEPING_MATRIARCH -> pond(level, origin, 30, 2);
                            }
                            HeroPiece piece = pieces[n] = new HeroPiece(tree, origin, 424242L); // builds and caches the blueprint
                            BoundingBox box = piece.getBoundingBox();
                            int chunks = 0;
                            double worst = 0, total = 0;
                            for (int ccx = box.minX() >> 4; ccx <= box.maxX() >> 4; ccx++) {
                                for (int ccz = box.minZ() >> 4; ccz <= box.maxZ() >> 4; ccz++) {
                                    BoundingBox clip = new BoundingBox(ccx << 4, level.getMinY(), ccz << 4, (ccx << 4) + 15, level.getMaxY(), (ccz << 4) + 15);
                                    long t0 = System.nanoTime();
                                    placedBy[n] += piece.build(level, clip);
                                    double ms = (System.nanoTime() - t0) / 1e6;
                                    total += ms;
                                    worst = Math.max(worst, ms);
                                    chunks++;
                                }
                            }
                            log.append(String.format(" %s: %d blocks over %d chunks, %.2f ms per chunk avg, %.2f worst (live level);",
                                    tree.getSerializedName(), placedBy[n], chunks, total / chunks, worst));
                        }
                    })
                    .thenExecute(() -> {
                        // the light engine works through ten thousand blocks before the next batch: wait until it has (a
                        // fixed 200 ticks of a GameTest server that never waits between ticks can be no time at all)
                        for (HeroPiece piece : pieces) {
                            BoundingBox b = piece.getBoundingBox();
                            AwakenTests.awaitLight(h, new BlockPos(b.minX(), 0, b.minZ()), new BlockPos(b.maxX(), 0, b.maxZ()));
                        }
                        for (int n = 0; n < trees.length; n++) {
                            HeroTree tree = trees[n];
                            int r = switch (tree) {
                                case ELDER_COLOSSUS -> 44;
                                case LUMEN_SPIRE -> 26;
                                case WEEPING_MATRIARCH -> 30;
                            };
                            Check c = check(level, origins[n], r, pieces[n].getBoundingBox());
                            String what = tree.getSerializedName();
                            h.assertTrue(placedBy[n] > (tree == HeroTree.ELDER_COLOSSUS ? 6000 : 1500), what + " built " + placedBy[n] + " blocks");
                            if (tree == HeroTree.WEEPING_MATRIARCH) {
                                // the pond's water is the two layers over the peat: a tendril there would have replaced it
                                int wet = 0;
                                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                                for (int x = -r; x <= r; x++) {
                                    for (int z = -r; z <= r; z++) {
                                        for (int y = 0; y < 2; y++) if (level.getBlockState(m.setWithOffset(origins[n], x, y, z)).is(MireBlocks.WEEPWOOD_TENDRILS.get())) wet++;
                                    }
                                }
                                h.assertTrue(wet == 0, what + ": " + wet + " tendrils hang into the water");
                                h.assertTrue(c.strands >= 200, what + " hangs a curtain of " + c.strands + " tendrils");
                                h.assertTrue(c.floating >= 3, what + ": " + c.floating + " moonblooms float under its crown");
                            }
                            h.assertTrue(c.outside == 0, what + ": " + c.outside + " blocks outside its box (first " + c.firstOutside + ")");
                            h.assertTrue(c.loose == 0, what + ": " + c.loose + " blocks hang loose (first " + c.firstLoose + ")");
                            h.assertTrue(c.decaying == 0, what + ": " + c.decaying + " leaves born decaying");
                            h.assertTrue(c.grounded > 20, what + " stands on the ground (" + c.grounded + " columns)");
                            // left standing out there (no test comes near); the chunks let go
                            int cx0 = origins[n].getX() >> 4, cz0 = origins[n].getZ() >> 4;
                            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) level.setChunkForced(cx0 + dx, cz0 + dz, false);
                        }
                        Awaken.LOG.info("[terrain] hero builds:{}", log);
                    })
                    .thenSucceed();
        });

        // Sites on a real overworld: the search finds level ground in the right biome (here the archetypes: forests, and the
        // swamps for the Matriarch; the GameTest world has no Lithostitched, so nothing places the Awakened biomes), dry
        // or, for a tree that stands in water, shallow enough.
        AwakenTests.register("terrain_hero_sites", 400, h -> {
            ServerLevel level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(source, settings);
            long seed = 777L;
            RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
            var sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
            var resolver = source.createResolver(sampler);
            var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            StringBuilder log = new StringBuilder();
            for (HeroTree tree : HeroTree.values()) {
                var spread = (RandomSpreadStructurePlacement) sets.getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,
                        Awaken.id(tree.getSerializedName()))).value().placement();
                h.assertTrue(spread.spacing() * 16 >= 300 && spread.spacing() * 16 <= 500, tree + ": one candidate per " + spread.spacing() * 16 + " blocks");
                Predicate<Holder<Biome>> valid = tree == HeroTree.WEEPING_MATRIARCH
                        ? b -> b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP) : b -> b.is(BiomeTags.IS_FOREST);
                // candidates whose region has the biome (swamps are rare on one vanilla seed's sample: the search is what's tested)
                int found = 0, candidates = 0;
                for (int i = -15; i <= 15 && candidates < 36; i++) {
                    for (int j = -15; j <= 15 && candidates < 36; j++) {
                        ChunkPos chunk = spread.getPotentialStructureChunk(seed, i * spread.spacing(), j * spread.spacing());
                        Holder<Biome> here = resolver.getNoiseBiome(QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(gen.getSeaLevel()),
                                QuartPos.fromBlock(chunk.getMiddleBlockZ()));
                        if (!valid.test(here)) continue;
                        candidates++;
                        var ctx = new Structure.GenerationContext(access, gen, source, sampler, random, level.getServer().getStructureTemplateManager(),
                                seed, chunk, level, valid);
                        BlockPos at = HeroStructure.findSite(ctx, tree);
                        if (at == null) continue;
                        found++;
                        Holder<Biome> b = resolver.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
                        h.assertTrue(valid.test(b), tree + " site in " + b.getRegisteredName());
                        int top = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        int floor = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        h.assertTrue(HeroStructure.standing(tree, top, floor, gen.getSeaLevel()), tree + " site on ground it stands on at " + at.toShortString()
                                + " (top " + top + ", ground " + floor + ")");
                        h.assertTrue(at.getY() <= floor + 1 && at.getY() >= floor + 1 - tree.maxStep, tree + " foot at the ground: " + at.getY() + " over " + floor);
                    }
                }
                log.append(String.format(" %s: %d of %d candidates;", tree.getSerializedName(), found, candidates));
                h.assertTrue(found >= 1, tree + " finds sites in its archetypes (" + found + " of " + candidates + ")");
            }
            Awaken.LOG.info("[terrain] hero sites:{}", log);
            h.succeed();
        });
    }

    /** Measurements of a blueprint. */
    static final class Shape {
        int top, trunkTop, width12, rootReach, leafReach, leaves, persistent, gold, vines, tips, loose, cells, disconnected;
        /** The first cell not joined to the ground, and its code (for the failure message). */
        String firstDisconnected = "";

        @Override
        public String toString() {
            return "top " + top + ", trunk " + trunkTop + ", " + width12 + " across, roots " + rootReach + ", canopy " + leafReach + ", " + leaves + " leaves ("
                    + gold + " gold, " + persistent + " persistent), " + vines + " vines, " + cells + " cells" + firstDisconnected;
        }
    }

    static Shape shape(Blueprint bp) {
        Shape s = new Shape();
        int area = 0;
        for (int x = -bp.extent; x <= bp.extent; x++) {
            for (int z = -bp.extent; z <= bp.extent; z++) {
                for (int y = bp.minY; y <= bp.maxY; y++) {
                    byte c = bp.get(x, y, z);
                    if (c == Blueprint.EMPTY) continue;
                    s.cells++;
                    s.top = Math.max(s.top, y);
                    int reach = (int) Math.round(Math.sqrt(x * x + z * z));
                    if (Blueprint.wood(c)) {
                        // the trunk: wood round the axis (it sways at most two blocks)
                        if (x * x + z * z <= 6) s.trunkTop = Math.max(s.trunkTop, y);
                        if (y == 20 && x * x + z * z <= 49) area++;
                        if (y == bp.minY) s.rootReach = Math.max(s.rootReach, reach);
                    } else if (c == Blueprint.LEAF || c == Blueprint.LEAF2) s.loose++;
                    else if (Blueprint.leaf(c)) {
                        s.leaves++;
                        if (c == Blueprint.LEAF_PERSISTENT || c == Blueprint.LEAF2_PERSISTENT) s.persistent++;
                        if (c >= Blueprint.LEAF2_D1) s.gold++;
                        s.leafReach = Math.max(s.leafReach, reach);
                    } else if (c == Blueprint.VINE || c == Blueprint.VINE_TIP) {
                        s.vines++;
                        if (c == Blueprint.VINE_TIP) s.tips++;
                    }
                }
            }
        }
        s.width12 = (int) Math.round(2 * Math.sqrt(area / Math.PI)); // the trunk's mean width twenty blocks up
        // every cell joins the ground face to face through other cells (from the bottom of the trunk and roots)
        int n = bp.cells.length, sxz = bp.sx * bp.sx;
        boolean[] seen = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int i = 0; i < sxz; i++) {
            if (bp.cells[i] != Blueprint.EMPTY) {
                seen[i] = true;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int z = i % bp.sx, x = (i / bp.sx) % bp.sx, y = i / sxz;
            int[] nb = {x + 1 < bp.sx ? i + bp.sx : -1, x > 0 ? i - bp.sx : -1, z + 1 < bp.sx ? i + 1 : -1, z > 0 ? i - 1 : -1,
                    y + 1 < bp.sy ? i + sxz : -1, y > 0 ? i - sxz : -1};
            for (int j : nb) {
                if (j < 0 || seen[j] || bp.cells[j] == Blueprint.EMPTY) continue;
                seen[j] = true;
                queue[tail++] = j;
            }
        }
        for (int i = 0; i < n; i++) {
            if (bp.cells[i] == Blueprint.EMPTY || seen[i] || s.disconnected++ > 0) continue;
            int z = i % bp.sx - bp.extent, x = (i / bp.sx) % bp.sx - bp.extent, y = i / sxz + bp.minY;
            s.firstDisconnected = ", first not joined: code " + bp.cells[i] + " at " + x + "," + y + "," + z;
        }
        return s;
    }

    /** Clears the area and lays grass over dirt: flat at origin.y - 1, or rising one block every four along x. */
    static void ground(ServerLevel level, BlockPos origin, int r, boolean slope) {
        wipe(level, origin, r);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            int top = slope ? Math.floorDiv(x, 4) : 0;
            for (int z = -r; z <= r; z++) {
                for (int y = -14; y < top - 1; y++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.setWithOffset(origin, x, top - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Mirepeat over dirt, its top at origin.y - 1, under {@code depth} blocks of still water walled in at the rim. */
    static void pond(ServerLevel level, BlockPos origin, int r, int depth) {
        wipe(level, origin, r);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean rim = Math.abs(x) == r || Math.abs(z) == r;
                for (int y = -14; y < -1; y++) level.setBlock(m.setWithOffset(origin, x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.setWithOffset(origin, x, -1, z), MireBlocks.MIREPEAT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = 0; y < depth; y++) {
                    level.setBlock(m.setWithOffset(origin, x, y, z), rim ? Blocks.DIRT.defaultBlockState() : Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    static void wipe(ServerLevel level, BlockPos origin, int r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 110; y >= -14; y--) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    m.setWithOffset(origin, x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    static final class Check {
        int outside, loose, decaying, grounded, strands, floating;
        BlockPos firstOutside, firstLoose;
    }

    private static boolean strand(BlockState s) {
        return s.is(LandsBlocks.LUMEN_VINES.get()) || s.is(MireBlocks.WEEPWOOD_TENDRILS.get());
    }

    private static boolean material(BlockState s) {
        return s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || strand(s);
    }

    /** Walks the built tree face to face from the wood that rests on the ground; checks the box and the leaves. */
    static Check check(ServerLevel level, BlockPos origin, int r, BoundingBox box) {
        Check c = new Check();
        Set<BlockPos> tree = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> columns = new HashSet<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = -14; y <= 110; y++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockState s = level.getBlockState(m.setWithOffset(origin, x, y, z));
                    if (s.is(MireBlocks.MOONBLOOM.get())) c.floating++;
                    if (!material(s)) continue;
                    BlockPos p = m.immutable();
                    tree.add(p);
                    if (!box.isInside(p) && c.outside++ == 0) c.firstOutside = p.subtract(origin);
                    if (strand(s)) c.strands++;
                    if (s.is(BlockTags.LEAVES) && s.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE && !s.getValue(LeavesBlock.PERSISTENT)) c.decaying++;
                    if (s.is(BlockTags.LOGS)) {
                        BlockState below = level.getBlockState(p.below());
                        if (below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL) || below.is(Blocks.MOSS_BLOCK)
                                || below.is(MireBlocks.MIREPEAT.get())) {
                            queue.add(p);
                            columns.add(((long) p.getX() << 32) | (p.getZ() & 0xffffffffL));
                        }
                    }
                }
            }
        }
        c.grounded = columns.size();
        Set<BlockPos> seen = new HashSet<>(queue);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (tree.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        for (BlockPos p : tree) if (!seen.contains(p) && c.loose++ == 0) c.firstLoose = p.subtract(origin);
        return c;
    }
}
