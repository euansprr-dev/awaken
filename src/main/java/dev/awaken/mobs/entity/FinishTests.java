package dev.awaken.mobs.entity;

import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.KitReach;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The Whole Boss Fight's GameTests on the mobs' side (SPEC §67.2, §67.4): the last stand, and the tick a blow lands. */
final class FinishTests {
    private FinishTests() {}

    public static void register() {
        AwakenTests.register("fights_last_stand", 60, FinishTests::lastStand);
        AwakenTests.register("fights_last_stand_ends", AwakenBoss.BEATEN_TICKS + 80, FinishTests::lastStandEnds);
        AwakenTests.register("fights_ring_lands", 200, FinishTests::ringLands);
    }

    /**
     * A boss with an ending doesn't fall to a player's killing blow (SPEC §67.4): it is held at its last point, beaten,
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
     * The ring a client draws closes on the tick the blow lands (SPEC §67.2): for the Ringer's Swing at a still body in
     * its reach, the rule the client asks ({@link KitReach#firstTouch}) five ticks before the blow names the active tick
     * the server then strikes on. The test begins the move between two of the boss's ticks, so the strike may come a tick
     * sooner than in play, never later.
     */
    static void ringLands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        AwakenBoss boss = KitTests.spawn(level, h, "proving_ringer");
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
        pig.setPos(h.absoluteVec(new Vec3(4.5, 1, 7.0)));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        level.addFreshEntity(pig);
        boss.setTarget(pig);
        h.assertTrue(boss.playKitString("swing_a", List.of()), "the Swing plays");
        long began = level.getGameTime();
        int[] said = {Integer.MIN_VALUE};
        var kit = boss.kit();
        KitMoves.Move move = kit.moves().get(kit.moves().index("swing_a"));
        KitMoves.Played played = KitMoves.played(move, boss.kitSpeed(), boss.kitHold());
        int wind = played.windup() + played.hold();
        h.runAfterDelay(Math.max(1, wind - 5), () -> {
            boss.setTarget(pig);
            int k = KitReach.firstTouch(move, played.active(), boss.getX(), boss.getY(), boss.getZ(), boss.yBodyRot, boss.kitLean(),
                    boss.getBbHeight() * AwakenBoss.KIT_LEAN_HIP, move.motion().advance(), move.motion().rise(), pig.getBoundingBox());
            h.assertTrue(k >= 0, "the rule says the Swing reaches a body " + String.format("%.1f", boss.distanceTo(pig)) + " blocks off");
            said[0] = wind + 1 + k;
        });
        h.succeedWhen(() -> {
            boss.setTarget(pig);
            h.assertTrue(pig.hurtTime > 0 || pig.getHealth() < 1000, "the bell lands");
            long landed = level.getGameTime() - began;
            h.assertTrue(said[0] != Integer.MIN_VALUE && landed - said[0] >= -1 && landed - said[0] <= 0,
                    "the ring said tick " + said[0] + " and the blow landed on tick " + landed);
            boss.discard();
            pig.discard();
        });
    }
}
