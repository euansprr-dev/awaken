package dev.awaken.storage;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What sort each item is (SPEC §56). Resolved once when tags load, so a lookup is one hash read and nothing is saved:
 * changing the list can't touch a world.
 */
public final class Sorts {
    private Sorts() {}

    /** An item's sort, and where it comes in the one order everything is laid out in. */
    public record Place(Sort sort, int rank) {}

    private static final Place UNKNOWN = new Place(Sort.ODD, Integer.MAX_VALUE);
    /** Null until first asked for after tags load: components aren't bound yet when the tag event fires. */
    private static volatile @Nullable Map<Item, Place> places;

    public static Sort of(ItemStack s) {
        return place(s.getItem()).sort();
    }

    public static Sort of(Item item) {
        return place(item).sort();
    }

    public static int rank(Item item) {
        return place(item).rank();
    }

    private static Place place(Item item) {
        Map<Item, Place> m = places;
        if (m == null) places = m = build();
        return m.getOrDefault(item, UNKNOWN);
    }

    /** Every item's place. */
    private static Map<Item, Place> build() {
        record Row(Item item, Sort sort, int family, String path) {}
        List<Row> rows = new ArrayList<>(BuiltInRegistries.ITEM.size());
        for (Item item : BuiltInRegistries.ITEM) {
            Sort sort = classify(item);
            rows.add(new Row(item, sort, family(item, sort), BuiltInRegistries.ITEM.getKey(item).getPath()));
        }
        rows.sort(Comparator.comparing(Row::sort).thenComparingInt(Row::family).thenComparing(Row::path));
        Map<Item, Place> out = new Reference2ObjectOpenHashMap<>(rows.size());
        for (int i = 0; i < rows.size(); i++) out.put(rows.get(i).item(), new Place(rows.get(i).sort(), i));
        return out;
    }

    static void onTags(TagsUpdatedEvent e) {
        if (e.shouldUpdateStaticData()) places = null;
    }

