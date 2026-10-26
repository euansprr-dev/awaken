package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The rappel and the rope's fittings (SPEC §28.5), as one GameTest: the pure pay-out (bites and the smooth run, the
 * floor and the reach as its limits, a kick's bound), the fittings' numbers, the server's acceptance and refusals of a
 * real survival player's rappel (over-length, over-rate, astray; the iron line's +6, the Deepglass line's faster run),
 * and no fall damage at the bottom.
 */
final class AscentRappelTests {
    private AscentRappelTests() {}

    static void rappel(GameTestHelper h) {
        payOut(h);
        bound(h);
        server(h);
        h.succeed();
    }

    /** The pay-out's step: half a block in bites of three ticks (the catch on the third), or a smooth run; its limits. */
    private static void payOut(GameTestHelper h) {
        double paid = 0, most = 0;
        int catches = 0;
        for (int t = 0; t < 30; t++) {
            double step = AscentRules.payStep(paid, false);
            if (AscentRules.payCatch(paid, step, false)) {
                catches++;
                h.assertTrue(t % 3 == 2, "a bite's catch on its third tick, not tick " + t);
            }
            most = Math.max(most, step);
            paid += step;
        }
        h.assertTrue(Math.abs(paid - 5.0) < 1e-6 && catches == 10, "30 ticks of bites pay 5 blocks with 10 catches: " + paid + ", " + catches);
        h.assertTrue(most <= 0.24 + 1e-9 && Math.abs(AscentRules.payRate(false) - 0.5 / 3) < 1e-9, "a bite's steps " + most);
        double smooth = 0;
        for (int t = 0; t < 30; t++) {
            double step = AscentRules.payStep(smooth, true);
            h.assertFalse(AscentRules.payCatch(smooth, step, true), "the Deepglass line never catches");
            smooth += step;
        }
        h.assertTrue(smooth > paid * 1.2 && Math.abs(smooth - 30 * AscentRules.RAPPEL_SMOOTH) < 1e-6,
                "the Deepglass line runs smooth and faster: " + smooth + " against " + paid);
        // the limits: the floor under the hand (the feet set down on it and a hair past), and the reach
        double toFloor = AscentRules.payLimit(44, 0, 40, 0, 0, 0, 0);
        h.assertTrue(Math.abs(toFloor - (40 - AscentRules.HAND_HEIGHT + AscentRules.RAPPEL_FLOOR_SLACK)) < 1e-9, "to the floor " + toFloor);
        h.assertTrue(AscentRules.payLimit(30, 0, 40, 0, 0, 0, 0) == 30, "never past the reach");
        // a rope paid out down a 40-high drop lets the body down at its pace, stops at the floor, and stays on the rope
        AscentRules.Kin k = new AscentRules.Kin().set(0, 30, 0.3, 0, 0, 0);
        double ax = 0, ay = 40, az = 0.04, len = Math.sqrt(10 * 10 + 0.26 * 0.26), floor = 0;
        double[] s = new double[3];
        double y0 = k.y;
        paid = 0;
        for (int t = 0; t < 400; t++) {
            double limit = AscentRules.payLimit(44, ax, ay, az, k.x, k.z, floor);
            double pay = Math.min(AscentRules.payStep(paid, false), Math.max(0, limit - len));
            len = AscentRules.payOut(k, ax, ay, az, len, pay);
            paid += pay;
            AscentRules.lean(k, ax, az);
            AscentRules.tetherStep(k, ax, ay, az, len, 0, 0, 0, s);
            double r = Math.sqrt(k.x * k.x + (k.y - ay) * (k.y - ay) + (k.z - az) * (k.z - az));
            h.assertTrue(r <= len + 1e-6, "the body past the rope's end at tick " + t);
            if (t == 59) h.assertTrue(Math.abs((y0 - k.y) - 10) < 0.6, "60 ticks of bites let the body down 10: " + (y0 - k.y));
        }
        h.assertTrue(k.y - AscentRules.HAND_HEIGHT >= floor - AscentRules.RAPPEL_FLOOR_SLACK - 1e-3 && k.y - AscentRules.HAND_HEIGHT < floor + 0.05,
                "paid out to the floor, never through it: feet at " + (k.y - AscentRules.HAND_HEIGHT));
        // the fittings
        h.assertTrue(AscentRules.reach(0, 0, RopeKit.LINE_NONE) == 44 && AscentRules.reach(0, 0, RopeKit.LINE_IRON) == 50
                && AscentRules.reach(0, 0, RopeKit.LINE_DEEPGLASS) == 50 && AscentRules.reach(10, 6, RopeKit.LINE_IRON) == 60, "the line's +6");
        h.assertTrue(AscentRules.reelRate(true) > AscentRules.reelRate(false) * 1.4 && AscentRules.fitReelRate(true) > AscentRules.fitReelRate(false) * 1.4,
                "the gold reel winds faster");
        h.assertTrue(RopeKit.ofBits(new RopeKit(RopeKit.SCHEMA, RopeKit.LINE_DEEPGLASS, RopeKit.REEL_GOLD).bits()).equals(
                new RopeKit(RopeKit.SCHEMA, RopeKit.LINE_DEEPGLASS, RopeKit.REEL_GOLD)), "the kit's bits round-trip");
    }

