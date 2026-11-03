package dev.awaken.lucidium.dream;

import dev.awaken.core.PlayDays;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.lucidium.Lucidium;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.trial.Course;
import dev.awaken.lucidium.trial.Trial;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The Rooms, the Chase and the Duel (SPEC §9.5): one test per rule's mechanism, the hidden place, a shared Room. */
final class RoomTests {
    private RoomTests() {}

    static void register() {
        AwakenTests.register("dreams_room_rules", 300, RoomTests::roomRules);
        AwakenTests.register("dreams_hidden_place", 200, RoomTests::hiddenPlace);
        AwakenTests.register("dreams_shared", 200, RoomTests::shared);
    }

    private static long now(GameTestHelper h) {
        return Dreams.now(h.getLevel().getServer());
    }

    /** Each rule's own mechanism, driven as a player would; the Duel last, through to its morning. */
    private static void roomRules(GameTestHelper h) {
        Set<Trial> homes = new HashSet<>();
        for (String b : List.of("dreaming", "daydream", "lullaby", "falling", "nightmare", "lucid")) homes.add(DreamOffers.bellTrial(b));
        h.assertTrue(homes.size() == 6 && !homes.contains(null), "each of the six dream bells has a dream of its own");

        // what's wrong here: a guess at nothing costs 5 s; each wrong thing looked at and named; the third wins
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        ActiveDream ad = TrialTests.enter(h, sp, Trial.WRONG);
        DreamState st = Dreams.state(sp);
        Dreams.realityCheck(sp);
        Course.Room r = ad.bp.course.room();
        h.assertTrue(r != null && r.things().size() == 3 && ad.roomGoal == null, "three wrong things, and no way out yet");
        long end = st.clockEnd;
        Vec3 mid = Vec3.atBottomCenterOf(r.centre());
        RoomRules.lookFrom(sp, mid, mid.add(0, -0.5, -12));
        Dreams.realityCheck(sp);
        h.assertTrue(st.clockEnd == end - RoomRules.WRONG_COST, "nothing wrong there: 5 seconds gone");
        for (int i = 0; i < 3; i++) DreamOffers.solve(sp);
        h.assertTrue(Integer.bitCount(ad.roomFound) == 3 && st.phase == 2, "all three named, and the room is won");
        TrialTests.done(h, sp);

        // look away: it moves the moment it's out of view, somewhere else on the floor
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.LOOK_AWAY, "bell:daydream");
        Dreams.realityCheck(sp);
        r = ad.bp.course.room();
        BlockPos g0 = ad.roomGoal;
        h.assertTrue(g0 != null && !RoomRules.touched(ad, new AABB(g0)), "a bell's room: the bell is taken by striking it, not touched");
        Lucidium.DreamBell bell = Lucidium.dreamBell(sp);
        h.assertTrue(bell != null && bell.id().equals("daydream") && g0.equals(bell.at()), "the bells see where the dream keeps its bell");
        RoomRules.lookFrom(sp, Vec3.atBottomCenterOf(r.centre()).add(0, 0, -8), Vec3.atCenterOf(g0));
        RoomRules.lookAway(sp, ad, now(h));
        h.assertTrue(ad.roomSeen && g0.equals(ad.roomGoal), "in view, it stays");
        Dreams.moveWithin(sp, sp.position(), sp.getYRot() + 180, 0);
        RoomRules.lookAway(sp, ad, now(h));
        BlockPos g1 = ad.roomGoal;
        h.assertTrue(g1 != null && !g1.equals(g0) && Vec3.atCenterOf(g1).distanceTo(Vec3.atCenterOf(r.centre())) <= r.half(),
                "looked away: it moved, and it's still on the floor");
        bell = Lucidium.dreamBell(sp);
        h.assertTrue(bell != null && g1.equals(bell.at()), "and the bell went with it");
        TrialTests.done(h, sp);

