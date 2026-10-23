package dev.awaken.gates.block;

import dev.awaken.gates.GateVortexBlockEntity;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.run.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
    public static final class Portal extends Block {
        public enum Kind { EXIT, RIFT, INNER, RETURN }

        private final Kind kind;

        public Portal(BlockBehaviour.Properties p, Kind kind) {
            super(p);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
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
            if (random.nextInt(3) == 0) {
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
