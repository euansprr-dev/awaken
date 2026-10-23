package dev.awaken.descent.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiConsumer;

/** A plain block that does something when a player uses it (the Well's lift, a stratum's ascent stone). */
public class UseBlock extends Block {
    private final BiConsumer<ServerPlayer, BlockPos> onUse;

    public UseBlock(BlockBehaviour.Properties p, BiConsumer<ServerPlayer, BlockPos> onUse) {
        super(p);
        this.onUse = onUse;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) onUse.accept(sp, pos);
        return InteractionResult.SUCCESS;
    }
}
