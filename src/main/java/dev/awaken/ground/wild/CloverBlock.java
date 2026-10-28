package dev.awaken.ground.wild;

import net.minecraft.world.level.block.FlowerBedBlock;

/**
 * Clover in the lawn (SPEC §41.2): trefoil leaves with white and pink heads, one to four patches a block like the game's pink
 * petals (a flower bed, so it nods in the wind as they do). Its leaves take the grass colour; the heads keep their own.
 */
public class CloverBlock extends FlowerBedBlock {
    public CloverBlock(Properties properties) {
        super(properties, 2);
    }
}
