package dev.awaken.gear;

import dev.awaken.core.api.Lasting;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

import java.util.List;

/**
 * How our gear wears (SPEC §68.1). Armour of ours wears by how hard it is hit: vanilla takes a quarter of a blow from
 * each piece and never less than a point, so a scratch costs what a crushing blow does, and a duel's many light blows
 * (6 to 10% of your health each) took a point a piece every time. Ours takes the quarter with no floor, the part of a
 * point settled by chance, so a fight's wear follows the damage taken and not the number of blows. A worn-out piece or
 * weapon ({@link Lasting}) is worth half: its attack damage, armour and toughness are halved while it has no use left.
 */
final class GearWear {
    private GearWear() {}

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Vanilla's share of a blow that a piece of armour takes as wear. */
    private static final float WEAR_SHARE = 0.25F;

    // the blow now being answered, as it will reach the armour: kept from the damage's own events, which run on the server
    // thread just before the armour's (the armour's event carries only the wear, already floored to a whole point)
    private static int blowOn = Integer.MIN_VALUE;
    private static long blowAt;
    private static float blow;

    /** Last of every listener: the blow as everyone has left it. */
    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        blowOn = e.getEntity().getId();
        blowAt = e.getEntity().level().getGameTime();
        blow = e.getAmount();
    }

    /** Last of every listener: what a raised guard or shield lets through is what reaches the armour. */
    static void onShieldBlock(LivingShieldBlockEvent e) {
        if (e.getEntity().getId() != blowOn) return;
        blow = Math.max(0F, e.getDamageContainer().getNewDamage() - (e.getBlocked() ? e.getBlockedDamage() : 0F));
    }

    static void onArmourHurt(ArmorHurtEvent e) {
        LivingEntity who = e.getEntity();
        boolean known = who.getId() == blowOn && who.level().getGameTime() == blowAt;
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack piece = e.getArmorItemStack(slot);
            if (piece.isEmpty() || !(piece.getItem() instanceof Lasting)) continue;
            float vanilla = e.getOriginalDamage(slot);
            if (vanilla <= 0F) continue; // not hurt by this source (fire on a fire-proof piece)
            float wear = known ? blow * WEAR_SHARE : vanilla;
            int whole = Mth.floor(wear);
            if (who.getRandom().nextFloat() < wear - whole) whole++;
            e.setNewDamage(slot, whole);
            if (whole > 0 && who instanceof ServerPlayer p && !Lasting.wornOut(piece) && piece.getDamageValue() + whole >= piece.getMaxDamage() - 1) {
                Lasting.tell(p, piece, false);
            }
        }
        blowOn = Integer.MIN_VALUE;
    }

    private static final List<Holder<Attribute>> HALVED = List.of(Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS);

    /** A worn-out weapon hits half as hard and a worn-out piece protects half as well, until it is mended. */
    static void onAttributes(ItemAttributeModifierEvent e) {
        if (!Lasting.wornOut(e.getItemStack())) return;
        for (ItemAttributeModifiers.Entry entry : List.copyOf(e.getModifiers())) {
            if (!HALVED.contains(entry.attribute()) || entry.modifier().amount() <= 0) continue;
            AttributeModifier m = entry.modifier();
            e.replaceModifier(entry.attribute(), new AttributeModifier(m.id(), m.amount() * Lasting.WORN_OUT_SHARE, m.operation()), entry.slot());
        }
    }
}
