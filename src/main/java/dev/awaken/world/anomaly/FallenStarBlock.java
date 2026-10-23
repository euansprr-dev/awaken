package dev.awaken.world.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The meteor a fallen star leaves in its crater. Sealed (white-hot, unbreakable in survival) while its guardian stands;
 * once the guardian is slain or fades it cools, and using or breaking it opens it: its loot table
 * ({@code blocks/anomaly_fallen_star}, materials) drops with the block and {@link Stars#opened} pays the rest.
 */
public class FallenStarBlock extends Block {
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    public FallenStarBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(SEALED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(SEALED) && !player.isCreative() ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (state.getValue(SEALED)) {
                sp.sendOverlayMessage(Component.literal("Too hot to touch while its guardian stands"));
                return InteractionResult.SUCCESS;
            }
            Stars.opened(sl, pos, sp);
            sl.destroyBlock(pos, true, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!state.getValue(SEALED) && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Stars.opened(sl, pos, sp);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
        if (state.getValue(SEALED)) {
            // still smouldering: smoke that rises well clear of the crater, and the odd spark
            if (random.nextInt(2) == 0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y,
                        z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
        } else if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6, 0,
                    0.02 + random.nextDouble() * 0.02, 0);
        }
    }
}
