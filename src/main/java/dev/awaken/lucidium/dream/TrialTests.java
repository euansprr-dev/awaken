package dev.awaken.lucidium.dream;

import dev.awaken.core.PlayDays;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.trial.Course;
import dev.awaken.lucidium.trial.CourseKit;
import dev.awaken.lucidium.trial.Trial;
import dev.awaken.lucidium.trial.TrialCompiler;
import dev.awaken.progression.Counters;
import dev.awaken.progression.LevelRoad;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** The trial dreams' GameTests (SPEC §9, docs/dreams/PLAN.md §9). GameTests have no Lucidium: dreams run in the test level. */
public final class TrialTests {
    private TrialTests() {}

    public static void register() {
        AwakenTests.register("dreams_runs_solvable", 20, TrialTests::runsSolvable);
        AwakenTests.register("dreams_waiting_ledger", 40, TrialTests::waitingLedger);
        AwakenTests.register("dreams_arrival", 60, TrialTests::arrival);
        AwakenTests.register("dreams_clock", 100, TrialTests::clock);
        AwakenTests.register("dreams_catch_never_wakes", 100, TrialTests::catchNeverWakes);
        AwakenTests.register("dreams_wake_button", 60, TrialTests::wakeButton);
        AwakenTests.register("dreams_win", 200, TrialTests::win);
        AwakenTests.register("dreams_move_remembered", 200, TrialTests::moveRemembered);
        AwakenTests.register("dreams_replay", 300, TrialTests::replay);
        AwakenTests.register("dreams_upgrade", 60, TrialTests::upgrade);
        RoomTests.register();
    }

    /** Every built trial passes its solver at its first variant, with a bell and without, and compiles into a course. */
    private static void runsSolvable(GameTestHelper h) {
        for (Trial t : DreamOffers.BUILT) {
            for (boolean bell : new boolean[]{false, true}) {
                CourseKit k = new CourseKit(0, TrialCompiler.BASE_Y, 9);
                if (t.run()) TrialCompiler.lay(k, t, 0, bell);
                else dev.awaken.lucidium.trial.RoomLayouts.lay(k, t, bell, bell, 7, false);
                int par = k.solve();
                h.assertTrue(par > 0, t.id + (bell ? " with a bell" : "") + " fails its solver: " + k.failure());
            }
            DreamPlan plan = DreamOffers.plan(7, java.util.UUID.randomUUID(), 3, 2, t.id, "", List.of(), "");
            Course c = TrialCompiler.compile(plan, 0, id -> java.util.Optional.empty()).course;
            h.assertTrue(c != null && c.trial() == t && c.par() > 0 && c.seconds() >= 60 && c.rings().size() >= 2,
                    t.id + " compiles into a course with rings and a clock");
            if (t.run() && t != Trial.FIRST)
                h.assertTrue(c.seconds() == Math.max(60, (int) Math.ceil(c.par() * 1.8 / 20.0)), t.id + "'s clock is its par x 1.8, at least 60 s");
        }
        h.assertTrue(Trial.FIRST.seconds == 75, "the first dream's clock is 75 s");
        h.succeed();
    }

