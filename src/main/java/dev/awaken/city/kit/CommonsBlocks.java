package dev.awaken.city.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The commons kit (Milestone 4, the Commons step): the countryside round Carillon, outside the wall. Milestones and
 * fingerposts on the roads, scarecrows and wheat stooks in the fields. Same rules as the rest of the kit: no block
 * entities, no loot, no light. Models are drawn for facing=north. Art: tools/art/specs/city_commons.py.
 */
public final class CommonsBlocks {
    private CommonsBlocks() {}

    /** The lower or upper block of a two-block piece. */
    public enum Half implements KitBlocks.Named { LOWER, UPPER }

    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);

    /** A milestone on a road's verge: a squat weathered post with the city's bell cut in its face (facing the road). */
    public static final class Milestone extends HorizontalDirectionalBlock {
        private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);

        public Milestone(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    /**
     * A fingerpost where a way leaves the road: two blocks of post, the upper with two arms, one pointing the way it faces
     * (to the Wayfarers' fire, or to the old mine) and one pointing back (to the city, its bell painted on the board).
     */
    public static final class Fingerpost extends HorizontalDirectionalBlock {
        public enum To implements KitBlocks.Named { CAMP, MINE }

        public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
        public static final EnumProperty<To> TO = EnumProperty.create("to", To.class);

        public Fingerpost(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.LOWER).setValue(TO, To.CAMP));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, HALF, TO);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return POST;
        }
    }

    /** A scarecrow on its stake: straw legs and the coat's skirt below, the arms on a crossbar and a sacking head above. */
    public static final class Scarecrow extends HorizontalDirectionalBlock {
        public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);

        public Scarecrow(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING, HALF);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return POST;
        }
    }

    /** A stook: sheaves of cut wheat stood up together to dry in a reaped field. */
    public static final class Stook extends HorizontalDirectionalBlock {
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

        public Stook(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }
}
