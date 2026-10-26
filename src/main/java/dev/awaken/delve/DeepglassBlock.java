package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deepglass (PLAN 7.3): black-violet crystal where the Deep presses up into the rock, below y 0 and in the Descent's
 * strata walls. It needs a diamond edge, it hums (a faint sound now and then within a few blocks), motes of the Deep
 * drift off its open faces, and the Veil sees it from far off (it's in {@code #c:ores}).
 */
public final class DeepglassBlock extends Block {
    public DeepglassBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(70) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DelveContent.DEEPGLASS_HUM.get(), SoundSource.BLOCKS,
                    0.35F, 0.85F + r.nextFloat() * 0.3F, false);
        }
        if (r.nextInt(5) != 0) return;
        Direction d = Direction.getRandom(r);
        BlockPos n = pos.relative(d);
        if (level.getBlockState(n).isSolidRender()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.8 : 0);
        level.addParticle(DelveContent.MOTE.get(), x, y, z, d.getStepX() * 0.004, 0.006, d.getStepZ() * 0.004);
    }
}