    /**
     * An old save asleep mid-dream when the dreams changed (an island dream, no trial): woken where they lay, the ledger
     * seeded with the new first dream and Runs the body can already do, and nothing of the old dream to replay (SPEC
     * §9.6). (The island's flight given back is {@code restorePlayer}'s, unseen here: a mock player is always creative.)
     */
    private static void upgrade(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        try {
            ServerLevel level = h.getLevel();
            BlockPos bed = h.absolutePos(BlockPos.ZERO).above(2);
            Vec3 lay = Vec3.atBottomCenterOf(bed);
            Dreamer d = Dreams.dreamer(sp);
            d.dreams = 6; // dreamed before the change
            d.ledger.seeded = false;
            DreamState st = Dreams.state(sp);
            st.reset();
            st.active = true;
            st.cell = 4000; // nothing runs there after the restart
            st.returnDim = level.dimension().identifier().toString();
            st.rx = lay.x;
            st.ry = lay.y;
            st.rz = lay.z;
            st.lucidity = 80;
            sp.teleportTo(level, lay.x, lay.y + 30, lay.z, java.util.Set.of(), 0, 0, true);

            Dreams.wake(sp, Dreams.Wake.LOGIN);
            h.assertFalse(Dreams.isDreaming(sp), "woken");
            h.assertTrue(sp.position().distanceTo(lay) < 2, "back where they lay: " + sp.position() + " vs " + lay);

            Progression.count(sp, LevelRoad.flagCounter(DreamOffers.FLAG), 1);
            DreamOffers.check(sp);
            h.assertTrue(d.ledger.seeded && d.ledger.isWaiting(Trial.FIRST.id), "seeded: the new first dream waits");
            h.assertTrue(d.ledger.isWaiting(Trial.WALL.id), "and a Run the body can already do, without waiting a day");
            h.assertTrue(DreamOffers.replays(d).isEmpty(), "nothing of the old dream to replay");
            h.succeed();
        } finally {
            h.getLevel().getServer().getPlayerList().remove(sp);
        }
    }

    /** A dream comes only when one waits: one a night, not two nights running unless asked, one new dream a play-day. */
    private static void waitingLedger(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        try {
            DreamLedger l = Dreams.dreamer(sp).ledger;
            long day = PlayDays.current(h.getLevel().getServer());
            h.assertFalse(DreamOffers.check(sp) || !l.waiting.isEmpty(), "before the level road's step nothing waits");
            Sleep.Session none = Sleep.begin(sp);
            h.assertTrue(!none.roll && none.outcome == Sleep.Outcome.NO_DREAM, "nothing waiting: a normal night");

            Progression.count(sp, LevelRoad.flagCounter(DreamOffers.FLAG), 1);
            DreamOffers.check(sp);
            DreamLedger.Waiting first = l.waitingFor(Trial.FIRST.id);
            h.assertTrue(first != null && first.ref().equals("bell:dreaming"), "the first dream waits, carrying the Dreaming Bell");
            Sleep.Session night = Sleep.begin(sp);
            h.assertTrue(night.roll && night.trial.equals(Trial.FIRST.id), "a dream waits: tonight dreams it");
            Sleep.forget(sp);

            l.lastDreamDay = day;
            h.assertTrue(l.tonight(day) == null, "one a night");
            l.lastDreamDay = day - 1;
            h.assertTrue(l.tonight(day) == null, "not two nights running");
            l.choice = "run";
            l.choiceDay = day;
            h.assertTrue(l.tonight(day) != null, "unless the journal asked for one");
            l.choice = "";
            l.lastDreamDay = day - 2;
            h.assertTrue(l.tonight(day) != null, "a night off, and it comes");

            l.finish(Trial.FIRST.id, 600, 0, day, "bell:dreaming");
            h.assertFalse(l.isWaiting(Trial.FIRST.id) || !l.given.contains("bell:dreaming"), "finished: off the list, its bell given");
            DreamOffers.check(sp);
            h.assertTrue(l.isWaiting(Trial.WALL.id), "then the first Run comes to wait");
            DreamOffers.check(sp);
            h.assertFalse(l.isWaiting(Trial.LEDGE.id), "but only one new dream a play-day");
            h.assertTrue(l.offer(Trial.LEDGE.id, day + 1, "test", "", false), "the next day, the next");
            h.assertTrue(tasksLine(sp), "the Tasks page says a dream is waiting");
        } finally {
            Sleep.forget(sp);
            h.getLevel().getServer().getPlayerList().remove(sp);
        }
        h.succeed();
    }

    private static boolean tasksLine(ServerPlayer sp) {
        return DreamOffers.steps(sp).stream().anyMatch(s -> s.title().startsWith("A dream is waiting"));
    }

    // ---- in a dream ---------------------------------------------------------------------------------------------------

    /** Into tonight's trial (the first dream, no bell), dreamed in the test level. */
    private static ActiveDream enter(GameTestHelper h, ServerPlayer sp) {
        return enter(h, sp, Trial.FIRST);
    }

    static ActiveDream enter(GameTestHelper h, ServerPlayer sp, Trial trial) {
        return enter(h, sp, trial, "");
    }

