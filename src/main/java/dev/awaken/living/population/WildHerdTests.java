package dev.awaken.living.population;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.PlayDays;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.api.Wildness;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** The Wild Lands' herds and packs (SPEC §41.2): the ledger's second version, a herd's senses and flight, a pack's hunt, the cost. */
public final class WildHerdTests {
    private WildHerdTests() {}

    public static void register() {
        AwakenTests.register("wild_ledger", 100, WildHerdTests::ledger);
        AwakenTests.registerIsolated("wild_herd_senses", 200, WildHerdTests::senses);
        AwakenTests.registerIsolated("wild_herd_flee", 400, WildHerdTests::flee);
        AwakenTests.registerIsolated("wild_pack_hunt", 200, WildHerdTests::packHunt);
        AwakenTests.registerIsolated("wild_herd_bench", 400, WildHerdTests::bench);
        AwakenTests.registerIsolated("wild_spoor", 100, WildHerdTests::spoor);
        AwakenTests.registerIsolated("wild_tracking", 100, WildHerdTests::tracking);
        AwakenTests.registerIsolated("wild_hunt", 100, WildHerdTests::hunt);
    }

    /** Sizes by band, old saves loading as they were, and a cell made in the wild holding herds and a pack. */
    private static void ledger(GameTestHelper h) {
        RandomSource r = RandomSource.create(9);
        double last = 0;
        for (Wildness.Band b : Wildness.Band.values()) {
            double sum = 0;
            for (int i = 0; i < 200; i++) {
                int n = Herds.herdSize(b, r);
                h.assertTrue(n >= Herds.HERD[b.ordinal()][0] && n <= Herds.HERD[b.ordinal()][1] && n <= Herds.HERD_CAP, "a herd's size in its band");
                sum += n;
            }
            h.assertTrue(sum / 200 > last, "wilder land, bigger herds: " + b);
            last = sum / 200;
        }
        h.assertTrue(Herds.packSize(Wildness.Band.TAMED, r) == 0 && Herds.odds(Wildness.Band.TAMED) == 0, "no herds or packs of the ledger's in tamed land");
        h.assertTrue(Herds.flight(Wildness.Band.DEEP) > Herds.flight(Wildness.Band.PASTURE), "wilder herds are warier");
        h.assertTrue(Herds.type("minecraft:cow") == EntityTypes.COW && Herds.type(Herds.WOLF) == EntityTypes.WOLF, "vanilla species by key");

        // a population saved before the Wild Lands loads with its old size and cap; a new one comes back whole
        var json = com.google.gson.JsonParser.parseString("{\"id\":3,\"kind\":\"herd\",\"species\":\"mossback\",\"hx\":0,\"hz\":0,\"count\":7,\"max\":12}");
        Ledger.Pop old = Ledger.Pop.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(old.wild < 0 && !old.alpha && old.cap() == 12, "a schema 1 herd keeps its cap of 12");
        Ledger.Pop now = new Ledger.Pop(4, "herd", "minecraft:cow", 0, 0, List.of(0, 0, 1, 1, 2, 2), 22, 22, 0, 5L, -1, 0.82F, true);
        Ledger.Pop back = Ledger.Pop.CODEC.parse(JsonOps.INSTANCE, Ledger.Pop.CODEC.encodeStart(JsonOps.INSTANCE, now).getOrThrow()).getOrThrow();
        h.assertTrue(back.wild == 0.82F && back.alpha && back.cap() == Herds.HERD_CAP && back.band() == Wildness.Band.DEEP, "a schema 2 herd round-trips");

        // a cell far out, where nobody tames the land: herds sized by their sites' bands, and a pack on the wildest
        ServerLevel level = h.getLevel();
        Ledger l = new Ledger();
        int herds = 0, packs = 0;
        for (int c = 0; c < 6; c++) {
            Ledger.Cell cell = l.generate(level, 3000 + c * 7, -3000);
            for (Ledger.Pop p : cell.pops()) {
                if (p.kind.equals("herd")) {
                    herds++;
                    int[] s = Herds.HERD[p.band().ordinal()];
                    h.assertTrue(p.band() != Wildness.Band.TAMED && p.count >= s[0] && p.count <= s[1], "a herd sized by its land: " + p.count + " in " + p.band());
                    h.assertTrue(Herds.type(p.species) != null, "a herd of a real species: " + p.species);
                    h.assertTrue(p.alpha == (p.band() == Wildness.Band.DEEP), "deep-wild herds have their bull");
                }
                if (p.kind.equals("pack")) {
                    packs++;
                    h.assertTrue(l.pop(p.herd) != null && p.count >= 2 && p.count <= Herds.PACK_CAP, "a pack trails a herd of its cell");
                }
            }
        }
        h.assertTrue(herds >= 6 && packs >= 3, "the open land holds herds (" + herds + ") and packs (" + packs + ")");
        h.succeed();
    }

