package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.KitReach;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The Whole Boss Fight's GameTests on the mobs' side (SPEC §68.2, §68.4): the last stand, and the tick a blow lands. */
public final class FinishTests {
    private FinishTests() {}

    public static void register() {
        AwakenTests.register("fights_last_stand", 60, FinishTests::lastStand);
        AwakenTests.register("fights_last_stand_ends", AwakenBoss.BEATEN_TICKS + 80, FinishTests::lastStandEnds);
        AwakenTests.registerIsolated("fights_ring_lands", 200, FinishTests::ringLands);
    }

    /**
     * A boss with an ending doesn't fall to a player's killing blow (SPEC §68.4): it is held at its last point, beaten,
     * and an ordinary blow after that does nothing. A boss without one dies as it always did, and what nothing withstands
     * still kills a beaten one.
     */
    static void lastStand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AwakenBoss boss = KitTests.spawn(level, h, "proving_ringer");
        boss.setNoAi(true);
        var live = AwakenTests.survivalPlayer(h, "last_stand", new BlockPos(1, 1, 1));
        DamageSource blow = level.damageSources().playerAttack(live.player());
        boss.hurtServer(level, blow, 1.0e9F);
        h.assertTrue(boss.isAlive() && boss.beaten() && boss.getHealth() == 1F && Strikes.atLastStand(boss),
                "a player's killing blow leaves it beaten at its last point (alive " + boss.isAlive() + ", health " + boss.getHealth() + ")");
        boss.damageCooldownTime = 0;
        h.assertTrue(!boss.hurtServer(level, blow, 1.0e9F) && boss.isAlive(), "beaten, an ordinary blow does nothing");
        AwakenBoss plain = KitTests.spawn(level, h, "bell_warden");
        plain.setNoAi(true);
        plain.hurtServer(level, blow, 1.0e9F);
        h.assertTrue(plain.isDeadOrDying() && !plain.beaten(), "a boss without an ending dies as it always did");
        boss.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
        h.assertTrue(boss.isDeadOrDying(), "what nothing withstands still kills a beaten body");
        plain.discard();
        boss.discard();
        live.leave();
        h.succeed();
    }

    /** Left alone, a beaten boss dies its plain death after {@link AwakenBoss#BEATEN_TICKS}: a fight can't be left stuck. */
    static void lastStandEnds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AwakenBoss boss = KitTests.spawn(level, h, "proving_ringer");
        var live = AwakenTests.survivalPlayer(h, "last_stand_waits", new BlockPos(1, 1, 1));
        boss.hurtServer(level, level.damageSources().playerAttack(live.player()), 1.0e9F);
        h.assertTrue(boss.isAlive() && boss.beaten(), "it is beaten");
        float health = live.player().getHealth();
        h.runAfterDelay(100, () -> h.assertTrue(boss.isAlive() && live.player().getHealth() >= health, "it waits, and harms nobody while it does"));
        h.succeedWhen(() -> {
            h.assertTrue(boss.isDeadOrDying(), "left alone, it dies its plain death");
            live.leave();
        });
    }

    /**
     * The ring a client draws closes on the tick the blow lands (SPEC §68.2): for the Ringer's Swing at a still player in
     * its reach, the rule the client asks ({@link KitReach#firstTouch}) of the pose the blow commits to at its lock (the
     * synced aim) names the active tick the server then strikes on. Counted as a client counts, from the move's own synced
     * start; the test begins the move between two of the boss's ticks, so the strike may come a tick sooner than in play,
     * never later.
     */
    static void ringLands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        p.setYRot(180F);
        p.setYHeadRot(180F);
        p.setYBodyRot(180F);
        MobDef def = Bestiary.get("proving_ringer");
        KitMoves.Table table = Kits.all().get("proving_ringer").moves();
        KitMoves.Move move = table.get(table.index("swing_a"));
        double back = (move.minRange() + move.maxRange()) * 0.5 + def.width * def.scale * 0.5 + 0.3;
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 21.5 - back)), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        int[] stage = {0}, said = {Integer.MIN_VALUE}, wind = {0}, active = {0};
        long[] from = {0};
        double[] stood = new double[3];
        h.onEachTick(() -> {
            boss.setTarget(p);
            if (stage[0] == 0) {
                FairTests.pool(p);
                h.assertTrue(boss.playKitString("swing_a", List.of()), "the Swing plays");
                KitMoves.Played played = KitMoves.played(move, boss.kitSpeed(), boss.kitHold());
                wind[0] = played.windup() + played.hold();
                active[0] = played.active();
                long now = level.getGameTime();
                from[0] = now + (boss.kitStart() - (int) now);
                stage[0] = 1;
                return;
            }
            if (stage[0] != 1) return;
            if (!boss.kitAimed()) {
                stood[0] = boss.getX();
                stood[1] = boss.getY();
                stood[2] = boss.getZ();
            } else if (said[0] == Integer.MIN_VALUE) {
                // as a client does: the blow's committed pose, from where the body stood as its aim was first known
                float yaw = boss.kitAimTurn(), r = yaw * net.minecraft.util.Mth.DEG_TO_RAD, step = boss.kitAimStep();
                int k = KitReach.firstTouch(move, active[0], stood[0] - net.minecraft.util.Mth.sin(r) * step, stood[1] + boss.kitAimDip(),
                        stood[2] + net.minecraft.util.Mth.cos(r) * step, yaw, boss.kitLean(), boss.getBbHeight() * AwakenBoss.KIT_LEAN_HIP,
                        move.motion().advance() * boss.kitAimRun(), move.motion().rise(), p.getBoundingBox());
                said[0] = k >= 0 ? wind[0] + 1 + k : -1;
            }
            String rule = said[0] == Integer.MIN_VALUE ? "its aim never came" : said[0] < 0 ? "the rule said it passes by" : "the rule said tick " + said[0];
            if (FairTests.share(p) > 1.0E-3F) {
                long landed = level.getGameTime() - from[0];
                h.assertTrue(said[0] >= 0 && landed - said[0] >= -1 && landed - said[0] <= 0, "the Swing landed on tick " + landed + "; " + rule);
                boss.discard();
                stage[0] = 2;
                h.succeed();
            } else if (boss.kitPlaying() == null) {
                h.assertTrue(false, "the Swing never landed on a still player " + String.format("%.1f", boss.distanceTo(p)) + " blocks off; " + rule);
            }
        });
    }
}
