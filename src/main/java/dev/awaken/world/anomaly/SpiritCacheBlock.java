package dev.awaken.world.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The spirit procession's gift: a small glowing coffer. Using it or breaking it opens it (its loot table,
 * {@code blocks/anomaly_spirit_cache}, drops once with the block) and tells the procession it was found.
 */
public class SpiritCacheBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);

    public SpiritCacheBlock(Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Processions.cacheOpened(sl, pos, sp);
            sl.destroyBlock(pos, true, sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Processions.cacheOpened(sl, pos, sp);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.03 + random.nextDouble() * 0.02, 0);
        }
    }
}
