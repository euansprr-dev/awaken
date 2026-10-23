package dev.awaken.gear;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

/** The seven pieces of every Hunter gear tier: four armour pieces, a melee blade, a power focus and a mattock. */
public enum Piece {
    HELMET("helmet", ArmorType.HELMET),
    CHESTPLATE("chestplate", ArmorType.CHESTPLATE),
    LEGGINGS("leggings", ArmorType.LEGGINGS),
    BOOTS("boots", ArmorType.BOOTS),
    BLADE("blade", null),
    FOCUS("focus", null),
    MATTOCK("mattock", null);

    public final String id;
    public final @Nullable ArmorType armorType;

    Piece(String id, @Nullable ArmorType armorType) {
        this.id = id;
        this.armorType = armorType;
    }

    public boolean isArmour() {
        return armorType != null;
    }

    public @Nullable EquipmentSlot slot() {
        return armorType == null ? null : armorType.getSlot();
    }

    public static final Piece[] ARMOUR = {HELMET, CHESTPLATE, LEGGINGS, BOOTS};
}
