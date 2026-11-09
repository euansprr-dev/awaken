package dev.awaken.riding;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.RunEvents;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.powers.Powers;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** GameTests for Riding Along (SPEC §55): one per mechanism. CI runs them. */
final class RidingTests {
    private RidingTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        AwakenTests.registerIsolated(name, 120, h -> {
            ServerLevel level = h.getLevel();
            Difficulty was = level.getDifficulty();
            level.getServer().setDifficulty(Difficulty.NORMAL, true); // monsters don't attack players on Peaceful
            GateService.useTestLevel(level);
            try {
                body.accept(h);
            } finally {
                Bonds.clear();
                level.getServer().setDifficulty(was, true);
            }
            h.succeed();
        });
    }

    static void register() {
        test("bond_needs_both", RidingTests::needsBoth);
        test("bond_door", RidingTests::door);
        test("bond_not_mid_fight", RidingTests::notMidFight);
        test("bond_one_rider_per_run", RidingTests::oneRider);
        test("bond_hits_forty", RidingTests::hitsForty);
        test("bond_fold_catches", RidingTests::foldCatches);
        test("bond_anchor_dies", RidingTests::anchorDies);
        test("bond_no_scaling", RidingTests::noScaling);
        test("bond_no_aggro", RidingTests::noAggro);
        test("bond_no_rank", RidingTests::noRank);
        test("bond_range", RidingTests::range);
        test("bond_left_on_logout", RidingTests::logout);
        test("bond_goals_step", RidingTests::goalsStep);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    private static int made;

    /** A vanilla mock player is creative for good: nothing can hurt it and no monster will have it. */
    private static ServerPlayer survivor(GameTestHelper h) {
        ServerPlayer p = AwakenTests.survivalPlayer(h, "bond-" + ++made, new BlockPos(0, 2, 0)).player();
        Progression.awaken(p);
        return p;
    }

    private static ServerPlayer at(GameTestHelper h, int x) {
        ServerPlayer p = survivor(h);
        BlockPos b = h.absolutePos(new BlockPos(x, 2, 0));
        p.teleportTo(h.getLevel(), b.getX() + 0.5, b.getY(), b.getZ() + 0.5, Set.of(), 0F, 0F, true);
        return p;
    }

    private static void tie(GameTestHelper h, ServerPlayer rider, ServerPlayer anchor) {
        Bonds.form(rider, anchor);
        h.assertTrue(Bonds.rider(rider) && Bonds.bonded(anchor) && !Bonds.rider(anchor), "the bond is made");
    }

    private static void remove(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) if (p != null) h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static GatePlan plan(long seed) {
        return GateLiveTests.plan(seed, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
    }

    private static void teardown(GameTestHelper h, GateRecord g, ServerPlayer... ps) {
        for (int i = 1; i < ps.length; i++) {
            if (ps[i] != null && Runs.run(ps[i]).active()) Gates.forceExtract(ps[i], false);
        }
        GateLiveTests.teardown(h.getLevel(), ps.length > 0 ? ps[0] : null, g);
        remove(h, ps);
    }

    private static Room firstCombat(FloorLayout f) {
        return GateLiveTests.biggestCombat(f);
    }

    private static boolean kept(GameTestHelper h, ServerPlayer p) {
        boolean claimed = GateRegistry.get(h.getLevel().getServer()).pendingFor(p.getUUID()).stream()
                .anyMatch(c -> c.items().stream().anyMatch(s -> s.is(Items.DIAMOND)));
        return claimed || p.getInventory().hasAnyMatching(s -> s.is(Items.DIAMOND));
    }

    private static void kill(GameTestHelper h, ServerPlayer p) {
        p.setInvulnerableTime(0);
        p.damageCooldownTime = 0; // 26.3's i-frames
        p.setHealth(1F);
        p.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 100_000F);
    }

    // ---- consent --------------------------------------------------------------------------------------------------------

    private static void needsBoth(GameTestHelper h) {
        ServerPlayer a = at(h, 0), b = at(h, 2);
        try {
            String on = "riding:on:" + a.getUUID() + ":" + b.getUUID();
            Signal.answer(a, on, true);
            h.assertFalse(Bonds.bonded(a), "an answer to a question nobody asked counts for nothing");
            Bonds.ask(a, b, true);
            h.assertFalse(Bonds.bonded(a), "asking isn't enough");
            Signal.answer(a, on, true);
            h.assertFalse(Bonds.bonded(a), "and the asker can't answer for the other");
            Signal.answer(b, on, true);
            h.assertTrue(Bonds.rider(a) && Bonds.bonded(b), "both agreed");

            String off = "riding:off:" + a.getUUID();
            Bonds.letGo(a);
            h.assertTrue(Bonds.bonded(a), "letting go takes two as well");
            Signal.answer(b, off, false);
            h.assertTrue(Bonds.bonded(a), "not yet keeps it");
            Bonds.letGo(a);
            Signal.answer(b, off, true);
            h.assertFalse(Bonds.bonded(a), "both let go");

            tie(h, a, b);
            Bonds.letGo(b);
            Bonds.runOutLettingGoForTest();
            Bonds.everySecond(h.getLevel().getServer());
            h.assertFalse(Bonds.bonded(a), "no answer in 30 seconds counts as yes");
        } finally {
            remove(h, a, b);
        }
    }

    private static void door(GameTestHelper h) {
        ServerPlayer a = at(h, 0), b = at(h, 2), far = at(h, 30);
        try {
            Bonds.door(a);
            String choose = "riding:door:" + a.getUUID() + ":" + b.getUUID();
            h.assertTrue(Signal.isPending(a, choose), "the door asks who rides, and offers the player beside you");
            Signal.answer(a, choose, false); // "b rides with me"
            String on = "riding:on:" + b.getUUID() + ":" + a.getUUID();
            h.assertTrue(Signal.isPending(b, on), "then b is asked");
            Signal.answer(b, on, true);
            h.assertTrue(Bonds.rider(b) && Bonds.bonded(a), "b rides with a");
            Bonds.door(a);
            h.assertTrue(Signal.isPending(b, "riding:off:" + a.getUUID()), "already tied, the door lets go");
            h.assertFalse(Signal.isPending(far, choose), "and nobody else was asked");
        } finally {
            remove(h, a, b, far);
        }
    }

    private static void notMidFight(GameTestHelper h) {
        ServerPlayer a = at(h, 0), b = at(h, 2);
        Zombie z = null;
        try {
            tie(h, a, b);
            z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2));
            z.setTarget(b);
            h.assertTrue(Powers.inCombat(b), "a monster is on them");
            Bonds.letGo(a);
            h.assertTrue(Bonds.bonded(a) && !Signal.isPending(b, "riding:off:" + a.getUUID()), "no letting go in a fight");
            Bonds.clear();
            h.assertTrue(Bonds.whyNot(a, b) != null, "and no tying on in one");
            Bonds.ask(a, b, true);
            h.assertFalse(Signal.isPending(b, "riding:on:" + a.getUUID() + ":" + b.getUUID()), "nothing is asked");
        } finally {
            if (z != null) z.discard();
            remove(h, a, b);
        }
    }

    private static void oneRider(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null, c = null, d = null;
        try {
            g = GateLiveTests.standUp(h, plan(5501), 0, 1);
            a = survivor(h);
            b = survivor(h);
            c = survivor(h);
            d = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            h.assertTrue(Runs.run(a).riding && !Runs.run(b).riding, "the run knows who rides");
            tie(h, c, d);
            h.assertFalse(Runs.enter(c, g), "a second rider can't enter");
            h.assertTrue(Runs.enter(d, g), "the gate is still open to anyone not riding");
        } finally {
            teardown(h, g, a, b, c, d);
        }
    }

    // ---- what a rider does ---------------------------------------------------------------------------------------------

    private static float blow(GameTestHelper h, Zombie z, ServerPlayer by) {
        z.setInvulnerableTime(0);
        z.damageCooldownTime = 0; // 26.3's i-frames: a second blow inside them only lands what it has over the last
        float before = z.getHealth();
        z.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(by), 10F);
        return before - z.getHealth();
    }

    private static void hitsForty(GameTestHelper h) {
        ServerPlayer a = at(h, 0), b = at(h, 2);
        Zombie z = null;
        try {
            tie(h, a, b);
            z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2));
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            float full = blow(h, z, b), rode = blow(h, z, a);
            h.assertTrue(full > 0, "the anchor's blow lands");
            h.assertTrue(Math.abs(rode / full - 0.4F) < 0.01F, "a rider's hit counts for 40%, got " + rode / full);
        } finally {
            if (z != null) z.discard();
            remove(h, a, b);
        }
    }

    private static void foldCatches(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null;
        try {
            g = GateLiveTests.standUp(h, plan(5502), 0, 1);
            a = survivor(h);
            b = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            Room r = firstCombat(f);
            GateLiveTests.put(h.getLevel(), a, g, f, r);
            GateLiveTests.put(h.getLevel(), b, g, f, r);
            Runs.run(a).satchel.add(new ItemStack(Items.DIAMOND));
            Runs.run(a).satchelXp = 42;
            long fractureBefore = Progression.profile(a).fractureUntil();

            kill(h, a);
            h.assertTrue(a.isAlive() && !a.isDeadOrDying(), "the blow didn't kill them");
            h.assertTrue(Fold.folded(a.getUUID()), "they're in the partner's shadow");
            h.assertTrue(Math.abs(a.getHealth() - a.getMaxHealth() * Fold.HEALTH_BACK) < 0.01F, "half health straight away");
            h.assertTrue(Runs.run(a).active() && Runs.run(a).satchelXp == 42 && !Runs.run(a).satchel.isEmpty(), "the run and the Satchel are untouched");
            h.assertTrue(Progression.profile(a).fractureUntil() == fractureBefore, "no Fracture");

            h.assertFalse(a.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 5F), "nothing can touch them in the shadow");
            a.teleportTo(h.getLevel(), a.getX() + 9, a.getY(), a.getZ(), Set.of(), 0F, 0F, true);
            Fold.elapseForTest(h.getLevel().getServer(), a.getUUID());
            h.assertFalse(Fold.folded(a.getUUID()), "four seconds on, they're back");
            h.assertTrue(a.distanceToSqr(b) < 16, "beside their partner, wherever they fell");
            h.assertFalse(a.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 5F), "and a moment's grace");
        } finally {
            teardown(h, g, a, b);
        }
    }

    private static void anchorDies(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null;
        try {
            g = GateLiveTests.standUp(h, plan(5503), 0, 1);
            a = survivor(h);
            b = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            Room r = firstCombat(f);
            GateLiveTests.put(h.getLevel(), a, g, f, r);
            GateLiveTests.put(h.getLevel(), b, g, f, r);
            Runs.run(a).satchel.add(new ItemStack(Items.DIAMOND));
            long fractureBefore = Progression.profile(a).fractureUntil();
            kill(h, b);
            h.assertFalse(Bonds.bonded(a), "the bond ends with the anchor");
            Bonds.tick(h.getLevel().getServer());
            h.assertTrue(a.isAlive(), "the rider never goes down");
            h.assertFalse(Runs.run(a).active(), "they're brought out of the gate");
            h.assertTrue(kept(h, a), "with their Satchel");
            h.assertTrue(Progression.profile(a).fractureUntil() == fractureBefore, "and no Fracture");
        } finally {
            teardown(h, g, a, b);
        }
    }

    private static void noScaling(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null;
        try {
            g = GateLiveTests.standUp(h, plan(5504), 0, 1);
            a = survivor(h);
            b = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            Runs.run(a).floor = 1;
            Runs.run(b).floor = 1;
            h.assertTrue(Rooms.partyOnFloor(h.getLevel(), g, f) == 1, "a rider doesn't count towards the party");
            Bonds.letGo(a);
            Signal.answer(b, "riding:off:" + a.getUUID(), true);
            h.assertTrue(Rooms.partyOnFloor(h.getLevel(), g, f) == 2, "and counts again once the thread lets go");
        } finally {
            teardown(h, g, a, b);
        }
    }

    private static void noAggro(GameTestHelper h) {
        ServerPlayer a = at(h, 0), b = at(h, 4);
        Zombie z = null;
        try {
            tie(h, a, b);
            z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2));
            z.setTarget(a);
            h.assertTrue(z.getTarget() == b, "with the anchor in sight, the monster goes for the anchor");
            b.teleportTo(h.getLevel(), b.getX() + 40, b.getY(), b.getZ(), Set.of(), 0F, 0F, true);
            z.setTarget(a);
            h.assertTrue(z.getTarget() == a, "alone, a rider can still be hunted");
            kill(h, a);
            h.assertTrue(Fold.folded(a.getUUID()), "caught");
            RidingEvents.sweep(a);
            h.assertTrue(z.getTarget() == null, "nothing chases what isn't there");
        } finally {
            if (z != null) z.discard();
            remove(h, a, b);
        }
    }

    // ---- what a rider earns --------------------------------------------------------------------------------------------

    private static void noRank(GameTestHelper h) {
        GateRecord g = null, high = null;
        ServerPlayer a = null, b = null, c = null;
        try {
            g = GateLiveTests.standUp(h, plan(5505), 0, 1);
            a = survivor(h);
            b = survivor(h);
            c = at(h, 6);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            GatePlan plan = g.plan;
            double full = Runs.floorReward(b, g, 0, 1), rode = Runs.floorReward(a, g, 0, 1);
            h.assertTrue(Math.abs(rode - full * 0.5) < 0.001, "half the floor's pay, got " + rode + " of " + full);

            GatePlan s = new GatePlan(5506, GatePlan.CONTENT_VERSION, Rank.S, plan.theme(), plan.layout(), List.of(), Twist.NONE, 3, plan.theme().bossId(), Theme.HIVE, 0);
            high = GateLiveTests.standUp(h, s, 0, 1);
            double own = Curve.floorReward(Progression.rank(a), 1) * 0.5;
            h.assertTrue(Math.abs(Runs.floorReward(a, high, 0, 1) - own) < 0.001, "riding above your rank pays no more than your own rank would");

            long before = Progression.profile(a).counter(Counters.GATES_CLEARED);
            Runs.lordDefeated(h.getLevel(), g, GateLayout.of(plan).floor(0, 1), null, a);
            h.assertTrue(Progression.profile(a).counter(Counters.GATES_CLEARED) == before, "a rider's clear earns no rank credit");
            h.assertTrue(Progression.profile(b).counter(Counters.GATES_CLEARED) == 1, "the anchor's does");

            double xpA = Progression.profile(a).xp(), xpC = Progression.profile(c).xp();
            Progression.worldXp(a, "bond_test", 0.01);
            Progression.worldXp(c, "bond_test", 0.01);
            double gotA = Progression.profile(a).xp() - xpA, gotC = Progression.profile(c).xp() - xpC;
            h.assertTrue(gotC > 0 && Math.abs(gotA / gotC - 0.5) < 0.05, "half the XP out in the world, got " + gotA + " of " + gotC);

            h.assertFalse(Trials.start(a, Trials.Kind.DUEL, Rank.D, false), "a rider can't take a Reassessment");
            h.assertFalse(Trials.start(b, Trials.Kind.DUEL, Rank.D, false), "nor can the one they ride with");
        } finally {
            teardown(h, g, a, b);
            if (high != null) GateLiveTests.teardown(h.getLevel(), null, high);
            remove(h, c);
        }
    }

    // ---- the reach and the end -----------------------------------------------------------------------------------------

    private static void range(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null;
        try {
            g = GateLiveTests.standUp(h, plan(5507), 0, 1);
            a = survivor(h);
            b = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            Room r = firstCombat(f);
            GateLiveTests.put(h.getLevel(), a, g, f, r);
            b.teleportTo(h.getLevel(), a.getX() + 60, a.getY() + 20, a.getZ(), Set.of(), 0F, 0F, true);
            h.assertFalse(Bonds.inReach(a, b), "past the thread's reach");
            Runs.run(a).satchel.add(new ItemStack(Items.DIAMOND));
            long fractureBefore = Progression.profile(a).fractureUntil();
            kill(h, a);
            h.assertFalse(Fold.folded(a.getUUID()), "nothing caught them");
            h.assertTrue(a.isDeadOrDying(), "so they went down");
            h.assertTrue(kept(h, a), "but a rider never loses the Satchel");
            h.assertTrue(Progression.profile(a).fractureUntil() == fractureBefore, "and takes no Fracture");
            h.assertFalse(Bonds.bonded(a) || Bonds.bonded(b), "the bond is over");
        } finally {
            teardown(h, g, a, b);
        }
    }

    private static void logout(GameTestHelper h) {
        GateRecord g = null;
        ServerPlayer a = null, b = null, c = null, d = null;
        try {
            g = GateLiveTests.standUp(h, plan(5508), 0, 1);
            a = survivor(h);
            b = survivor(h);
            tie(h, a, b);
            h.assertTrue(Runs.enter(a, g) && Runs.enter(b, g), "they go in");
            Bonds.loggedOut(b);
            h.assertFalse(Bonds.bonded(a), "the anchor logging off ends the bond");
            Bonds.tick(h.getLevel().getServer());
            h.assertFalse(Runs.run(a).active(), "and brings the rider out");

            c = survivor(h);
            d = survivor(h);
            tie(h, c, d);
            h.assertTrue(Runs.enter(c, g), "a rider goes back in");
            Bonds.loggedOut(c);
            h.assertTrue(Runs.run(c).active(), "a rider who logs off keeps their place till they're back");
            RunEvents.onLogin(c);
            h.assertFalse(Runs.run(c).active(), "and comes back outside: the thread didn't survive it");
        } finally {
            teardown(h, g, a, b, c, d);
        }
    }

    private static void goalsStep(GameTestHelper h) {
        ServerPlayer a = at(h, 0);
        ServerPlayer b = null;
        try {
            h.assertTrue(RidingGoals.steps(a).isEmpty(), "nobody near, nothing to offer");
            b = at(h, 3);
            h.assertTrue(RidingGoals.steps(a).stream().anyMatch(s -> s.key().equals(RidingGoals.KEY) && s.optional()), "someone near: an optional step on the Tasks page");
            tie(h, a, b);
            h.assertTrue(RidingGoals.steps(a).isEmpty(), "tied, it's gone");
            Bonds.clear();
            h.assertTrue(RidingGoals.steps(a).isEmpty(), "and once you've done it, it stays gone");
        } finally {
            remove(h, a, b);
        }
    }
}
