package dev.awaken.gates.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Scorched stone split by a crack that glows in the gate's rank colour (0 E .. 6 Sovereign, 7 = broken, red). It runs
 * from the foot of a surface gate across its pad; the gate darkens it to coarse dirt when it leaves.
 */
public final class GateFissureBlock extends Block {
    public static final IntegerProperty RANK = IntegerProperty.create("rank", 0, 7);
    /** Which way the crack runs: 0 east-west, 1 north-west to south-east, 2 north-south, 3 north-east to south-west. */
    public static final IntegerProperty DIR = IntegerProperty.create("dir", 0, 3);

    public GateFissureBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(RANK, 0).setValue(DIR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(RANK, DIR);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(state.getValue(RANK) == 7 ? 6 : 14) != 0 || !level.getBlockState(pos.above()).isAir()) return;
        level.addParticle(state.getValue(RANK) == 7 ? ParticleTypes.SMALL_FLAME : ParticleTypes.WHITE_ASH,
                pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
    }
}
