package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The 28 Hunter gear items (tiers E to B, seven pieces each). Behaviour lives in {@link SetBonus} and {@link GearCombat}. */
public final class GearItems {
    private GearItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    private static final Map<HunterTier, Map<Piece, DeferredItem<HunterGearItem>>> TABLE = new EnumMap<>(HunterTier.class);
    private static final Map<HunterTier, ArmorMaterial> MATERIALS = new EnumMap<>(HunterTier.class);

    static {
        for (HunterTier tier : HunterTier.values()) {
            MATERIALS.put(tier, tier.armorMaterial());
            Map<Piece, DeferredItem<HunterGearItem>> row = new EnumMap<>(Piece.class);
            for (Piece piece : Piece.values()) {
                row.put(piece, ITEMS.registerItem(tier.itemId(piece), p -> new HunterGearItem(p, tier, piece), p -> properties(p, tier, piece)));
            }
            TABLE.put(tier, row);
        }
    }

    public static Item get(HunterTier tier, Piece piece) {
        return TABLE.get(tier).get(piece).get();
    }

    public static ItemStack stack(HunterTier tier, Piece piece) {
        return new ItemStack(get(tier, piece));
    }

    public static @Nullable HunterGearItem gear(ItemStack stack) {
        return stack.getItem() instanceof HunterGearItem g ? g : null;
    }

    public static List<DeferredItem<HunterGearItem>> all() {
        return TABLE.values().stream().flatMap(m -> m.values().stream()).toList();
    }

    private static Item.Properties properties(Item.Properties p, HunterTier tier, Piece piece) {
        p = p.rarity(tier.rarity);
        return switch (piece) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> p.humanoidArmor(MATERIALS.get(tier), piece.armorType);
            case BLADE -> p.sword(new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, tier.blade.durability(), 1.0f, 0.0f,
                    tier.armour.enchantability(), tier.repairTag(piece)), tier.blade.damage(), tier.blade.speed());
            case FOCUS -> p.durability(tier.focus.durability())
                    .repairable(tier.repairTag(piece))
                    .enchantable(tier.armour.enchantability())
                    .attributes(melee(tier.focus.melee(), -2.8f))
                    .component(DataComponents.WEAPON, new Weapon(1));
            case MATTOCK -> mattock(p, tier);
        };
    }

    /** One head for stone, wood and earth, at the tier's speed; the tier's vanilla pickaxe decides what drops. */
    private static Item.Properties mattock(Item.Properties p, HunterTier tier) {
        HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
        float speed = tier.mattock.speed();
        Tool tool = new Tool(List.of(
                Tool.Rule.deniesDrops(blocks.getOrThrow(tier.mattock.incorrect())),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), speed),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_AXE), speed),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_SHOVEL), speed)),
                1.0f, 1, true);
        return p.durability(tier.mattock.durability())
                .repairable(tier.repairTag(Piece.MATTOCK))
                .enchantable(tier.armour.enchantability())
                .component(DataComponents.TOOL, tool)
                .attributes(melee(tier.mattock.melee(), -2.9f))
                .component(DataComponents.WEAPON, new Weapon(2, 0.0f));
    }

    private static ItemAttributeModifiers melee(float damage, float speed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(GearItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) event.accept(get(tier, piece));
        }
    }
}
