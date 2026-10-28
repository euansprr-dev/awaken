package dev.awaken.delve;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The Delver lamps (SPEC §34.6): struck, a lamp's light goes only where its wave reached (a pocket behind rock stays
 * dark), never more than 120 lights, and they leave with the lamp; in the lit hall no cell is dark enough for a monster.
 */
final class LampTests {
    private LampTests() {}

    static void register() {
        AwakenTests.registerIsolated("delve_lamp_lattice", 400, LampTests::lattice);
    }

    /** A hall 17 x 6 x 17 in stone, a dead lamp hung from its roof; a sealed pocket beside it. */
    private static void lattice(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 24, 0));
        for (int dx = -10; dx <= 14; dx++) for (int dz = -10; dz <= 10; dz++) for (int dy = -1; dy <= 7; dy++) {
            boolean hall = Math.abs(dx) <= 8 && Math.abs(dz) <= 8 && dy >= 0 && dy <= 5;
            boolean pocket = dx >= 10 && dx <= 13 && Math.abs(dz) <= 2 && dy >= 0 && dy <= 2;
            level.setBlock(o.offset(dx, dy, dz), hall || pocket ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos lamp = o.offset(0, 5, 0);
        level.setBlock(lamp, DelveContent.DELVER_LAMP.get().defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(Lamps.strike(level, null, lamp), "a dead lamp lights");
        h.assertFalse(Lamps.strike(level, null, lamp), "one lamp, one lattice");
        h.succeedWhen(() -> {
            h.assertFalse(Lamps.busy(level, lamp), "the wave is still going");
            DelveData.Lamp l = DelveData.get(level).lamp(lamp.asLong());
            h.assertTrue(l != null && !l.lights().isEmpty(), "the lamp keeps its lights");
            h.assertTrue(l.lights().size() <= Lamps.MAX_LIGHTS, "at most 120: " + l.lights().size());
            for (long c : l.lights()) {
                BlockPos b = BlockPos.of(c);
                int dx = b.getX() - o.getX(), dy = b.getY() - o.getY(), dz = b.getZ() - o.getZ();
                h.assertTrue(Math.abs(dx) <= 8 && Math.abs(dz) <= 8 && dy >= 0 && dy <= 5, "a light outside the reached air: " + b.toShortString());
                h.assertTrue(level.getBlockState(b).is(Blocks.LIGHT), "a light block at " + b.toShortString());
            }
            // no monster spawns in a lit hall: every cell of it has block light (overworld monsters need none)
            AwakenTests.awaitLight(h, o.offset(-9, -1, -9), o.offset(14, 7, 9));
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int dy = 0; dy <= 5; dy++) {
                BlockPos b = o.offset(dx, dy, dz);
                h.assertTrue(level.getBrightness(LightLayer.BLOCK, b) > 0, "a dark cell in the lit hall: " + b.toShortString());
            }
            for (int dx = 10; dx <= 13; dx++) h.assertTrue(!level.getBlockState(o.offset(dx, 1, 0)).is(Blocks.LIGHT), "the sealed pocket gets no light");
            // broken, the lamp takes its lights back
            level.destroyBlock(lamp, false);
            for (long c : l.lights()) h.assertTrue(!level.getBlockState(BlockPos.of(c)).is(Blocks.LIGHT), "a light left behind at " + BlockPos.of(c).toShortString());
            h.assertTrue(DelveData.get(level).lamp(lamp.asLong()) == null, "the world forgets the lamp");
        });
    }
}
