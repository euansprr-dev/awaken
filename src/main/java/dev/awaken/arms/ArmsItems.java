package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arms' weapons (SPEC §25): one item per {@link ArmsData} row (generated with their models by
 * tools/art/specs/arms_weapons.py), each the item vanilla's own of its kind is (a sword, an axe, a mace) with its
 * row's numbers, fighting with its row's style.
 */
public final class ArmsItems {
    private ArmsItems() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    private static final Map<String, ArmsData.Weapon> BY_ID = ArmsData.WEAPONS.stream()
            .collect(Collectors.toUnmodifiableMap(ArmsData.Weapon::id, Function.identity()));
    static final List<DeferredItem<Item>> ALL = ArmsData.WEAPONS.stream().map(ArmsItems::register).toList();
    /**
     * The Blueprints, one for each style but the Longsword: drawn at the Forge (ArmsForge), laid in the crafting grid
     * with a forged weapon's parts, and kept (each comes back out of the grid).
     */
    static final Map<String, DeferredItem<Item>> BLUEPRINTS = Archetypes.all().stream().map(Archetypes.Loaded::id)
            .filter(id -> !id.equals(Arms.LONGSWORD))
            .collect(Collectors.toUnmodifiableMap(id -> id, id -> ITEMS.registerItem(id + "_blueprint", BlueprintItem::new,
                    p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON))));
    private static final Map<String, DeferredItem<Item>> ITEM_BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(d -> d.getId().getPath(), Function.identity()));

    /**
     * Each style is the item vanilla's own of its kind is: an axe mines wood at its tier's speed and strips logs (the
     * axe preset), a maul is a mace (its smash, Density and Wind Burst), the rest are swords; all of the row's numbers.
     */
    private static DeferredItem<Item> register(ArmsData.Weapon w) {
        TagKey<Item> repair = TagKey.create(Registries.ITEM, Awaken.id(w.repair()));
        return switch (w.style()) {
            case Arms.AXE -> ITEMS.registerItem(w.id(), Item::new, p -> p.rarity(w.rarity())
                    .axe(new ToolMaterial(incorrectFor(w.tier()), w.durability(), mining(w.tier()), 0.0F, w.enchantability(), repair), w.damage(), w.speed()));
            case Arms.MAUL -> ITEMS.registerItem(w.id(), MaceItem::new, p -> p.rarity(w.rarity())
                    .durability(w.durability())
                    .component(DataComponents.TOOL, MaceItem.createToolProperties())
                    .repairable(repair)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, w.damage(), AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, w.speed(), AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())
                    .enchantable(w.enchantability())
                    .component(DataComponents.WEAPON, new Weapon(1)));
            default -> ITEMS.registerItem(w.id(), Item::new, p -> p.rarity(w.rarity())
                    .sword(new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, w.durability(), 1.0F, 0.0F, w.enchantability(), repair),
                            w.damage(), w.speed()));
        };
    }

    /** What an axe of a Hunter tier can't harvest, and how fast it cuts (stone, iron, diamond, netherite and on). */
    private static TagKey<net.minecraft.world.level.block.Block> incorrectFor(String tier) {
        return switch (tier) {
            case "E" -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case "D" -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            case "C" -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            default -> BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        };
    }

    private static float mining(String tier) {
        return switch (tier) {
            case "E" -> 4F;
            case "D" -> 6F;
            case "C" -> 8F;
            case "B" -> 9F;
            case "A" -> 10F;
            default -> 11F;
        };
    }

    /** One of our weapons by its id (a row of ArmsData), or null if there is none. */
    static @Nullable Item item(String id) {
        DeferredItem<Item> d = ITEM_BY_ID.get(id);
        return d == null ? null : d.get();
    }

    /** The row behind one of our weapons by its id, or null for any other item. */
    static ArmsData.@Nullable Weapon byId(Identifier id) {
        return id.getNamespace().equals(Awaken.MODID) ? BY_ID.get(id.getPath()) : null;
    }

    /** The row behind one of our weapons, or null for any other item. */
    public static ArmsData.@Nullable Weapon of(Item item) {
        return byId(BuiltInRegistries.ITEM.getKey(item));
    }

    /** A Blueprint: the crafting grid gives it back (it is a drawing, not a part). */
    static final class BlueprintItem extends Item {
        BlueprintItem(Item.Properties p) {
            super(p);
        }

        @Override
        public net.minecraft.world.item.@Nullable ItemStackTemplate getCraftingRemainder(net.minecraft.world.item.ItemInstance instance) {
            return new net.minecraft.world.item.ItemStackTemplate(this);
        }
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(ArmsItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (DeferredItem<Item> item : ALL) event.accept(item.get());
        BLUEPRINTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> event.accept(e.getValue().get()));
    }
}
