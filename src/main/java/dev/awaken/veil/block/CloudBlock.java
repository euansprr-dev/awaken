package dev.awaken.veil.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The sea of clouds under the Stillpoint. Soft: you can walk on it, and falling onto it never hurts. */
public class CloudBlock extends Block {
    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.resetFallDistance();
    }
}
