package dev.awaken.arms;

import dev.awaken.core.api.Lasting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A Maul: vanilla's mace (its smash, Density and Wind Burst), never destroyed by wear ({@link Lasting}, SPEC §68.1). */
public class ArmsMaulItem extends MaceItem implements Lasting {
    private final int formerUses;

    public ArmsMaulItem(Item.Properties p, int formerUses) {
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
