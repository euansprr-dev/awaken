package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The assisted swing (SPEC §17.31) and the rope that finds the way (SPEC §62): the swing bench's gates (ten places, 40
 * seeded runs each, the old rope beside the new, played by a bot that only holds the key), the swing's world read from
 * real blocks agreeing with the bench's boxes with the anchors it picks accepted by the server, and a sling off a real oak.
 */
final class AscentSwingTests {
    private AscentSwingTests() {}

    static void register() {
        dev.awaken.core.test.AwakenTests.register("ascent_swing_bench", 20, today(AscentSwingTests::bench));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_swing_level", 40, today(AscentSwingTests::level));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_sling_level", 40, today(AscentSwingTests::sling));
        dev.awaken.core.test.AwakenTests.register("ascent_swing_over_nothing", 20, today(AscentSwingTests::overNothing));
        dev.awaken.core.test.AwakenTests.register("ascent_swing_held", 20, today(AscentSwingTests::held));
        dev.awaken.core.test.AwakenTests.register("ascent_rope_chain", 20, today(AscentSwingTests::ropeChain));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_rope_aimed", 40, today(AscentSwingTests::aimed));
        dev.awaken.core.test.AwakenTests.register("ascent_sky_stair", 20, today(AscentSwingTests::skyStair));
        RealRopeTests.register();
    }

    /**
     * A test of the assisted swing as it was before the real swing (SPEC §62.9): run with {@link AscentSwing#real} off,
     * which is that swing exactly (the switch's other side). The real swing has its own tests ({@link RealRopeTests}).
     */
    static java.util.function.Consumer<GameTestHelper> today(java.util.function.Consumer<GameTestHelper> test) {
        return h -> {
            boolean was = AscentSwing.real;
            AscentSwing.real = false;
            try {
                test.accept(h);
            } finally {
                AscentSwing.real = was;
            }
        };
    }

    /**
     * The Rope dream's gaps over nothing (SPEC §62): roads in a void, laid for a let-go at the top of the arc. The held
     * rope never lets go by itself: let go at the top, thrown early in the jump or late, it lands on the far road. With
     * the held rope off, the guide's early let-go lands there too, and with ground under the gap it throws on as before.
     */
    private static void overNothing(GameTestHelper h) {
        AscentSwing.Tune was = AscentSwing.T;
        boolean guide = AscentSwing.guide, hold = AscentSwing.hold;
        AscentSwing.T = AscentSwing.Tune.JAR;
        try {
            int[][] gaps = {{22, 14, 6, 11}, {24, 14, 8, 11}};
            for (int[] g : gaps) for (int at : new int[] {2, 9}) {
                AscentSwing.guide = true;
                AscentSwing.hold = true;
                double top = SwingBench.gapLanding(g[0], g[1], g[2], g[3], at, false, true);
                h.assertTrue(top >= 0.3 && top <= g[3] - 0.3, "a held rope over nothing let go at the top lands on the far road: " + g[0]
                        + " wide, thrown at " + at + ", " + String.format("%.1f", top) + " past its edge, the road " + g[3] + " long");
                AscentSwing.hold = false;
                double held = SwingBench.gapLanding(g[0], g[1], g[2], g[3], at, false);
                h.assertTrue(held >= 0.3 && held <= g[3] - 0.5, "held rope off: the guide's let-go over nothing lands on the far road: "
                        + g[0] + " wide, thrown at " + at + ", " + String.format("%.1f", held) + " past its edge");
            }
            AscentSwing.guide = true;
            AscentSwing.hold = false;
            double over = SwingBench.gapLanding(22, 14, 6, 11, 2, false), ground = SwingBench.gapLanding(22, 14, 6, 11, 2, true);
            h.assertTrue(ground > over + 3, "held rope off: with ground under the gap the early let-go throws on as before: "
                    + String.format("%.1f", ground) + " past the edge, " + String.format("%.1f", over) + " over nothing");
        } finally {
            AscentSwing.T = was;
            AscentSwing.guide = guide;
            AscentSwing.hold = hold;
        }
        h.succeed();
    }

    /**
     * The held rope (SPEC §62): a 26-block rope three blocks off to one side, the body swinging in from 40° behind, held
     * 30 seconds with no key. It never asks to let go; after its first arc it swings back past the bottom, again and again,
     * each forward top lower than the last (drag, no drive), none over the cap; and it settles under its anchor. Then
     * forward held pumps it back up, never over {@code holdTop}; back held slows it to a hang. With the held rope off the
     * same rope lets go at the top of its first arc.
     */
    private static void held(GameTestHelper h) {
        AscentSwing.Tune was = AscentSwing.T;
        boolean guide = AscentSwing.guide, hold = AscentSwing.hold;
        AscentSwing.T = AscentSwing.Tune.JAR;
        try {
            AscentSwing.guide = true;
            AscentSwing.hold = true;
            AscentSwing.Tune t = AscentSwing.T;
            SwingSpace.Boxes space = new SwingSpace.Boxes();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Rope rope = new AscentSwing.Rope();
            AscentRules.Kin k = new AscentRules.Kin();
            heldStart(k, rope);
            double[] pv = new double[4];
            double lastTop = 999, maxAng = 0;
            int turns = 0;
            boolean forward = true;
            StringBuilder tops = new StringBuilder();
            for (int i = 0; i < 600; i++) {
                int how = AscentSwing.tick(space, k, rope, 0, 0, w);
                h.assertTrue(how == AscentSwing.GO, "a held rope asked to let go at tick " + i + ": " + how);
                AscentSwing.pivot(k.x, k.z, rope.ax, rope.ay, rope.az, 0, pv);
                double ang = AscentSwing.angleFromBottom(k, pv[0], pv[1], pv[2]);
                maxAng = Math.max(maxAng, ang);
                if (forward && k.vz < 0 || !forward && k.vz > 0) {
                    if (forward) {
                        h.assertTrue(ang < lastTop + 0.5, "a held rope's forward tops fall away: " + tops + " then " + String.format("%.1f", ang));
                        lastTop = ang;
                        tops.append(String.format(" %.1f", ang));
                    }
                    forward = !forward;
                    turns++;
                }
            }
            h.assertTrue(turns >= 8, "a held rope swings back and forth: " + turns + " turns in 30 s, forward tops" + tops);
            h.assertTrue(maxAng < t.holdCap + 3, "no held arc over the cap: " + String.format("%.1f", maxAng));
            AscentSwing.pivot(k.x, k.z, rope.ax, rope.ay, rope.az, 0, pv);
            h.assertTrue(Math.abs(pv[3]) < 0.5, "a held rope settles under its anchor: still " + String.format("%.2f", pv[3]) + " off");
            double pumped = swingFor(space, k, rope, w, 1, 600, 200, pv);
            h.assertTrue(pumped > 60 && pumped < t.holdTop + 4, "forward pumps a held rope up to its cap: " + String.format("%.1f", pumped));
            double slowed = swingFor(space, k, rope, w, -1, 600, 100, pv);
            h.assertTrue(slowed < 8, "back slows a held rope to a hang: " + String.format("%.1f", slowed));
            AscentSwing.hold = false;
            heldStart(k, rope);
            int let = -1;
            for (int i = 0; i < 200 && let < 0; i++) if (AscentSwing.tick(space, k, rope, 0, 0, w) == AscentSwing.TOP) let = i;
            h.assertTrue(let > 0, "held rope off: the rope lets go at the top of its first arc");
        } finally {
            AscentSwing.T = was;
            AscentSwing.guide = guide;
            AscentSwing.hold = hold;
        }
        h.succeed();
    }

    /**
     * The held chain, a link at a time (SPEC §62): held, the rope throws the next link once the last one ends, and never
     * again at what it couldn't take. In the client's loop (a link lasts 3 ticks, the chain waits 2, then throws at the
     * best pick unless stopped or spent), for 100 ticks after one press: where every link fails 0.4 on, one
     * throw; where every link arrives 4 on, a link at a time; where links fail 4 on at new anchors, the chain goes on;
     * where they fail 4 on at the same anchor, it is never thrown at again. Then each rule on its own.
     */
    private static void ropeChain(GameTestHelper h) {
        h.assertTrue(heldFor(0.4, false, false) == 1, "held where every link fails: one throw, not " + heldFor(0.4, false, false));
        int good = heldFor(4, true, false);
        h.assertTrue(good >= 15, "held where every link arrives: a link at a time, " + good + " in 100 ticks");
        int on = heldFor(4, false, false);
        h.assertTrue(on >= 15, "held where links fail after carrying you on, each at a new anchor: it goes on, " + on);
        h.assertTrue(heldFor(4, false, true) == 1, "never thrown again at an anchor that failed: " + heldFor(4, false, true) + " throws");
        RopeChain c = new RopeChain();
        c.press();
        c.thrown(0, 0, 64, 0);
        c.missed(0);
        h.assertTrue(c.stopped() && c.throwsThisPress() == 1, "a press that missed throws nothing more while it is held");
        c.press();
        h.assertTrue(!c.stopped() && c.throwsThisPress() == 0, "a new press may throw at once");
        c.thrown(0, 0, 64, 0);
        h.assertTrue(!c.ended(3, 0.2, 64, 0, false, false, "the zip was blocked", 7) && !c.stopped() && !c.spent(7),
                "a link let go by the key is never judged");
        c.thrown(10, 0, 64, 0);
        h.assertTrue(c.ended(18, 1.5, 64, 0, true, false, null, 7) && c.stopped(), "a held swing that landed 1.5 from its start went nowhere");
        c.press();
        h.assertTrue(!c.stopped(), "a press starts the chain again");
        c.thrown(0, 0, 64, 0);
        h.assertTrue(!c.ended(25, 5, 64, 0, true, false, null, 7), "a held link that carried the body 5 blocks is progress");
        c.thrown(30, 5, 64, 0);
        h.assertTrue(!c.ended(34, 5.5, 66, 0, true, true, null, 8), "a short zip that arrived is progress");
        c.thrown(40, 5, 66, 0);
        h.assertTrue(!c.ended(48, 9, 66, 0, true, false, "the sling's way closed", 9) && c.spent(9) && !c.stopped(),
                "a sling whose way closed 4 on: the chain goes on, never to that top again");
        c.thrown(50, 9, 66, 0);
        h.assertTrue(c.ended(53, 9.2, 66, 0, true, true, null, 10), "an arrival 0.2 off in 3 ticks went nowhere");
        h.assertTrue(!c.ended(60, 0, 0, 0, true, false, "the zip was blocked", 11), "no link open, nothing judged");
        c.press();
        h.assertTrue(!c.spent(9) && !c.stopped(), "a press forgets the spent anchor");
        h.succeed();
    }

    /**
     * The client's held loop for 100 ticks after one press: each link lasts 3 ticks and moves the body {@code step} on,
     * arriving or failing; its anchor is a new one each time, or the same one ({@code same}). The chain waits 2 ticks
     * after a link and throws at the pick unless stopped or the pick is spent. Returns the ropes thrown.
     */
    private static int heldFor(double step, boolean arrive, boolean same) {
        RopeChain c = new RopeChain();
        c.press();
        double x = 0;
        int thrown = 0, cool = 0, age = -1;
        long anchor = 0;
        for (int t = 0; t < 100; t++) {
            if (age >= 0) {
                if (++age < 3) continue;
                x += step;
                c.ended(t, x, 64, 0, true, arrive, arrive ? null : "the sling's way closed", anchor);
                age = -1;
                cool = 2;
            } else if (cool > 0) {
                cool--;
            } else {
                long pick = same ? 7 : t;
                if (c.stopped() || c.spent(pick)) continue;
                anchor = pick;
                c.thrown(t, x, 64, 0);
                thrown++;
                age = 0;
            }
        }
        return thrown;
    }

    /** The held test's rope: 26 long, its anchor 3 to the side and 40 up, the hand 40° behind its bottom, going 0.8 on. */
    private static void heldStart(AscentRules.Kin k, AscentSwing.Rope rope) {
        rope.set(3, 40, 20, 26, 26);
        double rad = Math.sqrt(26 * 26 - 9), a = Math.toRadians(40);
        k.set(0, 40 - rad * Math.cos(a), 20 - rad * Math.sin(a), 0, 0, 0.8);
    }

    /** A held swing for {@code ticks} with forward at {@code fwd}: its widest angle over the last {@code last} ticks. */
    private static double swingFor(SwingSpace s, AscentRules.Kin k, AscentSwing.Rope rope, AscentSwing.Work w, double fwd, int ticks, int last, double[] pv) {
        double max = 0;
        for (int i = 0; i < ticks; i++) {
            AscentSwing.tick(s, k, rope, 0, fwd, w);
            AscentSwing.pivot(k.x, k.z, rope.ax, rope.ay, rope.az, 0, pv);
            if (i >= ticks - last) max = Math.max(max, AscentSwing.angleFromBottom(k, pv[0], pv[1], pv[2]));
        }
        return max;
    }

    /** One bench gate: noted, so every row is logged before any gate fails the test. */
    private static void gate(java.util.List<String> bad, boolean ok, String what) {
        if (!ok) bad.add(what);
    }

    /**
     * The rope as SPEC §62 has it, played by bots (a player holding the key): never a hard hit on a rope, never a stopped
     * run, at least as far as the old rope everywhere, the tall places far past what a held rope did before (lane 120,
     * city 166, forest 124), the real-sized woods, hills and roofs (where it never fired) well past a run's 103 with no
     * hard hit at all, and few hard hits flying free among the giants. The held rope never lets go by itself, so these
     * bots let go of the key where the rope used to (as the window lights, else at the top); with the held rope off, the
     * rope's own let-go keeps its gates. Then the switch: with the guide off, the assisted swing's own gates as §17.31 set
     * them (two places are enough to hold the path). Every row is logged before any gate is judged.
     */
    private static void bench(GameTestHelper h) {
        AscentSwing.Tune was = AscentSwing.T;
        boolean guide = AscentSwing.guide, holdWas = AscentSwing.hold;
        AscentSwing.T = AscentSwing.Tune.JAR;
        java.util.List<String> bad = new java.util.ArrayList<>();
        StringBuilder rows = new StringBuilder();
        try {
            AscentSwing.guide = true;
            AscentSwing.hold = true;
            // how the holding bot lets go of a held rope, three ways (logged, never judged): as the glow lights (the gates'
            // bot: it goes furthest), at its brightest, only at the top of the arc
            String[] rules = {"lit", "bright", "top"};
            for (int rule = 0; rule < rules.length; rule++) {
                SwingBench.heldRule = rule;
                for (String place : new String[] {"lane", "city", "forest", "oak", "roofs"}) {
                    rows.append('\n').append(rules[rule]).append(' ').append(SwingBench.bench(place, true, true));
                }
            }
            SwingBench.heldRule = SwingBench.LIT;
            // a sling's aim leaning toward the heading (SPEC §62, "One key, one read") was tried on 11 Nov: at 1, 1.5, 2
            // and 3 blocks each missed one of these gates by a single run, so the jar's lean is 0
            AscentSwing.T = AscentSwing.Tune.JAR;
            heldGates(bad, rows);
            // the held rope off: the rope lets go by itself, as on 10 Nov
            String[] tall = {"lane", "city"};
            double[] tallAtLeast = {240, 250};
            AscentSwing.hold = false;
            for (int i = 0; i < 2; i++) {
                SwingBench.Row held = SwingBench.bench(tall[i], true, true);
                rows.append("\nself   ").append(held);
                gate(bad, held.hardOnRope() == 0 && held.stuckRuns() == 0, tall[i] + ", held rope off: " + held);
                gate(bad, held.medianDistance() >= tallAtLeast[i], tall[i] + ", held rope off: goes " + held + ", wanted " + tallAtLeast[i]);
            }
            AscentSwing.hold = true;
            AscentSwing.guide = false;
            for (String place : new String[] {"lane", "city"}) {
                SwingBench.Row today = SwingBench.bench(place, false), assist = SwingBench.bench(place, true);
                rows.append("\nclassic ").append(assist);
                gate(bad, assist.hardOnRope() == 0 && assist.stuckRuns() == 0, place + ", guide off: " + assist);
                gate(bad, assist.medianDistance() >= today.medianDistance() * 0.98, place + ", guide off: shorter than the old rope " + assist);
            }
            SwingBench.Row oak = SwingBench.bench("oak", true, true);
            gate(bad, oak.ropesPerRun() == 0, "guide off: the old rule gives a real wood no rope, as it did: " + oak);
        } finally {
            AscentSwing.T = was;
            AscentSwing.guide = guide;
            AscentSwing.hold = holdWas;
            SwingBench.heldRule = SwingBench.LIT;
        }
        dev.awaken.Awaken.LOG.info("[ascent] swing bench:{}", rows);
        h.assertTrue(bad.isEmpty(), String.join("; ", bad));
        h.succeed();
    }

    /**
     * One tree alone (SPEC §62): the first rope of a run, off a sprint jump, at a run, and fast. A hundred oaks, 8 to 26
     * ahead, their tops 5 to 9 up, dead ahead and a block and a half aside. The first sling was swept and refused four in
     * five of them from a sprint jump (its let-go could not lift the body over the crown it was pulled toward); a rope
     * that works everywhere takes most.
     */
    /** The held rope's gates (the holding bot letting go as the glow lights, the woods, the key-only bot), at {@code AscentSwing.T}. */
    private static void heldGates(java.util.List<String> bad, StringBuilder rows) {
        int free = 0;
        for (String place : SwingBench.PLACES) {
            SwingBench.Row today = SwingBench.bench(place, false), held = SwingBench.bench(place, true, true);
            rows.append('\n').append(today).append("\nheld   ").append(held);
            gate(bad, held.hardOnRope() == 0, place + ": hard hits on the rope " + held);
            gate(bad, held.stuckRuns() == 0, place + ": stopped runs " + held);
            gate(bad, held.medianDistance() >= today.medianDistance() * 0.98, place + ": shorter than the old rope " + held + " vs " + today);
            free += held.hardFree();
        }
        gate(bad, free <= 2, "hard hits flying free between ropes: " + free);
        // the held rope (11 Nov) lets go by the key alone: the bot letting go as the glow lights goes lane 227, city
        // 253, forest 246, where the rope letting go by itself (its guide timing each let-go) went lane 243, city 263
        String[] tall = {"lane", "city", "forest"};
        double[] heldAtLeast = {220, 245, 240};
        for (int i = 0; i < tall.length; i++) {
            SwingBench.Row held = SwingBench.bench(tall[i], true, true);
            gate(bad, held.medianDistance() >= heldAtLeast[i], tall[i] + ": a held rope goes " + held + ", wanted " + heldAtLeast[i]);
        }
        int jump = singleTrees(0.36), run = singleTrees(0.6), fast = singleTrees(1.0);
        gate(bad, jump >= 80 && run >= 70 && fast >= 60, "one tree alone, of 100: slung from a sprint jump " + jump + ", at a run " + run + ", fast " + fast);
        double[] woodsAtLeast = {185, 180, 175, 180};
        for (int i = 0; i < SwingBench.WOODS.length; i++) {
            String place = SwingBench.WOODS[i];
            SwingBench.Row held = SwingBench.bench(place, true, true);
            rows.append("\nheld   ").append(held);
            gate(bad, held.hardOnRope() == 0 && held.hardFree() == 0, place + ": hard hits " + held);
            gate(bad, held.stuckRuns() == 0, place + ": stopped runs " + held);
            gate(bad, held.ropesPerRun() >= 3, place + ": the rope hardly fires " + held);
            gate(bad, held.medianDistance() >= woodsAtLeast[i], place + ": a held rope goes " + held + ", wanted " + woodsAtLeast[i]);
        }
        // the player who only holds forward and the rope, from the ground, never jump (one thumb: the rope on one key,
        // jump on another). The "holding" bot above also hops on every landing and throws only in the air, so the
        // game's own hop from the ground went unplayed, and in the game it never fired. This bot follows the game's
        // rule for a throw from the ground; hills may stop it (a face two blocks high with nothing over it).
        String[] keyPlaces = {"lane", "city", "forest", "oak", "spruce", "roofs", "hills"};
        double[] keyAtLeast = {240, 230, 230, 140, 180, 165, 135};
        for (int i = 0; i < keyPlaces.length; i++) {
            SwingBench.Row key = SwingBench.benchKey(keyPlaces[i]);
            rows.append("\nkey    ").append(key);
            gate(bad, key.hardOnRope() == 0, keyPlaces[i] + ", key only: hard hits on the rope " + key);
            gate(bad, key.ropesPerRun() >= 3, keyPlaces[i] + ", key only: the rope hardly fires from the ground " + key);
            gate(bad, key.medianDistance() >= keyAtLeast[i], keyPlaces[i] + ", key only: goes " + key + ", wanted " + keyAtLeast[i]);
        }
    }

    static int singleTrees(double speed) {
        int slung = 0;
        AscentSwing.Work w = new AscentSwing.Work();
        AscentSwing.Pick pick = new AscentSwing.Pick();
        for (double ahead = 8; ahead <= 26; ahead += 2) {
            for (int top = 5; top <= 9; top++) {
                for (double aside = 0; aside <= 1.5; aside += 1.5) {
                    double z = 4.5 + ahead + 2;
                    SwingSpace.Boxes b = new SwingSpace.Boxes().add(8, 0, z, 9, top - 2, z + 1, false).add(6, top - 4, z - 2, 11, top, z + 3, true);
                    AscentRules.Kin hand = new AscentRules.Kin().set(8.5 + aside, 1 + AscentRules.HAND_HEIGHT, 4.5, 0, 0.3, speed);
                    if (!AscentSwing.find(b, hand, 0, AscentRules.TETHER_RANGE, w, pick) && AscentSwing.findSling(b, hand, 0, AscentRules.TETHER_RANGE, w, pick)) slung++;
                }
            }
        }
        return slung;
    }

    /**
     * The sling from real blocks (SPEC §62): a floor, and 14 ahead a crown of leaves 5 wide whose top is 7 up (an oak).
     * Nothing swings there; the sling finds the crown, the server takes its anchor as real, and the pull played from the
     * blocks lets go before the crown and comes down past it without meeting it. With the guide off there is no sling.
     */
    private static void sling(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 80);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        boolean guide = AscentSwing.guide;
        AscentSwing.Tune was = AscentSwing.T;
        AscentSwing.T = AscentSwing.Tune.JAR;
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 20, 50))) level.setBlock(q, air, flags);
        try {
            AscentSwing.guide = true;
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 0, 0), o.offset(16, 0, 48))) level.setBlock(q, stone, flags);
            for (int y = 1; y <= 5; y++) level.setBlock(o.offset(8, y, 20), Blocks.OAK_LOG.defaultBlockState(), flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 4, 18), o.offset(10, 7, 22))) {
                if (level.getBlockState(q).isAir()) level.setBlock(q, leaves, flags);
            }
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            // off the ground in a sprint jump, heading at the tree
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 8.5, by + 1 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.3, 0.36);
            h.assertTrue(!AscentSwing.find(space, hand, 0, AscentRules.TETHER_RANGE, w, pick), "an oak gives no swing");
            h.assertTrue(AscentSwing.findSling(space, hand, 0, AscentRules.TETHER_RANGE, w, pick), "an oak gives a sling");
            h.assertTrue(pick.sling && Math.abs(pick.top - (by + 7)) < 0.01 && pick.z - bz > 17.5 && pick.z - bz < 23.5,
                    String.format("the sling bit the crown: top %.2f at z %.2f", pick.top - by, pick.z - bz));
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.snapTo(bx + 8.5, by + 1, bz + 4.5, 0, 0);
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the crown as a real anchor");
            // the pull: a tick of flight, then pulled; it lets go short of the crown, moving faster than it began
            AscentRules.Kin k = new AscentRules.Kin().set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
            int age = 0;
            boolean let = false;
            for (int i = 0; i < 60 && !let; i++) {
                let = AscentSwing.slingStep(k, pick.px, pick.py, pick.pz, ++age);
                h.assertTrue(!space.bodyHits(k.x, k.y - AscentRules.HAND_HEIGHT, k.z), "the pull never meets a block (tick " + i + ")");
            }
            h.assertTrue(let && pick.pz - k.z > 1 && k.hspeed() > 0.9, String.format("it lets go before the crown at speed: %.2f short, %.2f b/t", pick.pz - k.z, k.hspeed()));
            double top = 0;
            for (int i = 0; i < 40 && k.z < bz + 23.5; i++) {
                k.vy = (k.vy - AscentRules.G) * AscentRules.CARRY_DRAG_V;
                k.vx *= AscentRules.CARRY_DRAG_H;
                k.vz *= AscentRules.CARRY_DRAG_H;
                k.x += k.vx;
                k.y += k.vy;
                k.z += k.vz;
                top = Math.max(top, k.y - AscentRules.HAND_HEIGHT - by);
                h.assertTrue(!space.bodyHits(k.x, k.y - AscentRules.HAND_HEIGHT, k.z), "the flight over the crown never meets it (tick " + i + ")");
            }
            h.assertTrue(k.z >= bz + 23.5 && top > 7, String.format("it flew on over the crown (feet as high as %.2f)", top));
            AscentSwing.guide = false;
            h.assertTrue(SwingBench.bench("oak", true, true).ropesPerRun() == 0, "guide off: no sling");
        } finally {
            AscentSwing.guide = guide;
            AscentSwing.T = was;
            for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 20, 50))) level.setBlock(q, air, flags);
        }
        h.succeed();
    }

    /**
     * The rope goes where you look (SPEC §62), from real blocks: a floor, an oak 14 ahead (its crown 5 wide, its top 7
     * up), and past it a stone wall 14 high. Looking at the crown from a sprint jump, the rope takes that crown, as a
     * sling past its top (an oak gives no swing), though the flight after the crown meets the wall (played on here):
     * past the top, what the flight meets is yours. The server takes the crown as real. Looking up at the sky
     * the look meets nothing; looking down at the floor it meets the floor 3 or so off (where a missed rope stops), and
     * the floor gives no sling.
     */
    private static void aimed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 80);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        boolean guide = AscentSwing.guide, hold = AscentSwing.hold;
        AscentSwing.Tune was = AscentSwing.T;
        AscentSwing.T = AscentSwing.Tune.JAR;
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 24, 50))) level.setBlock(q, air, flags);
        try {
            AscentSwing.guide = true;
            AscentSwing.hold = true;
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 0, 0), o.offset(16, 0, 48))) level.setBlock(q, stone, flags);
            for (int y = 1; y <= 5; y++) level.setBlock(o.offset(8, y, 20), Blocks.OAK_LOG.defaultBlockState(), flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 4, 18), o.offset(10, 7, 22))) {
                if (level.getBlockState(q).isAir()) level.setBlock(q, leaves, flags);
            }
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 1, 27), o.offset(16, 14, 28))) level.setBlock(q, stone, flags);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            // off the ground in a sprint jump, heading at the tree, the eye on the middle of the crown's face
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 8.5, by + 1 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.3, 0.36);
            double ex = bx + 8.5, ey = by + 1 + 1.62, ez = bz + 4.5;
            float pitch = (float) -Math.toDegrees(Math.atan2(by + 4.5 - ey, 18 - 4.5));
            h.assertTrue(AscentSwing.aimed(space, hand, ex, ey, ez, 0, pitch, AscentRules.TETHER_RANGE, true, w, pick),
                    String.format("the rope takes the crown you look at (the look met it %.1f off)", w.aimT));
            h.assertTrue(pick.sling && !pick.zip && Math.abs(pick.top - (by + 7)) < 0.01 && pick.z - bz > 17.5 && pick.z - bz < 19.5,
                    String.format("a sling past that crown's top: sling %b, top %.2f at z %.2f", pick.sling, pick.top - by, pick.z - bz));
            // what the aimed rope leaves to you: played on, the flight clears the crown and then meets the wall
            AscentRules.Kin k = new AscentRules.Kin().set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
            boolean free = false;
            int age = 0;
            double met = Double.NaN;
            for (int i = 0; i < 60 && Double.isNaN(met); i++) {
                if (!free && i > 0) {
                    free = AscentSwing.slingStep(k, pick.px, pick.py, pick.pz, ++age);
                } else {
                    k.vy = (k.vy - AscentRules.G) * AscentRules.CARRY_DRAG_V;
                    k.vx *= AscentRules.CARRY_DRAG_H;
                    k.vz *= AscentRules.CARRY_DRAG_H;
                    k.x += k.vx;
                    k.y += k.vy;
                    k.z += k.vz;
                }
                if (space.bodyHits(k.x, k.y - AscentRules.HAND_HEIGHT, k.z)) met = k.z - bz;
            }
            h.assertTrue(met > 24, String.format("the flight past the crown meets the wall (it met something at z %.2f)", met));
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.snapTo(bx + 8.5, by + 1, bz + 4.5, 0, 0);
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the crown as a real anchor");
            h.assertTrue(!AscentSwing.aimed(space, hand, ex, ey, ez, 0, -60, AscentRules.TETHER_RANGE, true, w, pick) && Double.isNaN(w.aimT),
                    "a look at the sky meets nothing");
            h.assertTrue(!AscentSwing.aimed(space, hand, ex, ey, ez, 0, 40, AscentRules.TETHER_RANGE, true, w, pick)
                    && w.aimT > 2 && w.aimT < 6, String.format("a look at the floor meets it %.1f off and gives no sling", w.aimT));
            // the tree and the wall gone: standing on the floor (the hop a press from the ground starts with), a pillar of
            // leaves 9 high only 5.5 ahead, too near to sling past: the rope pulls you onto its top
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 1, 0), o.offset(16, 24, 48))) level.setBlock(q, air, flags);
            for (int y = 1; y <= 9; y++) level.setBlock(o.offset(8, y, 10), leaves, flags);
            AscentRules.Kin ground = new AscentRules.Kin().set(bx + 8.5, by + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.5, 0);
            double gy = by + 1.62;
            float up = (float) -Math.toDegrees(Math.atan2(by + 4.5 - gy, 10 - 4.5));
            h.assertTrue(AscentSwing.aimed(space, ground, ex, gy, ez, 0, up, AscentRules.TETHER_RANGE, true, w, pick)
                    && pick.zip && Math.abs(pick.top - (by + 9)) < 0.01,
                    String.format("from the floor, a pillar of leaves too near to sling: pulled onto its top (zip %b, top %.2f; sling: %s, onto: %s)",
                            pick.zip, pick.top - by, AscentSwing.SLING_NO[w.aimSling], AscentSwing.ONTO_NO[w.aimOnto]));
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the pillar's top edge as a real anchor");
            // a canopy of leaves 6 over the floor, looked at steeply from under it: pulled up to it, to hang there
            for (int y = 1; y <= 9; y++) level.setBlock(o.offset(8, y, 10), air, flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 7, 2), o.offset(10, 7, 7))) level.setBlock(q, leaves, flags);
            h.assertTrue(AscentSwing.aimed(space, ground, ex, gy, ez, 0, -75, AscentRules.TETHER_RANGE, true, w, pick)
                    && pick.zip && Double.isNaN(pick.top) && Math.abs(pick.y - (by + 6.01)) < 0.02,
                    String.format("from under a canopy: up under it (zip %b, top %.2f, at %.2f)", pick.zip, pick.top, pick.y - by));
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the canopy's underside as a real anchor");
            // lower than you (12 Nov 2026, Euan: "even the ground or anything in front of me but slightly lower ... use the
            // rope as a sling"): 10 up (a hill's top), a low crown 14.5 ahead looked down at is slung off, the pull aimed
            // a little over the hand
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 7, 2), o.offset(10, 7, 7))) level.setBlock(q, air, flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 1, 19), o.offset(10, 3, 21))) level.setBlock(q, leaves, flags);
            AscentRules.Kin high = new AscentRules.Kin().set(bx + 8.5, by + 10 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.3, 0.36);
            double hy = by + 10 + 1.62;
            float down = (float) -Math.toDegrees(Math.atan2(by + 2 - hy, 19 - 4.5));
            h.assertTrue(AscentSwing.aimed(space, high, ex, hy, ez, 0, down, AscentRules.TETHER_RANGE, true, w, pick)
                    && pick.sling && Math.abs(pick.top - (by + 3)) < 0.01 && pick.py >= high.y + 1.49 && pick.y < high.y
                    && pick.z - bz > 18.5 && pick.z - bz < 19.5,
                    String.format("from 10 up, a crown under you: slung off (sling %b, top %.2f, pull at %.2f over the hand, bite at z %.2f; sling: %s, onto: %s)",
                            pick.sling, pick.top - by, pick.py - high.y, pick.z - bz, AscentSwing.SLING_NO[w.aimSling], AscentSwing.ONTO_NO[w.aimOnto]));
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the low crown's face as a real anchor");
            // the same falling fast (after a sling): the bite catches the fall, so the way is open
            AscentRules.Kin falling = new AscentRules.Kin().set(bx + 8.5, by + 10 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, -0.9, 0.36);
            h.assertTrue(AscentSwing.aimed(space, falling, ex, hy, ez, 0, down, AscentRules.TETHER_RANGE, true, w, pick) && pick.sling,
                    String.format("falling from 10 up, the crown under you: still slung off (sling %b; sling: %s)", pick.sling,
                            AscentSwing.SLING_NO[w.aimSling]));
            // and from the floor, the floor 15 ahead looked at: slung off it (the bite is the look's hit, as every hit 0.04
            // out of the face it met: LevelSpace.ray)
            for (BlockPos q : BlockPos.betweenClosed(o.offset(6, 1, 19), o.offset(10, 3, 21))) level.setBlock(q, air, flags);
            float ahead = (float) -Math.toDegrees(Math.atan2(by - gy, 15));
            h.assertTrue(AscentSwing.aimed(space, ground, ex, gy, ez, 0, ahead, AscentRules.TETHER_RANGE, true, w, pick)
                    && pick.sling && Math.abs(pick.y - (by + 0.04)) < 0.01 && pick.py >= ground.y + 1.49,
                    String.format("from the floor, the floor 15 ahead: slung off it (sling %b, bite %.2f up, pull at %.2f over the hand; sling: %s)",
                            pick.sling, pick.y - by, pick.py - ground.y, AscentSwing.SLING_NO[w.aimSling]));
            h.assertTrue(AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the floor as a real anchor");
            // an anchor on a cell's face where the block's shape stops short of it (a wall's post): the server would refuse
            // it, so the bite moves onto the post (12 Nov 2026: "no block's collision at the anchor" in Euan's play)
            level.setBlock(o.offset(8, 1, 30), Blocks.COBBLESTONE_WALL.defaultBlockState(), flags);
            AscentSwing.Pick edge = new AscentSwing.Pick();
            edge.x = bx + 7.96;
            edge.y = by + 0.5;
            edge.z = bz + 30.5;
            h.assertTrue(!AscentServer.anchorReal(p, edge.x, edge.y, edge.z), "the wall cell's face, off its post: no collision there");
            h.assertTrue(AscentSwing.bites(space, edge) && AscentServer.anchorReal(p, edge.x, edge.y, edge.z) && Math.abs(edge.x - (bx + 7.96)) <= 0.46,
                    String.format("the bite moves onto the post, and the server takes it (now at x %.2f)", edge.x - bx));
            // a tall tree with room under its crown (12 Nov 2026, Euan: "there's a clear way to swing very well under that
            // place ... it makes you swing on top"): its crown 9 over the floor, looked at from a sprint jump 16.5 off and
            // beside its trunk. The rope swings you under it, from its underside, wound in to the swing's length on the way
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 1, 0), o.offset(16, 24, 48))) level.setBlock(q, air, flags);
            for (int y = 1; y <= 14; y++) level.setBlock(o.offset(8, y, 24), Blocks.OAK_LOG.defaultBlockState(), flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(5, 10, 21), o.offset(11, 14, 27))) {
                if (level.getBlockState(q).isAir()) level.setBlock(q, leaves, flags);
            }
            AscentRules.Kin under = new AscentRules.Kin().set(bx + 6.5, by + 1 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.3, 0.36);
            float crown = (float) -Math.toDegrees(Math.atan2(by + 11.5 - ey, 21 - 4.5));
            h.assertTrue(AscentSwing.aimed(space, under, bx + 6.5, ey, ez, 0, crown, AscentRules.TETHER_RANGE, true, w, pick)
                    && !pick.sling && !pick.zip && pick.reel && Math.abs(pick.y - (by + 9)) < 0.1 && pick.z - bz > 21 && pick.z - bz < 21.6,
                    String.format("a crown 9 up with room under it: swung under (sling %b, zip %b, reel %b, at %.2f up, z %.2f)",
                            pick.sling, pick.zip, pick.reel, pick.y - by, pick.z - bz));
            h.assertTrue(AscentSwing.bites(space, pick) && AscentServer.anchorReal(p, pick.x, pick.y, pick.z), "the server takes the crown's underside");
            // played on: wound in, under the crown and up the far side, clear of the floor, the leaves and the trunk
            AscentRules.Kin k2 = new AscentRules.Kin().set(under.x, under.y, under.z, under.vx, under.vy, under.vz);
            AscentSwing.Rope rope = new AscentSwing.Rope().set(pick.x, pick.y, pick.z, pick.len, pick.fitLen);
            rope.reel = pick.reel;
            double[] pv = new double[4];
            double low = 99;
            boolean rose = false;
            for (int i = 0; i < 60 && !rose; i++) {
                AscentSwing.tick(space, k2, rope, 0, 0, w);
                low = Math.min(low, k2.y - AscentRules.HAND_HEIGHT - by);
                h.assertTrue(!space.bodyHits(k2.x, k2.y - AscentRules.HAND_HEIGHT, k2.z),
                        String.format("the swing under the crown meets nothing (tick %d, the hand %.2f up at z %.2f)", i, k2.y - by, k2.z - bz));
                AscentSwing.pivot(k2.x, k2.z, rope.ax, rope.ay, rope.az, 0, pv);
                rose = k2.z > pv[2] && AscentSwing.angleFromBottom(k2, pv[0], pv[1], pv[2]) > 25;
            }
            h.assertTrue(rose && rope.len <= pick.fitLen + 1e-3 && low > 0.3,
                    String.format("wound in to %.2f (fits %.2f), it swung under and 25 degrees up the far side (%b), the feet %.2f over the floor at the lowest",
                            rope.len, pick.fitLen, rose, low));
        } finally {
            AscentSwing.guide = guide;
            AscentSwing.hold = hold;
            AscentSwing.T = was;
            for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 24, 50))) level.setBlock(q, air, flags);
        }
        h.succeed();
    }

    /**
     * A 22-block pillar 16 ahead and to the right over a floor (one dead ahead gives no swing: the arc would meet it),
     * then a 5-block wall 8 ahead, built in the level: the swing's find picks the same anchor from the blocks as from
     * the same boxes, the server accepts it, and the wall gives a zip to its top.
     */
    private static void level(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 80);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 26, 34))) level.setBlock(q, air, flags);
        try {
            // the floor's top is the boxes' ground (local y 0 = o.y + 1)
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 0, 0), o.offset(16, 0, 32))) level.setBlock(q, stone, flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(12, 1, 20), o.offset(15, 22, 23))) level.setBlock(q, stone, flags);
            SwingSpace.Boxes boxes = new SwingSpace.Boxes().add(12, 0, 20, 16, 22, 24, false);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick fromBoxes = new AscentSwing.Pick(), fromLevel = new AscentSwing.Pick();
            // airborne 2 over the floor, running toward the pillar
            AscentRules.Kin hand = new AscentRules.Kin().set(8.5, 2 + AscentRules.HAND_HEIGHT, 4.5, 0, 0.1, 0.6);
            h.assertTrue(AscentSwing.find(boxes, hand, 0, AscentRules.TETHER_RANGE, w, fromBoxes), "the boxes give a swing off the pillar");
            hand.set(bx + 8.5, by + 2 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.1, 0.6);
            long t0 = System.nanoTime();
            boolean found = AscentSwing.find(space, hand, 0, AscentRules.TETHER_RANGE, w, fromLevel);
            long took = System.nanoTime() - t0;
            h.assertTrue(found, "the blocks give a swing off the pillar");
            double dx = fromLevel.x - bx - fromBoxes.x, dy = fromLevel.y - by - fromBoxes.y, dz = fromLevel.z - bz - fromBoxes.z;
            h.assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) < 1 && Math.abs(fromLevel.fitLen - fromBoxes.fitLen) < 1,
                    String.format("blocks and boxes agree: (%.2f %.2f %.2f) fit %.2f vs (%.2f %.2f %.2f) fit %.2f",
                            fromLevel.x - bx, fromLevel.y - by, fromLevel.z - bz, fromLevel.fitLen, fromBoxes.x, fromBoxes.y, fromBoxes.z, fromBoxes.fitLen));
            h.assertTrue(fromLevel.x - bx > 11.5 && fromLevel.z - bz > 19.5 && fromLevel.z - bz < 24.5 && fromLevel.y - by > 10,
                    String.format("the anchor is on the pillar, high up: (%.2f %.2f %.2f)", fromLevel.x - bx, fromLevel.y - by, fromLevel.z - bz));
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.snapTo(bx + 8.5, by + 2, bz + 4.5, 0, 0);
            h.assertTrue(AscentServer.anchorReal(p, fromLevel.x, fromLevel.y, fromLevel.z), "the server takes the anchor as real");
            h.assertTrue(AscentAnchors.blockOf(level, fromLevel.x, fromLevel.y, fromLevel.z) != null, "the anchor's block is found");
            // the marker runs this every other tick in the air: its warm cost (the median of 41)
            long[] times = new long[41];
            for (int i = 0; i < times.length; i++) {
                hand.set(bx + 8.5, by + 2 + AscentRules.HAND_HEIGHT, bz + 4.5 + i * 0.05, 0, 0.1, 0.6);
                long t1 = System.nanoTime();
                AscentSwing.find(space, hand, 0, AscentRules.TETHER_RANGE, w, fromLevel);
                times[i] = System.nanoTime() - t1;
            }
            java.util.Arrays.sort(times);
            dev.awaken.Awaken.LOG.info("[ascent] swing find on blocks: {} us cold, {} us warm (median)", took / 1000, times[20] / 1000);

            // a wall 5 high 8 ahead, standing on the floor and looking up at it a little: no swing, a zip to its top
            for (BlockPos q : BlockPos.betweenClosed(o.offset(12, 1, 20), o.offset(15, 22, 23))) level.setBlock(q, air, flags);
            for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 1, 12), o.offset(16, 5, 13))) level.setBlock(q, stone, flags);
            AscentSwing.Pick zip = new AscentSwing.Pick();
            h.assertTrue(AscentSwing.zipTo(space, bx + 8.5, by + 1.62, bz + 4.5, 0, -15, AscentRules.TETHER_RANGE, w, zip),
                    "the wall gives a zip up to its top");
            h.assertTrue(zip.zip && Math.abs(zip.top - (by + 5)) < 0.01 && zip.nz == -1, "the zip's lip is the wall's top: " + (zip.top - by));
            h.assertTrue(AscentServer.anchorReal(p, zip.x, zip.y, zip.z), "the server takes the lip as real");
        } finally {
            for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 26, 34))) level.setBlock(q, air, flags);
        }
        h.succeed();
    }

    // ---- the sky stair (SPEC §17.9) ------------------------------------------------------------------------------------

    /** The bench's deck: 206 over its ground (a platform at 276 over land at 70). */
    private static final int STAIR_DECK = 206;

    /**
     * The sky stair (SPEC §17.9, 11 Nov 2026): the bench's climbing bot ({@link SwingBench#climb}) up the stair to the
     * deck, the stones as their own cells, with the deck, its rim's studs, its cabin and keel, the pier, the islands'
     * places and the old chain and rings as they stand. First a sweep of shapes (logged, never judged), then the stair as
     * built: the bot that hops and holds the rope up nearly every time, the one that only holds forward and the rope up
     * most times, never a hard hit, in under a minute, and a third of the climb or more off the ground (the core gives
     * Wings to a climb by movement: a third of the last 20 s in the air or on a wall, Ascent.arrivedByMovement). Every
     * row is logged before any gate is judged.
     */
    private static void skyStair(GameTestHelper h) {
        AscentSwing.Tune was = AscentSwing.T;
        boolean guide = AscentSwing.guide, holdWas = AscentSwing.hold;
        AscentSwing.T = AscentSwing.Tune.JAR;
        AscentSwing.guide = true;
        AscentSwing.hold = true;
        java.util.List<String> bad = new java.util.ArrayList<>();
        StringBuilder rows = new StringBuilder();
        try {
            // radius, rise, run, the top stone's distance from the deck's middle and depth under it
            double[][] shapes = {{30, 12, 18, 29, 8}, {30, 12, 21, 29, 8}, {30, 13, 20, 29, 8}, {30, 11, 20, 29, 8}, {32, 12, 20, 29, 8},
                    {30, 13, 21, 29, 8}, {28, 12, 20, 29, 8}};
            for (double[] sh : shapes) {
                StairStats st = stairRuns(new dev.awaken.above.ruin.SkyRig.Stair(sh[0], sh[1], sh[2], 3.6, (int) sh[4], sh[3], 6));
                rows.append("\nsweep ").append(st);
            }
            StringBuilder trace = new StringBuilder();
            StairStats st = stairRuns(dev.awaken.above.ruin.SkyRig.Stair.DEFAULT, trace);
            rows.append("\nbuilt ").append(st).append(trace);
            // the real swing (SPEC §62.9) on the stair as built, logged beside it (its own gates are RealRopeTests')
            AscentSwing.real = true;
            try {
                rows.append("\nreal ").append(stairRuns(dev.awaken.above.ruin.SkyRig.Stair.DEFAULT));
            } finally {
                AscentSwing.real = false;
            }
            gate(bad, st.hopUp >= st.hopOf - 1, "the hopping bot gets up " + st.hopUp + " of " + st.hopOf);
            gate(bad, st.keyUp * 3 >= st.keyOf * 2, "the key-only bot gets up " + st.keyUp + " of " + st.keyOf);
            gate(bad, st.hard == 0, st.hard + " hard hits on the stair");
            gate(bad, st.hopSeconds <= 60, "the climb takes " + st.hopSeconds + " s");
            gate(bad, st.air >= 0.34, "a third of the climb off the ground: " + st.air);
        } finally {
            AscentSwing.T = was;
            AscentSwing.guide = guide;
            AscentSwing.hold = holdWas;
        }
        dev.awaken.Awaken.LOG.info("[ascent] sky stair bench:{}", rows);
        h.assertTrue(bad.isEmpty(), String.join("; ", bad));
        h.succeed();
    }

    /** One stair shape's climbs: the hopping bot's and the key-only bot's ups, the median climb, hard hits, the share off the ground. */
    private record StairStats(String shape, int stones, int hopUp, int hopOf, double hopSeconds, double ropes, double slings, int hard,
                              double air, String hopEnds, int keyUp, int keyOf, double keySeconds, String keyEnds) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%s: %d stones | hop %d/%d up, %.1f s, ropes %.1f, slings %.1f, hard %d, air %.0f%%%s | key %d/%d up, %.1f s%s",
                    shape, stones, hopUp, hopOf, hopSeconds, ropes, slings, hard, air * 100, hopEnds, keyUp, keyOf, keySeconds, keyEnds);
        }
    }

    /**
     * Climbs one stair shape on the bench: 12 hopping starts and 6 key-only, each from the ground short of the first stone
     * (where the next stone round would have stood, 2 to 10 further back, up to 3 off the line either side, at 0.7 to 1
     * of a run's pace), seeded.
     */
    private static StairStats stairRuns(dev.awaken.above.ruin.SkyRig.Stair shape) {
        return stairRuns(shape, null);
    }

    /** As above; with {@code trace}, the first three hopping climbs and the first two key-only ones, throw by throw. */
    private static StairStats stairRuns(dev.awaken.above.ruin.SkyRig.Stair shape, @org.jspecify.annotations.Nullable StringBuilder trace) {
        var stones = dev.awaken.above.ruin.SkyRig.stair(0, STAIR_DECK, 0, shape, (x, z) -> 0, (x, z) -> false);
        java.util.List<double[]> tops = new java.util.ArrayList<>();
        java.util.List<java.util.List<double[]>> pieces = new java.util.ArrayList<>();
        for (var s : stones) {
            tops.add(new double[] {s.x() + 0.5, s.top(), s.z() + 0.5, s.rad()});
            java.util.List<int[]> cells = new java.util.ArrayList<>();
            dev.awaken.above.ruin.SkyRig.stoneCells(s, (x, y, z, c) -> {
                if (c != dev.awaken.above.ruin.SkyRig.Cell.FLOWER && c != dev.awaken.above.ruin.SkyRig.Cell.ROOTS) cells.add(new int[] {x, y, z});
            });
            pieces.add(boxesOf(cells));
        }
        tops.add(new double[] {0.5, STAIR_DECK, 0.5, 18});
        java.util.List<double[]> always = new java.util.ArrayList<>();
        pieces.add(deckBoxes(always));
        // where the next stone round would have stood: the ground there, a little further back
        var helix = dev.awaken.above.ruin.SkyRig.helix(0, STAIR_DECK, 0, shape, -64);
        var low = stones.getFirst();
        var next = helix.get(Math.min(helix.size() - 1, stones.size()));
        double dx = low.x() - next.x(), dz = low.z() - next.z(), d = Math.max(1e-6, Math.hypot(dx, dz));
        dx /= d;
        dz /= d;
        SwingBench.Rng r = new SwingBench.Rng(31);
        java.util.List<Double> hopTicks = new java.util.ArrayList<>(), keyTicks = new java.util.ArrayList<>();
        java.util.Map<String, Integer> hopEnds = new java.util.TreeMap<>(), keyEnds = new java.util.TreeMap<>();
        int hopUp = 0, keyUp = 0, hard = 0, air = 0, ticks = 0, ropes = 0, slings = 0;
        for (int i = 0; i < 18; i++) {
            boolean key = i >= 12;
            double back = 2 + r.next() * 8, side = (r.next() - 0.5) * 6, speed = 0.7 + r.next() * 0.3;
            double sx = next.x() + 0.5 - dx * back - dz * side, sz = next.z() + 0.5 - dz * back + dx * side;
            SwingBench.climbTrace = trace != null ? new StringBuilder() : null;
            SwingBench.Climb c = SwingBench.climb(tops, pieces, always, sx, sz, speed, key, 3600);
            // traced: the climbs that fell, stuck or hit something hard
            if (trace != null && (!c.up() || c.hard() > 0)) trace.append("\n  ").append(key ? "key " : "hop ").append(i).append(':').append(SwingBench.climbTrace);
            SwingBench.climbTrace = null;
            hard += c.hard();
            if (!key) {
                air += c.air();
                ticks += c.ticks();
                ropes += c.ropes();
                slings += c.slings();
            }
            if (c.up()) {
                if (key) keyUp++;
                else hopUp++;
                (key ? keyTicks : hopTicks).add((double) c.ticks());
            } else {
                (key ? keyEnds : hopEnds).merge(c.end(), 1, Integer::sum);
            }
        }
        String label = String.format(java.util.Locale.ROOT, "radius %.0f, rise %.0f, run %.0f, top %.0f out %d under", shape.radius(), shape.rise(),
                shape.run(), shape.topRadius(), shape.topGap());
        return new StairStats(label, stones.size(), hopUp, 12, median(hopTicks) / 20, ropes / 12.0, slings / 12.0, hard,
                ticks == 0 ? 0 : air / (double) ticks, hopEnds.isEmpty() ? "" : ", " + hopEnds, keyUp, 6, median(keyTicks) / 20,
                keyEnds.isEmpty() ? "" : ", " + keyEnds);
    }

    private static double median(java.util.List<Double> v) {
        if (v.isEmpty()) return Double.NaN;
        java.util.List<Double> s = new java.util.ArrayList<>(v);
        java.util.Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /**
     * The platform round the stair's top as boxes (RuinBuilder.sky's, from its centre at 0, 0): the deck's disc and its
     * rim's studs (but on the pier, which the rig clears), the cabin, the keel, the pier, the four islands' places (19 out,
     * at the deck's height) and the old way up's perch; the chain and its rings, which run the whole height, go into
     * {@code always}.
     */
    private static java.util.List<double[]> deckBoxes(java.util.List<double[]> always) {
        int deck = STAIR_DECK;
        var l = dev.awaken.above.ruin.SkyRig.layout(0, deck, 0, 0);
        java.util.Set<Long> pier = new java.util.HashSet<>();
        for (BlockPos q : l.pier()) pier.add(BlockPos.asLong(q.getX(), 0, q.getZ()));
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d <= 11.6) {
                cells.add(new int[] {x, deck, z});
                if (d > 10.6 && Math.floorMod(x * 3 + z * 7, 5) == 0 && !pier.contains(BlockPos.asLong(x, 0, z))) cells.add(new int[] {x, deck + 1, z});
            }
            for (int ly = 4; ly < 12; ly++) if (d <= 1.0 + (ly - 4) * 1.32) cells.add(new int[] {x, deck - 12 + ly, z});
            if (Math.abs(x) <= 4 && Math.abs(z) <= 4) for (int y = deck + 1; y <= deck + 6; y++) cells.add(new int[] {x, y, z});
        }
        for (BlockPos q : l.pier()) cells.add(new int[] {q.getX(), q.getY(), q.getZ()});
        for (BlockPos q : l.perch()) cells.add(new int[] {q.getX(), q.getY(), q.getZ()});
        // the island the deck stands in (RuinBuilder.garden, SKY_STYLE 3), its collar, trees and the four islands as the
        // real blueprint lays them (unturned; the ring is the same all round to within its noise), and the arch
        java.util.Map<Long, BlockState> laid = new java.util.HashMap<>();
        dev.awaken.above.ruin.RuinBuilder.build(dev.awaken.above.ruin.RuinKind.SKY, 7L, 1, new dev.awaken.above.ruin.RuinBuilder.Sink() {
            @Override
            public void set(int x, int y, int z, BlockState st) {
                laid.put(BlockPos.asLong(x, y, z), st);
            }

            @Override
            public void fillDown(int x, int y, int z, BlockState st, int depth) {}

            @Override
            public void chest(int x, int y, int z, net.minecraft.core.Direction facing, net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table) {}
        });
        laid.forEach((k, st) -> {
            if (st.isSolid()) cells.add(new int[] {BlockPos.getX(k) - 24, deck - 12 + BlockPos.getY(k), BlockPos.getZ(k) - 24});
        });
        for (int[] c : dev.awaken.above.ruin.SkyRig.archCells()) cells.add(new int[] {c[0], deck + c[1], c[2]});
        for (int[] c : new int[][] {{0, -19}, {19, 0}, {0, 19}, {-19, 0}}) {
            for (int x = c[0] - 4; x <= c[0] + 4; x++) for (int z = c[1] - 4; z <= c[1] + 4; z++) {
                double d = Math.hypot(x - c[0], z - c[1]);
                if (d > 3.2) continue;
                int depth = (int) Math.round((3.2 - d) * 1.6 + 1);
                for (int k = 0; k <= depth; k++) cells.add(new int[] {x, deck - k, z});
            }
        }
        BlockPos top = l.chainTop();
        always.add(new double[] {top.getX() + 0.4, l.chainBottom(), top.getZ() + 0.4, top.getX() + 0.6, top.getY() + 1, top.getZ() + 0.6});
        for (BlockPos q : l.rings()) always.add(new double[] {q.getX(), q.getY(), q.getZ(), q.getX() + 1, q.getY() + 1, q.getZ() + 1});
        return boxesOf(cells);
    }

    /** Cells as few boxes: runs along x merged, then runs of the same footprint stacked. */
    static java.util.List<double[]> boxesOf(java.util.List<int[]> cells) {
        cells.sort(java.util.Comparator.comparingInt((int[] c) -> c[1]).thenComparingInt(c -> c[2]).thenComparingInt(c -> c[0]));
        java.util.List<double[]> runs = new java.util.ArrayList<>();
        for (int i = 0; i < cells.size(); ) {
            int[] c = cells.get(i);
            int j = i + 1, x1 = c[0];
            while (j < cells.size() && cells.get(j)[1] == c[1] && cells.get(j)[2] == c[2] && cells.get(j)[0] <= x1 + 1) {
                x1 = Math.max(x1, cells.get(j)[0]);
                j++;
            }
            runs.add(new double[] {c[0], c[1], c[2], x1 + 1, c[1] + 1, c[2] + 1});
            i = j;
        }
        java.util.Map<String, double[]> open = new java.util.HashMap<>();
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (double[] q : runs) {
            String key = q[0] + "," + q[2] + "," + q[3];
            double[] o = open.get(key);
            if (o != null && o[4] == q[1]) o[4] = q[4];
            else {
                double[] b = q.clone();
                open.put(key, b);
                out.add(b);
            }
        }
        return out;
    }
}
