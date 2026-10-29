package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The armour sets' own items (SPEC §44.3): four pieces for every set that isn't a Hunter set, from the phase that builds
 * it ({@link ArmourSet#made}), and each such set's pattern. A Hunter set's pieces stay the {@code gear} module's items.
 */
public final class ArmourItems {
    private ArmourItems() {}

    /** A set's armour numbers: points a piece, toughness and knockback resistance a piece, speed and power damage a piece. */
    public record Numbers(int helm, int chest, int legs, int boots, float toughness, float knockback, double speed, double power) {
        public int defense(ArmorType type) {
            return switch (type) {
                case HELMET -> helm;
                case CHESTPLATE -> chest;
                case LEGGINGS -> legs;
                default -> boots;
            };
        }

        public int total() {
            return helm + chest + legs + boots;
        }
    }

    /** Strider speed a piece (multiplies the base), Channeler power damage a piece. */
    public static final double STRIDE = 0.05, CHANNEL = 0.03;

    /**
     * A set's numbers: its rank's Hunter numbers, then its archetype's (SPEC §44.3): Bulwark +1 point on the cuirass and
     * greaves, +0.5 toughness, +0.05 knockback; Strider -1 on the cuirass, +5% speed a piece; Channeler -1 on the
     * cuirass, +3% power damage a piece; Duelist as the rank; Lord-forged +1 on the cuirass. A Hunter set keeps its own.
     */
    public static Numbers numbers(ArmourSet set) {
        HunterTier.Armour a = set.tier().armour;
        int helm = a.helm(), chest = a.chest(), legs = a.legs(), boots = a.boots();
        float tough = a.toughness(), kb = a.knockback();
        double speed = 0, power = 0;
        if (set.hunter == null) {
            switch (set.archetype) {
                case BULWARK -> {
                    chest++;
                    legs++;
                    tough += 0.5F;
                    kb += 0.05F;
                }
                case STRIDER -> {
                    chest--;
                    speed = STRIDE;
                }
                case CHANNELER -> {
                    chest--;
                    power = CHANNEL;
                }
                case LORD -> chest++;
                default -> {}
            }
        }
        return new Numbers(helm, chest, legs, boots, tough, kb, speed, power);
    }

    static ArmorMaterial material(ArmourSet set) {
        Numbers n = numbers(set);
        HunterTier t = set.tier();
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        for (ArmorType type : ForgeSong.PIECES) defense.put(type, n.defense(type));
        // the rank's Hunter look stands in where a set's meshes are missing (SPEC §44, failure and recovery)
        return new ArmorMaterial(t.armour.durability(), defense, t.armour.enchantability(), t.armour.sound(), n.toughness(), n.knockback(),
                t.repairTag(Piece.HELMET), t.asset());
    }

    private static final Map<ArmourSet, Map<ArmorType, DeferredItem<ForgedArmourItem>>> PIECES = new EnumMap<>(ArmourSet.class);
    private static final Map<ArmourSet, DeferredItem<PatternItem>> PATTERNS = new EnumMap<>(ArmourSet.class);

    static {
        for (ArmourSet set : ArmourSet.values()) {
            if (set.hunter != null || !set.made()) continue;
            ArmorMaterial mat = material(set);
            Map<ArmorType, DeferredItem<ForgedArmourItem>> row = new EnumMap<>(ArmorType.class);
            for (ArmorType type : ForgeSong.PIECES) {
                row.put(type, ArmourContent.ITEMS.registerItem(set.id + "_" + suffix(type), p -> new ForgedArmourItem(p, set, type),
                        p -> properties(p, set, type, mat)));
            }
            PIECES.put(set, row);
            if (set.rank != dev.awaken.progression.Rank.E) {
                PATTERNS.put(set, ArmourContent.ITEMS.registerItem(set.id + "_pattern", p -> new PatternItem(p, set),
                        p -> p.stacksTo(16).rarity(set.archetype == Archetype.LORD ? net.minecraft.world.item.Rarity.EPIC : net.minecraft.world.item.Rarity.RARE)));
            }
        }
    }

    private static Item.Properties properties(Item.Properties p, ArmourSet set, ArmorType type, ArmorMaterial mat) {
        p = p.rarity(set.tier().rarity).humanoidArmor(mat, type);
        Numbers n = numbers(set);
        if (n.speed() > 0) {
            ItemAttributeModifiers mods = mat.createAttributes(type).withModifierAdded(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(Awaken.id("armour_stride." + type.getName()), n.speed(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                    EquipmentSlotGroup.bySlot(type.getSlot()));
            p = p.attributes(mods);
        }
        return p;
    }

    static String suffix(ArmorType type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            default -> "boots";
        };
    }

    /** A set's piece as an item: the Hunter sets' from {@code gear}, the others' own; null where it isn't made yet. */
    public static @Nullable Item item(ArmourSet set, ArmorType type) {
        if (set.hunter != null) return GearItems.get(set.hunter, Piece.ARMOUR[ForgeSong.slot(type)]);
        var row = PIECES.get(set);
        return row == null ? null : row.get(type).get();
    }

    public static @Nullable Item pattern(ArmourSet set) {
        var d = PATTERNS.get(set);
        return d == null ? null : d.get();
    }

    /** The sets' own pieces' and patterns' ids (for the item sources ledger). */
    static String[] pieceIds() {
        return PIECES.values().stream().flatMap(m -> m.values().stream()).map(d -> d.getId().getPath()).toArray(String[]::new);
    }

    static String[] patternIds() {
        return PATTERNS.values().stream().map(d -> d.getId().getPath()).toArray(String[]::new);
    }

    static void init(IEventBus modBus) {
        modBus.addListener(ArmourItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        event.accept(ArmourContent.STAND_ITEM.get());
        for (ArmourSet set : ArmourSet.values()) {
            var row = PIECES.get(set);
            if (row != null) for (ArmorType type : ForgeSong.PIECES) event.accept(row.get(type).get());
            var pat = PATTERNS.get(set);
            if (pat != null) event.accept(pat.get());
        }
    }

    static ItemStack stack(ArmourSet set, ArmorType type) {
        Item it = item(set, type);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }
}
