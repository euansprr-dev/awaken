package dev.awaken.world.fauna;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.FaunaTalent;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import dev.awaken.progression.Progression;
import dev.awaken.world.fauna.lair.LairBuilder;
import dev.awaken.world.fauna.lair.LairStructure;
import dev.awaken.world.fauna.lair.LairStyle;
import dev.awaken.world.fauna.lair.LairWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for P2 of the Awakened layer: the six new families (bestiary, biome modifiers, natural spawning in each
 * family's biome by day and by night, the role caps with every family around one player) and the named beasts' lairs
 * (structures and sets through the registry, where the placement rules put lairs on a real overworld generator, a
 * lair built and its beast waking under the fauna rules, falling and leaving the lair quiet, rumours and the tracker).
 * The P2 creatures' talents are tested in the mobs module ({@code mobs.entity.WildTalentTests}).
 */
final class WildTests {
    private WildTests() {}

    static final int UP = FaunaTests.UP;

    /** The P2 families, each with a biome it lives in and the ground a test patch there gets. */
    record Family(Theme theme, ResourceKey<Biome> biome, Block ground, String modifier) {}

    static final List<Family> FAMILIES = List.of(
            new Family(Theme.MIRE, Biomes.SWAMP, Blocks.GRASS_BLOCK, "fauna_mire"),
            new Family(Theme.CANOPY, Biomes.JUNGLE, Blocks.GRASS_BLOCK, "fauna_canopy"),
            new Family(Theme.DUSKWOOD, Biomes.DARK_FOREST, Blocks.GRASS_BLOCK, "fauna_duskwood"),
            new Family(Theme.BLOSSOM, Biomes.CHERRY_GROVE, Blocks.GRASS_BLOCK, "fauna_blossom"),
            new Family(Theme.CRAG, Biomes.STONY_PEAKS, Blocks.STONE, "fauna_crag"),
            new Family(Theme.SPORE, Biomes.MUSHROOM_FIELDS, Blocks.MYCELIUM, "fauna_spore"));

    static boolean p2(MobDef d) {
        return d.theme.ordinal() > Theme.FROSTWILD.ordinal() && d.isFauna();
    }

    static void register() {
        AwakenTests.register("fauna_p2_bestiary", 20, h -> {
            Map<Theme, List<MobDef>> byFamily = new EnumMap<>(Theme.class);
            int n = 0;
            for (MobDef d : Bestiary.fauna()) {
                if (!p2(d)) continue;
                n++;
                byFamily.computeIfAbsent(d.theme, k -> new ArrayList<>()).add(d);
                EntityType<?> type = MobTypes.type(d);
                h.assertTrue(type != null && type.getCategory() == MobTypes.FAUNA, d.id + " is in the fauna category");
                h.assertTrue(SpawnPlacements.getPlacementType(type) == SpawnPlacementTypes.ON_GROUND, d.id + " has a spawn placement");
                h.assertTrue(Habitat.ALL.containsKey(d.id) && !Habitat.of(d.id).hint().isEmpty(), d.id + " has a habitat and a hint");
                h.assertTrue(Habitat.of(d.id).hint().length() <= 40, d.id + "'s hint fits a toast");
                h.assertTrue(d.talent != FaunaTalent.NONE, d.id + " has a talent (a purpose)");
                if (d.fauna == FaunaRole.BEAST) {
                    h.assertTrue(LairStyle.byBeast(d.id) != null, d.id + " has a lair");
                    h.assertTrue(Habitat.of(d.id).minPlayerLevel() == 3, d.id + " from level 3");
                }
            }
            h.assertTrue(n == 22, "22 P2 creatures, have " + n);
            h.assertTrue(byFamily.size() == 6, "six P2 families: " + byFamily.keySet());
            for (Map.Entry<Theme, List<MobDef>> e : byFamily.entrySet()) {
                Set<FaunaRole> roles = EnumSet.noneOf(FaunaRole.class);
                int beasts = 0;
                for (MobDef d : e.getValue()) {
                    roles.add(d.fauna);
                    if (d.fauna == FaunaRole.BEAST) beasts++;
                }
                h.assertTrue(e.getValue().size() >= 3 && e.getValue().size() <= 4, e.getKey() + " has 3-4 creatures");
                h.assertTrue(roles.contains(FaunaRole.AMBIENT) && beasts == 1, e.getKey() + " has ambient creatures and one named beast: " + roles);
            }
            // every named beast, P1's too, has a lair
            for (MobDef d : Bestiary.fauna()) if (d.fauna == FaunaRole.BEAST) h.assertTrue(LairStyle.byBeast(d.id) != null, d.id + "'s lair");
            // the families' materials, two each
            h.assertTrue(FaunaItems.MATERIALS.size() == 12, "twelve materials");
            for (String id : FaunaItems.MATERIALS) {
                h.assertTrue(FaunaItems.get(id) != Items.AIR, id + " registered");
                h.assertTrue(BuiltInRegistries.ITEM.getKey(FaunaItems.get(id)).equals(Awaken.id(id)), id + " under its own id");
            }
            h.succeed();
        });

        AwakenTests.register("fauna_p2_biome_modifiers", 20, h -> {
            var access = h.getLevel().registryAccess();
            var mods = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
            Set<EntityType<?>> listed = new HashSet<>();
            for (String name : List.of("fauna_mire", "fauna_canopy", "fauna_duskwood", "fauna_blossom", "fauna_blossom_meadow", "fauna_crag", "fauna_spore")) {
                Optional<Holder.Reference<BiomeModifier>> m = mods.get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Awaken.id(name)));
                h.assertTrue(m.isPresent() && m.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, name + " decodes as add_spawns");
                var add = (BiomeModifiers.AddSpawnsBiomeModifier) m.get().value();
                h.assertTrue(add.biomes().size() > 0, name + " names biomes");
                for (Weighted<MobSpawnSettings.SpawnerData> w : add.spawners().unwrap()) {
                    MobDef d = MobTypes.def(w.value().type());
                    h.assertTrue(d != null && d.isFauna(), name + " spawns only fauna");
                    listed.add(w.value().type());
                }
            }
            for (MobDef d : Bestiary.fauna()) if (p2(d)) h.assertTrue(listed.contains(MobTypes.type(d)), d.id + " spawns in some biome");
            var biomes = access.lookupOrThrow(Registries.BIOME);
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.SWAMP), "bogwick", "mudskipper", "bog_toad", "mire_mother");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.MANGROVE_SWAMP), "bogwick", "bog_toad");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.BAMBOO_JUNGLE), "dart_frog", "crested_basilisk", "canopy_prowler", "plumed_coil");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.DARK_FOREST), "gloam_snail", "hollow_owl", "thornling", "weaver_queen");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.PALE_GARDEN), "gloam_snail", "thornling");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.CHERRY_GROVE), "petalwing", "honey_bear", "empress_moth");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.MEADOW), "petalwing", "lumen_moth", "briar_sow");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.JAGGED_PEAKS), "scree_marmot", "peak_kite", "crag_ram", "stonehorn");
            FaunaTests.assertSpawns(h, biomes.getOrThrow(Biomes.MUSHROOM_FIELDS), "shroomlet", "sporeback_snail", "mycelord");
            // the dark forest and the blossom forests belong to their new families now
            h.assertTrue(!FaunaCommands.hasFamily(biomes.getOrThrow(Biomes.DARK_FOREST), Theme.GREENWILD), "no Greenwild in the dark forest");
            for (Family f : FAMILIES) h.assertTrue(FaunaCommands.hasFamily(biomes.getOrThrow(f.biome()), f.theme()), f.theme() + " found from the live spawns");
            h.succeed();
        });

        AwakenTests.registerIsolated("fauna_p2_families_spawn", 1200, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP, 30));
            FaunaTests.awaken(p);
            BlockPos start = h.absolutePos(new BlockPos(0, UP, 0));
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            long clock = Math.floorMod(level.getOverworldClockTime(), 24000L);
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other != p && other.distanceToSqr(Vec3.atCenterOf(start)) < 200 * 200) other.setPos(other.getX() + 2000, other.getY(), other.getZ());
            }
            var seq = h.startSequence();
            for (Family f : FAMILIES) {
                seq.thenExecute(() -> {
                    for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                        BlockPos at = start.offset(dx, 0, dz);
                        level.setBlock(at.below(), f.ground().defaultBlockState(), 3);
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                        level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 3);
                    }
                    BlockPos a = start.offset(-10, -4, -10), b = start.offset(10, 6, 10);
                    level.getServer().getCommands().performPrefixedCommand(cmd, "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                            + b.getX() + " " + b.getY() + " " + b.getZ() + " " + f.biome().identifier());
                    level.getServer().getCommands().performPrefixedCommand(cmd, "time set 6000");
                });
                seq.thenIdle(5);
                seq.thenExecute(() -> spawnHere(h, level, p, start, f, 6000L));
                seq.thenExecute(() -> level.getServer().getCommands().performPrefixedCommand(cmd, "time set 18000"));
                seq.thenIdle(5);
                seq.thenExecute(() -> spawnHere(h, level, p, start, f, 18000L));
            }
            seq.thenExecute(() -> {
                level.getServer().getCommands().performPrefixedCommand(cmd, "time set " + clock);
                level.getServer().getPlayerList().remove(p);
            });
            seq.thenSucceed();
        });

        AwakenTests.registerIsolated("fauna_p2_caps_hold_with_every_family", 600, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP, 0));
            FaunaTests.awaken(p);
            while (Progression.profile(p).level() < 3) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            long clock = Math.floorMod(level.getOverworldClockTime(), 24000L);
            List<BlockPos> patches = new ArrayList<>();
            int[][] offsets = {{-24, -24}, {0, -26}, {24, -24}, {-24, 24}, {0, 26}, {24, 24}};
            for (int i = 0; i < FAMILIES.size(); i++) {
                Family f = FAMILIES.get(i);
                BlockPos c = h.absolutePos(new BlockPos(offsets[i][0], UP, offsets[i][1]));
                patches.add(c);
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    level.setBlock(c.offset(dx, -1, dz), f.ground().defaultBlockState(), 3);
                    level.setBlock(c.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(c.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
                }
                BlockPos a = c.offset(-6, -4, -6), b = c.offset(6, 6, 6);
                level.getServer().getCommands().performPrefixedCommand(cmd, "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                        + b.getX() + " " + b.getY() + " " + b.getZ() + " " + f.biome().identifier());
            }
            List<FaunaMob> made = new ArrayList<>();
            h.startSequence()
                    .thenExecute(() -> {
                        clearFauna(level, p);
                        level.getServer().getCommands().performPrefixedCommand(cmd, "time set 6000");
                    })
                    .thenIdle(5)
                    .thenExecute(() -> hammer(h, level, p, patches, made, 6000L))
                    .thenExecute(() -> level.getServer().getCommands().performPrefixedCommand(cmd, "time set 18000"))
                    .thenIdle(5)
                    .thenExecute(() -> hammer(h, level, p, patches, made, 18000L))
                    .thenExecute(() -> {
                        made.forEach(FaunaMob::discard);
                        level.getServer().getCommands().performPrefixedCommand(cmd, "time set " + clock);
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });

        AwakenTests.register("fauna_lair_structures", 20, h -> {
            var access = h.getLevel().registryAccess();
            var structures = access.lookupOrThrow(Registries.STRUCTURE);
            var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            var biomes = access.lookupOrThrow(Registries.BIOME);
            h.assertTrue(BuiltInRegistries.STRUCTURE_TYPE.containsKey(Awaken.id("fauna_lair")), "the fauna_lair structure type");
            var tag = structures.get(TagKey.create(Registries.STRUCTURE, Awaken.id("fauna_lairs")));
            h.assertTrue(tag.isPresent() && tag.get().size() == LairStyle.values().length, "#awaken:fauna_lairs lists every lair");
            for (LairStyle style : LairStyle.values()) {
                Optional<Holder.Reference<Structure>> s = structures.get(LairWorldgen.structure(style.beast));
                h.assertTrue(s.isPresent(), "structure awaken:lair/" + style.beast);
                h.assertTrue(s.get().value() instanceof LairStructure ls && ls.style() == style, style.beast + " builds " + style);
                HolderSet<Biome> where = s.get().value().biomes();
                Optional<Holder.Reference<StructureSet>> set = sets.get(LairWorldgen.set(style.beast));
                h.assertTrue(set.isPresent(), "structure set awaken:lair/" + style.beast);
                h.assertTrue(set.get().value().placement() instanceof RandomSpreadStructurePlacement, style.beast + " spreads at random");
                var spread = (RandomSpreadStructurePlacement) set.get().value().placement();
                int blocks = spread.spacing() * 16;
                if (style == LairStyle.SPORE_RING) h.assertTrue(blocks >= 500 && blocks <= 1000, "mushroom islands are rare: a denser set, " + blocks);
                else h.assertTrue(blocks >= 1500 && blocks <= 2500, style.beast + ": one lair per 1,500-2,500 blocks, " + blocks);
                h.assertTrue(spread.separation() < spread.spacing(), style.beast + " separation < spacing");
                MobDef beast = Bestiary.get(style.beast);
                h.assertTrue(beast != null && beast.fauna == FaunaRole.BEAST, style.beast + " is a named beast");
                // its biomes are its family's: every one of them spawns the family, none another family's beast
                h.assertTrue(where.size() > 0, style.beast + " names biomes");
                for (Holder<Biome> b : where) {
                    h.assertTrue(FaunaCommands.hasFamily(b, beast.theme), style.beast + "'s lair biome " + b.getRegisteredName() + " is " + beast.theme);
                }
            }
            h.assertTrue(structures.get(LairWorldgen.structure("mire_mother")).get().value().biomes().contains(biomes.getOrThrow(Biomes.SWAMP)), "a bog lair in the swamp");
            h.succeed();
        });

        AwakenTests.register("fauna_lair_placement_rules", 200, h -> {
            ServerLevel level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(source, settings);
            long seed = 20261023L;
            RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
            var sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
            var resolver = source.createResolver(sampler);
            var structures = access.lookupOrThrow(Registries.STRUCTURE);
            var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            Map<LairStyle, Integer> found = new EnumMap<>(LairStyle.class);
            int candidates = 0;
            for (LairStyle style : LairStyle.values()) {
                Structure structure = structures.getOrThrow(LairWorldgen.structure(style.beast)).value();
                var spread = (RandomSpreadStructurePlacement) sets.getOrThrow(LairWorldgen.set(style.beast)).value().placement();
                int cells = style == LairStyle.SPORE_RING ? 8 : 5;
                for (int cx = 0; cx < cells; cx++) {
                    for (int cz = 0; cz < cells; cz++) {
                        ChunkPos chunk = spread.getPotentialStructureChunk(seed, cx * spread.spacing(), cz * spread.spacing());
                        candidates++;
                        var ctx = new Structure.GenerationContext(access, gen, source, sampler, random, level.getServer().getStructureTemplateManager(), seed, chunk,
                                level, structure.biomes()::contains);
                        Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(ctx);
                        if (stub.isEmpty()) continue;
                        found.merge(style, 1, Integer::sum);
                        BlockPos at = stub.get().position();
                        // within reach of its start chunk (structure references reach eight chunks)
                        ChunkPos lairChunk = ChunkPos.containing(at);
                        h.assertTrue(Math.abs(lairChunk.x() - chunk.x()) <= 7 && Math.abs(lairChunk.z() - chunk.z()) <= 7, style + " sits near its start chunk");
                        // in the beast's biome, on dry ground (shallow bog for the Mire Mother), flat enough
                        Holder<Biome> b = resolver.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(at.getY()), QuartPos.fromBlock(at.getZ()));
                        h.assertTrue(structure.biomes().contains(b), style + " lair in " + b.getRegisteredName());
                        int top = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        int floor = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        h.assertTrue(top - floor <= (style.wet ? 2 : 0), style + " not in deep water at " + at.toShortString());
                        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                        for (int[] k : new int[][]{{0, 0}, {style.radius, style.radius}, {-style.radius, style.radius}, {style.radius, -style.radius},
                                {-style.radius, -style.radius}}) {
                            int y = gen.getFirstOccupiedHeight(at.getX() + k[0], at.getZ() + k[1], Heightmap.Types.WORLD_SURFACE_WG, level, random);
                            lo = Math.min(lo, y);
                            hi = Math.max(hi, y);
                        }
                        h.assertTrue(hi - lo <= style.maxSlope, style + " on ground flat enough: " + (hi - lo));
                        h.assertTrue(Math.abs(at.getY() - top) <= style.maxSlope + 3, style + " floor near the surface");
                    }
                }
            }
            int total = found.values().stream().mapToInt(Integer::intValue).sum();
            Awaken.LOG.info("[fauna] lair placement: {} of {} candidate chunks became lairs: {}", total, candidates, found);
            h.assertTrue(total >= 3, "lairs exist on a real overworld (" + total + " of " + candidates + "): " + found);
            h.assertTrue(total < candidates / 2, "and are rare: " + total + " of " + candidates);
            h.succeed();
        });

        AwakenTests.registerIsolated("fauna_lair_beast_waits", 600, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 4, 10));
            BlockPos centre = h.absolutePos(new BlockPos(0, UP, 0));
            clearFauna(level, p);
            LairRegistry reg = LairRegistry.get(level.getServer());
            BlockPos den = Lairs.placeAt(level, LairStyle.WALLOW, centre, 42L);
            long key = ChunkPos.containing(centre).pack();
            List<NamedBeast> beast = new ArrayList<>();
            UUID[] id = new UUID[1];
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(den), "the den is ticking"))
                    .thenExecute(() -> {
                        h.assertTrue(LairBuilder.standingRoom(level, den), "the den is standing room");
                        h.assertTrue(level.getBlockState(centre.offset(-1, 0, 0)).is(Blocks.MUD) || level.getBlockState(centre.offset(-1, 0, 0)).is(Blocks.PACKED_MUD),
                                "a wallow of mud at the centre");
                        int berries = 0;
                        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) for (int dy = -6; dy <= 6; dy++) {
                            if (level.getBlockState(centre.offset(dx, dy, dz)).is(Blocks.SWEET_BERRY_BUSH)) berries++;
                        }
                        h.assertTrue(berries >= 3, "a bramble ring of berry bushes: " + berries);
                        LairRegistry.Entry e = reg.get(key);
                        h.assertTrue(e != null && e.beast().equals("briar_sow") && e.den().equals(den), "the lair is registered with its den");
                        // the fauna rules hold at a lair too
                        h.assertTrue("the player hasn't awakened".equals(Lairs.wake(level, reg, e, p, false)), "not before awakening");
                        FaunaTests.awaken(p);
                        h.assertTrue("player level too low".equals(Lairs.wake(level, reg, e, p, false)), "not below level 3");
                        while (Progression.profile(p).level() < 3) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
                        FaunaRules.HearthCheck before = FaunaRules.hearth;
                        FaunaRules.hearth = (l, at) -> true;
                        try {
                            h.assertTrue("inside a Hearth radius".equals(Lairs.wake(level, reg, e, p, false)), "never inside a Hearth radius");
                        } finally {
                            FaunaRules.hearth = before;
                        }
                        level.getGameRules().set(FaunaModule.SPAWNING.get(), false, level.getServer());
                        try {
                            h.assertTrue(Lairs.wake(level, reg, e, p, false) != null, "the fauna game rule switches lairs off too");
                        } finally {
                            level.getGameRules().set(FaunaModule.SPAWNING.get(), true, level.getServer());
                        }
                        // its beast waits there
                        String why = Lairs.wake(level, reg, e, p, true);
                        h.assertTrue(why == null, "the beast wakes (refused: " + why + ")");
                        LairRegistry.Entry woke = reg.get(key);
                        h.assertTrue(woke.resident().isPresent(), "the lair knows its beast");
                        id[0] = woke.resident().get();
                        h.assertTrue(level.getEntity(id[0]) instanceof NamedBeast, "a named beast");
                        NamedBeast b = (NamedBeast) level.getEntity(id[0]);
                        beast.add(b);
                        h.assertTrue(b.def().id.equals("briar_sow"), "the Briar Sow");
                        h.assertTrue(b.isPersistenceRequired(), "it stays");
                        h.assertTrue(b.distanceToSqr(Vec3.atBottomCenterOf(den)) < 4 * 4, "at the den");
                        h.assertTrue(b.hasHome() && b.getHomePosition().equals(den), "its territory is the lair");
                        h.assertTrue("its beast is home".equals(Lairs.wake(level, reg, reg.get(key), p, false)), "one beast per lair");
                    })
                    .thenWaitUntil(() -> {
                        BeastRegistry.Entry be = BeastRegistry.get(level.getServer()).all().stream().filter(x -> x.id().equals(id[0])).findFirst().orElse(null);
                        h.assertTrue(be != null && be.lair(), "the beast registry knows it as a lair beast");
                    })
                    .thenExecute(() -> {
                        // found: standing in the lair pays a discovery and marks it found
                        p.setPos(Vec3.atBottomCenterOf(den.east(3)));
                        Lairs.visit(level, reg, p);
                        h.assertTrue(Progression.profile(p).counter("awaken:discovered/lair/briar_sow") == 1, "finding the lair is a discovery");
                        FaunaState.KnownLair k = p.getData(FaunaModule.STATE.get()).lair(key);
                        h.assertTrue(k != null && k.found(), "remembered as found");
                        beast.get(0).hurtServer(level, level.damageSources().playerAttack(p), 1.0E6F);
                    })
                    .thenWaitUntil(() -> h.assertTrue(beast.get(0).isDeadOrDying(), "felled"))
                    .thenExecute(() -> {
                        LairRegistry.Entry e = reg.get(key);
                        h.assertTrue(e.resident().isEmpty() && e.felledAt() > 0, "the lair is quiet after its beast falls");
                        h.assertTrue("its beast fell; the lair is quiet".equals(Lairs.wake(level, reg, e, p, false)), "no new beast at once");
                        reg.remove(key);
                        level.getServer().getPlayerList().remove(p);
                        // leave the spot as found: the beast's drops would count as another test's loot, the wallow's water
                        // would run into the next test's patch
                        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(den).inflate(16))
                                .forEach(net.minecraft.world.entity.Entity::discard);
                        wipe(level, centre.offset(-LairStyle.EXTENT, -LairStyle.DEPTH, -LairStyle.EXTENT), centre.offset(LairStyle.EXTENT, LairStyle.HEIGHT, LairStyle.EXTENT));
                    })
                    .thenSucceed();
        });

        AwakenTests.registerIsolated("fauna_lair_rumour_and_tracker", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP, 0));
            FaunaTests.awaken(p);
            while (Progression.profile(p).level() < 3) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
            Lairs.Locator before = Lairs.locator;
            try {
                Lairs.locator = (l, beast, near) -> beast.equals("briar_sow") ? near.offset(900, 0, -400) : null;
                BlockPos heard = Lairs.heardNow(p, "briar_sow");
                h.assertTrue(heard != null, "a rumour of the Briar Sow's lair");
                h.assertTrue(Progression.profile(p).counter("awaken:lair_heard/briar_sow") == 1, "the Codex knows it was heard of");
                h.assertTrue(Lairs.heardNow(p, "briar_sow") == null, "one rumour per lair");
                var lines = FaunaNow.suggest(p);
                var lair = lines.stream().filter(s -> s.id().startsWith("fauna:lair:")).findFirst();
                h.assertTrue(lair.isPresent(), "the tracker points at it: " + lines);
                String text = lair.get().text().getString();
                h.assertTrue(text.startsWith("Lair of the Briar Sow · ") && text.endsWith("E"), "names it, how far and which way: " + text);
                h.assertTrue(lair.get().where().isPresent(), "with a compass target");
                h.assertTrue(Lairs.heardNow(p, "mire_mother") == null, "no rumour when there is no lair near");
                // a first sighting of a family's creature from level 3 queues its beast's rumour
                int queued = Lairs.pendingRumours();
                Lairs.onSighting(p, Bestiary.get("bog_toad"));
                h.assertTrue(Lairs.pendingRumours() == queued + 1, "a sighting queues a rumour");
                // once found, the line goes
                FaunaState s = p.getData(FaunaModule.STATE.get());
                FaunaState.KnownLair k = s.lairs().get(0);
                p.setData(FaunaModule.STATE.get(), s.withLair(new FaunaState.KnownLair(k.beast(), k.key(), k.pos(), true)));
                h.assertTrue(FaunaNow.suggest(p).stream().noneMatch(x -> x.id().startsWith("fauna:lair:")), "a found lair leaves the tracker");
                h.assertTrue(Lairs.distance(985).equals("990 m") && Lairs.distance(1234).equals("1.2 km"), "distances read well");
                h.assertTrue(Lairs.possessive("The Briar Sow").equals("The Briar Sow's"), "possessive");
            } finally {
                Lairs.locator = before;
                Lairs.clear();
                level.getServer().getPlayerList().remove(p);
            }
            h.succeed();
        });

        AwakenTests.register("fauna_state_codec_keeps_lairs", 20, h -> {
            FaunaState s = FaunaState.EMPTY.done().withLair(new FaunaState.KnownLair("stonehorn", 77L, new BlockPos(1, 70, 2), false))
                    .withLair(new FaunaState.KnownLair("stonehorn", 77L, new BlockPos(3, 71, 4), true));
            h.assertTrue(s.lairs().size() == 1 && s.lairs().get(0).found() && s.lairs().get(0).pos().getX() == 3, "one lair per key, found stays found");
            var json = FaunaState.MAP_CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, s).getOrThrow();
            FaunaState back = FaunaState.MAP_CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            h.assertTrue(back.equals(s), "the state survives a save");
            var old = com.google.gson.JsonParser.parseString("{\"schema\":1,\"revealed\":true}");
            FaunaState legacy = FaunaState.MAP_CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, old).getOrThrow();
            h.assertTrue(legacy.revealed() && legacy.lairs().isEmpty(), "P1 saves load");
            for (int i = 0; i < FaunaState.MAX_LAIRS + 5; i++) s = s.withLair(new FaunaState.KnownLair("mycelord", 1000L + i, BlockPos.ZERO, false));
            h.assertTrue(s.lairs().size() == FaunaState.MAX_LAIRS && s.lair(77L) != null, "bounded, found lairs kept");
            h.succeed();
        });
    }

    /** Vanilla's spawner at the test patch: something of the family appears, nothing of another family. */
    private static void spawnHere(GameTestHelper h, ServerLevel level, ServerPlayer p, BlockPos start, Family f, long time) {
        clearFauna(level, p);
        List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
        List<FaunaMob> spawned = new ArrayList<>();
        FaunaRules.timeOverride = time;
        boolean night = time > 13000;
        try {
            h.assertTrue(level.getBiome(start).is(f.biome()), "the patch is " + f.biome().identifier());
            h.assertTrue(level.getNearestPlayer(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 128.0, false) == p, "the test player is the nearest");
            for (int i = 0; i < 160 && spawned.size() < 4; i++) {
                NaturalSpawner.spawnCategoryForPosition(MobTypes.FAUNA, level, start);
                for (FaunaMob m : FaunaCensus.in(level)) {
                    if (!before.contains(m) && !spawned.contains(m) && m.distanceToSqr(Vec3.atCenterOf(start)) < 48 * 48) spawned.add(m);
                }
            }
            boolean anyOfFamily = false;
            for (FaunaMob m : spawned) {
                MobDef d = m.def();
                boolean ok = d.theme == f.theme() || (f.theme() == Theme.BLOSSOM && d.id.equals("lumen_moth"));
                h.assertTrue(ok, d.id + " spawned in " + f.biome().identifier() + " (" + f.theme() + ")");
                anyOfFamily |= d.theme == f.theme();
                Habitat hab = Habitat.of(d.id);
                if (hab.window() != Habitat.Window.DARK) h.assertTrue(hab.window().contains(time), d.id + " keeps its time of day");
                h.assertTrue(d.fauna != FaunaRole.BEAST, "no beast this close or this low a level");
            }
            boolean nightLife = false;
            for (MobDef d : Bestiary.fauna()) {
                if (d.theme != f.theme() || d.fauna == FaunaRole.BEAST || d.fauna == FaunaRole.HUNTER) continue;
                if (Habitat.of(d.id).window().contains(18000)) nightLife = true;
            }
            if (!night || nightLife) h.assertTrue(anyOfFamily, f.theme() + " creatures spawn in " + f.biome().identifier() + (night ? " by night" : " by day"));
            for (FaunaRole r : FaunaRole.values()) {
                h.assertTrue(FaunaCensus.countNear(level, r, p.getX(), p.getZ(), 128) <= r.capPerPlayer, r + " stays under its cap");
            }
        } finally {
            FaunaRules.timeOverride = null;
            spawned.forEach(FaunaMob::discard);
        }
    }

    /** Hundreds of spawn attempts at every family's patch around one player: no role ever passes its cap. */
    private static void hammer(GameTestHelper h, ServerLevel level, ServerPlayer p, List<BlockPos> patches, List<FaunaMob> made, long time) {
        FaunaRules.timeOverride = time;
        try {
            List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
            for (int round = 0; round < 60; round++) {
                for (BlockPos c : patches) NaturalSpawner.spawnCategoryForPosition(MobTypes.FAUNA, level, c);
                for (FaunaRole r : FaunaRole.values()) {
                    int n = FaunaCensus.countNear(level, r, p.getX(), p.getZ(), 128);
                    h.assertTrue(n <= r.capPerPlayer, r + " under its cap with every family around: " + n + " > " + r.capPerPlayer);
                }
            }
            Set<Theme> seen = EnumSet.noneOf(Theme.class);
            for (FaunaMob m : FaunaCensus.in(level)) {
                if (before.contains(m)) continue;
                made.add(m);
                seen.add(m.def().theme);
            }
            if (time < 12000) h.assertTrue(seen.size() >= 3, "several families came out: " + seen);
        } finally {
            FaunaRules.timeOverride = null;
        }
    }

    /** Air in a box (tests that build put the shared test spot back as they found it). */
    static void wipe(ServerLevel level, BlockPos a, BlockPos b) {
        for (BlockPos q : BlockPos.betweenClosed(a, b)) {
            if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static void clearFauna(ServerLevel level, ServerPlayer p) {
        for (FaunaMob m : List.copyOf(FaunaCensus.in(level))) if (m.distanceToSqr(p) < 200 * 200) m.discard();
    }
}
