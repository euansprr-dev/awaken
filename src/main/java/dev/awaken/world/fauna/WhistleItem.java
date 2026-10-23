package dev.awaken.world.fauna;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Hunter's Whistle: use it and your mount comes (from up to {@value Mounts#RANGE} blocks, the quick way when it is
 * far); sneak and use it and your mount goes home to rest at your Hearth. When nothing comes, the action bar says why
 * (no mount yet, another world, out of earshot, no room here...). A ten second cooldown after an answer, two after a
 * miss.
 */
public final class WhistleItem extends Item {
    public WhistleItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Mounts.Result r = Mounts.whistle(sp, sp.isShiftKeyDown());
        sp.sendOverlayMessage(Component.literal(r.line()).withColor(r.ok() ? 0xBFF4D6 : 0xFFD27A));
        sp.getCooldowns().addCooldown(stack, r.ok() ? Mounts.COOLDOWN : Mounts.FAIL_COOLDOWN);
        return InteractionResult.SUCCESS_SERVER;
    }
}
