package dev.awaken.descent;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.descent.entity.GraveEcho;
import dev.awaken.descent.player.DeathRules;
import dev.awaken.descent.player.DeepPack;
import dev.awaken.descent.player.DeepPacks;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.player.Strain;
import dev.awaken.descent.rules.DreamRooms;
import dev.awaken.descent.rules.Flood;
import dev.awaken.descent.rules.Foundry;
import dev.awaken.descent.rules.Zones;
import dev.awaken.descent.well.Well;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.world.Terrain;
import dev.awaken.progression.ProgressionModule;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for the descent module (pure logic and in-world behaviour). */
final class DescentTests {
    private DescentTests() {}

    static void register() {
        AwakenTests.register("descent_generator_determinism", 400, DescentTests::determinism);
        AwakenTests.register("descent_landmarks_and_helix", 400, DescentTests::landmarks);
        AwakenTests.register("descent_generation_is_cheap", 600, DescentTests::cheap);
        AwakenTests.register("descent_gating_by_rank", 40, DescentTests::gating);
        AwakenTests.register("descent_strain_maths", 40, DescentTests::strain);
        AwakenTests.register("descent_deep_pack_death_rules", 40, DescentTests::deathRules);
        AwakenTests.register("descent_deep_pack_insert", 40, DescentTests::packInsert);
        AwakenTests.register("descent_grave_echo_recover", 100, DescentTests::graveRecover);
        AwakenTests.register("descent_well_ring_distance", 40, DescentTests::wellRing);
        AwakenTests.register("descent_waystone_travel_list", 40, DescentTests::travelList);
        AwakenTests.register("descent_foundry_puzzle", 40, DescentTests::foundry);
        AwakenTests.register("descent_rule_timers", 40, DescentTests::timers);
        AwakenTests.register("descent_endless_rules", 40, DescentTests::endless);
        AwakenTests.register("descent_dream_view", 40, DescentTests::dreamView);
        AwakenTests.register("descent_data_loads", 40, DescentTests::dataLoads);
        AwakenTests.register("descent_well_builds", 1200, DescentTests::wellBuilds);
    }

    /** The Well is placed once and built at server start: wait for it, then check its shape. */
    private static void wellBuilds(GameTestHelper h) {
        var server = h.getLevel().getServer();
        h.succeedWhen(() -> {
            dev.awaken.descent.well.WellData d = dev.awaken.descent.well.WellData.get(server);
            h.assertTrue(d.built(), "the Well is not built yet");
            var level = server.overworld();
            double dist = Math.sqrt(Math.pow(d.x - d.spawnX, 2) + Math.pow(d.z - d.spawnZ, 2));
            h.assertTrue(dist >= Well.MIN_DISTANCE && dist <= Well.MAX_DISTANCE, "the Well is " + Math.round(dist) + " blocks from spawn");
            h.assertTrue(level.getBlockState(new BlockPos(d.x, d.y - 20, d.z)).isAir(), "the shaft is not open");
            h.assertTrue(level.getBlockState(new BlockPos(d.x + Well.RING_INNER + 1, d.y - 20, d.z)).is(DescentContent.WELL_STONE.get())
                    || level.getBlockState(new BlockPos(d.x + Well.RING_INNER + 1, d.y - 20, d.z)).is(DescentContent.WELL_GLYPH.get()),
                    "the shaft is not lined");
            h.assertTrue(level.getBlockState(new BlockPos(d.x + Well.RING_INNER + 3, d.y + 1, d.z)).is(DescentContent.WELL_LIFT.get()), "no lift on the rim");
            h.assertTrue(level.getBlockState(new BlockPos(d.x, d.bottom, d.z)).is(net.minecraft.world.level.block.Blocks.WATER), "no pool at the bottom");
            h.assertTrue(Well.inShaft(d, d.x + 0.5, d.y - 10, d.z + 0.5) && !Well.inShaft(d, d.x + 10.5, d.y - 10, d.z + 0.5),
                    "shaft detection");
        });
    }

