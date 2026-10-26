package dev.awaken.village.trouble;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Something a village gives to remember it by (SPEC §28.7): a carved charm on a cord, named for what you did ("Bren's
 * Ribbon", "Kettleby's Thanks") with a line or two of who gave it and why. Nothing more than that: a keepsake.
 */
public final class Keepsake extends Item {
    public Keepsake(Item.Properties p) {
        super(p);
    }

    /** A keepsake named and inscribed. */
    public static ItemStack make(String name, String... lines) {
        ItemStack s = new ItemStack(TroubleContent.KEEPSAKE.get());
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GOLD));
        List<Component> lore = new ArrayList<>();
        for (String l : lines) lore.add(Component.literal(l).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        s.set(DataComponents.LORE, new ItemLore(lore));
        return s;
    }
}