        // hush: creeping close is quiet; walking close wakes it, and the door's somewhere else
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.HUSH);
        Dreams.realityCheck(sp);
        g0 = ad.roomGoal;
        Dreams.moveWithin(sp, Vec3.atBottomCenterOf(g0).add(0, 0, -6), 0, 0);
        sp.setShiftKeyDown(true);
        RoomRules.hush(sp, ad, now(h));
        h.assertTrue(g0.equals(ad.roomGoal), "creeping: it sleeps on");
        sp.setShiftKeyDown(false);
        RoomRules.hush(sp, ad, now(h));
        h.assertTrue(ad.roomGoal != null && !g0.equals(ad.roomGoal), "walking up to it: it woke, and the door moved");
        TrialTests.done(h, sp);

        // the long fall: eyes down, it lifts you; eyes up, there's somewhere to land
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.LONG_FALL);
        st = Dreams.state(sp);
        Dreams.realityCheck(sp);
        r = ad.bp.course.room();
        h.assertTrue(ad.roomGoal == null && RoomRules.extraDepth(ad.bp.course) == RoomRules.FALL_DEPTH, "no landing yet, and a deep fall before any catch");
        Vec3 falling = new Vec3(r.centre().getX() + 0.5, r.edgeY() - 20, r.centre().getZ() + 6.5);
        Dreams.moveWithin(sp, falling, 0, 80);
        sp.fallDistance = 12;
        sp.setOnGround(false);
        RoomRules.fall(sp, st, ad, r, now(h));
        h.assertTrue(sp.getY() > falling.y + RoomRules.LIFT - 1 && ad.roomGoal == null, "watching the fall, the dream lifts you back up");
        Dreams.moveWithin(sp, falling, 0, -30);
        sp.fallDistance = 12;
        sp.setOnGround(false);
        RoomRules.fall(sp, st, ad, r, now(h));
        h.assertTrue(ad.roomLanded && ad.roomGoal != null && ad.roomGoal.getY() < falling.y, "eyes up: a landing below you, and the door on it");
        TrialTests.done(h, sp);

        // the fold: walk on and the hallway comes back; walk backwards past its door and the door opens
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.FOLD);
        st = Dreams.state(sp);
        Dreams.realityCheck(sp);
        r = ad.bp.course.room();
        double cx = r.centre().getX() + 0.5;
        Dreams.moveWithin(sp, new Vec3(cx, r.edgeY(), r.loopTo() + 1.5), 0, 0);
        st.lastX = sp.getX();
        st.lastZ = sp.getZ() - 0.2;
        RoomRules.fold(sp, st, ad, r, now(h));
        h.assertTrue(sp.getZ() < r.loopTo() && ad.roomLoops == 1 && !ad.roomOpen, "walked on past the end: the hallway came back");
        BlockPos door = r.things().getFirst();
        Dreams.moveWithin(sp, new Vec3(cx, r.edgeY(), door.getZ() + 0.8), 0, 0);
        st.lastX = sp.getX();
        st.lastZ = sp.getZ() + 0.25;
        RoomRules.fold(sp, st, ad, r, now(h));
        h.assertTrue(ad.roomOpen, "walked backwards past the door: it opened");
        TrialTests.done(h, sp);

        // no door: a door in the wrong wall leads into more wall; the right wall's is the way out
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.NO_DOOR);
        Dreams.realityCheck(sp);
        r = ad.bp.course.room();
        int wrong = (r.right() + 1) % r.walls().size();
        Vec3 wc = r.walls().get(wrong).getCenter();
        RoomRules.lookFrom(sp, Vec3.atBottomCenterOf(r.centre()), new Vec3(wc.x, r.centre().getY() + 1.6, wc.z));
        Dreams.realityCheck(sp);
        h.assertTrue((ad.roomFound & 1 << wrong) != 0 && !ad.roomOpen, "the wrong wall: a door into a wall");
        DreamOffers.solve(sp);
        h.assertTrue(ad.roomOpen && (ad.roomFound & 1 << r.right()) != 0, "the right wall: the way out");
        TrialTests.done(h, sp);

        // the Chase: the fear it waited for; carrying a bell, the door won't let you by without it
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.CHASE, "fear:burning");
        h.assertTrue(RoomRules.fear(ad, sp) == Nightmares.Family.BURNING, "the Chase is the fear that waited");
        TrialTests.done(h, sp);
        sp = h.makeMockServerPlayerInLevel();
        ad = TrialTests.enter(h, sp, Trial.CHASE, "bell:nightmare");
        st = Dreams.state(sp);
        Dreams.realityCheck(sp);
        h.assertTrue(Trials.goal(ad.bp.course, ad).contains("carrying a bell"), "the goal line says the fear carries the bell");
        AABB goal = ad.bp.course.goal();
        Dreams.moveWithin(sp, new Vec3(goal.getCenter().x, goal.minY, goal.getCenter().z), 0, 0);
        Trials.tick(sp, st, ad, now(h));
        h.assertTrue(st.phase == 1, "at the door without the bell: not yet");
        TrialTests.done(h, sp);

        // the Duel: worn down, 10 s gone and again; the mentor yields, it's won, and the lesson comes with the morning
        ServerPlayer duel = h.makeMockServerPlayerInLevel();
        ActiveDream da = TrialTests.enter(h, duel, Trial.DUEL);
        DreamState ds = Dreams.state(duel);
        Dreams.realityCheck(duel);
        DreamOffers.solve(duel);
        h.assertTrue(da.mentorState != -1, "into the ring: your mentor");
        da.mentorState = 1;
        long clock = ds.clockEnd;
        Trials.duelEnded(da, duel, false);
        h.assertTrue(ds.clockEnd == clock - Trials.DUEL_COST && da.mentorState == -1 && ds.phase == 1, "worn down: 10 s gone, and again");
        da.roomNext = 0;
        DreamOffers.solve(duel);
        da.mentorState = 1;
        long duels = dev.awaken.progression.Progression.profile(duel).counter(Mentors.MENTOR_DUELS_WON);
        DreamOffers.solve(duel);
        h.assertTrue(ds.phase == 2, "the mentor yields: won");
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(duel), "the prize played, and you woke"))
                .thenExecute(() -> {
                    h.assertTrue(dev.awaken.progression.Progression.profile(duel).counter(Mentors.MENTOR_DUELS_WON) == duels + 1, "the lesson counted");
                    h.assertTrue(Dreams.dreamer(duel).ledger.finished(Trial.DUEL.id), "the Duel is in the ledger");
                    TrialTests.done(h, duel);
                })
                .thenSucceed();
    }

    /** A Room hiding a real place: shown at the end, on the map and in the Journal's Visions when you wake; the next in turn. */
    private static void hiddenPlace(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Dreamer d = Dreams.dreamer(sp);
        DreamLedger l = d.ledger;
        long day = PlayDays.current(h.getLevel().getServer());
        d.putVision(new Vision("t_place", "t_place", "a ruined portal", "minecraft:overworld", new BlockPos(400, 70, -900), "", day, -1, false));
        l.lastNewDay = Long.MIN_VALUE / 2;
        h.assertTrue(DreamOffers.offerPlace(d, l, day), "a place worth finding: a Room comes to wait");
        DreamLedger.Waiting w = l.waiting.getLast();
        h.assertTrue(w.trial().equals(DreamOffers.PLACE_ROOMS.getFirst().id) && w.ref().equals("vision:t_place"), "the first in turn, carrying the place");
        l.waiting.remove(w);
        ActiveDream ad = TrialTests.enter(h, sp, Trial.FOLD, "vision:t_place");
        DreamPlan plan = ad.bp.plan;
        h.assertTrue(plan.vision().equals(Optional.of("t_place")) && plan.nodes().stream().anyMatch(n -> DreamPlan.VISION.equals(n.kind())),
                "tonight's plan holds the place");
        Dreams.realityCheck(sp);
        DreamOffers.solve(sp);
        AABB goal = ad.bp.course.goal();
        Dreams.moveWithin(sp, new Vec3(goal.getCenter().x, goal.minY, goal.getCenter().z), 0, 0);
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(sp), "through the door, the place seen, and awake"))
                .thenExecute(() -> {
                    Vision v = d.vision("t_place").orElseThrow();
                    h.assertTrue(v.shown() && !v.hint().isEmpty(), "the place is in the Journal's Visions, with which way it lies");
                    h.assertTrue(l.given.contains("vision:t_place") && !l.isWaiting(Trial.FOLD.id), "shown once, off the list");
                    d.putVision(new Vision("t_place2", "t_place2", "a desert well", "minecraft:overworld", new BlockPos(-300, 64, 500), "", day, -1, false));
                    l.lastNewDay = Long.MIN_VALUE / 2;
                    h.assertTrue(DreamOffers.offerPlace(d, l, day) && l.isWaiting(DreamOffers.PLACE_ROOMS.get(1).id), "the next place, in the next Room");
                    d.putVision(d.vision("t_place2").orElseThrow().withShown(day, "test"));
                    DreamOffers.dropStale(sp, d, l, day);
                    h.assertFalse(l.isWaiting(DreamOffers.PLACE_ROOMS.get(1).id), "a place already shown no longer waits");
                    TrialTests.done(h, sp);
                })
                .thenSucceed();
    }

    /** Two in a Room: won for everyone in control; a guest's own waiting dream carrying something else stays waiting. */
    private static void shared(GameTestHelper h) {
        ServerPlayer host = h.makeMockServerPlayerInLevel(), guest = h.makeMockServerPlayerInLevel();
        long day = PlayDays.current(h.getLevel().getServer());
        Dreams.dreamer(guest).ledger.waiting.add(new DreamLedger.Waiting(Trial.NO_DOOR.id, day, "test", "vision:theirs"));
        ActiveDream ad = TrialTests.enter(h, host, Trial.NO_DOOR);
        guest.setNoGravity(true);
        h.assertTrue(Dreams.join(guest, ad, new Dreams.Entry(false, null, h.getLevel(), false)), "the guest joins the dream");
        Dreams.realityCheck(host);
        Dreams.realityCheck(guest);
        DreamOffers.solve(host);
        h.assertTrue(ad.roomOpen, "the host made the door");
        AABB goal = ad.bp.course.goal();
        Dreams.moveWithin(host, new Vec3(goal.getCenter().x, goal.minY, goal.getCenter().z), 0, 0);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Dreams.state(guest).phase == 2 || !Dreams.isDreaming(guest), "won for the guest too"))
                .thenWaitUntil(() -> h.assertFalse(Dreams.isDreaming(host) || Dreams.isDreaming(guest), "both woke"))
                .thenExecute(() -> {
                    h.assertTrue(Dreams.dreamer(host).ledger.finished(Trial.NO_DOOR.id) && Dreams.dreamer(guest).ledger.finished(Trial.NO_DOOR.id),
                            "both finished it");
                    h.assertTrue(Dreams.dreamer(guest).ledger.isWaiting(Trial.NO_DOOR.id), "the guest's own place still waits for them");
                    TrialTests.done(h, host);
                    TrialTests.done(h, guest);
                })
                .thenSucceed();
    }
}
