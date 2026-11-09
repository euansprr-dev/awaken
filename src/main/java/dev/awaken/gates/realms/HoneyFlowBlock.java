package dev.awaken.gates.realms;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The surface of a honey river (SPEC 50.8): it glows, it looks like liquid, and you wade through it up to the knees. */
public final class HoneyFlowBlock extends Block {
    private static final VoxelShape LOOK = Block.box(0, 0, 0, 16, 15, 16), WADE = Block.box(0, 0, 0, 16, 7, 16);
    private static final Vec3 SLOW = new Vec3(0.62, 1.0, 0.62);

    public HoneyFlowBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return LOOK;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return WADE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (entity.getY() < pos.getY() + 15 / 16.0) entity.makeStuckInBlock(state, SLOW);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // heard, not seen: a falling-honey particle on a flat river reads as dirt floating on it
        if (random.nextInt(90) == 0) level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, SoundEvents.BEEHIVE_DRIP, SoundSource.AMBIENT, 0.28F, 0.65F + random.nextFloat() * 0.3F, false);
    }
}
