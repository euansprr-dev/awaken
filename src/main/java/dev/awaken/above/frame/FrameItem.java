package dev.awaken.above.frame;

import dev.awaken.above.AboveComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A piece of a Precursor exosuit Frame. Holds socketed module ids in {@code awaken:frame_modules}. */
public class FrameItem extends Item {
    private final ArmorType armorType;

    public FrameItem(Item.Properties p, ArmorType type) {
        super(p);
        this.armorType = type;
    }

    public ArmorType armorType() {
        return armorType;
    }

    public static List<String> modules(ItemStack stack) {
        return stack.getOrDefault(AboveComponents.FRAME_MODULES.get(), List.of());
    }

    public static boolean canSocket(ItemStack frame, FrameModule m) {
        if (!(frame.getItem() instanceof FrameItem fi) || !m.fits(fi.armorType)) return false;
        List<String> mods = modules(frame);
        return mods.size() < FrameModule.slots(fi.armorType) && !mods.contains(m.id);
    }

    public static void socket(ItemStack frame, FrameModule m) {
        List<String> mods = new ArrayList<>(modules(frame));
        mods.add(m.id);
        frame.set(AboveComponents.FRAME_MODULES.get(), List.copyOf(mods));
    }

    /** Removes the module at {@code index}; returns it (or null). */
    public static FrameModule unsocket(ItemStack frame, int index) {
        List<String> mods = new ArrayList<>(modules(frame));
        if (index < 0 || index >= mods.size()) return null;
        FrameModule m = FrameModule.byId(mods.remove(index));
        frame.set(AboveComponents.FRAME_MODULES.get(), List.copyOf(mods));
        return m;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        List<String> mods = modules(stack);
        int slots = FrameModule.slots(armorType);
        out.accept(Component.literal("Module slots " + mods.size() + " / " + slots).withColor(0x8FA7C4));
        for (String id : mods) {
            FrameModule m = FrameModule.byId(id);
            out.accept(Component.literal("◆ " + (m == null ? id : m.displayName)).withColor(0x7FC3FF));
        }
        if (mods.isEmpty()) out.accept(Component.literal("Socket modules at a Frame Forge").withColor(0x8FA7C4));
    }
}
