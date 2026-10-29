package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Wild rye's foot (SPEC §41.2): the bottom of a three-block plant, stiff close stems under the two-block {@link WildRyeBlock}.
 * Not a sway class, so it stands still and the rye bends from its middle: the middle's base does not move either, so the
 * three blocks never shear apart. The rye's item places it, and the rye above with it; without its rye it goes.
 */
public class RyeStemsBlock extends VegetationBlock {
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

    public RyeStemsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return pos.getY() < level.getMaxY() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)
                && level.getBlockState(pos.above(2)).canBeReplaced(context) ? super.getStateForPlacement(context) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        BlockState rye = WildDoubleBlock.grown(WildBlocks.WILD_RYE.get(), level, pos);
        level.setBlock(pos.above(), rye.setValue(WildDoubleBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlockAndUpdate(pos.above(2), rye.setValue(WildDoubleBlock.HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (dir == Direction.UP && !(neighbour.is(WildBlocks.WILD_RYE.get()) && neighbour.getValue(WildDoubleBlock.HALF) == DoubleBlockHalf.LOWER)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbour, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean isPrecise) {
        WildLife.pushThrough(state, level, pos, entity);
    }
}
