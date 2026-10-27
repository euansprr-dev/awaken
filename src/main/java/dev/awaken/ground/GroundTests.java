package dev.awaken.ground;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/** The ground's seam rules (SPEC §31): the masks the client draws from, and the drift's range. */
final class GroundTests {
    private GroundTests() {}

    static void register() {
        // a one-block riser wraps; a two-block riser (a cliff) does not; a side against a solid block is hidden and stays 0;
        // a side against a plant counts as open
        AwakenTests.register("ground_wrap_mask", 20, h -> {
            // low ground one block up (y 1) with dirt under it, 12 wide
            for (int x = 0; x < 12; x++) for (int z = 0; z < 6; z++) {
                h.setBlock(x, 0, z, Blocks.DIRT);
                h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            }
            // a step: one grass block on the low ground, its four sides open over ground one lower
            h.setBlock(2, 2, 2, Blocks.GRASS_BLOCK);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0b1111, "a one-block step wraps on every side");
            // a plant beside it counts as open
            h.setBlock(3, 2, 2, Blocks.SHORT_GRASS);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0b1111, "a plant beside it is open");
            // solid beside: that face is hidden, no bit
            h.setBlock(3, 2, 2, Blocks.STONE);
            int m = SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)));
            h.assertTrue(m == 0b1101, "the side against stone (east) stays 0, the rest wrap: " + Integer.toBinaryString(m));
            // a two-block riser: a pillar of two grass blocks, ground two below it
            h.setBlock(8, 2, 3, Blocks.DIRT);
            h.setBlock(8, 3, 3, Blocks.GRASS_BLOCK);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(8, 3, 3))) == 0, "a two-block riser keeps its dirt face");
            // a grass block with something solid on it is not a lawn: nothing wraps
            h.setBlock(2, 3, 2, Blocks.STONE);
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2))) == 0, "a covered block keeps its sides");
            h.setBlock(2, 3, 2, Blocks.AIR);
            // a flat lawn has nothing to wrap
            h.assertTrue(SeamlessRules.wrapMask(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5))) == 0, "a flat lawn: the tops are open but the ground beside is level, no riser");
            h.succeed();
        });

        // grass creeps over the edges of a path or dirt that meet grass at the same height, with the corner only where just
        // the diagonal is grass
        AwakenTests.register("ground_creep_mask", 20, h -> {
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) {
                h.setBlock(x, 0, z, Blocks.DIRT);
                h.setBlock(x, 1, z, Blocks.GRASS_BLOCK);
            }
            // a path at (3,1,3) with grass to the east (4,1,3) and south (3,1,4): edges E and S
            h.setBlock(3, 1, 3, Blocks.DIRT_PATH);
            h.setBlock(2, 1, 3, Blocks.DIRT);
            h.setBlock(3, 1, 2, Blocks.DIRT);
            h.setBlock(2, 1, 2, Blocks.DIRT);
            int m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)));
            h.assertTrue(m == 0b0110, "east and south edges, no corner between them: " + Integer.toBinaryString(m));
            // a dirt block whose only grass is on the diagonal (south-east): a corner
            for (int x = 4; x <= 7; x++) for (int z = 4; z <= 7; z++) h.setBlock(x, 1, z, Blocks.COARSE_DIRT);
            h.setBlock(7, 1, 7, Blocks.GRASS_BLOCK);
            m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(6, 1, 6)));
            h.assertTrue(m == 1 << (4 + 1), "south-east corner only: " + Integer.toBinaryString(m));
            // a block under a roof shows no top, so nothing creeps
            h.setBlock(3, 2, 3, Blocks.STONE);
            h.assertTrue(SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3))) == 0, "a covered top has no creep");
            // snow on the grass: no creep from it
            h.setBlock(3, 2, 3, Blocks.AIR);
            h.setBlock(4, 1, 3, Blocks.GRASS_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.SnowyBlock.SNOWY, true));
            m = SeamlessRules.creepMask(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)));
            // the east side is no grass edge now, so the grass past it (north-east) gets a corner instead
            h.assertTrue(m == (0b0100 | 1 << 4), "a snowy grass block is not a grass edge: " + Integer.toBinaryString(m));
            h.succeed();
        });

        // the lawn drift is small, centred, smooth and the same every time
        AwakenTests.register("ground_drift_range", 20, h -> {
            double sum = 0;
            float lo = 9, hi = -9, worstStep = 0;
            int n = 0;
            for (int x = -300; x < 300; x += 3) for (int z = -300; z < 300; z += 3) {
                float t = LawnDrift.noise(x, z);
                sum += t;
                n++;
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
                worstStep = Math.max(worstStep, Math.abs(t - LawnDrift.noise(x + 1, z)));
                h.assertTrue(t == LawnDrift.noise(x, z), "deterministic");
            }
            h.assertTrue(lo >= -1f && hi <= 1f, "within -1..1");
            h.assertTrue(Math.abs(sum / n) < 0.2, "centred: " + sum / n);
            h.assertTrue(hi - lo > 1.0f, "it does vary: " + (hi - lo));
            h.assertTrue(worstStep < 0.3f, "smooth block to block: " + worstStep);
            // the colour moves a few per cent at the ends, never out of range, and keeps its alpha
            int grass = 0xFF79C05A;
            for (int x = -300; x < 300; x += 7) {
                int c = LawnDrift.apply(grass, x, x * 3);
                h.assertTrue((c >>> 24) == 0xFF, "alpha kept");
                int dr = Math.abs((c >> 16 & 255) - 0x79), dg = Math.abs((c >> 8 & 255) - 0xC0), db = Math.abs((c & 255) - 0x5A);
                h.assertTrue(dr <= 14 && dg <= 12 && db <= 9, "a small move: " + dr + " " + dg + " " + db);
            }
            h.succeed();
        });
    }
}
