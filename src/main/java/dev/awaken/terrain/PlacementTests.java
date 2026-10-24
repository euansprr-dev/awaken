package dev.awaken.terrain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where the Awakened biomes go (docs/SPEC.md §15, "Placement"). The lands pack's Lithostitched injectors are read as
 * data and held to the biome table; every biome with ground of its own has both its material-rule splices; and the share
 * of land the Awakened biomes take on the five studio seeds is measured by applying the injectors to the vanilla
 * generator the way Lithostitched does (its {@code InjectorBiomeSource}: the first replace_partially injector whose
 * target is the biome there and whose field is in range wins, the field sampled at the biome's quart corner). The
 * GameTest world has no Lithostitched or Tectonic; the studio on the Mac measures the real thing.
 */
final class PlacementTests {
    private PlacementTests() {}

    /** The terrain studio's five seeds (tools/autopilot/terrain-survey.txt). */
    static final long[] STUDIO_SEEDS = {777L, 20261024L, 4242L, 1337L, 90210L};
    /** How far round the origin, and how finely, each seed is surveyed (the studio: 3072 at 32). */
    private static final int RADIUS = 3072, STEP = 96;
    /**
     * The share of land the Awakened biomes take on the studio seeds, mean over the five (plan: about 40%). Tuned
     * 2026-10-24 to 41.5% here and 40.6% under Tectonic's {@code awakened} preset; a threshold or target change that
     * moves it far either way fails.
     */
    static final double SHARE_MIN = 0.34, SHARE_MAX = 0.48;

    /**
     * Which overworld rule each part of an Awakened biome's ground goes first in: its top above water, what lies under
     * it, or (the marsh) its whole floor, under water too (tools/content/lands3_content.py, TARGETS).
     */
    private static final Map<String, String> PARTS = Map.of("surface", "minecraft:overworld/biome_surface",
            "under", "minecraft:overworld/under_biome_surface", "floor", "minecraft:overworld/surface");

    /** One replace_partially injector: where a target biome stands and the field is within [min, max], the replacement goes instead. */
    record Injector(Identifier id, Set<ResourceKey<Biome>> targets, ResourceKey<Biome> replacement, Identifier field, float min, float max) {}

    static void register() {
        AwakenTests.register("terrain_lands_placement", 2400, PlacementTests::placement);
    }

    private static void placement(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var access = h.getLevel().registryAccess();
        HolderLookup.RegistryLookup<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
        var functions = access.lookupOrThrow(Registries.DENSITY_FUNCTION);
        List<Injector> injectors = injectors(h, LandsPack.files(server, "lithostitched/biome_injector"));
        var lands = LandsPack.files(server, "worldgen/biome").keySet();

        // every Awakened biome has one injector; it stands in for its archetype there, and only on vanilla land biomes
        Map<ResourceKey<Biome>, Identifier> claimed = new HashMap<>();
        for (Identifier id : lands) {
            List<Injector> mine = injectors.stream().filter(i -> i.replacement().identifier().equals(id)).toList();
            h.assertTrue(mine.size() == 1, id + " has one injector: " + mine.stream().map(Injector::id).toList());
            Injector in = mine.get(0);
            ResourceKey<Biome> arch = BiomeTable.archetype(biomes.getOrThrow(in.replacement()));
            h.assertTrue(in.targets().contains(arch), id + " replaces its archetype " + arch + ": " + in.targets());
            h.assertTrue(functions.get(ResourceKey.create(Registries.DENSITY_FUNCTION, in.field())).isPresent(), in.id() + " reads " + in.field() + ", which exists");
            h.assertTrue(in.min() <= in.max(), in.id() + " has a range: " + in.min() + ".." + in.max());
            for (ResourceKey<Biome> t : in.targets()) {
                var target = biomes.get(t);
                h.assertTrue(target.isPresent() && t.identifier().getNamespace().equals("minecraft") && target.get().is(BiomeTags.IS_OVERWORLD),
                        in.id() + " targets a vanilla overworld biome: " + t.identifier());
                Identifier before = claimed.put(t, id);
                h.assertTrue(before == null, t.identifier() + " is claimed by both " + before + " and " + id);
            }
        }
        h.assertTrue(injectors.size() == lands.size(), "no injector without its biome: " + injectors.stream().map(Injector::id).toList());

        // ground of its own goes first in the overworld's surface pass, guarded by its biome
        var rules = LandsPack.files(server, "worldgen/material_rule");
        var modifiers = LandsPack.files(server, "lithostitched/worldgen_modifier");
        for (Identifier rule : rules.keySet()) {
            String name = rule.getPath().substring(rule.getPath().lastIndexOf('/') + 1);
            String part = name.substring(name.lastIndexOf('_') + 1);
            String target = PARTS.get(part);
            h.assertTrue(target != null, rule + " is a biome's _surface, _under or _floor rule");
            String biome = name.substring(0, name.length() - part.length() - 1);
            h.assertTrue(lands.contains(Awaken.id(biome)), rule + " belongs to a lands biome");
            JsonObject m = json(h, modifiers.get(rule));
            h.assertTrue(m != null, rule + " is spliced in by a modifier of the same name");
            h.assertTrue("lithostitched:set_material_rule".equals(str(m, "type")) && "prepend".equals(str(m, "injection_type")), rule + " goes first");
            h.assertTrue(target.equals(str(m, "target_rules")), rule + " splices into " + target + ", not " + str(m, "target_rules"));
            JsonObject guard = m.getAsJsonObject("material_rule");
            boolean guarded = guard != null && "minecraft:condition".equals(str(guard, "type"))
                    && ("awaken:" + biome).equals(str(guard.getAsJsonObject("if_true"), "biome_is")) && rule.toString().equals(str(guard, "then_run"));
            h.assertTrue(guarded, rule + " runs only in " + biome);
        }

        // the share of land they take on the studio seeds
        var settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(preset), settings);
        Map<Identifier, DensityFunction> fields = new HashMap<>();
        for (Injector in : injectors) fields.computeIfAbsent(in.field(), f -> functions.getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, f)).value());
        Map<String, Integer> byBiome = new TreeMap<>();
        // per Awakened biome: of its archetypes' land, how much it claims (the rest stays vanilla)
        Map<String, Integer> archetypeLand = new TreeMap<>();
        float[] field = new float[1 << 16];
        int sampled = 0;
        StringBuilder log = new StringBuilder();
        double sum = 0;
        long t0 = System.currentTimeMillis();
        for (long seed : STUDIO_SEEDS) {
            RandomState rs = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
            Survey.Grid g = Survey.sample("placement", seed, gen, rs, h.getLevel(), 0, 0, RADIUS, STEP);
            int land = 0, awakened = 0, targets = 0;
            for (int row = 0; row < g.size(); row++) {
                for (int col = 0; col < g.size(); col++) {
                    int i = g.at(col, row), floor = g.floor()[i];
                    if (floor < g.seaLevel()) continue;
                    land++;
                    var key = ResourceKey.create(Registries.BIOME, Identifier.parse(g.palette().get(g.biome()[i])));
                    int x = g.originX() + col * STEP, z = g.originZ() + row * STEP, y = Math.max(floor, g.seaLevel());
                    for (Injector in : injectors) {
                        if (!in.targets().contains(key)) continue;
                        targets++;
                        archetypeLand.merge(in.replacement().identifier().getPath(), 1, Integer::sum);
                        // Lithostitched samples the field at the biome's quart corner
                        float v = rs.sampleBlockValueUncached(fields.get(in.field()), QuartPos.toBlock(QuartPos.fromBlock(x)),
                                QuartPos.toBlock(QuartPos.fromBlock(y)), QuartPos.toBlock(QuartPos.fromBlock(z)));
                        if (sampled == field.length) field = Arrays.copyOf(field, sampled * 2);
                        field[sampled++] = v;
                        if (v < in.min() || v > in.max()) break;
                        awakened++;
                        byBiome.merge(in.replacement().identifier().getPath(), 1, Integer::sum);
                        break;
                    }
                }
            }
            double share = awakened / (double) Math.max(1, land);
            sum += share;
            log.append(String.format(Locale.ROOT, " seed %d: %.0f%% of land (archetypes %.0f%%);", seed, share * 100, targets * 100.0 / Math.max(1, land)));
        }
        double mean = sum / STUDIO_SEEDS.length;
        int total = byBiome.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder split = new StringBuilder();
        byBiome.forEach((b, n) -> split.append(String.format(Locale.ROOT, " %s %.0f%% (%.0f%% of its archetypes)", b, n * 100.0 / Math.max(1, total),
                n * 100.0 / Math.max(1, archetypeLand.getOrDefault(b, 0)))));
        // the field over the archetypes' land (it is terrain-blind: the same under Tectonic), for tuning the thresholds
        Arrays.sort(field, 0, sampled);
        StringBuilder deciles = new StringBuilder();
        for (int d = 1; d <= 9; d++) deciles.append(String.format(Locale.ROOT, " %.2f", sampled == 0 ? 0 : field[sampled * d / 10]));
        Awaken.LOG.info("[terrain] lands placement (vanilla shapes, {} blocks round the origin every {}):{} mean {}%; of it:{}; the field's deciles there:{}; {} ms",
                RADIUS, STEP, log, String.format(Locale.ROOT, "%.1f", mean * 100), split, deciles, System.currentTimeMillis() - t0);
        for (Identifier id : lands) h.assertTrue(byBiome.getOrDefault(id.getPath(), 0) > 0, id + " is placed on the studio seeds");
        h.assertTrue(mean >= SHARE_MIN && mean <= SHARE_MAX, "Awakened biomes take " + Math.round(mean * 100) + "% of the land (want "
                + Math.round(SHARE_MIN * 100) + "-" + Math.round(SHARE_MAX * 100) + "%)");
        h.succeed();
    }

    /** The pack's replace_partially injectors, each with its one field and range (anything else fails the test). */
    private static List<Injector> injectors(GameTestHelper h, Map<Identifier, Resource> files) {
        List<Injector> out = new ArrayList<>();
        for (var e : files.entrySet()) {
            JsonObject o = json(h, e.getValue());
            h.assertTrue(o != null && "lithostitched:replace_partially".equals(str(o, "type")), e.getKey() + " is a replace_partially injector");
            h.assertTrue("minecraft:overworld".equals(str(o, "dimension")), e.getKey() + " is for the overworld");
            Set<ResourceKey<Biome>> targets = new HashSet<>();
            for (JsonElement t : o.getAsJsonArray("targets")) targets.add(ResourceKey.create(Registries.BIOME, Identifier.parse(t.getAsString())));
            JsonObject params = o.getAsJsonObject("parameters");
            h.assertTrue(params != null && params.size() == 1, e.getKey() + " reads one field: " + params);
            var p = params.entrySet().iterator().next();
            JsonObject range = p.getValue().getAsJsonObject();
            float min = range.has("min_inclusive") ? range.get("min_inclusive").getAsFloat() : -Float.MAX_VALUE;
            float max = range.has("max_inclusive") ? range.get("max_inclusive").getAsFloat() : Float.MAX_VALUE;
            out.add(new Injector(e.getKey(), targets, ResourceKey.create(Registries.BIOME, Identifier.parse(str(o, "replacement"))),
                    Identifier.parse(p.getKey()), min, max));
        }
        return out;
    }

    private static JsonObject json(GameTestHelper h, Resource r) {
        if (r == null) return null;
        try (Reader in = r.openAsReader()) {
            return JsonParser.parseReader(in).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            h.fail("unreadable lands pack file: " + e);
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
