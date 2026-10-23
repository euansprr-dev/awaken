package dev.awaken.world.anomaly;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.world.landmark.Landmarks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for the P2 anomalies: the fallen star (lands in a crater, its guardian, sealed until the guardian is slain or
 * fades, pays once, burns out unopened, strays turned away) and the shimmer grove (waits, wakes when stepped into, each
 * variant bends its rule, everything goes back), plus the kind weights over simulated sessions.
 */
final class AnomalyP2Tests {
    private AnomalyP2Tests() {}

    static void register() {
        kindsAndVariety();
        starPaysOnce();
        starBurnsOut();
        groves();
    }

    // ---- pure ---------------------------------------------------------------------------------------------------------

    static final int RIFT = AnomalyKind.RIFT_TEAR.ordinal(), GATE = AnomalyKind.WANDERING_GATE.ordinal(),
            PROC = AnomalyKind.SPIRIT_PROCESSION.ordinal(), STAR = AnomalyKind.FALLEN_STAR.ordinal(), GROVE = AnomalyKind.SHIMMER_GROVE.ordinal();

    static void kindsAndVariety() {
        AwakenTests.register("anomaly_p2_kinds_and_variety", 20, h -> {
            int[] day = Pacing.weights(false, false, false, 10, ""), night = Pacing.weights(false, true, false, 10, "");
            h.assertTrue(night[STAR] > day[STAR] * 3, "fallen stars come mostly at night: day " + day[STAR] + ", night " + night[STAR]);
            h.assertTrue(day[GROVE] > night[GROVE] * 3, "shimmer groves come mostly by day: day " + day[GROVE] + ", night " + night[GROVE]);
            h.assertTrue(night[PROC] == 0, "processions stay day only");
            h.assertTrue(day[RIFT] < day[PROC] + day[GROVE] + day[STAR], "calm days: rifts are the smaller part by day");
            h.assertTrue(night[RIFT] + night[STAR] > night[PROC] + night[GROVE] + night[GATE], "dangerous nights");
            h.assertTrue(Pacing.weights(false, true, false, 10, AnomalyKind.FALLEN_STAR.id)[STAR] == 0, "no star twice running");
            h.assertTrue(Pacing.weights(false, false, false, 10, AnomalyKind.SHIMMER_GROVE.id)[GROVE] == 0, "no grove twice running");
            h.assertTrue(Pacing.weights(false, false, false, 10, AnomalyKind.RIFT_TEAR.id)[RIFT] == day[RIFT] / 2, "a rift may come again, at half weight");
            int[] fresh = Pacing.weights(false, false, false, 10, "", Set.of(AnomalyKind.RIFT_TEAR.id));
            h.assertTrue(fresh[GROVE] == 2 * day[GROVE] && fresh[RIFT] == day[RIFT], "kinds never met come twice as often");
            int[] peaceful = Pacing.weights(false, true, true, 2, AnomalyKind.FALLEN_STAR.id);
            h.assertTrue(peaceful[RIFT] == 0 && Pacing.total(peaceful) > 0, "a peaceful night still has something (a grove)");
            // a 60-minute session: the first anomaly ~13 min in (prologue, grace, first countdown), then one every ~9 outdoor
            // minutes (+ ~2.5 for the event itself), day and night by the clock; the player levels up as it goes
            RandomSource r = RandomSource.create(7);
            int sessions = 2000, events = 5;
            int distinctTotal = 0;
            int[] seenIn = new int[AnomalyKind.values().length];
            for (int s = 0; s < sessions; s++) {
                Set<String> met = new HashSet<>();
                String last = "";
                double t = 13 + r.nextDouble() * 3;
                int level = 2;
                boolean[] seen = new boolean[AnomalyKind.values().length];
                for (int i = 0; i < events; i++) {
                    boolean isNight = (t % 20) >= 10.5;
                    AnomalyKind k = Pacing.pick(Pacing.weights(i == 0, isNight, false, level, last, met), r);
                    h.assertTrue(k != null, "something always fits");
                    seen[k.ordinal()] = true;
                    met.add(k.id);
                    last = k.id;
                    t += (6 + r.nextDouble() * 6) / Pacing.rate(isNight) + 2.5;
                    level = Math.min(6, level + 1);
                }
                for (int k = 0; k < seen.length; k++) {
                    if (seen[k]) {
                        distinctTotal++;
                        seenIn[k]++;
                    }
                }
            }
            double mean = distinctTotal / (double) sessions;
            h.assertTrue(mean >= 3.3, "a 60-minute session sees variety: " + mean + " kinds on average");
            for (AnomalyKind k : AnomalyKind.values()) {
                h.assertTrue(seenIn[k.ordinal()] >= sessions * 0.35, k.id + " shows up in a good share of sessions: " + seenIn[k.ordinal()] + "/" + sessions);
            }
            // grove variants never repeat back to back
            for (GroveVariant last : GroveVariant.values()) {
                for (int i = 0; i < 50; i++) h.assertTrue(Groves.pickVariant(r, last.id) != last, "no " + last.id + " twice running");
            }
            // star rewards
            h.assertTrue(Stars.xpFor(Rank.E, true) > Stars.xpFor(Rank.E, false), "slaying the guardian pays more");
            for (int i = 0; i < 100; i++) {
                int s0 = Stars.shardsFor(Rank.E, false, r), s1 = Stars.shardsFor(Rank.E, true, r);
                h.assertTrue(s0 >= 1 && s0 <= 2 && s1 >= 3 && s1 <= 4, "E-rank star shards " + s0 + " / slain " + s1);
            }
            h.assertTrue(AnomalyNow.left(Pacing.MINUTE * 2 + 5).equals("3 min left") && AnomalyNow.left(400).equals("ending"), "tracker minutes");
            h.succeed();
        });
    }

