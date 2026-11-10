package dev.awaken.finds.verb;

import dev.awaken.finds.kit.PropBlock;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** What's in a hand, as the finds read it (SPEC §58.5): a flame, water, a blade. And the two ways a block can be lit. */
public final class Hands {
    private Hands() {}

    public static boolean flame(ItemStack s) {
        return s.is(Items.FLINT_AND_STEEL) || s.is(Items.FIRE_CHARGE) || s.is(Items.TORCH) || s.is(Items.SOUL_TORCH) || s.is(Items.LAVA_BUCKET) || s.is(Items.BLAZE_POWDER)
                || s.is(Items.CAMPFIRE) || s.is(Items.LANTERN);
    }

    public static boolean water(ItemStack s) {
        return s.is(Items.WATER_BUCKET) || s.is(Items.WET_SPONGE);
    }

    public static boolean cold(ItemStack s) {
        return s.is(Items.SNOWBALL) || s.is(Items.POWDER_SNOW_BUCKET) || s.is(Items.ICE) || s.is(Items.PACKED_ICE) || s.is(Items.BLUE_ICE) || s.is(Items.SNOW_BLOCK);
    }

    public static boolean blade(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.SHEARS);
    }

    public static boolean pick(ItemStack s) {
        return s.is(ItemTags.PICKAXES);
    }

    public static boolean rings(ItemStack s) {
        return s.is(Items.BELL) || s.is(Items.GOAT_HORN) || s.is(Items.NOTE_BLOCK);
    }

    /** Whether a block that can be lit is. A prop is lit in its odd states; a vanilla block by its own flag. */
    public static boolean lit(BlockState s) {
        if (s.getBlock() instanceof PropBlock) return (s.getValue(PropBlock.STATE) & 1) == 1;
        return s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT);
    }

    public static BlockState lit(BlockState s, boolean on) {
        if (s.getBlock() instanceof PropBlock) return s.setValue(PropBlock.STATE, (s.getValue(PropBlock.STATE) & 2) | (on ? 1 : 0));
        return s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, on) : s;
    }
}
