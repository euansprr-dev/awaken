package dev.awaken.hearth.trophy;

import dev.awaken.hearth.HearthContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A boss trophy. Hang it on a Trophy Mount; in a Trophy Hall each different boss gives a small lasting bonus. */
public class TrophyItem extends Item {
    public TrophyItem(Item.Properties p) {
        super(p);
    }

    @Override
    public Component getName(ItemStack stack) {
        TrophyData d = stack.get(HearthContent.TROPHY.get());
        return d == null ? super.getName(stack) : Component.translatable("item.awaken.trophy.named", d.bossName());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        TrophyData d = stack.get(HearthContent.TROPHY.get());
        if (d != null && d.day() > 0) out.accept(Component.literal("Fell on play-day " + (d.day() + 1)).withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Hang it on a Trophy Mount in your Hearth").withStyle(ChatFormatting.DARK_GRAY));
    }
}
