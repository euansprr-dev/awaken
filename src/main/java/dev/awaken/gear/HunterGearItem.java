package dev.awaken.gear;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Every Hunter gear piece. The stats are vanilla components; the tooltip says the tier and what the piece adds. */
public class HunterGearItem extends Item {
    public final HunterTier tier;
    public final Piece piece;

    public HunterGearItem(Item.Properties p, HunterTier tier, Piece piece) {
        super(p);
        this.tier = tier;
        this.piece = piece;
    }

    static final int GREY = 0x8A93A6;
    static final int LIGHT = 0xC9D2E3;

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(tier.rank.label + "-rank Hunter gear · " + tier.setName).withColor(tier.rank.colour));
        switch (piece) {
            case FOCUS -> {
                int pct = (int) Math.round(GearCombat.focusBonus(stack) * 100);
                out.accept(Component.literal("Powers deal +" + pct + "% damage").withColor(LIGHT));
                out.accept(Component.literal("Held in either hand").withColor(GREY));
            }
            case MATTOCK -> out.accept(Component.literal("Mines stone, wood and earth").withColor(LIGHT));
            case BLADE -> {}
            default -> {
                for (SetBonus.Line line : SetBonus.lines(tier)) {
                    out.accept(Component.literal(line.pieces() + " worn: " + line.text()).withColor(GREY));
                }
            }
        }
    }
}
