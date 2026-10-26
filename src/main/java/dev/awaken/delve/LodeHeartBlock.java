package dev.awaken.delve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lode's heart (PLAN 7.3): a crystallised knot of its ore at the seam's centre, faintly lit and glinting. Breaking the
 * heart a lode was planned with opens it ("the vein gives", {@link Veins}); its loot is a lump of the ore.
 */
public final class LodeHeartBlock extends Block {
    public final Ore ore;

    public LodeHeartBlock(BlockBehaviour.Properties p, Ore ore) {
        super(p);
        this.ore = ore;
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) != 0) return;
        Direction d = Direction.getRandom(r);
        if (level.getBlockState(pos.relative(d)).isSolidRender()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.52 + (d.getStepX() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        double y = pos.getY() + 0.5 + d.getStepY() * 0.52 + (d.getStepY() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.52 + (d.getStepZ() == 0 ? (r.nextDouble() - 0.5) * 0.9 : 0);
        // a glint doesn't move: its y speed carries the ore (ordinal + 1), which the client turns into its tint
        level.addParticle(DelveContent.GLINT.get(), x, y, z, 0, ore.ordinal() + 1, 0);
    }
}
