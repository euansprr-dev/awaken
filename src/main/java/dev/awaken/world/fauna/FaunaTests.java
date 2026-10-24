package dev.awaken.world.fauna;

import com.mojang.authlib.GameProfile;
import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import dev.awaken.progression.Progression;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
 * GameTests for the Awakened layer's creatures: the category and the bestiary, the biome modifiers through the
 * registry, the spawn rules (time, light, awakening, Hearth, caps, beasts), natural spawning end to end, the light
 * rule for night hunters, first sightings, the reveal and the named beasts' drops.
 */
final class FaunaTests {
    private FaunaTests() {}

    /**
     * In-world tests work on a platform this high above the test origin: isolated batches reuse the same spot, and
     * blocks other tests leave there (gate structures, walls) would block sight lines or the open sky.
     */
    static final int UP = 65;

    static final List<String> MODIFIERS = List.of("fauna_greenwild", "fauna_greenwild_woods", "fauna_sunscorch", "fauna_frostwild",
            "fauna_frostwild_taiga", "fauna_caves", "fauna_mire", "fauna_canopy", "fauna_duskwood", "fauna_blossom", "fauna_blossom_meadow",
            "fauna_crag", "fauna_spore",
            "fauna_tidewater_rivers", "fauna_tidewater_lakes", "fauna_tidewater_ocean", "fauna_tidewater_deep",
            "fauna_hollows_dripstone", "fauna_hollows_lush", "fauna_hollows_deep_dark");

    static void register() {
        AwakenTests.register("fauna_category_and_bestiary", 20, h -> {
            MobCategory c = MobTypes.FAUNA;
            h.assertTrue(c != MobCategory.CREATURE && c.name().equals("AWAKEN_FAUNA"), "the awaken:fauna category exists (enum extension), got " + c);
            h.assertTrue(c.getSerializedName().equals("awaken:fauna") && c.getMaxInstancesPerChunk() == 16, "16 per player");
            h.assertTrue(c.isFriendly() && !c.isPersistent() && c.getDespawnDistance() == 128, "friendly, despawns like monsters");
            List<MobDef> fauna = Bestiary.fauna();
            h.assertTrue(fauna.size() == 44, "44 overworld creatures (12 from P1, 22 from P2, 10 from P3), have " + fauna.size());
            Map<FaunaRole, Integer> roles = new EnumMap<>(FaunaRole.class);
            Map<Theme, Set<FaunaRole>> families = new EnumMap<>(Theme.class);
            for (MobDef d : fauna) {
                roles.merge(d.fauna, 1, Integer::sum);
                families.computeIfAbsent(d.theme, k -> EnumSet.noneOf(FaunaRole.class)).add(d.fauna);
                EntityType<? extends AwakenMob> type = MobTypes.type(d);
                boolean water = Habitat.of(d.id).water();
                h.assertTrue(type != null && type.getCategory() == (water ? dev.awaken.mobs.entity.Fauna3Kinds.FAUNA_WATER : c), d.id + " is in its fauna category");
                h.assertTrue(type.isAllowedInPeaceful() == (d.fauna == FaunaRole.AMBIENT || d.fauna == FaunaRole.NEUTRAL), d.id + " peaceful rule");
                h.assertTrue(SpawnPlacements.getPlacementType(type) == (water ? SpawnPlacementTypes.IN_WATER : SpawnPlacementTypes.ON_GROUND), d.id + " has a spawn placement");
                h.assertTrue(Habitat.ALL.containsKey(d.id) && !Habitat.of(d.id).hint().isEmpty(), d.id + " has a habitat and a hint");
                h.assertTrue(d.talent != dev.awaken.mobs.def.FaunaTalent.NONE, d.id + " has a talent (a purpose)");
                h.assertTrue(d.theme.isWild(), d.id + " belongs to a biome family");
            }
            h.assertTrue(roles.get(FaunaRole.AMBIENT) == 17 && roles.get(FaunaRole.NEUTRAL) == 10 && roles.get(FaunaRole.HUNTER) == 6
                    && roles.get(FaunaRole.BEAST) == 11, "17 ambient, 10 neutral, 6 hunters, 11 beasts: " + roles);
            for (Theme t : List.of(Theme.GREENWILD, Theme.SUNSCORCH, Theme.FROSTWILD)) {
                Set<FaunaRole> r = families.get(t);
                h.assertTrue(r != null && r.contains(FaunaRole.AMBIENT) && r.contains(FaunaRole.NEUTRAL) && r.contains(FaunaRole.BEAST),
                        t + " has ambient, neutral and a named beast: " + r);
            }
            int caps = 0;
            for (FaunaRole r : FaunaRole.values()) caps += r.capPerPlayer;
            h.assertTrue(caps >= 16 && FaunaRole.HUNTER.capPerPlayer < 16 / 2, "role caps fit under the category cap");
            h.succeed();
        });

        AwakenTests.register("fauna_biome_modifiers_decode", 20, h -> {
            var access = h.getLevel().registryAccess();
            var mods = access.lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
            Set<EntityType<?>> listed = new HashSet<>();
            for (String name : MODIFIERS) {
                var key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Awaken.id(name));
                Optional<Holder.Reference<BiomeModifier>> m = mods.get(key);
                h.assertTrue(m.isPresent(), "biome modifier awaken:" + name + " decoded");
                h.assertTrue(m.get().value() instanceof BiomeModifiers.AddSpawnsBiomeModifier, name + " is add_spawns");
                var add = (BiomeModifiers.AddSpawnsBiomeModifier) m.get().value();
                h.assertTrue(add.biomes().size() > 0, name + " names biomes");
                for (Weighted<MobSpawnSettings.SpawnerData> w : add.spawners().unwrap()) {
                    EntityType<?> t = w.value().type();
                    MobDef d = MobTypes.def(t);
                    h.assertTrue(d != null && d.isFauna() && (t.getCategory() == MobTypes.FAUNA || t.getCategory() == dev.awaken.mobs.entity.Fauna3Kinds.FAUNA_WATER),
                            name + " spawns only fauna: " + BuiltInRegistries.ENTITY_TYPE.getKey(t));
                    listed.add(t);
                }
            }
            for (MobDef d : Bestiary.fauna()) h.assertTrue(listed.contains(MobTypes.type(d)), d.id + " spawns in some biome");
            var biomes = access.lookupOrThrow(Registries.BIOME);
            assertSpawns(h, biomes.getOrThrow(Biomes.PLAINS), "lumen_moth", "bloom_sprite", "mossback", "gloomstalker", "briar_sow");
            assertSpawns(h, biomes.getOrThrow(Biomes.DESERT), "glass_scarab", "dune_ray", "dune_wyrm");
            assertSpawns(h, biomes.getOrThrow(Biomes.SNOWY_TAIGA), "frostfinch", "frost_stag", "wisp_hound", "old_antler");
            assertSpawns(h, biomes.getOrThrow(Biomes.DRIPSTONE_CAVES), "gloomstalker", "wisp_hound");
            h.assertTrue(FaunaCommands.hasFamily(biomes.getOrThrow(Biomes.FOREST), Theme.GREENWILD) && !FaunaCommands.hasFamily(biomes.getOrThrow(Biomes.OCEAN), Theme.GREENWILD),
                    "families found from the live biome spawns");
            h.succeed();
        });

