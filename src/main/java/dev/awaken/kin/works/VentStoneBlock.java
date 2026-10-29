package dev.awaken.kin.works;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A vent stone (SPEC §43.12): warm through, it breathes steam from its cracks wherever it's set, a hot spring's or yours. */
public final class VentStoneBlock extends Block {
    public VentStoneBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) != 0) return;
        double x = pos.getX() + 0.25 + r.nextDouble() * 0.5, z = pos.getZ() + 0.25 + r.nextDouble() * 0.5;
        level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 1.02, z, (r.nextDouble() - 0.5) * 0.01, 0.04 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.01);
        if (r.nextInt(6) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x, pos.getY() + 1.0, z, 0, 0.01, 0);
    }
}
