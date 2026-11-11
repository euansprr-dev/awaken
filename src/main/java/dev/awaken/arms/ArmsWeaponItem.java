package dev.awaken.arms;

import dev.awaken.core.api.Lasting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** An Arms weapon: vanilla's item of its kind, never destroyed by wear ({@link Lasting}, SPEC §68.1). */
public class ArmsWeaponItem extends Item implements Lasting {
    private final int formerUses;

    public ArmsWeaponItem(Item.Properties p, int formerUses) {
        super(p);
        this.formerUses = formerUses;
    }

    @Override
    public int formerUses() {
        return formerUses;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Lasting.tooltip(stack, true, out);
        super.appendHoverText(stack, ctx, display, out, flag);
    }
}
