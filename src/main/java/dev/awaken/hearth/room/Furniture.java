package dev.awaken.hearth.room;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.block.ForgeBlock;
import dev.awaken.hearth.block.MeditationMatBlock;
import dev.awaken.hearth.block.RolePadBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.echo.EchoRole;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * What makes a room something. {@link RoomType} lists which of these each room needs, the scanner counts them, and the
 * Hearth guide shows them (label, icon, recipe), so the rule lives in one place.
 */
public enum Furniture {
    BED("a bed", "bed", "the bed", null),
    MAT("a Meditation Mat", "mat", "the Meditation Mat", "meditation_mat"),
    ANVIL("an anvil", "anvil", "the anvil", null),
    FORGE("a Forge", "Forge", "the Forge", "forge"),
    STAND("an armour stand", "stand", "the armour stand", null),
    TROPHY("a Lord's trophy on a Trophy Mount", "trophy", "the trophy", "trophy_mount"),
    MACHINE("a Precursor machine", "machine", "the machine", null),
    PAD("a role pad", "pad", "the role pad", "role_pad_guard");

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

    /** The furniture a block is, for the scanner's seeds (beds by their head, mounts with or without a trophy). */
    public static @Nullable Furniture of(BlockState s) {
        if (s.isAir()) return null;
        Block b = s.getBlock();
        if (b instanceof MeditationMatBlock) return MAT;
        if (b instanceof ForgeBlock) return FORGE;
        if (b instanceof RolePadBlock) return PAD;
        if (b instanceof TrophyMountBlock) return TROPHY;
        if (s.is(BlockTags.ANVIL)) return ANVIL;
        if (s.is(BlockTags.BEDS)) {
            return s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD ? BED : null;
        }
        if (s.is(HearthContent.MACHINES)) return MACHINE;
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
