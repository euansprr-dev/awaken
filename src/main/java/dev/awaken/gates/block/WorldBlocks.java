package dev.awaken.gates.block;

import dev.awaken.gates.worlds.WorldRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gate world blocks with a behaviour of their own (SPEC §38.6): candle clusters, coloured glass, things that hang or
 * face a wall, rope you can climb, candles that float on water and bronze you can strike. Textures and models come from
 * tools/art/specs/gate_worlds_*.py.
 */
public final class WorldBlocks {
    private WorldBlocks() {}

    /** One to five candles standing together, lit or out (each lit candle gives 3 light). */
    public static final class Candles extends Block {
        public static final IntegerProperty CANDLES = IntegerProperty.create("candles", 1, 5);
        public static final BooleanProperty LIT = BlockStateProperties.LIT;
        private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 9, 13);

        public Candles(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(CANDLES, 3).setValue(LIT, true));
        }

        public static int light(BlockState s) {
            return s.getValue(LIT) ? Math.min(15, 3 * s.getValue(CANDLES)) : 0;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(CANDLES, LIT);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPE;
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return false;
        }
    }

    /** Leaded glass in one of five colours (HUE 0..4: deep blue, ruby, amber, sky, violet), glowing from behind. */
    public static final class RoseGlass extends TransparentBlock {
        public static final IntegerProperty HUE = IntegerProperty.create("hue", 0, 4);

        public RoseGlass(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(HUE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(HUE);
        }
    }

    /** A block with a face (an ossuary niche) or a thing hung against a wall (a vestment: no collision, thin). */
    public static final class Facing extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private final boolean hanging;
        private static final VoxelShape[] THIN = {
                Block.box(0, 0, 14, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 2), Block.box(14, 0, 0, 16, 16, 16), Block.box(0, 0, 0, 2, 16, 16)};

        public Facing(BlockBehaviour.Properties p, boolean hanging) {
            super(p);
            this.hanging = hanging;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            if (!hanging) return Shapes.block();
            return switch (s.getValue(FACING)) {
                case SOUTH -> THIN[1];
                case WEST -> THIN[2];
                case EAST -> THIN[3];
                default -> THIN[0];
            };
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return hanging ? Shapes.empty() : Shapes.block();
        }
    }

    /** A thin thing to climb or float: bell rope (climbable by tag) or a lily candle on still water. */
    public static final class Thin extends Block {
        private final VoxelShape shape;

        public Thin(BlockBehaviour.Properties p, VoxelShape shape) {
            super(p);
            this.shape = shape;
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return shape;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }
    }

    /** Bell bronze: strike it (hit or use) and a world's rule may answer (SPEC §38.6, "The Belfry's rule"). */
    public static class Bronze extends Block {
        public Bronze(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
            if (player instanceof ServerPlayer sp) WorldRules.strike(sp, pos);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player instanceof ServerPlayer sp) return WorldRules.strike(sp, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * A handbell in a stone yoke, standing on the floor where anyone can reach it (SPEC §38.8): bronze like the great
     * bells, so striking it answers the world's rule. Its yoke runs across {@link #FACING}; you walk through it.
     */
    public static final class Handbell extends Bronze {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape ACROSS_X = Block.box(0, 0, 4, 16, 16, 12), ACROSS_Z = Block.box(4, 0, 0, 12, 16, 16);

        public Handbell(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return s.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_X : ACROSS_Z;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return true;
        }
    }
}
