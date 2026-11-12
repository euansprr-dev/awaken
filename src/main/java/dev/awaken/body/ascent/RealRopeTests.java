package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The real swing (SPEC §62.9) from real blocks, each press played as the client plays it (the pick where you look, the
 * throw's entry and flight, the bite, then the held rope with forward held): the rope stays as long as it bit (no pull),
 * a vanilla oak beside you swings you round it, a tall tree from a sprint jump hoists you up its near side into a swing
 * under its crown, and from over that crown the press swings you under it and out. Nothing is touched on the way.
 */
final class RealRopeTests {
    private RealRopeTests() {}

    static void register() {
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_rope_real", 40, real(RealRopeTests::stays));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_rope_round", 40, real(RealRopeTests::round));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_rope_hoist", 40, real(RealRopeTests::hoist));
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_rope_from_top", 40, real(RealRopeTests::fromTop));
    }

    /** A test on the real rope (the switch on), the jar's tune, the held rope and the guide as the client has them. */
    private static java.util.function.Consumer<GameTestHelper> real(java.util.function.Consumer<GameTestHelper> test) {
        return h -> {
            boolean was = AscentSwing.real, guide = AscentSwing.guide, hold = AscentSwing.hold;
            AscentSwing.Tune tune = AscentSwing.T;
            AscentSwing.real = true;
            AscentSwing.guide = true;
            AscentSwing.hold = true;
            AscentSwing.T = AscentSwing.Tune.JAR;
            try {
                test.accept(h);
            } finally {
                AscentSwing.real = was;
                AscentSwing.guide = guide;
                AscentSwing.hold = hold;
                AscentSwing.T = tune;
            }
        };
    }

    // ---- the places ----------------------------------------------------------------------------------------------------

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    /** A floor 17 by 49 high in the test's column, the space clear over it; local y 0 (the floor's top) is o.y + 1. */
    private static BlockPos ground(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 80);
        clear(level, o);
        for (BlockPos q : BlockPos.betweenClosed(o.offset(0, 0, 0), o.offset(16, 0, 48))) level.setBlock(q, Blocks.STONE.defaultBlockState(), FLAGS);
        return o;
    }

    private static void clear(ServerLevel level, BlockPos o) {
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-2, 0, -2), o.offset(18, 24, 50))) level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    /** Euan's tall tree (ascent_rope_aimed's): a trunk at x 8, z 24 to 14 up, a crown x 5..11, z 21..27, 9 to 14 up. */
    private static void tallTree(ServerLevel level, BlockPos o) {
        for (int y = 1; y <= 14; y++) level.setBlock(o.offset(8, y, 24), Blocks.OAK_LOG.defaultBlockState(), FLAGS);
        for (BlockPos q : BlockPos.betweenClosed(o.offset(5, 10, 21), o.offset(11, 14, 27))) {
            if (level.getBlockState(q).isAir()) level.setBlock(q, leaves(), FLAGS);
        }
    }

    /** A vanilla-like oak at (x, z): a trunk 6 high, a crown of two wide layers 3 to 5 up and two narrow ones to 7. */
    private static void oak(ServerLevel level, BlockPos o, int x, int z) {
        for (int y = 1; y <= 6; y++) level.setBlock(o.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState(), FLAGS);
        for (BlockPos q : BlockPos.betweenClosed(o.offset(x - 2, 4, z - 2), o.offset(x + 2, 5, z + 2))) {
            if (level.getBlockState(q).isAir()) level.setBlock(q, leaves(), FLAGS);
        }
        for (BlockPos q : BlockPos.betweenClosed(o.offset(x - 1, 6, z - 1), o.offset(x + 1, 7, z + 1))) {
            if (level.getBlockState(q).isAir()) level.setBlock(q, leaves(), FLAGS);
        }
    }

    // ---- the press, played -----------------------------------------------------------------------------------------------

    /** The yaw (degrees, Minecraft's: 0 looks along +z) and pitch (down positive) from an eye to a point. */
    private static float yawTo(double ex, double ez, double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-(x - ex), z - ez));
    }

    private static float pitchTo(double ex, double ey, double ez, double x, double y, double z) {
        return (float) -Math.toDegrees(Math.atan2(y - ey, Math.hypot(x - ex, z - ez)));
    }

    /** What a press did, tick by tick after the bite. */
    private static final class Ride {
        /** The rope's length at the bite and 10 ticks on; ticks of the first second moving straight at the anchor. */
        double biteLen, lenAt10 = Double.NaN;
        int straightIn;
        /** The tick the body first touched something (0: in the rope's flight), or -1; the tick the hoist ended, or -1. */
        int touched = -1, hoistEnd = -1;
        /** How far the hoist ended from its start point; the most the heading turned in the first second (degrees). */
        double hoistGap = Double.NaN, turned;
        /** The feet's lowest over the floor; the furthest along z; whether it crossed under the anchor (5 or more under it). */
        double lowestFeet = 99, furthestZ = -1e9;
        boolean crossedUnder;
        final StringBuilder trace = new StringBuilder();

        @Override
        public String toString() {
            return String.format("bite %.2f, 10 ticks on %.2f, straight in %d of 20, touched at %d, hoist ended %d (%.2f off), turned %.0f°, "
                    + "feet at least %.2f up, furthest z %.2f, under the anchor %b", biteLen, lenAt10, straightIn, touched, hoistEnd, hoistGap, turned,
                    lowestFeet, furthestZ, crossedUnder);
        }
    }

    /**
     * The press as the client plays it (AscentMoves.throwTo, tetherTick, swingTick): the pick's rope, the entry along the
     * look, the rope's flight (the body carried on, half forward), the bite, then {@code ticks} of the held rope with
     * forward held. Positions are local to (bx, by, bz).
     */
    private static Ride ride(SwingSpace s, AscentRules.Kin hand, float yaw, AscentSwing.Pick pick, int ticks, double bx, double by, double bz) {
        Ride r = new Ride();
        AscentSwing.Work w = new AscentSwing.Work();
        AscentRules.Kin k = new AscentRules.Kin().set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
        AscentSwing.Rope rope = new AscentSwing.Rope().throwFor(pick);
        AscentSwing.entry(k, yaw);
        double[] sc = new double[3];
        for (int t = AscentSwing.travel(pick.len); t > 0; t--) {
            double x0 = k.x, y0 = k.y, z0 = k.z;
            AscentRules.carryStep(k, yaw, 0.5, 0, sc);
            if (r.touched < 0 && AscentSwing.swept(s, x0, y0, z0, k.x, k.y, k.z, 0, 0, 0)) r.touched = 0;
        }
        AscentSwing.bite(s, k, rope, yaw);
        r.biteLen = rope.len;
        double h0 = Math.atan2(k.vx, k.vz);
        for (int i = 1; i <= ticks; i++) {
            boolean hauling = rope.hoist;
            double x0 = k.x, y0 = k.y, z0 = k.z;
            AscentSwing.tick(s, k, rope, yaw, 1, w);
            if (r.touched < 0 && AscentSwing.swept(s, x0, y0, z0, k.x, k.y, k.z, 0, 0, 0)) r.touched = i;
            if (hauling && !rope.hoist) {
                r.hoistEnd = i;
                r.hoistGap = Math.sqrt((k.x - pick.hx) * (k.x - pick.hx) + (k.y - pick.hy) * (k.y - pick.hy) + (k.z - pick.hz) * (k.z - pick.hz));
                r.biteLen = rope.len;
                h0 = Math.atan2(k.vx, k.vz);
            }
            if (i == 10) r.lenAt10 = rope.len;
            if (i <= 20 && !hauling) {
                double dx = rope.ax - k.x, dy = rope.ay - k.y, dz = rope.az - k.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz), sp = k.speed();
                if (d > 1e-6 && sp > 1e-6 && (k.vx * dx + k.vy * dy + k.vz * dz) / (d * sp) > 0.6) r.straightIn++;
                if (k.hspeed() > 0.05) r.turned = Math.max(r.turned, Math.abs(Math.toDegrees(Math.IEEEremainder(Math.atan2(k.vx, k.vz) - h0, 2 * Math.PI))));
            }
            r.lowestFeet = Math.min(r.lowestFeet, k.y - AscentRules.HAND_HEIGHT - by);
            r.furthestZ = Math.max(r.furthestZ, k.z - bz);
            if (!hauling && (z0 - rope.az) * (k.z - rope.az) <= 0 && Math.abs(k.x - rope.ax) < 1.5 && k.y < rope.ay - 5) r.crossedUnder = true;
            if (i % 3 == 0 || i <= 3) r.trace.append(String.format(" | %d: %.1f %.1f %.1f r%.1f%s", i, k.x - bx, k.y - AscentRules.HAND_HEIGHT - by, k.z - bz,
                    rope.len, rope.hoist ? " haul" : ""));
        }
        return r;
    }

    private static String pickOf(AscentSwing.Pick p, AscentSwing.Work w, double bx, double by, double bz) {
        return String.format("%s at (%.2f %.2f %.2f), rope %.2f, zip %b, sling %b, hoist %b, room %.0f°; look met %.1f off; sling: %s",
                p.move >= 0 && p.move < RealRope.MOVE.length ? RealRope.MOVE[p.move] : "?", p.x - bx, p.y - by, p.z - bz, p.len, p.zip, p.sling,
                p.hoist, p.cap, w.aimT, AscentSwing.SLING_NO[w.aimSling]);
    }

    /**
     * Every point the press tries round what the look meets, and what its played swing did (for a failure's message):
     * whether it bites and is in clear view, the move, or why not, where it met something, how far it turned.
     */
    private static String report(SwingSpace s, AscentRules.Kin hand, double ex, double ey, double ez, float yaw, float pitch, double bx, double by, double bz) {
        AscentSwing.Work w = new AscentSwing.Work();
        SwingSpace.Hit a = w.aim;
        double cp = Math.cos(Math.toRadians(pitch));
        if (!s.ray(ex, ey, ez, AscentSwing.fx(yaw) * cp, -Math.sin(Math.toRadians(pitch)), AscentSwing.fz(yaw) * cp, AscentRules.TETHER_RANGE, a)) {
            return " || the look met nothing";
        }
        StringBuilder b = new StringBuilder(String.format(" || met (%.2f %.2f %.2f) facing ", a.x - bx, a.y - by, a.z - bz)).append(a.nx).append(' ')
                .append(a.ny).append(' ').append(a.nz);
        int n = RealRope.points(s, a, yaw, w);
        double[] p = w.rpts.clone();
        AscentSwing.Pick c = new AscentSwing.Pick();
        for (int i = 0; i < n; i++) {
            c.x = p[i * 3];
            c.y = p[i * 3 + 1];
            c.z = p[i * 3 + 2];
            b.append(String.format(" | (%.2f %.2f %.2f)", c.x - bx, c.y - by, c.z - bz));
            if (!AscentSwing.bites(s, c)) {
                b.append(" no bite");
                continue;
            }
            if (!AscentSwing.clear(s, hand.x, hand.y, hand.z, c.x, c.y, c.z, 0.5, w)) b.append(" not in view");
            int kind = RealRope.play(s, hand, yaw, c.x, c.y, c.z, w);
            b.append(kind > 0 ? " " + RealRope.MOVE[kind] : " no: " + RealRope.NO[w.playNo]);
            b.append(String.format(" (rope %.1f, turned %.0f, skim %d", w.playBite, w.playTurn, w.playSkim));
            if (w.playHitAt >= 0 || w.playNo == RealRope.NO_FLIGHT) {
                b.append(String.format(", met at tick %d at (%.2f %.2f %.2f)", w.playHitAt, w.playHx - bx, w.playHy - AscentRules.HAND_HEIGHT - by, w.playHz - bz));
            }
            b.append(')');
        }
        return b.toString();
    }

    // ---- the tests -------------------------------------------------------------------------------------------------------

    /**
     * The rope stays real: over the tall tree and beside the oak, 10 ticks after the bite the rope is at least 85% as long
     * as it bit (it only eases in while you fall), and under a tenth of the first second goes straight at the anchor
     * (the pull the old rope gave 9 presses in 10).
     */
    private static void stays(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = ground(h);
        try {
            tallTree(level, o);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 6.5, by + 13 + AscentRules.HAND_HEIGHT, bz + 10, 0, -0.1, 0.8);
            double ex = hand.x, ey = by + 13 + 1.62, ez = hand.z;
            float yaw = 0, pitch = pitchTo(ex, ey, ez, bx + 6.5, by + 14, bz + 23);
            h.assertTrue(AscentSwing.aimed(space, hand, ex, ey, ez, yaw, pitch, AscentRules.TETHER_RANGE, true, w, pick) && !pick.zip && !pick.sling,
                    "over the tall tree, the press gives a real swing: " + pickOf(pick, w, bx, by, bz));
            Ride r = ride(space, hand, yaw, pick, 20, bx, by, bz);
            h.assertTrue(r.lenAt10 >= 0.85 * r.biteLen - 1e-6 && r.straightIn < 2,
                    "over the tall tree, the rope keeps its length and never pulls: " + r + r.trace);
            // the oak beside you
            clear(level, o);
            ground(h);
            oak(level, o, 8, 20);
            AscentRules.Kin side = new AscentRules.Kin().set(bx + 12, by + 1 + AscentRules.HAND_HEIGHT, bz + 18.5, 0, 0.1, 1.0);
            double sy = by + 1 + 1.62;
            float sYaw = yawTo(side.x, side.z, bx + 8.5, bz + 20.5), sPitch = pitchTo(side.x, sy, side.z, bx + 8.5, by + 6.5, bz + 20.5);
            h.assertTrue(AscentSwing.aimed(space, side, side.x, sy, side.z, sYaw, sPitch, AscentRules.TETHER_RANGE, true, w, pick) && !pick.zip && !pick.sling,
                    "beside the oak, the press gives a real swing: " + pickOf(pick, w, bx, by, bz) + report(space, side, side.x, sy, side.z, sYaw, sPitch, bx, by, bz));
            Ride s = ride(space, side, sYaw, pick, 20, bx, by, bz);
            h.assertTrue(s.lenAt10 >= 0.85 * s.biteLen - 1e-6 && s.straightIn < 2, "beside the oak, the rope keeps its length and never pulls: " + s + s.trace);
        } finally {
            clear(level, o);
        }
        h.succeed();
    }

    /**
     * Round (Euan's drawing, 12 Nov 2026: "when it can't [swing you under], then it does something more like this"): at a
     * run of 1 block a tick past a vanilla oak 3.5 to the side and 2 ahead, the press looking at its crown swings you round
     * it: the heading turns 60 degrees or more within a second, the feet never under 0.9 over the floor, nothing touched.
     */
    private static void round(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = ground(h);
        try {
            oak(level, o, 8, 20);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 12, by + 1 + AscentRules.HAND_HEIGHT, bz + 18.5, 0, 0.1, 1.0);
            double ey = by + 1 + 1.62;
            float yaw = yawTo(hand.x, hand.z, bx + 8.5, bz + 20.5), pitch = pitchTo(hand.x, ey, hand.z, bx + 8.5, by + 6.5, bz + 20.5);
            h.assertTrue(AscentSwing.aimed(space, hand, hand.x, ey, hand.z, yaw, pitch, AscentRules.TETHER_RANGE, true, w, pick) && pick.move == RealRope.ROUND
                    && !pick.zip && !pick.sling, "beside an oak, the press swings round it: " + pickOf(pick, w, bx, by, bz)
                    + report(space, hand, hand.x, ey, hand.z, yaw, pitch, bx, by, bz));
            h.assertTrue(AscentServer.anchorReal(mock(h, bx + 12, by + 1, bz + 18.5), pick.x, pick.y, pick.z), "the server takes the oak's anchor as real");
            Ride r = ride(space, hand, yaw, pick, 20, bx, by, bz);
            h.assertTrue(r.turned >= 60 && r.lowestFeet >= 0.9 && r.touched < 0, "round the oak: turned 60° or more in a second, clear of everything: " + r + r.trace);
        } finally {
            clear(level, o);
        }
        h.succeed();
    }

    /**
     * The hoist: from a sprint jump 16.5 before the tall tree, its crown 9 up (no swing from down here: a rope that long
     * would drag you through the floor), the press hauls you up the crown's near side to where a swing under it starts,
     * in under a second, then swings you under the crown and on 3 or more past its anchor; nothing touched.
     */
    private static void hoist(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = ground(h);
        try {
            tallTree(level, o);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 6.5, by + 1 + AscentRules.HAND_HEIGHT, bz + 4.5, 0, 0.3, 0.36);
            double ey = by + 1 + 1.62;
            float yaw = 0, pitch = pitchTo(hand.x, ey, hand.z, bx + 6.5, by + 11.5, bz + 21);
            h.assertTrue(AscentSwing.aimed(space, hand, hand.x, ey, hand.z, yaw, pitch, AscentRules.TETHER_RANGE, true, w, pick) && pick.hoist
                    && pick.move == RealRope.HOIST, "from a sprint jump at the tall tree, the press hoists: " + pickOf(pick, w, bx, by, bz));
            h.assertTrue(AscentServer.anchorReal(mock(h, bx + 6.5, by + 1, bz + 4.5), pick.x, pick.y, pick.z), "the server takes the crown's anchor as real");
            Ride r = ride(space, hand, yaw, pick, 60, bx, by, bz);
            h.assertTrue(r.hoistEnd > 0 && r.hoistEnd <= 20 && r.hoistGap <= RealRope.HOIST_NEAR + 0.1,
                    "the hoist ends in under a second at the swing's start: " + r + r.trace);
            h.assertTrue(r.touched < 0 && r.furthestZ >= pick.z - bz + 3, "the hoisted swing goes under the crown and on, clear of everything: " + r + r.trace);
        } finally {
            clear(level, o);
        }
        h.succeed();
    }

    /**
     * From over the tree (13 up and 13 before its crown, running at it): the press looking at the crown's top swings you
     * under it (the old rope's only answer up here was a sling over), out past its far side, nothing touched.
     */
    private static void fromTop(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = ground(h);
        try {
            tallTree(level, o);
            LevelSpace space = new LevelSpace().at(level, null);
            double bx = o.getX(), by = o.getY() + 1, bz = o.getZ();
            AscentSwing.Work w = new AscentSwing.Work();
            AscentSwing.Pick pick = new AscentSwing.Pick();
            AscentRules.Kin hand = new AscentRules.Kin().set(bx + 6.5, by + 13 + AscentRules.HAND_HEIGHT, bz + 10, 0, -0.1, 0.8);
            double ey = by + 13 + 1.62;
            float yaw = 0, pitch = pitchTo(hand.x, ey, hand.z, bx + 6.5, by + 14, bz + 23);
            h.assertTrue(AscentSwing.aimed(space, hand, hand.x, ey, hand.z, yaw, pitch, AscentRules.TETHER_RANGE, true, w, pick) && pick.move == RealRope.UNDER
                    && !pick.zip && !pick.sling && !pick.hoist, "from over the tall tree, the press swings under its crown: " + pickOf(pick, w, bx, by, bz));
            h.assertTrue(AscentServer.anchorReal(mock(h, bx + 6.5, by + 13, bz + 10), pick.x, pick.y, pick.z), "the server takes the crown's anchor as real");
            Ride r = ride(space, hand, yaw, pick, 45, bx, by, bz);
            h.assertTrue(r.touched < 0 && r.crossedUnder && r.furthestZ > 28, "under the crown and out past its far side, clear of everything: " + r + r.trace);
        } finally {
            clear(level, o);
        }
        h.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer mock(GameTestHelper h, double x, double y, double z) {
        net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.snapTo(x, y, z, 0, 0);
        return p;
    }
}
