package dev.awaken.above.scan;

import dev.awaken.above.AboveBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * The Precursor Scanner. Use it on a broken machine to read its blueprint, on a log terminal to read the log; hold it up
 * at the sky and sweep to hear which way an undiscovered ruin lies.
 */
public class ScannerItem extends Item {
    public ScannerItem(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean machine = state.is(AboveBlocks.BROKEN_MACHINE.get());
        boolean terminal = state.is(AboveBlocks.LOG_TERMINAL.get());
        if (!machine && !terminal) return InteractionResult.PASS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            if (machine) Scanning.scanMachine(sp, pos, state);
            else Scanning.readTerminal(sp, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72_000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (!(entity instanceof ServerPlayer sp)) return;
        int used = getUseDuration(stack, entity) - ticksRemaining;
        if (used > 0 && used % 10 == 0) Scanning.sweep(sp, used);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Use on a broken Precursor machine to read it").withColor(0xD9E4F2));
        out.accept(Component.literal("Hold it up at the sky to listen for ruins").withColor(0x8FA7C4));
    }
}
