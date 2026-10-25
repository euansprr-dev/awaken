package dev.awaken.living.wayfarer;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/** The scene finder's GameTest (SPEC §21.5): a cliff ledge and flat road ground in real terrain, never on forbidden land. */
public final class SceneTests {
    private SceneTests() {}

    public static void register() {
        AwakenTests.registerIsolated("living_scene_finder", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos base = h.absolutePos(new BlockPos(0, 1, 0));
            // a 7x7 stone tower ten high on open ground, and open ground 24 blocks off for the road
            AwakenTests.clearAbove(level, base, 30, 0, 30);
            for (int dx = -12; dx <= 36; dx++) for (int dz = -12; dz <= 12; dz++) level.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) for (int dy = 0; dy < 10; dy++) {
                level.setBlock(base.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
            }
            BlockPos centre = base.offset(3, 0, 3);
            Scenes.Forbidden was = Scenes.forbidden;
            try {
                Scenes.forbidden = (l, pos) -> false;
                BlockPos cliff = Scenes.findSpot(level, centre, "cliff", RandomSource.create(1L), 0, 4);
                h.assertTrue(cliff != null, "a cliff spot on the tower");
                h.assertTrue(cliff.getY() == base.getY() + 10, "on its top: " + cliff.getY() + " vs " + (base.getY() + 10));
                h.assertTrue(Scenes.drop(level, cliff.getX(), cliff.getY(), cliff.getZ()) >= 6, "with a drop beside it");
                BlockPos foot = Scenes.foot(level, cliff);
                h.assertTrue(foot.getY() <= cliff.getY() - 6, "its foot is at the bottom: " + foot.getY());
                BlockPos road = Scenes.findSpot(level, base.offset(24, 0, 0), "road", RandomSource.create(2L), 0, 6);
                h.assertTrue(road != null && road.getY() == base.getY(), "flat open ground for a road scene");
                h.assertTrue(Scenes.findSpot(level, base.offset(24, 0, 0), "cliff", RandomSource.create(3L), 0, 6) == null, "no cliff on flat ground");
                // forbidden land (a Hearth's, the city's) is never used
                Scenes.forbidden = (l, pos) -> true;
                h.assertTrue(Scenes.findSpot(level, centre, "cliff", RandomSource.create(1L), 0, 4) == null, "never on a Hearth's land or the city's");
                h.assertTrue(Scenes.findSpot(level, base.offset(24, 0, 0), "road", RandomSource.create(2L), 0, 6) == null, "nor a road there");
            } finally {
                Scenes.forbidden = was;
            }
            h.succeed();
        });
    }
}
