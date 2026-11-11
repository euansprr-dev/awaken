package dev.awaken.core.api;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IItemExtension;

import java.util.function.Consumer;

/**
 * Gear that is never destroyed (SPEC §68.1): an item of ours that implements this stops one use short of breaking and
 * is <b>worn out</b> there: it stays where it is, at half its strength, until it is mended. The item's class only names
 * the interface ({@code class X extends Item implements Lasting}); the rule is here, so every module's gear keeps it
 * alike. Worn out is read off the stack's own damage, so nothing new is saved and mending it by any means ends it.
 * <p>
 * It also carries a stack made under older numbers: one that holds its own count of uses, lower than its kind has
 * today, was given that count as a share over its kind's old one (the Armoury's quarter, a craft's tenth) and keeps the
 * same share over today's ({@link #formerUses}).
 */
public interface Lasting extends IItemExtension {
    /** What a worn-out weapon's blows and a worn-out piece's protection are worth. */
    float WORN_OUT_SHARE = 0.5F;
    /** Under this share of its uses a piece is nearly worn out (the Hearth says so once you are home). */
    float NEARLY = 0.15F;

    /** The uses this kind had before its numbers last changed, or 0 when they never did. */
    default int formerUses() {
        return 0;
    }

    @Override
    default int getMaxDamage(ItemStack stack) {
        int own = stack.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        Integer kind = stack.getPrototype().get(DataComponents.MAX_DAMAGE);
        int former = formerUses();
        if (kind == null || own <= 0 || own >= kind || former <= 0 || own < former) return own;
        // its own count under the old numbers: the same share over the kind's count today
        return Math.round(kind * (own / (float) former));
    }

    /** Wear stops one use short of breaking: the stack is never broken, so nothing removes it. */
    @Override
    default void setDamage(ItemStack stack, int damage) {
        stack.set(DataComponents.DAMAGE, Mth.clamp(damage, 0, Math.max(0, stack.getMaxDamage() - 1)));
    }

    /** Whether a stack of ours has no use left in it. */
    static boolean wornOut(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof Lasting && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Whether a stack of ours is under {@link #NEARLY} of its uses (and not yet worn out). */
    static boolean nearlyWornOut(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Lasting) || !stack.isDamageableItem() || wornOut(stack)) return false;
        int max = stack.getMaxDamage();
        return max > 0 && (max - stack.getDamageValue()) < max * NEARLY;
    }

    /** The tooltip's line for a worn-out stack (a weapon's or a piece of armour's), or nothing. */
    static void tooltip(ItemStack stack, boolean weapon, Consumer<Component> out) {
        if (wornOut(stack)) out.accept(Component.translatable(weapon ? "awaken.gear.worn_out.weapon" : "awaken.gear.worn_out.armour").withColor(0xE0A050));
    }

    /** The moment a stack wears out, said to its owner once: the sound of it giving, and what to do about it. */
    static void tell(ServerPlayer owner, ItemStack stack, boolean weapon) {
        owner.sendOverlayMessage(Component.translatable(weapon ? "awaken.gear.wore_out.weapon" : "awaken.gear.wore_out.armour", stack.getHoverName()));
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
    }
}
