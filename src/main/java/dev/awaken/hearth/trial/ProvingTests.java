package dev.awaken.hearth.trial;

import dev.awaken.core.api.Promises;
import dev.awaken.core.api.TrialHooks;
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

/** The Provings (SPEC §46.4, §46.6 to §46.10): each hall's layout, and each Warden's whole flow in a hall built for the test. */
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
        AwakenTests.registerIsolated("proving_unmade", 3000, ProvingTests::unmade);
        AwakenTests.registerIsolated("proving_sky_warden", 3000, ProvingTests::skyWarden);
        AwakenTests.registerIsolated("proving_keepers_shadow", 3000, ProvingTests::keeper);
        AwakenTests.registerIsolated("proving_last_loop", 3000, ProvingTests::lastLoop);
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

    /**
     * The third Proving (SPEC §46.7): the Broken Span's layout; then the whole flow in a Span built for the test: you
     * wake high over the bastion; once you're on it the north arm lifts and the Unmade lands out of its stones; at two
     * thirds the ring falls (the road south stays), at a third the lamps go out (the gatehouse's stay); over the edge
     * you wake at the south end and the Span is whole again; the win is rank B; the Span is put back after.
     */
    private static void unmade(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(Span.block(0, 0, 0) != null && Span.block(0, 1, 0) == null, "you stand on the bastion's floor");
        h.assertTrue(Span.block(0, 1, Span.ARCH_N) == null && Span.block(0, 6, Span.ARCH_N) == null && Span.block(0, 7, Span.ARCH_N) != null
                && Span.block(7, 3, Span.ARCH_N) != null, "the arch is open under its crown, the towers either side");
        h.assertTrue(Span.falls(14, 0) && Span.falls(0, -15) && !Span.falls(0, 15) && !Span.falls(0, 10), "the ring falls, the road south and the middle stay");
        h.assertTrue(Span.block(0, 0, Span.WAKE_Z) != null && Span.block(0, 1, Span.WAKE_Z) == null && Span.block(0, 2, Span.WAKE_Z) == null,
                "you wake on the south arm's road");
        h.assertTrue(Span.block(0, 0, Span.OUT_Z) != null, "the road goes on past the gatehouse");
        h.assertTrue(UnmadeWarden.sector(0, -10) == 0 && UnmadeWarden.sector(10, 0) == 2 && UnmadeWarden.sector(0, 10) == 4, "the eighths count from the north");

        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel home = sp.level();
        sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
        Progression.awaken(sp);
        Progression.passReassessment(sp);
        Progression.passReassessment(sp);
        double need = 1;
        for (int l = 1; l < Rank.B.levelRequired; l++) need += Curve.xpToNext(l);
        Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
        h.assertTrue(Progression.rank(sp) == Rank.C && Progression.profile(sp).reassessmentReady(), "ready for B");
        h.assertTrue(Trials.Kind.forTarget(Rank.B) == Trials.Kind.PROVING, "B is a Proving");
        BlockPos c = h.absolutePos(new BlockPos(0, 60, 0)); // the Span reaches 40 down: well clear of the test's ground
        Provings.Run run = Provings.openAt(sp, home, c, Rank.B, false);
        h.assertTrue(run.warden == UnmadeWarden.WARDEN && run.room instanceof UnmadeWarden.Gather, "the Unmade's Span");
        ServerLevel trials = run.level;
        int[] step = {0};
        long[] mark = {0};
        h.onEachTick(() -> {
            long now = trials.getGameTime();
            LivingEntity w = Provings.warden(run);
            UnmadeWarden.Gather g = (UnmadeWarden.Gather) run.room;
            switch (step[0]) {
                case 0 -> {
                    if (run.stage != Provings.Stage.WAITING) return;
                    h.assertTrue(sp.getY() > c.getY() + 10 && sp.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "you wake high, falling slowly");
                    h.assertTrue(Span.HALL.is(trials, c, 0, 0, -40), "the north arm stands");
                    onBastion(sp, c);
                    step[0] = 1;
                }
                case 1 -> {
                    if (g.liftedAt < 0) return;
                    h.assertTrue(trials.getBlockState(c.offset(0, 0, -40)).isAir() && !g.stones.isEmpty(), "the north arm lifted into stones");
                    h.assertTrue(Span.HALL.is(trials, c, 0, 0, 0), "the bastion stays");
                    step[0] = 2;
                }
                case 2 -> {
                    if (w == null) return;
                    h.assertTrue(Math.abs(w.getZ() - (c.getZ() + Span.LAND_Z + 0.5)) < 2 && g.stones.isEmpty(), "it lands on the north rim out of its stones");
                    w.setHealth(w.getMaxHealth() * 0.6F);
                    mark[0] = now;
                    step[0] = 3;
                }
                case 3 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 1, "at two thirds the ring cracks");
                    if (run.turnAt >= 0) return;
                    h.assertTrue(trials.getBlockState(c.offset(15, 0, 0)).isAir() && trials.getBlockState(c.offset(0, 0, -15)).isAir(), "the ring fell");
                    h.assertTrue(Span.HALL.is(trials, c, 0, 0, 15) && Span.HALL.is(trials, c, 0, 0, 10), "the road south and the middle stand");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.3F);
                    mark[0] = now;
                    step[0] = 4;
                }
                case 4 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 2, "at a third the lamps go out");
                    if (run.turnAt >= 0) return;
                    h.assertTrue(trials.getBlockState(c.offset(5, 4, 44)).isAir(), "the south arm's lamps are out");
                    h.assertTrue(trials.getBlockState(c.offset(6, 5, Span.ARCH_N - 1)).is(Blocks.LANTERN), "the gatehouse's lamps stay");
                    h.assertTrue(trials.getBlockState(c.offset(3, 3, 3)).equals(Span.lightOf(Span.DARK)), "the bastion's light is low");
                    // over the edge: you wake at the south end, and alone it starts over, the Span whole again
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + UnmadeWarden.VOID - 1, c.getZ() + 15.5, Set.<Relative>of(), 0F, 0F, true);
                    mark[0] = now;
                    step[0] = 5;
                }
                case 5 -> {
                    if (now - mark[0] < 2) return;
                    h.assertTrue(sp.isAlive() && Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "going over the edge is a fall");
                    h.assertTrue(Math.abs(sp.getZ() - (c.getZ() + Span.WAKE_Z + 0.5)) < 1.5 && sp.getY() > c.getY(), "you wake on the south arm");
                    h.assertTrue(run.stage == Provings.Stage.WAITING && run.turns == 0, "it starts over");
                    h.assertTrue(Span.HALL.is(trials, c, 15, 0, 0) && Span.HALL.is(trials, c, 0, 0, -40) && Span.HALL.is(trials, c, 5, 4, 44)
                            && Span.HALL.is(trials, c, 3, 3, 3), "the ring, the north arm and the lamps are back");
                    onBastion(sp, c);
                    step[0] = 6;
                }
                case 6 -> {
                    if (w == null) return;
                    w.kill(trials);
                    h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                    mark[0] = now;
                    step[0] = 7;
                }
                case 7 -> {
                    if (Trials.inTrial(sp)) return;
                    h.assertTrue(now - mark[0] >= Provings.OUTRO, "the Span mends before home");
                    h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                    h.assertTrue(Progression.rank(sp) == Rank.B, "rank B, was " + Progression.rank(sp));
                    step[0] = 8;
                }
                case 8 -> {
                    if (!Provings.settled()) return;
                    h.assertTrue(Span.HALL.standing(trials, c) && Span.HALL.is(trials, c, 15, 0, 0) && Span.HALL.is(trials, c, 5, 4, 44),
                            "the Span is put back");
                    step[0] = 9;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> h.assertTrue(step[0] == 9, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
    }

    private static void skyWarden(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(Cathedral.block(0, 0, 0) != null && Cathedral.block(0, 1, 0) == null, "you stand on the font");
        h.assertTrue(Cathedral.block(0, Cathedral.CLOUD_TOP, 0) != null && Cathedral.block(0, Cathedral.WAKE_DY, 0) == null
                && Cathedral.block(0, Cathedral.WAKE_DY + 1, 0) == null, "you wake on the cloud under it");
        h.assertTrue(Cathedral.block(0, -1, 0) != null && Cathedral.block(1, -5, 0) == null && Cathedral.block(1, -18, 0) == null,
                "the shaft runs up under the font's cap");
        h.assertTrue(Cathedral.block(0, 1, Cathedral.WEST) == null && Cathedral.block(0, 5, Cathedral.WEST) == null
                && Cathedral.block(0, 7, Cathedral.WEST) != null && Cathedral.block(0, 0, Cathedral.OUT_Z) != null, "the west door opens onto the porch");
        h.assertTrue(Cathedral.block(0, 0, Cathedral.DOOR_Z) != null && Cathedral.block(0, 1, Cathedral.DOOR_Z) == null
                && Cathedral.block(0, 2, Cathedral.DOOR_Z) == null, "you wake inside the door");
        h.assertTrue(Cathedral.falls(12, 0) && !Cathedral.falls(5, 0) && !Cathedral.falls(0, Cathedral.OUT_Z), "the aisles fall, the nave and porch stay");
        h.assertTrue(Cathedral.lifts(0, 24, 0) && !Cathedral.lifts(8, 9, -16) && !Cathedral.lifts(12, 14, 0), "the roof lifts over the arches");
        h.assertTrue(Cathedral.block(8, 20, -20) != null && Cathedral.block(8, 5, -16) != null, "a clerestory window over a column");

        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel home = sp.level();
        sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
        Progression.awaken(sp);
        for (int k = 0; k < 3; k++) Progression.passReassessment(sp);
        double need = 1;
        for (int l = 1; l < Rank.A.levelRequired; l++) need += Curve.xpToNext(l);
        Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
        h.assertTrue(Progression.rank(sp) == Rank.B && Progression.profile(sp).reassessmentReady(), "ready for A");
        h.assertTrue(Trials.Kind.forTarget(Rank.A) == Trials.Kind.PROVING, "A is a Proving");
        // what the Ascent is asked for (the sky at the roof, the gift at the end), still passed on to it
        TrialHooks.Trials was = TrialHooks.installed();
        Set<String> asked = new java.util.HashSet<>();
        TrialHooks.install(new TrialHooks.Trials() {
            @Override
            public boolean atEdge(ServerPlayer p, String trial) {
                asked.add("edge " + trial);
                return was.atEdge(p, trial);
            }

            @Override
            public void done(ServerPlayer p, String trial, int ticks, int medal) {
                asked.add("done " + trial);
                was.done(p, trial, ticks, medal);
            }
        });
        BlockPos c = h.absolutePos(new BlockPos(0, 60, 0)); // the cloud is 30 down: well clear of the test's ground
        Provings.Run run = Provings.openAt(sp, home, c, Rank.A, false);
        h.assertTrue(run.warden == SkyWarden.WARDEN && run.room instanceof SkyWarden.Sky, "the Sky Warden's cathedral");
        ServerLevel trials = run.level;
        Hall hall = Cathedral.HALL;
        int[] step = {0};
        long[] mark = {0};
        h.onEachTick(() -> {
            long now = trials.getGameTime();
            LivingEntity w = Provings.warden(run);
            SkyWarden.Sky s = SkyWarden.sky(run);
            switch (step[0]) {
                case 0 -> {
                    if (run.stage != Provings.Stage.WAITING) return;
                    h.assertTrue(sp.getY() < c.getY() - 20, "you wake on the cloud");
                    h.assertTrue(hall.is(trials, c, 0, 0, 0) && hall.is(trials, c, 12, 0, 0), "the font is shut, the aisles stand");
                    step[0] = 1;
                }
                case 1 -> {
                    if (s.liftAt < 0) return;
                    h.assertTrue(trials.getBlockState(c).isAir() && sp.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION),
                            "the font opens and the light lifts you");
                    // up the shaft (a mock player doesn't move by itself)
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + 1.5, c.getZ() + 0.5, Set.<Relative>of(), 180F, 0F, true);
                    step[0] = 2;
                }
                case 2 -> {
                    if (!s.lifted) return;
                    h.assertTrue(hall.is(trials, c, 0, 0, 0) && hall.is(trials, c, 0, -1, 0), "the font shuts again");
                    h.assertTrue(sp.getY() >= c.getY() + 0.9 && !sp.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION), "you're up, on it");
                    step[0] = 3;
                }
                case 3 -> {
                    if (w == null) return;
                    h.assertTrue(Math.abs(w.getZ() - (c.getZ() + Cathedral.LAND_Z + 0.5)) < 2 && w.getY() < c.getY() + 3, "it comes down in the nave");
                    w.setHealth(w.getMaxHealth() * 0.6F);
                    mark[0] = now;
                    step[0] = 4;
                }
                case 4 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 1, "at two thirds the aisles go");
                    if (run.turnAt >= 0) return;
                    h.assertTrue(trials.getBlockState(c.offset(12, 0, 0)).isAir() && trials.getBlockState(c.offset(-12, 0, 0)).isAir(), "the aisles fell");
                    h.assertTrue(hall.is(trials, c, 5, 0, 0) && hall.is(trials, c, 8, 5, -16) && hall.is(trials, c, 8, 20, -20), "the nave and its arcades stand");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.3F);
                    mark[0] = now;
                    step[0] = 5;
                }
                case 5 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 2 && asked.contains("edge " + TrialHooks.SKY_WARDEN), "at a third the roof lifts and the sky is given");
                    if (run.turnAt >= 0) return;
                    h.assertTrue(trials.getBlockState(c.offset(8, 20, -20)).isAir(), "the roof lifted");
                    h.assertTrue(hall.is(trials, c, 8, 5, -16) && hall.is(trials, c, 3, 3, 3), "the columns and the nave's light stay");
                    // over the edge: you wake inside the door, and alone it starts over, the cathedral whole again
                    sp.teleportTo(trials, c.getX() + 15.5, c.getY() + SkyWarden.VOID - 1, c.getZ() + 0.5, Set.<Relative>of(), 0F, 0F, true);
                    mark[0] = now;
                    step[0] = 6;
                }
                case 6 -> {
                    if (now - mark[0] < 2) return;
                    h.assertTrue(sp.isAlive() && Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "going over the edge is a fall");
                    h.assertTrue(Math.abs(sp.getZ() - (c.getZ() + Cathedral.DOOR_Z + 0.5)) < 1.5 && sp.getY() > c.getY(), "you wake inside the door");
                    h.assertTrue(run.stage == Provings.Stage.WAITING && run.turns == 0, "it starts over");
                    step[0] = 7;
                }
                case 7 -> {
                    if (s.mend != null) return;
                    h.assertTrue(hall.is(trials, c, 12, 0, 0) && hall.is(trials, c, 8, 20, -20) && hall.is(trials, c, 0, 0, 0),
                            "the aisles and the roof are back, the font shut");
                    // up the nave: it comes again
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + 1, c.getZ() + 10.5, Set.<Relative>of(), 180F, 0F, true);
                    step[0] = 8;
                }
                case 8 -> {
                    if (w == null) return;
                    w.kill(trials);
                    h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                    mark[0] = now;
                    step[0] = 9;
                }
                case 9 -> {
                    if (Trials.inTrial(sp)) return;
                    h.assertTrue(now - mark[0] >= Provings.OUTRO, "the cathedral mends before home");
                    h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                    h.assertTrue(Progression.rank(sp) == Rank.A, "rank A, was " + Progression.rank(sp));
                    h.assertTrue(asked.contains("done " + TrialHooks.SKY_WARDEN), "the sky's gift");
                    step[0] = 10;
                }
                case 10 -> {
                    if (!Provings.settled()) return;
                    h.assertTrue(hall.standing(trials, c) && hall.is(trials, c, 12, 0, 0) && hall.is(trials, c, 8, 20, -20), "the cathedral is put back");
                    TrialHooks.install(was);
                    step[0] = 11;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> h.assertTrue(step[0] == 11, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
    }

    private static void keeper(net.minecraft.gametest.framework.GameTestHelper h) {
        // the hall as laid out: you wake on the landing over the stair, the court's floor is open, the braziers stand on
        // it, the Court's door is shut and opens all the way at its last step, the dark never reaches the inner ring
        h.assertTrue(Threshold.block(0, Threshold.LANDING, Threshold.WAKE_Z) != null && Threshold.block(0, Threshold.WAKE_DY, Threshold.WAKE_Z) == null
                && Threshold.block(0, Threshold.WAKE_DY + 1, Threshold.WAKE_Z) == null, "you wake on the landing");
        h.assertTrue(Threshold.block(0, 0, 0) != null && Threshold.block(0, 1, 0) == null && Threshold.block(0, 3, 0) == null, "the court is open");
        h.assertTrue(Threshold.block(19, 3, 0) == Threshold.FIRE && Threshold.block(-13, 3, -13) == Threshold.FIRE, "the braziers");
        h.assertTrue(Threshold.block(0, 5, Threshold.LEAF_S) != null && Threshold.gdLeaf(0, 5, 0) != null
                && Threshold.gdLeaf(0, 5, KeeperWarden.DOOR_STEPS) == null && Threshold.gdLeaf(3, 5, KeeperWarden.DOOR_STEPS) == null,
                "the Court's door is shut and opens all the way");
        h.assertTrue(Threshold.block(0, Threshold.LANDING, Threshold.OUT_Z) != null && Threshold.block(0, Threshold.WAKE_DY, Threshold.OUT_Z) == null,
                "the arch behind the landing is open");
        int[][][] bands = Threshold.darkBands();
        boolean inside = false;
        for (int[][] b : bands) for (int[] p : b) inside |= Threshold.r(p[0], p[2]) < Threshold.LIGHT_R;
        h.assertTrue(bands.length > 4 && !inside, "the dark stops at the inner ring");
        h.assertTrue(KeeperWarden.lane(0, -20, 1) && !KeeperWarden.lane(0, -12, 1) && KeeperWarden.lane(0, -12, 2) && !KeeperWarden.lane(4, -20, 3),
                "the light's lane grows with the door");

        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel home = sp.level();
        sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
        Progression.awaken(sp);
        for (int k = 0; k < 4; k++) Progression.passReassessment(sp);
        double need = 1;
        for (int l = 1; l < Rank.S.levelRequired; l++) need += Curve.xpToNext(l);
        Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
        h.assertTrue(Progression.rank(sp) == Rank.A && Progression.profile(sp).reassessmentReady(), "ready for S");
        h.assertTrue(Trials.Kind.forTarget(Rank.S) == Trials.Kind.PROVING, "S is a Proving");
        BlockPos c = h.absolutePos(new BlockPos(0, 40, 0)); // the stair's foot is 27 down: clear of the test's ground
        Provings.Run run = Provings.openAt(sp, home, c, Rank.S, false);
        h.assertTrue(run.warden == KeeperWarden.WARDEN && run.room instanceof KeeperWarden.Court, "the Keeper's court");
        ServerLevel trials = run.level;
        Hall hall = Threshold.HALL;
        int[] spot = bands[3][0];
        BlockPos dark = c.offset(spot[0], 0, spot[2]), lane = c.offset(0, 0, -22), shut = c.offset(0, 5, Threshold.LEAF_S);
        int[] leaf = Threshold.leaves()[2][0];
        BlockPos door = c.offset(leaf[0], leaf[1], leaf[2]);
        int[] step = {0};
        long[] mark = {0};
        h.onEachTick(() -> {
            long now = trials.getGameTime();
            LivingEntity w = Provings.warden(run);
            KeeperWarden.Court k = KeeperWarden.court(run);
            switch (step[0]) {
                case 0 -> {
                    if (run.stage != Provings.Stage.WAITING) return;
                    h.assertTrue(sp.getY() > c.getY() + Threshold.LANDING && sp.getZ() > c.getZ() + Threshold.R, "you wake on the landing");
                    h.assertTrue(!lit(trials, c.offset(19, 3, 0)) && !trials.getBlockState(door).isAir(), "the braziers are out, the doors shut");
                    step[0] = 1;
                }
                case 1 -> {
                    if (k.lit < Threshold.BRAZIERS.length) return;
                    for (int[] b : Threshold.BRAZIERS) h.assertTrue(lit(trials, c.offset(b[0], 3, b[1])), "the braziers light themselves");
                    intoCourt(sp, c);
                    step[0] = 2;
                }
                case 2 -> {
                    if (k.poolAt < 0) return;
                    h.assertTrue(k.pool.z < sp.getZ() - 2 && Math.abs(k.pool.x - sp.getX()) < 1, "your shadow pools in front of you");
                    step[0] = 3;
                }
                case 3 -> {
                    if (w == null) return;
                    h.assertTrue(w.position().distanceTo(k.pool) < 2, "it stands up out of the pool");
                    w.setHealth(w.getMaxHealth() * 0.7F);
                    mark[0] = now;
                    step[0] = 4;
                }
                case 4 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 1, "at three quarters the doors go");
                    if (k.sunk < KeeperWarden.SINK_ROWS) return;
                    for (int[][] d : Threshold.leaves()) for (int[] p : d) h.assertTrue(trials.getBlockState(c.offset(p[0], p[1], p[2])).isAir(), "the doors sank");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.45F);
                    mark[0] = now;
                    step[0] = 5;
                }
                case 5 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 2, "at half the dark comes in");
                    if (k.dark < bands.length) return;
                    h.assertTrue(trials.getBlockState(dark).is(Blocks.SCULK) && !trials.getBlockState(c).is(Blocks.SCULK), "the dark stops at the ring");
                    sp.teleportTo(trials, dark.getX() + 0.5, dark.getY() + 1, dark.getZ() + 0.5, Set.<Relative>of(), 0F, 0F, true);
                    step[0] = 6;
                }
                case 6 -> {
                    sp.setOnGround(true); // a mock player never lands by itself
                    if (!sp.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) return;
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.2F);
                    mark[0] = now;
                    step[0] = 7;
                }
                case 7 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 3 && k.door >= 1, "from a quarter the Court's door opens");
                    h.assertTrue(trials.getBlockState(shut).isAir() && !trials.getBlockState(lane).is(Blocks.SCULK), "its light burns the dark back");
                    // over the stair's wall: you wake on the landing, and alone it starts over, the court whole again
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + KeeperWarden.VOID - 1, c.getZ() + 0.5, Set.<Relative>of(), 0F, 0F, true);
                    mark[0] = now;
                    step[0] = 8;
                }
                case 8 -> {
                    if (now - mark[0] < 2) return;
                    h.assertTrue(sp.isAlive() && Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "going over is a fall");
                    h.assertTrue(sp.getY() > c.getY() + Threshold.LANDING, "you wake on the landing");
                    h.assertTrue(run.stage == Provings.Stage.WAITING && run.turns == 0, "it starts over");
                    step[0] = 9;
                }
                case 9 -> {
                    if (k.reset != null) return;
                    h.assertTrue(!trials.getBlockState(door).isAir() && !trials.getBlockState(shut).isAir() && !trials.getBlockState(dark).is(Blocks.SCULK)
                            && lit(trials, c.offset(19, 3, 0)), "the doors are back, the dark gone, the braziers burning");
                    intoCourt(sp, c);
                    step[0] = 10;
                }
                case 10 -> {
                    if (w == null) return;
                    w.kill(trials);
                    h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                    mark[0] = now;
                    step[0] = 11;
                }
                case 11 -> {
                    if (k.door < KeeperWarden.DOOR_STEPS) return;
                    h.assertTrue(trials.getBlockState(shut).isAir() && trials.getBlockState(c.offset(3, 5, Threshold.LEAF_S)).isAir(), "the Court's door opens");
                    step[0] = 12;
                }
                case 12 -> {
                    if (Trials.inTrial(sp)) return;
                    h.assertTrue(now - mark[0] >= Provings.OUTRO, "the door stands open before home");
                    h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                    h.assertTrue(Progression.rank(sp) == Rank.S, "rank S, was " + Progression.rank(sp));
                    step[0] = 13;
                }
                case 13 -> {
                    if (!Provings.settled()) return;
                    h.assertTrue(hall.standing(trials, c) && !trials.getBlockState(shut).isAir() && !trials.getBlockState(door).isAir(), "the court is put back");
                    step[0] = 14;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> h.assertTrue(step[0] == 14, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
    }

    private static void lastLoop(net.minecraft.gametest.framework.GameTestHelper h) {
        // the hall as laid out: you wake on the floor by the arch, the Loop is gold in the floor, the outer ring falls but
        // not the causeway you wake on, the Seed's door is shut and opens all the way at its last step
        h.assertTrue(Antechamber.block(0, 0, Antechamber.WAKE_Z) != null && Antechamber.block(0, 1, Antechamber.WAKE_Z) == null
                && Antechamber.block(0, 2, Antechamber.WAKE_Z) == null, "you wake on the floor");
        h.assertTrue(Antechamber.block(0, 0, (int) Antechamber.LOOP_R).is(Blocks.GOLD_BLOCK) && Antechamber.block(0, 1, 0) == null, "the Loop in the floor");
        h.assertTrue(Antechamber.falls(0, -19) && Antechamber.falls(19, 0) && !Antechamber.falls(0, Antechamber.WAKE_Z)
                && !Antechamber.falls(2, Antechamber.WAKE_Z + 2) && !Antechamber.falls(0, -12), "the ring falls, not the causeway or the middle");
        for (int[][] hour : Antechamber.ring()) for (int[] p : hour) h.assertTrue(Antechamber.falls(p[0], p[2]), "only the ring falls");
        h.assertTrue(Antechamber.block(0, Antechamber.DOOR_DY, Antechamber.DOOR_Z) != null && Antechamber.doorLeaf(0, 1, LoopWarden.DOOR_STEPS - 1, 0) != null
                && Antechamber.doorLeaf(0, 1, LoopWarden.DOOR_STEPS, 0) == null && Antechamber.doorLeaf(2, 1, LoopWarden.DOOR_STEPS, 0) == null,
                "the Seed's door is shut and opens all the way");
        h.assertTrue(Antechamber.block(0, Antechamber.DOOR_DY, Antechamber.DOOR_Z + 1) == null && Antechamber.block(4, 2, Antechamber.DOOR_Z + 1) == null,
                "nothing stands in front of the door");

        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel home = sp.level();
        sp.setGameMode(GameType.CREATIVE); // never its target: the test strikes and falls by hand
        Progression.awaken(sp);
        for (int k = 0; k < 5; k++) Progression.passReassessment(sp);
        double need = 1;
        for (int l = 1; l < Rank.SOVEREIGN.levelRequired; l++) need += Curve.xpToNext(l);
        Progression.grantXp(sp, need / dev.awaken.core.AwakenConfig.xpMultiplier(), Progression.XpSource.COMMAND);
        h.assertTrue(Progression.rank(sp) == Rank.S && Progression.profile(sp).reassessmentReady(), "ready for Sovereign");
        h.assertTrue(Trials.Kind.forTarget(Rank.SOVEREIGN) == Trials.Kind.PROVING, "Sovereign is a Proving");
        BlockPos c = h.absolutePos(new BlockPos(0, 24, 0)); // the floor's underside is 9 down: clear of the test's ground
        Provings.Run run = Provings.openAt(sp, home, c, Rank.SOVEREIGN, false);
        h.assertTrue(run.warden == LoopWarden.WARDEN && run.room instanceof LoopWarden.Loop, "the antechamber");
        ServerLevel trials = run.level;
        Hall hall = Antechamber.HALL;
        BlockPos plate = c.offset(0, 0, -12), ring = c.offset(0, 0, -19), causeway = c.offset(0, 0, 19), core = c.offset(0, Antechamber.DOOR_DY, Antechamber.DOOR_Z);
        int[] step = {0};
        long[] mark = {0};
        LoopWarden.Replay[] replay = {null};
        h.onEachTick(() -> {
            long now = trials.getGameTime();
            LivingEntity w = Provings.warden(run);
            LoopWarden.Loop l = LoopWarden.loop(run);
            switch (step[0]) {
                case 0 -> {
                    if (run.stage != Provings.Stage.WAITING) return;
                    h.assertTrue(sp.getZ() > c.getZ() + Antechamber.RING_IN, "you wake by the arch");
                    h.assertTrue(!trials.getBlockState(core).isAir() && trials.getBlockState(plate).equals(Antechamber.face(0, -12, 12)), "the door shut, the day in");
                    mark[0] = now;
                    step[0] = 1;
                }
                case 1 -> {
                    // walk north into the Loop: its path is the one you walked
                    if (l.drawAt >= 0) {
                        h.assertTrue(l.arrive.z > sp.getZ() + 4 && Math.abs(l.arrive.x - sp.getX()) < 1 && l.drawn.size() >= 2, "it'll stand where you were");
                        step[0] = 2;
                        return;
                    }
                    double z = c.getZ() + Antechamber.WAKE_Z + 0.5 - (now - mark[0]) * 0.25;
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + 1, z, Set.<Relative>of(), 180F, 0F, true);
                    h.assertTrue(z > c.getZ() + 2, "walking into the Loop wakes it");
                }
                case 2 -> {
                    if (w == null) return;
                    h.assertTrue(w.position().distanceTo(l.arrive) < 2, "it stands where you were");
                    w.setHealth(w.getMaxHealth() * 0.7F);
                    mark[0] = now;
                    step[0] = 3;
                }
                case 3 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 1 && l.replayAt > now, "at three quarters night falls, and the replays are coming");
                    if (l.swept < 12) return;
                    h.assertTrue(trials.getBlockState(plate).equals(Antechamber.night(0, -12, 12)), "night swept round the floor");
                    l.replayAt = now; // the replay now, not in five seconds
                    step[0] = 4;
                }
                case 4 -> {
                    // standing still on your own trail: its run of light reaches you
                    if (replay[0] == null) {
                        if (l.replays.isEmpty()) return;
                        replay[0] = l.replays.getFirst();
                    }
                    if (now - replay[0].at <= LoopWarden.REPLAY_TELL + LoopWarden.REPLAY_RUN + 1) return;
                    h.assertTrue(replay[0].hit.contains(sp.getUUID()) && l.replays.isEmpty(), "your own trail runs you down");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.45F);
                    mark[0] = now;
                    step[0] = 5;
                }
                case 5 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 2, "at half the ring cracks");
                    if (l.fallen < 12) return;
                    h.assertTrue(trials.getBlockState(ring).isAir() && trials.getBlockState(c.offset(19, 0, 0)).isAir(), "the outer ring fell");
                    h.assertTrue(!trials.getBlockState(causeway).isAir() && !trials.getBlockState(plate).isAir(), "the causeway and the middle stay");
                    if (w == null) return;
                    w.setHealth(w.getMaxHealth() * 0.2F);
                    mark[0] = now;
                    step[0] = 6;
                }
                case 6 -> {
                    if (now - mark[0] < 1) return;
                    h.assertTrue(run.turns == 3 && l.door >= 1 && l.turning, "from a quarter the Seed's door turns and opens");
                    h.assertTrue(trials.getBlockState(core).isAir(), "its core is gone");
                    // over the edge where the ring fell: you wake by the arch, and alone it starts over, the hall whole again
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + LoopWarden.VOID - 1, c.getZ() - 19.5, Set.<Relative>of(), 0F, 0F, true);
                    mark[0] = now;
                    step[0] = 7;
                }
                case 7 -> {
                    if (now - mark[0] < 2) return;
                    h.assertTrue(sp.isAlive() && Trials.inTrial(sp) && run.falls.getOrDefault(sp.getUUID(), 0) == 1, "going over is a fall");
                    h.assertTrue(sp.getZ() > c.getZ() + Antechamber.RING_IN && sp.getY() > c.getY(), "you wake by the arch");
                    h.assertTrue(run.stage == Provings.Stage.WAITING && run.turns == 0, "it starts over");
                    step[0] = 8;
                }
                case 8 -> {
                    if (l.reset != null) return;
                    h.assertTrue(!trials.getBlockState(ring).isAir() && !trials.getBlockState(core).isAir()
                            && trials.getBlockState(plate).equals(Antechamber.face(0, -12, 12)), "the ring, the door and the day are back");
                    sp.teleportTo(trials, c.getX() + 0.5, c.getY() + 1, c.getZ() + 4.5, Set.<Relative>of(), 180F, 0F, true);
                    step[0] = 9;
                }
                case 9 -> {
                    if (w == null) return;
                    w.kill(trials);
                    h.assertTrue(run.stage == Provings.Stage.WON, "beaten");
                    mark[0] = now;
                    step[0] = 10;
                }
                case 10 -> {
                    if (l.door < LoopWarden.DOOR_STEPS) return;
                    h.assertTrue(trials.getBlockState(c.offset(0, 1, Antechamber.DOOR_Z)).isAir() && trials.getBlockState(c.offset(0, 0, -10)).equals(Antechamber.PATH),
                            "the Seed's door opens, a path of light to it");
                    step[0] = 11;
                }
                case 11 -> {
                    if (Trials.inTrial(sp)) return;
                    h.assertTrue(now - mark[0] >= Provings.OUTRO, "the door stands open before home");
                    h.assertTrue(Trials.lastResult(sp.getUUID()).orElse(null) == Trials.State.PASSED, "passed");
                    h.assertTrue(Progression.rank(sp) == Rank.SOVEREIGN, "Sovereign, was " + Progression.rank(sp));
                    step[0] = 12;
                }
                case 12 -> {
                    if (!Provings.settled()) return;
                    h.assertTrue(hall.standing(trials, c) && !trials.getBlockState(core).isAir() && !trials.getBlockState(ring).isAir(), "the hall is put back");
                    step[0] = 13;
                }
                default -> {}
            }
        });
        h.succeedWhen(() -> h.assertTrue(step[0] == 13, "the Proving ran through, at step " + step[0] + ", stage " + run.stage));
    }

    private static boolean lit(ServerLevel level, BlockPos at) {
        var s = level.getBlockState(at);
        return s.is(Blocks.SOUL_CAMPFIRE) && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
    }

    /** Down the stair into the court, looking north to the Court's door. */
    private static void intoCourt(ServerPlayer sp, BlockPos c) {
        sp.teleportTo((ServerLevel) sp.level(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 8.5, Set.<Relative>of(), 180F, 0F, true);
    }

    /** Down on the bastion, south of its middle, as landing from the fall would. */
    private static void onBastion(ServerPlayer sp, BlockPos c) {
        sp.teleportTo((ServerLevel) sp.level(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 4.5, Set.<Relative>of(), 0F, 0F, true);
    }

    /** Onto the chancel's south end, as climbing the half-step would. */
    private static void onChancel(ServerPlayer sp, BlockPos c) {
        sp.teleportTo((ServerLevel) sp.level(), c.getX() + 0.5, c.getY() + 1, c.getZ() + 20.5, Set.<Relative>of(), 180F, 0F, true);
    }
}
