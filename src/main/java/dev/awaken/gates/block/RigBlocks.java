package dev.awaken.gates.block;

import dev.awaken.gates.run.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gate Rig's blocks (SPEC §17 "Gate Rig"): the Mirror Hall's turning mirror, its lens and its seal. They only
 * route use to {@link Interactions}; what they do is the rig director's.
 */
public final class RigBlocks {
    private RigBlocks() {}

    /**
     * A silvered pane on a frame, standing at a diagonal (TURN 0..3: its face to the NE, SE, SW, NW). Using it turns it a
     * quarter clockwise. While it turns (TURNING) the block isn't drawn: the client draws it swinging round instead.
     */
    public static final class Mirror extends Block {
        public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 3);
        public static final BooleanProperty TURNING = BooleanProperty.create("turning");
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

        public Mirror(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(TURN, 0).setValue(TURNING, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(TURN, TURNING);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPE;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return state.getValue(TURNING) ? RenderShape.INVISIBLE : RenderShape.MODEL;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.turnMirror(sp, pos);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return false;
        }
    }

    /** The lens the hall's light comes out of (FACING: the way it shines). */
    public static final class Lens extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

        public Lens(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }
    }

    /** The seal beside the barred door: dark until the light reaches it (FACING: toward the hall). */
    public static final class Seal extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public Seal(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, LIT);
        }
    }
}