    /** The rules, first match wins. A tag beats all of them. */
    static Sort classify(Item item) {
        ItemStack s = new ItemStack(item);
        for (Sort t : Sort.values()) if (t != Sort.ODD && s.is(t.tag)) return t;
        if (item instanceof SpawnEggItem) return Sort.ODD;
        Block block = item instanceof BlockItem b ? b.getBlock() : null;
        if (isGear(s, item, block)) return Sort.GEAR;
        if (s.has(DataComponents.POTION_CONTENTS)) return Sort.BREWS;
        if (s.has(DataComponents.FOOD)) return Sort.FOOD;
        if (s.is(Tags.Items.ORES) || s.is(Tags.Items.RAW_MATERIALS) || s.is(Tags.Items.INGOTS) || s.is(Tags.Items.NUGGETS) || s.is(ItemTags.METAL_NUGGETS)
                || s.is(Tags.Items.STORAGE_BLOCKS) || s.is(ItemTags.COALS) || s.is(Tags.Items.DUSTS_REDSTONE)) return Sort.METAL;
        if (s.is(Tags.Items.GEMS) || s.is(Tags.Items.CLUSTERS) || s.is(Tags.Items.BUDS)) return Sort.GEMS;
        if (s.is(Tags.Items.SEEDS) || s.is(Tags.Items.CROPS) || s.is(Tags.BlockItems.FLOWERS.item()) || s.is(Tags.Items.MUSHROOMS) || s.is(ItemTags.SAPLINGS)
                || s.is(ItemTags.LEAVES) || s.is(ItemTags.MOSS_BLOCKS)) return Sort.PLANTS;
        if (s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS) || s.is(ItemTags.WOODEN_SLABS) || s.is(ItemTags.WOODEN_STAIRS) || s.is(ItemTags.WOODEN_FENCES)
                || s.is(ItemTags.FENCE_GATES) || s.is(ItemTags.WOODEN_DOORS) || s.is(ItemTags.WOODEN_TRAPDOORS) || s.is(ItemTags.WOODEN_BUTTONS)
                || s.is(ItemTags.WOODEN_PRESSURE_PLATES) || s.is(ItemTags.SIGNS) || s.is(ItemTags.HANGING_SIGNS) || s.is(ItemTags.WOODEN_SHELVES)
                || s.is(ItemTags.BAMBOO_BLOCKS) || s.is(Tags.Items.RODS_WOODEN)) return Sort.WOOD;
        if (s.is(Tags.Items.BONES) || s.is(Tags.Items.STRINGS) || s.is(Tags.Items.LEATHERS) || s.is(Tags.Items.FEATHERS) || s.is(Tags.Items.SLIME_BALLS)
                || s.is(Tags.Items.ENDER_PEARLS) || s.is(Tags.Items.GUNPOWDERS) || s.is(Tags.Items.RODS_BLAZE) || s.is(Tags.Items.RODS_BREEZE)
                || s.is(Tags.Items.EGGS) || s.is(ItemTags.SKULLS)) return Sort.DROPS;
        if (isDressed(s)) return Sort.BUILDING;
        if (block != null && isStoneOrEarth(s, block)) return Sort.STONE;
        if (block != null) return Sort.BUILDING;
        if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Awaken.MODID)) return Sort.GATE;
        return Sort.ODD;
    }

    private static boolean isGear(ItemStack s, Item item, Block block) {
        if (s.is(ItemTags.ARROWS) || s.isDamageableItem() || s.is(Tags.Items.TOOLS) || s.is(Tags.Items.BUCKETS) || item instanceof BucketItem) return true;
        if (s.is(ItemTags.BOATS) || s.is(ItemTags.CHEST_BOATS) || item instanceof MinecartItem) return true;
        // carpets and carved pumpkins can be worn too, but nobody files them with their armour
        return block == null && s.has(DataComponents.EQUIPPABLE);
    }

    /** Blocks made to look nice: dug-up stone rules would otherwise take them. */
    private static boolean isDressed(ItemStack s) {
        return s.is(ItemTags.TERRACOTTA) || s.is(Tags.Items.GLAZED_TERRACOTTAS) || s.is(ItemTags.GLAZED_TERRACOTTA) || s.is(Tags.Items.CONCRETES)
                || s.is(ItemTags.CONCRETE) || s.is(ItemTags.CONCRETE_STAIRS) || s.is(ItemTags.CONCRETE_SLABS) || s.is(ItemTags.CONCRETE_POWDERS) || s.is(Tags.Items.CONCRETE_POWDERS) || s.is(ItemTags.WOOL)
                || s.is(ItemTags.WOOL_CARPETS) || s.is(Tags.Items.GLASS_BLOCKS) || s.is(Tags.Items.GLASS_PANES) || s.is(ItemTags.COPPER);
    }

    private static boolean isStoneOrEarth(ItemStack s, Block block) {
        if (s.is(Tags.Items.COBBLESTONES) || s.is(Tags.Items.STONES) || s.is(Tags.Items.SANDS) || s.is(Tags.Items.GRAVELS) || s.is(Tags.Items.NETHERRACKS)
                || s.is(Tags.Items.END_STONES) || s.is(Tags.Items.OBSIDIANS) || s.is(Tags.Items.SANDSTONE_BLOCKS) || s.is(Tags.Items.SANDSTONE_SLABS)
                || s.is(Tags.Items.SANDSTONE_STAIRS) || s.is(ItemTags.STONE_BRICKS) || s.is(Tags.Items.BRICKS) || s.is(ItemTags.WALLS)) return true;
        BlockState b = block.defaultBlockState();
        if (b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(BlockTags.BASE_STONE_NETHER) || b.is(BlockTags.DIRT) || b.is(BlockTags.MUD)) return true;
        if (b.is(BlockTags.MINEABLE_WITH_SHOVEL) && b.isSolid()) return true;
        if (!b.is(BlockTags.MINEABLE_WITH_PICKAXE) || block instanceof EntityBlock) return false; // a furnace is stone but isn't "stone"
        if (block instanceof StairBlock || block instanceof SlabBlock || block instanceof WallBlock) return isStoneSound(b.getSoundType());
        return b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) && isStoneSound(b.getSoundType());
    }

    private static boolean isStoneSound(SoundType t) {
        return t == SoundType.STONE || t == SoundType.DEEPSLATE || t == SoundType.DEEPSLATE_BRICKS || t == SoundType.DEEPSLATE_TILES || t == SoundType.POLISHED_DEEPSLATE
                || t == SoundType.TUFF || t == SoundType.TUFF_BRICKS || t == SoundType.POLISHED_TUFF || t == SoundType.NETHERRACK || t == SoundType.NETHER_BRICKS
                || t == SoundType.BASALT || t == SoundType.CALCITE || t == SoundType.DRIPSTONE_BLOCK || t == SoundType.GILDED_BLACKSTONE || t == SoundType.MUD_BRICKS
                || t == SoundType.PACKED_MUD;
    }

    /** Within a sort: like things together (swords by swords, stairs by stairs), so a colour doesn't split a material. */
    private static int family(Item item, Sort sort) {
        ItemStack s = new ItemStack(item);
        Block block = item instanceof BlockItem b ? b.getBlock() : null;
        return switch (sort) {
            case STONE -> block == null ? 5 : block instanceof StairBlock ? 2 : block instanceof SlabBlock ? 1 : block instanceof WallBlock ? 3 : 0;
            case WOOD -> s.is(ItemTags.LOGS) ? 0 : s.is(ItemTags.PLANKS) ? 1 : block instanceof SlabBlock ? 2 : block instanceof StairBlock ? 3
                    : s.is(ItemTags.WOODEN_FENCES) || s.is(ItemTags.FENCE_GATES) ? 4 : s.is(ItemTags.WOODEN_DOORS) || s.is(ItemTags.WOODEN_TRAPDOORS) ? 5
                    : s.is(ItemTags.SIGNS) || s.is(ItemTags.HANGING_SIGNS) ? 6 : 7;
            case BUILDING -> s.is(Tags.Items.DYES) ? 8 : s.is(Tags.Items.GLASS_BLOCKS) || s.is(Tags.Items.GLASS_PANES) ? 1 : s.is(ItemTags.WOOL) ? 2
                    : s.is(ItemTags.WOOL_CARPETS) ? 3 : s.is(ItemTags.CONCRETE) || s.is(ItemTags.CONCRETE_POWDERS) ? 4 : s.is(ItemTags.TERRACOTTA)
                    || s.is(ItemTags.GLAZED_TERRACOTTA) ? 5 : s.is(ItemTags.CANDLES) ? 6 : s.is(ItemTags.BEDS) ? 7 : 0;
            case METAL -> s.is(Tags.Items.ORES) || s.is(Tags.Items.RAW_MATERIALS) ? 0 : s.is(Tags.Items.INGOTS) ? 1 : s.is(Tags.Items.NUGGETS) ? 2
                    : s.is(Tags.Items.STORAGE_BLOCKS) ? 3 : 4;
            case GEAR -> s.is(ItemTags.SWORDS) ? 0 : s.is(ItemTags.AXES) ? 1 : s.is(ItemTags.PICKAXES) ? 2 : s.is(ItemTags.SHOVELS) ? 3 : s.is(ItemTags.HOES) ? 4
                    : s.is(ItemTags.SPEARS) ? 5 : s.is(Tags.Items.RANGED_WEAPON_TOOLS) ? 6 : s.is(ItemTags.ARROWS) ? 7 : s.is(Tags.Items.TOOLS_SHIELD) ? 8
                    : s.is(ItemTags.HEAD_ARMOR) ? 9 : s.is(ItemTags.CHEST_ARMOR) ? 10 : s.is(ItemTags.LEG_ARMOR) ? 11 : s.is(ItemTags.FOOT_ARMOR) ? 12
                    : s.is(Tags.Items.BUCKETS) ? 13 : s.is(ItemTags.BOATS) || s.is(ItemTags.CHEST_BOATS) ? 14 : 15;
            default -> 0;
        };
    }
}