    /** A kick off a wall (its face at z = 0, the body's centre held 0.3 off it): out 1-2.5 and back within 30 ticks. */
    private static void bound(GameTestHelper h) {
        for (double depth : new double[]{6, 20, 40}) {
            AscentRules.Kin k = new AscentRules.Kin().set(0, -depth, 0.3, 0, 0, 0);
            double len = Math.sqrt(depth * depth + 0.26 * 0.26), out = 0;
            double[] s = new double[3];
            AscentRules.kick(k, 0, 1);
            int back = -1;
            for (int t = 1; t < 60 && back < 0; t++) {
                AscentRules.lean(k, 0, 0.04);
                AscentRules.tetherStep(k, 0, 0, 0.04, len, 0, 0, 0, s);
                out = Math.max(out, k.z);
                if (k.z < 0.3) {
                    k.z = 0.3;
                    if (t > 3) back = t;
                }
            }
            h.assertTrue(out > 1.0 && out < 2.5 && back > 8 && back < 30, "a bound on " + depth + " of rope went out " + out + " and back at " + back);
        }
    }

    /**
     * A real survival player's rappel through the server's checks: taken within reach and rate; refused over its length
     * (the iron line's +6 moves the line), over the rate (the Deepglass line's run is faster), and astray from its
     * anchor; never teleported; and a landing at the bottom costs nothing.
     */
    private static void server(GameTestHelper h) {
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        double x0 = base.getX() + 0.5, y0 = h.getLevel().getMaxY() - 50, z0 = base.getZ() + 0.5;
        BlockPos block = BlockPos.containing(x0, y0 + AscentRules.HAND_HEIGHT + 6, z0);
        h.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
        double ax = block.getX() + 0.5, ay = block.getY() - 0.04, az = block.getZ() + 0.5;
        double rope = ay - (y0 + AscentRules.HAND_HEIGHT);
        AscentTests.Flyer f = AscentTests.Flyer.at(h, x0, y0, z0);
        try {
            Ascent.grant(f.p(), Ascent.Tier.TETHER, "test");
            AscentMotion m = AscentServer.motion(f.p());
            // within reach and rate: taken
            hang(f, ax, ay, az, rope);
            h.assertTrue(m.state == AscentState.RAPPEL, "the server took the rappel: " + m.state);
            int refused = AscentServer.refusals;
            m.lenAt -= 10;
            state(f, ax, ay, az, rope + 1.5);
            h.assertTrue(m.state == AscentState.RAPPEL && AscentServer.refusals == refused, "1.5 paid out in 10 ticks is a rappel");
            // over the rate: 8 blocks in 5 ticks
            m.lenAt -= 5;
            state(f, ax, ay, az, rope + 9.5);
            h.assertTrue(m.state == AscentState.NONE && AscentServer.refusals == refused + 1, "8 blocks in 5 ticks refused");
            h.assertTrue(f.p().position().distanceTo(new net.minecraft.world.phys.Vec3(x0, y0, z0)) < 1e-6, "a refusal never moves the body");
            // the Deepglass line pays out faster: 3.2 in 10 ticks is over the bites' rate, within its
            hang(f, ax, ay, az, rope);
            m.lenAt -= 10;
            state(f, ax, ay, az, rope + 3.2);
            h.assertTrue(m.state == AscentState.NONE, "3.2 in 10 ticks is faster than the bites allow");
            f.p().setData(AscentContent.ROPE_KIT, RopeKit.EMPTY.withLine(RopeKit.LINE_DEEPGLASS));
            hang(f, ax, ay, az, rope);
            m.lenAt -= 10;
            state(f, ax, ay, az, rope + 3.2);
            h.assertTrue(m.state == AscentState.RAPPEL, "and within the Deepglass line's smooth run");
            // over the length: 49 is past a bare rope's 44 (and its block), within an iron line's 50
            f.p().setData(AscentContent.ROPE_KIT, RopeKit.EMPTY);
            hang(f, ax, ay, az, rope);
            m.lenAt -= AscentRules.RAPPEL_BANK_TICKS * 4L;
            m.payBank = 100;
            state(f, ax, ay, az, 49);
            h.assertTrue(m.state == AscentState.NONE, "a 49 rope is past a bare reach");
            f.p().setData(AscentContent.ROPE_KIT, RopeKit.EMPTY.withLine(RopeKit.LINE_IRON));
            hang(f, ax, ay, az, 44.5);
            h.assertTrue(m.state == AscentState.RAPPEL, "an iron line's 50 holds a 44.5 rope");
            state(f, ax, ay, az, 51.5);
            h.assertTrue(m.state == AscentState.NONE, "but not past its 50 and a block");
            // astray: the hand further from the anchor than the rope could reach
            hang(f, ax, ay, az, rope);
            h.assertFalse(AscentServer.rappelAstray(f.p(), m), "on its rope");
            m.len = 2;
            m.lenAt = AscentServer.now(f.p());
            m.lenAtMs = net.minecraft.util.Util.getMillis();
            h.assertTrue(AscentServer.rappelAstray(f.p(), m), "a 2 rope from 6 off is astray");
            int before = AscentServer.refusals;
            AscentServer.onState(f.p(), new AscentPayloads.State(AscentState.RAPPEL.ordinal(), ax, ay, az, 3F));
            h.assertTrue(m.state == AscentState.NONE && AscentServer.refusals == before + 1, "a rappel claimed short of the body is refused");
            // no fall damage at the bottom: a hard landing on a rappel costs nothing; the same landing off any rope hurts
            hang(f, ax, ay, az, rope);
            float health = f.p().getHealth();
            f.p().causeFallDamage(30, 1F, f.p().damageSources().fall());
            h.assertTrue(f.p().getHealth() >= health, "a landing on a rappel hurt: " + health + " -> " + f.p().getHealth());
            var onRope = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(f.p(), 30, 1F);
            AscentServer.onFall(onRope);
            h.assertTrue(onRope.getDistance() == 0 && onRope.getDamageMultiplier() == 0, "the rappel's landing is zeroed");
            // the same landing long after the rope is left alone (other systems may still soften it; not the Ascent)
            AscentServer.onState(f.p(), new AscentPayloads.State(AscentState.NONE.ordinal(), 0, 0, 0, 0));
            m.endedAt = AscentServer.now(f.p()) - 1000;
            var offRope = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(f.p(), 30, 1F);
            AscentServer.onFall(offRope);
            h.assertTrue(offRope.getDistance() == 30 && offRope.getDamageMultiplier() == 1F, "a landing off any rope is the world's (the test is real)");
            h.assertFalse(f.p().hasDisconnected(), "disconnected");
        } finally {
            f.remove();
            h.getLevel().setBlock(block, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** On a line at the anchor, then the rappel begun on it. */
    private static void hang(AscentTests.Flyer f, double ax, double ay, double az, double len) {
        AscentServer.onState(f.p(), new AscentPayloads.State(AscentState.TETHER.ordinal(), ax, ay, az, (float) len));
        state(f, ax, ay, az, len);
    }

    private static void state(AscentTests.Flyer f, double ax, double ay, double az, double len) {
        AscentServer.onState(f.p(), new AscentPayloads.State(AscentState.RAPPEL.ordinal(), ax, ay, az, (float) len));
    }
}
