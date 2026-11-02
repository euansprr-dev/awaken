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

/** The Provings (SPEC §46.4, §46.6): each hall's layout, and each Warden's whole flow in a hall built for the test. */
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
                        LivingEntity r = Provings.warden(run);
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
                        LivingEntity r = Provings.warden(run);
                        if (r == null) return;
                        r.setHealth(r.getMaxHealth() * 0.45F);
                        mark[0] = now;
                        step[0] = 3;
                    }
                    case 3 -> {
                        if (now - mark[0] < 1) return;
                        h.assertTrue(run.turns >= 1, "at half its health the water drains");
                        if (now - mark[0] < 70) return;
                        h.assertFalse(Belfry.water(trials, c.offset(24, 0, 0)) || Belfry.water(trials, c.offset(0, 0, Belfry.WAKE_Z)), "the water ran off");
                        h.assertTrue(trials.getBlockState(c.offset(0, 0, Belfry.PIT_Z)).isAir() && trials.getBlockState(c.offset(0, -1, Belfry.PIT_Z)).isAir(),
                                "the floor over the pit gave way");
                        LivingEntity r = Provings.warden(run);
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
        AwakenTests.registerIsolated("proving_examiner", 3000, ProvingTests::examiner);
    }

    /**
     * The second Proving (SPEC §46.6): the Hall of Reflections' layout and what of a room is copied; then the whole flow
     * in a hall built for the test: you wake where you stood in your room, it turns to glass, the Examiner steps out of
     * it and it breaks; the mirrors wake and it says what it read; the ring rises; a fall puts the hall back; the win
     * is rank C; the hall is put back after.
     */
    private static void examiner(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(Reflections.block(0, 0, 0) != null && Reflections.block(0, 1, 0) == null, "you stand on the floor in the middle");
        h.assertTrue(Reflections.block(0, 1, 41) == null && Reflections.block(0, 3, 41) == null && Reflections.block(0, 4, 41) != null,
                "a door three blocks tall");
        h.assertTrue(Reflections.block(0, Reflections.LANDING_DY - 1, Reflections.LANDING_Z) != null
                && Reflections.block(0, Reflections.LANDING_DY, Reflections.LANDING_Z) == null, "the landing at the stair's top");
        h.assertTrue(Reflections.mirror(Reflections.WALL_R, 8, 0) && !Reflections.mirror(0, 8, Reflections.WALL_R), "mirrors round the wall, none over the door");
        for (int[] cv : Reflections.COVER) {
            double r = Reflections.r(cv[0], cv[1]);
            h.assertTrue(r >= ExaminerWarden.ARRIVE_AHEAD + 6 && r < Reflections.RING_R - 3, "a standing mirror clear of the middle, inside the ring at " + cv[0] + "," + cv[1]);
        }
        h.assertTrue(Reflections.ring(0, 2, Reflections.RING_R) == null && Reflections.ring(0, 4, Reflections.RING_R) != null
                && Reflections.ring(0, 5, -Reflections.RING_R) != null, "the ring has a door south");
        ServerLevel lv = h.getLevel();
        BlockPos any = h.absolutePos(new BlockPos(0, 1, 0));
        h.assertTrue(ExaminerWarden.copied(Blocks.WATER.defaultBlockState(), lv, any).isAir() && ExaminerWarden.copied(Blocks.SPAWNER.defaultBlockState(), lv, any).isAir()
                && ExaminerWarden.copied(Blocks.TNT.defaultBlockState(), lv, any).isAir(), "no water, spawner or TNT comes");
        h.assertTrue(ExaminerWarden.copied(Blocks.CHEST.defaultBlockState(), lv, any).is(Blocks.CHEST), "a chest comes, empty");
        var wet = Blocks.STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        h.assertTrue(ExaminerWarden.copied(wet, lv, any) == wet.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false),
                "what stood in water comes dry");
        h.assertTrue(ExaminerWarden.glassOf(net.minecraft.world.level.material.MapColor.COLOR_RED)
                .is(Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED)), "red turns to red glass");

        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel home = sp.level();
        sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
        Progression.awaken(sp);
        Progression.passReassessment(sp);
        double need = 1;
        for (int l = 1; l < Rank.C.levelRequired; l++) need += Curve.xpToNext(l);
        Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
        h.assertTrue(Progression.rank(sp) == Rank.D && Progression.profile(sp).reassessmentReady(), "ready for C");
        h.assertTrue(Trials.Kind.forTarget(Rank.C) == Trials.Kind.PROVING, "C is a Proving");
        // the room you stand in: an emerald block beside you, a chest behind, a pool in front
        BlockPos feet = h.absolutePos(new BlockPos(2, 2, 2));
        sp.teleportTo(home, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.<Relative>of(), 90F, 0F, true);
        home.setBlockAndUpdate(feet.offset(3, 0, 0), Blocks.EMERALD_BLOCK.defaultBlockState());
        home.setBlockAndUpdate(feet.offset(-3, 0, 0), Blocks.CHEST.defaultBlockState());
        home.setBlockAndUpdate(feet.offset(0, 0, 3), Blocks.WATER.defaultBlockState());
        BlockPos c = h.absolutePos(new BlockPos(0, 40, 0));
        Provings.Run run = Provings.openAt(sp, home, c, Rank.C, false);
        h.assertTrue(run.warden == ExaminerWarden.WARDEN && run.room instanceof ExaminerWarden.Room, "the Examiner read the room you stood in");
        ExaminerWarden.Room room = (ExaminerWarden.Room) run.room;
        ServerLevel trials = run.level;
        BlockPos at = c.above(); // where you stood, in the hall
        int[] step = {0};
        long[] mark = {0};
        h.onEachTick(() -> {
            long now = trials.getGameTime();
            LivingEntity w = Provings.warden(run);
            switch (step[0]) {
                case 0 -> {
                    if (run.stage != Provings.Stage.WAITING) return;
                    h.assertTrue(trials.getBlockState(at.offset(3, 0, 0)).is(Blocks.EMERALD_BLOCK), "the emerald beside you");
                    h.assertTrue(trials.getBlockState(at.offset(-3, 0, 0)).is(Blocks.CHEST), "the chest behind you");
                    h.assertTrue(trials.getBlockState(at.offset(0, 0, 3)).isAir(), "no pool");
                    h.assertTrue(Math.abs(sp.getX() - (at.getX() + 0.5)) < 0.6 && Math.abs(sp.getZ() - (at.getZ() + 0.5)) < 0.6
                            && Math.abs(sp.getYRot() - 90F) < 1F, "where you stood, looking the same way");
                    step[0] = 1;
                }
                case 1 -> {
                    if (room.glassDone < 0) return;
                    h.assertTrue(trials.getBlockState(at.offset(3, 0, 0)).getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock, "the emerald turned to glass");
                    h.assertTrue(trials.getBlockState(at.offset(-3, 0, 0)).isAir(), "the chest went");
                    step[0] = 2;
                }
                case 2 -> {
                    if (w == null) return;
                    h.assertTrue(room.shattered && Reflections.HALL.is(trials, c, 3, 1, 0) && Reflections.HALL.is(trials, c, 0, 0, 0), "the room broke into the hall");
                    h.assertTrue(w.getX() < at.getX() - 6 && Math.abs(w.getZ() - (at.getZ() + 0.5)) < 1.5, "it steps out in front of you, at " + (w.getX() - at.getX()));
                    w.setHealth(w.getMaxHealth() * 0.6F);
                    mark[0] = now;
                    step[0] = 3;
                }
                case 3 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns >= 1, "at two thirds of its health the mirrors wake");
                    if (now - mark[0] < ExaminerWarden.READ_AFTER + 2) return;
                    h.assertTrue(trials.getBlockState(c.offset(Reflections.WALL_R, 8, 0)) == Reflections.woken(true), "a mirror woke");
                    h.assertTrue(room.told, "it said what it read");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.3F);
                    mark[0] = now;
                    step[0] = 4;
                }
                case 4 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 2, "at a third the walls close in");
                    if (now - mark[0] < Reflections.RING_HI * ExaminerWarden.RING_ROW_TICKS + 4) return;
                    h.assertTrue(trials.getBlockState(c.offset(0, 5, -Reflections.RING_R)).equals(Reflections.ring(0, 5, -Reflections.RING_R)), "the ring rose");
                    // a fall: you wake inside the door, and alone it starts over: the mirrors sleep, the ring goes down
                    sp.hurtServer(trials, sp.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                    h.assertTrue(sp.isAlive() && Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "a Proving never kills");
                    h.assertTrue(Math.abs(sp.getZ() - (c.getZ() + Reflections.WAKE_Z + 0.5)) < 1.5, "inside the door");
                    h.assertTrue(run.stage == Provings.Stage.WAITING && run.turns == 0, "it starts over");
                    h.assertTrue(Reflections.HALL.is(trials, c, Reflections.WALL_R, 8, 0), "the mirrors sleep");
                    h.assertTrue(trials.getBlockState(c.offset(0, 5, -Reflections.RING_R)).isAir(), "the ring went down");
                    step[0] = 5;
                }
                case 5 -> {
                    if (w == null) return;
                    h.assertTrue(Math.abs(w.getZ() - (c.getZ() + Reflections.RETURN_Z + 0.5)) < 3, "out of the north mirror");
                    w.kill(trials);
                    h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                    mark[0] = now;
                    step[0] = 6;
                }
                case 6 -> {
                    if (Trials.inTrial(sp)) return;
                    h.assertTrue(now - mark[0] >= Provings.OUTRO, "its gift before home");
                    h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                    h.assertTrue(Progression.rank(sp) == Rank.C, "rank C, was " + Progression.rank(sp));
                    step[0] = 7;
                }
                case 7 -> {
                    if (!Provings.settled()) return;
                    h.assertTrue(Reflections.HALL.is(trials, c, 3, 1, 0) && Reflections.HALL.is(trials, c, Reflections.WALL_R, 8, 0)
                            && trials.getBlockState(c.offset(0, 5, -Reflections.RING_R)).isAir(), "the hall is put back");
                    step[0] = 8;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> h.assertTrue(step[0] == 8, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
    }

    /** Onto the chancel's south end, as climbing the half-step would. */
    private static void onChancel(ServerPlayer sp, BlockPos c) {
        sp.teleportTo((ServerLevel) sp.level(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 20.5, Set.<Relative>of(), 180F, 0F, true);
    }
}
