package dev.awaken.together;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.FightsHooks;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.core.api.RidingHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.core.test.Pair;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.run.Rooms;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.powers.Powers;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.Set;
import java.util.function.Consumer;

/**
 * GameTests for Together (SPEC §70): {@code pair_} for the party and any two in one fight, {@code gentle_} for a
 * player's own difficulty, one per mechanism. CI runs them.
 */
final class TogetherTests {
    private TogetherTests() {}

    private static void test(String name, Consumer<GameTestHelper> body) {
        AwakenTests.registerIsolated(name, 120, h -> {
            ServerLevel level = h.getLevel();
            Difficulty was = level.getDifficulty();
            level.getServer().setDifficulty(Difficulty.NORMAL, true); // monsters don't attack players on Peaceful
            GateService.useTestLevel(level);
            try {
                body.accept(h);
            } finally {
                Fights.forget();
                PartyService.forget();
                PartyData.get(level.getServer()).clear();
                level.getServer().setDifficulty(was, true);
                GateLiveTests.teardown(level, null); // puts the test level back, or every later test's player counts as inside a gate
            }
            h.succeed();
        });
    }

    static void register() {
        test("pair_party_by_card", TogetherTests::partyByCard);
        test("pair_party_holds_four", TogetherTests::partyHoldsFour);
        test("pair_party_not_mid_fight", TogetherTests::partyNotMidFight);
        test("pair_strip_on_change_only", TogetherTests::stripOnChangeOnly);
        test("pair_back_turned", TogetherTests::backTurned);
        test("pair_down_and_lift", TogetherTests::downAndLift);
        test("pair_both_down", TogetherTests::bothDown);
        test("pair_boss_turns_at_openings", TogetherTests::bossTurns);
        test("pair_answering_peal", TogetherTests::answeringPeal);
        test("gentle_setting_kept_and_waits", TogetherTests::settingWaits);
        test("gentle_half_damage", TogetherTests::halfDamage);
        test("gentle_second_winds", TogetherTests::secondWinds);
        test("gentle_two_at_a_time", TogetherTests::twoAtATime);
        test("gentle_half_a_fighter", TogetherTests::halfAFighter);
        test("gentle_one_phrase_in_four", TogetherTests::onePhraseInFour);
        test("gentle_story_death_cost", TogetherTests::storyDeathCost);
        test("alone_plays_the_same", TogetherTests::alonePlaysTheSame);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    private static GatePlan plan(long seed) {
        return GateLiveTests.plan(seed, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
    }

    private static void kill(GameTestHelper h, ServerPlayer p) {
        p.setInvulnerableTime(0);
        p.damageCooldownTime = 0; // 26.3's i-frames
        p.setHealth(1F);
        p.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 100_000F);
    }

    private static float blow(GameTestHelper h, Zombie z, ServerPlayer by) {
        z.setInvulnerableTime(0);
        z.damageCooldownTime = 0;
        float before = z.getHealth();
        z.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(by), 10F);
        return before - z.getHealth();
    }

