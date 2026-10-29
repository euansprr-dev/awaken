package dev.awaken.caves;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.awaken.Awaken;
import dev.awaken.caves.life.UnderSites;
import dev.awaken.caves.skin.CaveSkin;
import dev.awaken.caves.skin.CaveSkinFeature;
import dev.awaken.caves.skin.SkinBlocks;
import dev.awaken.caves.skin.SkinRock;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.Tags;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The Living Deep's ground truth (SPEC §42, P0). The GameTest world has no Lithostitched or Tectonic, so the caves pack's
 * injectors and wrapper are read as data and applied to the vanilla generator the way Lithostitched applies them: the
 * injectors are held to {@link CaveRegion} point by point and their shares measured on the five studio seeds; the
 * shape wrapper is spliced into vanilla's {@code minecraft:overworld/caves/entrances} (its registry value replaced, as
 * Lithostitched's {@code wrap_density_function} does), and the land on top must not move by a block, nor chunk filling
 * cost more than {@link #COST_MAX} times as much. Structures stay whole by construction: the router's wrapped caves sit
 * under the beardifier, which still carves and fills around every structure piece.
 */
final class CavesTests {
    private CavesTests() {}

    /** The terrain studio's five seeds (tools/autopilot/terrain-survey.txt, as PlacementTests). */
    static final long[] STUDIO_SEEDS = {777L, 20261024L, 4242L, 1337L, 90210L};
    /** The gate (SPEC §42.6): filling a chunk with the shapes costs at most this much more. */
    static final double COST_MAX = 1.20;
    private static final ResourceKey<DensityFunction> ENTRANCES =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/caves/entrances"));

    static void register() {
        AwakenTests.register("caves_region_shares", 2400, CavesTests::shares);
        AwakenTests.register("caves_surface_untouched", 2400, CavesTests::surface);
        AwakenTests.register("caves_gen_cost", 2400, CavesTests::cost);
        AwakenTests.register("caves_skin_census", 2400, CavesTests::skin);
        AwakenTests.register("caves_underlife_sites", 200, CavesTests::underlife);
        AwakenTests.register("caves_region_rock", 2400, CavesTests::regionRock);
        AwakenTests.register("caves_region_pieces", 2400, CavesTests::regionPieces);
    }

    // ---------------------------------------------------------------- the regions

    /** One replace_partially injector: where its target stands and every parameter is in range, the replacement goes. */
    record Rule(Identifier id, int priority, ResourceKey<Biome> replacement, Map<String, float[]> ranges) {
        boolean holds(Map<String, Float> at) {
            for (var e : ranges.entrySet()) {
                float v = at.get(e.getKey());
                if (v < e.getValue()[0] || v > e.getValue()[1]) return false;
            }
            return true;
        }
    }

    private static final List<String> CLIMATE = List.of("depth", "humidity", "temperature", "continentalness", "erosion");

    private static void shares(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var access = h.getLevel().registryAccess();
        h.assertTrue(server.getPackRepository().getSelectedIds().contains(CavesModule.PACK_ID), "a new world loads the caves pack");
        var biomes = access.lookupOrThrow(Registries.BIOME);
        var functions = access.lookupOrThrow(Registries.DENSITY_FUNCTION);

        // the region biomes: in the overworld (lodes, Deepglass, lamps and the Hollows reach them), not underground-tagged
        // (that tag turns the Hollows' band spawns off), each with an archetype, and good for vanilla's underground structures
        TagKey<Biome> hosts = TagKey.create(Registries.BIOME, Awaken.id("caves/hosts"));
        for (CaveRegion r : CaveRegion.ORDER) {
            var b = biomes.get(r.biome());
            h.assertTrue(b.isPresent(), r.id + " is a biome");
            h.assertTrue(b.get().is(BiomeTags.IS_OVERWORLD) && !b.get().is(Tags.Biomes.IS_UNDERGROUND) && !b.get().is(hosts), r.id + " is overworld, not underground-tagged, not a host");
            h.assertTrue(BiomeTable.archetype(b.get()) != null, r.id + " has an archetype");
            h.assertTrue(b.get().is(BiomeTags.HAS_MINESHAFT), r.id + " can hold a mineshaft");
        }
        // the hosts are the land's own biomes: none of vanilla's cave biomes (the deep dark keeps its cities)
        var hostSet = biomes.getOrThrow(hosts);
        h.assertTrue(hostSet.size() >= 40 && hostSet.stream().noneMatch(b -> b.is(Tags.Biomes.IS_UNDERGROUND) || b.is(Biomes.DEEP_DARK)
                || b.is(Biomes.DRIPSTONE_CAVES) || b.is(Biomes.LUSH_CAVES)), "the hosts are the vanilla land biomes: " + hostSet.size());

        // the injectors say what CaveRegion says
        List<Rule> rules = new ArrayList<>();
        for (var e : files(server, "lithostitched/biome_injector").entrySet()) {
            JsonObject o = json(h, e.getValue());
            h.assertTrue("lithostitched:replace_partially".equals(str(o, "type")) && "minecraft:overworld".equals(str(o, "dimension")),
                    e.getKey() + " is an overworld replace_partially injector");
            h.assertTrue("#awaken:caves/hosts".equals(str(o, "targets")), e.getKey() + " targets the hosts");
            Map<String, float[]> ranges = new TreeMap<>();
            for (var p : o.getAsJsonObject("parameters").entrySet()) {
                JsonObject range = p.getValue().getAsJsonObject();
                ranges.put(p.getKey(), new float[]{range.has("min_inclusive") ? range.get("min_inclusive").getAsFloat() : -Float.MAX_VALUE,
                        range.has("max_inclusive") ? range.get("max_inclusive").getAsFloat() : Float.MAX_VALUE});
                h.assertTrue(CLIMATE.contains(p.getKey()) || p.getKey().startsWith("awaken:caves/"), e.getKey() + " reads a known parameter: " + p.getKey());
            }
            rules.add(new Rule(e.getKey(), o.get("priority").getAsInt(), ResourceKey.create(Registries.BIOME, Identifier.parse(str(o, "replacement"))), ranges));
        }
        rules.sort(Comparator.comparingInt(Rule::priority).thenComparing(r -> r.id().toString()));
        for (CaveRegion r : CaveRegion.ORDER) {
            long n = rules.stream().filter(x -> x.replacement().equals(r.biome())).count();
            h.assertTrue(n == r.boxes.size(), r.id + " has an injector per box of land: " + n + " for " + r.boxes.size());
        }
        h.assertTrue(rules.stream().allMatch(x -> CaveRegion.ORDER.stream().anyMatch(r -> r.biome().equals(x.replacement()))), "every injector places a region");

        // measured on the studio seeds: the injectors point by point against CaveRegion, the fields' deciles, the shares
        var settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        Map<String, DensityFunction> fields = new HashMap<>();
        for (var band : CaveRegion.Band.values()) fields.put(band.key(), fn(functions, "caves/" + band.key()));
        DensityFunction yFn = fn(functions, "caves/y");
        final int radius = 3072, step = 128;
        int side = radius * 2 / step + 1;
        float[][] decile = new float[3][side * side * STUDIO_SEEDS.length];
        int columns = 0, points = 0, disagree = 0, ambiguous = 0;
        Map<CaveRegion.Band, int[]> inBand = new EnumMap<>(CaveRegion.Band.class);
        Map<CaveRegion, int[]> placed = new EnumMap<>(CaveRegion.class), onLand = new EnumMap<>(CaveRegion.class);
        StringBuilder bySeed = new StringBuilder();
        long t0 = System.currentTimeMillis();
        for (long seed : STUDIO_SEEDS) {
            RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings);
            NoiseRouter router = settings.noiseRouter();
            int seedPoints = 0, seedPlaced = 0;
            for (int i = 0; i < side; i++) {
                for (int j = 0; j < side; j++) {
                    int x = -radius + i * step, z = -radius + j * step;
                    float[] field = new float[3];
                    for (var band : CaveRegion.Band.values()) field[band.ordinal()] = rs.sampleBlockValueUncached(fields.get(band.key()), x, 0, z);
                    for (int k = 0; k < 3; k++) decile[k][columns] = field[k];
                    columns++;
                    float hum = rs.sampleBlockValueUncached(router.vegetation(), x, 0, z), temp = rs.sampleBlockValueUncached(router.temperature(), x, 0, z);
                    float cont = rs.sampleBlockValueUncached(router.continents(), x, 0, z), ero = rs.sampleBlockValueUncached(router.erosion(), x, 0, z);
                    for (int y = -56; y <= 120; y += 8) {
                        float d = rs.sampleBlockValueUncached(router.depth(), x, y, z);
                        if (d < CaveRegion.MIN_DEPTH) continue;
                        CaveRegion.Band band = CaveRegion.Band.at(y, d);
                        if (y <= CaveRegion.MAX_Y || band == CaveRegion.Band.DEEP) {
                            inBand.computeIfAbsent(band, b -> new int[1])[0]++;
                            seedPoints++;
                        }
                        CaveRegion want = CaveRegion.at(y, d, field[band.ordinal()], hum, temp, cont, ero);
                        if (want != null) {
                            placed.computeIfAbsent(want, r -> new int[1])[0]++;
                            seedPlaced++;
                        }
                        // its land: where its land holds and no region tried before it in the band, over the same slot, holds
                        for (int ri = 0; ri < CaveRegion.ORDER.size(); ri++) {
                            CaveRegion r = CaveRegion.ORDER.get(ri);
                            if (r.band != band || y > CaveRegion.MAX_Y || !r.holds(hum, temp, cont, ero)) continue;
                            boolean taken = false;
                            for (int ei = 0; ei < ri && !taken; ei++) {
                                CaveRegion e = CaveRegion.ORDER.get(ei);
                                taken = e.band == band && e.fromQuantile < r.toQuantile && r.fromQuantile < e.toQuantile && e.holds(hum, temp, cont, ero);
                            }
                            if (!taken) onLand.computeIfAbsent(r, q -> new int[1])[0]++;
                        }
                        // as Lithostitched sees it: the climate quantised in the biome's target point, the fields as they are
                        Map<String, Float> at = new HashMap<>();
                        at.put("depth", q(d));
                        at.put("humidity", q(hum));
                        at.put("temperature", q(temp));
                        at.put("continentalness", q(cont));
                        at.put("erosion", q(ero));
                        at.put("awaken:caves/y", rs.sampleBlockValueUncached(yFn, x, y, z));
                        for (var b : CaveRegion.Band.values()) at.put("awaken:caves/" + b.key(), field[b.ordinal()]);
                        Rule first = null;
                        for (Rule rule : rules) {
                            if (!rule.holds(at)) continue;
                            if (first == null) first = rule;
                            else if (rule.priority() == first.priority() && !rule.replacement().equals(first.replacement())) ambiguous++;
                        }
                        ResourceKey<Biome> got = first == null ? null : first.replacement();
                        if (got == null ? want != null : want == null || !got.equals(want.biome())) disagree++;
                        points++;
                    }
                }
            }
            bySeed.append(String.format(Locale.ROOT, " %d: %.0f%%", seed, seedPlaced * 100.0 / Math.max(1, seedPoints)));
        }
        // the fields' deciles, against CaveRegion.Fields
        StringBuilder deciles = new StringBuilder();
        float worst = 0;
        for (var band : CaveRegion.Band.values()) {
            float[] v = Arrays.copyOf(decile[band.ordinal()], columns);
            Arrays.sort(v);
            deciles.append(' ').append(band.key()).append(" {-9f");
            for (int d = 1; d <= 9; d++) {
                float m = v[columns * d / 10];
                worst = Math.max(worst, Math.abs(m - CaveRegion.Fields.QUANTILES[band.ordinal()][d]));
                deciles.append(String.format(Locale.ROOT, ", %.3ff", m));
            }
            deciles.append(", 9f}");
        }
        StringBuilder split = new StringBuilder();
        for (CaveRegion r : CaveRegion.ORDER) {
            int n = placed.getOrDefault(r, new int[1])[0], band = inBand.getOrDefault(r.band, new int[1])[0], land = onLand.getOrDefault(r, new int[1])[0];
            split.append(String.format(Locale.ROOT, " %s %.0f%% of the %s (%.0f%% of its land there, want %.0f%%);", r.id, n * 100.0 / Math.max(1, band),
                    r.band.key(), n * 100.0 / Math.max(1, land), slot(r) * 100));
        }
        Awaken.LOG.info("[caves] regions on the studio seeds ({} columns, {} points under the ground): placed by seed{}; {} injectors, "
                        + "against CaveRegion {} points differ, {} ambiguous; field deciles (worst off the table by {}):{};{} {} ms", columns, points, bySeed, rules.size(),
                disagree, ambiguous, String.format(Locale.ROOT, "%.3f", worst), deciles, split, System.currentTimeMillis() - t0);

        h.assertTrue(disagree <= points / 1000, "the injectors and CaveRegion agree (" + disagree + " of " + points + " points differ)");
        h.assertTrue(ambiguous == 0, "no point meets two regions' injectors at one priority (" + ambiguous + ")");
        h.assertTrue(worst <= 0.03f, "CaveRegion.Fields matches the fields' measured deciles (worst by " + worst + "; the log has the table)");
        for (CaveRegion r : CaveRegion.ORDER) {
            int n = placed.getOrDefault(r, new int[1])[0], land = onLand.getOrDefault(r, new int[1])[0];
            double share = n / (double) Math.max(1, land);
            h.assertTrue(n > 0, r.id + " is placed on the studio seeds");
            h.assertTrue(Math.abs(share - slot(r)) <= 0.12, r.id + " takes " + Math.round(share * 100) + "% of its land in its band, want about "
                    + Math.round(slot(r) * 100) + "%");
        }
        h.succeed();
    }

    /** A region's share of its band, where its land is: its slot's width of the band's cover. */
    private static double slot(CaveRegion r) {
        return (r.toQuantile - r.fromQuantile) * CaveRegion.Fields.COVER[r.band.ordinal()];
    }

    /** A climate value as a biome's target point holds it (Lithostitched reads it back from there). */
    private static float q(float v) {
        return Climate.quantizeCoord(v) / 10000f;
    }

    // ---------------------------------------------------------------- the land on top, and the cost

    /** Vanilla's overworld generator, and the same with the caves pack's wrapper spliced in as Lithostitched would. */
    record Pair(NoiseBasedChunkGenerator plain, NoiseBasedChunkGenerator shaped, NoiseGeneratorSettings plainSettings, NoiseGeneratorSettings shapedSettings) {
        RandomState random(GameTestHelper h, long seed, boolean shapedOne) {
            return RandomState.create(h.getLevel().registryAccess().lookupOrThrow(Registries.NOISE), seed, shapedOne ? shapedSettings : plainSettings);
        }
    }

    private static Pair pair(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var access = h.getLevel().registryAccess();
        JsonObject modifier = json(h, files(server, "lithostitched/worldgen_modifier").get(Awaken.id("caves/shape_vanilla")));
        h.assertTrue(modifier != null && "minecraft:overworld/caves/entrances".equals(str(modifier, "target_function")),
                "the vanilla wrapper targets vanilla's cave entrances");
        // the marker is the function it wraps
        JsonObject wrapper = modifier.getAsJsonObject("wrapper_function").deepCopy();
        h.assertTrue("lithostitched:wrapped_marker".equals(str(wrapper.getAsJsonObject("input"), "type")), "the wrapper wraps its marker");
        wrapper.add("input", new JsonPrimitive(ENTRANCES.identifier().toString()));
        var parsed = DensityFunction.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, access), wrapper);
        h.assertTrue(parsed.isSuccess(), "the wrapper decodes: " + parsed.error().map(e -> e.message()).orElse(""));
        DensityFunction shape = parsed.getOrThrow();
        h.assertTrue(shape instanceof CaveShapeFunction, "the wrapper is awaken:cave_shape");
        DfRewriteRule splice = new DfRewriteRule() {
            @Override
            public DensityFunction rewrite(DensityFunction f) {
                return f instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> held) && held.is(ENTRANCES) ? shape : f.rewriteChildren(this);
            }
        };
        var holder = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings s = holder.value();
        NoiseRouter r = s.noiseRouter();
        NoiseRouter shaped = new NoiseRouter(r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                r.chunkSurfaceLevel(), splice.rewrite(r.finalDensity()));
        h.assertTrue(!shaped.finalDensity().equals(r.finalDensity()), "the wrapper is spliced into the overworld's final density");
        NoiseGeneratorSettings ss = new NoiseGeneratorSettings(s.noiseSettings(), s.defaultBlock(), s.defaultFluid(), shaped, s.materialRule(),
                s.spawnTarget(), s.seaLevel(), s.disableMobGeneration(), s.aquifers(), s.useLegacyRandomSource(), s.debugFunctions());
        var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var source = MultiNoiseBiomeSource.createFromPreset(preset);
        return new Pair(new NoiseBasedChunkGenerator(source, holder), new NoiseBasedChunkGenerator(source, Holder.direct(ss)), s, ss);
    }

    /**
     * The land on top does not move: on the studio seeds the surface and the sea floor are the same in every column of a
     * grid, and in whole chunks over each region every block above y {@code MAX_Y + 5} and the top 12 blocks under the
     * ground are the same; under it, the regions' shapes open new cave.
     */
    private static void surface(GameTestHelper h) {
        Pair p = pair(h);
        var level = h.getLevel();
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        int columns = 0, moved = 0;
        StringBuilder where = new StringBuilder();
        long t0 = System.currentTimeMillis();
        for (long seed : STUDIO_SEEDS) {
            RandomState plain = p.random(h, seed, false), shaped = p.random(h, seed, true);
            for (int x = -1536; x <= 1536; x += 128) {
                for (int z = -1536; z <= 1536; z += 128) {
                    columns++;
                    for (var type : new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG}) {
                        int a = p.plain().getBaseHeight(x, z, type, heights, plain), b = p.shaped().getBaseHeight(x, z, type, heights, shaped);
                        if (a == b) continue;
                        moved++;
                        if (where.length() < 400) where.append(String.format(Locale.ROOT, " %d@%d,%d %s %d->%d", seed, x, z, type, a, b));
                    }
                }
            }
        }
        // whole chunks over each region (the first few found near the origin, on the first studio seed that has it: the
        // Windcleft needs mountains), plain against shaped
        Map<CaveRegion, Long> found = new EnumMap<>(CaveRegion.class);
        int changedHigh = 0, changedTop = 0;
        StringBuilder opened = new StringBuilder();
        for (long seed : STUDIO_SEEDS) {
            if (found.size() == CaveRegion.ORDER.size()) break;
            RandomState plain = p.random(h, seed, false), shaped = p.random(h, seed, true);
            for (var e : regionChunks(h, plain, p.plainSettings().noiseRouter(), 8).entrySet()) {
                // a narrow shape (the Windcleft's cracks) may miss one or two chunks: take a seed with four or more
                if (found.containsKey(e.getKey()) || e.getValue().size() < 4 && seed != STUDIO_SEEDS[STUDIO_SEEDS.length - 1]) continue;
                found.put(e.getKey(), seed);
                long open0 = 0, open1 = 0, below = 0;
                for (ChunkPos pos : e.getValue()) {
                    ProtoChunk a = build(level, p.plain(), plain, pos, heights), b = build(level, p.shaped(), shaped, pos, heights);
                    BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            int top = a.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
                            for (int y = -60; y <= top; y++) {
                                at.set(pos.getMinBlockX() + dx, y, pos.getMinBlockZ() + dz);
                                BlockState sa = a.getBlockState(at), sb = b.getBlockState(at);
                                boolean same = sa == sb;
                                if (y > CaveRegion.MAX_Y + 5 && !same) changedHigh++;
                                else if (y > top - 12 && !same) changedTop++;
                                if (y <= CaveRegion.MAX_Y) {
                                    below++;
                                    if (open(sa)) open0++;
                                    if (open(sb)) open1++;
                                }
                            }
                        }
                    }
                }
                opened.append(String.format(Locale.ROOT, " %s (%d chunks, seed %d): open %.1f%% -> %.1f%%;", e.getKey().id, e.getValue().size(), seed,
                        open0 * 100.0 / Math.max(1, below), open1 * 100.0 / Math.max(1, below)));
                // over all its chunks: a shape opens in patches (a terrace layer on about 30% of the land), so one chunk may miss
                h.assertTrue(open1 > open0 + below / 200, e.getKey().id + "'s shapes open new cave under its land: " + open0 + " -> " + open1 + " of " + below);
            }
        }
        Awaken.LOG.info("[caves] the land on top: {} of {} grid columns moved{}; in region chunks {} blocks changed above y {} and {} in the top 12 "
                + "under the ground; cave under y {}:{} {} ms", moved, columns * 2, where, changedHigh, CaveRegion.MAX_Y + 5, changedTop, CaveRegion.MAX_Y,
                opened, System.currentTimeMillis() - t0);
        for (CaveRegion r : CaveRegion.ORDER) h.assertTrue(found.containsKey(r), r.id + " is found near the origin of a studio seed");
        h.assertTrue(moved == 0, "the surface and sea floor never move (" + moved + " columns did:" + where + ")");
        h.assertTrue(changedHigh == 0 && changedTop == 0, "nothing changes above the cap or in the top 12 blocks (" + changedHigh + ", " + changedTop + ")");
        h.succeed();
    }

    /**
     * The gate (SPEC §42.6): filling chunks with the regions' shapes costs at most {@link #COST_MAX} times the plain
     * fill, on chunks spread round the origin of seed 777, best of three rounds each, warmed first. The fill is the
     * noise pass only; chunk generation as a whole (surface, carvers, features, light) costs more, so its share is less.
     */
    private static void cost(GameTestHelper h) {
        Pair p = pair(h);
        var level = h.getLevel();
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        long seed = STUDIO_SEEDS[0];
        RandomState plain = p.random(h, seed, false), shaped = p.random(h, seed, true);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < 6; i++) for (int j = 0; j < 4; j++) chunks.add(new ChunkPos(-60 + i * 24, -36 + j * 24));
        for (ChunkPos pos : chunks) {
            build(level, p.plain(), plain, pos, heights);
            build(level, p.shaped(), shaped, pos, heights);
        }
        long best0 = Long.MAX_VALUE, best1 = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            for (int k = 0; k < 2; k++) {
                boolean shapedOne = (round + k) % 2 == 1;
                long t = System.nanoTime();
                for (ChunkPos pos : chunks) build(level, shapedOne ? p.shaped() : p.plain(), shapedOne ? shaped : plain, pos, heights);
                t = System.nanoTime() - t;
                if (shapedOne) best1 = Math.min(best1, t);
                else best0 = Math.min(best0, t);
            }
        }
        double ratio = best1 / (double) best0;
        Awaken.LOG.info("[caves] chunk fill on {} chunks: plain {} ms a chunk, with the shapes {} ms ({}x, gate {}x)", chunks.size(),
                String.format(Locale.ROOT, "%.2f", best0 / 1e6 / chunks.size()), String.format(Locale.ROOT, "%.2f", best1 / 1e6 / chunks.size()),
                String.format(Locale.ROOT, "%.3f", ratio), COST_MAX);
        h.assertTrue(ratio <= COST_MAX, "the shapes cost " + String.format(Locale.ROOT, "%.0f", (ratio - 1) * 100) + "% more per chunk fill (gate "
                + Math.round((COST_MAX - 1) * 100) + "%)");
        h.succeed();
    }

    /** The skin's gate (SPEC §42.4): at least this share of cave floor is dressed. */
    static final double SKIN_DRESSED = 0.30;
    /**
     * The skin's budget, as a share of filling the same chunk (noise, surface, carvers), so it holds on any machine: on the
     * Mac 2026-10-29 the fill was about 13 ms a chunk and the skin 1.2 ms on a freshly filled chunk (0.5 ms once it's in
     * the cache: most of the cost is the first read of the chunk's sections).
     */
    static final double SKIN_SHARE = 0.15;

    /**
     * The skin on real caves (SPEC §42.4): chunks of seed 777 filled with the caves pack's shapes are dressed as the
     * feature dresses them; at least {@link #SKIN_DRESSED} of the cave floor cells have a piece on them or beside them
     * (a floor reads by its pieces, not every cell), every piece rests on or hangs from natural rock, a chunk comes out
     * the same dressed twice, and dressing costs at most {@link #SKIN_SHARE} of filling the chunk.
     */
    private static void skin(GameTestHelper h) {
        Pair p = pair(h);
        var level = h.getLevel();
        h.assertTrue(level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .get(ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id("caves/skin"))).isPresent(), "the skin is a loaded placed feature");
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        long seed = STUDIO_SEEDS[0];
        RandomState rs = p.random(h, seed, true);
        int floors = 0, dressed = 0, pieces = 0, rubble = 0, formations = 0, puddles = 0, overlays = 0;
        long nanos = 0, fillNanos = 0;
        int n = 0;
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                ChunkPos pos = new ChunkPos(-40 + i * 21, -30 + j * 19);
                long f0 = System.nanoTime();
                ProtoChunk chunk = build(level, p.shaped(), rs, pos, heights);
                if (i > 0) fillNanos += System.nanoTime() - f0;
                int cx = pos.getMinBlockX(), cz = pos.getMinBlockZ();
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                CaveSkinFeature.Cells cells = new CaveSkinFeature.Cells() {
                    @Override
                    public BlockState get(int x, int y, int z) {
                        if ((x & ~15) != cx || (z & ~15) != cz || y < -64 || y >= 320) return stone;
                        return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x & 15, y & 15, z & 15);   // as the feature reads
                    }

                    @Override
                    public void set(int x, int y, int z, BlockState state) {
                        chunk.setBlockState(m.set(x, y, z), state, 0);
                    }

                    @Override
                    public int top(int x, int z) {
                        return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
                    }

                    @Override
                    public int minY() {
                        return -64;
                    }
                };
                // the floor cells before dressing
                List<BlockPos> floor = new ArrayList<>();
                for (int dx = 1; dx < 15; dx++) for (int dz = 1; dz < 15; dz++) {
                    int x = cx + dx, z = cz + dz, top = cells.top(x, z) - CaveSkinFeature.UNDER_GROUND;
                    for (int y = -60; y < top; y++) {
                        if (cells.get(x, y, z).isAir() && SkinRock.of(cells.get(x, y - 1, z).getBlock()) != null) floor.add(new BlockPos(x, y, z));
                    }
                }
                long t = System.nanoTime();
                int placed = CaveSkinFeature.dress(cells, cx, cz, seed, chunk);
                if (i > 0) {                // the first row warms the code
                    nanos += System.nanoTime() - t;
                    n++;
                }
                pieces += placed;
                for (BlockPos f : floor) {
                    boolean near = false;
                    for (int dx = -1; dx <= 1 && !near; dx++) for (int dz = -1; dz <= 1 && !near; dz++) for (int dy = 0; dy <= 2 && !near; dy++) {
                        near = CaveSkinFeature.skin(cells.get(f.getX() + dx, f.getY() + dy, f.getZ() + dz).getBlock());
                    }
                    if (near) dressed++;
                }
                floors += floor.size();
                // every piece rests on or hangs from natural rock
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = -62; y < 300; y++) {
                    BlockState s = cells.get(cx + dx, y, cz + dz);
                    if (!CaveSkinFeature.skin(s.getBlock())) continue;
                    BlockState under = cells.get(cx + dx, y - 1, cz + dz);
                    if (s.getBlock() instanceof SkinBlocks.Formation) {
                        formations++;
                        Direction from = s.getValue(SkinBlocks.Formation.DIRECTION).getOpposite();
                        BlockState behind = cells.get(cx + dx, y + from.getStepY(), cz + dz);
                        h.assertTrue(behind.getBlock() instanceof SkinBlocks.Formation || SkinRock.of(behind.getBlock()) != null,
                                "a formation grows from rock at " + (cx + dx) + " " + y + " " + (cz + dz) + ": " + behind);
                    } else if (s.getBlock() instanceof MultifaceBlock) {
                        overlays++;
                    } else if (s.getBlock() != CaveSkin.DUSTY_WEB.get()) {
                        if (s.getBlock() instanceof SkinBlocks.Rubble) rubble++;
                        if (s.getBlock() instanceof SkinBlocks.Puddle) puddles++;
                        h.assertTrue(SkinRock.of(under.getBlock()) != null, "a floor piece lies on natural rock at " + (cx + dx) + " " + y + " "
                                + (cz + dz) + ": " + s + " on " + under);
                    }
                }
                if (i == 0 && j == 0) {
                    // the same chunk again comes out the same
                    ProtoChunk again = build(level, p.shaped(), rs, pos, heights);
                    CaveSkinFeature.Cells twice = new CaveSkinFeature.Cells() {
                        @Override
                        public BlockState get(int x, int y, int z) {
                            if ((x & ~15) != cx || (z & ~15) != cz || y < -64 || y >= 320) return stone;
                            return again.getBlockState(m.set(x, y, z));
                        }

                        @Override
                        public void set(int x, int y, int z, BlockState state) {
                            again.setBlockState(m.set(x, y, z), state, 0);
                        }

                        @Override
                        public int top(int x, int z) {
                            return again.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
                        }

                        @Override
                        public int minY() {
                            return -64;
                        }
                    };
                    h.assertTrue(CaveSkinFeature.dress(twice, cx, cz, seed, again) == placed, "a chunk dresses the same twice");
                }
            }
        }
        double share = dressed / (double) Math.max(1, floors), ms = nanos / 1e6 / n, fill = fillNanos / 1e6 / n;
        Awaken.LOG.info("[caves] skin on {} timed chunks of 16: {} pieces ({} rubble, {} formation pieces, {} puddles, {} overlays), {} of {} floor cells dressed ({}), {} ms a chunk against {} ms to fill it",
                n, pieces, rubble, formations, puddles, overlays, dressed, floors, String.format(Locale.ROOT, "%.2f", share), String.format(Locale.ROOT, "%.3f", ms),
                String.format(Locale.ROOT, "%.2f", fill));
        h.assertTrue(floors > 500, "the chunks have cave floors: " + floors);
        h.assertTrue(share >= SKIN_DRESSED, "the skin dresses " + Math.round(share * 100) + "% of cave floors (gate " + Math.round(SKIN_DRESSED * 100) + "%)");
        h.assertTrue(formations > 0 && puddles > 0 && rubble > 0 && overlays > 0, "every kind of piece is laid");
        h.assertTrue(ms <= fill * SKIN_SHARE, "the skin costs " + String.format(Locale.ROOT, "%.2f", ms) + " ms a chunk, "
                + Math.round(ms / fill * 100) + "% of the fill (budget " + Math.round(SKIN_SHARE * 100) + "%)");
        h.succeed();
    }

    /** The host rock a region turns to its own (as {@code CaveSkinFeature.host}). */
    private static final java.util.Set<Block> HOSTS = java.util.Set.of(Blocks.STONE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF);
    /** The regions' gates (SPEC §42.7): glowcaps on at most this share of open floor cells, and the regions' cost per chunk. */
    static final double GLOW_MAX = 0.06, REGION_SHARE = 0.25;
    /** Chunks filled for each region, and how many of them first only warm the code (the timing is the rest's). */
    static final int REGION_CHUNKS = 5, REGION_WARM = 2;
    /**
     * Each warming chunk is dressed this many times, and each timed one filled and dressed this many times (fresh copies):
     * the fastest of each is the work's cost. On the CI runner the region rule ran 3 to 5 times its Mac cost after two warm
     * dressings (the skin, warm from its own test, didn't): the compiler hadn't caught up with code only this test runs.
     */
    static final int REGION_WARM_RUNS = 6, REGION_REPEATS = 5;

    /**
     * The regions' own (SPEC §42.7), on real filled chunks (the census's) told they are all one region: four Rootways, four
     * Ochre Deep. Ores are planted first (the fill lays none): coal, copper, iron and gold, each in solid stone. Under the
     * land's last {@link CaveSkinFeature#REGION_UNDER} blocks the host rock becomes the region's, an ore with a twin there
     * becomes the twin, an ore without one keeps its stone shell; above, nothing changes. The growth is laid (glowcaps,
     * root curtains; rust, flowstone, ember lichen), each piece where it may stand, the glow sparse, the cost in budget.
     */
    private static void regionRock(GameTestHelper h) {
        Pair p = pair(h);
        var level = h.getLevel();
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        long seed = STUDIO_SEEDS[0];
        RandomState rs = p.random(h, seed, true);
        BlockState stone = Blocks.STONE.defaultBlockState();
        Block[] planted = {Blocks.COAL_ORE, Blocks.COPPER_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE};
        long nanos = 0, fillNanos = 0;
        // each chunk's dressing against its own fill; the gate takes the middle one, so one slow fill or pause can't swing it
        List<Double> ratios = new ArrayList<>();
        int n = 0, floors = 0, glowcaps = 0, roots = 0, amber = 0, rust = 0, flow = 0, ember = 0, swapped = 0;
        for (CaveRegion region : new CaveRegion[] {CaveRegion.ROOTWAYS, CaveRegion.OCHRE_DEEP}) {
            CaveSkin.RegionKit kit = CaveSkin.kit(region);
            Block rock = kit.rock().get();
            for (int i = 0; i < REGION_CHUNKS; i++) {
                ChunkPos pos = new ChunkPos(-40 + i * 21, region == CaveRegion.ROOTWAYS ? -30 : -11);
                long f0 = System.nanoTime();
                ProtoChunk chunk = build(level, p.shaped(), rs, pos, heights);
                long fillOne = System.nanoTime() - f0;
                int cx = pos.getMinBlockX(), cz = pos.getMinBlockZ();
                CaveSkinFeature.Cells cells = benchCells(chunk, cx, cz);
                int under = CaveSkinFeature.UNDER_GROUND, limit = CaveSkinFeature.REGION_UNDER;
                // plant the ores in solid host rock (all 26 round it host), under the region's limit, three apart in y
                List<BlockPos> ores = new ArrayList<>();
                List<Block[]> shells = new ArrayList<>();
                for (int y = 1; y < 100 && ores.size() < planted.length; y += 3) {
                    search:
                    for (int dx = 2; dx < 14; dx += 2) for (int dz = 2; dz < 14; dz += 2) {
                        int x = cx + dx, z = cz + dz;
                        if (y >= cells.top(x, z) - limit - 2) continue;
                        Block[] shell = new Block[27];
                        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
                            Block was = cells.get(x + a, y + b, z + c).getBlock();
                            if (!HOSTS.contains(was)) continue search;
                            shell[(b + 1) * 9 + (c + 1) * 3 + a + 1] = was;
                        }
                        cells.set(x, y, z, planted[ores.size()].defaultBlockState());
                        ores.add(new BlockPos(x, y, z));
                        shells.add(shell);
                        break search;
                    }
                }
                h.assertTrue(ores.size() == planted.length, "found host rock for the ores in " + pos + ": " + ores.size());
                // the stone above the limit, counted before
                int stoneAbove = 0, openFloor = 0;
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                    int top = cells.top(cx + dx, cz + dz);
                    for (int y = top - limit; y < top; y++) if (cells.get(cx + dx, y, cz + dz).getBlock() == Blocks.STONE) stoneAbove++;
                    for (int y = -60; y < top - limit; y++) {
                        if (cells.get(cx + dx, y, cz + dz).isAir() && cells.get(cx + dx, y - 1, cz + dz).isSolidRender()) openFloor++;
                    }
                }
                CaveSkinFeature.Regions regions = (x, y, z) -> region;
                if (i < REGION_WARM) {
                    for (int run = 1; run < REGION_WARM_RUNS; run++) {
                        ProtoChunk copy = build(level, p.shaped(), rs, pos, heights);
                        CaveSkinFeature.dress(benchCells(copy, cx, cz), cx, cz, seed, copy, regions);
                    }
                }
                long t = System.nanoTime();
                CaveSkinFeature.dress(cells, cx, cz, seed, chunk, regions);
                if (i >= REGION_WARM) {
                    long one = System.nanoTime() - t;
                    // again on fresh copies: the fastest fill and dressing measure the work (a pause or a busy machine only adds)
                    for (int rep = 1; rep < REGION_REPEATS; rep++) {
                        long f1 = System.nanoTime();
                        ProtoChunk copy = build(level, p.shaped(), rs, pos, heights);
                        fillOne = Math.min(fillOne, System.nanoTime() - f1);
                        CaveSkinFeature.Cells again = benchCells(copy, cx, cz);
                        long t1 = System.nanoTime();
                        CaveSkinFeature.dress(again, cx, cz, seed, copy, regions);
                        one = Math.min(one, System.nanoTime() - t1);
                    }
                    fillNanos += fillOne;
                    nanos += one;
                    ratios.add(one / (double) fillOne);
                    n++;
                }
                floors += openFloor;
                // the ores: a twin where the region has one, else the ore kept in a shell of the stone it grew in
                for (int k = 0; k < planted.length; k++) {
                    BlockPos o = ores.get(k);
                    Block now = cells.get(o.getX(), o.getY(), o.getZ()).getBlock();
                    var twin = kit.twins().get(planted[k]);
                    if (twin != null) {
                        h.assertTrue(now == twin.get(), planted[k] + " becomes its " + region + " twin, not " + now);
                    } else {
                        h.assertTrue(now == planted[k], planted[k] + " stays itself in the " + region + ", not " + now);
                        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++) {
                            if ((a | b | c) == 0) continue;
                            Block shell = cells.get(o.getX() + a, o.getY() + b, o.getZ() + c).getBlock();
                            Block was = shells.get(k)[(b + 1) * 9 + (c + 1) * 3 + a + 1];
                            // the shell stays within the ore's section; past it the region's rock may come
                            h.assertTrue(shell == was || (o.getY() + b) >> 4 != o.getY() >> 4 && shell == rock,
                                    "the " + planted[k] + " keeps its shell of " + was + " in the " + region + ": " + shell);
                        }
                    }
                }
                // the rock: no host stone left under the limit but shells; untouched above it
                int left = 0, mine = 0, above = 0;
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                    int top = cells.top(cx + dx, cz + dz);
                    for (int y = -60; y < top; y++) {
                        Block b = cells.get(cx + dx, y, cz + dz).getBlock();
                        if (y >= top - limit) {
                            if (b == Blocks.STONE) above++;
                            continue;
                        }
                        if (b == rock) mine++;
                        else if (HOSTS.contains(b)) left++;
                    }
                }
                swapped += mine;
                h.assertTrue(mine > 2000, "the " + region + " has its own rock in " + pos + ": " + mine);
                h.assertTrue(left <= 26 * planted.length, "only the shells keep host rock under the limit in " + pos + ": " + left);
                h.assertTrue(above == stoneAbove, "the rock near the land is the land's in " + pos + ": " + above + " of " + stoneAbove);
                // the growth, each where it may stand
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = -62; y < 300; y++) {
                    int x = cx + dx, z = cz + dz;
                    BlockState s = cells.get(x, y, z);
                    Block b = s.getBlock();
                    if (b instanceof SkinBlocks.Glowcap) {
                        glowcaps++;
                        h.assertTrue(region == CaveRegion.ROOTWAYS && cells.get(x, y - 1, z).isSolidRender(), "a glowcap stands on rock in the Rootways");
                    } else if (b instanceof SkinBlocks.RootCurtain) {
                        roots++;
                        BlockState over = cells.get(x, y + 1, z), next = cells.get(x, y - 1, z);
                        h.assertTrue(region == CaveRegion.ROOTWAYS && (over.getBlock() instanceof SkinBlocks.RootCurtain || over.isSolidRender()),
                                "a root curtain hangs from rock in the Rootways at " + x + " " + y + " " + z);
                        h.assertTrue(s.getValue(SkinBlocks.RootCurtain.TIP) == !(next.getBlock() instanceof SkinBlocks.RootCurtain),
                                "a root curtain's last block is its tip");
                    } else if (b == CaveSkin.ROOT_AMBER.get()) {
                        amber++;
                        int up = 1;
                        while (up < 7 && !(cells.get(x, y + up, z).getBlock() instanceof SkinBlocks.RootCurtain)) up++;
                        h.assertTrue(region == CaveRegion.ROOTWAYS && cells.get(x, y - 1, z).isSolidRender() && up < 7,
                                "root amber lies on rock under a root curtain in the Rootways at " + x + " " + y + " " + z);
                    } else if (b == CaveSkin.RUST_CRUST.get() || b == CaveSkin.RUST_FLOWSTONE.get() || b == CaveSkin.EMBER_LICHEN.get()) {
                        h.assertTrue(region == CaveRegion.OCHRE_DEEP, b + " only in the Ochre Deep");
                        if (b == CaveSkin.RUST_CRUST.get()) rust++;
                        else if (b == CaveSkin.RUST_FLOWSTONE.get()) flow++;
                        else ember++;
                    } else if (region == CaveRegion.OCHRE_DEEP && (b == CaveSkin.MINERAL_CRUST.get() || b == CaveSkin.WET_STREAKS.get())) {
                        h.fail("the Ochre Deep's walls are rust, not " + b);
                    }
                }
            }
        }
        double ms = nanos / 1e6 / n, fill = fillNanos / 1e6 / n, glow = glowcaps / (double) Math.max(1, floors);
        Awaken.LOG.info("[caves] regions on {} timed chunks of " + 2 * REGION_CHUNKS + ": {} cells of region rock, {} glowcaps ({} of {} open floors), {} root curtain blocks, {} root amber, {} rust crust, {} rust flowstone, {} ember lichen; {} ms a chunk against {} ms to fill it",
                n, swapped, glowcaps, String.format(Locale.ROOT, "%.3f", glow), floors, roots, amber, rust, flow, ember, String.format(Locale.ROOT, "%.3f", ms),
                String.format(Locale.ROOT, "%.2f", fill));
        h.assertTrue(glowcaps > 0 && roots > 0 && amber > 0 && rust > 0 && flow > 0 && ember > 0, "every region piece is laid");
        h.assertTrue(glow <= GLOW_MAX, "glowcaps stay sparse: " + Math.round(glow * 1000) / 10.0 + "% of open floors (gate " + Math.round(GLOW_MAX * 100) + "%)");
        Collections.sort(ratios);
        double mid = ratios.isEmpty() ? 0 : (ratios.get((ratios.size() - 1) / 2) + ratios.get(ratios.size() / 2)) / 2;
        h.assertTrue(mid <= REGION_SHARE, "the skin with the regions costs " + String.format(Locale.ROOT, "%.2f", ms) + " ms a chunk, "
                + Math.round(mid * 100) + "% of the fill on the middle chunk (budget " + Math.round(REGION_SHARE * 100) + "%)");
        h.succeed();
    }

    /** The P3 regions' own pieces (SPEC §42.8), by region: the first of each is its floor's, and must be laid. */
    private static Map<CaveRegion, List<Block>> pieces() {
        Map<CaveRegion, List<Block>> m = new java.util.EnumMap<>(CaveRegion.class);
        m.put(CaveRegion.PALE_HALLS, List.of(CaveSkin.RIMSTONE.get(), CaveSkin.CAVE_PEARLS.get()));
        m.put(CaveRegion.WINDCLEFT, List.of(CaveSkin.RIME.get(), CaveSkin.OLD_ROPE.get()));
        m.put(CaveRegion.DROWNED_GALLERIES, List.of(CaveSkin.SILT.get(), CaveSkin.DRIFTWOOD.get(), CaveSkin.CAVE_WEED.get(), CaveSkin.GLOW_ALGAE.get()));
        m.put(CaveRegion.GLIMMER_HOLLOWS, List.of(CaveSkin.AMBER_CRYSTAL.get(), CaveSkin.GLACIAL_CRYSTAL.get()));
        m.put(CaveRegion.BLACK_VAULTS, List.of(CaveSkin.ASH.get()));
        m.put(CaveRegion.VIOLET_DEEP, List.of(CaveSkin.VIOLET_GROWTH.get(), CaveSkin.SHED_CHITIN.get()));
        return m;
    }

    /**
     * The P3 regions' pieces (SPEC §42.8) on real filled chunks, two told they are all one region for each of the six:
     * each lays its floor's piece (the Glimmer Hollows either crystal) and no other region's, and driftwood and shed
     * chitin keep to their own share of the roll (at most twice their rate of the open floors: a silt or growth roll that
     * missed its field once laid driftwood on a third of the Drowned Galleries' floor).
     */
    private static void regionPieces(GameTestHelper h) {
        Pair p = pair(h);
        var level = h.getLevel();
        LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);
        long seed = STUDIO_SEEDS[0];
        RandomState rs = p.random(h, seed, true);
        Map<CaveRegion, List<Block>> pieces = pieces();
        StringBuilder log = new StringBuilder();
        int k = 0;
        for (var e : pieces.entrySet()) {
            CaveRegion region = e.getKey();
            Map<Block, Integer> count = new java.util.IdentityHashMap<>();
            int floors = 0;
            for (int i = 0; i < 2; i++) {
                ChunkPos pos = new ChunkPos(-40 + i * 21, -50 + k * 9);
                ProtoChunk chunk = build(level, p.shaped(), rs, pos, heights);
                int cx = pos.getMinBlockX(), cz = pos.getMinBlockZ();
                CaveSkinFeature.Cells cells = benchCells(chunk, cx, cz);
                int limit = CaveSkinFeature.REGION_UNDER;
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                    int top = cells.top(cx + dx, cz + dz);
                    for (int y = -60; y < top - limit; y++) {
                        if (cells.get(cx + dx, y, cz + dz).isAir() && cells.get(cx + dx, y - 1, cz + dz).isSolidRender()) floors++;
                    }
                }
                CaveSkinFeature.dress(cells, cx, cz, seed, chunk, (x, y, z) -> region);
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = -62; y < 300; y++) {
                    Block b = cells.get(cx + dx, y, cz + dz).getBlock();
                    for (var other : pieces.entrySet()) {
                        if (!other.getValue().contains(b)) continue;
                        h.assertTrue(other.getKey() == region, b + " only in the " + other.getKey() + ", not the " + region + " at " + (cx + dx) + " " + y + " " + (cz + dz));
                        count.merge(b, 1, Integer::sum);
                    }
                }
            }
            k++;
            List<Block> own = e.getValue();
            int first = region == CaveRegion.GLIMMER_HOLLOWS ? count.getOrDefault(own.get(0), 0) + count.getOrDefault(own.get(1), 0)
                    : count.getOrDefault(own.get(0), 0);
            log.append(region.id).append(": ").append(floors).append(" floors");
            for (Block b : own) log.append(", ").append(count.getOrDefault(b, 0)).append(' ').append(b.builtInRegistryHolder().key().identifier().getPath());
            log.append("; ");
            h.assertTrue(first > 0, "the " + region + " lays its floor's " + own.get(0) + " (" + floors + " open floors)");
            for (Block b : new Block[] {CaveSkin.DRIFTWOOD.get(), CaveSkin.SHED_CHITIN.get()}) {
                if (!own.contains(b)) continue;
                double rate = b == CaveSkin.DRIFTWOOD.get() ? CaveSkinFeature.DRIFTWOOD : CaveSkinFeature.CHITIN;
                double share = count.getOrDefault(b, 0) / (double) Math.max(1, floors);
                h.assertTrue(share <= 2 * rate, b + " keeps to its share in the " + region + ": " + String.format(Locale.ROOT, "%.3f", share)
                        + " of open floors (rate " + rate + ")");
            }
        }
        Awaken.LOG.info("[caves] region pieces: {}", log);
        h.succeed();
    }

    /**
     * The underlife's sites (SPEC §42.5): a stone room 9 x 7 x 9 with a low alcove (its roof at 2), a 2 x 2 pool sunk in its
     * floor and a lone source, lichen on one wall. Each cell reads as the place it is, and a crawler stays on its face.
     */
    private static void underlife(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 3, 0));
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                water = Blocks.WATER.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -3; dy <= 8; dy++) {
            boolean room = Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && dy >= 0 && dy <= 6 && !(dx >= 3 && dy >= 2);
            level.setBlock(o.offset(dx, dy, dz), room ? air : stone, Block.UPDATE_CLIENTS);
        }
        for (int dx = -3; dx <= -2; dx++) for (int dz = -3; dz <= -2; dz++) level.setBlock(o.offset(dx, -1, dz), water, Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(2, -1, 2), water, Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(-4, 1, 0), CaveSkin.LICHEN_MIDDLE.get().defaultBlockState()
                .setValue(MultifaceBlock.getFaceProperty(Direction.WEST), true), Block.UPDATE_CLIENTS);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.function.BiFunction<int[], Integer, Boolean> is = (c, bits) -> UnderSites.classify(level, m, o.getX() + c[0], o.getY() + c[1], o.getZ() + c[2]) == bits;
        h.assertTrue(is.apply(new int[]{-4, 0, 0}, UnderSites.FLOOR | UnderSites.WALL), "a floor by a wall");
        h.assertTrue(is.apply(new int[]{0, 0, 0}, UnderSites.FLOOR), "the open floor");
        h.assertTrue(is.apply(new int[]{0, 3, 0}, 0), "mid-air is no place");
        h.assertTrue(is.apply(new int[]{0, 6, 0}, UnderSites.ROOF | UnderSites.ROOST), "a high roof is a roost");
        h.assertTrue(is.apply(new int[]{4, 1, 0}, UnderSites.ROOF | UnderSites.WALL), "the alcove's low roof is no roost");
        h.assertTrue(is.apply(new int[]{-4, 1, 0}, UnderSites.WALL), "lichen on a wall is open to a crawler");
        h.assertTrue(is.apply(new int[]{-3, -1, -3}, UnderSites.POOL), "a pool");
        h.assertTrue(is.apply(new int[]{2, -1, 2}, 0), "a lone source is no pool");
        h.assertTrue(is.apply(new int[]{0, -1, 0}, 0), "rock is no place");
        // a crawler on the west wall stays on it; past the roof, in the rock, or over the pool it doesn't
        h.assertTrue(UnderSites.onSurface(level, m, o.getX() - 4 + 0.01, o.getY() + 2.5, o.getZ() + 0.5, Direction.EAST), "on the west wall");
        h.assertFalse(UnderSites.onSurface(level, m, o.getX() - 4 + 0.01, o.getY() + 7.5, o.getZ() + 0.5, Direction.EAST), "past the roof");
        h.assertTrue(UnderSites.onSurface(level, m, o.getX() + 0.5, o.getY() + 0.01, o.getZ() + 0.5, Direction.UP), "on the floor");
        h.assertFalse(UnderSites.onSurface(level, m, o.getX() - 2.5, o.getY() + 0.01, o.getZ() - 2.5, Direction.UP), "on the pool");
        h.assertTrue(UnderSites.onSurface(level, m, o.getX() + 0.5, o.getY() + 6.99, o.getZ() + 0.5, Direction.DOWN), "on the roof");
        h.assertTrue(UnderSites.heldLight(new ItemStack(Items.TORCH)) == 14, "a torch gives 14");
        h.assertTrue(UnderSites.heldLight(new ItemStack(Items.STONE)) == 0, "stone gives none");
        h.succeed();
    }

    /** Up to {@code each} chunks per region near the origin, where the region holds at the chunk's middle at some height. */
    private static Map<CaveRegion, List<ChunkPos>> regionChunks(GameTestHelper h, RandomState rs, NoiseRouter router, int each) {
        var functions = h.getLevel().registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction[] fields = new DensityFunction[3];
        for (var band : CaveRegion.Band.values()) fields[band.ordinal()] = fn(functions, "caves/" + band.key());
        Map<CaveRegion, List<ChunkPos>> out = new EnumMap<>(CaveRegion.class);
        for (int cx = -48; cx <= 48; cx += 3) {
            for (int cz = -48; cz <= 48; cz += 3) {
                int x = cx * 16 + 8, z = cz * 16 + 8;
                float hum = rs.sampleBlockValueUncached(router.vegetation(), x, 0, z), temp = rs.sampleBlockValueUncached(router.temperature(), x, 0, z);
                float cont = rs.sampleBlockValueUncached(router.continents(), x, 0, z), ero = rs.sampleBlockValueUncached(router.erosion(), x, 0, z);
                for (int y = -56; y <= CaveRegion.MAX_Y; y += 8) {
                    float d = rs.sampleBlockValueUncached(router.depth(), x, y, z);
                    if (d < CaveShapeFunction.SEAL + CaveShapeFunction.SEAL_FADE) continue;
                    CaveRegion.Band band = CaveRegion.Band.at(y, d);
                    CaveRegion r = CaveRegion.at(y, d, rs.sampleBlockValueUncached(fields[band.ordinal()], x, 0, z), hum, temp, cont, ero);
                    if (r == null) continue;
                    var list = out.computeIfAbsent(r, k -> new ArrayList<>());
                    // spread out (six chunks apart at least), so one patch of a shape doesn't stand for the region
                    boolean near = false;
                    for (ChunkPos q : list) near |= Math.abs(q.x() - cx) < 6 && Math.abs(q.z() - cz) < 6;
                    if (list.size() < each && !near) list.add(new ChunkPos(cx, cz));
                }
            }
        }
        return out;
    }

    /**
     * Fills one chunk (the noise, the surface and the carvers) away from the live world, which would wait on the server
     * thread this test holds: no structure starts (the beardifier is empty for both fills alike) and the surface laid as
     * plains everywhere (an uncached biome lookup per column would cost more than the fill and hide its cost).
     */
    /** One filled chunk's cells for the skin (stone all round it), as the bench reads and writes them. */
    private static CaveSkinFeature.Cells benchCells(ProtoChunk chunk, int cx, int cz) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return new CaveSkinFeature.Cells() {
            @Override
            public BlockState get(int x, int y, int z) {
                if ((x & ~15) != cx || (z & ~15) != cz || y < -64 || y >= 320) return stone;
                return chunk.getSection(chunk.getSectionIndex(y)).getBlockState(x & 15, y & 15, z & 15);
            }

            @Override
            public void set(int x, int y, int z, BlockState state) {
                chunk.setBlockState(m.set(x, y, z), state, 0);
            }

            @Override
            public int top(int x, int z) {
                return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }

            @Override
            public int minY() {
                return -64;
            }
        };
    }

    private static ProtoChunk build(ServerLevel level, NoiseBasedChunkGenerator gen, RandomState rs, ChunkPos pos, LevelHeightAccessor heights) {
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, heights, level.palettedContainerFactory(), null);
        Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var biomes = new BiomeManager((qx, qy, qz) -> plains, BiomeManager.obfuscateSeed(rs.seed()));
        gen.buildTerrain(chunk, Blender.empty(), rs, noStructures(level), biomes, null, gen.getBiomeSource().possibleBiomes()).join();
        return chunk;
    }

    private static StructureManager noStructures(ServerLevel level) {
        return new StructureManager(level, new WorldOptions(0L, false, false), null) {
            @Override
            public List<StructureStart> startsForStructure(int sectionX, int sectionZ, Predicate<Structure> matcher) {
                return List.of();
            }
        };
    }

    private static boolean open(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty();
    }

    // ---------------------------------------------------------------- the pack as data

    private static DensityFunction fn(net.minecraft.core.HolderLookup.RegistryLookup<DensityFunction> functions, String path) {
        return functions.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Awaken.id(path))).value();
    }

    /** The caves pack's own JSON files under {@code folder}, by id without the folder or the extension (as LandsPack.files). */
    static SortedMap<Identifier, Resource> files(MinecraftServer server, String folder) {
        SortedMap<Identifier, Resource> out = new TreeMap<>();
        var pack = server.getPackRepository().getPack(CavesModule.PACK_ID);
        if (pack == null) return out;
        for (var e : server.getResourceManager().listResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            if (!e.getValue().sourcePackId().equals(pack.getId())) continue;
            String p = e.getKey().getPath();
            out.put(Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), p.substring(folder.length() + 1, p.length() - ".json".length())), e.getValue());
        }
        return out;
    }

    private static JsonObject json(GameTestHelper h, Resource r) {
        if (r == null) return null;
        try (Reader in = r.openAsReader()) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            h.fail("unreadable caves pack file: " + e);
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        JsonElement e = o.get(key);
        return e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
