package dev.awaken.delve.forged;

import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.delve.api.Pick;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A pick forged at the War Anvil (SPEC §49). It never breaks: at its last use it goes blunt until it's dressed. The
 * Mattock strips and flattens as an axe and a shovel would, and tills when you sneak. Its rank and form are in the item;
 * everything else in {@link PickForged}.
 */
public final class ForgedPickItem extends Item {
    public final PickForm form;
    public final int tier;

    public ForgedPickItem(Item.Properties p, PickForm form, int tier) {
        super(p);
        this.form = form;
        this.tier = tier;
    }

    /** It wears to its last use and stays there. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<ItemStack> onBroken) {
        return Math.max(0, Math.min(amount, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (form != PickForm.MATTOCK) return InteractionResult.PASS;
        var registry = ctx.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER);
        if (ctx.getPlayer() != null && ctx.getPlayer().isSecondaryUseActive()) return transform(ctx, registry.getOrThrow(BlockTransformers.HOE));
        InteractionResult axe = transform(ctx, registry.getOrThrow(BlockTransformers.AXE));
        return axe != InteractionResult.PASS ? axe : transform(ctx, registry.getOrThrow(BlockTransformers.SHOVEL));
    }

    private static InteractionResult transform(UseOnContext ctx, Holder<BlockTransformer> t) {
        return t.value().transformBlock(ctx, t);
    }

    /** The Delver's speed is its rank and its forging; Efficiency would stack past both. Anything else a pick takes, it takes. */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return !enchantment.is(Enchantments.EFFICIENCY) && super.supportsEnchantment(stack, enchantment);
    }

    /** A tally bumping or wear shouldn't make it dip in the hand: only another item does. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(oldStack.getItem());
    }

    /** Nor restart a block half broken. */
    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !newStack.is(oldStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        PickForged d = ForgedPicks.data(stack);
        String line = AnvilSong.Quality.of(d.quality()).label + ", " + PickStats.colourName(d.colour()).toLowerCase(Locale.ROOT);
        if (d.is(PickForged.TWIN)) line += ", twin-struck";
        if (d.is(PickForged.MEMORY)) line += ", from memory";
        out.accept(Component.literal(line).withStyle(ChatFormatting.GOLD));
        if (!d.maker().isEmpty()) out.accept(Component.literal("Forged by " + d.maker() + (d.day() > 0 ? " on day " + d.day() : "")).withStyle(ChatFormatting.GRAY));
        else if (d.is(PickForged.OLD)) out.accept(Component.literal("Remade from an old pick").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal(way()).withStyle(ChatFormatting.DARK_AQUA));
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) out.accept(Component.literal("Blunt. Dress it at a War Anvil.").withStyle(ChatFormatting.RED));
        else if (d.tally().keen() > 0) out.accept(Component.literal("Keen for " + d.tally().keen() + " more blocks").withStyle(ChatFormatting.AQUA));
        for (Pick.Gift g : Pick.Gift.values()) if (Pick.has(stack, g)) out.accept(Component.literal(g.line).withStyle(ChatFormatting.DARK_PURPLE));
        PickForged.Tally t = d.tally();
        if (t.blocks() > 0) {
            String tally = String.format(Locale.ROOT, "%,d blocks, %,d ores", t.blocks(), t.ores());
            if (t.hearts() > 0) tally += String.format(Locale.ROOT, ", %,d lode %s", t.hearts(), t.hearts() == 1 ? "heart" : "hearts");
            out.accept(Component.literal(tally).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** One line on how this form digs. */
    private String way() {
        return switch (form) {
            case DELVER -> "Fastest in plain rock, and it hums toward metal";
            case BREAKER -> "Its heavy strike comes quicker and wears it less";
            case MATTOCK -> "One tool for stone, wood and earth";
            case SOUNDER -> "A wide seam, more ore, and it hears further";
        };
    }
}