    /** Into a trial carrying {@code ref} (a bell, a place, a fear). */
    static ActiveDream enter(GameTestHelper h, ServerPlayer sp, Trial trial, String ref) {
        ServerLevel level = h.getLevel();
        sp.setNoGravity(true);
        long day = PlayDays.current(level.getServer());
        Dreams.dreamer(sp).ledger.waiting.addFirst(new DreamLedger.Waiting(trial.id, day, "test", ref));
        DreamPlan plan = DreamOffers.plan(sp).orElseThrow();
        Planner.Prepared p = Planner.buildNow(level.getServer(), sp.getUUID(), plan);
        ActiveDream ad = Dreams.enter(sp, p, new Dreams.Entry(false, null, level, false));
        h.assertTrue(ad != null && Trials.is(ad), "entered a trial dream");
        // the test level has no dream generator: the doorway's glass is laid by hand to see it clear
        for (BlockPos pos : ad.bp.course.doorway()) level.setBlock(pos, LucidiumContent.DREAM_GLASS.get().defaultBlockState(), 3);
        return ad;
    }

    static void done(GameTestHelper h, ServerPlayer sp) {
        if (Dreams.isDreaming(sp)) Dreams.wake(sp, Dreams.Wake.API);
        h.getLevel().getServer().getPlayerList().remove(sp);
    }

