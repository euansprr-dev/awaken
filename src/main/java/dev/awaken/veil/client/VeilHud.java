package dev.awaken.veil.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.signal.client.Glass;
import dev.awaken.veil.Cord;
import dev.awaken.veil.Meditation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The meditation ring around the crosshair, and the spirit's two quiet bars (Focus and Cord) with the keys that matter.
 */
final class VeilHud {
    private VeilHud() {}

    private static final int DOTS = 48;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        if (VeilClientState.glimpseFlash > 0) {
            int a = (int) (VeilClientState.glimpseFlash / 8F * 160);
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xE8F4FF);
        }
        if (VeilClientState.spirit()) spirit(g, mc, pt);
        else if (VeilClientState.meditation.ticks() > 0) ring(g, mc, pt);
    }

    // ---- meditation -------------------------------------------------------------------------------------------------

    private static void ring(GuiGraphicsExtractor g, Minecraft mc, float pt) {
        var med = VeilClientState.meditation;
        float t = VeilClientState.medTicks + pt;
        boolean travel = med.canTravel() && !med.justSit();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        float breath = 0.5F + 0.5F * Mth.sin((VeilClientState.age + pt) * 0.08F);
        float settle = Math.min(1F, t / Meditation.SETTLE);
        float radius = 16 + 1.5F * breath;
        float step = Meditation.STEP_OUT, still = Meditation.STILLPOINT;
        float p1 = Math.min(1F, t / step);
        float p2 = med.inStillpoint() ? 0F : Mth.clamp((t - step) / (still - step), 0F, 1F);
        for (int i = 0; i < DOTS; i++) {
            float frac = (float) i / DOTS;
            double a = -Math.PI / 2 + frac * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * radius), y = cy + (int) Math.round(Math.sin(a) * radius);
            int colour;
            float alpha;
            if (!travel) {
                colour = Glass.TEXT;
                alpha = (0.25F + 0.55F * breath) * settle;
            } else if (frac < p2) {
                colour = Glass.LANTERN;
                alpha = 0.95F;
            } else if (frac < p1) {
                colour = med.inStillpoint() ? Glass.LANTERN : Glass.EDGE;
                alpha = 0.9F;
            } else {
                colour = Glass.LABEL;
                alpha = 0.25F * settle;
            }
            g.fill(x, y, x + 2, y + 2, Glass.a(colour, alpha));
        }
        Font f = mc.font;
        String line;
        int col = Glass.TEXT;
        if (!travel) line = med.justSit() ? "Just sitting" : t < Meditation.SETTLE ? "" : "Breathe";
        else if (med.inStillpoint()) line = t >= step ? "Release to go back" : "Be still";
        else if (t < step) line = "Be still";
        else if (t < still) {
            line = "Release: step out  ·  Hold: the Stillpoint";
            col = Glass.EDGE;
        } else line = "";
        if (!line.isEmpty()) {
            int w = f.width(line);
            g.text(f, line, cx - w / 2, cy + 26, Glass.a(col, 0.85F * settle), true);
        }
        if (travel && t >= Meditation.SETTLE) {
            String hint = "[" + AwakenKeys.DISMISS.getTranslatedKeyMessage().getString() + "] just sit";
            int w = f.width(hint);
            g.text(f, hint, cx - w / 2, cy + 38, Glass.a(Glass.LABEL, 0.6F * settle), false);
        }
    }

    // ---- spirit -----------------------------------------------------------------------------------------------------

    private static void spirit(GuiGraphicsExtractor g, Minecraft mc, float pt) {
        var st = VeilClientState.state;
        Font f = mc.font;
        int w = 150;
        int x = g.guiWidth() / 2 - w / 2;
        int y = g.guiHeight() - 52;
        if (VeilClientState.bodyHurtFlash > 0) {
            float a = VeilClientState.bodyHurtFlash / 14F * 0.35F;
            int edge = Glass.a(Glass.WARN, a);
            g.fillGradient(0, 0, g.guiWidth(), 24, edge, 0);
            g.fillGradient(0, g.guiHeight() - 24, g.guiWidth(), g.guiHeight(), 0, edge);
        }
        // focus (time)
        double focus = st.maxTicks() <= 0 ? 0 : Math.max(0, VeilClientState.ticksLeft - pt) / st.maxTicks();
        int secs = Math.max(0, VeilClientState.ticksLeft / 20);
        int focusCol = focus < 0.2 ? Glass.WARN : Glass.EDGE;
        g.text(f, "FOCUS", x, y, Glass.a(Glass.LABEL, 0.9F), false);
        String time = secs / 60 + ":" + String.format("%02d", secs % 60);
        g.text(f, time, x + w - f.width(time), y, Glass.a(focusCol, 0.9F), false);
        Glass.bar(g, x, y + 10, w, 3, focus, focusCol, 0.85F);
        // cord (tension)
        Vec3 pos = mc.player.position();
        double dist = pos.distanceTo(st.anchor());
        double tension = Cord.tension(dist, Math.max(1, st.cord()));
        int cordCol = tension >= Cord.DANGER ? Glass.WARN : tension >= Cord.WARN ? Glass.AMBER : Glass.TEXT;
        float pulse = tension >= Cord.WARN ? 0.6F + 0.4F * Mth.sin((VeilClientState.age + pt) * 0.5F) : 0.85F;
        g.text(f, "CORD", x, y + 17, Glass.a(Glass.LABEL, 0.9F), false);
        String cord = (int) dist + " / " + st.cord();
        g.text(f, cord, x + w - f.width(cord), y + 17, Glass.a(cordCol, 0.9F), false);
        Glass.bar(g, x, y + 27, w, 3, Math.min(1, tension), cordCol, pulse);
        // keys
        String hint;
        if (VeilClientState.possessing()) hint = "[Shift] let go   [Attack] act   [" + key(AwakenKeys.ACCEPT) + "] return";
        else hint = "[" + key(AwakenKeys.ACCEPT) + "] return   [" + key(AwakenKeys.FOCUS) + "] speak   "
                + (st.possessLevel() > 0 ? "[" + key(AwakenKeys.BIND) + "] wear   " : "") + "[Use] touch blue";
        int hw = f.width(hint);
        g.text(f, hint, g.guiWidth() / 2 - hw / 2, y + 34, Glass.a(Glass.LABEL, 0.7F), false);
        if (st.inPocket()) {
            String s = "VEIL GATE";
            g.text(f, s, g.guiWidth() / 2 - f.width(s) / 2, y - 12, Glass.a(Glass.EDGE, 0.9F), false);
        }
    }

    private static String key(net.minecraft.client.KeyMapping k) {
        return k.getTranslatedKeyMessage().getString();
    }
}
