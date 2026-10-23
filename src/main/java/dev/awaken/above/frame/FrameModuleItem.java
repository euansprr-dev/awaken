package dev.awaken.above.frame;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A Frame module, waiting to be socketed at a Frame Forge. */
public class FrameModuleItem extends Item {
    private final FrameModule module;

    public FrameModuleItem(Item.Properties p, FrameModule module) {
        super(p);
        this.module = module;
    }

    public FrameModule module() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal(module.description).withColor(0xD9E4F2));
        List<String> pieces = new ArrayList<>();
        for (ArmorType t : ArmorType.values()) if (module.fits(t)) pieces.add(pieceName(t));
        out.accept(Component.literal("Fits: " + String.join(", ", pieces)).withColor(0x8FA7C4));
    }

    public static String pieceName(ArmorType t) {
        return switch (t) {
            case HELMET -> "Helm";
            case CHESTPLATE -> "Chest";
            case LEGGINGS -> "Legs";
            case BOOTS -> "Boots";
            case BODY -> "Body";
        };
    }
}
