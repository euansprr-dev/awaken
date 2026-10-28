package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bramble (SPEC §41.2): arching thorny canes, the only wild plant that slows you, and it snags you if you sprint through.
 * {@code fruit}: 0 white flowers, 1 green berries, 2 ripe blackberries; it ripens on random ticks in the light, and ripe
 * berries are picked by hand (back to flowers). Sways as the game's bushes do.
 */
public class BrambleBlock extends BushBlock {
    public static final IntegerProperty FRUIT = IntegerProperty.create("fruit", 0, 2);
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 15.0);
    /** Slowed to this share of your speed inside it (sweet berry bushes keep 0.8). */
    private static final Vec3 STUCK = new Vec3(0.7, 0.75, 0.7);

    public BrambleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FRUIT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FRUIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FRUIT) < 2;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0 && level.getRawBrightness(pos.above(), 0) >= 9) {
            level.setBlock(pos, state.setValue(FRUIT, state.getValue(FRUIT) + 1), 2);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean isPrecise) {
        if (!(entity instanceof LivingEntity living) || entity.is(EntityTypes.FOX) || entity.is(EntityTypes.BEE)) return;
        entity.makeStuckInBlock(state, STUCK);
        if (level instanceof ServerLevel server && living.isSprinting()) {
            entity.hurtServer(server, level.damageSources().sweetBerryBush(), 1.0F);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(FRUIT) < 2) return super.useWithoutItem(state, level, pos, player, hit);
        if (level instanceof ServerLevel server) {
            Block.popResource(server, pos, new ItemStack(WildBlocks.BLACKBERRIES.get(), 1 + server.getRandom().nextInt(2)));
            server.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + server.getRandom().nextFloat() * 0.4F);
            BlockState picked = state.setValue(FRUIT, 0);
            server.setBlock(pos, picked, 2);
            server.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, picked));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return state.getValue(FRUIT) < 2;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        level.setBlock(pos, state.setValue(FRUIT, Math.min(2, state.getValue(FRUIT) + 1)), 2);
    }
}
