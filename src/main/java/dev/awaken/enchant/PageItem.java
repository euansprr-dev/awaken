package dev.awaken.enchant;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/**
 * An Enchantment Page (SPEC §45.2): one enchantment, in vanilla's {@code stored_enchantments}. Use it to learn the
 * enchantment for good; the page is spent. A page you know already stays, to give away. One whose enchantment is gone
 * from the game turns to paper.
 */
public class PageItem extends Item {
    static final int GREY = 0x9AA3B2;

    public PageItem(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (Pages.read(sp, stack)) {
            stack.consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (Pages.teaches(stack).isEmpty() && stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()) {
            player.setItemInHand(hand, new ItemStack(Items.PAPER, stack.getCount()));
        }
        return InteractionResult.FAIL;
    }

    @Override
    public Component getName(ItemStack stack) {
        List<Inscriptions.Entry> on = Pages.teaches(stack);
        if (on.isEmpty()) return super.getName(stack);
        return Component.literal("Page of " + on.getFirst().name());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        for (Inscriptions.Entry e : Pages.teaches(stack)) {
            out.accept(Component.literal(e.family().label + " · fits " + e.fits()).withColor(e.colour()));
            out.accept(Component.literal(e.line()).withColor(GREY));
        }
        String where = Pages.where(stack);
        if (!where.isEmpty()) out.accept(Component.literal(where).withColor(GREY));
        out.accept(Component.literal("Use it to learn it for good").withColor(GREY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
