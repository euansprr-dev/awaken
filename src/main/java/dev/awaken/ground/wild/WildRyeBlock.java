package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Wild rye's upper two blocks (SPEC §41.2): a two-block plant standing on its own stems ({@link RyeStemsBlock}). */
public class WildRyeBlock extends WildDoubleBlock {
    public WildRyeBlock(Properties properties) {
        super(WildKind.RYE, properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(WildBlocks.RYE_STEMS.get());
    }
}
