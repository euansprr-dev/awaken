package dev.awaken.world.fauna;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.FaunaTalent;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.CaveFauna;
import dev.awaken.mobs.entity.DeepBeast;
import dev.awaken.mobs.entity.Fauna3Kinds;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import dev.awaken.mobs.entity.WaterFauna;
import dev.awaken.mobs.entity.WildMount;
import dev.awaken.mobs.entity.WildPose;
import dev.awaken.progression.Progression;
import dev.awaken.world.fauna.lair.LairBuilder;
import dev.awaken.world.fauna.lair.LairPiece;
import dev.awaken.world.fauna.lair.LairStyle;
import dev.awaken.world.fauna.lair.LairWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * P3's GameTests: the Tidewater and the Hollows in the bestiary, their biome modifiers and their own water category,
 * natural spawning in water and in caves by day and by night (the real spawner, the light rule underground), the water
 * caps, the lairs (the sea floor and the caves: placement on a real overworld, both blueprints built, a beast waiting at
 * the shrine), and every talent (the school's shimmer, the bell's air, the otter's fetch, the cricket's ore, the
 * wickling's berries, the lurker's roof, the hushling's ears).
 */
final class Fauna3Tests {
    private Fauna3Tests() {}

    static final int UP = FaunaTests.UP;
    static final List<String> P3 = List.of("glimmerfin", "breathbell", "brook_otter", "tidesteed", "lantern_maw",
            "chime_cricket", "wickling", "drip_lurker", "hushling", "thousandfoot");
    static final List<String> MODIFIERS = List.of("fauna_tidewater_rivers", "fauna_tidewater_lakes", "fauna_tidewater_ocean", "fauna_tidewater_deep",
            "fauna_hollows_dripstone", "fauna_hollows_lush", "fauna_hollows_deep_dark");

    static void register() {
        // Diagnostic, only with -PmotionDiag: records 800 ticks of a Tidesteed, a Dune Ray and vanilla controls (a dolphin,
        // a camel) to run/motion_diag.csv, to judge how smoothly they move (speed jerk, body turn, pitch and surface bob).
        // Keep the creatures near the platform's middle: past the test's loaded chunks they stop ticking.
        if (Boolean.getBoolean("awaken.motionDiag")) AwakenTests.registerIsolated("fauna_motion_diag", 900, h -> {
            ServerLevel level = h.getLevel();
            BlockPos tank = h.absolutePos(new BlockPos(0, UP + 2, 0));
            Fauna3Commands.buildTank(level, tank, 8, 5, 8);
            BlockPos sand = h.absolutePos(new BlockPos(0, UP, 22));
            for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
                level.setBlock(sand.offset(x, -1, z), Blocks.SANDSTONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(sand.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
            List<AwakenMob> made = new ArrayList<>();
            List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
            all.add(spawn(level, "tidesteed", Vec3.atCenterOf(tank.above(2)), made));
            all.add(spawn(level, "dune_ray", Vec3.atBottomCenterOf(sand), made));
            var dolphin = net.minecraft.world.entity.EntityTypes.DOLPHIN.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            dolphin.snapTo(tank.getX() + 2.5, tank.getY() + 2, tank.getZ() + 2.5);
            dolphin.setPersistenceRequired();
            level.addFreshEntity(dolphin);
            all.add(dolphin);
            var camel = net.minecraft.world.entity.EntityTypes.CAMEL.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            camel.snapTo(sand.getX() + 3.5, sand.getY(), sand.getZ() + 3.5);
            camel.setPersistenceRequired();
            level.addFreshEntity(camel);
            all.add(camel);
            StringBuilder csv = new StringBuilder("tick,who,x,y,z,body,yrot,xrot,dx,dy,dz,ground,water,extra\n");
            int[] t = {0};
            h.onEachTick(() -> {
                t[0]++;
                for (net.minecraft.world.entity.Mob m : all) {
                    if (m == null) continue;
                    Vec3 v = m.getDeltaMovement();
                    csv.append(String.format(java.util.Locale.ROOT, "%d,%s,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.4f,%.4f,%.4f,%b,%b%n", t[0],
                            net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath(), m.getX(), m.getY(), m.getZ(),
                            m.yBodyRot, m.getYRot(), m.getXRot(), v.x, v.y, v.z, m.onGround(), m.isInWater()));
                    if (m instanceof AwakenMob am && am.def().id.equals("dune_ray")) {
                        csv.setLength(csv.length() - 1);
                        StringBuilder goals = new StringBuilder();
                        for (var w : am.goalSelector.getAvailableGoals()) if (w.isRunning()) goals.append(w.getGoal().getClass().getSimpleName()).append('+');
                        csv.append(String.format(java.util.Locale.ROOT, ",want=%b %.1f %.1f %.1f goals=%s%n", am.getMoveControl().hasWanted(),
                                am.getMoveControl().getWantedX() - am.getX(), am.getMoveControl().getWantedY() - am.getY(), am.getMoveControl().getWantedZ() - am.getZ(), goals));
                    }
                }
            });
            h.runAfterDelay(800, () -> {
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of("motion_diag.csv"), csv.toString());
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                for (net.minecraft.world.entity.Mob m : all) if (m != null) m.discard();
                h.succeed();
            });
        });

        AwakenTests.register("fauna_p3_bestiary", 20, h -> {
            List<String> problems = new ArrayList<>();
            Map<Theme, Set<FaunaRole>> families = new EnumMap<>(Theme.class);
            for (String id : P3) {
                MobDef d = Bestiary.get(id);
                if (d == null) {
                    problems.add(id + " missing");
                    continue;
                }
                families.computeIfAbsent(d.theme, k -> EnumSet.noneOf(FaunaRole.class)).add(d.fauna);
                Habitat hab = Habitat.of(id);
                EntityType<? extends AwakenMob> type = MobTypes.type(d);
                if (type == null) {
                    problems.add(id + " not registered");
                    continue;
                }
                boolean water = hab.water();
                if (water != Fauna3Kinds.WATER.contains(id)) problems.add(id + ": water habitat and water class disagree");
                if (type.getCategory() != (water ? Fauna3Kinds.FAUNA_WATER : MobTypes.FAUNA)) problems.add(id + ": category " + type.getCategory());
                if (SpawnPlacements.getPlacementType(type) != (water ? SpawnPlacementTypes.IN_WATER : SpawnPlacementTypes.ON_GROUND)) problems.add(id + ": placement");
                if (!Habitat.ALL.containsKey(id) || hab.hint().isEmpty() || hab.hint().length() > 40) problems.add(id + ": hint '" + hab.hint() + "'");
                if (!water && hab.ground() != Habitat.Ground.CAVE) problems.add(id + ": a cave creature must live on cave floors");
                if (d.talent == FaunaTalent.NONE) problems.add(id + ": no talent");
                if (d.fauna == FaunaRole.BEAST && (LairStyle.byBeast(id) == null || hab.minPlayerLevel() != 3)) problems.add(id + ": a beast needs a lair, from level 3");
                if (!d.model.hero() || !d.model.wild()) problems.add(id + ": not on a P3 body");
                HeroRig rig = HeroRig.get(id);
                if (rig == null) {
                    problems.add(id + ": no rig");
                    continue;
                }
                problems.addAll(rig.problems());
                if (water && rig.clip("fly") == null) problems.add(id + ": a swimmer needs its swim loop (fly)");
                if (!water && rig.clip("walk") == null) problems.add(id + ": a cave creature walks");
                if (water && !Fauna3Kinds.AMPHIBIOUS.contains(id) && rig.clip("beached") == null) problems.add(id + ": a fish out of water flops (beached)");
                String hold = switch (id) {
                    case "drip_lurker" -> "hang";
                    case "breathbell" -> "deflate";
                    case "brook_otter" -> "carry";
                    case "chime_cricket" -> "chirp";
                    case "wickling" -> "ripen";
                    case "hushling" -> "listen";
                    case "tidesteed" -> "ridden";
                    default -> null;
                };
                if (hold != null && rig.clip(hold) == null) problems.add(id + ": no " + hold + " clip");
                // the class each one needs
                var e = type.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                boolean ok = switch (id) {
                    case "lantern_maw" -> e instanceof DeepBeast;
                    case "thousandfoot" -> e instanceof NamedBeast && !(e instanceof DeepBeast);
                    default -> water ? e instanceof WaterFauna : e instanceof CaveFauna;
                };
                if (!ok) problems.add(id + ": wrong entity class " + (e == null ? null : e.getClass().getSimpleName()));
                if (e != null) e.discard();
                if (h.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Awaken.id("entities/" + id)))
                        == net.minecraft.world.level.storage.loot.LootTable.EMPTY) problems.add(id + ": no loot table");
            }
            for (Theme t : List.of(Theme.TIDEWATER, Theme.HOLLOWS)) {
                Set<FaunaRole> r = families.get(t);
                if (r == null || !r.contains(FaunaRole.AMBIENT) || !r.contains(FaunaRole.BEAST)) problems.add(t + " needs ambient creatures and a beast: " + r);
            }
            if (!families.getOrDefault(Theme.HOLLOWS, Set.of()).contains(FaunaRole.HUNTER)) problems.add("the Hollows need night hunters");
            for (String m : List.of("tide_scale", "tide_pelt", "tide_gel", "hollow_crystal", "hollow_chitin", "hollow_wick", "hunters_bridle", "hunters_whistle",
                    "maw_lantern", "thousandfoot_mandible")) {
                if (!BuiltInRegistries.ITEM.containsKey(Awaken.id(m))) problems.add("item " + m + " missing");
            }
            for (String mount : List.of("frost_stag", "dune_ray", "tidesteed")) {
                var e = MobTypes.type(mount).create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                if (!(e instanceof WildMount m) || m.kind() == null) problems.add(mount + " is not a mount");
                if (e != null) e.discard();
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        AwakenTests.register("fauna_p3_biome_modifiers", 20, h -> {
            var access = h.getLevel().registryAccess();
            var mods = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
            for (String name : MODIFIERS) {
                Optional<Holder.Reference<BiomeModifier>> m = mods.get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Awaken.id(name)));
                h.assertTrue(m.isPresent(), "biome modifier awaken:" + name + " decoded");
            }
            var biomes = access.lookupOrThrow(Registries.BIOME);
            assertSpawns(h, biomes.getOrThrow(Biomes.RIVER), Fauna3Kinds.FAUNA_WATER, "glimmerfin", "brook_otter");
            assertSpawns(h, biomes.getOrThrow(Biomes.OCEAN), Fauna3Kinds.FAUNA_WATER, "breathbell", "tidesteed");
            assertSpawns(h, biomes.getOrThrow(Biomes.DEEP_OCEAN), Fauna3Kinds.FAUNA_WATER, "breathbell", "tidesteed", "lantern_maw");
            assertSpawns(h, biomes.getOrThrow(Biomes.PLAINS), Fauna3Kinds.FAUNA_WATER, "glimmerfin");
            assertSpawns(h, biomes.getOrThrow(Biomes.DRIPSTONE_CAVES), MobTypes.FAUNA, "chime_cricket", "drip_lurker", "thousandfoot");
            assertSpawns(h, biomes.getOrThrow(Biomes.LUSH_CAVES), MobTypes.FAUNA, "wickling", "chime_cricket");
            assertSpawns(h, biomes.getOrThrow(Biomes.DEEP_DARK), MobTypes.FAUNA, "hushling");
            // never in vanilla's categories: the cod, the squid and the dolphins keep every slot they had
            for (var b : List.of(Biomes.RIVER, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.WARM_OCEAN, Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES)) {
                for (MobCategory cat : List.of(MobCategory.WATER_AMBIENT, MobCategory.WATER_CREATURE, MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS,
                        MobCategory.MONSTER, MobCategory.CREATURE)) {
                    for (Weighted<MobSpawnSettings.SpawnerData> w : spawns(biomes.getOrThrow(b), cat)) {
                        h.assertTrue(MobTypes.def(w.value().type()) == null, b.identifier() + " " + cat + " holds an AWAKEN creature");
                    }
                }
            }
            MobCategory c = Fauna3Kinds.FAUNA_WATER;
            h.assertTrue(c.name().equals("AWAKEN_FAUNA_WATER") && c.getSerializedName().equals("awaken:fauna_water"), "the awaken:fauna_water category exists");
            h.assertTrue(c.getMaxInstancesPerChunk() == 10 && c.isFriendly() && !c.isPersistent() && c.getDespawnDistance() == 64, "10 per player, friendly, like fish");
            h.succeed();
        });

        AwakenTests.register("fauna_p3_water_rules", 20, h -> {
            Habitat fish = Habitat.of("glimmerfin"), otter = Habitat.of("brook_otter"), maw = Habitat.of("lantern_maw");
            h.assertTrue(fish.water() && otter.water() && maw.water() && !Habitat.of("wickling").water(), "water habitats");
            int amb = FaunaRules.waterCap(FaunaRole.AMBIENT), neu = FaunaRules.waterCap(FaunaRole.NEUTRAL);
            h.assertTrue(FaunaRules.allowed(FaunaRole.AMBIENT, fish, true, 1, false, amb - 1, 0.9F) && !FaunaRules.allowed(FaunaRole.AMBIENT, fish, true, 1, false, amb, 0.0F),
                    "water ambient cap " + amb);
            h.assertTrue(FaunaRules.allowed(FaunaRole.NEUTRAL, otter, true, 1, false, neu - 1, 0.0F) && !FaunaRules.allowed(FaunaRole.NEUTRAL, otter, true, 1, false, neu, 0.0F),
                    "water neutral cap " + neu);
            h.assertTrue(amb + neu <= Fauna3Kinds.FAUNA_WATER.getMaxInstancesPerChunk(), "the water caps fit inside their category's 10");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.NEUTRAL, otter, true, 5, true, 0, 0.0F), "nothing but ambient inside a Hearth radius, in water too");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.BEAST, maw, true, 2, false, 0, 0.0F) && FaunaRules.allowed(FaunaRole.BEAST, maw, true, 3, false, 0, 0.0F), "the Maw from level 3");
            h.assertTrue(FaunaRules.allowed(FaunaRole.AMBIENT, fish, false, 1, false, 0, 0.1F) && !FaunaRules.allowed(FaunaRole.NEUTRAL, otter, false, 1, false, 0, 0.0F),
                    "before awakening only ambient hints, in water too");
            h.assertTrue(Habitat.Ground.CAVE.accepts(Blocks.DEEPSLATE.defaultBlockState()) && Habitat.Ground.CAVE.accepts(Blocks.DRIPSTONE_BLOCK.defaultBlockState())
                    && !Habitat.Ground.CAVE.accepts(Blocks.OAK_PLANKS.defaultBlockState()), "cave floors, never your floors");
            h.succeed();
        });

        // The real spawner in water: a river by day (fish and otters) and by night (fish, no otters: they sleep), the sea
        // (jellies and steeds), every one of them in water, none above its own caps, nothing in a land category.
        AwakenTests.registerIsolated("fauna_p3_water_spawning", 900, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 2, 34));
            FaunaTests.awaken(p);
            BlockPos centre = h.absolutePos(new BlockPos(0, UP, 0));
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other != p && other.distanceToSqr(Vec3.atCenterOf(centre)) < 200 * 200) other.setPos(other.getX() + 2000, other.getY(), other.getZ());
            }
            Fauna3Commands.buildTank(level, centre, 8, 5, 8);
            List<FaunaMob> made = new ArrayList<>();
            var seq = h.startSequence();
            for (var biome : List.of(Biomes.RIVER, Biomes.OCEAN)) {
                seq.thenExecute(() -> fillBiome(level, cmd, centre, 10, biome));
                seq.thenIdle(5);
                seq.thenExecute(() -> spawnWater(h, level, p, centre.above(3), biome, 6000L, made));
                seq.thenExecute(() -> spawnWater(h, level, p, centre.above(3), biome, 18000L, made));
            }
            seq.thenExecute(() -> {
                made.forEach(FaunaMob::discard);
                WildTests.wipe(level, centre.offset(-10, -1, -10), centre.offset(10, 8, 10));
                level.getServer().getPlayerList().remove(p);
            });
            seq.thenSucceed();
        });

        // Caves: a sealed, dark grotto in each cave biome; the Hollows come out by day and by night (a cave has no day),
        // the hunters only in the dark: one torch and they stop spawning while the cricket still comes.
        AwakenTests.registerIsolated("fauna_p3_caves_spawn", 1200, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 2, 34));
            FaunaTests.awaken(p);
            BlockPos centre = h.absolutePos(new BlockPos(0, UP, 0));
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other != p && other.distanceToSqr(Vec3.atCenterOf(centre)) < 200 * 200) other.setPos(other.getX() + 2000, other.getY(), other.getZ());
            }
            Fauna3Commands.buildGrotto(level, centre, 7, 5, 7);
            List<FaunaMob> made = new ArrayList<>();
            var seq = h.startSequence();
            seq.thenIdle(30); // the light engine darkens the sealed room
            for (var biome : List.of(Biomes.DRIPSTONE_CAVES, Biomes.LUSH_CAVES, Biomes.DEEP_DARK)) {
                seq.thenExecute(() -> fillBiome(level, cmd, centre, 9, biome));
                seq.thenIdle(5);
                seq.thenExecute(() -> spawnCave(h, level, p, centre.above(1), biome, 6000L, made, true));
                seq.thenExecute(() -> spawnCave(h, level, p, centre.above(1), biome, 18000L, made, true));
            }
            // the light rule: a torch on the wall, and no hunter spawns (the ambient cricket still does)
            seq.thenExecute(() -> {
                fillBiome(level, cmd, centre, 9, Biomes.DRIPSTONE_CAVES);
                level.setBlock(centre.offset(0, 1, 0), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
            });
            seq.thenIdle(30);
            seq.thenExecute(() -> spawnCave(h, level, p, centre.offset(2, 1, 0), Biomes.DRIPSTONE_CAVES, 18000L, made, false));
            seq.thenExecute(() -> {
                made.forEach(FaunaMob::discard);
                WildTests.wipe(level, centre.offset(-9, -2, -9), centre.offset(9, 8, 9));
                level.getServer().getPlayerList().remove(p);
            });
            seq.thenSucceed();
        });

        // Lairs on a real overworld (the noise generator and the biome source of a fixed seed): the Sunken Shrine only on
        // the floor of deep oceans under deep water, the Crystal Nest only deep underground in its cave biomes.
        AwakenTests.register("fauna_p3_lair_placement", 300, h -> {
            ServerLevel level = h.getLevel();
            var access = level.registryAccess();
            Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
            var preset = access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromPreset(preset);
            NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(source, settings);
            long seed = 20261024L;
            RandomState random = RandomState.create(access.lookupOrThrow(Registries.NOISE), seed, settings.value());
            var sampler = random.createClimateSampler(SamplerContext.builder().enableCaches().build());
            var resolver = source.createResolver(sampler);
            var structures = access.lookupOrThrow(Registries.STRUCTURE);
            var sets = access.lookupOrThrow(Registries.STRUCTURE_SET);
            Map<LairStyle, Integer> found = new EnumMap<>(LairStyle.class);
            int candidates = 0;
            for (LairStyle style : List.of(LairStyle.SUNKEN_SHRINE, LairStyle.CRYSTAL_NEST)) {
                Structure structure = structures.getOrThrow(LairWorldgen.structure(style.beast)).value();
                var spread = (RandomSpreadStructurePlacement) sets.getOrThrow(LairWorldgen.set(style.beast)).value().placement();
                h.assertTrue(spread.spacing() * 16 >= 1500 && spread.spacing() * 16 <= 2500, style + " is rare: one per " + spread.spacing() * 16 + " blocks");
                for (int cx = 0; cx < 7; cx++) {
                    for (int cz = 0; cz < 7; cz++) {
                        ChunkPos chunk = spread.getPotentialStructureChunk(seed, cx * spread.spacing(), cz * spread.spacing());
                        candidates++;
                        var ctx = new Structure.GenerationContext(access, gen, source, sampler, random, level.getServer().getStructureTemplateManager(), seed, chunk,
                                level, structure.biomes()::contains);
                        Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(ctx);
                        if (stub.isEmpty()) continue;
                        found.merge(style, 1, Integer::sum);
                        BlockPos at = stub.get().position();
                        Holder<Biome> b = resolver.getNoiseBiome(QuartPos.fromBlock(at.getX()), QuartPos.fromBlock(style == LairStyle.CRYSTAL_NEST ? at.getY() + 3 : at.getY()),
                                QuartPos.fromBlock(at.getZ()));
                        h.assertTrue(structure.biomes().contains(b), style + " lair in " + b.getRegisteredName());
                        int top = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        if (style == LairStyle.SUNKEN_SHRINE) {
                            int floor = gen.getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                            h.assertTrue(top - floor >= 10, "the shrine lies under deep water (" + (top - floor) + ") at " + at.toShortString());
                            h.assertTrue(Math.abs(at.getY() - floor) <= style.maxSlope, "on the sea floor");
                        } else {
                            h.assertTrue(top - at.getY() >= 24, "the nest is deep underground (" + (top - at.getY()) + " under the surface)");
                        }
                    }
                }
            }
            int total = found.values().stream().mapToInt(Integer::intValue).sum();
            Awaken.LOG.info("[fauna] P3 lair placement: {} of {} candidate chunks: {}", total, candidates, found);
            h.assertTrue(total >= 1, "P3 lairs exist on a real overworld (" + total + " of " + candidates + "): " + found);
            h.assertTrue(total < candidates * 3 / 4, "and are not everywhere: " + total + " of " + candidates);
            h.succeed();
        });

        // Both blueprints built where they belong (the shrine in a tank of sea water, the nest in solid rock); the dens are
        // open room; the Lantern Maw waits at its shrine in the water. Writes run/fauna_lairs/*.json for the previews.
        AwakenTests.registerIsolated("fauna_p3_lairs_build", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos centre = h.absolutePos(new BlockPos(0, UP + 2, 0));
            BlockPos lo = centre.offset(-LairStyle.EXTENT - 2, -LairStyle.DEPTH - 1, -LairStyle.EXTENT - 2);
            BlockPos hi = centre.offset(LairStyle.EXTENT + 2, LairStyle.HEIGHT + 2, LairStyle.EXTENT + 2);
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 20, 12));
            List<AwakenMob> made = new ArrayList<>();
            h.startSequence()
                    .thenExecute(() -> {
                        // the sea: a tank of water with a sand floor, the shrine on it
                        Fauna3Commands.buildTank(level, centre.below(), LairStyle.EXTENT, LairStyle.HEIGHT, LairStyle.EXTENT);
                        LairPiece shrine = new LairPiece(LairStyle.SUNKEN_SHRINE, centre, 5L, 0);
                        shrine.build(level, shrine.getBoundingBox());
                        BlockPos den = shrine.den();
                        h.assertTrue(LairBuilder.openRoom(level, den), "the shrine's den is open water over its dais");
                        h.assertTrue(level.getFluidState(den).is(net.minecraft.tags.FluidTags.WATER), "the den is under water");
                        int lanterns = count(level, centre, 10, Blocks.SEA_LANTERN), bricks = count(level, centre, 10, Blocks.PRISMARINE_BRICKS);
                        h.assertTrue(lanterns >= 4 && bricks >= 30, "a shrine of prismarine lit by sea lanterns: " + lanterns + " lanterns, " + bricks + " bricks");
                        WildTests.dump(level, LairStyle.SUNKEN_SHRINE, 0, lo, hi, centre);
                        // its beast waits there, in the water
                        FaunaTests.awaken(p);
                        while (Progression.profile(p).level() < 3) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
                        MobDef d = Bestiary.get("lantern_maw");
                        AwakenMob maw = Mobs.spawn(level, d, Lairs.standing(level, d, den), 3, new MobRole(d.tier.role, 3, d.theme.id, Optional.empty(), ""),
                                EnumSet.noneOf(Forces.Tag.class));
                        h.assertTrue(maw instanceof DeepBeast, "the Lantern Maw is a deep beast");
                        made.add(maw);
                        h.assertTrue(level.getFluidState(maw.blockPosition()).is(net.minecraft.tags.FluidTags.WATER), "it hangs in the water at its den");
                    })
                    .thenIdle(40)
                    .thenExecute(() -> {
                        AwakenMob maw = made.get(0);
                        h.assertTrue(maw.isAlive() && maw.isInWater() && maw.getHealth() >= maw.getMaxHealth() - 0.01F, "it lives in the water (no drowning, no flopping)");
                        h.assertTrue((((DeepBeast) maw).wildState() & WildPose.S_SWIM) != 0, "it swims");
                        made.forEach(AwakenMob::discard);
                        made.clear();
                        WildTests.wipe(level, lo, hi);
                        // the rock: a block of stone, the nest carved out of it
                        for (BlockPos q : BlockPos.betweenClosed(centre.offset(-12, -3, -12), centre.offset(12, 10, 12))) {
                            level.setBlock(q, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                        LairPiece nest = new LairPiece(LairStyle.CRYSTAL_NEST, centre, 9L, 1);
                        nest.build(level, nest.getBoundingBox());
                        BlockPos den = nest.den();
                        h.assertTrue(LairBuilder.standingRoom(level, den), "the nest's den is standing room");
                        for (int dy = 0; dy < 4; dy++) h.assertTrue(level.getBlockState(den.above(dy)).getCollisionShape(level, den.above(dy)).isEmpty(), "headroom over the den");
                        h.assertTrue(level.getBlockState(centre.above(4)).isAir() && level.getBlockState(centre.offset(5, 2, 0)).isAir(), "a chamber carved in the rock");
                        int amethyst = count(level, centre, 10, Blocks.AMETHYST_CLUSTER) + count(level, centre, 10, Blocks.LARGE_AMETHYST_BUD)
                                + count(level, centre, 10, Blocks.SMALL_AMETHYST_BUD);
                        h.assertTrue(amethyst >= 6, "amethyst grows in the nest: " + amethyst);
                        h.assertTrue(level.getBlockState(centre.offset(LairStyle.EXTENT, 2, 0)).isAir(), "a tunnel leads out");
                        WildTests.dump(level, LairStyle.CRYSTAL_NEST, 1, lo, hi, centre);
                        WildTests.wipe(level, centre.offset(-12, -6, -12), centre.offset(12, 14, 12));
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });

        // Every talent, called where it works: the shimmer, the bell, the fetch, the cricket's ore, the wickling's vine,
        // the lurker's roof and its light rule, the hushling's ears.
        AwakenTests.registerIsolated("fauna_p3_talents", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos centre = h.absolutePos(new BlockPos(0, UP + 2, 0));
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP + 4, 0));
            FaunaTests.awaken(p);
            List<AwakenMob> made = new ArrayList<>();
            Fauna3Commands.buildTank(level, centre, 6, 5, 6);
            WaterFauna[] w = new WaterFauna[3];
            h.startSequence()
                    .thenExecute(() -> {
                        p.teleportTo(level, centre.getX() + 0.5, centre.getY() + 2, centre.getZ() + 0.5, Set.of(), 0, 0, true);
                        w[0] = (WaterFauna) spawn(level, "glimmerfin", Vec3.atCenterOf(centre.above(2)).add(1, 0, 0), made);
                        w[1] = (WaterFauna) spawn(level, "breathbell", Vec3.atCenterOf(centre.above(2)).add(0.6, 0, 0), made);
                        w[2] = (WaterFauna) spawn(level, "brook_otter", Vec3.atCenterOf(centre.above(2)).add(-2, 0, 2), made);
                        h.assertTrue(w[0] != null && w[1] != null && w[2] != null, "the three swimmers spawned");
                    })
                    .thenIdle(3) // their in-water state is worked out on their first ticks
                    .thenExecute(() -> {
                        WaterFauna fish = w[0], bell = w[1];
                        // the school's shimmer: a swimmer among them can see in the dark water
                        p.teleportTo(level, centre.getX() + 0.5, centre.getY() + 2, centre.getZ() + 0.5, Set.of(), 0, 0, true);
                        fish.teleportTo(p.getX() + 1, p.getY(), p.getZ());
                        // a test player has no client to tick it: two ticks of its own work out that it is in the water
                        // (the first finds the water, the second puts its eyes under it)
                        p.doTick();
                        p.doTick();
                        h.assertTrue(p.isInWater(), "the player swims");
                        h.assertTrue(fish.isInWater() && (fish.wildState() & WildPose.S_SWIM) != 0, "the fish swims");
                        p.removeEffect(MobEffects.NIGHT_VISION);
                        h.assertTrue(testShimmer(fish, level) && p.hasEffect(MobEffects.NIGHT_VISION), "swimming through a school opens your eyes");
                        // the bell's air: a drowning swimmer touching it breathes again; then the bell is empty for a while
                        p.setAirSupply(10);
                        bell.teleportTo(p.getX() + 0.6, p.getY(), p.getZ());
                        h.assertTrue(p.isUnderWater(), "the player is under water");
                        h.assertTrue(testAirbell(bell, level), "the bell gives its air");
                        h.assertTrue(p.getAirSupply() >= 10 + WaterFauna.BELL_AIR - 1 && bell.bellEmpty(), "air came back and the bell is empty");
                        p.setAirSupply(10);
                        h.assertTrue(!testAirbell(bell, level), "an empty bell gives nothing until it refills");
                        // the otter's fetch: it dives, rummages, and brings its find to the player
                        h.assertTrue(w[2].startFetchForTest(level), "the otter dives for something");
                    })
                    .thenWaitUntil(() -> {
                        w[2].fetchStepForTest();
                        h.assertTrue(w[2].fetchStage() == 3 && !w[2].carried().isEmpty(), "it rummages and comes up with a find");
                    })
                    .thenExecute(() -> {
                        WaterFauna otter = w[2];
                        h.assertTrue((otter.wildState() & WildPose.S_CARRY) != 0, "it carries the find in its mouth");
                        otter.teleportTo(p.getX() + 1, p.getY(), p.getZ());
                        otter.fetchStepForTest();
                        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4));
                        h.assertTrue(!items.isEmpty() && otter.fetchStage() == 0, "and drops it at the player's feet");
                        items.forEach(ItemEntity::discard);
                        made.forEach(AwakenMob::discard);
                        made.clear();
                        WildTests.wipe(level, centre.offset(-8, -1, -8), centre.offset(8, 8, 8));
                        // the cave: a dark grotto with ore, vines and a roof
                        Fauna3Commands.buildGrotto(level, centre, 6, 5, 5);
                        p.teleportTo(level, centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5, Set.of(), 0, 0, true);
                    })
                    .thenIdle(30)
                    .thenExecute(() -> {
                        // the cricket finds the ore through the rock and sings beside it
                        CaveFauna cricket = (CaveFauna) spawn(level, "chime_cricket", Vec3.atBottomCenterOf(centre.offset(4, 1, 3)), made);
                        h.assertTrue(cricket != null, "the cricket spawned");
                        for (int i = 0; i < 40 && cricket.singingFor() == null; i++) cricket.seekOreForTest(level);
                        BlockPos ore = cricket.singingFor();
                        h.assertTrue(ore != null && level.getBlockState(ore).is(net.neoforged.neoforge.common.Tags.Blocks.ORES), "the cricket knows where the ore is: " + ore);
                        // the wickling ripens the vine it stands under
                        CaveFauna wick = (CaveFauna) spawn(level, "wickling", Vec3.atBottomCenterOf(centre.offset(-2, 1, -3)), made);
                        h.assertTrue(wick != null, "the wick spawned");
                        BlockPos vine = centre.offset(-2, 4, -3);
                        h.assertTrue(level.getBlockState(vine).getBlock() instanceof CaveVines, "a vine hangs there");
                        h.assertTrue(wick.ripen(level, vine) >= 1 && CaveVines.hasGlowBerries(level.getBlockState(vine)), "glow berries ripen on it");
                        // the drip lurker clings to the roof; in the dark it drops on whoever passes under it
                        CaveFauna lurker = (CaveFauna) spawn(level, "drip_lurker", Vec3.atBottomCenterOf(centre.offset(3, 1, -2)), made);
                        h.assertTrue(lurker != null, "the lurker spawned");
                        int roof = lurker.ceilingAboveForTest();
                        h.assertTrue(roof == centre.getY() + 6, "it finds the roof over it: " + roof);
                        lurker.hang(roof);
                        h.assertTrue(lurker.hanging() && lurker.isNoGravity() && (lurker.wildState() & WildPose.S_HANG) != 0, "it hangs");
                        h.assertTrue(FaunaMob.inDarkness(p), "the grotto is dark");
                        lurker.drop(p);
                        h.assertTrue(!lurker.hanging() && lurker.getTarget() == p, "it drops on you in the dark");
                        // the hushling hears a running player in the dark, and not one who sneaks
                        CaveFauna hush = (CaveFauna) spawn(level, "hushling", Vec3.atBottomCenterOf(centre.offset(-3, 1, 2)), made);
                        h.assertTrue(hush != null, "the hush spawned");
                        h.assertTrue(hush.def().has(dev.awaken.mobs.def.Trait.LISTENER), "a listener");
                    })
                    .thenExecute(() -> {
                        // the light rule: a torch at the player's feet and the dark is gone
                        level.setBlock(BlockPos.containing(p.position()), Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
                    })
                    .thenIdle(20)
                    .thenExecute(() -> {
                        h.assertTrue(!FaunaMob.inDarkness(p), "in torchlight the player is safe from the hunters (the light rule)");
                        made.forEach(AwakenMob::discard);
                        WildTests.wipe(level, centre.offset(-8, -2, -8), centre.offset(8, 8, 8));
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    static AwakenMob spawn(ServerLevel level, String id, Vec3 at, List<AwakenMob> made) {
        MobDef d = Bestiary.get(id);
        AwakenMob e = Mobs.spawn(level, d, at, 3, new MobRole(d.tier.role, 3, d.theme.id, Optional.empty(), ""), EnumSet.noneOf(Forces.Tag.class));
        if (e != null) {
            e.setPersistenceRequired();
            made.add(e);
        }
        return e;
    }

    static boolean testShimmer(WaterFauna fish, ServerLevel level) {
        return fish.shimmerForTest(level);
    }

    static boolean testAirbell(WaterFauna bell, ServerLevel level) {
        return bell.airbellForTest(level);
    }

    static int count(ServerLevel level, BlockPos centre, int r, Block block) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(centre.offset(-r, -2, -r), centre.offset(r, LairStyle.HEIGHT, r))) if (level.getBlockState(q).is(block)) n++;
        return n;
    }

    static List<Weighted<MobSpawnSettings.SpawnerData>> spawns(Holder<Biome> biome, MobCategory cat) {
        var e = biome.value().getAttributes().get(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        if (e == null || !(e.argument() instanceof MobSpawnSettings s)) return List.of();
        return s.getMobsToSpawn(cat).unwrap();
    }

    static void assertSpawns(net.minecraft.gametest.framework.GameTestHelper h, Holder<Biome> biome, MobCategory cat, String... ids) {
        Set<String> got = new HashSet<>();
        for (Weighted<MobSpawnSettings.SpawnerData> w : spawns(biome, cat)) {
            MobDef d = MobTypes.def(w.value().type());
            if (d != null) got.add(d.id);
        }
        for (String id : ids) h.assertTrue(got.contains(id), biome.getRegisteredName() + " " + cat.getSerializedName() + " spawns " + id + " (has " + got + ")");
    }

    static void fillBiome(ServerLevel level, net.minecraft.commands.CommandSourceStack cmd, BlockPos c, int r, ResourceKey<Biome> biome) {
        BlockPos a = c.offset(-r, -4, -r), b = c.offset(r, 10, r);
        level.getServer().getCommands().performPrefixedCommand(cmd, "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
                + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome.identifier());
    }

    /** The water spawner, a few hundred tries at the tank: what comes is the family's, in water, in its hours, under its caps. */
    private static void spawnWater(net.minecraft.gametest.framework.GameTestHelper h, ServerLevel level, ServerPlayer p, BlockPos start, ResourceKey<Biome> biome,
                                   long time, List<FaunaMob> made) {
        FaunaRules.timeOverride = time;
        boolean night = time > 13000;
        try {
            h.assertTrue(level.getBiome(start).is(biome), "the tank is " + biome.identifier());
            h.assertTrue(level.getFluidState(start).is(net.minecraft.tags.FluidTags.WATER), "the tank holds water");
            List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
            List<FaunaMob> spawned = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                NaturalSpawner.spawnCategoryForPosition(Fauna3Kinds.FAUNA_WATER, level, start);
                NaturalSpawner.spawnCategoryForPosition(MobTypes.FAUNA, level, start); // nothing of the land comes out of the tank
                for (FaunaMob m : FaunaCensus.in(level)) {
                    if (!before.contains(m) && !spawned.contains(m) && m.distanceToSqr(Vec3.atCenterOf(start)) < 24 * 24) spawned.add(m);
                }
                for (FaunaRole r : FaunaRole.values()) {
                    if (r == FaunaRole.BEAST) continue;
                    int n = FaunaCensus.countWater(level, r, p.getX(), p.getZ(), 128);
                    h.assertTrue(n <= FaunaRules.waterCap(r), r + " in water stays under its cap: " + n);
                }
            }
            Set<String> kinds = new HashSet<>();
            for (FaunaMob m : spawned) {
                MobDef d = m.def();
                kinds.add(d.id);
                h.assertTrue(d.theme == Theme.TIDEWATER && m.getType().getCategory() == Fauna3Kinds.FAUNA_WATER, d.id + " came out of the water");
                h.assertTrue(m.isInWater() || level.getFluidState(m.blockPosition()).is(net.minecraft.tags.FluidTags.WATER), d.id + " spawned in water");
                Habitat hab = Habitat.of(d.id);
                h.assertTrue(hab.window().contains(time), d.id + " keeps its hours");
                h.assertTrue(d.fauna != FaunaRole.BEAST, "no beast this close or this low a level");
            }
            if (biome == Biomes.RIVER) {
                h.assertTrue(kinds.contains("glimmerfin"), "glimmerfin school in the river " + (night ? "by night" : "by day") + ": " + kinds);
                if (night) h.assertTrue(!kinds.contains("brook_otter"), "otters sleep at night");
            } else {
                h.assertTrue(kinds.contains("breathbell") || kinds.contains("tidesteed"), "the sea's creatures " + (night ? "by night" : "by day") + ": " + kinds);
            }
            made.addAll(spawned);
            spawned.forEach(FaunaMob::discard);
        } finally {
            FaunaRules.timeOverride = null;
        }
    }

    private static void spawnCave(net.minecraft.gametest.framework.GameTestHelper h, ServerLevel level, ServerPlayer p, BlockPos start, ResourceKey<Biome> biome,
                                  long time, List<FaunaMob> made, boolean dark) {
        FaunaRules.timeOverride = time;
        try {
            h.assertTrue(level.getBiome(start).is(biome), "the grotto is " + biome.identifier());
            List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
            List<FaunaMob> spawned = new ArrayList<>();
            for (int i = 0; i < 300 && spawned.size() < 6; i++) {
                NaturalSpawner.spawnCategoryForPosition(MobTypes.FAUNA, level, start);
                for (FaunaMob m : FaunaCensus.in(level)) {
                    if (!before.contains(m) && !spawned.contains(m) && m.distanceToSqr(Vec3.atCenterOf(start)) < 16 * 16) spawned.add(m);
                }
            }
            Set<String> kinds = new HashSet<>();
            boolean hunter = false;
            for (FaunaMob m : spawned) {
                MobDef d = m.def();
                kinds.add(d.id);
                h.assertTrue(d.theme == Theme.HOLLOWS || d.id.equals("gloomstalker") || d.id.equals("wisp_hound"), d.id + " belongs in a cave");
                h.assertTrue(d.fauna != FaunaRole.BEAST, "no beast this close or this low a level");
                hunter |= d.fauna == FaunaRole.HUNTER;
            }
            String want = biome == Biomes.LUSH_CAVES ? "wickling" : biome == Biomes.DEEP_DARK ? "hushling" : "chime_cricket";
            if (dark) {
                h.assertTrue(!spawned.isEmpty(), "the Hollows come out in " + biome.identifier() + (time > 13000 ? " by night" : " by day"));
                if (!want.equals("hushling")) h.assertTrue(kinds.contains(want) || kinds.contains("chime_cricket"), want + " in " + biome.identifier() + ": " + kinds);
            } else {
                h.assertTrue(!hunter, "no hunter spawns in torchlight: " + kinds);
            }
            made.addAll(spawned);
            spawned.forEach(FaunaMob::discard);
        } finally {
            FaunaRules.timeOverride = null;
        }
    }

    @SuppressWarnings("unused")
    private static AABB box(BlockPos c, int r) {
        return new AABB(c).inflate(r);
    }
}
