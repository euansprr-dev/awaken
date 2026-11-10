package dev.awaken.gates.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Molten wax, The Wax Galleries' rule (SPEC 50.8). You wade through it and it drags at you; a fall into it costs you nothing. The cold wax round it is an ordinary block and holds your
 * weight. A block rather than a watcher so the drag runs on the client too and feels like the wax, not like a lagging effect.
 */
public final class WarmWaxBlock extends Block {
    /** How much of your speed you keep in it. */
    public static final double KEEPS = 0.42;

    public static DeferredBlock<WarmWaxBlock> BLOCK;

    public WarmWaxBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    /**
     * The wax family's blocks that need a class of their own, into the theme table so each gets its item: the slab and stairs
     * cut from cold wax, the arch corner (it faces a way), the drip (it has a length). Warm wax itself is never held.
     */
    public static void register(DeferredRegister.Blocks blocks, java.util.Map<String, DeferredBlock<? extends Block>> out) {
        dev.awaken.core.api.Sources.neverHeld("molten wax, drawn into the land", "wax_warm");
        BLOCK = blocks.registerBlock("wax_warm", WarmWaxBlock::new,
                p -> p.noCollision().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F).sound(SoundType.HONEY_BLOCK).lightLevel(s -> 12)
                        .isValidSpawn((s, l, pos, t) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false)
                        .pushReaction(PushReaction.IMMOVEABLE));
        DeferredBlock<? extends Block> cold = out.get("wax_cold");
        out.put("wax_cold_slab", blocks.registerBlock("wax_cold_slab", net.minecraft.world.level.block.SlabBlock::new,
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_WHITE).sound(SoundType.CANDLE).strength(0.6F)));
        out.put("wax_cold_stairs", blocks.registerBlock("wax_cold_stairs", p -> new net.minecraft.world.level.block.StairBlock(cold.get().defaultBlockState(), p),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_WHITE).sound(SoundType.CANDLE).strength(0.6F)));
        out.put("wax_arch", blocks.registerBlock("wax_arch", p -> new dev.awaken.gates.block.WorldBlocks.Facing(p, false),
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_WHITE).sound(SoundType.CANDLE).strength(0.6F).noOcclusion()
                        .isValidSpawn((s, l, pos, t) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false)));
        out.put("wax_drip", blocks.registerBlock("wax_drip", Drip::new,
                p -> p.mapColor(net.minecraft.world.level.material.MapColor.TERRACOTTA_WHITE).sound(SoundType.CANDLE).noOcclusion().noCollision().instabreak()
                        .isValidSpawn((s, l, pos, t) -> false).isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false)));
    }

    /** A hanging drip of set wax. Length 0 is a strand that carries on into the block below; 1 to 3 are tips, short to long. */
    public static final class Drip extends Block {
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty LENGTH = net.minecraft.world.level.block.state.properties.IntegerProperty.create("length", 0, 3);
        private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

        public Drip(BlockBehaviour.Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(LENGTH, 3));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
            b.add(LENGTH);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState other, Direction side) {
        return other.is(this) || super.skipRendering(state, other, side);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        entity.makeStuckInBlock(state, new Vec3(KEEPS, 0.7, KEEPS));
        entity.resetFallDistance();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(60) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0.01, 0);
    }
}
