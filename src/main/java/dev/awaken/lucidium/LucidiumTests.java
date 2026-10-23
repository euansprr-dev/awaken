package dev.awaken.lucidium;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.dream.ActiveDream;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.Lucidity;
import dev.awaken.lucidium.dream.Planner;
import dev.awaken.lucidium.dream.Sleep;
import dev.awaken.lucidium.journal.SnapshotFiles;
import dev.awaken.lucidium.plan.DreamPalette;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.world.BlueprintCompiler;
import dev.awaken.lucidium.world.Cells;
import dev.awaken.lucidium.world.DreamBlueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** GameTests for the journal, plans, palettes, lucidity, nightmares, the not-ready fallback and waking safely. */
final class LucidiumTests {
    private LucidiumTests() {}

    static void register() {
        AwakenTests.register("lucidium_snapshot_cap_rotation", 40, LucidiumTests::capAndRotation);
        AwakenTests.register("lucidium_snapshot_capture_file", 40, LucidiumTests::captureFile);
        AwakenTests.register("lucidium_plan_determinism", 40, LucidiumTests::planDeterminism);
        AwakenTests.register("lucidium_palette_remap", 20, LucidiumTests::paletteRemap);
        AwakenTests.register("lucidium_lucidity_maths", 20, LucidiumTests::lucidityMaths);
        AwakenTests.register("lucidium_nightmare_families", 20, LucidiumTests::nightmareFamilies);
        AwakenTests.register("lucidium_plan_not_ready_fallback", 40, LucidiumTests::notReady);
        AwakenTests.register("lucidium_safe_wake", 160, LucidiumTests::safeWake);
    }

    private static final Snapshot.Kind[] ROTA = {Snapshot.Kind.BIOME, Snapshot.Kind.STRUCTURE, Snapshot.Kind.BOSS,
            Snapshot.Kind.NEAR_DEATH, Snapshot.Kind.VISTA, Snapshot.Kind.GATE, Snapshot.Kind.HOME, Snapshot.Kind.KILLS};

    private static Snapshot snap(Dreamer d, Snapshot.Kind kind, long day, int x) {
        return new Snapshot(d.newId(day), kind, day, "minecraft:overworld", new BlockPos(x, 64, x * 3), "test " + kind, "", 0);
    }

    // ---- journal --------------------------------------------------------------------------------------------------------

    private static void capAndRotation(GameTestHelper h) {
        Dreamer d = new Dreamer();
        int accepted = 0;
        for (int i = 0; i < 16; i++) {
            if (d.add(snap(d, ROTA[i % ROTA.length], 1, i)).isPresent()) accepted++;
        }
        h.assertTrue(accepted == Dreamer.PER_DAY, "10 snapshots per play-day, accepted " + accepted);
        h.assertFalse(d.canCapture(Snapshot.Kind.BIOME, 1), "day 1 is full");
        h.assertTrue(d.add(snap(d, Snapshot.Kind.MANUAL, 1, 99)).isPresent(), "admin captures ignore the daily cap");

        Dreamer kinds = new Dreamer();
        int biomes = 0;
        for (int i = 0; i < 5; i++) if (kinds.add(snap(kinds, Snapshot.Kind.BIOME, 2, i)).isPresent()) biomes++;
        h.assertTrue(biomes == Snapshot.Kind.BIOME.dailyMax(), "per-kind cap, got " + biomes);

        Dreamer rot = new Dreamer();
        String first = null;
        List<String> rotatedOut = new ArrayList<>();
        for (long day = 1; day <= 9; day++) {
            for (int i = 0; i < 10; i++) {
                Snapshot s = snap(rot, ROTA[i % ROTA.length], day, i);
                if (first == null) first = s.id();
                rot.add(s).ifPresent(rotatedOut::addAll);
            }
        }
        h.assertTrue(rot.snapshots.size() == Dreamer.KEEP, "keeps the 64 most recent, has " + rot.snapshots.size());
        h.assertTrue(!rotatedOut.isEmpty() && rotatedOut.getFirst().equals(first), "the oldest is rotated out first");
        h.assertTrue(rot.snapshots.getLast().day() == 9, "newest kept");
        h.succeed();
    }

