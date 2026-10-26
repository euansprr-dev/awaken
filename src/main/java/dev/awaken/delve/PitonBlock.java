package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A Runner's piton (PLAN 7.5): an iron spike hammered into a wall (its facing points out of the wall, as a ladder's
 * does). Use a coil of Runner's rope on it and the rope pays out straight down from under it, up to {@link #ROPE}
 * blocks or to the ground, climbable with the pipe grip; take the rope or the piton and the coil comes back.
 */
public final class PitonBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ROPED = BooleanProperty.create("roped");
    public static final int ROPE = 32;
    private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Block.box(6, 6, 7, 10, 11, 16));

    public PitonBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROPED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ROPED);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.get(s.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        Direction out = s.getValue(FACING);
        BlockPos wall = pos.relative(out.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, out);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (!face.getAxis().isHorizontal()) {
            // clicked a top or bottom: take the wall the player faces
            for (Direction d : ctx.getNearestLookingDirections()) {
                if (!d.getAxis().isHorizontal()) continue;
                BlockState s = defaultBlockState().setValue(FACING, d.getOpposite());
                if (s.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return s;
            }
            return null;
        }
        BlockState s = defaultBlockState().setValue(FACING, face);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState s, @Nullable LivingEntity by, ItemStack stack) {
        if (!level.isClientSide()) level.playSound(null, pos, DelveContent.PITON_SET.get(), SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    @Override
    protected BlockState updateShape(BlockState s, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos np,
                                     BlockState ns, RandomSource random) {
        if (dir.getOpposite() == s.getValue(FACING) && !s.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, level, ticks, pos, dir, np, ns, random);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DelveContent.RUNNER_ROPE.get()) || s.getValue(ROPED)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        int n = payOut(sl, pos);
        if (n == 0) return InteractionResult.FAIL;
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Hangs the rope from under the piton at {@code pos}: how many blocks it reached (0 when there was no drop below it). */
    public static int payOut(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof PitonBlock) || s.getValue(ROPED)) return 0;
        BlockPos.MutableBlockPos m = pos.mutable();
        int n = 0;
        while (n < ROPE) {
            m.move(Direction.DOWN);
            if (!level.getBlockState(m).isAir() || m.getY() < level.getMinY()) break;
            n++;
        }
        if (n == 0) return 0;
        level.setBlock(pos, s.setValue(ROPED, true), Block.UPDATE_ALL);
        BlockState line = DelveContent.RUNNER_LINE.get().defaultBlockState();
        m.set(pos);
        for (int i = 0; i < n; i++) {
            m.move(Direction.DOWN);
            level.setBlock(m, line, Block.UPDATE_CLIENTS);
        }
        level.playSound(null, pos, DelveContent.ROPE_PAY.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return n;
    }

    /** Takes the rope back up into its coil (the piton stays): true if there was one. */
    static boolean reelIn(Level level, BlockPos piton) {
        BlockState s = level.getBlockState(piton);
        if (!(s.getBlock() instanceof PitonBlock) || !s.getValue(ROPED)) return false;
        level.setBlock(piton, s.setValue(ROPED, false), Block.UPDATE_ALL);
        BlockPos.MutableBlockPos m = piton.mutable();
        for (int i = 0; i < ROPE + 1; i++) {
            m.move(Direction.DOWN);
            if (!level.getBlockState(m).is(DelveContent.RUNNER_LINE.get())) break;
            level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        // the loot table gives the coil back with a roped piton; the rope below goes with it
        if (!level.isClientSide() && s.getValue(ROPED)) {
            BlockPos.MutableBlockPos m = pos.mutable();
            for (int i = 0; i < ROPE + 1; i++) {
                m.move(Direction.DOWN);
                if (!level.getBlockState(m).is(DelveContent.RUNNER_LINE.get())) break;
                level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return super.playerWillDestroy(level, pos, s, player);
    }
}
