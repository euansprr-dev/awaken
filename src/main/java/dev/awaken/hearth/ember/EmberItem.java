package dev.awaken.hearth.ember;

import dev.awaken.hearth.Embers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The Hearth Ember (SPEC §28.2): a small iron lantern holding a bell-gold coal from the Wayfarers' fire. Carried, its
 * light is drawn on the ground round you (no light blocks). Used on open ground it sets the coal down and founds a
 * Tier 0 Hearthfire there. One per player, kept through death; a spare one goes out on its own.
 */
public class EmberItem extends Item {
    public EmberItem(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (ctx.getLevel() instanceof ServerLevel level && ctx.getPlayer() instanceof ServerPlayer sp) {
            Embers.setDown(sp, level, ctx.getClickedPos(), ctx.getHand());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner instanceof ServerPlayer sp && sp.tickCount % 40 == 7) Embers.checkCarried(sp, stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("An ember from the Wayfarer camp's fire.").withStyle(ChatFormatting.GOLD));
        out.accept(Component.literal("Place it where you want your home. It starts your Hearth.").withStyle(ChatFormatting.GRAY));
    }
}
