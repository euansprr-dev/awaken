package dev.awaken.anims.client.station;

import dev.awaken.anims.AnimsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * What working a station sounds like (SPEC §64.4): each moment one of Minecraft's own recordings ({@link AnimsSounds}),
 * played here on the client for your own hands and, quieter, for the hands of others you can see at work. A thing set
 * down sounds like what it is: a block makes its own place sound, metal a chain's, stone and gems a dry click, food and
 * cloth a soft one, anything else the tap of a thing laid on a board.
 */
final class StationSounds {
    private StationSounds() {}

    static void play(@Nullable BlockPos at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || at == null) return;
        mc.level.playLocalSound(at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    /** A stack set down at a station. {@code quiet}: someone else's hands. */
    static void lay(@Nullable BlockPos at, ItemStack stack, boolean quiet) {
        float k = quiet ? 0.55F : 1F, pitch = 0.94F + (float) ((System.nanoTime() >>> 20) % 13) * 0.01F;
        if (stack.getItem() instanceof BlockItem block) {
            // a block sounds like itself, quietly
            var type = block.getBlock().defaultBlockState().getSoundType();
            play(at, type.getPlaceSound(), 0.3F * k, type.getPitch() * 1.25F);
        } else if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.NUGGETS)) play(at, AnimsSounds.LAY_METAL.get(), k, pitch);
        else if (stack.is(Tags.Items.GEMS)) play(at, AnimsSounds.LAY_STONE.get(), k, pitch);
        else if (stack.is(Tags.Items.FOODS) || stack.is(ItemTags.WOOL)) play(at, AnimsSounds.LAY_SOFT.get(), k, pitch);
        else play(at, AnimsSounds.LAY.get(), k, pitch);
    }

    static void lift(@Nullable BlockPos at) {
        play(at, AnimsSounds.LIFT.get(), 1F, 1F);
    }

    static void result(@Nullable BlockPos at) {
        play(at, AnimsSounds.RESULT.get(), 1F, 1F);
    }

    static void take(@Nullable BlockPos at) {
        play(at, AnimsSounds.TAKE.get(), 1F, 1F);
    }

    static void fuel(@Nullable BlockPos at) {
        play(at, AnimsSounds.FUEL.get(), 1F, 1F);
        play(at, AnimsSounds.FLARE.get(), 1F, 1F);
    }

    static void kneel(@Nullable BlockPos at) {
        play(at, AnimsSounds.KNEEL.get(), 1F, 1F);
    }
}
