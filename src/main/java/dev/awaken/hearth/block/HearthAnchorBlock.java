package dev.awaken.hearth.block;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The heart of a home. One per player; defines the Hearth's radius, respawn point and the warmth that mends Fracture. */
public class HearthAnchorBlock extends Block {
    public HearthAnchorBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel level && ctx.getPlayer() instanceof ServerPlayer sp
                && !HearthService.canPlaceAnchor(sp, level, ctx.getClickedPos())) {
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel sl && by instanceof ServerPlayer sp) HearthService.onAnchorPlaced(sp, sl, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) HearthService.onAnchorUsed(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        HearthService.onAnchorRemoved(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(HearthContent.HEARTH_EMBER.get(), x, pos.getY() + 1.05, z, (random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.01);
    }
}