    // ---- fallen star --------------------------------------------------------------------------------------------------

    static void starPaysOnce() {
        AwakenTests.registerIsolated("anomaly_star_lands_guards_pays_once", 1200, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = AnomalyTests.platform(h, 12, 12);
            ServerPlayer p = AnomalyTests.awakenedPlayer(h, g.offset(9, 0, 0));
            Progression.discover(p, AnomalyKind.FALLEN_STAR.discoveryKey(), 0.1); // so the reward below is the star's alone
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the platform ticks"))
                    .thenExecute(() -> starRun(h, level, server, g, p))
                    .thenSucceed();
        });
    }

    private static void starRun(GameTestHelper h, ServerLevel level, MinecraftServer server, BlockPos g, ServerPlayer p) {
        AnomalyData data = AnomalyData.get(server);
        // its spot needs open sky: a roof well above the ground (not "cramped") still refuses it
        Spots.Rules sky = Spots.Rules.NORMAL.hearth((l, q, r) -> false).gates(false).sky(true);
        BlockPos from = g.offset(-40, 0, 0);
        h.assertTrue(Spots.check(level, from, g, sky, null) == null, "open ground under the sky: " + Spots.check(level, from, g, sky, null));
        level.setBlock(g.above(6), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue("no sky".equals(Spots.check(level, from, g, sky, null)), "a star never lands under a roof");
        level.setBlock(g.above(6), Blocks.AIR.defaultBlockState(), 3);

        clearOthers(level, p, g);
        Anomaly a = Stars.open(level, p, g, data);
        UUID light = a.entity;
        h.assertTrue(light != null && level.getEntity(light) instanceof FallingStarEntity, "a light streaks down");
        h.assertFalse(a.landed, "still falling");
        h.assertTrue(Stars.impact(level, data, a), "it lands");
        h.assertTrue(a.landed && level.getBlockState(a.target).is(AnomalyContent.FALLEN_STAR.get())
                && level.getBlockState(a.target).getValue(FallenStarBlock.SEALED), "a sealed meteor lies there");
        h.assertTrue(a.target.getY() <= g.getY() - 1, "in a crater: the meteor sits below the old ground (" + a.target.getY() + " vs " + g.getY() + ")");
        h.assertTrue(level.getBlockState(a.target.below()).is(Blocks.MAGMA_BLOCK), "scorched underneath");
        h.assertTrue(level.getEntity(light) == null || level.getEntity(light).isRemoved(), "the falling light is gone");
        h.assertTrue(a.focus().equals(a.target), "the anomaly points at the meteor now");

        Profile prof = Progression.profile(p);
        double before = AnomalyTests.totalXp(prof);
        Stars.opened(level, a.target, p);
        h.assertFalse(a.paid, "sealed: nothing opens while it's guarded");
        long now = level.getGameTime();
        h.assertTrue(Stars.update(level, data, a, now) == null, "it lies there");
        if (a.guardState == Anomaly.GUARD_UP) {
            Entity e = level.getEntity(a.guard);
            h.assertTrue(e instanceof LivingEntity, "a guardian rose: " + e);
            LivingEntity guard = (LivingEntity) e;
            h.assertTrue(guard.hasEffect(MobEffects.GLOWING) && guard.entityTags().contains(Stars.GUARD_TAG), "it glows and is marked as the star's");
            h.assertTrue(a.guardUntil - now == Stars.GUARD_LIFE, "short-lived: " + (a.guardUntil - now));
            h.assertTrue(level.getBlockState(a.target).getValue(FallenStarBlock.SEALED), "still sealed while it stands");
            guard.hurtServer(level, level.damageSources().playerAttack(p), 1.0e6F);
            if (guard.isAlive()) guard.kill(level);
            h.assertTrue(a.guardState == Anomaly.GUARD_SLAIN, "slain: " + a.guardState);
        } else {
            h.assertTrue(a.guardState == Anomaly.GUARD_FADED, "no guardian could rise: the star cools instead (" + a.guardState + ")");
        }
        h.assertTrue(Stars.update(level, data, a, now + 10) == null, "still there");
        h.assertFalse(level.getBlockState(a.target).getValue(FallenStarBlock.SEALED), "the star cooled");
        boolean slain = a.guardState == Anomaly.GUARD_SLAIN;
        BlockPos at = a.target;
        Stars.opened(level, at, p);
        level.destroyBlock(at, true, p);
        h.assertTrue(a.paid && data.find(a.id) == null, "opened, and over");
        double gained = AnomalyTests.totalXp(prof) - before;
        double base = Stars.xpFor(Progression.rank(p), slain);
        h.assertTrue(gained >= 0.8 * base, "XP " + (slain ? "0.3" : "0.2") + " × floor 1: gained " + gained + " of " + base);
        int shards = AnomalyTests.shardsNear(level, at);
        h.assertTrue(shards >= (slain ? 3 : 1), "essence flies out: " + shards);
        int materials = 0;
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(10))) {
            if (!it.getItem().is(CoreApi.ESSENCE_SHARD.get())) materials += it.getItem().getCount();
        }
        h.assertTrue(materials >= 1, "materials from the meteor's loot table: " + materials);
        h.assertTrue(prof.counter("awaken:stars_opened") == 1 && AnomalyDirector.memory(p).stars == 1, "counted once");
        if (slain) h.assertTrue(prof.counter("awaken:star_guardians_slain") == 1, "the slayer is counted");
        Stars.opened(level, at, p);
        h.assertTrue(prof.counter("awaken:stars_opened") == 1 && AnomalyTests.totalXp(prof) - before == gained, "paid exactly once");
        AnomalyTests.discardItems(level, at);
    }

    static void starBurnsOut() {
        AwakenTests.registerIsolated("anomaly_star_burns_out_and_strays_go", 1200, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = AnomalyTests.platform(h, -12, -12);
            ServerPlayer p = AnomalyTests.awakenedPlayer(h, g.offset(30, 0, 0)); // too far to wake a guardian
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the platform ticks"))
                    .thenExecute(() -> {
                        AnomalyData data = AnomalyData.get(server);
                        clearOthers(level, p, g);
                        Anomaly a = Stars.open(level, p, g, data);
                        long now = level.getGameTime();
                        // the light never arrives (its chunk didn't tick): the director lands it anyway
                        h.assertTrue(Stars.update(level, data, a, now) == null && !a.landed, "still falling for a moment");
                        h.assertTrue(Stars.update(level, data, a, a.startedAt + Stars.FALL_TICKS + 20) == null && a.landed, "landed by the director");
                        h.assertTrue(a.endsAt - (a.startedAt + Stars.FALL_TICKS + 20) >= Stars.LIFETIME - 20, "it waits a few minutes for someone");
                        h.assertTrue(a.guardState == Anomaly.GUARD_NONE, "nobody came close: no guardian");
                        BlockPos at = a.target;
                        String ended = Stars.update(level, data, a, a.endsAt);
                        h.assertTrue(ended != null && ended.contains("burned out"), "unopened, it burns out: " + ended);
                        AnomalyDirector.end(server, data, a, ended, true);
                        h.assertFalse(level.getBlockState(at).is(AnomalyContent.FALLEN_STAR.get()), "the meteor is gone (the crater stays)");
                        h.assertTrue(data.find(a.id) == null, "forgotten");
                        // a guardian whose star is gone never comes back into the world (after a restart, an unload)
                        Zombie stray = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
                        h.assertTrue(stray != null, "a stand-in guardian");
                        stray.snapTo(g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5, 0F, 0F);
                        stray.addTag(Stars.GUARD_TAG);
                        stray.getPersistentData().putInt(Stars.GUARD_TAG, 987654);
                        h.assertFalse(level.addFreshEntity(stray), "turned away at the door");
                        // a restart with a meteor on the ground: it comes down
                        AnomalyData saved = new AnomalyData();
                        Anomaly b = new Anomaly(saved.allocateId(), AnomalyKind.FALLEN_STAR, UUID.randomUUID(), g, now, now + 5000);
                        b.landed = true;
                        b.target = g.above(2);
                        level.setBlock(b.target, AnomalyContent.FALLEN_STAR.get().defaultBlockState(), 3);
                        saved.add(b);
                        h.assertTrue(AnomalyDirector.recover(server, saved) == 1 && saved.active().isEmpty(), "interrupted star dropped");
                        h.assertTrue(level.getBlockState(b.target).isAir(), "its meteor came down");
                    })
                    .thenSucceed();
        });
    }

    /**
     * Mock players left behind by earlier batches may stand on this test's spot; a grove wakes and a guardian rises for
     * anyone, so they are moved far away first.
     */
    static void clearOthers(ServerLevel level, ServerPlayer keep, BlockPos around) {
        for (ServerPlayer o : List.copyOf(level.players())) {
            if (o != keep && o.blockPosition().distSqr(around) < 48 * 48) {
                o.teleportTo(level, around.getX() + 5000.5, around.getY() + 200, around.getZ() + 5000.5, Set.of(), 0, 0, true);
            }
        }
    }

    // ---- shimmer grove ------------------------------------------------------------------------------------------------

    /** A grassy platform (radius 12) for groves: flowers need soil. */
    static BlockPos meadow(GameTestHelper h, int dx, int dz) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(dx, 3, dz));
        BlockState dirt = Blocks.DIRT.defaultBlockState(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = -3; y <= -1; y++) level.setBlock(c.offset(x, y, z), dirt, 3);
                level.setBlock(c.offset(x, 0, z), grass, 3);
                for (int y = 1; y <= 8; y++) level.setBlock(c.offset(x, y, z), air, 3);
            }
        }
        return c;
    }

    static void groves() {
        AwakenTests.registerIsolated("anomaly_grove_bends_the_rules", 1200, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos g = meadow(h, 0, 0);
            ServerPlayer p = AnomalyTests.awakenedPlayer(h, g.offset(11, 0, 0));
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(level.isPositionEntityTicking(g), "the meadow ticks"))
                    .thenExecute(() -> {
                        Landmarks.testOverrides(g.offset(300, 0, 400), true);
                        try {
                            groveRun(h, level, server, g, p);
                        } finally {
                            Landmarks.testOverrides(null, false);
                        }
                    })
                    .thenSucceed();
        });
    }

    private static void groveRun(GameTestHelper h, ServerLevel level, MinecraftServer server, BlockPos g, ServerPlayer p) {
        AnomalyData data = AnomalyData.get(server);
        Profile prof = Progression.profile(p);
        Progression.topUpToLevel(p, 10, Progression.XpSource.COMMAND); // the whisper needs a reason to send you to the Well
        int visited = 0;
        for (GroveVariant v : GroveVariant.values()) {
            clearOthers(level, p, g);
            p.teleportTo(level, g.getX() + 11.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
            Anomaly a = Groves.open(level, p, g, data, v);
            ShimmerEntity s = Groves.shimmer(level, a);
            h.assertTrue(s != null && s.variant() == v && !s.active(), v.id + ": the air shimmers, waiting");
            h.assertTrue(a.endsAt - a.startedAt == Groves.WAIT, "it waits " + Groves.WAIT / Pacing.MINUTE + " minutes for someone");
            long now = level.getGameTime();
            h.assertTrue(Groves.update(level, data, a, now) == null && a.stage == Anomaly.OPEN, v.id + ": nothing bends from outside");
            p.teleportTo(level, g.getX() + 2.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
            h.assertTrue(Groves.update(level, data, a, now) == null && a.stage == Anomaly.ACTIVE, v.id + ": stepping in wakes it");
            h.assertTrue(a.endsAt == now + Groves.DURATION && s.active(), v.id + ": the rules bend for 2½ minutes");
            visited++;
            h.assertTrue(prof.counter("awaken:groves_visited") == visited && prof.counter("awaken:discovered/" + AnomalyKind.SHIMMER_GROVE.discoveryKey()) == 1,
                    "counted; the first one is a discovery");
            switch (v) {
                case LIGHTFOOT -> {
                    Groves.update(level, data, a, now + 10);
                    h.assertTrue(Groves.lightened(p), "light steps inside");
                    p.teleportTo(level, g.getX() + 11.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
                    Groves.update(level, data, a, now + 20);
                    h.assertFalse(Groves.lightened(p), "normal weight again outside");
                    p.teleportTo(level, g.getX() + 1.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
                    Groves.update(level, data, a, now + 30);
                    h.assertTrue(Groves.lightened(p) && a.touched.contains(p.getUUID()), "light again inside");
                }
                case BLOOM -> {
                    int flowers = 0;
                    for (BlockPos q : BlockPos.betweenClosed(g.offset(-8, 1, -8), g.offset(8, 1, 8))) {
                        if (level.getBlockState(q).is(BlockTags.SMALL_FLOWERS)) flowers++;
                    }
                    h.assertTrue(flowers >= 4, "flowers spring up: " + flowers);
                    p.removeEffect(MobEffects.REGENERATION);
                    Groves.bend(level, a, 0);
                    h.assertTrue(p.hasEffect(MobEffects.REGENERATION), "and mend whoever stands among them");
                }
                case WHISPER -> {
                    h.assertTrue(a.hinted, "the spirit spoke");
                    h.assertTrue("well".equals(Landmarks.memory(p).whispered()),
                            "it whispered about the Well (a landmark worth the trip at level 10): " + Landmarks.memory(p).describe());
                }
                case ORE_SIGHT -> h.assertTrue(s.active() && s.variant() == GroveVariant.ORE_SIGHT, "ore sight is the client's to draw");
            }
            String ended = Groves.update(level, data, a, a.endsAt);
            h.assertTrue(ended != null, v.id + ": it fades in time");
            AnomalyDirector.end(server, data, a, ended, true);
            h.assertFalse(Groves.lightened(p), v.id + ": everything goes back");
            h.assertTrue(a.touched.isEmpty() && (s.fading() || s.isRemoved()), v.id + ": the shimmer thins out");
            h.assertTrue(data.find(a.id) == null, "forgotten");
        }
        // an unvisited grove just fades
        clearOthers(level, p, g);
        Anomaly lonely = Groves.open(level, p, g, data, GroveVariant.BLOOM);
        p.teleportTo(level, g.getX() + 11.5, g.getY() + 1, g.getZ() + 0.5, Set.of(), 0, 0, true);
        String ended = Groves.update(level, data, lonely, lonely.endsAt);
        h.assertTrue(ended != null && ended.contains("nobody"), "nobody came: " + ended);
        AnomalyDirector.end(server, data, lonely, ended, true);
    }
}