    private static void captureFile(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(BlockPos.ZERO, Blocks.GOLD_BLOCK);
        BlockPos gold = h.absolutePos(BlockPos.ZERO);
        BlockPos feet = gold.above();
        CompoundTag tag = SnapshotFiles.capture(level, feet);
        Fragment f = Fragment.fromTemplate(BuiltInRegistries.BLOCK, tag);
        h.assertTrue(f.sx == 16 && f.sy == 12 && f.sz == 16, "16x12x16 template");
        BlockState at = f.get(8, Snapshot.FLOOR - 1, 8);
        h.assertTrue(at != null && at.is(Blocks.GOLD_BLOCK), "floor block lands at (8,3,8), got " + at);
        Path file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("awaken").resolve("lucidium-test").resolve("capture.nbt");
        SnapshotFiles.writeNow(file, tag);
        Optional<Fragment> back = SnapshotFiles.load(file);
        h.assertTrue(back.isPresent() && back.get().get(8, 3, 8) != null && back.get().get(8, 3, 8).is(Blocks.GOLD_BLOCK), "file round trip");
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
        h.succeed();
    }

    // ---- plans --------------------------------------------------------------------------------------------------------

    private static List<Snapshot> journal() {
        Dreamer d = new Dreamer();
        List<Snapshot> out = new ArrayList<>();
        Snapshot.Kind[] kinds = {Snapshot.Kind.HOME, Snapshot.Kind.BIOME, Snapshot.Kind.BOSS, Snapshot.Kind.VISTA,
                Snapshot.Kind.NEAR_DEATH, Snapshot.Kind.KILLS, Snapshot.Kind.STRUCTURE, Snapshot.Kind.GATE, Snapshot.Kind.BIOME};
        for (int i = 0; i < kinds.length; i++) out.add(snap(d, kinds[i], 1 + i / 4, i));
        return out;
    }

    private static DreamPlanner.Input input(long day, Map<Nightmares.Family, Integer> fears, String intention) {
        return new DreamPlanner.Input(1234L, new UUID(1, 2), day, 0, journal(), fears, intention, Optional.empty(), Optional.of("awaken:prism"));
    }

    private static void planDeterminism(GameTestHelper h) {
        Map<Nightmares.Family, Integer> fears = Map.of(Nightmares.Family.FALLING, 2);
        DreamPlan a = DreamPlanner.plan(input(3, fears, "")).orElseThrow();
        DreamPlan b = DreamPlanner.plan(input(3, fears, "")).orElseThrow();
        h.assertTrue(a.equals(b), "same inputs, same plan");
        DreamPlan c = DreamPlanner.plan(input(4, fears, "")).orElseThrow();
        h.assertTrue(a.seed() != c.seed(), "another night, another seed");
        long memories = a.nodes().stream().filter(n -> n.kind().equals(DreamPlan.MEMORY) || n.kind().equals(DreamPlan.IMAGINED)).count();
        h.assertTrue(memories >= DreamPlanner.MIN_NODES && memories <= DreamPlanner.MAX_NODES, "4-8 places, got " + memories);
        h.assertTrue(a.nodes().getFirst().kind().equals(DreamPlan.MEMORY) && a.nodes().getFirst().label().contains("HOME"), "dreams start at home");
        h.assertTrue(a.edges().size() >= a.nodes().size() - 1, "every place is linked");
        h.assertTrue(a.signs().size() == 2 && !a.signs().get(0).equals(a.signs().get(1)), "two different dream signs");
        h.assertTrue(a.durationTicks() >= 3600 && a.durationTicks() <= 9600, "3-8 minutes");
        DreamPlan fear = DreamPlanner.plan(input(3, fears, DreamPlanner.FACE_MY_FEAR)).orElseThrow();
        h.assertTrue(fear.nightmare().equals(Optional.of("falling")), "face my fear brings the falling nightmare");
        DreamPlan door = DreamPlanner.plan(input(3, Map.of(), DreamPlanner.FIND_THE_DOOR)).orElseThrow();
        h.assertTrue(door.nodes().stream().anyMatch(n -> n.kind().equals(DreamPlan.DOOR)), "find the door adds the door");
        // the plan survives saving
        var json = DreamPlan.CODEC.encodeStart(JsonOps.INSTANCE, a).getOrThrow();
        h.assertTrue(DreamPlan.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(a), "plan codec round trip");
        // and builds the same dream twice (missing snapshot files dream as imagined places)
        DreamBlueprint x = BlueprintCompiler.compile(a, 4000, id -> Optional.empty());
        DreamBlueprint y = BlueprintCompiler.compile(a, 4000, id -> Optional.empty());
        h.assertTrue(x.spawn.equals(y.spawn) && x.chunkCount() == y.chunkCount(), "blueprint deterministic");
        BlockPos probe = x.nodes.get(1).feet().below();
        h.assertTrue(java.util.Objects.equals(x.blockAt(probe.getX(), probe.getY(), probe.getZ()), y.blockAt(probe.getX(), probe.getY(), probe.getZ())),
                "same block at the same place");
        h.assertTrue(Cells.cellAt((int) x.spawn.x, (int) x.spawn.z) == 4000, "the dream stays inside its cell");
        h.assertTrue(DreamPlanner.plan(new DreamPlanner.Input(1, new UUID(3, 4), 1, 0, List.of(), Map.of(), "", Optional.empty(), Optional.empty())).isEmpty(),
                "an empty journal has nothing to dream");
        h.succeed();
    }

