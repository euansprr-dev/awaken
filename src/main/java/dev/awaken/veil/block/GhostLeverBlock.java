package dev.awaken.veil.block;

import dev.awaken.veil.VeilContent;
import dev.awaken.veil.VeilGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A lever made of spirit matter. Only a spirit can pull it; it powers redstone like any lever. */
public class GhostLeverBlock extends LeverBlock implements SpiritUsable {
    public GhostLeverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) SpiritUsable.ghostOnly(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean spiritUse(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer spirit) {
        pull(state, level, pos, null);
        BlockState after = level.getBlockState(pos);
        level.playSound(null, pos, VeilContent.TOUCH.get(), SoundSource.BLOCKS, 0.7F, after.getValue(POWERED) ? 1.3F : 0.9F);
        level.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
        VeilGates.onLever(level, pos);
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < (state.getValue(POWERED) ? 0.3F : 0.08F)) {
            level.addParticle(ParticleTypes.GLOW, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.3 + random.nextDouble() * 0.4,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
