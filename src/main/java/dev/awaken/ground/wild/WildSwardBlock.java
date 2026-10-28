package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wild sward (SPEC §41.2): the deep wild's ground, grass with last year's thatch showing. A grass block in every way but its
 * top; it never spreads itself (the land's grass spreads as the game's own grass), and covered it dies to dirt as grass does.
 */
public class WildSwardBlock extends GrassBlock {
    public WildSwardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // the game's grass block's own tick, as if this were one: it spreads plain grass, or dies to dirt
        Blocks.GRASS_BLOCK.defaultBlockState().setValue(SNOWY, state.getValue(SNOWY)).randomTick(level, pos, random);
    }
}
