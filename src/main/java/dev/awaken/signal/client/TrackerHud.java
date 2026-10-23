package dev.awaken.signal.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The Now tracker: one quiet line in the top-left corner with the best thing you could do next (and a compass when it
 * has a place), plus "1 decision waiting · K" when a choice card folded away. Shows nothing when there is nothing.
 */
final class TrackerHud {
    private TrackerHud() {}

    static boolean enabled() {
        try {
            return AwakenConfig.Client.NOW_TRACKER.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static boolean v2() {
        try {
            return AwakenConfig.Client.SYSTEM_STYLE.get() == AwakenConfig.Client.SystemStyle.V2;
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Inside the Descent its own HUD owns the corner, so the tracker sits below it. */
    private static boolean lowered(Minecraft mc) {
        Identifier dim = mc.player.level().dimension().identifier();
        return dim.getNamespace().equals(Awaken.MODID) && (dim.getPath().startsWith("stratum_") || dim.getPath().equals("endless"));
    }

    private record Lines(String line1, String compass, String line2, int colour1, Rect rect) {}

    private static Lines cached;
    private static int cacheKey;

    private static Lines build(Minecraft mc, Font font, List<NowHooks.Suggestion> list, int waiting, int sw, int sh, double s) {
        int maxText = SignalLayout.trackerTextWidth(sw, s);
        String line1 = null, compass = "";
        int colour1 = Glass.TEXT;
        if (!list.isEmpty()) {
            NowHooks.Suggestion top = list.get(0);
            compass = NowClient.compass(top).map(c -> "  " + c).orElse("");
            int room = maxText - font.width(compass);
            line1 = SignalText.ellipsize("◇ " + top.text().getString(), Math.max(30, room), font::width);
            if (top.priority() >= 90) colour1 = Glass.WARN;
        }
        String line2 = waiting <= 0 ? null : SignalText.ellipsize("◇ " + waiting + (waiting == 1 ? " decision waiting · " : " decisions waiting · ")
                + SystemHud.key(AwakenKeys.STATUS), maxText, font::width);
        int textW = 0, lines = 0;
        if (line1 != null) {
            textW = font.width(line1) + font.width(compass);
            lines++;
        }
        if (line2 != null) {
            textW = Math.max(textW, font.width(line2));
            lines++;
        }
        return new Lines(line1, compass, line2, colour1, SignalLayout.tracker(sw, sh, s, textW, lines, lowered(mc)));
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (!enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        List<NowHooks.Suggestion> list = NowClient.suggestions();
        int waiting = v2() ? SystemHud.waiting() : 0;
        if (list.isEmpty() && waiting == 0) return;

        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        double s = SystemHud.scale();
        // the text only changes with the suggestions, the decisions, or where you stand and look: build it then
        int key = NowClient.version();
        key = 31 * key + waiting;
        key = 31 * key + mc.player.getBlockX();
        key = 31 * key + mc.player.getBlockZ();
        key = 31 * key + Math.round(mc.player.getYRot() / 5f);
        key = 31 * key + sw * 1000 + (int) Math.round(s * 100);
        if (key != cacheKey || cached == null) {
            cacheKey = key;
            cached = build(mc, font, list, waiting, sw, sh, s);
        }
        Lines c = cached;
        String line1 = c.line1, compass = c.compass, line2 = c.line2;
        int colour1 = c.colour1;
        Rect r = c.rect;
        float alpha = Math.min(1f, (NowClient.sinceChange() + dt.getGameTimeDeltaPartialTick(false)) / 8f);

        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) s, (float) s);
        int w0 = (int) Math.round(r.w() / s), h0 = (int) Math.round(r.h() / s);
        g.fill(0, 0, w0, h0, Glass.a(Glass.GLASS, 0.55f));
        g.fill(0, 0, 1, h0, Glass.a(Glass.EDGE, 0.8f));
        int ty = 3;
        if (line1 != null) {
            g.text(font, line1, 4, ty, Glass.a(colour1, Math.max(0.35f, alpha)), true);
            if (!compass.isEmpty()) g.text(font, compass, 4 + font.width(line1), ty, Glass.a(Glass.EDGE, 1f), true);
            ty += SignalLayout.LINE;
        }
        if (line2 != null) g.text(font, line2, 4, ty, Glass.a(Glass.AMBER, 1f), true);
        g.pose().popMatrix();
    }
}
