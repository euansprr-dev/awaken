package dev.awaken.hearth.trial;

import dev.awaken.core.api.Promises;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/** The first Proving (SPEC §46.4): the Drowned Belfry's layout, and the Ringer's whole flow in a hall built for the test. */
public final class ProvingTests {
    private ProvingTests() {}

    public static void register() {
        AwakenTests.registerIsolated("proving_ringer", 2400, h -> {
            // the hall as laid out: you wake knee-deep, the chancel is dry and a step up, the lid seals the pit, the
            // door fits you and not the Ringer, the stair climbs to the landing
            h.assertTrue(Belfry.wet(Belfry.block(0, 0, Belfry.WAKE_Z)) && Belfry.block(0, 1, Belfry.WAKE_Z) == null, "you wake in water, head clear");
            h.assertTrue(Belfry.block(0, 0, 0) != null && !Belfry.wet(Belfry.block(0, 0, 0)) && Belfry.block(0, 1, 0) == null, "the chancel is dry");
            h.assertTrue(Belfry.wet(Belfry.block(Belfry.CHANCEL_X + 1, 0, 0)), "a waterlogged half-step onto it");
            h.assertTrue(Belfry.lid(0, Belfry.PIT_Z) && Belfry.block(0, -3, Belfry.PIT_Z) == null, "the lid seals a hollow pit");
            h.assertTrue(Belfry.block(0, 1, Belfry.SOUTH) == null && Belfry.block(0, 3, Belfry.SOUTH) == null
                    && Belfry.block(0, 4, Belfry.SOUTH) != null, "a door three blocks tall");
            h.assertTrue(Belfry.block(0, Belfry.LANDING_DY - 1, Belfry.LANDING_Z) != null && Belfry.block(0, Belfry.LANDING_DY, Belfry.LANDING_Z) == null,
                    "the landing at the stair's top");
            for (int[] c : Belfry.COVER) {
                h.assertTrue(Belfry.chancel(c[0], c[1]) && Math.abs(c[0]) > 2 && Math.abs(c[1] - Belfry.DROP_Z) + Math.abs(c[0]) > 6,
                        "a fallen bell clear of the runner and the drop at " + c[0] + "," + c[1]);
            }

            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            ServerLevel home = sp.level();
            sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
            Progression.awaken(sp);
            double need = 1;
            for (int l = 1; l < Rank.D.levelRequired; l++) need += Curve.xpToNext(l);
            Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
            h.assertTrue(Progression.profile(sp).reassessmentReady(), "ready for D");
            h.assertTrue(Trials.Kind.forTarget(Rank.D) == Trials.Kind.PROVING, "D is a Proving");
            // the GameTest server has no trials dimension: the hall goes up here, over the superflat, while the bell rings
            BlockPos c = h.absolutePos(new BlockPos(0, 24, 0));
            Provings.Run run = Provings.openAt(sp, home, c, Rank.D, false);
            h.assertTrue(Trials.inTrial(sp) && run.stage == Provings.Stage.OPENING, "the hall is readied while it rings");
            ServerLevel trials = run.level;
            int[] step = {0};
            long[] mark = {0};
            h.onEachTick(() -> {
                long now = trials.getGameTime();
                switch (step[0]) {
                    case 0 -> {
                        if (run.stage != Provings.Stage.WAITING || sp.level() != trials) return;
                        h.assertTrue(Math.abs(sp.getZ() - (c.getZ() + Belfry.WAKE_Z + 0.5)) < 1.5, "woke at the door, z " + (sp.getZ() - c.getZ()));
                        h.assertTrue(Belfry.water(trials, c.offset(0, 0, Belfry.WAKE_Z)), "knee-deep");
                        h.assertTrue(trials.getBlockState(c.offset(0, 0, Belfry.PIT_Z)).equals(Belfry.block(0, 0, Belfry.PIT_Z)), "the lid is shut");
                        onChancel(sp, c);
                        step[0] = 1;
                    }
                    case 1 -> {
                        LivingEntity r = Provings.ringer(run);
                        if (r == null) return;
                        h.assertTrue(Math.abs(r.getZ() - (c.getZ() + Belfry.DROP_Z + 0.5)) < 3, "it drops at the chancel's middle");
                        // a fall: you wake at the door, whole, and alone it starts over
                        sp.hurtServer(trials, sp.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                        h.assertTrue(sp.isAlive() && sp.getHealth() >= sp.getMaxHealth() - 0.01F, "a Proving never kills");
                        h.assertTrue(Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "still in it, one fall");
                        h.assertTrue(Math.abs(sp.getZ() - (c.getZ() + Belfry.WAKE_Z + 0.5)) < 1.5, "back at the door");
                        h.assertTrue(run.stage == Provings.Stage.WAITING && !r.isAlive(), "the Ringer went back up into the bells");
                        onChancel(sp, c);
                        step[0] = 2;
                    }
                    case 2 -> {
                        LivingEntity r = Provings.ringer(run);
                        if (r == null) return;
                        r.setHealth(r.getMaxHealth() * 0.45F);
                        mark[0] = now;
                        step[0] = 3;
                    }
                    case 3 -> {
                        if (now - mark[0] < 1) return;
                        h.assertTrue(run.drained, "at half its health the water drains");
                        if (now - mark[0] < 70) return;
                        h.assertFalse(Belfry.water(trials, c.offset(24, 0, 0)) || Belfry.water(trials, c.offset(0, 0, Belfry.WAKE_Z)), "the water ran off");
                        h.assertTrue(trials.getBlockState(c.offset(0, 0, Belfry.PIT_Z)).isAir() && trials.getBlockState(c.offset(0, -1, Belfry.PIT_Z)).isAir(),
                                "the floor over the pit gave way");
                        LivingEntity r = Provings.ringer(run);
                        h.assertTrue(r != null, "it fights on");
                        r.kill(trials);
                        h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                        mark[0] = now;
                        step[0] = 4;
                    }
                    case 4 -> {
                        if (Trials.inTrial(sp)) return;
                        h.assertTrue(now - mark[0] >= Provings.OUTRO, "the water, the chain and the Tenor before home");
                        h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                        h.assertTrue(Progression.rank(sp) == Rank.D, "rank D, was " + Progression.rank(sp));
                        h.assertTrue(Promises.answered(sp, Promises.Need.tier("tether")), "its chain is the rope");
                        step[0] = 5;
                    }
                    case 5 -> {
                        if (!Provings.settled()) return;
                        h.assertTrue(Belfry.water(trials, c.offset(24, 0, 0)) && Belfry.water(trials, c.offset(0, 0, Belfry.WAKE_Z)), "the water's back");
                        h.assertTrue(trials.getBlockState(c.offset(0, 0, Belfry.PIT_Z)).equals(Belfry.block(0, 0, Belfry.PIT_Z)), "the lid's back");
                        h.assertTrue(trials.getBlockState(c.offset(0, 1, 0)).isAir() || trials.getBlockState(c.offset(0, 1, 0)).is(Blocks.LIGHT), "nothing left lying");
                        step[0] = 6;
                    }
                    default -> {}
                }
            });
            h.succeedWhen(() -> h.assertTrue(step[0] == 6, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
        });
    }

    /** Onto the chancel's south end, as climbing the half-step would. */
    private static void onChancel(ServerPlayer sp, BlockPos c) {
        sp.teleportTo((ServerLevel) sp.level(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 20.5, Set.<Relative>of(), 180F, 0F, true);
    }
}
