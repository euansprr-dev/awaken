package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.progression.Progression;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Raise (docs/arms-progression/PLAN.md §3.7, the answer to finding versus making): at a Forge, a weapon in the hand is
 * lifted to the next tier for that tier's parts (the same Fang, Parts, Binding, metal and shards the crafted weapon costs,
 * no Blueprint), keeping its Marks, its attunement and its name. A forged weapon becomes the next tier's weapon of its
 * style; a Lord-forged one keeps its own body and its Art, and gains a point of damage a tier (a Lord-forged weapon is
 * one point over its home rank's, and Raise keeps that gap). A found weapon is never above the crafted weapon of its tier.
 * Since the War Anvil (SPEC §46.8) it's done there as a reforging; {@link #lift} is the change itself, which keeps
 * everything written on the weapon.
 */
public final class ArmsRaise {
    private ArmsRaise() {}

    static final String RAISED = "awaken_raised";

    /** What raising the weapon in the main hand would do: the weapon, the tier it would reach, and what that costs. */
    public record Plan(ArmsData.Weapon weapon, int next, boolean lordForged, int raised, List<ArmsPayloads.NeedRow> needs, boolean reached) {}

    /** How many times a Lord-forged weapon has been raised. */
    public static int raised(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr(RAISED, 0);
    }

    static @Nullable Plan plan(ServerPlayer p) {
        return plan(p, p.getMainHandItem());
    }

    /** What raising this weapon would do for this player (their level, their pack), or null when it can't go higher. */
    public static @Nullable Plan plan(ServerPlayer p, ItemStack held) {
        if (held.isEmpty()) return null;
        ArmsData.Weapon w = ArmsItems.of(held.getItem());
        if (w == null) return null;
        boolean forged = LordForged.byWeapon(w.id()) != null;
        int raised = raised(held);
        int base = ArmsArmoury.tierIndex(w.tier());
        int next = forged ? base + raised + 1 : base + 1;
        if (base < 0 || next > 5) return null;
        List<ArmsPayloads.NeedRow> needs = new ArrayList<>();
        for (ArmsPayloads.NeedRow n : ArmsArmoury.needs(p, w.style(), next)) if (!n.id().endsWith("_blueprint")) needs.add(n);
        return new Plan(w, next, forged, raised, needs, Progression.profile(p).level() >= ArmsArmoury.LEVEL[next]);
    }

    /** Pays for the next tier's parts and lifts the weapon in the hand. True when it did. */
    static boolean raise(ServerPlayer p) {
        Plan plan = plan(p);
        if (plan == null || !plan.reached()) return false;
        for (ArmsPayloads.NeedRow n : plan.needs()) if (n.have() < n.need() && !p.hasInfiniteMaterials()) return false;
        ItemStack out = lift(p.getMainHandItem(), plan);
        if (out == null) return false;
        if (!p.hasInfiniteMaterials()) for (ArmsPayloads.NeedRow n : plan.needs()) ArmsArmoury.take(p, n);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, out);
        dev.awaken.signal.Signal.toast(p, Component.literal("RAISED · ").withColor(0xE2B450).append(out.getHoverName().copy().withColor(0xFFE3A0))
                .append(Component.literal(" · " + ArmsArmoury.TIERS[plan.next()] + " tier").withColor(0xC8C2B0)));
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.3F);
        counted(p);
        return true;
    }

    /** Counted wherever a weapon is raised (here, or reforged at a War Anvil). */
    public static void counted(ServerPlayer p) {
        Progression.count(p, "awaken:weapons_raised");
        Progression.markNewThing(p, "raise");
    }

    /**
     * The weapon lifted to its plan's tier (nothing paid here), or null. A Lord-forged one keeps its own body and Art and
     * gains a point of damage; a forged one becomes the next tier's of its style with everything written on it carried
     * over (Marks, attunement, name, Temper, enchantments, its story), its damage and uses set to the new weapon's own and
     * its Temper put back on. Either way its uses start fresh.
     */
    public static @Nullable ItemStack lift(ItemStack held, Plan plan) {
        ItemStack out;
        if (plan.lordForged()) {
            out = held.copy();
            CompoundTag tag = out.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putInt(RAISED, plan.raised() + 1);
            out.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            ItemAttributeModifiers mods = out.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            out.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(Awaken.id("raise_" + (plan.raised() + 1)), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
        } else {
            Item item = ArmsItems.item(ArmsArmoury.PREFIX[plan.next()] + "_" + plan.weapon().style());
            if (item == null) return null;
            out = held.transmuteCopy(item, 1);
            out.set(DataComponents.ATTRIBUTE_MODIFIERS, item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
            Integer uses = item.components().get(DataComponents.MAX_DAMAGE);
            if (uses != null) out.set(DataComponents.MAX_DAMAGE, uses);
            int temper = dev.awaken.hearth.Upgrades.temperOf(held);
            if (temper > 0) dev.awaken.hearth.Upgrades.applyTemper(out, temper);
        }
        out.setDamageValue(0);
        return out;
    }
}
