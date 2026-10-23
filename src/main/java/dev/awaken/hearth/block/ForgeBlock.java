package dev.awaken.hearth.block;

import dev.awaken.hearth.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Hearth's forge. Hold a weapon or armour piece and use it to temper the gear; use it empty-handed to upgrade the
 * Forge itself. In an Armory (anvil + Forge + armour stand in one room) it tempers higher.
 */
public class ForgeBlock extends HorizontalDirectionalBlock {
    public ForgeBlock(BlockBehaviour.Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Upgrades.isTemperable(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Upgrades.offerTemper(sp, sl, pos, hand, "forge");
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Upgrades.offerForgeUpgrade(sp, sl, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel sl) dev.awaken.hearth.HearthService.blockChanged(sl, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction f = state.getValue(FACING);
        double x = pos.getX() + 0.5 + f.getStepX() * 0.52, z = pos.getZ() + 0.5 + f.getStepZ() * 0.52;
        double side = (random.nextDouble() - 0.5) * 0.5;
        x += f.getAxis() == Direction.Axis.Z ? side : 0;
        z += f.getAxis() == Direction.Axis.X ? side : 0;
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, x, pos.getY() + 0.25 + random.nextDouble() * 0.2, z, 0, 0, 0);
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0, 0.03, 0);
    }
}