    private static Zombie tough(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(x, 2, z));
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        zombie.setHealth(500);
        return zombie;
    }

    private static void teardown(GameTestHelper h, GateRecord g, Pair.Two two) {
        if (two != null) {
            for (ServerPlayer p : new ServerPlayer[]{two.second(), two.first()}) if (Runs.run(p).active()) Gates.forceExtract(p, false);
        }
        GateLiveTests.teardown(h.getLevel(), null, g);
        if (two != null) two.leave();
    }

    /** Both in one gate, on the first floor's biggest fighting room. */
    private static GateRecord inAGate(GameTestHelper h, Pair.Two two, long seed) {
        GateRecord g = GateLiveTests.standUp(h, plan(seed), 0, 1);
        h.assertTrue(Runs.enter(two.first(), g) && Runs.enter(two.second(), g), "they go in");
        FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
        Room r = GateLiveTests.biggestCombat(f);
        GateLiveTests.put(h.getLevel(), two.first(), g, f, r);
        GateLiveTests.put(h.getLevel(), two.second(), g, f, r);
        return g;
    }

    // ---- the party ------------------------------------------------------------------------------------------------------

    private static void partyByCard(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertFalse(PartyHooks.sameParty(a, b), "two strangers aren't a party");
            String card = "together:join:" + a.getUUID() + ":" + b.getUUID();
            Signal.answer(b, card, true);
            h.assertFalse(PartyHooks.sameParty(a, b), "an answer to a card nobody sent counts for nothing");
            PartyService.ask(a, b);
            h.assertTrue(Signal.isPending(b, card), "the other is asked");
            h.assertFalse(PartyHooks.sameParty(a, b), "asking isn't enough");
            Signal.answer(a, card, true);
            h.assertFalse(PartyHooks.sameParty(a, b), "and the asker can't answer for them");
            Signal.answer(b, card, true);
            h.assertTrue(PartyHooks.sameParty(a, b) && PartyHooks.members(a).contains(b.getUUID()), "a yes makes the party");
            PartyService.leave(b);
            h.assertFalse(PartyHooks.sameParty(a, b), "either of them can leave, and a party of one is no party");
            h.assertTrue(PartyData.get(h.getLevel().getServer()).of(a.getUUID()) == null, "nothing is left of it");
        } finally {
            two.leave();
        }
    }

    private static void partyHoldsFour(GameTestHelper h) {
        AwakenTests.Live[] five = new AwakenTests.Live[5];
        try {
            for (int i = 0; i < 5; i++) five[i] = Pair.one(h, "four", i * 2);
            for (int i = 1; i < 4; i++) h.assertTrue(PartyService.join(five[0].player(), five[i].player()), "the party takes a fourth");
            h.assertTrue(PartyHooks.members(five[0].player()).size() == 4, "four of them");
            h.assertFalse(PartyService.join(five[0].player(), five[4].player()), "and no fifth");
            h.assertTrue(PartyService.whyNot(five[4].player(), five[0].player()) != null, "the fifth is told why");
        } finally {
            for (AwakenTests.Live l : five) if (l != null) l.leave();
        }
    }

    private static void partyNotMidFight(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        Zombie z = null;
        try {
            z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2));
            z.setTarget(b);
            h.assertTrue(Powers.inCombat(b), "a monster is on them");
            PartyService.ask(a, b);
            h.assertFalse(Signal.isPending(b, "together:join:" + a.getUUID() + ":" + b.getUUID()), "nothing is asked in a fight");
            h.assertFalse(Fights.set(b, true), "and a setting doesn't change in one");
            h.assertFalse(Fights.gentle(b), "they are still on Full");
        } finally {
            if (z != null) z.discard();
            two.leave();
        }
    }

    private static void stripOnChangeOnly(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            var server = h.getLevel().getServer();
            TogetherNet.forget();
            TogetherNet.pass(server);
            h.assertTrue(TogetherNet.sendsForTest(a.getUUID()) == 1, "the party is sent once");
            for (int i = 0; i < 4; i++) TogetherNet.pass(server);
            h.assertTrue(TogetherNet.sendsForTest(a.getUUID()) == 1, "and not again while nothing changes");
            b.setHealth(b.getMaxHealth() * 0.5F);
            TogetherNet.pass(server);
            h.assertTrue(TogetherNet.sendsForTest(a.getUUID()) == 2, "a partner's health changing is one more");
        } finally {
            two.leave();
        }
    }

    // ---- any two in one fight ---------------------------------------------------------------------------------------------

    private static void backTurned(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        Zombie z = null;
        try {
            z = tough(h, 1, 2);
            z.setTarget(b);
            float plain = blow(h, z, a);
            h.assertTrue(plain > 0, "a blow lands");
            h.assertFalse(Fights.backTurned(z, a), "strangers, and no blow being thrown: nothing more");
            h.assertTrue(PartyService.join(a, b), "a party");
            h.assertFalse(Fights.backTurned(z, a), "a party, but the creature isn't in the middle of a blow");
            h.assertTrue(Fights.stance(z, 20, a) == 20, "so stance counts as it did");
        } finally {
            if (z != null) z.discard();
            two.leave();
        }
    }

    private static void downAndLift(GameTestHelper h) {
        GateRecord g = null;
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            g = inAGate(h, two, 7001);
            kill(h, a);
            h.assertTrue(a.isAlive() && Fights.down(a.getUUID()), "with a partner standing on the floor, a killing blow puts them down");
            h.assertTrue(Runs.run(a).active(), "and the run goes on");
            h.assertTrue(FightsHooks.kneeling(a) && RidingHooks.folded(a), "down, they can't act");
            h.assertFalse(a.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 5F), "and nothing lands on them");
            long t = h.getLevel().getGameTime();
            Fights.Down d = downOf(a);
            d.lift = TogetherConfig.liftTicks() - 1;
            d.lastLift = t - 4;
            var lift = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(b, net.minecraft.world.InteractionHand.MAIN_HAND, a, a.position());
            Fights.onInteract(lift);
            h.assertFalse(Fights.down(a.getUUID()), "a partner's hand held on them lifts them");
            h.assertTrue(Math.abs(a.getHealth() - a.getMaxHealth() * (float) TogetherConfig.liftHealth()) < 0.01F, "at a third of their health");
            Fights.refillGraceForTest(a.getUUID());
            a.setInvulnerableTime(0);
            a.damageCooldownTime = 0;
            a.setHealth(1F);
            var generic = h.getLevel().damageSources().generic();
            String seen = "shielded " + Fights.shieldedForTest(a.getUUID()) + ", invulnerable to it " + a.isInvulnerableTo(h.getLevel(), generic)
                    + ", client loaded " + a.connection.hasClientLoaded() + ", changing dimension " + a.isChangingDimension();
            boolean landed = a.hurtServer(h.getLevel(), generic, 100_000F);
            seen += ", landed " + landed + ", health " + a.getHealth() + ", dying " + a.isDeadOrDying() + ", down " + Fights.down(a.getUUID());
            h.assertFalse(Fights.down(a.getUUID()), "once a floor: the second killing blow is the death (" + seen + ")");
            h.assertFalse(Runs.run(a).active(), "and it ends their run as it always did (" + seen + ")");
        } finally {
            teardown(h, g, two);
        }
    }

    private static Fights.Down downOf(ServerPlayer p) {
        return Fights.downForTest(p.getUUID());
    }

    private static void bothDown(GameTestHelper h) {
        GateRecord g = null;
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            g = inAGate(h, two, 7002);
            kill(h, a);
            h.assertTrue(Fights.down(a.getUUID()), "one is down");
            kill(h, b);
            h.assertFalse(Fights.down(b.getUUID()), "with nobody left standing, the second doesn't go down: they die");
            Fights.tick(h.getLevel().getServer());
            h.assertFalse(Fights.down(a.getUUID()), "and the first, with nobody to come, dies too");
            h.assertFalse(Runs.run(a).active() || Runs.run(b).active(), "both runs are over");
        } finally {
            teardown(h, g, two);
        }
    }

    /** A zombie that counts as a gate's Lord: whom it fights is the boss rule's to say. */
    private static Zombie lord(GameTestHelper h) {
        Zombie z = tough(h, 1, 3);
        z.setData(dev.awaken.core.api.CoreApi.MOB_ROLE, new dev.awaken.core.api.MobRole(dev.awaken.core.api.MobRole.Role.BOSS, 10, "", java.util.Optional.empty(), ""));
        return z;
    }

    private static void bossTurns(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        Zombie z = null;
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            z = lord(h);
            h.assertTrue(Fights.nextTarget(z, a) == a, "nothing done to it: it stays on the one it has");
            blow(h, z, b);
            h.assertTrue(Fights.nextTarget(z, a) == b, "at an opening it turns to whoever did most to it since it last chose");
            h.assertTrue(Fights.nextTarget(z, b) == b, "and the count starts again: it stays");
            blow(h, z, b);
            blow(h, z, a);
            blow(h, z, a);
            h.assertTrue(Fights.nextTarget(z, b) == a, "more from the other turns it back");
            PartyData.get(h.getLevel().getServer()).leave(b.getUUID(), true); // by the book: they've been fighting, and leaving waits for that
            blow(h, z, b);
            blow(h, z, b);
            h.assertTrue(Fights.nextTarget(z, a) == a, "someone who isn't of the party is the game's own business, as before");
        } finally {
            if (z != null) z.discard();
            two.leave();
        }
    }

    private static void answeringPeal(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            h.assertFalse(Answering.rung(h.getLevel(), a), "a ring with nobody far off is the handbell's own");
            h.assertFalse(Answering.rung(h.getLevel(), b), "and nobody was called, so nothing is answered");
            h.assertTrue(Answering.whyNot(b, a) == null || h.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD,
                    "out in the world and clear of a fight, nothing stops it");
        } finally {
            two.leave();
        }
    }

    // ---- my fights: Full or Gentle ---------------------------------------------------------------------------------------

    private static void settingWaits(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first();
        try {
            h.assertFalse(Fights.gentle(a) || FightsHooks.gentle(a), "everyone starts on Full");
            h.assertTrue(Fights.set(a, true) && FightsHooks.gentle(a), "out of a fight it changes at once");
            h.assertTrue(a.getData(TogetherContent.FIGHTS).gentle(), "and it is kept on the player");
            h.assertTrue(Fights.windsLeft(a) == TogetherConfig.secondWinds(), "with every second wind");
            h.assertTrue(Fights.set(a, false) && !FightsHooks.gentle(a), "and back");
        } finally {
            two.leave();
        }
    }

    private static void halfDamage(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(Fights.set(b, true), "one of them on Gentle");
            for (ServerPlayer p : new ServerPlayer[]{a, b}) {
                p.setInvulnerableTime(0);
                p.damageCooldownTime = 0;
                p.setHealth(p.getMaxHealth());
            }
            float fa = a.getHealth(), fb = b.getHealth();
            a.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 8F);
            b.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 8F);
            float full = fa - a.getHealth(), gentle = fb - b.getHealth();
            h.assertTrue(full > 0, "the blow lands on Full");
            h.assertTrue(Math.abs(gentle / full - (float) TogetherConfig.gentleDamage()) < 0.01F, "and does half on Gentle, got " + gentle / full);
        } finally {
            two.leave();
        }
    }

    private static void secondWinds(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first();
        try {
            h.assertTrue(Fights.set(a, true), "on Gentle");
            int winds = TogetherConfig.secondWinds();
            for (int i = 0; i < winds; i++) {
                kill(h, a);
                h.assertTrue(a.isAlive() && !a.isDeadOrDying(), "a killing blow is a second wind");
                h.assertTrue(Math.abs(a.getHealth() - a.getMaxHealth() * Fights.WIND_HEALTH) < 0.01F, "up at half health");
                h.assertTrue(FightsHooks.kneeling(a), "on a knee");
                h.assertFalse(a.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 5F), "where nothing can touch them");
                h.assertTrue(Fights.windsLeft(a) == winds - 1 - i, "one fewer left");
                Fights.elapseForTest(h.getLevel().getServer(), a.getUUID());
                h.assertFalse(FightsHooks.kneeling(a), "two seconds on, they're up");
                Fights.refillGraceForTest(a.getUUID());
            }
            kill(h, a);
            h.assertTrue(a.isDeadOrDying(), "with none left, the next one is real");
        } finally {
            two.leave();
        }
    }

    private static void twoAtATime(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        Zombie[] z = new Zombie[4];
        try {
            h.assertTrue(Fights.set(b, true), "one of them on Gentle");
            for (int i = 0; i < z.length; i++) {
                z[i] = h.spawn(EntityTypes.ZOMBIE, new BlockPos(i, 2, 3));
                z[i].setTarget(b);
            }
            int onGentle = 0, onFull = 0;
            for (Zombie zombie : z) {
                if (zombie.getTarget() == b) onGentle++;
                if (zombie.getTarget() == a) onFull++;
            }
            h.assertTrue(onGentle == TogetherConfig.gentlePack(), "only so many small monsters are on a Gentle player at a time, got " + onGentle);
            h.assertTrue(onFull == z.length - onGentle, "the rest go for someone on Full");
        } finally {
            for (Zombie zombie : z) if (zombie != null) zombie.discard();
            two.leave();
        }
    }

    private static void halfAFighter(GameTestHelper h) {
        GateRecord g = null;
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            g = inAGate(h, two, 7003);
            FloorLayout f = GateLayout.of(g.plan).floor(0, 1);
            h.assertTrue(Rooms.fightersOnFloor(h.getLevel(), g, f) == 2.0, "two on Full are two fighters");
            h.assertTrue(Fights.set(b, true), "one of them on Gentle");
            double fighters = Rooms.fightersOnFloor(h.getLevel(), g, f);
            h.assertTrue(Math.abs(fighters - (1.0 + TogetherConfig.gentleWeight())) < 1.0E-6, "a player on Gentle is half a fighter, got " + fighters);
            int[] company = Rooms.partyCompany(12, 2, fighters);
            h.assertTrue(company[0] == 14 && company[1] == 2, "a sixth more monsters and no extra elite, got " + company[0] + " and " + company[1]);
            Zombie lord = tough(h, 1, 2);
            try {
                float before = lord.getMaxHealth();
                FightsHooks.sizeStance(lord, fighters);
                h.assertTrue(Math.abs(FightsHooks.partyStance(lord) - 1.25) < 1.0E-6, "a boss's stance grows a quarter for her");
                h.assertTrue(lord.getMaxHealth() == before, "sizing stance alone leaves health be");
            } finally {
                lord.discard();
            }
            Fights.set(a, true);
            h.assertTrue(FightsHooks.fighters(Set.of(a, b)) >= 1.0, "two on Gentle are never less than one fighter");
        } finally {
            teardown(h, g, two);
        }
    }

    private static void onePhraseInFour(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        Zombie z = null;
        try {
            h.assertTrue(PartyService.join(a, b), "a party");
            h.assertTrue(Fights.set(b, true), "one of them on Gentle");
            z = lord(h);
            int n = TogetherConfig.onePhraseIn();
            ServerPlayer on = a;
            int gentle = 0;
            for (int i = 0; i < n * 3; i++) {
                on = (ServerPlayer) Fights.nextTarget(z, on);
                if (on == b) gentle++;
            }
            h.assertTrue(gentle == 3, "with someone on Full in sight, a player on Gentle gets one phrase in " + n + ", got " + gentle + " of " + n * 3);
            h.assertTrue(Fights.set(a, true), "both on Gentle");
            h.assertTrue(Fights.nextTarget(z, b) == b, "with nobody on Full, it fights whom it was fighting");
        } finally {
            if (z != null) z.discard();
            two.leave();
        }
    }

    private static void storyDeathCost(GameTestHelper h) {
        Pair.Two two = Pair.of(h);
        ServerPlayer a = two.first(), b = two.second();
        try {
            h.assertTrue(FightsHooks.deathCost(a) == AwakenConfig.DIFFICULTY.get(), "on Full a death costs what the world says");
            h.assertTrue(Fights.set(b, true), "one of them on Gentle");
            h.assertTrue(FightsHooks.deathCost(b) == AwakenConfig.Difficulty.STORY, "on Gentle it costs what Story costs");
        } finally {
            two.leave();
        }
    }

    // ---- alone ------------------------------------------------------------------------------------------------------------

    private static void alonePlaysTheSame(GameTestHelper h) {
        AwakenTests.Live one = Pair.one(h, "alone", 0);
        ServerPlayer p = one.player();
        Zombie z = null;
        try {
            h.assertTrue(PartyHooks.members(p).equals(java.util.List.of(p.getUUID())), "alone, your party is you");
            h.assertTrue(FightsHooks.weight(p) == 1.0 && !FightsHooks.gentle(p) && !FightsHooks.kneeling(p), "on Full, one fighter, on your feet");
            h.assertTrue(FightsHooks.fighters(Set.of(p)) == 1.0, "a room is sized for one");
            h.assertFalse(FightsHooks.goesDown(p), "nobody to lift you: a death is a death");
            z = tough(h, 1, 2);
            z.setTarget(p);
            h.assertTrue(z.getTarget() == p, "a monster's choice is its own");
            h.assertFalse(Fights.backTurned(z, p), "its back is never turned to someone alone");
            h.assertTrue(FightsHooks.stance(z, 20, p) == 20, "stance counts as it did");
            p.setInvulnerableTime(0);
            p.damageCooldownTime = 0;
            float before = p.getHealth();
            p.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 6F);
            h.assertTrue(Math.abs(before - p.getHealth() - 6F) < 0.01F, "a blow does what it did");
            var server = h.getLevel().getServer();
            TogetherNet.forget();
            for (int i = 0; i < 6; i++) TogetherNet.pass(server);
            h.assertTrue(TogetherNet.sendsForTest(p.getUUID()) == 1, "and the wire carries their own row once, then nothing while it doesn't change");
        } finally {
            if (z != null) z.discard();
            one.leave();
        }
    }
}