    /** Arriving: the dream waits for H; H takes control (the doorway clears, the clock starts); H again glints. */
    private static void arrival(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ActiveDream ad = enter(h, sp);
        Course c = ad.bp.course;
        DreamState st = Dreams.state(sp);
        h.assertTrue(st.phase == 0 && st.trial.equals(Trial.FIRST.id), "arrived, waiting for H");
        h.assertTrue(sp.position().distanceTo(c.start()) < 1.5, "in your room at the start");
        h.assertFalse(ad.doorwayOpen, "the doorway is closed");
        h.assertTrue(st.endTick - Dreams.now(h.getLevel().getServer()) <= Trials.WAIT_MAX, "the night waits, but not forever");
        long lucid = Progression.profile(sp).counter(Counters.LUCID_DREAMS);
        Dreams.realityCheck(sp);
        long now = Dreams.now(h.getLevel().getServer());
        h.assertTrue(st.phase == 1 && ad.doorwayOpen, "H takes control and opens the doorway");
        for (BlockPos pos : c.doorway()) h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "the doorway's glass is gone");
        h.assertTrue(Progression.profile(sp).counter(Counters.LUCID_DREAMS) == lucid + 1, "a lucid dream counted");
        h.assertTrue(st.clockTotal == (c.seconds() + st.bonus) * 20 && st.clockEnd == now + st.clockTotal, "the clock: the dream's time and your seconds");
        long end = st.clockEnd;
        Dreams.realityCheck(sp);
        h.assertTrue(st.clockEnd == end - Trials.GLINT_COST, "H again: the path glints for 2 s of the clock");
        Dreams.realityCheck(sp);
        h.assertTrue(st.clockEnd == end - Trials.GLINT_COST, "once every 4 s");
        h.assertFalse(Dreams.state(sp).grantedFly, "no flight in a trial");
        done(h, sp);
        h.succeed();
    }

    /** The clock runs out: you wake, and the dream still waits. */
    private static void clock(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        enter(h, sp);
        Dreams.realityCheck(sp);
        DreamState st = Dreams.state(sp);
        st.clockEnd = Dreams.now(h.getLevel().getServer()) + 3;
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "the dream slipped away"))
                .thenExecute(() -> {
                    Dreamer d = Dreams.dreamer(sp);
                    h.assertTrue(d.ledger.isWaiting(Trial.FIRST.id) && !d.ledger.finished(Trial.FIRST.id), "and still waits");
                    h.assertTrue(d.history.getLast().summary().contains("slipped away") && d.history.getLast().written(), "the journal wrote it");
                    done(h, sp);
                })
                .thenSucceed();
    }

    /** A fall below the course is caught: back to the last ring, 3 s gone, still dreaming. */
    private static void catchNeverWakes(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ActiveDream ad = enter(h, sp);
        Course c = ad.bp.course;
        Dreams.realityCheck(sp);
        DreamState st = Dreams.state(sp);
        long end = st.clockEnd;
        for (int i = 0; i < 3; i++) {
            sp.teleportTo(h.getLevel(), c.start().x, c.floorY() - 20, c.start().z + 30, java.util.Set.of(), 0, 0, true);
            Trials.tick(sp, st, ad, Dreams.now(h.getLevel().getServer()));
        }
        h.assertTrue(Dreams.isDreaming(sp) && st.catches == 3, "three falls, three catches, still dreaming");
        h.assertTrue(sp.position().distanceTo(c.ring(st.ring).at()) < 1.5, "back at the last ring");
        h.assertTrue(st.clockEnd == end - 3 * Trials.CATCH_COST, "each cost 3 s");
        done(h, sp);
        h.succeed();
    }

    /** The pause screen's Wake up: out at once, nothing lost, the dream still waiting. */
    private static void wakeButton(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        enter(h, sp);
        h.assertTrue(LucidiumNet.wakeUp(sp), "Wake up handled");
        h.assertFalse(Dreams.isDreaming(sp), "awake");
        h.assertTrue(Dreams.dreamer(sp).ledger.isWaiting(Trial.FIRST.id), "the dream still waits");
        h.assertFalse(LucidiumNet.wakeUp(sp), "awake, the button does nothing");
        done(h, sp);
        h.succeed();
    }

    /** Reaching the door wins: the prize plays, you wake, the ledger keeps your time, the prize is in your pack. */
    private static void win(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ActiveDream ad = enter(h, sp);
        Course c = ad.bp.course;
        Dreams.realityCheck(sp);
        long finished = Progression.profile(sp).counter(Trials.FINISHED);
        Vec3 door = c.goal().getCenter();
        sp.teleportTo(h.getLevel(), door.x, c.goal().minY, door.z, java.util.Set.of(), 0, 0, true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Dreams.state(sp).phase == 2 || !Dreams.isDreaming(sp), "the door reached"))
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "the prize played, and you woke"))
                .thenExecute(() -> {
                    Dreamer d = Dreams.dreamer(sp);
                    h.assertTrue(d.ledger.finished(Trial.FIRST.id) && !d.ledger.isWaiting(Trial.FIRST.id), "finished, off the list");
                    h.assertTrue(Progression.profile(sp).counter(Trials.FINISHED) == finished + 1, "a dream finished counted");
                    h.assertTrue(sp.getInventory().countItem(LucidiumContent.DREAM_RESIDUE.get()) >= Trials.RESIDUE, "the residue came with you");
                    h.assertTrue(d.history.getLast().summary().contains("reached the end"), "the journal wrote your time");
                    h.assertTrue(Trials.recall(d) >= 2, "a finished dream is a second more in every dream after it");
                    done(h, sp);
                })
                .thenSucceed();
    }

    /** A Run finished: the body remembers its moves (THE BODY marks them), once; the morning card says how to do them awake. */
    private static void moveRemembered(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        h.assertFalse(dev.awaken.core.api.MovementHooks.remembered(sp, "wallrun"), "no dream has taught the wall-run yet");
        ActiveDream ad = enter(h, sp, Trial.WALL);
        Course c = ad.bp.course;
        h.assertTrue(c.trial() == Trial.WALL, "the wall's dream");
        h.assertTrue(Trials.prizeLine(c, ad, Dreams.dreamer(sp)).equals("Your body remembers the wall-run."), "the prize says what the body keeps");
        Dreams.realityCheck(sp);
        Vec3 door = c.goal().getCenter();
        sp.teleportTo(h.getLevel(), door.x, c.goal().minY, door.z, java.util.Set.of(), 0, 0, true);
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "won, and woke"))
                .thenExecute(() -> {
                    Dreamer d = Dreams.dreamer(sp);
                    h.assertTrue(d.ledger.remembered.contains(Trial.WALL.id), "the ledger knows the wall was remembered");
                    h.assertTrue(d.ledger.ghosts.containsKey(Trial.WALL.id), "the best run is kept as a ghost");
                    int g = DreamLedger.pack(-3.25, 41.5, 160.75);
                    h.assertTrue(DreamLedger.unpackX(g) == -3.25 && DreamLedger.unpackY(g) == 41.5 && DreamLedger.unpackZ(g) == 160.75, "a ghost's sample packs and unpacks");
                    h.assertTrue(dev.awaken.core.api.MovementHooks.remembered(sp, "wallrun") && dev.awaken.core.api.MovementHooks.remembered(sp, "walljump"),
                            "THE BODY marks the wall-run and the wall jump");
                    h.assertFalse(dev.awaken.core.api.MovementHooks.remembered(sp, "hang"), "and nothing else");
                    h.assertFalse(Trials.remember(sp, Trial.WALL, d, false, false), "a move is remembered once");
                    h.assertTrue(Trials.prizeLine(c, ad, d).equals("You made it to the end of the dream."), "the second time, just the end");
                    done(h, sp);
                })
                .thenSucceed();
    }
    /**
     * Replays (SPEC §9.6): a finished trial is listed to dream again; dreamt again it carries nothing (no bell), spends
     * neither tonight's dream nor the Journal's choice, takes nothing off the waiting list, and counts its time.
     */
    private static void replay(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        long day = PlayDays.current(level.getServer());
        Dreamer d = Dreams.dreamer(sp);
        ActiveDream first = enter(h, sp, Trial.WALL);
        h.assertTrue(d.ledger.lastDreamDay == day, "tonight's dream is spent on it");
        Dreams.realityCheck(sp);
        Vec3 door = first.bp.course.goal().getCenter();
        sp.teleportTo(level, door.x, first.bp.course.goal().minY, door.z, java.util.Set.of(), 0, 0, true);
        int[] before = new int[2];
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "won, and woke"))
                .thenExecute(() -> {
                    List<String> list = dev.awaken.lucidium.Lucidium.dreams(sp);
                    h.assertTrue(list.size() == 1 && list.getFirst().startsWith("The wall: best "), "the wall is there to dream again: " + list);
                    d.ledger.lastDreamDay = day - 5;
                    d.ledger.choice = "run";
                    d.ledger.choiceDay = day;
                    d.ledger.waiting.add(new DreamLedger.Waiting(Trial.WALL.id, day, "test", "bell:test"));
                    before[0] = sp.getInventory().countItem(LucidiumContent.DREAM_RESIDUE.get());
                    before[1] = d.ledger.finished.get(Trial.WALL.id).times();
                    DreamPlan plan = DreamOffers.replayPlan(sp, Trial.WALL.id);
                    h.assertTrue(plan.trialRef().equals(DreamLedger.REPLAY), "a replay's plan says so");
                    ActiveDream ad = Dreams.enter(sp, Planner.buildNow(level.getServer(), sp.getUUID(), plan), new Dreams.Entry(false, null, level, false));
                    h.assertTrue(ad != null && Trials.replay(ad) && ad.bp.course.bell() == null, "dreamt again, with no bell in it");
                    h.assertTrue(d.ledger.lastDreamDay == day - 5 && d.ledger.asked(day), "a replay spends neither tonight's dream nor the Journal's choice");
                    h.assertFalse(dev.awaken.lucidium.Lucidium.replay(sp, 0), "no replay inside a dream");
                    Dreams.realityCheck(sp);
                    Vec3 end = ad.bp.course.goal().getCenter();
                    sp.teleportTo(level, end.x, ad.bp.course.goal().minY, end.z, java.util.Set.of(), 0, 0, true);
                })
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "the replay won, and woke"))
                .thenExecute(() -> {
                    h.assertTrue(d.ledger.finished.get(Trial.WALL.id).times() == before[1] + 1, "the replay's time counts");
                    h.assertTrue(d.ledger.waitingFor(Trial.WALL.id) != null, "what waits stays waiting");
                    h.assertTrue(sp.getInventory().countItem(LucidiumContent.DREAM_RESIDUE.get()) == before[0], "no residue twice in a day");
                    done(h, sp);
                })
                .thenSucceed();
    }
}
