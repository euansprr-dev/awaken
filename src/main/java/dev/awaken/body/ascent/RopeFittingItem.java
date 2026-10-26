package dev.awaken.body.ascent;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A fitting for the Tether (SPEC §28.5): an iron line, a gold reel or a Deepglass line. Used, it is fitted to your rope
 * (one line and one reel at a time; the one it replaces comes back to you). Without the rope there is nothing to fit
 * it to yet.
 */
public final class RopeFittingItem extends Item {
    public enum Kind {
        IRON_LINE, GOLD_REEL, DEEPGLASS_LINE;

        /** A line's RopeKit id (none for the reel). */
        int line() {
            return this == IRON_LINE ? RopeKit.LINE_IRON : this == DEEPGLASS_LINE ? RopeKit.LINE_DEEPGLASS : RopeKit.LINE_NONE;
        }

        boolean reel() {
            return this == GOLD_REEL;
        }

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public final Kind kind;

    public RopeFittingItem(Item.Properties p, Kind kind) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            ItemStack stack = player.getItemInHand(hand);
            if (!fit(sp, kind)) return InteractionResult.FAIL;
            if (!sp.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Fits {@code kind} to the player's Tether: saved, the old fitting of its kind handed back, the client told. False
     * (with a line why) when there is no rope yet or it is already fitted.
     */
    static boolean fit(ServerPlayer p, Kind kind) {
        if (!Ascent.has(p, Ascent.Tier.TETHER)) {
            p.sendOverlayMessage(Component.translatable("awaken.ascent.fit.no_rope").withStyle(ChatFormatting.GRAY));
            return false;
        }
        RopeKit kit = p.getData(AscentContent.ROPE_KIT);
        if (kind.reel() ? kit.gold() : kit.line() == kind.line()) {
            p.sendOverlayMessage(Component.translatable("awaken.ascent.fit.already", Component.translatable("item.awaken." + kind.key()))
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        Item back = kind.reel() ? null : lineItem(kit.line());
        RopeKit next = kind.reel() ? kit.withReel(RopeKit.REEL_GOLD) : kit.withLine(kind.line());
        p.setData(AscentContent.ROPE_KIT, next);
        if (back != null) {
            ItemStack old = new ItemStack(back);
            if (!p.getInventory().add(old)) p.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), AscentContent.RAPPEL_FIT.get(), SoundSource.PLAYERS, 0.9F,
                kind == Kind.GOLD_REEL ? 1.2F : kind == Kind.DEEPGLASS_LINE ? 0.85F : 1F);
        AscentServer.syncKit(p, true);
        dev.awaken.core.DirectorLog.log(p, "ascent", "fitted " + kind.key());
        return true;
    }

    /** The item a fitted line comes back as. */
    static Item lineItem(int line) {
        return line == RopeKit.LINE_IRON ? AscentContent.IRON_LINE.get() : line == RopeKit.LINE_DEEPGLASS ? AscentContent.DEEPGLASS_LINE.get() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.awaken." + kind.key() + ".what").withStyle(ChatFormatting.GRAY));
        RopeKit kit = RopeKit.client;
        boolean on = kind.reel() ? kit.gold() : kit.line() == kind.line();
        if (on) out.accept(Component.translatable("awaken.ascent.fit.on").withStyle(ChatFormatting.AQUA));
        else out.accept(Component.translatable("awaken.ascent.fit.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
