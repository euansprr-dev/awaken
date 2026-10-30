package dev.awaken.armour;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.function.Consumer;

/** A piece of a set with items of its own (SPEC §44.3): its stats are vanilla's components; the tooltip says what the set does. */
public class ForgedArmourItem extends Item {
    public final ArmourSet set;
    public final ArmorType piece;

    public ForgedArmourItem(Item.Properties p, ArmourSet set, ArmorType piece) {
        super(p);
        this.set = set;
        this.piece = piece;
    }

    static final int GREY = 0x8A93A6, LIGHT = 0xC9D2E3;

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(set.rank.label + "-rank " + set.archetype.label + " armour · " + set.setName).withColor(set.rank.colour));
        ArmourItems.Numbers n = ArmourItems.numbers(set);
        if (n.speed() > 0) out.accept(Component.literal("+" + Math.round(n.speed() * 100) + "% speed").withColor(LIGHT));
        if (n.power() > 0) out.accept(Component.literal("Powers hit " + Math.round(n.power() * 100) + "% harder").withColor(LIGHT));
        for (ArmourBonus.Line l : ArmourBonus.lines(set)) out.accept(Component.literal(l.pieces() + " worn: " + l.text()).withColor(GREY));
    }
}
