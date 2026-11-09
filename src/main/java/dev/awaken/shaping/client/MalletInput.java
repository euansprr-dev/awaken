package dev.awaken.shaping.client;

import dev.awaken.shaping.MalletData;
import dev.awaken.shaping.ShapingContent;
import dev.awaken.shaping.ShapingPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Mallet's controls (SPEC §52.1). All of them are on the mouse and only with the Mallet in your main hand, so every
 * key keeps doing what it did: your powers stay on Z X C V and R with the Mallet out.
 * <ul>
 * <li>Right click: what the Mallet is set to.</li>
 * <li>Sneak and scroll: change what right click does.</li>
 * <li>Sneak and right click on a block: the Looks ring.</li>
 * <li>Middle click: copy that block's look.</li>
 * <li>Sneak and left click: undo.</li>
 * </ul>
 */
final class MalletInput {
    private MalletInput() {}

    private static boolean useReady = true, attackReady = true;
    private static @Nullable BlockPos lastPainted;

    static boolean held(Minecraft mc) {
        return mc.player != null && mc.player.getMainHandItem().is(ShapingContent.MALLET.get());
    }

    /** The button that clicked the ring is still down as it closes; it mustn't reopen it or undo. Wait for it to come up. */
    static void ringClosed() {
        useReady = false;
        attackReady = false;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyUse.isDown()) {
            useReady = true;
            lastPainted = null;
        }
        if (!mc.options.keyAttack.isDown()) attackReady = true;
    }

    private static @Nullable BlockHitResult block(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !held(mc)) return;
        BlockHitResult hit = block(mc);
        boolean sneak = p.isShiftKeyDown();
        if (e.isAttack()) {
            if (!sneak) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            if (attackReady) {
                attackReady = false;
                ClientPacketDistributor.sendToServer(new ShapingPayloads.Undo());
            }
            return;
        }
        if (e.isPickBlock()) {
            if (hit == null) return;
            e.setCanceled(true);
            e.setSwingHand(false);
            ClientPacketDistributor.sendToServer(new ShapingPayloads.Pick(hit.getBlockPos()));
            return;
        }
        if (!e.isUseItem()) return;
        if (sneak) {
            e.setCanceled(true);
            e.setSwingHand(false);
            if (useReady && hit != null) {
                useReady = false;
                LooksScreen.open(mc, hit.getBlockPos());
            }
            return;
        }
        // one knock a press; a look may be dragged across blocks, one each
        if (MalletData.of(p.getMainHandItem()).mode() == MalletData.Mode.KNOCK) {
            if (useReady) {
                useReady = false;
                return;
            }
        } else if (hit != null && !hit.getBlockPos().equals(lastPainted)) {
            lastPainted = hit.getBlockPos().immutable();
            useReady = false;
            return;
        }
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !held(mc) || !mc.player.isShiftKeyDown() || e.getScrollDeltaY() == 0) return;
        e.setCanceled(true);
        ClientPacketDistributor.sendToServer(new ShapingPayloads.TurnMode(e.getScrollDeltaY() > 0 ? 1 : -1));
    }
}
