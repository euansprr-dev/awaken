package dev.awaken.armour;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Armour drawn as sculpted meshes on the body's bones (docs/armour/PLAN.md). Today only the look probe: Lordscale's four
 * pieces with netherite's numbers, worn like any armour, drawn by {@code armour.client.ArmourLayer} instead of
 * vanilla's flat layers (their equippable names no equipment asset, so vanilla draws nothing for them).
 */
public final class ArmourModule {
    private ArmourModule() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /** The probe set, by piece. */
    public static final Map<ArmorType, DeferredItem<Item>> PROBE = new EnumMap<>(ArmorType.class);

    static {
        ArmorMaterial material = ArmorMaterials.NETHERITE;
        for (ArmorType type : new ArmorType[]{ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS}) {
            PROBE.put(type, ITEMS.registerItem("probe_lordscale_" + type.getName(), Item::new, p -> p.humanoidArmor(material, type)
                    .fireResistant()
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot()).setEquipSound(material.equipSound()).build())));
        }
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
