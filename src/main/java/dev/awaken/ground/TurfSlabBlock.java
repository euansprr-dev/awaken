package dev.awaken.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.function.Supplier;

/**
 * The grass slab (SPEC §31, P1). Its {@link #TUFT} says what stands in the slab's own block, drawn by its model from the
 * slab's top up: 0 nothing, 1 short grass, 2 tall grass, 3 a fern, 4 a flower, 5 a bush. The world generator picks it, at
 * the density the plants round it have, so a slope's foot columns are not bald. Like grass, it dies to dirt under something
 * solid (a random tick), but does not spread.
 */
public class TurfSlabBlock extends SoftSlabBlock {
    public static final IntegerProperty TUFT = IntegerProperty.create("tuft", 0, 5);

    private final Supplier<? extends SoftSlabBlock> dirt;

    public TurfSlabBlock(Properties properties, Supplier<? extends SoftSlabBlock> dirt) {
        super(properties);
        this.dirt = dirt;
        registerDefaultState(defaultBlockState().setValue(TUFT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TUFT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // a bottom slab's own top is open; a block over it (or over a top slab) smothers it
        if (!level.getBlockState(pos.above()).isSolidRender()) return;
        BlockState to = this.dirt.get().defaultBlockState().setValue(TYPE, state.getValue(TYPE)).setValue(WATERLOGGED, state.getValue(WATERLOGGED));
        level.setBlockAndUpdate(pos, to);
    }
}
