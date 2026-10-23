package dev.awaken.lucidium.block;

import dev.awaken.lucidium.LucidiumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Dream Bell (Wake Back To Bed). Armed, it rings at midnight for anyone asleep or dreaming within 8 blocks:
 * they wake, and if they sleep again within an in-game hour the next dream starts 25 lucidity higher.
 * Right-click to arm or silence it.
 */
public class DreamBellBlock extends Block {
    public static final BooleanProperty ARMED = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 13, 13);

    public DreamBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ARMED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            boolean armed = !state.getValue(ARMED);
            level.setBlock(pos, state.setValue(ARMED, armed), Block.UPDATE_ALL);
            level.playSound(null, pos, LucidiumContent.BELL.get(), SoundSource.BLOCKS, armed ? 0.6f : 0.3f, armed ? 1.4f : 0.8f);
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable(armed ? "block.awaken.dream_bell.armed" : "block.awaken.dream_bell.silent"));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
