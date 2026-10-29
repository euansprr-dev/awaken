package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A one-block wild plant (SPEC §41.2): thistle, heather, gorse, frost grass, dry tussock. A {@link BushBlock}, so it sways
 * as the game's grass does; no bone meal. A dry tussock grows on sand and terracotta as the dead bush does.
 */
public class WildBushBlock extends BushBlock {
    private final WildKind kind;
    private final VoxelShape shape;

    public WildBushBlock(WildKind kind, int height, Properties properties) {
        super(properties, height);
        this.kind = kind;
        this.shape = Block.column(14.0, 0.0, height);
    }

    public WildKind kind() {
        return kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        if (kind == WildKind.TUSSOCK) return state.is(BlockTags.SUPPORTS_DRY_VEGETATION);
        return super.mayPlaceOn(state, level, pos);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return false;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        WildLife.animate(state, level, pos, random);
    }
}
