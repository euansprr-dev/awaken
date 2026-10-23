package dev.awaken.hearth.request;

import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** A Power Core Shard, an Anchor request reward. Four of them fuse into a Power Core fit for your rank. */
public class CoreShardItem extends Item {
    public static final int FUSE = 4;

    public CoreShardItem(Item.Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getCount() < FUSE) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.literal("Four shards fuse into a Power Core · you hold " + stack.getCount()));
            }
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        return fuse(sp, sl, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }

    /** Takes four shards from {@code stack} and hands over a Power Core. Returns false (and takes nothing) if none rolls. */
    static boolean fuse(ServerPlayer sp, ServerLevel level, ItemStack stack) {
        ItemStack core = PowerCoreHooks.roll(Progression.rank(sp), sp.getRandom());
        if (core.isEmpty()) {
            sp.sendOverlayMessage(Component.literal("The shards hum, but nothing answers. (Powers are off on this world.)"));
            return false;
        }
        stack.shrink(FUSE);
        Component name = core.getHoverName();
        if (!sp.addItem(core)) sp.drop(core, false, Prediction.SERVER_ONLY);
        level.playSound(null, sp.blockPosition(), RequestContent.SND_FUSE.get(), SoundSource.PLAYERS, 1f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.1, sp.getZ(), 18, 0.3, 0.4, 0.3, 0.06);
        Signal.toast(sp, Component.literal("The shards fuse · ").append(name));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Four fuse into a Power Core · use them").withColor(0xC9D2E3));
        out.accept(Component.literal("The Anchor pays them for requests").withColor(0x8A93A6));
    }
}
