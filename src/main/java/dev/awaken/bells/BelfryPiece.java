package dev.awaken.bells;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * One of the Great Belfry's two needs (SPEC §43.14): the Heart-clapper from an S-rank gate's Lord, or a Sovereign's
 * relic. Set into your bell frame once your tower stands; it says so on the tooltip.
 */
public final class BelfryPiece extends Item {
    public BelfryPiece(Item.Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("item.awaken.belfry_piece.use").withStyle(ChatFormatting.GOLD));
    }
}