    /** A walled floor of the test's own (room for a herd to run), its centre's absolute position. */
    private static BlockPos field(GameTestHelper h, int half) {
        BlockPos lo = AwakenTests.site(h, new BlockPos(-half, 0, -half), new BlockPos(half, 12, half), Blocks.GRASS_BLOCK.defaultBlockState());
        ServerLevel level = h.getLevel();
        for (int i = 0; i <= half * 2; i++) for (int y = 1; y <= 2; y++) {
            level.setBlockAndUpdate(lo.offset(i, y, 0), Blocks.BARRIER.defaultBlockState());
            level.setBlockAndUpdate(lo.offset(i, y, half * 2), Blocks.BARRIER.defaultBlockState());
            level.setBlockAndUpdate(lo.offset(0, y, i), Blocks.BARRIER.defaultBlockState());
            level.setBlockAndUpdate(lo.offset(half * 2, y, i), Blocks.BARRIER.defaultBlockState());
        }
        return lo.offset(half, 1, half);
    }

    private static Ledger.Pop herd(Ledger l, String species, int x, int z, int n, float wild) {
        Ledger.Pop p = new Ledger.Pop(l.nextId++, "herd", species, x, z, List.of(x, z, x, z, x, z), n, n, 10, 42L, -1, wild, false);
        l.add(new Ledger.Cell(Ledger.key(Math.floorDiv(x, Ledger.CELL) + l.nextId * 1000, 0), new ArrayList<>(List.of(p))));
        return p;
    }

    private static double meanDist(List<PathfinderMob> herd, double x, double z) {
        double d = 0;
        for (PathfinderMob m : herd) d += Math.hypot(m.getX() - x, m.getZ() - z);
        return d / herd.size();
    }