        AwakenTests.register("fauna_spawn_rules_matrix", 20, h -> {
            Habitat ambient = Habitat.of("lumen_moth"), neutral = Habitat.of("mossback"), hunter = Habitat.of("gloomstalker"), beast = Habitat.of("old_antler");
            // before awakening: only rare ambient hints, at most three
            h.assertTrue(FaunaRules.allowed(FaunaRole.AMBIENT, ambient, false, 1, false, 0, 0.1F), "a hint before awakening");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.AMBIENT, ambient, false, 1, false, 0, 0.9F), "hints are rare");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.AMBIENT, ambient, false, 1, false, FaunaRules.HINT_CAP, 0.0F), "at most three hints");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.NEUTRAL, neutral, false, 1, false, 0, 0.0F), "no neutral before awakening");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.HUNTER, hunter, false, 1, false, 0, 0.0F), "no hunters before awakening");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.BEAST, beast, false, 9, false, 0, 0.0F), "no beasts before awakening");
            // awakened
            h.assertTrue(FaunaRules.allowed(FaunaRole.AMBIENT, ambient, true, 1, true, 7, 0.99F), "ambient even at home");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.AMBIENT, ambient, true, 1, false, 8, 0.0F), "ambient cap 8");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.HUNTER, hunter, true, 5, true, 0, 0.0F), "no hunters inside a Hearth radius");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.NEUTRAL, neutral, true, 5, true, 0, 0.0F), "no neutral inside a Hearth radius");
            h.assertTrue(FaunaRules.allowed(FaunaRole.NEUTRAL, neutral, true, 5, false, 4, 0.0F) && !FaunaRules.allowed(FaunaRole.NEUTRAL, neutral, true, 5, false, 5, 0.0F), "neutral cap 5");
            h.assertTrue(FaunaRules.allowed(FaunaRole.HUNTER, hunter, true, 1, false, 4, 0.0F) && !FaunaRules.allowed(FaunaRole.HUNTER, hunter, true, 1, false, 5, 0.0F), "hunter cap 5");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.BEAST, beast, true, 2, false, 0, 0.0F) && FaunaRules.allowed(FaunaRole.BEAST, beast, true, 3, false, 0, 0.0F), "beasts from level 3");
            h.assertTrue(!FaunaRules.allowed(FaunaRole.BEAST, beast, true, 9, false, 1, 0.0F), "one beast near a player");
            // time of day
            h.assertTrue(Habitat.Window.DAY.contains(6000) && !Habitat.Window.DAY.contains(18000) && Habitat.Window.DAY.contains(23800), "day window");
            h.assertTrue(Habitat.Window.DUSK_NIGHT.contains(12500) && Habitat.Window.DUSK_NIGHT.contains(18000) && !Habitat.Window.DUSK_NIGHT.contains(6000), "night window");
            h.assertTrue(Habitat.Window.DAY_DUSK.contains(13000) && !Habitat.Window.DAY_DUSK.contains(18000), "golden hour");
            // ground: wild ground yes, your floors no
            h.assertTrue(Habitat.Ground.NATURAL.accepts(Blocks.GRASS_BLOCK.defaultBlockState()) && Habitat.Ground.NATURAL.accepts(Blocks.SNOW_BLOCK.defaultBlockState())
                    && !Habitat.Ground.NATURAL.accepts(Blocks.OAK_PLANKS.defaultBlockState()) && !Habitat.Ground.NATURAL.accepts(Blocks.STONE_BRICKS.defaultBlockState()), "natural ground");
            h.assertTrue(Habitat.Ground.SANDY.accepts(Blocks.RED_SAND.defaultBlockState()) && !Habitat.Ground.SANDY.accepts(Blocks.GRASS_BLOCK.defaultBlockState()), "sandy ground");
            h.assertTrue(FaunaNow.compass(0, -10).equals("N") && FaunaNow.compass(10, 0).equals("E") && FaunaNow.compass(-7, 7).equals("SW"), "compass");
            h.succeed();
        });

        AwakenTests.register("fauna_beast_registry", 20, h -> {
            BeastRegistry r = new BeastRegistry();
            String ow = "minecraft:overworld";
            BlockPos a = new BlockPos(0, 64, 0), near = new BlockPos(100, 64, 0), far = new BlockPos(1000, 64, 0);
            h.assertTrue(r.roomFor(ow, a, 0), "a fresh world has room");
            UUID id = UUID.randomUUID();
            h.assertTrue(r.seen(id, "old_antler", ow, a, 100), "first report is a new beast");
            h.assertTrue(!r.seen(id, "old_antler", ow, a.east(), 140), "later reports aren't");
            h.assertTrue(!r.roomFor(ow, near, 100 + BeastRegistry.COOLDOWN), "spacing: none within " + BeastRegistry.SPACING);
            h.assertTrue(!r.roomFor(ow, far, 200), "cooldown after a new beast");
            h.assertTrue(r.roomFor(ow, far, 100 + BeastRegistry.COOLDOWN), "room again after the cooldown");
            h.assertTrue(r.roomFor("awaken:somewhere", near, 100 + BeastRegistry.COOLDOWN), "spacing is per dimension");
            r.reserve(100 + BeastRegistry.COOLDOWN);
            h.assertTrue(!r.roomFor(ow, far, 120 + BeastRegistry.COOLDOWN), "a reserved spawn holds the slot");
            h.assertTrue(r.nearest(ow, near, 160) != null && r.nearest(ow, far, 160) == null, "nearest within range");
            r.gone(id);
            h.assertTrue(r.all().isEmpty() && r.nearest(ow, a, 160) == null, "gone is gone");
            h.succeed();
        });

        AwakenTests.registerIsolated("fauna_spawn_rules_in_world", 1200, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = realPlayer(h, new BlockPos(0, UP, 30));
            BlockPos open = h.absolutePos(new BlockPos(0, UP, 0));
            ground(level, open);
            BlockPos dark = h.absolutePos(new BlockPos(6, UP, 0));
            BlockPos lit = h.absolutePos(new BlockPos(-6, UP, 0));
            box(level, dark, false);
            box(level, lit, true);
            List<AwakenMob> crowd = new ArrayList<>();
            h.startSequence()
                    // the light engine runs behind the block changes: wait until the rooms are really dark and lit
                    .thenWaitUntil(() -> lightSettled(h, level, dark, lit))
                    .thenExecute(() -> {
                        clearFauna(level, p);
                        FaunaRules.timeOverride = 6000L;
                        try {
                            RandomSource rnd = RandomSource.create(7L);
                            // not awakened: neutral never, ambient only sometimes (hints)
                            denies(h, level, "mossback", open, rnd, p, "no mossback before awakening");
                            int hints = 0;
                            String last = null;
                            for (int i = 0; i < 200; i++) {
                                String v = why(level, "bloom_sprite", open, rnd, p);
                                if (v == null) hints++;
                                else last = v;
                            }
                            if (!(hints > 30 && hints < 120)) {
                                StringBuilder above = new StringBuilder();
                                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, open.getX(), open.getZ());
                                for (int y = open.getY(); y <= top + 1; y++) {
                                    var st = level.getBlockState(new BlockPos(open.getX(), y, open.getZ()));
                                    if (!st.isAir()) above.append(y - open.getY()).append(':').append(st.getBlock().getDescriptionId()).append(' ');
                                }
                                Awaken.LOG.error("[fauna] open spot {} heightmap {} above: {}", open.toShortString(), top, above);
                            }
                            h.assertTrue(hints > 30 && hints < 120, "ambient hints about a third of the time before awakening, got " + hints + "/200 (last refusal: " + last + ")");
                            awaken(p);
                            allows(h, level, "bloom_sprite", open, rnd, p, "a sprite by day");
                            allows(h, level, "mossback", open, rnd, p, "a mossback by day");
                            denies(h, level, "lumen_moth", open, rnd, p, "no moths by day");
                            denies(h, level, "glass_scarab", open, rnd, p, "no scarab on grass");
                            denies(h, level, "mossback", dark, rnd, p, "surface creatures don't spawn under a roof");
                            allows(h, level, "gloomstalker", dark, rnd, p, "a night hunter in the dark");
                            denies(h, level, "gloomstalker", lit, rnd, p, "torchlight: no night hunter");
                            FaunaRules.timeOverride = 18000L;
                            allows(h, level, "lumen_moth", open, rnd, p, "moths at night");
                            denies(h, level, "bloom_sprite", open, rnd, p, "no sprites at night");
                            FaunaRules.timeOverride = 6000L;
                            // Hearth radius
                            FaunaRules.HearthCheck before = FaunaRules.hearth;
                            FaunaRules.hearth = (l, at) -> true;
                            try {
                                denies(h, level, "mossback", open, rnd, p, "no neutral creature inside a Hearth radius");
                                denies(h, level, "gloomstalker", dark, rnd, p, "no night hunter inside a Hearth radius");
                                allows(h, level, "bloom_sprite", open, rnd, p, "ambient creatures still visit home");
                            } finally {
                                FaunaRules.hearth = before;
                            }
                            // named beasts: level 3+ and never on top of you
                            denies(h, level, "briar_sow", open, rnd, p, "no beast below level 3");
                            while (Progression.profile(p).level() < 3) Progression.grantLevels(p, 1.0, Progression.XpSource.COMMAND);
                            BeastRegistry.get(level.getServer()).resetCooldown();
                            denies(h, level, "briar_sow", open, rnd, p, "no beast within " + FaunaRules.BEAST_MIN_DISTANCE + " blocks");
                            // the game rule
                            level.getGameRules().set(FaunaModule.SPAWNING.get(), false, level.getServer());
                            denies(h, level, "bloom_sprite", open, rnd, p, "awaken:fauna_spawning false stops spawns");
                            level.getGameRules().set(FaunaModule.SPAWNING.get(), true, level.getServer());
                            // the cap: five mossbacks near the player and the sixth is refused
                            MobDef moss = Bestiary.get("mossback");
                            for (int i = 0; i < FaunaRole.NEUTRAL.capPerPlayer; i++) {
                                AwakenMob m = Mobs.spawn(level, moss, Vec3.atBottomCenterOf(open.offset(i * 2 - 4, 0, 4)), 1,
                                        new MobRole(MobRole.Role.NORMAL, 1, moss.theme.id, Optional.empty(), ""), Set.of());
                                if (m != null) {
                                    m.setNoAi(true);
                                    crowd.add(m);
                                }
                            }
                            h.assertTrue(FaunaCensus.countNear(level, FaunaRole.NEUTRAL, p.getX(), p.getZ(), 128) >= FaunaRole.NEUTRAL.capPerPlayer, "census counts them");
                            denies(h, level, "mossback", open, rnd, p, "role cap reached");
                            allows(h, level, "bloom_sprite", open, rnd, p, "other roles unaffected");
                        } finally {
                            FaunaRules.timeOverride = null;
                            level.getGameRules().set(FaunaModule.SPAWNING.get(), true, level.getServer());
                            crowd.forEach(AwakenMob::discard);
                            level.getServer().getPlayerList().remove(p);
                            unbox(level, dark);
                            unbox(level, lit);
                        }
                    })
                    .thenSucceed();
        });

        AwakenTests.registerIsolated("fauna_natural_spawning_end_to_end", 200, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = realPlayer(h, new BlockPos(0, UP, 30));
            awaken(p);
            BlockPos start = h.absolutePos(new BlockPos(0, UP, 0));
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) ground(level, start.offset(dx, 0, dz));
            // The test world's biome is whatever the flat preset puts here: make this patch plains.
            BlockPos a = start.offset(-10, -4, -10), b = start.offset(10, 6, 10);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(),
                    "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " minecraft:plains");
            // Vanilla's spawner judges by the nearest player: send other tests' leftover players far away (this batch runs alone).
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other != p && other.distanceToSqr(Vec3.atCenterOf(start)) < 200 * 200) other.setPos(other.getX() + 2000, other.getY(), other.getZ());
            }
            // the real clock to noon too: night hunters also ask the sky how dark it is (earlier batches may leave it night)
            long clock = Math.floorMod(level.getOverworldClockTime(), 24000L);
            var cmd = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
            level.getServer().getCommands().performPrefixedCommand(cmd, "time set 6000");
            h.runAfterDelay(10, () -> {
                clearFauna(level, p);
                List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
                FaunaRules.timeOverride = 6000L;
                List<FaunaMob> spawned = new ArrayList<>();
                try {
                    h.assertTrue(level.getBiome(start).is(Biomes.PLAINS), "the test world is plains");
                    MobSpawnSettings here = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, start);
                    h.assertTrue(!here.getMobsToSpawn(MobTypes.FAUNA).isEmpty(), "plains spawns fauna here");
                    @SuppressWarnings("unchecked")
                    EntityType<Mob> moss = (EntityType<Mob>) (EntityType<?>) MobTypes.type("mossback");
                    h.assertTrue(SpawnPlacements.isSpawnPositionOk(moss, level, start), "the patch is a valid spawn position");
                    h.assertTrue(level.getNearestPlayer(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 128.0, false) == p, "the test player is the nearest");
                    String why = FaunaRules.verdict(moss, level, EntitySpawnReason.NATURAL, start, RandomSource.create(3L), null, false);
                    h.assertTrue(why == null, "the rules allow a mossback here by day, but: " + why);
                    for (int i = 0; i < 80 && spawned.size() < 6; i++) {
                        NaturalSpawner.spawnCategoryForPosition(MobTypes.FAUNA, level, start);
                        // only what spawned here: elsewhere in the test world it may be night, and night hunters spawn there
                        for (FaunaMob m : FaunaCensus.in(level)) {
                            if (!before.contains(m) && !spawned.contains(m) && m.distanceToSqr(Vec3.atCenterOf(start)) < 48 * 48) spawned.add(m);
                        }
                    }
                    h.assertTrue(!spawned.isEmpty(), "vanilla's spawner placed overworld creatures through the biome modifier");
                    for (FaunaMob m : spawned) {
                        MobDef d = m.def();
                        h.assertTrue(d.theme == Theme.GREENWILD, d.id + " belongs to the plains' family");
                        h.assertTrue(d.fauna != FaunaRole.HUNTER && !d.id.equals("lumen_moth"), d.id + " is a day creature");
                        h.assertTrue(d.fauna != FaunaRole.BEAST, "no beast this close or this low a level");
                        h.assertTrue(m.role().contentLevel() <= 1, d.id + " spawned at the player's level, was " + m.role().contentLevel());
                    }
                    for (FaunaRole r : FaunaRole.values()) {
                        h.assertTrue(FaunaCensus.countNear(level, r, p.getX(), p.getZ(), 128) <= r.capPerPlayer, r + " stays under its cap");
                    }
                } finally {
                    FaunaRules.timeOverride = null;
                    level.getServer().getCommands().performPrefixedCommand(cmd, "time set " + clock);
                    spawned.forEach(FaunaMob::discard);
                    level.getServer().getPlayerList().remove(p);
                }
                h.succeed();
            });
        });

        AwakenTests.registerIsolated("fauna_hunters_keep_the_light_rule", 1200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos dark = h.absolutePos(new BlockPos(6, UP, 0));
            BlockPos lit = h.absolutePos(new BlockPos(-6, UP, 0));
            box(level, dark, false);
            box(level, lit, true);
            ServerPlayer p = realPlayer(h, new BlockPos(-6, UP, 0));
            List<AwakenMob> hunter = new ArrayList<>();
            h.startSequence()
                    .thenWaitUntil(() -> lightSettled(h, level, dark, lit))
                    .thenExecute(() -> {
                        p.setPos(Vec3.atBottomCenterOf(dark));
                        h.assertTrue(FaunaMob.inDarkness(p), "a player in a dark room is fair game");
                        p.setPos(Vec3.atBottomCenterOf(lit));
                        h.assertTrue(!FaunaMob.inDarkness(p), "a player in torchlight is safe");
                        MobDef d = Bestiary.get("gloomstalker");
                        AwakenMob m = Mobs.spawn(level, d, Vec3.atBottomCenterOf(lit), 1, new MobRole(MobRole.Role.NORMAL, 1, d.theme.id, Optional.empty(), ""), Set.of());
                        h.assertTrue(m != null, "gloomstalker spawned");
                        hunter.add(m);
                        m.setTarget(p);
                        h.assertTrue(m.getTarget() == p, "it had a target");
                    })
                    .thenWaitUntil(() -> h.assertTrue(hunter.get(0).getTarget() == null, "a night hunter lets go of a player standing in torchlight"))
                    .thenExecute(() -> {
                        hunter.forEach(AwakenMob::discard);
                        level.getServer().getPlayerList().remove(p);
                        unbox(level, dark);
                        unbox(level, lit);
                    })
                    .thenSucceed();
        });

        AwakenTests.registerIsolated("fauna_sighting_discovery", 200, h -> {
            ServerLevel level = h.getLevel();
            // only the creatures this test places: natural spawns near the awakened player would be seen first
            boolean spawning = level.getGameRules().get(FaunaModule.SPAWNING.get());
            level.getGameRules().set(FaunaModule.SPAWNING.get(), false, level.getServer());
            ServerPlayer p = realPlayer(h, new BlockPos(0, UP, 0));
            p.setYRot(0.0F);
            p.setXRot(0.0F);
            p.setYHeadRot(0.0F);
            // creatures other tests left nearby would take the look's ray budget before ours, and their walls our view
            clearFauna(level, p);
            BlockPos at = h.absolutePos(new BlockPos(0, UP, 0));
            for (int dx = -4; dx <= 8; dx++) for (int dz = -10; dz <= 12; dz++) ground(level, at.offset(dx, 0, dz));
            List<AwakenMob> made = new ArrayList<>();
            try {
                MobDef moss = Bestiary.get("mossback");
                AwakenMob seen = Mobs.spawn(level, moss, Vec3.atBottomCenterOf(at.south(6)), 1, new MobRole(MobRole.Role.NORMAL, 1, moss.theme.id, Optional.empty(), ""), Set.of());
                seen.setNoAi(true);
                made.add(seen);
                h.assertTrue(Sightings.check(p) == null, "not awakened: nothing is discovered yet");
                awaken(p);
                for (FaunaMob m : List.copyOf(FaunaCensus.in(level))) if (!made.contains(m) && m.distanceToSqr(p) < 200 * 200) m.discard();
                double xp = Progression.profile(p).xp();
                int lv = Progression.profile(p).level();
                h.assertTrue(Sightings.check(p) == moss, "the mossback in front of the player is discovered");
                h.assertTrue(Progression.profile(p).counter("awaken:discovered/creature/mossback") == 1, "the discovery counter");
                h.assertTrue(Progression.profile(p).level() > lv || Progression.profile(p).xp() > xp, "discovery XP paid");
                Sightings.forget(p.getUUID());
                h.assertTrue(Sightings.check(p) == null, "a second look is nothing new");
                // behind a wall: not seen
                MobDef stag = Bestiary.get("frost_stag");
                AwakenMob hidden = Mobs.spawn(level, stag, Vec3.atBottomCenterOf(at.south(10).east(3)), 1, new MobRole(MobRole.Role.NORMAL, 1, stag.theme.id, Optional.empty(), ""), Set.of());
                hidden.setNoAi(true);
                made.add(hidden);
                for (int x = -1; x <= 7; x++) for (int y = 0; y <= 4; y++) level.setBlock(at.south(8).east(x).above(y), Blocks.STONE.defaultBlockState(), 3);
                Sightings.forget(p.getUUID());
                h.assertTrue(Sightings.check(p) == null, "a creature behind a wall isn't seen");
                // behind the player: not seen either
                MobDef moth = Bestiary.get("lumen_moth");
                AwakenMob behind = Mobs.spawn(level, moth, Vec3.atBottomCenterOf(at.north(8)).add(0, 1, 0), 0, new MobRole(MobRole.Role.NORMAL, 0, moth.theme.id, Optional.empty(), ""), Set.of());
                behind.setNoAi(true);
                made.add(behind);
                Sightings.forget(p.getUUID());
                h.assertTrue(Sightings.check(p) == null, "a creature behind your back isn't seen");
                p.setYRot(180.0F);
                p.setYHeadRot(180.0F);
                Sightings.forget(p.getUUID());
                h.assertTrue(Sightings.check(p) == moth, "turn around and you see it");
            } finally {
                level.getGameRules().set(FaunaModule.SPAWNING.get(), spawning, level.getServer());
                made.forEach(AwakenMob::discard);
                level.getServer().getPlayerList().remove(p);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("fauna_reveal_on_awakening", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(0, UP, 0));
            for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) ground(level, at.offset(dx, 0, dz));
            ServerPlayer p = realPlayer(h, new BlockPos(0, UP, 0));
            clearFauna(level, p);
            List<FaunaMob> before = new ArrayList<>(FaunaCensus.in(level));
            Reveal.FORCED.add(p.getUUID());
            Progression.awaken(p);
            h.assertTrue(p.getData(FaunaModule.STATE.get()).revealPending(), "the reveal waits a moment");
            h.succeedWhen(() -> {
                h.assertTrue(p.getData(FaunaModule.STATE.get()).revealed(), "the reveal played");
                Reveal.FORCED.remove(p.getUUID());
                List<FaunaMob> burst = new ArrayList<>();
                for (FaunaMob m : FaunaCensus.in(level)) if (!before.contains(m) && m.distanceTo(p) < 20) burst.add(m);
                h.assertTrue(!burst.isEmpty(), "ambient creatures stepped out around the player");
                for (FaunaMob m : burst) h.assertTrue(m.def().fauna == FaunaRole.AMBIENT, m.def().id + " is ambient");
                burst.forEach(FaunaMob::discard);
                level.getServer().getPlayerList().remove(p);
            });
        });

        AwakenTests.registerIsolated("fauna_named_beast_drops", 200, h -> {
            ServerLevel level = h.getLevel();
            // Out of the beast's warning range, so it grazes instead of picking a fight.
            ServerPlayer p = realPlayer(h, new BlockPos(0, 1, -24));
            awaken(p);
            MobDef d = Bestiary.get("briar_sow");
            BlockPos at = h.absolutePos(new BlockPos(0, 1, 2));
            AwakenMob e = Mobs.spawn(level, d, Vec3.atBottomCenterOf(at), 3, new MobRole(d.tier.role, 3, d.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(e instanceof NamedBeast, "a named beast entity");
            NamedBeast beast = (NamedBeast) e;
            h.assertTrue(beast.isPersistenceRequired(), "beasts never despawn");
            h.assertTrue(beast.getData(CoreApi.MOB_ROLE.get()).role() == MobRole.Role.WARDEN, "Warden-sized");
            h.assertTrue(beast.bar() != null, "it has a bar");
            beast.setGuaranteedCore(true);
            UUID id = beast.getUUID();
            long[] felled = {0};
            Vec3[] where = {Vec3.ZERO};
            List<ItemEntity> drops = new ArrayList<>();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(BeastRegistry.get(level.getServer()).all().stream().anyMatch(x -> x.id().equals(id)), "the beast reported in"))
                    .thenExecute(() -> {
                        felled[0] = Progression.profile(p).counter("awaken:beasts_felled");
                        where[0] = beast.position();
                        beast.hurtServer(level, level.damageSources().playerAttack(p), 1.0E6F);
                    })
                    .thenWaitUntil(() -> {
                        h.assertTrue(beast.isDeadOrDying(), "felled");
                        drops.clear();
                        drops.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(where[0])).inflate(4)));
                        int essence = 0;
                        boolean part = false, core = false;
                        for (ItemEntity ie : drops) {
                            if (ie.getItem().is(CoreApi.ESSENCE_SHARD.get())) essence += ie.getItem().getCount();
                            if (BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath().equals("briar_tusk")) part = true;
                            if (BuiltInRegistries.ITEM.getKey(ie.getItem().getItem()).getPath().equals("power_core")) core = true;
                        }
                        h.assertTrue(essence >= 2, "essence shards dropped: " + essence);
                        h.assertTrue(part, "its part dropped");
                        h.assertTrue(core, "a Power Core dropped (guaranteed for the test; 45% in play)");
                        h.assertTrue(Progression.profile(p).counter("awaken:beasts_felled") == felled[0] + 1, "beasts_felled counted");
                        h.assertTrue(Progression.profile(p).counter("awaken:discovered/beast_felled/briar_sow") == 1, "first kill paid");
                        h.assertTrue(BeastRegistry.get(level.getServer()).all().stream().noneMatch(x -> x.id().equals(id)), "a felled beast leaves the registry");
                    })
                    .thenExecute(() -> {
                        drops.forEach(ItemEntity::discard);
                        level.getServer().getPlayerList().remove(p);
                    })
                    .thenSucceed();
        });
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    static void assertSpawns(GameTestHelper h, Holder<Biome> biome, String... ids) {
        var entry = biome.value().getAttributes().get(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        h.assertTrue(entry != null && entry.argument() instanceof MobSpawnSettings, biome.getRegisteredName() + " has natural spawns");
        MobSpawnSettings s = (MobSpawnSettings) entry.argument();
        Set<String> got = new HashSet<>();
        for (Weighted<MobSpawnSettings.SpawnerData> w : s.getMobsToSpawn(MobTypes.FAUNA).unwrap()) got.add(BuiltInRegistries.ENTITY_TYPE.getKey(w.value().type()).getPath());
        for (String id : ids) h.assertTrue(got.contains(id), biome.getRegisteredName() + " spawns " + id + " (has " + got + ")");
        for (MobCategory c : List.of(MobCategory.CREATURE, MobCategory.MONSTER, MobCategory.AMBIENT)) {
            for (Weighted<MobSpawnSettings.SpawnerData> w : s.getMobsToSpawn(c).unwrap()) {
                h.assertTrue(MobTypes.def(w.value().type()) == null || !MobTypes.def(w.value().type()).isFauna(), "no fauna in vanilla's " + c + " list");
            }
        }
    }

    /** The rules' answer for one creature here, judged against p (null = it may spawn). */
    @SuppressWarnings("unchecked")
    static String why(ServerLevel level, String id, BlockPos pos, RandomSource rnd, ServerPlayer p) {
        EntityType<Mob> type = (EntityType<Mob>) (EntityType<?>) MobTypes.type(id);
        return FaunaRules.verdict(type, level, EntitySpawnReason.NATURAL, pos, rnd, p, true);
    }

    static void allows(GameTestHelper h, ServerLevel level, String id, BlockPos pos, RandomSource rnd, ServerPlayer p, String what) {
        String v = why(level, id, pos, rnd, p);
        h.assertTrue(v == null, what + " (refused: " + v + ")");
    }

    static void denies(GameTestHelper h, ServerLevel level, String id, BlockPos pos, RandomSource rnd, ServerPlayer p, String what) {
        h.assertTrue(why(level, id, pos, rnd, p) != null, what);
    }

    /**
     * The light engine trails block changes, on its own threads; wait until the sealed room is dark and the lit one bright
     * (paced: see {@link AwakenTests#await}).
     */
    static void lightSettled(GameTestHelper h, ServerLevel level, BlockPos dark, BlockPos lit) {
        AwakenTests.await(h, () -> level.getBrightness(net.minecraft.world.level.LightLayer.SKY, dark) == 0
                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, dark) == 0, "the sealed room is dark");
        AwakenTests.await(h, () -> level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, lit) >= 10
                && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, lit.above()) >= 10, "the lit room is bright");
    }

    /**
     * Grass with open sky over it. Tests build high above the test plot, where the framework doesn't clean up, and
     * plots are reused: another test's sealed room or wall may still stand here.
     */
    static void ground(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        for (int y = 0; y <= 6; y++) level.setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 3);
    }

    /** Takes a {@link #box} down again: plots are reused, and a room left high above one roofs the next test's sky. */
    static void unbox(ServerLevel level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) {
            level.setBlock(pos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** A sealed stone room around a two-high space at pos (grass floor), lit by glowstone in the wall if asked. */
    static void box(ServerLevel level, BlockPos pos, boolean lit) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) {
            boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
            level.setBlock(pos.offset(dx, dy, dz), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        if (lit) level.setBlock(pos.offset(1, 0, 1), Blocks.GLOWSTONE.defaultBlockState(), 3);
    }

    /** Isolated tests start from an empty patch: creatures left by earlier tests would count against the caps. */
    static void clearFauna(ServerLevel level, ServerPlayer p) {
        for (FaunaMob m : List.copyOf(FaunaCensus.in(level))) if (m.distanceToSqr(p) < 200 * 200) m.discard();
    }

    /** Awakens without the reveal burst (so tests count only their own creatures). */
    static void awaken(ServerPlayer p) {
        Progression.awaken(p);
        p.setData(FaunaModule.STATE.get(), p.getData(FaunaModule.STATE.get()).done());
    }

    /**
     * A survival player in the test level. The helper's mock player answers "creative" to everything, which hostile
     * mobs (rightly) ignore; this one is a plain ServerPlayer on an embedded connection.
     */
    static ServerPlayer realPlayer(GameTestHelper h, BlockPos relative) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "fauna-test"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        BlockPos at = h.absolutePos(relative);
        if (player.level() != level) player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0, true);
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return player;
    }
}
