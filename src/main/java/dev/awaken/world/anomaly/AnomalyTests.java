package dev.awaken.world.anomaly;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** GameTests for anomalies (tools/gradle.sh 1200 runGameTestServer, or /test run awaken:anomaly_*). */
final class AnomalyTests {
    private AnomalyTests() {}

    static void register() {
        pacing();
        eligibility();
        avoidTag();
        spots();
        directorPauses();
        riftPaysOnce();
        riftIgnored();
        roundTrip();
        recoverAfterRestart();
        processionCache();
        wanderingGate();
        AnomalyP2Tests.register();
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /** A clean stone floor (radius 7) with open air above, well away from the test's own blocks. Returns the centre. */
    static BlockPos platform(GameTestHelper h, int dx, int dz) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(dx, 3, dz));
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -3; y <= 0; y++) level.setBlock(c.offset(x, y, z), stone, 3);
                for (int y = 1; y <= 7; y++) level.setBlock(c.offset(x, y, z), air, 3);
            }
        }
        return c;
    }

    static ServerPlayer awakenedPlayer(GameTestHelper h, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, Set.of(), 0, 0, true);
        AnomalyMemory mem = AnomalyDirector.memory(p);
        mem.awakenedAt = h.getLevel().getGameTime() - Pacing.GRACE - 1; // past the grace
        return p;
    }

    /** Total XP a profile has earned, so a level-up doesn't hide a grant. */
    static double totalXp(Profile p) {
        double t = p.xp();
        for (int lv = 1; lv < p.level(); lv++) t += Curve.xpToNext(lv);
        return t;
    }

    static int shardsNear(ServerLevel level, BlockPos at) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(10))) {
            if (e.getItem().is(CoreApi.ESSENCE_SHARD.get())) n += e.getItem().getCount();
        }
        return n;
    }

    static void discardAll(ServerLevel level, List<UUID> ids) {
        for (UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
    }

    static void discardItems(ServerLevel level, BlockPos at) {
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(12))) e.discard();
    }

    // ---- pure logic ---------------------------------------------------------------------------------------------------

    static void pacing() {
        AwakenTests.register("anomaly_pacing_and_kinds", 20, h -> {
            RandomSource r = RandomSource.create(42);
            for (int i = 0; i < 300; i++) {
                int f = Pacing.firstDelay(r), n = Pacing.nextDelay(r);
                h.assertTrue(f >= 3 * Pacing.MINUTE && f <= 6 * Pacing.MINUTE, "first delay 3–6 min: " + f);
                h.assertTrue(n >= 6 * Pacing.MINUTE && n <= 12 * Pacing.MINUTE, "next delay 6–12 min: " + n);
            }
            double day = meanGap(false, r), night = meanGap(true, r);
            h.assertTrue(day >= 8.0 && day <= 10.0, "by day one roughly every 9 minutes, got " + day);
            h.assertTrue(night >= 5.3 && night <= 6.7, "at night 1.5× as often (~6 min), got " + night);
            h.assertTrue(Pacing.step(100, 20, true) == 70 && Pacing.step(100, 20, false) == 80, "night runs the clock 1.5× faster");
            // kinds
            int[] first = Pacing.weights(true, false, false, 2, "");
            h.assertTrue(first[0] > 0 && first[1] == 0 && first[2] == 0, "the first one is always a rift");
            int[] night1 = Pacing.weights(false, true, false, 10, "");
            h.assertTrue(night1[2] == 0 && night1[0] > 0, "the calm event is day only");
            int[] peaceful = Pacing.weights(false, false, true, 10, "");
            h.assertTrue(peaceful[0] == 0 && peaceful[2] > 0, "no rifts on peaceful");
            int[] young = Pacing.weights(false, false, false, 2, "");
            h.assertTrue(young[1] == 0, "no wandering gates before level 3");
            int[] again = Pacing.weights(false, false, false, 10, AnomalyKind.SPIRIT_PROCESSION.id);
            h.assertTrue(again[2] == 0, "the calm event never twice in a row");
            h.assertTrue(Pacing.pick(new int[] {0, 0, 0}, r) == null, "nothing fits → nothing");
            int calm = 0;
            for (int i = 0; i < 1000; i++) {
                AnomalyKind k = Pacing.pick(Pacing.weights(false, false, false, 10, ""), r);
                if (k == AnomalyKind.SPIRIT_PROCESSION || k == AnomalyKind.SHIMMER_GROVE) calm++;
            }
            h.assertTrue(calm > 380 && calm < 560, "nearly half of the day's anomalies are calm (a procession or a grove): " + calm);
            // words
            h.assertTrue(Spots.direction(BlockPos.ZERO, new BlockPos(0, 0, -10)).equals("N") && Spots.direction(BlockPos.ZERO, new BlockPos(10, 0, 0)).equals("E")
                    && Spots.direction(BlockPos.ZERO, new BlockPos(30, 0, -30)).equals("NE"), "compass: north is -Z, east +X");
            h.assertTrue(Spots.where(BlockPos.ZERO, new BlockPos(30, 5, -40)).equals("50 m NE"), "where: " + Spots.where(BlockPos.ZERO, new BlockPos(30, 5, -40)));
            h.succeed();
        });
    }

    /** Simulated outdoor minutes between anomalies when each ends at once (the countdown alone). */
    static double meanGap(boolean night, RandomSource r) {
        double timer = Pacing.nextDelay(r);
        long ticks = 0, last = 0;
        int events = 0;
        double gaps = 0;
        while (events < 400) {
            ticks += 20;
            timer = Pacing.step(timer, 20, night);
            if (timer <= 0) {
                gaps += (ticks - last) / (double) Pacing.MINUTE;
                last = ticks;
                events++;
                timer = Pacing.nextDelay(r);
            }
        }
        return gaps / events;
    }

    static void eligibility() {
        AwakenTests.register("anomaly_eligibility_rules", 20, h -> {
            Eligibility.Facts ok = new Eligibility.Facts(true, true, true, false, false, false, Long.MAX_VALUE, false, false);
            h.assertTrue(Eligibility.blocked(ok) == null, "an awakened player outdoors in the overworld is eligible");
            check(h, new Eligibility.Facts(false, true, true, false, false, false, Long.MAX_VALUE, false, false), "switched off");
            check(h, new Eligibility.Facts(true, false, true, false, false, false, Long.MAX_VALUE, false, false), "not awakened");
            check(h, new Eligibility.Facts(true, true, false, false, true, false, Long.MAX_VALUE, false, false), "in a gate run");
            check(h, new Eligibility.Facts(true, true, true, false, true, false, Long.MAX_VALUE, false, false), "in a gate run");
            check(h, new Eligibility.Facts(true, true, false, false, false, false, Long.MAX_VALUE, false, false), "not in the overworld");
            check(h, new Eligibility.Facts(true, true, true, true, false, false, Long.MAX_VALUE, false, false), "spectating");
            check(h, new Eligibility.Facts(true, true, true, false, false, true, Long.MAX_VALUE, false, false), "asleep");
            check(h, new Eligibility.Facts(true, true, true, false, false, false, Pacing.GRACE - 1, false, false), "just awakened");
            h.assertTrue(Eligibility.blocked(new Eligibility.Facts(true, true, true, false, false, false, Pacing.GRACE, false, false)) == null,
                    "two minutes after awakening it may start");
            check(h, new Eligibility.Facts(true, true, true, false, false, false, Long.MAX_VALUE, true, false), "at home");
            check(h, new Eligibility.Facts(true, true, true, false, false, false, Long.MAX_VALUE, false, true), "one anomaly is already active");
            h.succeed();
        });
    }

    private static void check(GameTestHelper h, Eligibility.Facts f, String expect) {
        String got = Eligibility.blocked(f);
        h.assertTrue(got != null && got.contains(expect), "expected '" + expect + "', got " + got);
    }

    static void avoidTag() {
        AwakenTests.register("anomaly_avoid_tag_has_builds", 20, h -> {
            for (var b : List.of(Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.BRICKS, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED), Blocks.OAK_DOOR, Blocks.TORCH, Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE),
                    Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.OAK_FENCE, Blocks.GLASS_PANE)) {
                h.assertTrue(b.defaultBlockState().is(Spots.AVOID), b + " counts as a build");
            }
            for (var b : List.of(Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.DIRT, Blocks.SAND, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.SHORT_GRASS)) {
                h.assertFalse(b.defaultBlockState().is(Spots.AVOID), b + " is natural");
            }
            h.succeed();
        });
    }

    // ---- spots --------------------------------------------------------------------------------------------------------

    static void spots() {
        AwakenTests.registerIsolated("anomaly_spot_rejects_builds_water_hearth", 2400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos g = platform(h, 12, 12);
            // a player standing there keeps the chunks simulated, as in play
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.teleportTo(level, g.getX() - 20.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
            // let the force-loaded chunks settle into entity-ticking before checking "loaded ground"
            h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the platform ticks"))
                    .thenExecute(() -> spotChecks(h, level, g)).thenSucceed();
        });
    }

    private static void spotChecks(GameTestHelper h, ServerLevel level, BlockPos g) {
        {
            h.assertTrue(Spots.checkGround(level, g) == null, "clean natural ground is fine: " + Spots.checkGround(level, g));
            expectGround(h, g, g.offset(3, 1, 2), Blocks.OAK_PLANKS.defaultBlockState(), "builds");
            expectGround(h, g, g.offset(-4, 2, 4), Blocks.GLASS.defaultBlockState(), "builds");
            expectGround(h, g, g.offset(5, 1, -5), Blocks.BRICKS.defaultBlockState(), "builds");
            expectGround(h, g, g.offset(2, 1, 0), Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState(), "builds");
            expectGround(h, g, g.offset(-4, 1, 0), Blocks.CHEST.defaultBlockState(), "block entity");
            expectGround(h, g, g.offset(2, 0, 1), Blocks.WATER.defaultBlockState(), "water");
            expectGround(h, g, g, Blocks.WATER.defaultBlockState(), "water");
            // a pond five blocks off is not "wet ground"
            level.setBlock(g.offset(5, 0, 0), Blocks.WATER.defaultBlockState(), 3);
            h.assertTrue(Spots.checkGround(level, g) == null, "water 5 blocks away is fine: " + Spots.checkGround(level, g));
            level.setBlock(g.offset(5, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            // a roof over the spot
            expectGround(h, g, g.above(2), Blocks.STONE.defaultBlockState(), "cramped");
            // the outside rules: distance, height, Hearth
            // gates left standing by other tests that used this spot before aren't what's tested here
            Spots.Rules noHearth = Spots.Rules.NORMAL.hearth((l, p, r) -> false).gates(false);
            Spots.Rules hearth = Spots.Rules.NORMAL.hearth((l, p, r) -> true).gates(false);
            BlockPos from = g.offset(-40, 0, 0);
            h.assertTrue(Spots.check(level, from, g, noHearth, null) == null, "40 blocks away on clean ground: " + Spots.check(level, from, g, noHearth, null));
            h.assertTrue("hearth".equals(Spots.check(level, from, g, hearth, null)), "never inside a Hearth's reach");
            h.assertTrue("too close".equals(Spots.check(level, g.offset(-10, 0, 0), g, noHearth, null)), "not on top of the player");
            h.assertTrue("too far".equals(Spots.check(level, g.offset(-120, 0, 0), g, noHearth, null)), "not a kilometre off");
            h.assertTrue("too steep".equals(Spots.check(level, g.offset(-40, 40, 0), g, noHearth, null)), "not on a cliff far above");
            // another anomaly close by
            AnomalyData data = new AnomalyData();
            data.add(new Anomaly(1, AnomalyKind.RIFT_TEAR, UUID.randomUUID(), g.offset(20, 0, 0), 0, 100));
            h.assertTrue("another anomaly".equals(Spots.check(level, from, g, noHearth, data)), "keeps clear of other anomalies");
        }
    }

    private static void expectGround(GameTestHelper h, BlockPos g, BlockPos at, BlockState put, String reason) {
        ServerLevel level = h.getLevel();
        BlockState was = level.getBlockState(at);
        level.setBlock(at, put, 3);
        String got = Spots.checkGround(level, g);
        level.setBlock(at, was, 3);
        h.assertTrue(reason.equals(got), put.getBlock() + " at " + at.subtract(g).toShortString() + ": expected " + reason + ", got " + got);
    }

    // ---- the director -------------------------------------------------------------------------------------------------

    static void directorPauses() {
        AwakenTests.registerIsolated("anomaly_director_pauses_and_caps", 80, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, 14, 14);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.teleportTo(level, g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
            AnomalyMemory mem = AnomalyDirector.memory(p);
            h.assertTrue(AnomalyDirector.upkeep(p, 20).blockedBy("not awakened"), "nothing before awakening");
            Progression.awaken(p);
            h.assertTrue(mem.awakenedAt >= 0 && mem.timer >= Pacing.FIRST_MIN && mem.timer <= Pacing.FIRST_MAX, "awakening sets the first countdown: " + mem.describe());
            h.assertTrue(AnomalyDirector.upkeep(p, 20).blockedBy("just awakened"), "never in the first two minutes");
            mem.awakenedAt = level.getGameTime() - Pacing.GRACE - 1;
            mem.timer = 0; // due now
            p.setSleepingPos(p.blockPosition());
            AnomalyDirector.Decision asleep = AnomalyDirector.upkeep(p, 20);
            h.assertTrue(asleep.blockedBy("asleep") && asleep.started() == null, "never while asleep: " + asleep.outcome());
            h.assertTrue(mem.timer == 0, "the countdown waits while you sleep");
            p.clearSleepingPos();
            // one at a time: with a rift already open for this player, nothing else starts
            AnomalyData data = AnomalyData.get(server);
            Anomaly rift = Rifts.open(level, p, g, data, true);
            AnomalyDirector.Decision busy = AnomalyDirector.upkeep(p, 20);
            h.assertTrue(busy.blockedBy("one anomaly is already active"), "one at a time: " + busy.outcome());
            StringBuilder why = new StringBuilder();
            h.assertTrue(AnomalyDirector.start(p, AnomalyKind.SPIRIT_PROCESSION, false, why) == null && why.toString().contains("already active"),
                    "commands respect the cap too: " + why);
            AnomalyDirector.end(server, data, rift, "test over", true);
            discardAll(level, rift.mobs);
            AnomalyDirector.Decision free = AnomalyDirector.upkeep(p, 20);
            h.assertFalse(free.outcome().startsWith("blocked"), "awake, outdoors, nothing active: the director acts (" + free.outcome() + ")");
            if (free.started() != null) {
                AnomalyDirector.end(server, data, free.started(), "test over", true);
                discardAll(level, free.started().mobs);
            }
            h.assertTrue(data.activeFor(p.getUUID()) == null, "cleaned up");
            h.succeed();
        });
    }

    // ---- rifts --------------------------------------------------------------------------------------------------------

    static void riftPaysOnce() {
        AwakenTests.registerIsolated("anomaly_rift_close_pays_once", 2400, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, 12, -12);
            ServerPlayer p = awakenedPlayer(h, g.offset(3, 0, 0));
            Progression.discover(p, AnomalyKind.RIFT_TEAR.discoveryKey(), 0.1); // so the reward below is the rift's alone
            // anomalies only ever open on entity-ticking ground (Spots); wait for the test's chunks to get there
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the platform ticks"))
                    .thenExecute(() -> closeTheRift(h, level, server, g, p))
                    .thenSucceed();
        });
    }

    private static void closeTheRift(GameTestHelper h, ServerLevel level, MinecraftServer server, BlockPos g, ServerPlayer p) {
        {
            // isolated tests reuse the same spots: an earlier test's shards on this ground would read as a payout
            discardItems(level, g);
            AnomalyData data = AnomalyData.get(server);
            Anomaly a = Rifts.open(level, p, g, data, true);
            RiftEntity e = Rifts.entity(level, a);
            h.assertTrue(e != null && e.hitsLeft() == a.maxHits && a.maxHits >= 4, "a core with a few hits");
            h.assertTrue(a.spawned >= 1, "it spills monsters at once: " + a.spawned);
            Profile prof = Progression.profile(p);
            double before = totalXp(prof);
            var src = level.damageSources().playerAttack(p);
            h.assertFalse(e.hurtServer(level, level.damageSources().generic(), 5), "only players can strike the core");
            for (int i = 0; i < a.maxHits - 1; i++) {
                e.lastHit = Long.MIN_VALUE / 2;
                h.assertTrue(e.hurtServer(level, src, 5), "strike " + (i + 1));
            }
            h.assertFalse(e.hurtServer(level, src, 5), "strikes closer than " + Rifts.HIT_COOLDOWN + " ticks don't count");
            h.assertTrue(totalXp(prof) == before, "no XP while it stands: " + (totalXp(prof) - before));
            h.assertTrue(!a.paid, "not marked paid while it stands");
            h.assertTrue(shardsNear(level, g) == 0, "no shards while it stands: " + shardsNear(level, g));
            e.lastHit = Long.MIN_VALUE / 2;
            h.assertTrue(e.hurtServer(level, src, 5), "the last strike");
            double gained = totalXp(prof) - before;
            int shards = shardsNear(level, g);
            h.assertTrue(a.paid && data.find(a.id) == null, "closed and gone from the active list");
            h.assertTrue(gained >= 0.25 * Rifts.xpFor(Progression.rank(p)), "XP ~0.3 × floor 1: gained " + gained + ", base " + Rifts.xpFor(Progression.rank(p)));
            h.assertTrue(shards >= 2, "essence shards fly out: " + shards);
            h.assertTrue(prof.counter("awaken:rifts_closed") == 1 && AnomalyDirector.memory(p).riftsClosed == 1, "counted once");
            // again: nothing more
            h.assertFalse(Rifts.close(level, data, a, p), "a second close pays nothing");
            e.lastHit = Long.MIN_VALUE / 2;
            h.assertFalse(e.hurtServer(level, src, 5), "a closing core takes no more hits");
            h.assertTrue(totalXp(prof) == before + gained && shardsNear(level, g) == shards && prof.counter("awaken:rifts_closed") == 1, "paid exactly once");
            h.assertTrue(e.closing(), "the tear collapses (and goes on its own a second later)");
            discardAll(level, a.mobs);
            discardItems(level, g);
        }
    }

    static void riftIgnored() {
        AwakenTests.registerIsolated("anomaly_rift_ignored_closes_cleanly", 2400, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, -12, 12);
            ServerPlayer p = awakenedPlayer(h, g.offset(6, 0, 0));
            AnomalyData data = AnomalyData.get(server);
            Profile prof = Progression.profile(p);
            Anomaly[] rift = new Anomaly[1];
            RiftEntity[] core = new RiftEntity[1];
            double[] before = new double[1];
            List<UUID> mobs = new java.util.ArrayList<>();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the platform ticks"))
                    .thenExecute(() -> {
                        rift[0] = Rifts.open(level, p, g, data, true);
                        core[0] = Rifts.entity(level, rift[0]);
                        before[0] = totalXp(prof);
                        rift[0].endsAt = level.getGameTime() + 25; // its time runs out
                        mobs.addAll(rift[0].mobs);
                        h.assertTrue(core[0] != null && !mobs.isEmpty(), "a tear, and it let monsters out");
                    })
                    .thenWaitUntil(() -> h.assertTrue(data.find(rift[0].id) == null, "the director ends it when its time is up"))
                    .thenWaitUntil(() -> h.assertTrue(core[0].isRemoved(), "the tear goes"))
                    .thenExecute(() -> {
                        h.assertFalse(rift[0].paid, "nothing paid");
                        h.assertTrue(prof.counter("awaken:rifts_closed") == 0 && shardsNear(level, g) == 0, "no reward, no counter");
                        h.assertTrue(totalXp(prof) - before[0] < 0.5 * Rifts.xpFor(Progression.rank(p)), "no rift XP (a first-sighting discovery at most)");
                        int alive = 0;
                        for (UUID id : mobs) {
                            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                                alive++;
                                h.assertFalse(m.isPersistenceRequired(), "what it let out despawns the normal way");
                            }
                        }
                        h.assertTrue(alive > 0, "its monsters stay (they are ordinary now)");
                        discardAll(level, mobs);
                    })
                    .thenSucceed();
        });
    }

    // ---- saving -------------------------------------------------------------------------------------------------------

    static void roundTrip() {
        AwakenTests.register("anomaly_state_round_trip", 20, h -> {
            AnomalyData data = new AnomalyData();
            data.nextId = 9;
            data.enabled = false;
            Anomaly rift = new Anomaly(3, AnomalyKind.RIFT_TEAR, UUID.randomUUID(), new BlockPos(10, 70, -4), 1000, 3400);
            rift.theme = "awaken:hive";
            rift.contentLevel = 5;
            rift.maxHits = 4;
            rift.hitsLeft = 2;
            rift.guardSpawned = true;
            rift.nextWaveAt = 1600;
            rift.spawned = 4;
            rift.discovered = true;
            rift.entity = UUID.randomUUID();
            rift.mobs.add(UUID.randomUUID());
            rift.mobs.add(UUID.randomUUID());
            Anomaly trail = new Anomaly(4, AnomalyKind.SPIRIT_PROCESSION, UUID.randomUUID(), new BlockPos(-30, 64, 8), 2000, 9200);
            trail.stage = Anomaly.CACHE;
            trail.target = new BlockPos(-55, 66, 20);
            trail.progress = 1.0;
            trail.cachePlaced = true;
            trail.motes.add(UUID.randomUUID());
            Anomaly gate = new Anomaly(5, AnomalyKind.WANDERING_GATE, UUID.randomUUID(), new BlockPos(100, 80, 100), 3000, 12000);
            gate.gateId = "17";
            Anomaly star = new Anomaly(6, AnomalyKind.FALLEN_STAR, UUID.randomUUID(), new BlockPos(-70, 63, 12), 4000, 11000);
            star.landed = true;
            star.target = new BlockPos(-70, 62, 12);
            star.guardState = Anomaly.GUARD_UP;
            star.guardUntil = 5800;
            star.guard = UUID.randomUUID();
            star.theme = "awaken:hive";
            star.contentLevel = 5;
            Anomaly grove = new Anomaly(7, AnomalyKind.SHIMMER_GROVE, UUID.randomUUID(), new BlockPos(40, 70, -90), 5000, 8000);
            grove.stage = Anomaly.ACTIVE;
            grove.variant = GroveVariant.WHISPER.ordinal();
            grove.hinted = true;
            data.add(rift);
            data.add(trail);
            data.add(gate);
            data.add(star);
            data.add(grove);
            data.removals().add(new AnomalyData.Removal("minecraft:overworld", new BlockPos(1, 2, 3)));
            Tag tag = AnomalyData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            h.assertTrue(tag instanceof CompoundTag ct && ct.getIntOr("schema", -1) == AnomalyData.SCHEMA, "the file says its version");
            AnomalyData back = AnomalyData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(back.nextId == 9 && !back.enabled, "counter and switch survive");
            h.assertTrue(back.active().size() == 5, "all five anomalies survive");
            Anomaly starBack = back.find(6), groveBack = back.find(7);
            h.assertTrue(starBack != null && starBack.landed && starBack.guardState == Anomaly.GUARD_UP && star.guard.equals(starBack.guard)
                    && starBack.target.equals(star.target), "the star's state survives");
            h.assertTrue(groveBack != null && groveBack.variant == GroveVariant.WHISPER.ordinal() && groveBack.hinted && groveBack.stage == Anomaly.ACTIVE,
                    "the grove's state survives");
            // a schema-1 record (no star or grove parts) loads with defaults
            CompoundTag old = (CompoundTag) Anomaly.CODEC.encodeStart(NbtOps.INSTANCE, rift).getOrThrow();
            old.remove("star");
            old.remove("grove");
            Anomaly oldBack = Anomaly.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
            h.assertTrue(!oldBack.landed && oldBack.guardState == Anomaly.GUARD_NONE && oldBack.variant == 0, "an old record loads with defaults");
            for (int i = 0; i < 5; i++) {
                h.assertTrue(back.active().get(i).save().equals(data.active().get(i).save()), "anomaly " + i + " is the same after a save and load");
            }
            h.assertTrue(back.removals().equals(data.removals()), "pending removals survive");
            AnomalyData empty = AnomalyData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
            h.assertTrue(empty.enabled && empty.nextId == 1 && empty.active().isEmpty(), "an empty file loads with defaults");
            // the player's memory
            AnomalyMemory mem = new AnomalyMemory();
            mem.timer = 4321.5;
            mem.awakenedAt = 777;
            mem.started = 6;
            mem.riftsClosed = 3;
            mem.caches = 1;
            mem.lastKind = "rift_tear";
            mem.lastStartedAt = 55555;
            mem.stars = 2;
            mem.groves = 4;
            mem.lastGrove = "bloom";
            Tag mt = AnomalyMemory.MAP_CODEC.codec().encodeStart(NbtOps.INSTANCE, mem).getOrThrow();
            AnomalyMemory mb = AnomalyMemory.MAP_CODEC.codec().parse(NbtOps.INSTANCE, mt).getOrThrow();
            h.assertTrue(mb.describe().equals(mem.describe()), "memory survives: " + mb.describe());
            AnomalyMemory fresh = AnomalyMemory.MAP_CODEC.codec().parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
            h.assertTrue(fresh.timer < 0 && fresh.awakenedAt < 0, "old players load unscheduled, with no grace");
            h.succeed();
        });
    }

    static void recoverAfterRestart() {
        AwakenTests.registerIsolated("anomaly_restart_cleans_up", 60, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, -12, -12);
            // its own copy of the world data: other tests' anomalies may be running at the same time
            AnomalyData data = new AnomalyData();
            // as the file holds them after a restart: a rift mid-fight and a procession whose cache stands unopened
            Anomaly rift = new Anomaly(data.allocateId(), AnomalyKind.RIFT_TEAR, UUID.randomUUID(), g, level.getGameTime(), level.getGameTime() + 2400);
            rift.entity = UUID.randomUUID();
            BlockPos cache = g.offset(3, 1, 3);
            level.setBlock(cache, AnomalyContent.SPIRIT_CACHE.get().defaultBlockState(), 3);
            Anomaly trail = new Anomaly(data.allocateId(), AnomalyKind.SPIRIT_PROCESSION, UUID.randomUUID(), g.offset(-3, 0, 0), level.getGameTime(),
                    level.getGameTime() + 6000);
            trail.stage = Anomaly.CACHE;
            trail.cachePlaced = true;
            trail.target = cache.below();
            data.add(rift);
            data.add(trail);
            int n = AnomalyDirector.recover(server, data);
            h.assertTrue(n == 2 && data.active().isEmpty(), "interrupted anomalies are dropped: " + n);
            h.assertTrue(level.getBlockState(cache).isAir(), "the unopened cache came down");
            h.assertTrue(data.removals().isEmpty(), "nothing left waiting (its chunk was loaded)");
            h.succeed();
        });
    }

    // ---- processions --------------------------------------------------------------------------------------------------

    static void processionCache() {
        AwakenTests.registerIsolated("anomaly_procession_leads_to_a_cache", 2400, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, 14, -14);
            BlockPos start = g.offset(-5, 0, 0), end = g.offset(5, 0, 0);
            ServerPlayer p = awakenedPlayer(h, start.offset(0, 0, -3));
            AnomalyData data = AnomalyData.get(server);
            // lights only appear where entities tick, and they first gather at the start of the path, five blocks west of
            // the platform's centre and often over a chunk border: the whole path must tick, not just its middle
            h.startSequence().thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(start) && level.isPositionEntityTicking(g)
                            && level.isPositionEntityTicking(end), "the path ticks"))
                    .thenExecute(() -> followTheLights(h, level, g, start, end, p, data)).thenSucceed();
        });
    }

    private static void followTheLights(GameTestHelper h, ServerLevel level, BlockPos g, BlockPos start, BlockPos end, ServerPlayer p, AnomalyData data) {
        {
            Anomaly a = Processions.open(level, p, start, end, data);
            h.assertTrue(a.motes.size() == Processions.MOTES, "five lights: " + a.motes.size());
            long now = level.getGameTime();
            // walk with the lights: each pass they move on while you stay near the lead
            for (int i = 0; i < 40 && a.stage != Anomaly.CACHE; i++) {
                var lead = Processions.lead(level, a);
                p.teleportTo(level, lead.x, g.getY() + 1, lead.z, Set.of(), 0, 0, true);
                h.assertTrue(Processions.update(level, data, a, now + i * 10L) == null, "it goes on");
            }
            h.assertTrue(a.stage == Anomaly.CACHE, "followed to the end, the lights leave a cache");
            BlockPos at = a.focus();
            h.assertTrue(level.getBlockState(at).is(AnomalyContent.SPIRIT_CACHE.get()), "the cache stands at the end of the path");
            Profile prof = Progression.profile(p);
            Processions.cacheOpened(level, at, p);
            level.destroyBlock(at, true, p);
            h.assertTrue(data.find(a.id) == null && prof.counter("awaken:spirit_caches") == 1, "opened, counted, over");
            h.assertTrue(prof.counter("awaken:discovered/" + AnomalyKind.SPIRIT_PROCESSION.discoveryKey()) == 1, "the first one is a discovery");
            h.assertTrue(shardsNear(level, at) >= 1, "its loot table drops essence");
            Processions.cacheOpened(level, at, p);
            h.assertTrue(prof.counter("awaken:spirit_caches") == 1, "pays once");
            // the lights fade on their own within a few seconds; tidy up the loot
            discardItems(level, at);
        }
    }

    // ---- wandering gates ----------------------------------------------------------------------------------------------

    static void wanderingGate() {
        // The gate world is a datapack dimension the GameTest server lacks: the gates module's test level stands in for
        // one tick (docs/v2/BRIEF.md, as story's prologue test does).
        AwakenTests.registerIsolated("anomaly_wandering_gate_tracked", 200, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = platform(h, -14, 14);
            ServerPlayer p = awakenedPlayer(h, g.offset(-6, 0, 0));
            AnomalyData data = AnomalyData.get(server);
            h.startSequence()
                    .thenIdle(5)
                    .thenExecute(() -> {
                        dev.awaken.gates.GateService.useTestLevel(level);
                        try {
                            Anomaly a = WanderingGates.open(level, p, g, data);
                            h.assertTrue(a != null && !a.gateId.isEmpty(), "a wandering gate formed for the anomaly");
                            var view = dev.awaken.core.api.GateHooks.get(level, a.gateId);
                            h.assertTrue(view.isPresent() && view.get().breaksOnPlayDay() == Long.MAX_VALUE && view.get().floors() <= 2,
                                    "short, at the player's rank, never breaks");
                            long now = level.getGameTime();
                            int left = WanderingGates.minutesLeft(a, now);
                            h.assertTrue(left >= 5 && left <= 8, "fades in 5–8 minutes: " + left);
                            h.assertTrue(WanderingGates.update(level, data, a, now) == null, "tracked while it stands");
                            dev.awaken.core.api.GateHooks.release(level, a.gateId);
                            String ended = WanderingGates.update(level, data, a, now);
                            h.assertTrue(ended != null, "released, the anomaly ends with it");
                            AnomalyDirector.end(server, data, a, ended, true);
                            h.assertTrue(data.find(a.id) == null, "and is forgotten");
                        } finally {
                            dev.awaken.gates.GateService.useTestLevel(null);
                        }
                    })
                    .thenSucceed();
        });
    }
}
