package dev.awaken.village.trouble;

import net.minecraft.ChatFormatting;
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
 * A village's bell (SPEC §28.7, the Bell): new-cast in bell bronze at its smith, or taken back from the camp that stole
 * it. Heavy: carried in a hand it slows you (see {@link BellTrouble#heavy}). Used on the empty place in its belfry, it
 * hangs there and rings again.
 */
public final class VillageBell extends Item {
    public VillageBell(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
        return BellTrouble.hang(level, p, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getItemInHand()) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (owner instanceof ServerPlayer p && level.getGameTime() % 10 == 3) BellTrouble.heavy(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.awaken.village_bell.desc").withStyle(ChatFormatting.GRAY));
    }
}