    /**
     * The GameTest server never creates datapack dimensions, so decode ours here: every stratum's dimension JSON must
     * parse into our generator, and their dimension types and biomes must be registered.
     */
    private static void dataLoads(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        for (int n = 0; n <= Strata.COUNT; n++) {
            String name = n == 0 ? "endless" : "stratum_" + n;
            var id = dev.awaken.Awaken.id("dimension/" + name + ".json");
            var res = server.getResourceManager().getResource(id);
            h.assertTrue(res.isPresent(), "missing " + id);
            try (var reader = res.get().openAsReader()) {
                var json = com.google.gson.JsonParser.parseReader(reader);
                var stem = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json).getOrThrow();
                h.assertTrue(stem.generator() instanceof dev.awaken.descent.world.StratumGenerator gen && gen.stratum() == n,
                        name + " does not use the stratum generator");
                h.assertTrue(stem.type().value().minY() == Layout.MIN_Y && stem.type().value().height() == Layout.HEIGHT,
                        name + " has the wrong height");
            } catch (Exception e) {
                h.fail(name + ": " + e.getMessage());
            }
            if (n > 0) {
                var biome = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, dev.awaken.Awaken.id(name));
                h.assertTrue(server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).get(biome).isPresent(),
                        "biome " + name + " is not registered");
            }
        }
        h.succeed();
    }

    // ---- worldgen ---------------------------------------------------------------------------------------------------

    private static final int[][] AREAS = {{-16, -48}, {0, 0}, {32, -16}, {-48, 16}, {16, 32}};

    private static void determinism(GameTestHelper h) {
        long seed = 1234567L;
        Set<Long> perForm = new HashSet<>();
        for (int depth = 1; depth <= Strata.COUNT + 3; depth++) {
            int form = Strata.formOf(depth);
            Terrain a = new Terrain(form, seed * 31 + depth), b = new Terrain(form, seed * 31 + depth), c = new Terrain(form, seed * 31 + depth + 999);
            long combined = 0;
            boolean anyDiff = false;
            for (int[] area : AREAS) {
                long da = a.digest(area[0], area[1]), db = b.digest(area[0], area[1]);
                h.assertTrue(da == db, "stratum form " + form + " is not deterministic at " + area[0] + "," + area[1]);
                if (c.digest(area[0], area[1]) != da) anyDiff = true;
                combined = combined * 31 + da;
            }
            h.assertTrue(anyDiff, "form " + form + " ignores the seed");
            if (depth <= Strata.COUNT) h.assertTrue(perForm.add(combined), "form " + form + " looks the same as another stratum");
        }
        // the cached factory agrees with a fresh instance
        Terrain cached = Terrain.forDepth(3, seed), again = Terrain.forDepth(3, seed);
        h.assertTrue(cached.digest(0, 0) == again.digest(0, 0), "cached terrain differs");
        h.succeed();
    }

    private static boolean blocks(BlockState s) {
        return !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static boolean walkable(BlockState[] col, int y) {
        return y > 0 && y + 2 < col.length && blocks(col[y]) && !blocks(col[y + 1]) && !blocks(col[y + 2]);
    }

    private static void landmarks(GameTestHelper h) {
        long seed = 99L;
        for (int form = 1; form <= Strata.COUNT; form++) {
            Terrain t = new Terrain(form, seed + form);
            BlockPos e = Layout.entry(form);
            BlockState[] col = t.singleColumn(e.getX(), e.getZ()).clone();
            h.assertTrue(walkable(col, e.getY() - 1), "form " + form + ": arrival platform is not safe");
            BlockPos asc = Layout.ascent(form);
            col = t.singleColumn(asc.getX(), asc.getZ()).clone();
            h.assertTrue(col[asc.getY()].is(DescentContent.ASCENT_STONE.get()), "form " + form + ": no ascent stone");
            BlockPos w = Layout.waystone(form);
            col = t.singleColumn(w.getX(), w.getZ()).clone();
            h.assertTrue(col[w.getY()].is(DescentContent.WAYSTONE.get()), "form " + form + ": no waystone at the camp");
            BlockPos pit = Layout.pit(form);
            col = t.singleColumn(pit.getX(), pit.getZ()).clone();
            h.assertTrue(col[pit.getY()].is(DescentContent.DESCENT_PIT.get()), "form " + form + ": no pit");
            if (form == 6) h.assertTrue(col[pit.getY() + 1].is(DescentContent.FOUNDRY_SEAL.get()), "the Foundry's pit is not sealed");
            BlockPos camp = Layout.campArrival(form);
            col = t.singleColumn(camp.getX(), camp.getZ()).clone();
            h.assertTrue(walkable(col, camp.getY() - 1), "form " + form + ": the camp arrival spot is not safe");
            BlockPos bed = Layout.bedFoot(form);
            col = t.singleColumn(bed.getX(), bed.getZ()).clone();
            h.assertTrue(col[bed.getY()].is(net.minecraft.world.level.block.Blocks.STRAW_BED), "form " + form + ": no bed at the camp");
            if (form != 7) helixWalk(h, t, form);
        }
        h.succeed();
    }

    /** Walks the spiral from the platform to the floor: there must always be footing with headroom, in 1-block steps. */
    private static void helixWalk(GameTestHelper h, Terrain t, int form) {
        int floor = Layout.floorY(form);
        int last = Layout.ENTRY_Y - 1;
        int samples = 0;
        int pitch = t.helixPitch(), w = t.helixWidth();
        for (double s = 0.02; ; s += 0.004) {
            int k = (int) Math.floor(s);
            double a = s - k;
            double yS = Layout.ENTRY_Y - 1 - pitch * (k + a);
            if (yS < floor + 2) break;
            double th = Layout.THETA0 + a * Math.PI * 2;
            // the footing a player would take: any column across the ramp, closest to where they were
            int found = Integer.MIN_VALUE;
            for (double rr = t.helixR(yS) - w + 1; rr <= t.helixR(yS); rr += 1.0) {
                int x = (int) Math.floor(Math.cos(th) * rr), z = (int) Math.floor(Math.sin(th) * rr);
                BlockState[] col = t.singleColumn(x, z);
                for (int y = (int) Math.floor(yS) + 2; y >= (int) Math.floor(yS) - 3; y--) {
                    if (walkable(col, y) && (found == Integer.MIN_VALUE || Math.abs(y - last) < Math.abs(found - last))) found = y;
                }
            }
            h.assertTrue(found != Integer.MIN_VALUE, "form " + form + ": the spiral has no footing at turn " + k + " angle " + String.format("%.2f", a));
            if (!(last - found <= 2 && found - last <= 2)) {
                StringBuilder dbg = new StringBuilder();
                double rr = t.helixR(yS) - w / 2.0;
                int x = (int) Math.floor(Math.cos(th) * rr), z = (int) Math.floor(Math.sin(th) * rr);
                BlockState[] col = t.singleColumn(x, z);
                for (int y = (int) yS + 3; y >= (int) yS - 5; y--) dbg.append(y).append('=').append(col[y].getBlock().getDescriptionId().replace("block.", "")).append(' ');
                h.fail("form " + form + ": the spiral jumps " + (last - found) + " blocks at turn " + k
                        + " (from y " + last + " to " + found + ", surface " + String.format("%.1f", yS) + ", angle " + String.format("%.3f", a)
                        + ") at " + x + "," + z + ": " + dbg);
            }
            last = found;
            samples++;
        }
        h.assertTrue(samples > 100, "form " + form + ": spiral too short");
    }

    private static void cheap(GameTestHelper h) {
        long t0 = System.nanoTime();
        int chunks = 0;
        for (int form = 1; form <= Strata.COUNT; form++) {
            Terrain t = new Terrain(form, 4242L + form);
            for (int[] area : AREAS) {
                t.digest(area[0], area[1]);
                chunks++;
            }
        }
        double msPerChunk = (System.nanoTime() - t0) / 1e6 / chunks;
        h.assertTrue(msPerChunk < 40, "generation too slow: " + String.format("%.1f", msPerChunk) + " ms per chunk");
        h.succeed();
    }

    // ---- gating, strain, death --------------------------------------------------------------------------------------

    private static void gating(GameTestHelper h) {
        h.assertTrue(Strata.access(Rank.E, 1, false, 1) == Strata.Access.OPEN, "the Rim must always be open");
        h.assertTrue(Strata.access(Rank.E, 9, false, 2) == Strata.Access.SEALED_RANK, "Gardens open at D");
        h.assertTrue(Strata.access(Rank.D, 10, false, 3) == Strata.Access.OPEN, "Choir opens at D");
        h.assertTrue(Strata.access(Rank.D, 20, false, 4) == Strata.Access.SEALED_RANK, "Orchard opens at C");
        h.assertTrue(Strata.access(Rank.C, 25, false, 5) == Strata.Access.OPEN, "Silent Halls open at C");
        h.assertTrue(Strata.access(Rank.C, 40, false, 6) == Strata.Access.SEALED_RANK, "Foundry opens at B");
        h.assertTrue(Strata.access(Rank.B, 45, false, 7) == Strata.Access.OPEN, "Inverted Sea opens at B");
        h.assertTrue(Strata.access(Rank.B, 60, false, 8) == Strata.Access.SEALED_RANK, "Dreaming opens at A");
        h.assertTrue(Strata.access(Rank.A, 99, false, 9) == Strata.Access.SEALED_RANK, "Hollow Court opens at S");
        h.assertTrue(Strata.access(Rank.S, 100, false, 9) == Strata.Access.OPEN, "Hollow Court opens at S");
        h.assertTrue(Strata.access(Rank.S, 120, false, 10) == Strata.Access.SEALED_LEVEL, "the Seed needs the Sovereign level");
        h.assertTrue(Strata.access(Rank.S, 135, false, 10) == Strata.Access.OPEN, "the Seed opens at S level 135");
        h.assertTrue(Strata.access(Rank.SOVEREIGN, 140, false, 11) == Strata.Access.SEALED_SEED, "the Endless needs the Seed");
        h.assertTrue(Strata.access(Rank.SOVEREIGN, 140, true, 12) == Strata.Access.OPEN, "the Endless opens after the Seed");
        // content level climbs with depth, stratum n = rank index min(6, (n+1)/2)
        h.assertTrue(Strata.contentLevel(1) == Rank.D.contentLevel && Strata.contentLevel(3) == Rank.C.contentLevel
                && Strata.contentLevel(10) == Rank.S.contentLevel, "stratum content levels");
        for (int n = 2; n <= 14; n++) h.assertTrue(Strata.contentLevel(n) >= Strata.contentLevel(n - 1), "content level must not drop");
        h.succeed();
    }

    private static void strain(GameTestHelper h) {
        h.assertTrue(close(Strain.next(0, 1), 0.05), "one stratum = 5%");
        h.assertTrue(close(Strain.next(0.05, 2), 0.15), "stacking");
        h.assertTrue(close(Strain.next(0.3, 5), 0.4), "cap at 40%");
        h.assertTrue(close(Strain.next(0.2, 0), 0.2), "no climb, no strain");
        h.assertTrue(close(Strain.next(0.2, -3), 0.2), "going down never strains");
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setData(ProgressionModule.STRAIN.get(), 0.0);
        Strain.climb(p, 5, 0);
        h.assertTrue(close(p.getData(ProgressionModule.STRAIN.get()), 0.25), "climbing 5 strata from stratum 5 = 25%");
        Strain.climb(p, 5, 1);
        h.assertTrue(close(p.getData(ProgressionModule.STRAIN.get()), 0.4), "strain caps at 40%");
        Strain.climb(p, 2, 6);
        h.assertTrue(close(p.getData(ProgressionModule.STRAIN.get()), 0.4), "descending adds nothing");
        Strain.clear(p);
        h.assertTrue(close(p.getData(ProgressionModule.STRAIN.get()), 0), "clear");
        h.assertTrue(close(DescentWaystones.strainFor(5, 1), 0.2) && close(DescentWaystones.strainFor(1, 5), 0)
                && close(DescentWaystones.strainFor(4, 0), 0.2), "travel strain");
        h.succeed();
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static List<ItemStack> samplePack() {
        List<ItemStack> l = new ArrayList<>();
        l.add(new ItemStack(Items.IRON_INGOT, 10));
        l.add(new ItemStack(Items.DIAMOND, 3));
        l.add(new ItemStack(Items.DIAMOND_SWORD));
        l.add(new ItemStack(Items.BOW));
        l.add(new ItemStack(Items.SHIELD));
        return l;
    }

    private static int count(List<ItemStack> l) {
        int n = 0;
        for (ItemStack s : l) n += s.getCount();
        return n;
    }

    private static void deathRules(GameTestHelper h) {
        List<ItemStack> pack = samplePack();
        int total = count(pack);
        DeathRules.Split story = DeathRules.split(AwakenConfig.Difficulty.STORY, pack);
        h.assertTrue(count(story.kept()) + count(story.echo()) == total, "STORY must not lose or copy items");
        h.assertTrue(count(story.kept()) == 5 + 2 + 2, "STORY keeps half of each stack (rounded up) and alternates unstackables");
        for (var diff : new AwakenConfig.Difficulty[]{AwakenConfig.Difficulty.HUNTER, AwakenConfig.Difficulty.HARDLINE, AwakenConfig.Difficulty.HARDCORE}) {
            DeathRules.Split s = DeathRules.split(diff, pack);
            h.assertTrue(s.kept().isEmpty() && count(s.echo()) == total, diff + " leaves everything in the Grave Echo");
        }
        h.assertTrue(count(pack) == total, "splitting must not change the pack");
        h.assertTrue(DeathRules.maxEchoes(AwakenConfig.Difficulty.HARDLINE) == 1 && DeathRules.maxEchoes(AwakenConfig.Difficulty.HUNTER) == 3,
                "echo limits");
        h.succeed();
    }

    private static void packInsert(GameTestHelper h) {
        DeepPack pack = new DeepPack();
        ItemStack cobble = new ItemStack(Items.COBBLESTONE, 40);
        h.assertTrue(pack.insert(cobble) == 40 && cobble.isEmpty(), "insert moves the stack");
        ItemStack more = new ItemStack(Items.COBBLESTONE, 40);
        pack.insert(more);
        h.assertTrue(pack.used() == 2 && pack.totalItems() == 80, "stacks merge before using new slots");
        for (int i = 0; i < 40; i++) pack.insert(new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(pack.used() == DeepPack.SIZE, "27 slots");
        ItemStack extra = new ItemStack(Items.BOW);
        h.assertTrue(pack.insert(extra) == 0 && extra.getCount() == 1, "a full pack refuses");
        DeepPack copy = DeepPack.of(pack.stacks());
        h.assertTrue(copy.totalItems() == pack.totalItems(), "round trip");
        h.assertTrue(pack.takeAll().size() == DeepPack.SIZE && pack.isEmpty(), "takeAll empties");
        h.succeed();
    }

    private static void graveRecover(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        GraveEcho echo = GraveEcho.create(h.getLevel(), at, p, samplePack());
        h.assertTrue(echo != null && count(echo.contents()) == count(samplePack()), "the Grave Echo holds the pack");
        DescentData d = DescentSync.data(p);
        d.graves.add(new DescentData.Grave(h.getLevel().dimension().identifier().toString(), at, echo.getUUID(), 3));
        int before = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) before += p.getInventory().getItem(i).getCount();
        DeepPacks.recover(p, echo);
        int after = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) after += p.getInventory().getItem(i).getCount();
        h.assertTrue(after - before == count(samplePack()), "outside the Descent, recovered loot goes to the inventory");
        h.assertTrue(echo.isRemoved() && d.graves.isEmpty(), "the Grave Echo is gone once recovered");
        h.succeed();
    }

    // ---- the Well, travel, puzzles, timers --------------------------------------------------------------------------

    private static void wellRing(GameTestHelper h) {
        for (long seed = 0; seed < 64; seed++) {
            int sx = (int) (seed * 37 % 2000) - 1000, sz = (int) (seed * 91 % 2000) - 1000;
            for (BlockPos c : Well.candidates(seed * 7919, sx, sz, 32)) {
                double d = Math.sqrt(Math.pow(c.getX() - sx, 2) + Math.pow(c.getZ() - sz, 2));
                h.assertTrue(d - Well.RING_OUTER >= Well.MIN_DISTANCE && d + Well.RING_OUTER <= Well.MAX_DISTANCE,
                        "Well site at " + Math.round(d) + " blocks is outside 400-700");
            }
        }
        List<BlockPos> a = Well.candidates(5, 0, 0, 8), b = Well.candidates(5, 0, 0, 8);
        h.assertTrue(a.equals(b), "Well sites must be deterministic");
        h.succeed();
    }

    private static void travelList(GameTestHelper h) {
        Set<Integer> lit = new java.util.TreeSet<>(List.of(1, 3, 5));
        h.assertTrue(DescentWaystones.destinations(lit, 3).equals(List.of(0, 1, 5)), "from stratum 3: the Well, 1 and 5");
        h.assertTrue(DescentWaystones.destinations(lit, 0).equals(List.of(1, 3, 5, DescentTravel.RIM_ENTRANCE)), "from the Well");
        h.assertTrue(DescentWaystones.destinations(Set.of(), 2).equals(List.of(0)), "unlit: only the Well");
        h.assertTrue(DescentWaystones.destinations(new java.util.TreeSet<>(List.of(12, 4)), 12).equals(List.of(0, 4)), "Endless camps");
        h.succeed();
    }

    private static void foundry(GameTestHelper h) {
        boolean[] lit = new boolean[Layout.CONDUITS];
        for (int i = 0; i < Layout.CONDUITS; i++) lit = Foundry.press(lit, i);
        h.assertTrue(Foundry.solved(lit), "pressing every conduit once lights the ring");
        // every pattern of the ring can be solved (breadth-first over 32 states)
        boolean[] seen = new boolean[1 << Layout.CONDUITS];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        int goal = (1 << Layout.CONDUITS) - 1;
        q.add(goal);
        seen[goal] = true;
        while (!q.isEmpty()) {
            int s = q.poll();
            for (int i = 0; i < Layout.CONDUITS; i++) {
                int n = s;
                for (int k = -1; k <= 1; k++) n ^= 1 << Math.floorMod(i + k, Layout.CONDUITS);
                if (!seen[n]) { seen[n] = true; q.add(n); }
            }
        }
        for (boolean b : seen) h.assertTrue(b, "a conduit pattern can't be solved");
        h.succeed();
    }

    private static void timers(GameTestHelper h) {
        h.assertTrue(Flood.delta(0) == 0 && Flood.delta(Flood.HOLD) == Flood.MAX_DELTA && Flood.delta(Flood.CYCLE - 1) <= 1
                && Flood.delta(Flood.CYCLE + 100) == 0, "flood cycle");
        h.assertTrue(Flood.delta(Flood.RISE + 200) > 0 && Flood.delta(Flood.RISE + 200) < Flood.MAX_DELTA, "flood rises gradually");
        h.assertTrue(Zones.tidePhase(0) == 0 && Zones.tidePhase(Zones.TIDE_SWELL) == 1 && Zones.tidePhase(Zones.TIDE_INVERT) == 2
                && Zones.tidePhase(Zones.TIDE_EBB) == 3 && Zones.tidePhase(Zones.TIDE_CALM) == 0, "tide phases");
        h.assertTrue(Flood.baseFor(3, 50) == 47 && Flood.baseFor(3, 25) == Layout.floorY(3) && Flood.baseFor(3, 120) == Integer.MIN_VALUE,
                "flood bodies of the Drowned Choir");
        h.succeed();
    }

    private static void endless(GameTestHelper h) {
        for (int d = 1; d <= 200; d++) {
            int depth = Strata.ENDLESS_BASE + d;
            int rules = Strata.rulesOf(depth);
            int form = Strata.endlessForm(d);
            h.assertTrue(form >= 1 && form <= 9, "endless form");
            h.assertTrue(Integer.bitCount(rules) >= 1 && Integer.bitCount(rules) <= 3, "endless depth " + d + " has " + Integer.bitCount(rules) + " rules");
            if (form >= 2) h.assertTrue(Strata.has(rules, form), "endless depth keeps its form's rule");
            h.assertTrue(!Strata.has(rules, Strata.RULE_ROOMS) || form == 8, "rooms only where there are dream rooms");
            h.assertTrue(!Strata.has(rules, Strata.RULE_MACHINES) || form == 6, "machines only in a foundry");
            int x = Strata.cellCenterX(d), z = Strata.cellCenterZ(d);
            h.assertTrue(Strata.endlessCellAt(x, z) == d && Strata.endlessCellAt(x + 400, z - 400) == d, "cell lookup");
        }
        h.assertTrue(Strata.rulesOf(1) == 0 && Strata.rulesOf(5) == 1 << Strata.RULE_LISTENERS, "strata have their own rule");
        h.succeed();
    }

    private static void dreamView(GameTestHelper h) {
        Vec3 look = new Vec3(1, 0, 0);
        h.assertTrue(DreamRooms.viewDot(look, new Vec3(10, 0, 1)) > 0.9, "in front is observed");
        h.assertTrue(DreamRooms.viewDot(look, new Vec3(-10, 0, 0)) < 0, "behind is not observed");
        Terrain t = new Terrain(8, 77L);
        for (int room = 0; room < Terrain.roomCount(); room += 7) {
            BlockPos c = t.roomCenter(room);
            h.assertTrue(t.roomAt(c.getX(), c.getY(), c.getZ()) == room, "room centre maps to its room");
        }
        h.succeed();
    }
}
