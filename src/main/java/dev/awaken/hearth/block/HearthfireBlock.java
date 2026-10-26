package dev.awaken.hearth.block;

import dev.awaken.hearth.Embers;
import dev.awaken.hearth.HearthContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Hearthfire (SPEC §28.2): a ring of stones holding the Ember's coal, burning on its own. Set down from the Hearth
 * Ember, it is a Tier 0 Hearth (radius 24): respawn, rest, calm land. A Lord's Heart (or the Anchor) used on it turns it
 * into the Anchor in place; until the first room stands, using it empty-handed lifts the Ember again. Only its owner
 * can break it, and the coal comes back to them. Light 14; its flame and drawn glow are our own.
 */
public class HearthfireBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 5.0);

    public HearthfireBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HearthfireBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!Embers.raisesTheFire(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Embers.raise(sp, sl, pos, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Embers.useFire(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Embers.onFireRemoved(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.3, z = pos.getZ() + 0.5;
        // tongues of flame licking up off the coal, and now and then an ember that lifts and drifts
        if (random.nextInt(2) == 0) {
            level.addParticle(HearthContent.HEARTH_FLAME.get(), x + (random.nextDouble() - 0.5) * 0.3, y + 0.05,
                    z + (random.nextDouble() - 0.5) * 0.3, 0, 0.02 + random.nextDouble() * 0.015, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(HearthContent.HEARTH_EMBER.get(), x + (random.nextDouble() - 0.5) * 0.4, y + 0.4, z + (random.nextDouble() - 0.5) * 0.4,
                    (random.nextDouble() - 0.5) * 0.02, 0.03 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
        }
        if (random.nextInt(18) == 0) {
            level.playLocalSound(x, y, z, HearthContent.SND_FIRE.get(), SoundSource.BLOCKS, 0.55F + random.nextFloat() * 0.3F,
                    0.85F + random.nextFloat() * 0.3F, false);
        }
    }
}
