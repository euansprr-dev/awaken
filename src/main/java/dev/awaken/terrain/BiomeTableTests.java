package dev.awaken.terrain;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.guide.Cottage;
import dev.awaken.terrain.api.BiomeTable;
import dev.awaken.veil.Spirits;
import dev.awaken.world.fauna.lair.LairStyle;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The biome table (tools/terrain/biomes.py, docs/SPEC.md §15) as the game reads it. The GameTest world is new, so the
 * Awakened Lands pack and its tags are loaded (nothing places its biomes here: Lithostitched is not installed).
 */
final class BiomeTableTests {
    private BiomeTableTests() {}

    /**
     * What every vanilla overworld biome had before the table, from the pre-table data (the fauna modifiers' id lists,
     * the has_lair lists, Cottage's and LairStyle's id and tag checks): biome | Awaken spawners (id:weight:min-max) |
     * lairs | cottage | lair second looks.
     */
    private static final String BEFORE = """
            badlands|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|badlands|glass_hollow
            bamboo_jungle|canopy_prowler:22:1-2,crested_basilisk:18:1-2,dart_frog:28:2-3,plumed_coil:1:1-1|plumed_coil|jungle|
            beach|||meadow|
            birch_forest|empress_moth:1:1-1,frost_stag:8:1-2,frostfinch:8:2-4,glimmerfin:8:3-5,honey_bear:14:1-2,lumen_moth:14:2-3,petalwing:30:2-4|empress_moth|birch|
            cherry_grove|empress_moth:1:1-1,glimmerfin:8:3-5,honey_bear:14:1-2,lumen_moth:14:2-3,petalwing:30:2-4|empress_moth|cherry|
            cold_ocean|breathbell:20:2-3,glimmerfin:6:3-5,tidesteed:12:1-2||taiga|
            dappled_forest|bloom_sprite:20:1-2,briar_sow:1:1-1,frost_stag:8:1-2,frostfinch:8:2-4,gloomstalker:22:1-2,lumen_moth:30:2-4,mossback:14:1-2|briar_sow|meadow|
            dark_forest|glimmerfin:8:3-5,gloam_snail:26:1-3,hollow_owl:14:1-1,thornling:24:2-3,weaver_queen:1:1-1|weaver_queen|swamp|
            deep_cold_ocean|breathbell:20:2-3,glimmerfin:6:3-5,lantern_maw:1:1-1,tidesteed:12:1-2|lantern_maw|taiga|
            deep_dark|chime_cricket:6:1-1,hushling:24:2-3||meadow|
            deep_frozen_ocean|lantern_maw:1:1-1|lantern_maw|taiga|
            deep_lukewarm_ocean|breathbell:20:2-3,glimmerfin:6:3-5,lantern_maw:1:1-1,tidesteed:12:1-2|lantern_maw|meadow|sunken_shrine
            deep_ocean|breathbell:20:2-3,glimmerfin:6:3-5,lantern_maw:1:1-1,tidesteed:12:1-2|lantern_maw|meadow|
            desert|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|desert|
            dripstone_caves|chime_cricket:26:1-3,drip_lurker:22:1-2,gloomstalker:10:1-2,thousandfoot:1:1-1,wisp_hound:10:2-2|thousandfoot|meadow|
            eroded_badlands|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|badlands|glass_hollow
            flower_forest|empress_moth:1:1-1,glimmerfin:8:3-5,honey_bear:14:1-2,lumen_moth:14:2-3,petalwing:30:2-4|empress_moth|meadow|
            forest|bloom_sprite:20:1-2,briar_sow:1:1-1,frost_stag:8:1-2,frostfinch:8:2-4,glimmerfin:8:3-5,gloomstalker:22:1-2,lumen_moth:30:2-4,mossback:14:1-2|briar_sow|meadow|
            frozen_ocean|||taiga|
            frozen_peaks|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1|stonehorn|taiga|rams_cairn
            frozen_river|brook_otter:14:1-2,glimmerfin:30:4-6||taiga|
            grove|frost_stag:22:1-3,frostfinch:26:3-5,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|antler_grove
            ice_spikes|frost_stag:22:1-3,frostfinch:26:3-5,old_antler:1:1-1,wisp_hound:22:2-3||taiga|
            jagged_peaks|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1|stonehorn|taiga|rams_cairn
            jungle|canopy_prowler:22:1-2,crested_basilisk:18:1-2,dart_frog:28:2-3,glimmerfin:8:3-5,plumed_coil:1:1-1|plumed_coil|jungle|
            lukewarm_ocean|breathbell:20:2-3,glimmerfin:6:3-5,tidesteed:12:1-2||meadow|
            lush_caves|chime_cricket:12:1-2,gloomstalker:10:1-2,wickling:26:2-3,wisp_hound:10:2-2|thousandfoot|meadow|crystal_nest
            mangrove_swamp|bog_toad:18:1-2,bogwick:26:2-4,mire_mother:1:1-1,mudskipper:24:2-3|mire_mother|mangrove|
            meadow|bloom_sprite:20:1-2,briar_sow:1:1-1,glimmerfin:8:3-5,gloomstalker:22:1-2,lumen_moth:30:2-4,mossback:14:1-2,petalwing:12:1-3|briar_sow|meadow|
            mushroom_fields|mycelord:1:1-1,shroomlet:30:2-4,sporeback_snail:20:1-2|mycelord|meadow|
            ocean|breathbell:20:2-3,glimmerfin:6:3-5,tidesteed:12:1-2||meadow|
            old_growth_birch_forest|empress_moth:1:1-1,frost_stag:8:1-2,frostfinch:8:2-4,honey_bear:14:1-2,lumen_moth:14:2-3,petalwing:30:2-4|empress_moth|birch|
            old_growth_pine_taiga|frost_stag:22:1-3,frostfinch:26:3-5,glimmerfin:8:3-5,lumen_moth:14:2-3,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|
            old_growth_spruce_taiga|frost_stag:22:1-3,frostfinch:26:3-5,glimmerfin:8:3-5,lumen_moth:14:2-3,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|
            pale_garden|gloam_snail:26:1-3,hollow_owl:14:1-1,thornling:24:2-3,weaver_queen:1:1-1|weaver_queen|swamp|silk_hollow
            plains|bloom_sprite:20:1-2,briar_sow:1:1-1,glimmerfin:8:3-5,gloomstalker:22:1-2,lumen_moth:30:2-4,mossback:14:1-2|briar_sow|meadow|
            river|brook_otter:14:1-2,glimmerfin:30:4-6||meadow|
            savanna|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,glimmerfin:8:3-5,gloomstalker:20:1-2|dune_wyrm|savanna|
            savanna_plateau|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|savanna|
            snowy_beach|||taiga|
            snowy_plains|frost_stag:22:1-3,frostfinch:26:3-5,glimmerfin:8:3-5,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|antler_grove
            snowy_slopes|frost_stag:22:1-3,frostfinch:26:3-5,old_antler:1:1-1,wisp_hound:22:2-3||taiga|antler_grove,rams_cairn
            snowy_taiga|frost_stag:22:1-3,frostfinch:26:3-5,glimmerfin:8:3-5,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|antler_grove
            sparse_jungle|canopy_prowler:22:1-2,crested_basilisk:18:1-2,dart_frog:28:2-3,plumed_coil:1:1-1|plumed_coil|jungle|
            stony_peaks|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1|stonehorn|meadow|
            stony_shore|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1||taiga|
            sulfur_caves|||meadow|
            sunflower_plains|bloom_sprite:20:1-2,briar_sow:1:1-1,glimmerfin:8:3-5,gloomstalker:22:1-2,lumen_moth:30:2-4,mossback:14:1-2,petalwing:12:1-3|briar_sow|meadow|
            swamp|bog_toad:18:1-2,bogwick:26:2-4,glimmerfin:8:3-5,mire_mother:1:1-1,mudskipper:24:2-3|mire_mother|swamp|
            taiga|frost_stag:22:1-3,frostfinch:26:3-5,glimmerfin:8:3-5,lumen_moth:14:2-3,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|
            warm_ocean|breathbell:20:2-3,glimmerfin:6:3-5,tidesteed:12:1-2||meadow|sunken_shrine
            windswept_forest|frost_stag:22:1-3,frostfinch:26:3-5,lumen_moth:14:2-3,old_antler:1:1-1,wisp_hound:22:2-3|old_antler|taiga|
            windswept_gravelly_hills|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1|stonehorn|taiga|
            windswept_hills|crag_ram:18:1-3,peak_kite:16:1-2,scree_marmot:26:2-3,stonehorn:1:1-1|stonehorn|taiga|
            windswept_savanna|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|savanna|
            wooded_badlands|dune_ray:22:1-2,dune_wyrm:1:1-1,glass_scarab:30:2-3,gloomstalker:20:1-2|dune_wyrm|badlands|glass_hollow
            """;

