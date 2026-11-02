package dev.awaken.bells;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The Bell Frame (SPEC §43.7, §43.16): two posts and a beam with three hooks, made from planks and logs and set inside
 * your own Hearth, one per home. Your first bells hang on it, your song starts from it, quiet ground centres on it, and the
 * Great Belfry's two pieces are set into it.
 */
public final class BellFrameBlock extends HookBlock {
    private static final Map<Direction, VoxelShape> SHAPE = Shapes.rotateHorizontal(Shapes.or(Block.box(1, 0, 6, 3, 16, 10),
            Block.box(13, 0, 6, 15, 16, 10), Block.box(0, 14, 5.5, 16, 16, 10.5)));

    public BellFrameBlock(BlockBehaviour.Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE.get(s.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level instanceof ServerLevel sl) BellTower.placed(sl, pos, by instanceof ServerPlayer sp ? sp : null);
    }

    /** A bell hangs on it; the Heart-clapper or a Sovereign's relic is set into it for the Great Belfry (SPEC §43.14). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(BellContent.HEART_CLAPPER.get()) || stack.is(BellContent.SOVEREIGN_RELIC.get())) {
            if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) GreatBelfry.offer(sl, pos, sp, stack);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Nothing in reach to ring: how many bells are in your song and what's next. */
    @Override
    protected void usedEmpty(ServerLevel level, BlockPos pos, ServerPlayer p) {
        BellTower.used(level, pos, p);
    }
}
