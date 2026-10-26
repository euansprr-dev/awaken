package dev.awaken.delve;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A coil of Runner's rope: use it on a piton and it pays out down to 32 blocks (the piton does the work). */
public final class RunnerRopeItem extends Item {
    public RunnerRopeItem(Item.Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Use it on a piton: it hangs " + PitonBlock.ROPE + " blocks down").withStyle(ChatFormatting.GRAY));
    }
}
