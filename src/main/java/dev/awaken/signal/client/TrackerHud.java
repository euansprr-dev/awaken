package dev.awaken.signal.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.signal.ui.Rect;
import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * The Now line, top left (SPEC §27.11; Euan 2026-10-27: "the top left is pulling me in so many directions"): only where
 * you're going, and only when you're going somewhere. The destination you chose on the Atlas, else the current task
 * step's place, with its compass; otherwise nothing. What the Now tracker suggests stays in the band's lists and "Anything
 * for me?". Under it, the Horizon line (SPEC §17) when the player turns it on (off by default).
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

    /** Inside the Descent its own HUD owns the corner, so the tracker sits below it. */
    private static boolean lowered(Minecraft mc) {
        Identifier dim = mc.player.level().dimension().identifier();
        return dim.getNamespace().equals(Awaken.MODID) && (dim.getPath().startsWith("stratum_") || dim.getPath().equals("endless"));
    }

    /** Where the boss bars drawn this frame end (0 when none); they draw before this layer and the run panel. */
    private static int bossBottom;

    /**
     * Each boss bar as vanilla places it: the bar is 5 tall, its name sits above it. A bar drawn another way (the Boss
     * Plate, SPEC §35.1, cancels vanilla's and sizes its slot) ends its increment after the slot's top, 9 over the bar.
     */
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        if (e.getIncrement() <= 0) return;
        bossBottom = Math.max(bossBottom, e.getY() + e.getIncrement() - 14);
    }

    /** Before any layer of a frame: the bars are counted afresh. */
    static void frameStart() {
        bossBottom = 0;
    }

    /** Where this frame's boss bars end so far (all of them, from the boss overlay's layer on). */
    static int bossBottom() {
        return bossBottom;
    }

    private record Line(String text, String compass, boolean destination, Rect rect) {}

    private static Line cached;
    private static int cacheKey;

    /** "◆ E-rank gate" for the destination you chose, "◇ Wayfarer camp" for the task's place, then the compass. */
    private static Line build(Minecraft mc, Font font, dev.awaken.signal.NowCorner.Place h, int sw, int sh, double s) {
        int maxText = SignalLayout.trackerTextWidth(sw, s);
        String compass = NowClient.compass(h.at(), h.dimension()).map(c -> "  " + c).orElse("");
        int room = maxText - font.width(compass);
        String text = SignalText.ellipsize((h.destination() ? "\u25C6 " : "\u25C7 ") + h.name(), Math.max(30, room), font::width);
        int textW = font.width(text) + font.width(compass);
        return new Line(text, compass, h.destination(), SignalLayout.tracker(sw, sh, s, textW, 1, lowered(mc)));
    }

    /** The Horizon line is the player's choice now (SPEC §27.11): off unless turned on. */
    static boolean horizonEnabled() {
        try {
            return AwakenConfig.Client.HORIZON_LINE.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        int bars = bossBottom;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        // the band and the Atlas show where you're going themselves; the cards would sit on their top edge (SPEC §30)
        if (mc.gui.screen() instanceof QuietScreen) return;
        Rect now = enabled() ? renderNow(g, dt, mc, bars) : null;
        if (horizonEnabled()) renderHorizon(g, dt, mc, bars, now);
    }

    /** The Now card; returns where it drew (null when there is nowhere you're going). */
    private static Rect renderNow(GuiGraphicsExtractor g, DeltaTracker dt, Minecraft mc, int bars) {
        dev.awaken.signal.NowCorner.Place h = NowClient.current();
        if (h == null) return null;

        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        double s = SystemHud.scale();
        // the text only changes with the place, or where you stand and look: build it then
        int key = NowClient.headingVersion();
        key = 31 * key + mc.player.getBlockX();
        key = 31 * key + mc.player.getBlockY();
        key = 31 * key + mc.player.getBlockZ();
        key = 31 * key + Math.round(mc.player.getYRot() / 5f);
        key = 31 * key + sw * 1000 + (int) Math.round(s * 100);
        if (key != cacheKey || cached == null) {
            cacheKey = key;
            cached = build(mc, font, h, sw, sh, s);
        }
        Line c = cached;
        Rect r = c.rect;
        // a boss fight: step below the bars rather than print over their names (narrow screens, long lines)
        if (bars > 0 && r.right() > sw / 2 - 92 && r.y() < bars + 3) r = new Rect(r.x(), bars + 3, r.w(), r.h());
        float alpha = Math.min(1f, (NowClient.sinceHeading() + dt.getGameTimeDeltaPartialTick(false)) / 8f);

        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) s, (float) s);
        int w0 = (int) Math.round(r.w() / s), h0 = (int) Math.round(r.h() / s);
        if (HudLook.holo()) {
            // always on: a still card (no scan, no glitch), only the line's own fade when it changes
            HudLook.card(g, 0, 0, w0, h0, Glass.EDGE, 1f, 1f, 0f, HudLook.FRAME); // the calm HUD: no band, no flicker
        } else {
            g.fill(0, 0, w0, h0, Glass.a(Glass.GLASS, 0.55f));
            g.fill(0, 0, 1, h0, Glass.a(Glass.EDGE, 0.8f));
        }
        HudLook.text(g, font, c.text, 4, 3, Glass.a(c.destination ? Glass.TITLE : Glass.TEXT, Math.max(0.35f, alpha)), true);
        if (!c.compass.isEmpty()) HudLook.text(g, font, c.compass, 4 + font.width(c.text), 3, Glass.a(Glass.EDGE, 1f), true);
        g.pose().popMatrix();
        return r;
    }

    // ---- the Horizon line (SPEC §17) ------------------------------------------------------------------------------

    private static final String PEAK = "\u25B3 ";

    /** The Horizon card's text, built when the line, your place or your heading changes. */
    private record Horizon(String name, String need, String compass, boolean tease, Rect rect) {}

    private static Horizon horizon;
    private static int horizonKey;

    /** "wings" → "Wings". */
    /** What a tease needs, in words: the sky's need is a way up (the rig's rings take a rope), never "Wings" alone. */
    private static String needLabel(String need) {
        return switch (need) {
            case "" -> "";
            case HorizonHooks.WINGS -> "a rope or wings";
            case HorizonHooks.TETHER -> "the Tether";
            default -> Character.toUpperCase(need.charAt(0)) + need.substring(1);
        };
    }

    /**
     * The Horizon line: one calm card in the Horizon's own light, just under the Now card (or in its place). A site you
     * can reach reads in the HUD's text colour; a tease is dimmer, with what it needs in amber.
     */
    private static void renderHorizon(GuiGraphicsExtractor g, DeltaTracker dt, Minecraft mc, int bars, Rect above) {
        var line = HorizonClient.line();
        if (line.isEmpty()) return;
        HorizonHooks.Line l = line.get();
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        double s = SystemHud.scale();
        int key = HorizonClient.version();
        key = 31 * key + mc.player.getBlockX();
        key = 31 * key + mc.player.getBlockZ();
        key = 31 * key + Math.round(mc.player.getYRot() / 5f);
        key = 31 * key + sw * 1000 + (int) Math.round(s * 100);
        key = 31 * key + (above == null ? -1 : above.bottom());
        if (key != horizonKey || horizon == null) {
            horizonKey = key;
            String compass = HorizonClient.compass(l).map(c -> "  " + c).orElse("");
            String need = l.tease() ? " \u00B7 needs " + needLabel(l.need()) : "";
            int room = SignalLayout.trackerTextWidth(sw, s) - font.width(PEAK) - font.width(need) - font.width(compass);
            String name = SignalText.ellipsize(l.name(), Math.max(30, room), font::width);
            int textW = font.width(PEAK) + font.width(name) + font.width(need) + font.width(compass);
            horizon = new Horizon(name, need, compass, l.tease(), SignalLayout.horizon(sw, sh, s, textW, above, lowered(mc)));
        }
        Horizon c = horizon;
        Rect r = c.rect;
        if (above == null && bars > 0 && r.right() > sw / 2 - 92 && r.y() < bars + 3) r = new Rect(r.x(), bars + 3, r.w(), r.h());
        float alpha = Math.max(0.35f, Math.min(1f, (HorizonClient.sinceChange() + dt.getGameTimeDeltaPartialTick(false)) / 8f));

        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale((float) s, (float) s);
        int w0 = (int) Math.round(r.w() / s), h0 = (int) Math.round(r.h() / s);
        if (HudLook.holo()) {
            HudLook.card(g, 0, 0, w0, h0, Glass.HORIZON, 1f, 1f, 0f, HudLook.FRAME);
        } else {
            g.fill(0, 0, w0, h0, Glass.a(Glass.GLASS, 0.55f));
            g.fill(0, 0, 1, h0, Glass.a(Glass.HORIZON, 0.8f));
        }
        int x = 4, ty = 3;
        HudLook.text(g, font, PEAK, x, ty, Glass.a(Glass.HORIZON, alpha), true);
        x += font.width(PEAK);
        HudLook.text(g, font, c.name, x, ty, Glass.a(c.tease ? Glass.LABEL : Glass.TEXT, alpha), true);
        x += font.width(c.name);
        if (!c.need.isEmpty()) {
            HudLook.text(g, font, c.need, x, ty, Glass.a(Glass.AMBER, alpha), true);
            x += font.width(c.need);
        }
        if (!c.compass.isEmpty()) HudLook.text(g, font, c.compass, x, ty, Glass.a(Glass.HORIZON, 1f), true);
        g.pose().popMatrix();
    }
}
