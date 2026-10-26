package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Runner's rope hanging from a piton: a free rope (no facing), held from the side the climber is on
 * ({@code body.GripRules}), nothing to bump into. It hangs only from a roped piton or more rope above; cut it anywhere
 * and the whole rope comes back as its coil.
 */
public final class RunnerLineBlock extends Block {
    private static final VoxelShape SHAPE = Block.column(3, 0, 16);

    public RunnerLineBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState s) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.getBlock() instanceof PitonBlock && above.getValue(PitonBlock.ROPED);
    }

    @Override
    protected BlockState updateShape(BlockState s, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np,
                                     BlockState ns, RandomSource random) {
        if (dir == Direction.UP && !s.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, level, ticks, pos, dir, np, ns, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        if (!level.isClientSide()) {
            BlockPos.MutableBlockPos m = pos.mutable();
            for (int i = 0; i < PitonBlock.ROPE + 1 && level.getBlockState(m).is(this); i++) m.move(Direction.UP);
            if (PitonBlock.reelIn(level, m.immutable()) && !player.hasInfiniteMaterials()) {
                ItemStack coil = new ItemStack(DelveContent.RUNNER_ROPE.get());
                if (!player.getInventory().add(coil)) level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, coil));
            }
        }
        return super.playerWillDestroy(level, pos, s, player);
    }
}