    /**
     * A herd's senses, one thought each: a crouched hunter downwind is let near; the same hunter on their feet is not; the
     * same hunter crouched upwind is smelled.
     */
    private static void senses(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 20);
        Ledger l = new Ledger();
        Ledger.Pop pop = herd(l, "minecraft:cow", c.getX(), c.getZ(), 8, 0.8F);
        h.assertTrue(l.materialize(level, pop, new double[]{c.getX(), c.getZ()}, 10) == 8, "eight cows");
        HerdBrain brain = l.herdBrain(pop.id);
        h.assertTrue(brain != null, "a herd with bodies has a brain");
        List<PathfinderMob> herd = l.living(level, pop.id);
        for (PathfinderMob m : herd) m.setNoAi(true); // they hold still: this checks what the brain decides
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            l.windField.set(level, 0F);
            double wx = l.windField.dirX, wz = l.windField.dirZ;
            double[] mid = {meanX(herd), meanZ(herd)};
            // ten blocks from the nearest cow, downwind (the wind blows from the herd to the hunter), crouched
            double far = 10 + maxReach(herd, mid);
            int now = pop.id;
            p.snapTo(mid[0] + wx * far, c.getY(), mid[1] + wz * far);
            p.setShiftKeyDown(true);
            p.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            l.windField.set(level, 0F);
            brain.think(l, level, 10, 6000, now);
            h.assertTrue(!brain.running(), "a crouched hunter downwind is let near: " + brain.mood);
            // on their feet at the same spot: inside the flight distance
            p.setShiftKeyDown(false);
            p.setPose(net.minecraft.world.entity.Pose.STANDING);
            brain.think(l, level, 10, 6000, now + 20);
            h.assertTrue(brain.running(), "on their feet, the hunter is seen and the herd runs: " + brain.mood);
            // a fresh herd mind, the hunter crouched upwind: the wind carries their scent to the herd
            HerdBrain fresh = new HerdBrain(pop);
            p.snapTo(mid[0] - wx * far, c.getY(), mid[1] - wz * far);
            p.setShiftKeyDown(true);
            p.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            fresh.think(l, level, 10, 6000, now + 40);
            h.assertTrue(fresh.running(), "crouched but upwind, the herd smells the hunter: " + fresh.mood);
        } finally {
            level.getServer().getPlayerList().remove(p);
            l.releaseAll(level);
        }
        h.succeed();
    }

    private static double meanX(List<PathfinderMob> l) {
        double s = 0;
        for (PathfinderMob m : l) s += m.getX();
        return s / l.size();
    }

    private static double meanZ(List<PathfinderMob> l) {
        double s = 0;
        for (PathfinderMob m : l) s += m.getZ();
        return s / l.size();
    }

    private static double maxReach(List<PathfinderMob> l, double[] mid) {
        double r = 0;
        for (PathfinderMob m : l) r = Math.max(r, Math.hypot(m.getX() - mid[0], m.getZ() - mid[1]));
        return r;
    }

    /** A wild herd of twenty stampedes from a sprinting hunter: well away within a few seconds, the near side first. */
    private static void flee(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 28);
        Ledger l = new Ledger();
        Ledger.Pop pop = herd(l, "minecraft:cow", c.getX() + 12, c.getZ(), 20, 0.8F);
        h.assertTrue(l.materialize(level, pop, new double[]{c.getX() + 12, c.getZ()}, 10) >= 18, "a herd of twenty");
        HerdBrain brain = l.herdBrain(pop.id);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.snapTo(c.getX() + 26, c.getY(), c.getZ());
        p.setSprinting(true);
        List<PathfinderMob> herd = l.living(level, pop.id);
        double start = meanDist(herd, p.getX(), p.getZ());
        int[] now = {pop.id - 1};
        boolean[] ran = {false};
        AwakenTests.onEnd(h, () -> {
            level.getServer().getPlayerList().remove(p);
            l.releaseAll(level);
        });
        h.succeedWhen(() -> {
            now[0]++;
            l.think(level, 10, 6000, now[0]);
            if (brain.mood == HerdBrain.Mood.STAMPEDE) ran[0] = true;
            double d = meanDist(l.living(level, pop.id), p.getX(), p.getZ());
            h.assertTrue(ran[0], "a big wild herd stampedes from a sprinter: " + brain.mood);
            h.assertTrue(d > start + 15, "the herd is well away: " + Math.round(d) + " from " + Math.round(start));
        });
    }

    /** A pack picks the straggler nearest it, the herd runs, and the kill costs the ledger's herd one. */
    private static void packHunt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 24);
        Ledger l = new Ledger();
        Ledger.Pop sheep = herd(l, "minecraft:sheep", c.getX() - 6, c.getZ(), 8, 0.6F);
        Ledger.Pop wolves = new Ledger.Pop(l.nextId++, "pack", Herds.WOLF, c.getX() + 16, c.getZ(), List.of(), 4, 4, 10, 7L, sheep.id, 0.6F, false);
        l.add(new Ledger.Cell(Ledger.key(-77, -77), new ArrayList<>(List.of(wolves))));
        h.assertTrue(l.materialize(level, sheep, new double[]{c.getX() - 6, c.getZ()}, 10) == 8, "eight sheep");
        h.assertTrue(l.materialize(level, wolves, new double[]{c.getX() + 16, c.getZ()}, 10) == 4, "four wolves");
        List<PathfinderMob> flock = l.living(level, sheep.id), pack = l.living(level, wolves.id);
        double px = meanX(pack), pz = meanZ(pack);
        PathfinderMob nearest = flock.getFirst();
        for (PathfinderMob m : flock) if (Math.hypot(m.getX() - px, m.getZ() - pz) < Math.hypot(nearest.getX() - px, nearest.getZ() - pz)) nearest = m;
        PackBrain brain = (PackBrain) l.brain(wolves.id);
        brain.chase(l, level, pack, flock, px, pz, 10, 100);
        h.assertTrue(brain.mood == PackBrain.Mood.CHASE && nearest.getUUID().equals(brain.prey), "the pack goes for the sheep nearest it");
        for (PathfinderMob w : pack) h.assertTrue(w.getTarget() == nearest, "every wolf on the one sheep");
        h.assertTrue(l.herdBrain(sheep.id).running(), "and the flock runs");
        int before = sheep.count;
        nearest.kill(level);
        l.died(nearest);
        h.assertTrue(sheep.count == before - 1, "the kill costs the flock one: " + sheep.count);
        h.assertTrue(brain.mood == PackBrain.Mood.FEED, "the pack feeds at the kill");
        boolean bones = false;
        for (Spoor.Mark m : Spoor.of(level).near(nearest.getX(), nearest.getZ(), 2)) {
            if (m.kind() == Spoor.Kind.KILL && m.pop() == wolves.id && m.prey().equals("minecraft:sheep")) bones = true;
        }
        h.assertTrue(bones, "the kill leaves bones that say wolves killed a sheep");
        l.releaseAll(level);
        h.succeed();
    }

    /**
     * Three herds of thirty and two packs near one person, grazing, then run off by a sprinter who leaves again: their minds
     * cost under half a millisecond a tick once warm, and no tick over ten (the path budget holds the wave back instead; the
     * first knock of a stampede warms the game's own damage code).
     */
    private static void bench(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            level.setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, true);
            level.getChunk((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz);
        }
        BlockPos c = field(h, 44);
        Ledger l = new Ledger();
        String[] kinds = {"minecraft:cow", "minecraft:sheep", "minecraft:pig"};
        List<Ledger.Pop> herds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Ledger.Pop p = herd(l, kinds[i], c.getX() - 20 + i * 20, c.getZ() - 14, 30, 0.85F);
            l.materialize(level, p, new double[]{p.hx, p.hz}, 10);
            herds.add(p);
        }
        for (int i = 0; i < 2; i++) {
            Ledger.Pop w = new Ledger.Pop(l.nextId++, "pack", Herds.WOLF, c.getX() - 14 + i * 28, c.getZ() + 24, List.of(), 6, 6, 10, 9L + i, herds.get(i).id, 0.85F, true);
            l.add(new Ledger.Cell(Ledger.key(-90 - i, -90), new ArrayList<>(List.of(w))));
            l.materialize(level, w, new double[]{w.hx, w.hz}, 10);
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        l.windField.set(level, 0F);
        double wx = l.windField.dirX, wz = l.windField.dirZ;
        // the hunter waits downwind (the herds can't smell them), sprints through, then leaves
        p.snapTo(c.getX() + wx * 40, c.getY(), c.getZ() + wz * 40);
        long[] spent = {0, 0};
        int[] ticks = {0};
        AwakenTests.onEnd(h, () -> {
            level.getServer().getPlayerList().remove(p);
            l.releaseAll(level);
        });
        h.succeedWhen(() -> {
            ticks[0]++;
            if (ticks[0] == 100) {
                p.snapTo(c.getX(), c.getY(), c.getZ() - 4); // runs in among them: the herds run
                p.setSprinting(true);
            }
            if (ticks[0] == 200) {
                p.snapTo(c.getX() + wx * 40, c.getY(), c.getZ() + wz * 40);
                p.setSprinting(false);
            }
            long t0 = System.nanoTime();
            l.think(level, 10, 6000, ticks[0]);
            long dt = System.nanoTime() - t0;
            if (ticks[0] > 40) { // past the JIT's warm-up
                spent[0] += dt;
                spent[1] = Math.max(spent[1], dt);
            }
            h.assertTrue(ticks[0] >= 340, "running");
            double ms = spent[0] / 1e6 / (ticks[0] - 40), worst = spent[1] / 1e6;
            dev.awaken.Awaken.LOG.info("[wild] herd bench: {} ms a tick, worst {} ms, over {} ticks", String.format("%.3f", ms), String.format("%.2f", worst), ticks[0] - 40);
            h.assertTrue(ms < AwakenTests.cpuBudget(0.5), "three herds of thirty and two packs: " + String.format("%.3f", ms) + " ms a tick");
            h.assertTrue(worst < AwakenTests.cpuBudget(10), "no tick over ten milliseconds: " + String.format("%.2f", worst));
        });
    }

    /**
     * Signs (SPEC §41.3): a herd that moves leaves prints pointing its way; a herd without bodies left prints along its path
     * and droppings at last night's bed near a player, each laid once.
     */
    private static void spoor(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 24);
        Ledger l = new Ledger();
        Ledger.Pop cows = herd(l, "minecraft:cow", c.getX() - 10, c.getZ(), 6, 0.6F);
        h.assertTrue(l.materialize(level, cows, new double[]{c.getX() - 10, c.getZ()}, 10) == 6, "six cows");
        HerdBrain brain = l.herdBrain(cows.id);
        List<PathfinderMob> herd = l.living(level, cows.id);
        brain.think(l, level, 10, 6000, 1);
        for (PathfinderMob m : herd) {
            m.snapTo(m.getX() + 8, m.getY(), m.getZ());
            m.setOnGround(true);
        }
        brain.think(l, level, 10, 6000, 21);
        Spoor.Mark prints = null;
        for (Spoor.Mark m : Spoor.of(level).near(c.getX(), c.getZ(), 24)) if (m.pop() == cows.id && m.kind() == Spoor.Kind.PRINTS) prints = m;
        h.assertTrue(prints != null && prints.count() == 6 && prints.hoofed(), "the herd's prints: six, hooves");
        h.assertTrue(Math.abs(Math.floorMod((int) prints.yaw() + 90 + 180, 360) - 180) < 5, "they point east, the way it went: " + prints.yaw());

        Ledger.Pop far = abstractHerd(l, c, 8);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.snapTo(c.getX(), c.getY(), c.getZ());
        AwakenTests.onEnd(h, () -> {
            level.getServer().getPlayerList().remove(p);
            l.releaseAll(level);
        });
        long now = level.getGameTime();
        Spoor.of(level).backfill(level, l, 10, 6000, now);
        int laid = 0, dung = 0;
        for (Spoor.Mark m : Spoor.of(level).near(c.getX(), c.getZ(), 40)) {
            if (m.pop() != far.id) continue;
            if (m.kind() == Spoor.Kind.PRINTS) {
                laid++;
                h.assertTrue(m.age(now) > 0 && m.age(now) <= Spoor.BACK_STEP * Spoor.BACK_STEPS, "a back-filled print is from the last two hours: " + m.age(now));
            }
            if (m.kind() == Spoor.Kind.DUNG) dung++;
        }
        h.assertTrue(laid >= 4, "the unseen herd left prints along its path: " + laid);
        h.assertTrue(dung == 1, "and its droppings at last night's bed: " + dung);
        int before = Spoor.of(level).near(c.getX(), c.getZ(), 40).size();
        Spoor.of(level).backfill(level, l, 10, 6000, now);
        h.assertTrue(Spoor.of(level).near(c.getX(), c.getZ(), 40).size() == before, "each is laid once");
        h.succeed();
    }

    /**
     * Reading and the trail (SPEC §41.3): crouching over a sign reads it (research one), two reads send the motes to the next
     * sign laid after them, a few more to the herd itself, and its bed goes on the Atlas.
     */
    private static void tracking(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 24);
        Ledger l = new Ledger();
        Ledger.Pop far = abstractHerd(l, c, 8);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.snapTo(c.getX(), c.getY(), c.getZ());
        dev.awaken.living.hunt.Tracking.useLedger(l);
        AwakenTests.onEnd(h, () -> {
            dev.awaken.living.hunt.Tracking.useLedger(null);
            dev.awaken.living.hunt.Tracking.forget(p);
            level.getServer().getPlayerList().remove(p);
        });
        long now = level.getGameTime();
        Spoor.of(level).backfill(level, l, 10, 6000, now);
        List<Spoor.Mark> trail = new ArrayList<>();
        for (Spoor.Mark m : Spoor.of(level).near(c.getX(), c.getZ(), 40)) if (m.pop() == far.id) trail.add(m);
        trail.sort(java.util.Comparator.comparingLong(Spoor.Mark::laid));
        h.assertTrue(trail.size() >= 6, "a trail to follow: " + trail.size());
        h.assertTrue(dev.awaken.living.hunt.Tracking.describe(trail.get(1), 0, now).startsWith("Hoof prints"), "a kind you don't know reads as hooves");
        h.assertTrue(dev.awaken.living.hunt.Tracking.describe(trail.get(1), 1, now).startsWith("Aurochs tracks"), "one you know by name");

        Spoor.Mark first = trail.getFirst();
        p.snapTo(first.x(), first.y(), first.z());
        p.setOnGround(true);
        p.setShiftKeyDown(true);
        for (int i = 0; i < 4; i++) dev.awaken.living.hunt.Tracking.crouch(level, p);
        var state = dev.awaken.living.hunt.Tracking.state(p);
        h.assertTrue(state.points(far.species) == 1, "crouched a second over it, the sign is read: research " + state.points(far.species));
        h.assertTrue(dev.awaken.living.hunt.Tracking.stage(p) == 1, "the trail begins");
        p.snapTo(c.getX(), c.getY(), c.getZ());
        dev.awaken.living.hunt.Tracking.read(level, p, trail.get(1));
        dev.awaken.living.hunt.Tracking.update(level, p);
        h.assertTrue(dev.awaken.living.hunt.Tracking.stage(p) == 2, "two read: the motes come");
        h.assertTrue(dev.awaken.living.hunt.Tracking.target(p) == trail.get(2).id(), "toward the next sign laid after them");
        for (int i = 2; i < 5; i++) dev.awaken.living.hunt.Tracking.read(level, p, trail.get(i));
        dev.awaken.living.hunt.Tracking.update(level, p);
        h.assertTrue(dev.awaken.living.hunt.Tracking.stage(p) == 3, "a few more: straight to the quarry");
        double[] at = l.where(far, PlayDays.current(level.getServer()), level.getOverworldClockTime()), motes = dev.awaken.living.hunt.Tracking.motes(p);
        h.assertTrue(Math.hypot(motes[0] - at[0], motes[2] - at[1]) < 1, "where the herd is now");
        h.assertTrue(!dev.awaken.living.hunt.Tracking.state(p).found().isEmpty(), "its bed is on the Atlas");
        h.succeed();
    }

    /** A herd with no bodies whose three pastures lie near (x, z): its signs are back-filled. */
    private static Ledger.Pop abstractHerd(Ledger l, BlockPos c, int n) {
        int x = c.getX(), z = c.getZ();
        Ledger.Pop p = new Ledger.Pop(l.nextId++, "herd", "minecraft:cow", x, z, List.of(x - 8, z - 4, x + 8, z, x, z + 8), n, n, 10, 43L, -1, 0.6F, false);
        l.add(new Ledger.Cell(Ledger.key(Math.floorDiv(x, Ledger.CELL), Math.floorDiv(z, Ledger.CELL)), new ArrayList<>(List.of(p))));
        return p;
    }

    /**
     * The hunt (SPEC §41.4): where blows land on a body; enough on the head breaks the horns (they drop, it staggers); at a
     * third of its health a herd's alpha limps off bleeding; its body lies on its side for its hunters to carve, three each.
     */
    private static void hunt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = field(h, 24);
        Ledger l = new Ledger();
        Ledger.Pop pop = new Ledger.Pop(l.nextId++, "herd", "minecraft:cow", c.getX(), c.getZ(), List.of(c.getX() - 20, c.getZ(), c.getX() - 20, c.getZ(),
                c.getX() - 20, c.getZ()), 3, 3, 10, 44L, -1, 0.9F, true);
        l.add(new Ledger.Cell(Ledger.key(-91, -91), new ArrayList<>(List.of(pop))));
        h.assertTrue(l.materialize(level, pop, new double[]{c.getX(), c.getZ()}, 10) == 3, "three cows");
        PathfinderMob bull = l.living(level, pop.id).getFirst();
        var tag = dev.awaken.core.api.CreatureTag.of(bull);
        bull.setData(dev.awaken.core.api.CoreApi.CREATURE_TAG.get(), new dev.awaken.core.api.CreatureTag(tag.level(), dev.awaken.core.api.CreatureTag.Kind.NEUTRAL, true, tag.title()));
        h.assertTrue(dev.awaken.living.hunt.Quarry.is(bull), "the herd's alpha is a quarry");
        ServerPlayer p = h.makeMockServerPlayerInLevel(), other = h.makeMockServerPlayerInLevel();
        p.snapTo(c.getX() - 3, c.getY(), c.getZ());
        dev.awaken.living.hunt.Tracking.useLedger(l);
        AwakenTests.onEnd(h, () -> {
            dev.awaken.living.hunt.Tracking.useLedger(null);
            level.getServer().getPlayerList().remove(p);
            level.getServer().getPlayerList().remove(other);
            l.releaseAll(level);
        });

        bull.setYBodyRot(0); // facing south (+z)
        double x = bull.getX(), y = bull.getY(), z = bull.getZ(), hgt = bull.getBbHeight(), half = bull.getBbWidth() / 2;
        h.assertTrue(dev.awaken.living.hunt.Quarry.zone(bull, new net.minecraft.world.phys.Vec3(x, y + hgt * 0.9, z + half)) == dev.awaken.living.hunt.Quarry.Zone.HEAD, "the front top is the head");
        h.assertTrue(dev.awaken.living.hunt.Quarry.zone(bull, new net.minecraft.world.phys.Vec3(x, y + hgt * 0.6, z - half)) == dev.awaken.living.hunt.Quarry.Zone.REAR, "the back end the rear");
        h.assertTrue(dev.awaken.living.hunt.Quarry.zone(bull, new net.minecraft.world.phys.Vec3(x, y + hgt * 0.1, z)) == dev.awaken.living.hunt.Quarry.Zone.LEGS, "low the legs");

        float max = bull.getMaxHealth();
        dev.awaken.living.hunt.Quarry.hit(level, bull, dev.awaken.living.hunt.Quarry.Zone.HEAD, max * 0.1F, p);
        h.assertTrue(dev.awaken.living.hunt.Quarry.state(bull).broken.isEmpty(), "a tenth isn't enough to break the horns");
        dev.awaken.living.hunt.Quarry.hit(level, bull, dev.awaken.living.hunt.Quarry.Zone.HEAD, max * 0.06F, p);
        h.assertTrue(dev.awaken.living.hunt.Quarry.state(bull).broken.contains("horns"), "the horns break");
        h.assertTrue(bull.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "it staggers");
        h.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, bull.getBoundingBox().inflate(3)).isEmpty(), "and the horn drops");
        h.assertTrue(dev.awaken.living.hunt.Quarry.state(bull).hunters.contains(p.getUUID()), "who struck it is its hunter");

        bull.setHealth(max * 0.3F);
        dev.awaken.living.hunt.Quarry.wounded(level, bull);
        h.assertTrue(bull.entityTags().contains(Ledger.LIMPING) && dev.awaken.living.hunt.Quarry.state(bull).limped, "at a third it limps off");
        bull.snapTo(bull.getX() + 4, bull.getY(), bull.getZ());
        bull.setOnGround(true);
        dev.awaken.living.hunt.Quarry.step(level);
        boolean blood = false;
        for (Spoor.Mark m : Spoor.of(level).near(bull.getX(), bull.getZ(), 2)) if (m.kind() == Spoor.Kind.BLOOD && m.pop() == pop.id) blood = true;
        h.assertTrue(blood, "and bleeds a trail you can read as its herd's");
        h.assertTrue(!bull.getNavigation().isDone(), "toward its bed");

        var body = dev.awaken.living.hunt.Carcass.lay(level, bull, java.util.Set.of(p.getUUID()));
        h.assertTrue(body != null && dev.awaken.living.hunt.Carcass.is(body) && body.isNoAi(), "its body lies still");
        h.assertTrue(!dev.awaken.living.hunt.Carcass.carve(level, other, body, true), "one who didn't fight it can't carve it");
        int before = p.getInventory().getContainerSize();
        for (int i = 0; i < 3; i++) h.assertTrue(dev.awaken.living.hunt.Carcass.carve(level, p, body, true), "carve " + (i + 1));
        int items = 0;
        for (int i = 0; i < before; i++) if (!p.getInventory().getItem(i).isEmpty()) items++;
        h.assertTrue(items >= 1, "the carves are in the hunter's pack");
        h.assertTrue(body.isRemoved(), "carved out, the body is gone");
        boolean bones = false;
        for (Spoor.Mark m : Spoor.of(level).near(body.getX(), body.getZ(), 1)) if (m.kind() == Spoor.Kind.KILL && m.species().equals(dev.awaken.living.hunt.Carcass.HUNTER)) bones = true;
        h.assertTrue(bones, "its bones stay as a sign");
        h.succeed();
    }
}
