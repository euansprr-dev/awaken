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
        dev.awaken.core.test.AwakenTests.register("ascent_swing_bench", 20, AscentSwingTests::bench);
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_swing_level", 40, AscentSwingTests::level);
        dev.awaken.core.test.AwakenTests.registerIsolated("ascent_sling_level", 40, AscentSwingTests::sling);
    }

    /**
     * The rope as SPEC §62 has it, played by a bot that only holds the key (how a player plays it): never a hard hit on a
     * rope, never a stopped run, at least as far as the old rope everywhere, the tall places far past what a held rope
     * did before (lane 120, city 166, forest 124), the real-sized woods, hills and roofs (where it never fired) well past
     * a run's 103 with no hard hit at all, and few hard hits flying free among the giants. Then the switch: with the guide
     * off, the assisted swing's own gates as §17.31 set them (two places are enough to hold the path).
     */
    private static void bench(GameTestHelper h) {
        AscentSwing.Tune was = AscentSwing.T;
        boolean guide = AscentSwing.guide;
        AscentSwing.T = AscentSwing.Tune.JAR;
        try {
            AscentSwing.guide = true;
            int free = 0;
            StringBuilder rows = new StringBuilder();
            for (String place : SwingBench.PLACES) {
                SwingBench.Row today = SwingBench.bench(place, false), held = SwingBench.bench(place, true, true);
                rows.append('\n').append(today).append("\nheld   ").append(held);
                h.assertTrue(held.hardOnRope() == 0, place + ": hard hits on the rope " + held);
                h.assertTrue(held.stuckRuns() == 0, place + ": stopped runs " + held);
                h.assertTrue(held.medianDistance() >= today.medianDistance() * 0.98, place + ": shorter than the old rope " + held + " vs " + today);
                free += held.hardFree();
            }
            h.assertTrue(free <= 6, "hard hits flying free between ropes: " + free);
            String[] tall = {"lane", "city", "forest"};
            double[] tallAtLeast = {240, 245, 240};
            for (int i = 0; i < tall.length; i++) {
                SwingBench.Row held = SwingBench.bench(tall[i], true, true);
                h.assertTrue(held.medianDistance() >= tallAtLeast[i], tall[i] + ": a held rope goes " + held + ", wanted " + tallAtLeast[i]);
            }
            double[] woodsAtLeast = {165, 150, 120, 170};
            for (int i = 0; i < SwingBench.WOODS.length; i++) {
                String place = SwingBench.WOODS[i];
                SwingBench.Row held = SwingBench.bench(place, true, true);
                rows.append("\nheld   ").append(held);
                h.assertTrue(held.hardOnRope() == 0 && held.hardFree() == 0, place + ": hard hits " + held);
                h.assertTrue(held.stuckRuns() == 0, place + ": stopped runs " + held);
                h.assertTrue(held.ropesPerRun() >= 3, place + ": the rope hardly fires " + held);
                h.assertTrue(held.medianDistance() >= woodsAtLeast[i], place + ": a held rope goes " + held + ", wanted " + woodsAtLeast[i]);
            }
            AscentSwing.guide = false;
            for (String place : new String[] {"lane", "city"}) {
                SwingBench.Row today = SwingBench.bench(place, false), assist = SwingBench.bench(place, true);
                rows.append("\nclassic ").append(assist);
                h.assertTrue(assist.hardOnRope() == 0 && assist.stuckRuns() == 0, place + ", guide off: " + assist);
                h.assertTrue(assist.medianDistance() >= today.medianDistance() * 0.98, place + ", guide off: shorter than the old rope " + assist);
            }
            SwingBench.Row oak = SwingBench.bench("oak", true, true);
            h.assertTrue(oak.ropesPerRun() == 0, "guide off: the old rule gives a real wood no rope, as it did: " + oak);
            dev.awaken.Awaken.LOG.info("[ascent] swing bench:{}", rows);
        } finally {
            AscentSwing.T = was;
            AscentSwing.guide = guide;
        }
        h.succeed();
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
}