    static void register() {
        AwakenTests.register("terrain_biome_table_census", 20, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
            // the Awakened biomes: every biome the lands pack defines
            var pack = server.getPackRepository().getPack(LandsPack.ID);
            h.assertTrue(pack != null, "the Awakened Lands pack is offered");
            TreeSet<Identifier> lands = new TreeSet<>();
            for (var e : server.getResourceManager().listResources("worldgen/biome", id -> id.getPath().endsWith(".json")).entrySet()) {
                if (!e.getValue().sourcePackId().equals(pack.getId())) continue;
                String p = e.getKey().getPath();
                lands.add(Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), p.substring("worldgen/biome/".length(), p.length() - ".json".length())));
            }
            h.assertTrue(lands.contains(Awaken.id("elderwood")), "the lands pack's biomes are found: " + lands);
            for (Identifier id : lands) {
                var found = biomes.get(ResourceKey.create(Registries.BIOME, id));
                h.assertTrue(found.isPresent(), id + " is in the biome registry");
                Holder<Biome> b = found.get();
                h.assertTrue(b.is(BiomeTags.IS_OVERWORLD), id + " is in #minecraft:is_overworld");
                // its archetype: one vanilla overworld biome
                h.assertTrue(tags(b, "archetype/").size() == 1, id + " has one archetype tag: " + tags(b, "archetype/"));
                ResourceKey<Biome> arch = BiomeTable.archetype(b);
                h.assertTrue(arch != null && arch.identifier().getNamespace().equals("minecraft"), id + " stands in for a vanilla biome: " + arch);
                var archetype = biomes.get(arch);
                h.assertTrue(archetype.isPresent() && archetype.get().is(BiomeTags.IS_OVERWORLD), id + "'s archetype " + arch.identifier() + " is a vanilla overworld biome");
                // fauna: in a group, and the spawn modifiers really reached it
                h.assertTrue(!tags(b, "fauna/").isEmpty(), id + " is in a fauna group");
                h.assertTrue(!spawners(b).isEmpty(), id + " spawns Awaken creatures");
                // the cottage: one plan, and the cottage reads it
                List<String> plans = tags(b, "cottage/");
                h.assertTrue(plans.size() == 1, id + " has one cottage plan: " + plans);
                h.assertTrue(Cottage.palette(b).id().equals(plans.get(0)), id + " builds a " + plans.get(0) + " cottage, got " + Cottage.palette(b).id());
                // spirits: found through the archetype's name
                h.assertTrue(!Spirits.forBiome(b).isEmpty(), id + " has spirits (archetype " + arch.identifier() + ")");
            }
            // and every vanilla overworld biome has its row (a cottage plan each)
            for (Holder<Biome> b : vanillaOverworld(biomes)) {
                h.assertTrue(tags(b, "cottage/").size() == 1, b.getRegisteredName() + " has one cottage plan: " + tags(b, "cottage/"));
                h.assertTrue(b.unwrapKey().equals(java.util.Optional.ofNullable(BiomeTable.archetype(b))), b.getRegisteredName() + " is its own archetype");
            }
            h.succeed();
        });

        AwakenTests.register("terrain_biome_table_parity", 20, h -> {
            var access = h.getLevel().registryAccess();
            var biomes = access.lookupOrThrow(Registries.BIOME);
            Map<String, String> before = new LinkedHashMap<>();
            for (String line : BEFORE.strip().split("\\R")) before.put(line.strip().substring(0, line.strip().indexOf('|')), line.strip());
            // lair structures by beast
            Map<String, Structure> lairs = new TreeMap<>();
            for (var s : access.lookupOrThrow(Registries.STRUCTURE).listElements().toList()) {
                Identifier k = s.key().identifier();
                if (k.getNamespace().equals(Awaken.MODID) && k.getPath().startsWith("lair/")) lairs.put(k.getPath().substring("lair/".length()), s.value());
            }
            h.assertTrue(lairs.size() == LairStyle.values().length, "every lair structure: " + lairs.keySet());
            List<String> wrong = new ArrayList<>();
            var seen = new TreeSet<String>();
            for (Holder<Biome> b : vanillaOverworld(biomes)) {
                String name = b.unwrapKey().orElseThrow().identifier().getPath();
                seen.add(name);
                List<String> lair = new ArrayList<>(), looks = new ArrayList<>();
                for (var e : lairs.entrySet()) if (e.getValue().biomes().contains(b)) lair.add(e.getKey());
                for (LairStyle s : LairStyle.values()) if (s.variant(b) == 1) looks.add(s.getSerializedName());
                String now = name + "|" + String.join(",", spawners(b)) + "|" + String.join(",", lair) + "|" + Cottage.palette(b).id() + "|" + String.join(",", looks);
                String was = before.get(name);
                if (!now.equals(was)) wrong.add("\n  was " + was + "\n  now " + now);
                // spirits: a vanilla biome is its own archetype, so the lookup by holder is the lookup by name
                if (!Spirits.forBiome(b).equals(Spirits.forBiome(b.getRegisteredName()))) wrong.add("\n  spirits of " + name);
            }
            h.assertTrue(seen.equals(new TreeSet<>(before.keySet())), "the same vanilla overworld biomes: " + seen);
            h.assertTrue(wrong.isEmpty(), "vanilla biomes changed:" + String.join("", wrong));
            h.succeed();
        });
    }

    /** Every vanilla biome in #minecraft:is_overworld. */
    private static List<Holder<Biome>> vanillaOverworld(net.minecraft.core.HolderLookup.RegistryLookup<Biome> biomes) {
        List<Holder<Biome>> out = new ArrayList<>();
        for (Holder<Biome> b : biomes.getOrThrow(BiomeTags.IS_OVERWORLD)) {
            if (b.unwrapKey().orElseThrow().identifier().getNamespace().equals("minecraft")) out.add(b);
        }
        return out;
    }

    /** The biome table's awaken: tags on a biome under one folder, by name (e.g. "cottage/" gives ["birch"]). */
    private static List<String> tags(Holder<Biome> b, String folder) {
        List<String> out = new ArrayList<>();
        b.tags().map(TagKey::location).filter(t -> t.getNamespace().equals(Awaken.MODID) && t.getPath().startsWith(folder))
                .forEach(t -> out.add(t.getPath().substring(folder.length())));
        out.sort(null);
        return out;
    }

    /** The biome's Awaken spawners as id:weight:min-max, sorted, every category. */
    private static List<String> spawners(Holder<Biome> b) {
        List<String> out = new ArrayList<>();
        var e = b.value().getAttributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        if (e == null || !(e.argument() instanceof MobSpawnSettings s)) return out;
        for (MobCategory cat : MobCategory.values()) {
            for (Weighted<MobSpawnSettings.SpawnerData> w : s.getMobsToSpawn(cat).unwrap()) {
                Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type());
                if (!type.getNamespace().equals(Awaken.MODID)) continue;
                out.add(type.getPath() + ":" + w.weight() + ":" + w.value().count().minInclusive() + "-" + w.value().count().maxInclusive());
            }
        }
        out.sort(null);
        return out;
    }
}
