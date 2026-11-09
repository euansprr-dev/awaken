package dev.awaken.riding.client;

import dev.awaken.core.client.InputLock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Four seconds in your partner's shadow, from the inside (SPEC §55): you're held still, you see through their eyes under
 * a dark veil with a countdown, and your body and name are gone for everyone until you come back.
 */
final class FoldView {
    private FoldView() {}

    private static final Object HOLD = new Object();
    /** The failsafe on the input hold: well past a fold's four seconds. */
    private static final int HOLD_TICKS = 20 * 8;
    private static final int VEIL_EDGE = 0xE0060409, VEIL_MIDDLE = 0x60060409;

    private static boolean active;

    private static ClientRiding.@Nullable Folding mine() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : ClientRiding.FOLDS.get(p.getUUID());
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientRiding.tick();
        ClientRiding.Folding f = mine();
        if (f == null || mc.level == null) {
            if (active) stop();
            return;
        }
        if (!active) {
            active = true;
            InputLock.hold(HOLD, HOLD_TICKS);
        }
        Player anchor = mc.level.getPlayerByUUID(f.anchor());
        Entity cam = mc.getCameraEntity();
        if (anchor != null && cam != anchor && cam == mc.player) mc.setCameraEntity(anchor);
    }

    private static void stop() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        InputLock.release(HOLD);
        if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
    }

    static void clear() {
        if (active) stop();
    }

    static void hand(RenderHandEvent e) {
        if (active) e.setCanceled(true);
    }

    static void outline(ExtractBlockOutlineRenderStateEvent e) {
        if (active) e.setCanceled(true);
    }

    static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!active) return;
        e.setSwingHand(false);
        e.setCanceled(true);
    }

    /** A folded rider's body is gone for everyone, not only for them. */
    static void player(RenderPlayerEvent.Pre<?> e) {
        if (ClientRiding.FOLDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(e.getRenderState().id) instanceof Player p && ClientRiding.folding(p.getUUID())) e.setCanceled(true);
    }

    static void nameTag(RenderNameTagEvent.CanRender e) {
        if (!ClientRiding.FOLDS.isEmpty() && e.getEntity() instanceof Player p && ClientRiding.folding(p.getUUID())) e.setCanRender(TriState.FALSE);
    }

    static void veil(GuiGraphicsExtractor g, DeltaTracker dt) {
        ClientRiding.Folding f = mine();
        Minecraft mc = Minecraft.getInstance();
        if (f == null || mc.level == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fillGradient(0, 0, w, h / 2, VEIL_EDGE, VEIL_MIDDLE);
        g.fillGradient(0, h / 2, w, h, VEIL_MIDDLE, VEIL_EDGE);
        Font font = mc.font;
        int seconds = Mth.ceil(Math.max(0, f.end() - mc.level.getGameTime()) / 20F);
        g.centeredText(font, "You're safe in " + ClientRiding.nameOf(f.anchor()) + "'s shadow.", w / 2, h / 2 - 14, 0xFFF3E4D4);
        g.centeredText(font, seconds > 0 ? "Back in " + seconds + "." : "Back now.", w / 2, h / 2, 0xFFF0A48C);
    }
}
