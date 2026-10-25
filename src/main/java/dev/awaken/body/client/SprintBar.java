package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.BodyRules;
import dev.awaken.body.client.feel.FeelConfig;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The tactical sprint's line (SPEC §16.3): a thin Holo-blue line in the free gap just above the hotbar (Aether's gold
 * one sits above the XP bar, so they never meet). Drawn only when it matters: it drains while the sprint runs, fills
 * from the middle as it recharges, gives one soft flash with a tick of light when it's ready, then fades. At rest,
 * ready, nothing. Reduced motion: no flash, just the fade.
 */
final class SprintBar {
    private SprintBar() {}

    private static final int BLUE = 0x7FC3FF;
    private static float shown;
    private static int readyAt = -100;
    private static boolean charging;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        int now = ClientMoves.ticks;
        float fill;
        boolean busy;
        if (ClientMoves.tac) {
            fill = 1 - Math.min(1, (now - ClientMoves.tacStart) / (float) BodyRules.TAC_TICKS);
            busy = true;
        } else if (now < ClientMoves.tacReadyAt) {
            int total = BodyRules.tacRecharge(mc.player);
            fill = 1 - Math.min(1, (ClientMoves.tacReadyAt - now) / (float) total);
            busy = true;
        } else {
            fill = 1;
            busy = false;
        }
        // the moment it's ready again: one flash and a tick of light, then it goes
        if (charging && !busy) readyAt = now;
        charging = busy;
        boolean flashing = now - readyAt < 14;
        shown += ((busy || flashing ? 1F : 0F) - shown) * (busy ? 0.25F : 0.08F);
        if (shown < 0.02F) return;
        int cx = g.guiWidth() / 2, half = Math.round(91 * fill), y = g.guiHeight() - 24;
        float a = shown;
        g.fill(cx - 91, y, cx + 91, y + 2, Glass.a(0x0E1E2E, 0.6F * a));
        if (half > 0) {
            HudLook.glow(g, cx - half - 3, y - 2, half * 2 + 6, 6, BLUE, 0.55F * a);
            g.fill(cx - half, y, cx + half, y + 2, Glass.a(BLUE, a));
        }
        if (flashing && !FeelConfig.reduced()) {
            float f = 1 - (now - readyAt + dt.getGameTimeDeltaPartialTick(false)) / 14F;
            HudLook.glow(g, cx - 97, y - 4, 194, 10, 0xD8F0FF, 0.7F * Math.max(0, f));
        }
    }
}
