package dev.awaken.hearth.room;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.block.ForgeBlock;
import dev.awaken.hearth.block.MeditationMatBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.echo.EchoRole;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * What makes a room something. {@link RoomType} lists which of these each room needs, the scanner counts them (in the
 * room's open cells and in its walls alike), and the Hearth guide shows them (label, icon, recipe), so the rule lives in
 * one place. {@link #SEAT} and {@link #TABLE} aren't single blocks: the scanner counts a stair with room to sit on it,
 * and a fence or wall post with a carpet, a pressure plate or a slab on top.
 */
public enum Furniture {
    BED("a bed", "bed", "the bed", null),
    MAT("a Meditation Mat", "mat", "the Meditation Mat", "meditation_mat"),
    ANVIL("an anvil", "anvil", "the anvil", null),
    FORGE("a Forge", "Forge", "the Forge", "forge"),
    STAND("an armour stand", "stand", "the armour stand", null),
    TROPHY("a Lord's trophy on a Trophy Mount", "trophy", "the trophy", "trophy_mount"),
    MACHINE("a Precursor machine", "machine", "the machine", null),
    /** (SPEC §43.4) the old role pad, a plain block now: no room wants it; the slot keeps saved rooms' counts in place. */
    PAD("a role pad", "pad", "the role pad", null),
    COOKER("a furnace or smoker", "stove", "the stove", null),
    CRAFTING("a crafting table", "table", "the crafting table", null),
    BARREL("a barrel", "barrel", "the barrel", null),
    LECTERN("a lectern", "lectern", "the lectern", null),
    BOOKSHELF("six bookshelves", "books", "the bookshelves", null),
    HAY("a hay bale", "hay", "the hay", null),
    GATE("a fence gate", "gate", "the fence gate", null),
    BELL("a bell", "bell", "the bell", null),
    GROWING("farmland or a planted pot", "soil", "the soil", null),
    SEAT("six seats (stairs to sit on)", "seats", "the seats", null),
    TABLE("a table (a post with a carpet, plate or slab on it)", "table", "the table", null),
    /** (SPEC §43.6) the Ore Shed's heart: a lode heart broken free and carried home. */
    LODE_HEART("a lode heart", "heart", "the lode heart", null);

    public static final int COUNT = values().length;

    /** "a Meditation Mat": the guide's checklist. */
    public final String label;
    /** "mat": the one-line Now tracker. */
    public final String shortName;
    /** "the Meditation Mat": reasons ("it sees the sky above the bed"). */
    public final String the;
    /** The AWAKEN recipe (path) the guide shows inline, or null for vanilla furniture. */
    public final @Nullable String recipe;

    Furniture(String label, String shortName, String the, @Nullable String recipe) {
        this.label = label;
        this.shortName = shortName;
        this.the = the;
        this.recipe = recipe;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** The pieces the guide explains when their space leaks (the others are everywhere outdoors: no nagging about them). */
    public boolean explained() {
        return ordinal() <= PAD.ordinal();
    }

    /** How many of this a room needs before it counts (six bookshelves, six seats; one of the rest). */
    public int needed() {
        return this == BOOKSHELF || this == SEAT ? 6 : 1;
    }

    /** The furniture a block is, for the scanner's seeds and counts (beds by their head, mounts with or without a trophy). */
    public static @Nullable Furniture of(BlockState s) {
        if (s.isAir()) return null;
        Block b = s.getBlock();
        if (b instanceof MeditationMatBlock) return MAT;
        if (b instanceof dev.awaken.delve.LodeHeartBlock) return LODE_HEART;
        if (b instanceof ForgeBlock) return FORGE;
        if (b instanceof TrophyMountBlock) return TROPHY;
        if (s.is(BlockTags.ANVIL)) return ANVIL;
        if (s.is(BlockTags.BEDS)) {
            return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD ? BED : null;
        }
        if (s.is(HearthContent.MACHINES)) return MACHINE;
        if (b == Blocks.FURNACE || b == Blocks.SMOKER) return COOKER;
        if (b == Blocks.CRAFTING_TABLE) return CRAFTING;
        if (s.is(Tags.BlockItems.BARRELS.block())) return BARREL;
        if (b instanceof LecternBlock) return LECTERN;
        if (s.is(Tags.BlockItems.BOOKSHELVES.block())) return BOOKSHELF;
        if (b instanceof HayBlock) return HAY;
        if (s.is(BlockTags.FENCE_GATES)) return GATE;
        if (b instanceof BellBlock) return BELL;
        if (b instanceof FarmlandBlock) return GROWING;
        if (b instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) return GROWING;
        return null;
    }

    /** The guide's icon. */
    public ItemStack icon() {
        return switch (this) {
            case BED -> new ItemStack(Items.BED.pick(DyeColor.RED));
            case MAT -> new ItemStack(HearthContent.MEDITATION_MAT_ITEM.get());
            case ANVIL -> new ItemStack(Items.ANVIL);
            case FORGE -> new ItemStack(HearthContent.FORGE_ITEM.get());
            case STAND -> new ItemStack(Items.ARMOR_STAND);
            case TROPHY -> new ItemStack(HearthContent.TROPHY_MOUNT_ITEM.get());
            case MACHINE -> machineIcon();
            case PAD -> new ItemStack(HearthContent.ROLE_PAD_ITEMS.get(EchoRole.GUARD).get());
            case COOKER -> new ItemStack(Items.SMOKER);
            case CRAFTING -> new ItemStack(Items.CRAFTING_TABLE);
            case BARREL -> new ItemStack(Items.BARREL);
            case LECTERN -> new ItemStack(Items.LECTERN);
            case BOOKSHELF -> new ItemStack(Items.BOOKSHELF);
            case HAY -> new ItemStack(Items.HAY_BLOCK);
            case GATE -> new ItemStack(Items.OAK_FENCE_GATE);
            case BELL -> new ItemStack(Items.BELL);
            case GROWING -> new ItemStack(Items.FLOWER_POT);
            case SEAT -> new ItemStack(Items.OAK_STAIRS);
            case TABLE -> new ItemStack(Items.OAK_FENCE);
            case LODE_HEART -> new ItemStack(dev.awaken.delve.DelveContent.HEART_ITEMS.get(dev.awaken.delve.Ore.IRON).get());
        };
    }

    private static ItemStack machineIcon() {
        for (Holder<Block> b : BuiltInRegistries.BLOCK.getTagOrEmpty(HearthContent.MACHINES)) {
            var item = b.value().asItem();
            if (item != Items.AIR) return new ItemStack(item);
        }
        return new ItemStack(Items.OBSERVER);
    }
}
