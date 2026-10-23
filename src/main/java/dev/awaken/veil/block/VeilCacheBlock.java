package dev.awaken.veil.block;

import dev.awaken.veil.VeilGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The heart of a Veil Gate. A spirit who touches it is paid once and sent back out through the rift. */
public class VeilCacheBlock extends Block implements SpiritUsable {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public VeilCacheBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SpiritUsable.ghostOnly(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean spiritUse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer spirit) {
        return VeilGates.claimCache(spirit, level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.5F) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + Math.cos(a) * 0.45, pos.getY() + 0.2 + random.nextDouble() * 0.8,
                    pos.getZ() + 0.5 + Math.sin(a) * 0.45, 0, 0.015, 0);
        }
    }
}
