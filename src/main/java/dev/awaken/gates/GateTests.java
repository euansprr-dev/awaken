package dev.awaken.gates;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.Claims;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.run.Satchel;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.GatePainter;
import dev.awaken.gates.world.GateTestAccess;
import dev.awaken.gates.world.Puzzles;
import dev.awaken.gates.world.Room;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** GameTests for the gates module: Director rules, determinism, claims, death rules, states, cell maths, painting. */
final class GateTests {
    private GateTests() {}

    static void register() {
        AwakenTests.register("gates_director_rules", 40, h -> {
            for (Rank player : Rank.values()) {
                GateMemory mem = new GateMemory();
                List<String> themes = new ArrayList<>();
                List<String> layouts = new ArrayList<>();
                int sinceTwist = 0;
                for (int i = 0; i < 300; i++) {
                    Director.Roll roll = Director.roll(0xA11CE + i * 7919L + player.ordinal() * 1_000_003L, player, mem, null, null);
                    GatePlan p = roll.plan();
                    int d = Math.abs(p.rank().ordinal() - player.ordinal());
                    h.assertTrue(d <= 1, "rank within one of the player's: " + p.rank() + " for " + player);
                    h.assertTrue(p.theme().fits(p.rank()), "theme " + p.theme() + " fits rank " + p.rank());
                    for (int k = Math.max(0, themes.size() - 3); k < themes.size(); k++)
                        h.assertTrue(!themes.get(k).equals(p.theme().id), "no theme from the last 3 (" + p.theme() + " at roll " + i + ")");
                    for (int k = Math.max(0, layouts.size() - 2); k < layouts.size(); k++)
                        h.assertTrue(!layouts.get(k).equals(p.layout().name()), "no layout from the last 2 (" + p.layout() + ")");
                    int cost = 0;
                    for (Modifier m : p.modifiers()) cost += m.cost;
                    h.assertTrue(cost <= Curve.modifierBudget(p.rank()), "modifier budget " + cost + " > " + Curve.modifierBudget(p.rank()));
                    h.assertTrue(p.modifiers().size() <= Curve.modifierMax(p.rank()), "modifier count");
                    h.assertTrue(p.modifiers().stream().distinct().count() == p.modifiers().size(), "no duplicate modifiers");
                    if (p.theme().friendly) h.assertTrue(p.modifiers().isEmpty(), "friendly gates have no modifiers");
                    sinceTwist = p.twist() == Twist.NONE ? sinceTwist + 1 : 0;
                    h.assertTrue(sinceTwist <= 11, "a twist at least every 12 gates");
                    h.assertTrue(p.floors() == Math.min(6, 3 + p.rank().ordinal() / 2) || p.floors() == Math.min(6, 4 + p.rank().ordinal() / 2), "floors via Curve");
                    h.assertTrue(roll.breaksInDays() >= 1 && roll.breaksInDays() <= 5, "break days via Curve");
                    themes.add(p.theme().id);
                    layouts.add(p.layout().name());
                }
            }
            Director.Roll a = Director.roll(777, Rank.C, new GateMemory(), null, null), b = Director.roll(777, Rank.C, new GateMemory(), null, null);
            h.assertTrue(a.plan().equals(b.plan()) && a.trace().equals(b.trace()), "same seed and memory → same gate and trace");
            h.succeed();
        });

        AwakenTests.register("gates_plan_determinism", 60, h -> {
            for (int i = 0; i < 24; i++) {
                GatePlan plan = testPlan(9000L + i * 31L, Rank.values()[i % 6], Theme.values()[i % Theme.values().length],
                        Layout.values()[i % Layout.values().length], Twist.values()[i % Twist.values().length]);
                GateLayout a = GateTestAccess.build(plan), b = GateTestAccess.build(plan);
                h.assertTrue(signature(a).equals(signature(b)), "same plan → same rooms (" + plan.layout() + ")");
                for (FloorLayout f : a.main) {
                    h.assertTrue(!f.criticalPath().isEmpty(), "floor " + f.floor + " of " + plan.layout() + " reaches its end");
                    for (Room r : f.rooms) if (r.normals + r.elites > 0) h.assertTrue(!r.spawnSpots.isEmpty(), "spawn spots in " + r.type + " " + r.shape);
                }
                for (FloorLayout f : a.annex) h.assertTrue(!f.criticalPath().isEmpty(), "annex floor reaches its end");
                h.assertTrue(a.main.size() == plan.floors() && a.main.get(plan.floors() - 1).bossFloor, "last floor holds the Lord");
                h.assertTrue(a.annex.isEmpty() != plan.hasAnnex(), "annex only for Double Gate / Gate within a Gate");
                long pa = paintHash(a, 7), pb = paintHash(b, 7);
                h.assertTrue(pa == pb, "same plan → same blocks");
            }
            GatePlan x = testPlan(1, Rank.C, Theme.HIVE, Layout.LABYRINTH, Twist.NONE);
            GatePlan y = testPlan(2, Rank.C, Theme.HIVE, Layout.LABYRINTH, Twist.NONE);
            h.assertFalse(signature(GateTestAccess.build(x)).equals(signature(GateTestAccess.build(y))), "different seeds → different labyrinths");
            h.succeed();
        });

        AwakenTests.register("gates_painter_features", 60, h -> {
            GatePlan plan = testPlan(4242, Rank.D, Theme.BELL_CRYPT, Layout.PUZZLE_VAULT, Twist.NONE);
            GateLayout l = GateTestAccess.build(plan);
            FloorLayout f = l.main.get(0);
            Room start = f.room(f.start), end = f.room(f.end);
            Map<Long, BlockState> blocks = paintRoom(l, f, start);
            int ex = start.centerX(), ez = start.centerZ();
            BlockState under = blocks.get(key(ex, f.baseY + start.dy - 1, ez));
            h.assertTrue(under != null && under.is(GatesRegistry.ENTRANCE.get()), "entrance sigil under the arrival point, was " + under);
            h.assertTrue(!blocks.containsKey(key(ex, f.baseY + start.dy, ez)) && !blocks.containsKey(key(ex, f.baseY + start.dy + 1, ez)), "head room above the sigil");
            Map<Long, BlockState> shrine = paintRoom(l, f, end);
            Room.Feature sf = end.feature(Room.FeatureKind.SHRINE), seal = end.feature(Room.FeatureKind.SEAL);
            h.assertTrue(sf != null && seal != null, "shrine room has a shrine and a seal");
            BlockState s1 = shrine.get(key(sf.x(), f.baseY + end.dy + sf.y(), sf.z()));
            h.assertTrue(s1 != null && s1.is(GatesRegistry.SHRINE.get()), "shrine block painted, was " + s1);
            boolean barred = false;
            for (BlockState s : shrine.values()) if (s.is(GatesRegistry.BARS.get())) barred = true;
            h.assertTrue(barred, "puzzle vault shrine starts sealed");
            h.succeed();
        });

        AwakenTests.register("gates_claim_idempotent", 60, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            GateRegistry reg = GateRegistry.get(h.getLevel().getServer());
            String id = "test:" + UUID.randomUUID() + ":" + p.getUUID();
            Claim c = new Claim(id, p.getUUID(), -5, List.of(new ItemStack(Items.DIAMOND, 3)), 0, "extract", "");
            h.assertTrue(Claims.write(p, c), "first write accepted");
            h.assertFalse(Claims.write(p, c), "second write refused");
            int before = p.getInventory().countItem(Items.DIAMOND);
            Claims.deliver(p);
            Claims.deliver(p);
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) - before == 3, "paid exactly once");
            h.assertTrue(reg.isDone(id), "claim marked DONE");
            h.assertFalse(Claims.write(p, c), "a DONE claim can't be written again");
            Claims.deliver(p);
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) - before == 3, "still paid once after replays");
            h.succeed();
        });

        AwakenTests.register("gates_satchel_death_rules", 20, h -> {
            RunState run = new RunState();
            Satchel.add(run.satchel, new ItemStack(Items.IRON_INGOT, 16));
            Satchel.add(run.satchel, new ItemStack(Items.DIAMOND, 1));
            Satchel.add(run.satchel, new ItemStack(Items.EMERALD, 1));
            Satchel.add(run.satchel, new ItemStack(Items.BREAD, 5));
            List<ItemStack> kept = Runs.satchelOnDeath(run, AwakenConfig.Difficulty.STORY);
            h.assertTrue(run.satchel.isEmpty(), "Story: the Satchel ends empty");
            int iron = 0, gems = 0, bread = 0;
            for (ItemStack s : kept) {
                if (s.is(Items.IRON_INGOT)) iron += s.getCount();
                if (s.is(Items.DIAMOND) || s.is(Items.EMERALD)) gems += s.getCount();
                if (s.is(Items.BREAD)) bread += s.getCount();
            }
            h.assertTrue(iron == 8 && bread == 2 && gems == 1, "Story keeps half: iron " + iron + " bread " + bread + " gems " + gems);
            Satchel.add(run.satchel, new ItemStack(Items.IRON_INGOT, 16));
            h.assertTrue(Runs.satchelOnDeath(run, AwakenConfig.Difficulty.HUNTER).isEmpty() && run.satchel.isEmpty(), "Hunter loses the Satchel");
            Satchel.add(run.satchel, new ItemStack(Items.IRON_INGOT, 16));
            h.assertTrue(Runs.satchelOnDeath(run, AwakenConfig.Difficulty.HARDLINE).isEmpty(), "Hardline loses the Satchel");
            // capacity and merging
            RunState full = new RunState();
            for (int i = 0; i < 30; i++) Satchel.add(full.satchel, new ItemStack(i % 2 == 0 ? Items.STONE : Items.DIRT, 64));
            h.assertTrue(Satchel.count(full.satchel) == Satchel.SLOTS, "27 slots, was " + Satchel.count(full.satchel));
            ItemStack extra = new ItemStack(Items.OAK_LOG, 1);
            h.assertTrue(Satchel.add(full.satchel, extra) == 0 && extra.getCount() == 1, "a full Satchel refuses new items");
            RunState merge = new RunState();
            Satchel.add(merge.satchel, new ItemStack(Items.ARROW, 40));
            Satchel.add(merge.satchel, new ItemStack(Items.ARROW, 40));
            h.assertTrue(Satchel.count(merge.satchel) == 2 && Satchel.items(merge.satchel) == 80, "stacks merge");
            h.succeed();
        });

        AwakenTests.register("gates_state_machine", 20, h -> {
            GateRecord g = new GateRecord(1, testPlan(5, Rank.E, Theme.HIVE, Layout.SHAFT, Twist.NONE), BlockPos.ZERO, 0, 0, 0, 3, GateState.BUILDING, Optional.empty());
            h.assertFalse(g.transition(GateState.ACTIVE), "BUILDING can't go ACTIVE");
            h.assertTrue(g.transition(GateState.READY), "BUILDING → READY");
            h.assertTrue(g.transition(GateState.ACTIVE), "READY → ACTIVE");
            h.assertTrue(g.transition(GateState.OPEN), "ACTIVE → OPEN");
            h.assertTrue(g.transition(GateState.ACTIVE), "OPEN → ACTIVE");
            h.assertFalse(g.transition(GateState.READY), "can't go back to READY");
            h.assertTrue(g.transition(GateState.CLEARED), "ACTIVE → CLEARED");
            h.assertFalse(g.transition(GateState.BROKEN), "a cleared gate never breaks");
            h.assertTrue(g.transition(GateState.RELEASED), "CLEARED → RELEASED");
            h.assertTrue(GateState.RELEASED.next().isEmpty(), "RELEASED is final");
            GateRecord b = new GateRecord(2, g.plan, BlockPos.ZERO, 0, 1, 0, 3, GateState.READY, Optional.empty());
            h.assertTrue(b.transition(GateState.BROKEN) && b.transition(GateState.RELEASED), "READY → BROKEN → RELEASED");
            h.assertFalse(GateState.BROKEN.canGo(GateState.ACTIVE), "broken gates can't be entered");
            h.succeed();
        });

        AwakenTests.register("gates_cell_region_math", 20, h -> {
            int[] cells = {0, 1, 63, 64, 65, 130, 4095};
            for (int i : cells) {
                int ox = Cells.originX(i), oz = Cells.originZ(i);
                h.assertTrue(ox == (i % 64) * 1024 && oz == (i / 64) * 1024, "origin of " + i);
                h.assertTrue(Cells.regionX(i) == 2 * (i % 64) && Cells.regionZ(i) == 2 * (i / 64), "region of " + i);
                h.assertTrue(Cells.cellAt(ox, oz) == i && Cells.cellAt(ox + 511, oz + 511) == i && Cells.cellAt(ox + 200, oz + 300) == i, "inside cell " + i);
                h.assertTrue(Cells.cellAt(ox + 512, oz) == -1 && Cells.cellAt(ox, oz + 700) == -1, "gaps belong to no cell");
                h.assertTrue((ox >> 9) == Cells.regionX(i) && ((ox + 511) >> 9) == Cells.regionX(i), "one region file per cell");
            }
            h.assertTrue(Cells.regionFile(65).equals("r.2.2.mca"), "region file name");
            for (int f = 1; f <= 6; f++) {
                int y = Cells.floorY(f);
                h.assertTrue(y == 200 - 32 * (f - 1), "floor " + f + " Y");
                h.assertTrue(Cells.floorAtY(y) == f && Cells.floorAtY(y - 4) == f && Cells.floorAtY(y + 27) == f, "band of floor " + f);
            }
            h.assertTrue(Cells.cellAt(-5, 10) == -1, "negative coordinates hold no cell");
            h.succeed();
        });

        AwakenTests.register("gates_puzzles_solvable", 20, h -> {
            GatePlan plan = testPlan(77, Rank.B, Theme.PRECURSOR_LAB, Layout.PUZZLE_VAULT, Twist.NONE);
            GateLayout l = GateTestAccess.build(plan);
            int puzzles = 0;
            for (FloorLayout f : l.main) {
                for (Room r : f.rooms) {
                    if (r.type != Room.Type.PUZZLE) continue;
                    puzzles++;
                    int n = r.features(Room.FeatureKind.RUNE).size();
                    int mask = Puzzles.initialMask(r, n);
                    h.assertFalse(Puzzles.solved(mask, n), "a puzzle doesn't start solved");
                    boolean solvable = false;
                    for (int presses = 0; presses < (1 << n) && !solvable; presses++) {
                        int m = mask;
                        for (int i = 0; i < n; i++) if ((presses & (1 << i)) != 0) m = Puzzles.press(m, i, n);
                        solvable = Puzzles.solved(m, n);
                    }
                    h.assertTrue(solvable, "every rune puzzle can be solved");
                }
            }
            h.assertTrue(puzzles >= 2, "a Puzzle Vault has puzzles");
            h.succeed();
        });

        AwakenTests.register("gates_codecs_roundtrip", 20, h -> {
            var ops = RegistryOps.create(NbtOps.INSTANCE, h.getLevel().registryAccess());
            GateRecord g = new GateRecord(9, testPlan(33, Rank.A, Theme.FROST_MONASTERY, Layout.HUB_AND_SPOKES, Twist.ECHO_GATE),
                    new BlockPos(10, 70, -20), 1, 12, 4, 6, GateState.OPEN, Optional.of(UUID.randomUUID()));
            g.floor(0, 2).clearedRooms.add(5);
            g.floor(0, 2).runes.put(3, 9);
            g.entries.put(UUID.randomUUID(), 2);
            g.echoPaths.put("0:1:x", List.of(1L, 2L, 3L));
            var tag = GateRecord.CODEC.encodeStart(ops, g).getOrThrow();
            GateRecord back = GateRecord.CODEC.parse(ops, tag).getOrThrow();
            h.assertTrue(back.plan.equals(g.plan) && back.cell == 12 && back.state == GateState.OPEN, "gate record round trip");
            h.assertTrue(back.floor(0, 2).clearedRooms.contains(5) && back.floor(0, 2).runes.get(3) == 9, "floor state round trip");
            h.assertTrue(back.entries.size() == 1 && back.echoPaths.get("0:1:x").size() == 3, "entries and echo paths");
            RunState run = new RunState();
            run.gateId = 9;
            run.floor = 3;
            run.satchelXp = 123.5;
            run.runIndex = 2;
            Satchel.add(run.satchel, new ItemStack(Items.GOLD_INGOT, 7));
            run.cleared.add("0:1");
            var rt = RunState.MAP_CODEC.codec().encodeStart(ops, run).getOrThrow();
            RunState r2 = RunState.MAP_CODEC.codec().parse(ops, rt).getOrThrow();
            h.assertTrue(r2.gateId == 9 && r2.floor == 3 && r2.runIndex == 2 && Satchel.items(r2.satchel) == 7 && r2.clearedFloor(0, 1), "run state round trip");
            h.succeed();
        });

        AwakenTests.register("gates_dimension_json", 20, h -> {
            var server = h.getLevel().getServer();
            var ops = RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
            // the gates' own dimension, and the open sky's (38.11) with the same generator and biomes under a real sky
            for (String dim : List.of("gates", "gates_sky")) {
                var res = server.getResourceManager().getResource(dev.awaken.Awaken.id("dimension/" + dim + ".json"));
                h.assertTrue(res.isPresent(), "dimension/" + dim + ".json is in the mod's data");
                try (var reader = res.get().openAsReader()) {
                    var json = com.google.gson.JsonParser.parseReader(reader);
                    var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json).getOrThrow();
                    h.assertTrue(stem.generator() instanceof dev.awaken.gates.world.GateChunkGenerator, "generator is the gate cell generator");
                    var source = stem.generator().getBiomeSource();
                    h.assertTrue(source instanceof dev.awaken.gates.world.GateBiomeSource, "biome source is cell-aware");
                    // one biome per theme, the void, and every world's own and its moods' (SPEC §38.6)
                    java.util.Set<String> worlds = new java.util.HashSet<>();
                    for (var w : dev.awaken.gates.worlds.GateWorlds.all().values()) {
                        if (!w.biome().isEmpty()) worlds.add(w.biome());
                        for (var m : w.moods()) if (!m.biome().isEmpty()) worlds.add(m.biome());
                    }
                    h.assertTrue(source.possibleBiomes().size() == Theme.values().length + 1 + worlds.size(),
                            "one biome per theme, the void and each world's, was " + source.possibleBiomes().size());
                    h.assertTrue(stem.type().value().minY() == 0 && stem.type().value().height() == 256, "dimension height");
                    h.assertTrue(stem.type().value().hasSkyLight() == dim.equals("gates_sky"), dim + " has sky light only under the open sky");
                } catch (java.io.IOException e) {
                    h.fail("could not read the dimension " + dim + ": " + e);
                }
            }
            h.succeed();
        });

        AwakenTests.register("gates_chunk_generator", 40, h -> {
            var level = h.getLevel();
            GatePlan plan = testPlan(31337, Rank.C, Theme.EMBER_MINES, Layout.HUB_AND_SPOKES, Twist.NONE);
            int cell = 70;
            GateLayout l = GateTestAccess.build(plan);
            FloorLayout f = l.main.get(0);
            Room start = f.room(f.start);
            int chunkX = (Cells.originX(cell) >> 4) + f.originSlotX + start.gx;
            int chunkZ = (Cells.originZ(cell) >> 4) + f.originSlotZ + start.gz;
            var chunk = new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ),
                    net.minecraft.world.level.chunk.UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
            GateTestAccess.fill(chunk, plan, cell);
            BlockPos sigil = new BlockPos(Cells.originX(cell) + f.originSlotX * 16 + start.centerX(), f.baseY + start.dy - 1,
                    Cells.originZ(cell) + f.originSlotZ * 16 + start.centerZ());
            h.assertTrue(chunk.getBlockState(sigil).is(GatesRegistry.ENTRANCE.get()), "entrance sigil generated, was " + chunk.getBlockState(sigil));
            h.assertTrue(chunk.getBlockState(sigil.above()).isAir() && chunk.getBlockState(sigil.above(2)).isAir(), "room to stand");
            BlockPos wall = new BlockPos(Cells.originX(cell) + f.originSlotX * 16 + start.gx * 16, f.baseY + 2,
                    Cells.originZ(cell) + f.originSlotZ * 16 + start.gz * 16);
            h.assertFalse(chunk.getBlockState(wall).isAir(), "rooms are carved out of solid rock");
            int lights = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = f.baseY - 4; y < f.baseY + 28; y++) {
                if (chunk.getBlockState(new BlockPos(chunkX * 16 + x, y, chunkZ * 16 + z)).getLightEmission() > 0) lights++;
            }
            h.assertTrue(lights > 0, "the start room is lit");
            var empty = new net.minecraft.world.level.chunk.ProtoChunk(new net.minecraft.world.level.ChunkPos(-40, -40),
                    net.minecraft.world.level.chunk.UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
            GateTestAccess.fill(empty, plan, cell);
            h.assertTrue(empty.getBlockState(new BlockPos(-40 * 16, f.baseY, -40 * 16)).isAir(), "chunks outside a cell stay void");
            h.succeed();
        });

        // SPEC §27.6: the first gate waits for level 3, then forms 250-400 blocks out along the way the player left,
        // is counted as theirs and raised on the Horizon until a floor is cleared
        AwakenTests.registerIsolated("gates_first_gate_on_purpose", 100, h -> {
            var server = h.getLevel().getServer();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            GateRecord g = null;
            try {
                dev.awaken.progression.Progression.awaken(p);
                h.assertTrue(Director.firstGate(server, p) == null && dev.awaken.progression.Progression.profile(p).counter(Director.FIRST_WAIT) == 1,
                        "level 1: the first gate waits (one pass counted)");
                dev.awaken.progression.Progression.topUpToLevel(p, 3, dev.awaken.progression.Progression.XpSource.COMMAND);
                // a pass can find no dry ground 250-400 out (the test world's seed varies); a few passes always do
                for (int pass = 0; pass < 4 && g == null; pass++) g = Director.firstGate(server, p);
                h.assertTrue(g != null, "level 3, out of the city: the first gate forms");
                double dx = g.pos.getX() - p.getX(), dz = g.pos.getZ() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                h.assertTrue(d >= Director.FIRST_MIN - 2 && d <= Director.FIRST_MAX + 2, "250-400 blocks out: " + Math.round(d));
                h.assertTrue(g.plan.rank() == dev.awaken.progression.Progression.rank(p), "at the player's own rank");
                h.assertTrue(dev.awaken.progression.Progression.profile(p).counter(Director.FIRST_GATE) == g.id + 1L, "remembered as their first");
                var site = Director.firstGateSite(p);
                h.assertTrue(site.size() == 1 && site.getFirst().raised() && site.getFirst().where().equals(g.pos), "raised on the Horizon: " + site);
            } finally {
                if (g != null) GateService.release(server, g);
                server.getPlayerList().remove(p);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_build_break_hold", 900, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            BlockPos base = h.absolutePos(BlockPos.ZERO).offset(14, 0, 14);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) - 1;
            BlockPos ground = dryGround(level, new BlockPos(base.getX(), y, base.getZ()));
            GatePlan plan = testPlan(2024, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE);
            GateRecord g = GateService.create(server, GateRegistry.get(server).nextId(), plan, ground, 0, Optional.empty(), 2, List.of("test"), false);
            g.anyGround = true;
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(g.state == GateState.READY, "gate became READY, was " + g.state + " (trace: " + g.trace + ")"))
                    .thenExecute(() -> {
                        BlockPos core = g.pos.above(3);
                        h.assertTrue(level.getBlockState(core).is(GatesRegistry.CORE.get()), "surface core placed");
                        h.assertTrue(dev.awaken.gates.run.Interactions.structureAt(server, core) == g, "the core belongs to its gate");
                        h.assertTrue(Gates.gateAt(level, g.pos).map(i -> i.id() == g.id).orElse(false), "Gates.gateAt finds it");
                        h.assertTrue(Gates.preview(server, g.id).map(pv -> pv.theme() == Theme.BELL_CRYPT).orElse(false), "preview shows the plan");
                        GateCard.send(p, g);
                        Breaks.breakGate(server, g);
                        h.assertTrue(g.state == GateState.BROKEN, "the gate broke");
                    })
                    .thenWaitUntil(() -> h.assertTrue(g.lord.isPresent(), "a Gate Lord came out"))
                    .thenExecute(() -> {
                        var lord = level.getEntity(g.lord.get());
                        h.assertTrue(lord instanceof net.minecraft.world.entity.LivingEntity, "the Lord is in the world");
                        h.assertTrue(level.getBlockState(g.pos.above(3)).getValue(dev.awaken.gates.block.GateBlocks.Core.BROKEN), "the core shows the break");
                        h.assertTrue(lord.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).role() == dev.awaken.core.api.MobRole.Role.BREAK, "BREAK role");
                        lord.kill(level);
                    })
                    .thenWaitUntil(() -> h.assertTrue(g.state == GateState.RELEASED, "holding the break releases the gate, was " + g.state))
                    .thenWaitUntil(() -> h.assertFalse(level.getBlockState(g.pos.above(3)).is(GatesRegistry.CORE.get()), "the structure comes down"))
                    .thenExecute(() -> {
                        // only this gate's monsters: other tests may be running next door
                        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(g.pos).inflate(48),
                                e -> dev.awaken.core.api.MobRole.of(e).gateId().equals(g.key()))) e.discard();
                        h.assertTrue(GateRegistry.get(server).releasedCells().contains(g.cell), "its cell waits for cleanup");
                    })
                    .thenSucceed();
        });

        AwakenTests.registerIsolated("gates_run_walkthrough", 100, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            GatePlan plan = new GatePlan(424242, GatePlan.CONTENT_VERSION, Rank.E, Theme.OVERGROWN_TEMPLE, Layout.LABYRINTH, List.of(),
                    Twist.NONE, 3, Theme.OVERGROWN_TEMPLE.bossId(), Theme.HIVE, 0);
            GateRegistry reg = GateRegistry.get(server);
            int cell = reg.allocateCell();
            dev.awaken.gates.world.GateCells.put(cell, plan);
            GateService.testLevel = level;
            try {
                GateLayout l = GateLayout.of(plan);
                FloorLayout f = l.main.get(0);
                for (int gx = -1; gx <= f.w; gx++) {
                    for (int gz = -1; gz <= f.h; gz++) {
                        int cx = (Cells.originX(cell) >> 4) + f.originSlotX + gx, cz = (Cells.originZ(cell) >> 4) + f.originSlotZ + gz;
                        level.getChunk(cx, cz);
                        GatePainter.paint(l, cell, cx, cz, (x, y, z, s) -> level.setBlock(new BlockPos(cx * 16 + x, y, cz * 16 + z), s, 818));
                    }
                }
                GateRecord g = new GateRecord(reg.nextId(), plan, h.absolutePos(BlockPos.ZERO), 0, cell, 0, 99, GateState.READY, Optional.empty());
                g.interiorReady = true;
                reg.put(g);
                ServerPlayer p = h.makeMockServerPlayerInLevel();
                dev.awaken.progression.Progression.awaken(p);
                double xp0 = dev.awaken.progression.Progression.profile(p).xp() + dev.awaken.progression.Progression.profile(p).level() * 1e6;
                h.assertTrue(Runs.enter(p, g), "entered the gate");
                var w = dev.awaken.gates.run.Where.at(server, p.blockPosition());
                h.assertTrue(w != null && w.room() != null && w.room().id == f.start, "arrived in the start room");
                h.assertTrue(g.state == GateState.ACTIVE && g.inside.contains(p.getUUID()), "gate ACTIVE with the player inside");
                List<Integer> path = f.criticalPath();
                int spawned = 0;
                for (int i = 1; i < path.size(); i++) {
                    Room r = f.room(path.get(i));
                    int[] spot = r.spawnSpots.isEmpty() ? new int[]{r.centerX(), r.centerZ()} : r.spawnSpots.get(0);
                    BlockPos at = GateService.roomFloor(g, f, r, spot[0], spot[1]);
                    p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
                    dev.awaken.gates.run.RunEvents.tickNow(p);
                    var key = new dev.awaken.gates.run.GateMobs.Key(g.id, 0, 1, r.id);
                    for (UUID id : dev.awaken.gates.run.GateMobs.aliveIds(key)) {
                        var e = level.getEntity(id);
                        if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                            spawned++;
                            le.hurtServer(level, p.damageSources().playerAttack(p), 100000F);
                            e.discard();
                        }
                    }
                    h.assertTrue(dev.awaken.gates.run.GateMobs.alive(key) == 0, "room " + r.id + " emptied");
                }
                RunState run = Runs.run(p);
                h.assertTrue(run.clearedFloor(0, 1), "stepping into the shrine room clears the floor");
                h.assertTrue(run.satchelXp > 0, "the floor bonus rides in the Satchel");
                h.assertTrue(spawned > 0 && run.kills >= spawned, "monsters woke and kills were counted (" + spawned + " spawned, " + run.kills + " kills)");
                double xp1 = dev.awaken.progression.Progression.profile(p).xp() + dev.awaken.progression.Progression.profile(p).level() * 1e6;
                h.assertTrue(spawned == 0 || xp1 > xp0, "kill XP is granted live");
                h.assertTrue(dev.awaken.gates.run.Rooms.spots(level, g, f, f.room(f.end)).size() > 0, "shrine room has floor");
                ItemStack loot = new ItemStack(Items.DIAMOND, 5);
                Gates.satchelAdd(p, loot);
                h.assertTrue(loot.isEmpty() && Satchel.items(run.satchel) >= 5, "loot moved into the Satchel");
                int before = p.getInventory().countItem(Items.DIAMOND);
                dev.awaken.gates.run.Interactions.answer(p, "gates:extract", true);
                dev.awaken.gates.run.RunEvents.flushNow();
                h.assertFalse(Runs.run(p).active(), "extracting ends the run");
                h.assertTrue(p.getInventory().countItem(Items.DIAMOND) - before == 5, "the Satchel was paid out once");
                h.assertTrue(g.state == GateState.OPEN && g.inside.isEmpty(), "gate OPEN and empty, was " + g.state);
                h.assertTrue(dev.awaken.progression.Progression.profile(p).counter("awaken:floors_cleared") >= 1, "floors_cleared counted");
                // a second run: through the Seal to floor 2
                h.assertTrue(Runs.enter(p, g), "re-entered");
                h.assertTrue(Runs.run(p).runIndex == 1, "second run gets its own claim ids");
                Room shrine = f.room(f.end);
                BlockPos sp = GateService.roomFloor(g, f, shrine, shrine.centerX(), shrine.centerZ() + 2);
                p.teleportTo(level, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
                dev.awaken.gates.run.RunEvents.tickNow(p);
                Runs.descend(p);
                h.assertTrue(Runs.run(p).floor == 2, "the Seal leads to floor 2, run is on " + Runs.run(p).floor);
                h.assertTrue(Gates.activeRun(p).map(ri -> ri.floor() == 2).orElse(false), "activeRun reports floor 2");
                h.assertTrue(Gates.forceExtract(p, true), "force extract");
                h.assertFalse(Runs.run(p).active(), "out again");
            } finally {
                GateService.testLevel = null;
                dev.awaken.gates.world.GateCells.remove(cell);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_lord_and_death", 100, h -> {
            var level = h.getLevel();
            var server = level.getServer();
            GatePlan plan = new GatePlan(515151, GatePlan.CONTENT_VERSION, Rank.E, Theme.EMBER_MINES, Layout.ARENA_CHAIN, List.of(),
                    Twist.NONE, 3, Theme.EMBER_MINES.bossId(), Theme.HIVE, 0);
            GateRegistry reg = GateRegistry.get(server);
            int cell = reg.allocateCell();
            dev.awaken.gates.world.GateCells.put(cell, plan);
            GateService.testLevel = level;
            try {
                GateLayout l = GateLayout.of(plan);
                FloorLayout bf = l.main.get(plan.floors() - 1);
                for (int gx = -1; gx <= bf.w; gx++) {
                    for (int gz = -1; gz <= bf.h; gz++) {
                        int cx = (Cells.originX(cell) >> 4) + bf.originSlotX + gx, cz = (Cells.originZ(cell) >> 4) + bf.originSlotZ + gz;
                        level.getChunk(cx, cz);
                        GatePainter.paint(l, cell, cx, cz, (x, y, z, s) -> {
                            if (bf.paints(y)) level.setBlock(new BlockPos(cx * 16 + x, y, cz * 16 + z), s, 818);
                        });
                    }
                }
                GateRecord g = new GateRecord(reg.nextId(), plan, h.absolutePos(BlockPos.ZERO), 0, cell, 0, 99, GateState.READY, Optional.empty());
                g.interiorReady = true;
                reg.put(g);
                ServerPlayer p = h.makeMockServerPlayerInLevel();
                dev.awaken.progression.Progression.awaken(p);
                h.assertTrue(Runs.enter(p, g), "entered");
                Room arena = bf.room(bf.end);
                h.assertTrue(arena.type == Room.Type.BOSS, "the last floor ends in the Lord's arena");
                BlockPos at = GateService.roomFloor(g, bf, arena, arena.centerX() + 6, arena.centerZ() + 6);
                p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
                dev.awaken.gates.run.RunEvents.tickNow(p);
                h.assertTrue(Runs.run(p).floor == plan.floors(), "the run follows you to the boss floor");
                h.assertTrue(g.boss.isPresent(), "the Lord appeared");
                var lord = level.getEntity(g.boss.get());
                h.assertTrue(lord instanceof net.minecraft.world.entity.LivingEntity, "the Lord is a living thing");
                Gates.satchelAdd(p, new ItemStack(Items.EMERALD, 3));
                ((net.minecraft.world.entity.LivingEntity) lord).hurtServer(level, p.damageSources().playerAttack(p), 1_000_000F);
                h.assertTrue(g.bossDead && g.state == GateState.CLEARED, "the gate is cleared, was " + g.state);
                h.assertTrue(!reg.pendingFor(p.getUUID()).isEmpty(), "the hoard and Satchel wait in a claim");
                h.assertTrue(Runs.run(p).satchel.isEmpty(), "the Satchel moved into the claim");
                Room.Feature exit = arena.feature(Room.FeatureKind.EXIT_ANCHOR);
                BlockPos portal = GateService.world(g, bf, exit.x(), arena.dy + 1, exit.z());
                h.assertTrue(level.getBlockState(portal).is(GatesRegistry.EXIT.get()), "an exit portal opened");
                Runs.exitPortal(p);
                h.assertFalse(Runs.run(p).active(), "out through the exit");
                h.assertTrue(p.getInventory().countItem(dev.awaken.core.api.CoreApi.LORD_HEART.get()) >= 1, "the Lord's Heart was paid");
                h.assertTrue(p.getInventory().countItem(Items.EMERALD) >= 3, "the Satchel was paid");
                h.assertTrue(g.state == GateState.RELEASED, "an emptied cleared gate is released, was " + g.state);
                h.assertTrue(dev.awaken.progression.Progression.profile(p).counter("awaken:gates_cleared") >= 1, "gates_cleared counted");

                // death in a gate (Hunter): Satchel lost, gear kept, Fracture
                GateRecord g2 = new GateRecord(reg.nextId(), plan, h.absolutePos(BlockPos.ZERO).offset(3, 0, 0), 0, reg.allocateCell(), 0, 99, GateState.READY, Optional.empty());
                g2.interiorReady = true;
                reg.put(g2);
                dev.awaken.gates.world.GateCells.put(g2.cell, plan);
                p.getInventory().clearContent();
                p.getInventory().add(new ItemStack(Items.IRON_SWORD));
                h.assertTrue(Runs.enter(p, g2), "entered the second gate");
                Gates.satchelAdd(p, new ItemStack(Items.GOLD_INGOT, 9));
                // mock players never finish loading a client, so the game won't let them be hurt: fire the death event the way the game does
                var death = new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p, level.damageSources().generic());
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(death);
                h.assertFalse(death.isCanceled(), "no cheat death without the perk");
                h.assertFalse(Runs.run(p).active(), "death ends the run");
                if (dev.awaken.core.AwakenConfig.DIFFICULTY.get() != dev.awaken.core.AwakenConfig.Difficulty.STORY) {
                    h.assertTrue(dev.awaken.progression.Progression.profile(p).fractureUntil() > level.getGameTime(), "Fracture applied");
                }
                h.assertTrue(p.getInventory().isEmpty(), "nothing dropped from the inventory (held aside)");
                dev.awaken.gates.run.RunEvents.onRespawn(p);
                h.assertTrue(p.getInventory().countItem(Items.IRON_SWORD) == 1, "gear came back");
                h.assertTrue(p.getInventory().countItem(Items.GOLD_INGOT) == 0, "the Satchel did not");
                h.assertTrue(g2.inside.isEmpty() && g2.state == GateState.OPEN, "the gate stays open for another try");
                dev.awaken.gates.world.GateCells.remove(g2.cell);
            } finally {
                GateService.testLevel = null;
                dev.awaken.gates.world.GateCells.remove(cell);
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("gates_overworld_structure", 40, h -> {
            BlockPos base = h.absolutePos(new BlockPos(0, 0, 0)).offset(6, 0, 6);
            GateRecord g = new GateRecord(77, testPlan(1, Rank.C, Theme.HIVE, Layout.LABYRINTH, Twist.NONE), base, 0, 0, 0, 3, GateState.READY, Optional.empty());
            var level = h.getLevel();
            // the isolated tests share this spot: put the ground back exactly as it was afterwards
            int r = (int) Math.ceil(GateVortex.size(Rank.C.ordinal()).ringRadius()) + 1;
            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
            for (BlockPos q : BlockPos.betweenClosed(base.offset(-r, -5, -r), base.offset(r, 16, r))) before.put(q.immutable(), level.getBlockState(q));
            Overworld.place(level, g, false);
            h.assertTrue(level.getBlockState(base.above(3)).is(GatesRegistry.CORE.get()), "core in the middle of the opening");
            h.assertTrue(level.getBlockState(base.offset(2, 3, 0)).is(GatesRegistry.CORE.get()), "a C-rank opening is wider than the old doorway");
            int spur = (int) Math.round(GateVortex.size(Rank.C.ordinal()).rx() + 1.2F);
            h.assertFalse(level.getBlockState(base.offset(-spur, 1, -1)).isAir(), "a rock spur flanks the opening");
            h.assertTrue(level.getBlockState(base.above(3)).getValue(dev.awaken.gates.block.GateBlocks.Core.RANK) == Rank.C.ordinal(), "core coloured by rank");
            h.assertTrue(level.getBlockState(g.mouth()).isAir(), "the mouth is clear");
            Overworld.remove(level, g);
            h.assertTrue(level.getBlockState(base.above(3)).isAir(), "removed again");
            before.forEach((q, st) -> {
                if (level.getBlockState(q) != st) level.setBlock(q, st, 2);
            });
            h.succeed();
        });
    }

    static GatePlan testPlan(long seed, Rank rank, Theme theme, Layout layout, Twist twist) {
        List<Modifier> mods = twist == Twist.FRIENDLY || theme.friendly ? List.of() : List.of(Modifier.values()[(int) Math.floorMod(seed, 14L)]);
        return new GatePlan(seed, GatePlan.CONTENT_VERSION, rank, theme, layout, mods, twist, Curve.floorsFor(rank, seed % 2 == 0),
                theme.bossId(), Theme.values()[(theme.ordinal() + 3) % Theme.values().length], (int) Math.floorMod(seed, 3L));
    }

    static String signature(GateLayout l) {
        StringBuilder sb = new StringBuilder();
        for (List<FloorLayout> list : List.of(l.main, l.annex)) {
            for (FloorLayout f : list) {
                sb.append('F').append(f.area).append(f.floor).append(':').append(f.w).append('x').append(f.h).append('|');
                for (Room r : f.rooms) {
                    sb.append(r.type).append(r.shape).append(r.gx).append(',').append(r.gz).append(',').append(r.gw).append(r.gh)
                            .append(r.dy).append('h').append(r.height).append('n').append(r.normals).append('e').append(r.elites);
                    for (Room.Door d : r.doors) sb.append('d').append(d.side).append(d.toRoom).append(d.y);
                    sb.append(';');
                }
            }
        }
        return sb.toString();
    }

    /** Hash of every block painted for the start and end chunk of each floor (cell 7). */
    static long paintHash(GateLayout l, int cell) {
        long h = 1469598103934665603L;
        for (FloorLayout f : l.main) {
            for (int id : new int[]{f.start, f.end}) {
                Room r = f.room(id);
                for (var e : paintRoomAt(l, f, r, cell).entrySet()) {
                    h = (h ^ e.getKey()) * 1099511628211L;
                    h = (h ^ e.getValue().hashCode()) * 1099511628211L;
                }
            }
        }
        return h;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    static Map<Long, BlockState> paintRoom(GateLayout l, FloorLayout f, Room r) {
        return paintRoomAt(l, f, r, 3);
    }

    /** Paints the chunks a room covers; keys are floor-local x, absolute y, floor-local z. */
    static Map<Long, BlockState> paintRoomAt(GateLayout l, FloorLayout f, Room r, int cell) {
        Map<Long, BlockState> out = new HashMap<>();
        for (int sx = r.gx; sx < r.gx + r.gw; sx++) {
            for (int sz = r.gz; sz < r.gz + r.gh; sz++) {
                int chunkX = (Cells.originX(cell) >> 4) + f.originSlotX + sx;
                int chunkZ = (Cells.originZ(cell) >> 4) + f.originSlotZ + sz;
                int bx0 = sx * 16, bz0 = sz * 16;
                GatePainter.paint(l, cell, chunkX, chunkZ, (x, y, z, s) -> {
                    if (f.paints(y)) out.put(key(bx0 + x, y, bz0 + z), s);
                });
            }
        }
        return out;
    }

    /**
     * The test plot's surface is wherever the world put it, sometimes water: a gate never forms on wet ground ("no dry
     * ground at the chosen spot"), so the tests stand it on a dry block of their own with air over it.
     */
    static BlockPos dryGround(net.minecraft.server.level.ServerLevel level, BlockPos at) {
        level.setBlock(at, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (int dy = 1; dy <= 3; dy++) level.setBlock(at.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        return at;
    }
}
