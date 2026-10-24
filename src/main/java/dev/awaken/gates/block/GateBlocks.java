package dev.awaken.gates.block;

import dev.awaken.gates.GatePortalBlockEntity;
import dev.awaken.gates.GateVortexBlockEntity;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.run.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
import org.jspecify.annotations.Nullable;

/** The gate's own blocks. Interaction logic lives in {@link Interactions}; these classes only route events. */
public final class GateBlocks {
    private GateBlocks() {}

    /**
     * The surface gate's core: invisible blocks filling the opening; walk into one to enter. Rank picks the colour,
     * BROKEN after a break. The ANCHOR core (bottom centre) carries the block entity whose renderer draws the whole
     * vortex ({@link dev.awaken.gates.GateVortexBlockEntity}).
     */
    public static final class Core extends Block implements EntityBlock {
        public static final IntegerProperty RANK = IntegerProperty.create("rank", 0, 6);
        public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
        public static final BooleanProperty ANCHOR = BooleanProperty.create("anchor");

        public Core(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(RANK, 0).setValue(BROKEN, false).setValue(ANCHOR, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(RANK, BROKEN, ANCHOR);
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return state.getValue(ANCHOR) ? new GateVortexBlockEntity(pos, state) : null;
        }

        @Override
        public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide() || !state.getValue(ANCHOR) || type != GatesRegistry.VORTEX_BE.get()) return null;
            @SuppressWarnings("unchecked")
            BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<GateVortexBlockEntity>) GateVortexBlockEntity::serverTick;
            return ticker;
        }

        /** The renderer draws the opening as one surface; the blocks themselves are not drawn. */
        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier fx, boolean precise) {
            if (level instanceof ServerLevel sl && entity instanceof ServerPlayer sp && !state.getValue(BROKEN)) Interactions.touchCore(sl, pos, sp);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            // the renderer draws the motes; the blocks only shed ash once the gate has broken
            if (state.getValue(BROKEN) && random.nextInt(3) == 0) {
                level.addParticle(random.nextBoolean() ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                        pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0.03, 0);
            }
        }

        @Override
        protected boolean isPathfindable(BlockState s, PathComputationType t) {
            return false;
        }
    }

    /** Portals inside the gate: the exit after the Lord, the rift to the second gate, the inner gate and its way back. */
    /**
     * A doorway inside a gate (the exit after the Lord, the way back from a second gate, an inner gate, a rift). Placed
     * as a whole opening ({@link #place}): one block per opening is the ANCHOR, whose block entity draws a single vortex
     * across all of it like a surface gate's; the other blocks are invisible. LEGACY is the old per-block texture, kept
     * only for doorways placed before openings had a shape.
     */
    public static final class Portal extends Block implements EntityBlock {
        public enum Kind { EXIT, RIFT, INNER, RETURN }

        public enum Role implements StringRepresentable {
            LEGACY, PART, ANCHOR;

            @Override
            public String getSerializedName() {
                return name().toLowerCase(java.util.Locale.ROOT);
            }
        }

        public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final IntegerProperty RANK = IntegerProperty.create("rank", 0, 6);

        private final Kind kind;

        public Portal(BlockBehaviour.Properties p, Kind kind) {
            super(p);
            this.kind = kind;
            registerDefaultState(stateDefinition.any().setValue(ROLE, Role.LEGACY).setValue(AXIS, Direction.Axis.X).setValue(RANK, 0));
        }

        public Kind kind() {
            return kind;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(ROLE, AXIS, RANK);
        }

        /** The state for one block of an opening: the bottom of the middle column is the anchor. */
        public BlockState opening(Direction.Axis axis, int rank, boolean anchor) {
            return defaultBlockState().setValue(ROLE, anchor ? Role.ANCHOR : Role.PART).setValue(AXIS, axis)
                    .setValue(RANK, Math.max(0, Math.min(6, rank)));
        }

        /**
         * Places a whole opening at run time: {@code width} along {@code axis} centred on {@code bottomCentre},
         * {@code height} up from it.
         */
        public void place(Level level, BlockPos bottomCentre, Direction.Axis axis, int width, int height, int rank) {
            int half = width / 2;
            for (int a = -half; a <= half; a++) {
                for (int dy = 0; dy < height; dy++) {
                    BlockPos p = axis == Direction.Axis.X ? bottomCentre.offset(a, dy, 0) : bottomCentre.offset(0, dy, a);
                    level.setBlock(p, opening(axis, rank, a == 0 && dy == 0), Block.UPDATE_ALL);
                }
            }
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return state.getValue(ROLE) == Role.ANCHOR ? new GatePortalBlockEntity(pos, state) : null;
        }

        /** Old doorways (placed before openings had a shape) upgrade themselves once someone is near. */
        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return state.getValue(ROLE) == Role.LEGACY;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            upgradeLegacy(level, pos);
        }

        /** Finds the old opening this block belongs to and redraws it as one: axis, bottom row, middle column. */
        public void upgradeLegacy(ServerLevel level, BlockPos pos) {
            boolean alongX = level.getBlockState(pos.east()).is(this) || level.getBlockState(pos.west()).is(this);
            Direction.Axis axis = alongX ? Direction.Axis.X : Direction.Axis.Z;
            Direction down = alongX ? Direction.WEST : Direction.NORTH, up = down.getOpposite();
            BlockPos bottom = pos;
            for (int i = 0; i < 6 && level.getBlockState(bottom.below()).is(this); i++) bottom = bottom.below();
            BlockPos lo = bottom, hi = bottom;
            for (int i = 0; i < 4 && level.getBlockState(lo.relative(down)).is(this); i++) lo = lo.relative(down);
            for (int i = 0; i < 4 && level.getBlockState(hi.relative(up)).is(this); i++) hi = hi.relative(up);
            int width = (int) lo.distManhattan(hi) + 1, height = 1;
            while (height < 6 && level.getBlockState(bottom.above(height)).is(this)) height++;
            BlockPos anchor = lo.relative(up, width / 2);
            var where = dev.awaken.gates.run.Where.at(level.getServer(), pos);
            int rank = where == null ? 0 : where.gate().plan.rank().ordinal();
            for (int a = 0; a < width; a++) {
                for (int dy = 0; dy < height; dy++) {
                    BlockPos p = lo.relative(up, a).above(dy);
                    if (level.getBlockState(p).is(this)) level.setBlock(p, opening(axis, rank, p.equals(anchor)), Block.UPDATE_CLIENTS);
                }
            }
        }

        /** The anchor's renderer draws the opening as one surface; only legacy doorways draw their blocks. */
        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return state.getValue(ROLE) == Role.LEGACY ? RenderShape.MODEL : RenderShape.INVISIBLE;
        }

        /** Nothing to outline or aim at in a drawn opening (walking in still works: that uses the full block). */
        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(ROLE) == Role.LEGACY ? Shapes.block() : Shapes.empty();
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier fx, boolean precise) {
            if (level instanceof ServerLevel sl && entity instanceof ServerPlayer sp) Interactions.touchPortal(sl, pos, sp, kind);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            // a drawn opening has its own motes; a few portal sparks still drift off its blocks
            if (random.nextInt(state.getValue(ROLE) == Role.LEGACY ? 3 : 8) == 0) {
                level.addParticle(kind == Kind.RIFT ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                        pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0, 0, 0);
            }
        }
    }

    /** Rest Shrine: extract with what you carry. */
    public static final class Shrine extends Block {
        public Shrine(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.useShrine(sp, pos);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 1.1,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
            }
        }
    }

    /** Seal: go one floor deeper. */
    public static final class Seal extends Block {
        public Seal(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.useSeal(sp, pos);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + random.nextDouble(), pos.getY() + 1.2,
                        pos.getZ() + random.nextDouble(), 0, -0.4, 0);
            }
        }
    }

    /** Cache: opened once per player run; loot goes to the Satchel. */
    public static final class Cache extends Block {
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

        public Cache(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(OPEN);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.openCache(sp, pos, state);
            return InteractionResult.SUCCESS;
        }
    }

    /** Puzzle rune: pressing it flips it and its neighbours. */
    public static final class Rune extends Block {
        public static final BooleanProperty LIT = BlockStateProperties.LIT;

        public Rune(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(LIT);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.pressRune(sp, pos);
            return InteractionResult.SUCCESS;
        }
    }

    /** The sigil you arrive on. Using it on floor 1 offers a retreat. */
    public static final class Entrance extends Block {
        public Entrance(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.useEntrance(sp, pos);
            return InteractionResult.SUCCESS;
        }
    }

    /** A lost lantern (Friendly gates): carry it to the spirit. */
    public static final class Lantern extends Block {
        private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 9, 11);

        public Lantern(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) Interactions.takeLantern(sp, pos);
            return InteractionResult.SUCCESS;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 0, 0.02, 0);
        }
    }

    /** Sky Archipelago: an updraft that lifts you back to the islands. */
    public static final class Updraft extends Block {
        public Updraft(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (!level.isClientSide() && entity instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 6, false, false));
                le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
            }
            super.stepOn(level, pos, state, entity);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + random.nextDouble(), pos.getY() + 1.0, pos.getZ() + random.nextDouble(), 0, 0.25, 0);
        }
    }

    /** Rune bars: seal doorways and vault cages until the gate decides otherwise. */
    public static final class Bars extends Block {
        public Bars(BlockBehaviour.Properties p) {
            super(p);
        }

        @Override
        protected float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
            return 1.0F;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState s) {
            return true;
        }
    }
}
