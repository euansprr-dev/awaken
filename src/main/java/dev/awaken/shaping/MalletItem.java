package dev.awaken.shaping;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** The Mallet (SPEC §52.1). Right click does what it's set to; the other controls are read on the client and all use the mouse. */
public final class MalletItem extends Item {
    public MalletItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getHand() != InteractionHand.MAIN_HAND || ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getPlayer() instanceof ServerPlayer sp && ctx.getLevel() instanceof ServerLevel level) {
            Shaping.useOn(sp, level, ctx.getClickedPos(), ctx.getClickedFace(), ctx.getClickLocation(), ctx.getItemInHand());
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.SUCCESS;
    }
}
