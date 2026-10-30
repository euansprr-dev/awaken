package dev.awaken.bells;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * The handbell (SPEC §43.7), given with your first bell. Use it to ring the peal it's set to; sneak and use it to set
 * the next peal your bells make. Rung out in the wild it sends the land's beasts running for a minute.
 */
public final class Handbell extends Item {
    public Handbell(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            if (sp.isShiftKeyDown()) Peals.cycle(sp);
            else Peals.ring(sl, sp, sp.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Use to ring. Sneak and use to change the peal.").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