    private static void paletteRemap(GameTestHelper h) {
        DreamPalette p = DreamPalette.of("pearl", 3);
        h.assertTrue(p.apply(Blocks.STONE.defaultBlockState()).is(Blocks.SMOOTH_QUARTZ), "stone becomes quartz");
        h.assertTrue(p.apply(Blocks.GRASS_BLOCK.defaultBlockState()).is(Blocks.MOSS_BLOCK), "grass becomes moss");
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        BlockState remapped = p.apply(stairs);
        h.assertTrue(remapped.is(Blocks.BIRCH_STAIRS) && remapped.getValue(StairBlock.FACING) == Direction.EAST, "oak stairs become birch, facing kept: " + remapped);
        BlockState wool = p.apply(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState());
        h.assertTrue(wool.is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.ORANGE)), "red wool shifted by 3 is orange: " + wool);
        h.assertTrue("yellow_wool".equals(DreamPalette.rotateColour("light_blue_wool", 1)), "light_blue is not read as blue");
        h.assertTrue(DreamPalette.rotateColour("red_wool", 0) == null, "no shift, no change");
        h.assertTrue(p.apply(Blocks.WATER.defaultBlockState()).is(Blocks.WATER), "water untouched in pearl");
        DreamPalette again = DreamPalette.of("pearl", 3);
        h.assertTrue(again.apply(Blocks.COBBLESTONE_STAIRS.defaultBlockState()) == p.apply(Blocks.COBBLESTONE_STAIRS.defaultBlockState()), "deterministic");
        for (DreamPalette.Mood m : DreamPalette.MOODS) {
            for (var e : m.exact().entrySet()) {
                h.assertTrue(BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.withDefaultNamespace(e.getValue())),
                        "mood " + m.name() + " maps to a real block: " + e.getValue());
            }
        }
        h.succeed();
    }

    private static void lucidityMaths(GameTestHelper h) {
        h.assertTrue(Lucidity.start(true, true, 4, 0) == 37f, "intention 10 + WBTB 25 + recall 2");
        h.assertTrue(Lucidity.start(false, false, 100, 0) == 10f, "recall is capped at +10");
        h.assertTrue(Lucidity.start(true, true, 40, 50) == 95f, "10 + 25 + 10 + 50");
        h.assertTrue(Lucidity.start(true, true, 40, 70) == 100f, "clamped at 100");
        h.assertTrue(Math.abs(Lucidity.gain(40f, Lucidity.REALITY_CHECK, 10) - 53.2f) < 1e-3, "SPI 10 adds 10% to gains");
        h.assertTrue(Lucidity.gain(95f, Lucidity.DREAM_SIGN, 0) == 100f, "never above 100");
        h.assertTrue(Lucidity.realityCheckAmount(5) == Lucidity.REALITY_CHECK / 2, "spammed checks count half");
        h.assertFalse(Lucidity.canFly(49.9f, false), "no flight below 50");
        h.assertTrue(Lucidity.canFly(50f, false) && Lucidity.canFly(36f, true), "fly at 50, or 35 with the intention");
        h.assertTrue(Math.abs(Lucidity.dreamChance(5, false, false, false, false) - 0.45) < 1e-9, "40% + SPI%");
        h.assertTrue(Lucidity.dreamChance(30, true, true, true, true) == 0.95, "capped at 95%");
        h.succeed();
    }

    private static void nightmareFamilies(GameTestHelper h) {
        h.assertTrue(Nightmares.familyOfEntity("minecraft:zombie") == Nightmares.Family.UNDEAD, "zombie: undead");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:skeleton") == Nightmares.Family.UNDEAD, "skeleton: undead");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:spider") == Nightmares.Family.ARTHROPOD, "spider: arthropod");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:creeper") == Nightmares.Family.EXPLOSION, "creeper: explosion");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:drowned") == Nightmares.Family.DROWNING, "drowned: drowning");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:blaze") == Nightmares.Family.BURNING, "blaze: burning");
        h.assertTrue(Nightmares.familyOfEntity("minecraft:pillager") == Nightmares.Family.GENERIC, "pillager: generic");
        h.assertTrue(Nightmares.familyOfEntity("not an id!") == Nightmares.Family.GENERIC, "garbage: generic");
        var src = h.getLevel().damageSources();
        h.assertTrue(Nightmares.familyOf(src.fall()) == Nightmares.Family.FALLING, "fall");
        h.assertTrue(Nightmares.familyOf(src.lava()) == Nightmares.Family.BURNING, "lava");
        h.assertTrue(Nightmares.familyOf(src.drown()) == Nightmares.Family.DROWNING, "drown");
        h.assertTrue(Nightmares.familyOf(src.fellOutOfWorld()) == Nightmares.Family.FALLING, "void");
        Map<String, Long> counters = Map.of(Nightmares.KILLED_BY_WEEK + "minecraft:zombie", 3L,
                Nightmares.KILLED_BY_WEEK + "minecraft:skeleton", 1L, Nightmares.KILLED_BY_WEEK + "minecraft:creeper", 2L,
                "awaken:kills", 50L);
        Map<Nightmares.Family, Integer> own = new EnumMap<>(Nightmares.Family.class);
        own.put(Nightmares.Family.FALLING, 5);
        own.put(Nightmares.Family.UNDEAD, 2);
        Map<Nightmares.Family, Integer> scores = Nightmares.scores(counters, own);
        h.assertTrue(scores.get(Nightmares.Family.UNDEAD) == 4 && scores.get(Nightmares.Family.EXPLOSION) == 2
                && scores.get(Nightmares.Family.FALLING) == 5, "scores " + scores);
        h.assertTrue(Nightmares.top(scores).equals(Optional.of(Nightmares.Family.FALLING)), "the worst fear wins");
        var rng = new XoroshiroRandomSource(7);
        h.assertTrue(Nightmares.choose(scores, true, rng).equals(Optional.of(Nightmares.Family.FALLING)), "face my fear: certain");
        h.assertTrue(Nightmares.choose(Map.of(), true, rng).equals(Optional.of(Nightmares.Family.GENERIC)), "no deaths + intention: generic");
        h.assertTrue(Nightmares.choose(Map.of(), false, rng).isEmpty(), "no deaths: no nightmare");
        List<String> reset = Nightmares.weeklyCountersOf(Nightmares.Family.UNDEAD, counters);
        h.assertTrue(reset.size() == 2 && reset.stream().noneMatch(s -> s.contains("creeper")), "beating undead resets zombie and skeleton only");
        Dreamer d = new Dreamer();
        d.markDeath(Nightmares.Family.BURNING, 1);
        d.markDeath(Nightmares.Family.BURNING, 9);
        d.markDeath(Nightmares.Family.FALLING, 10);
        h.assertTrue(d.weeklyMarks(10).get(Nightmares.Family.BURNING) == 1, "only this week's deaths count");
        d.clearFamily(Nightmares.Family.BURNING);
        h.assertTrue(!d.weeklyMarks(10).containsKey(Nightmares.Family.BURNING), "a beaten fear is gone");
        h.succeed();
    }

    // ---- sleeping and waking -------------------------------------------------------------------------------------------

    private static void notReady(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Dreams.dreamer(sp).snapshots.clear();
        h.assertTrue(DreamPlanner.plan(Planner.input(sp)).isEmpty(), "nothing in the journal: no plan");
        Planner.invalidate(sp);
        Sleep.Session s = new Sleep.Session();
        s.roll = true;
        s.bed = sp.blockPosition();
        Sleep.Outcome out = Sleep.tryEnter(sp, s);
        h.assertTrue(out == Sleep.Outcome.NOT_READY, "plan not ready: a normal night, got " + out);
        h.assertFalse(Dreams.isDreaming(sp), "still in bed, not dreaming");
        h.assertTrue(sp.level() == h.getLevel(), "never left the world");
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void safeWake(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        BlockPos start = h.absolutePos(BlockPos.ZERO).above(2);
        sp.teleportTo(level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
        Vec3 before = sp.position();
        boolean mayflyBefore = sp.getAbilities().mayfly;
        DreamPlan plan = DreamPlanner.plan(new DreamPlanner.Input(99, sp.getUUID(), 1, 0,
                List.of(new Snapshot("missing_file", Snapshot.Kind.MANUAL, 1, "minecraft:overworld", BlockPos.ZERO, "nowhere", "", 0)),
                Map.of(), "", Optional.empty(), Optional.empty())).orElseThrow();
        // GameTests have no Lucidium dimension: dream "in" the test level instead
        Planner.Prepared p = Planner.buildNow(level.getServer(), sp.getUUID(), plan);
        ActiveDream ad = Dreams.enter(sp, p, new Dreams.Entry(false, null, level, false));
        h.assertTrue(ad != null && Dreams.isDreaming(sp), "entered the dream");
        h.assertTrue(sp.getPostEffects().contains(Dreams.FX), "dream shader on");
        h.assertTrue(sp.position().distanceTo(p.blueprint().spawn) < 2, "arrived at the dream's start");
        int cell = p.blueprint().cell;
        h.runAfterDelay(5, () -> {
            Dreams.wake(sp, Dreams.Wake.TIME);
            h.assertFalse(Dreams.isDreaming(sp), "awake");
            h.assertTrue(sp.position().distanceTo(before) < 1.5, "back where they lay down: " + sp.position() + " vs " + before);
            h.assertFalse(sp.getPostEffects().contains(Dreams.FX) || sp.getPostEffects().contains(Dreams.FX_LUCID), "shader cleared");
            h.assertTrue(sp.getAbilities().mayfly == mayflyBefore, "flight restored");
            h.assertTrue(dev.awaken.lucidium.world.DreamChunkGenerator.blueprint(cell) == null, "the dream is thrown away");
            // dying in a dream wakes you, shaken, with nothing lost
            Planner.Prepared again = Planner.buildNow(level.getServer(), sp.getUUID(), plan);
            h.assertTrue(Dreams.enter(sp, again, new Dreams.Entry(false, null, level, false)) != null, "dreaming again");
            int items = sp.getInventory().getContainerSize();
            sp.setHealth(0.5f);
            sp.die(level.damageSources().fellOutOfWorld()); // the death is cancelled and becomes a wake
            h.runAfterDelay(3, () -> {
                h.assertFalse(Dreams.isDreaming(sp), "a fatal blow wakes you");
                h.assertTrue(sp.isAlive() && sp.getHealth() > 0, "alive");
                h.assertTrue(sp.hasEffect(LucidiumContent.SHAKEN), "shaken");
                h.assertTrue(sp.getInventory().getContainerSize() == items, "nothing lost");
                level.getServer().getPlayerList().remove(sp);
                h.succeed();
            });
        });
    }
}
