package dev.awaken.signal.client;

import dev.awaken.signal.net.SignalPayloads;
import dev.awaken.signal.ui.SignalLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * The current task, top right (SPEC §27.10): a small card in the band's glass under the toasts' first two slots, its
 * title and a done/total count, then its steps: ticked when done, the current one bright, keys drawn as key caps
 * ("Double-tap [Left Control] to sprint harder"), a count where a step has one, and a compass to a place nearby to do
 * it. A step that ticks flashes once. The server ({@code progression.Checklist}) sends it when it changes; an empty
 * title clears it. Never over the crosshair, never when the HUD is hidden.
 */
final class TaskHud {
    private TaskHud() {}

    /** The card sits below two toast slots, so a toast never lands on it. */
    static final int TOP = SignalLayout.TOAST_H * 2 + 6, MAX_W = 176, FLASH = 24;

    private static SignalPayloads.Task task = new SignalPayloads.Task("", "", List.of());
    private static long since, ticks;
    private static final long[] TICKED = new long[16];

    static void onTask(SignalPayloads.Task t) {
        boolean same = t.id().equals(task.id());
        for (int i = 0; i < t.rows().size() && i < TICKED.length; i++) {
            boolean was = same && i < task.rows().size() && task.rows().get(i).done();
            if (t.rows().get(i).done() && !was && same) TICKED[i] = ticks;
            else if (!same) TICKED[i] = -1000;
        }
        if (!same) since = ticks;
        task = t;
    }

    static void tick() {
        ticks++;
    }

    static void clear() {
        task = new SignalPayloads.Task("", "", List.of());
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        SignalPayloads.Task t = task;
        if (t.title().isEmpty() || mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float fade = Mth.clamp((ticks - since + pt) / 10F, 0, 1);
        double s = SystemHud.scale();
        int done = 0;
        for (SignalPayloads.TaskRow r : t.rows()) if (r.done()) done++;
        String title = t.title().toUpperCase(Locale.ROOT), count = done + "/" + t.rows().size();
        // the widest line decides the card (unscaled)
        int w = font.width(title) + 10 + font.width(count);
        for (SignalPayloads.TaskRow r : t.rows()) w = Math.max(w, 12 + rowWidth(font, r, mc));
        w = Math.min(MAX_W, w) + 8;
        int h = 6 + 11 + t.rows().size() * 10 + 2;
        int sw = g.guiWidth();
        int x = (int) Math.round(sw - w * s - SignalLayout.MARGIN), y = TOP;

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale((float) s, (float) s);
        if (HudLook.holo()) HudLook.card(g, 0, 0, w, h, Glass.EDGE, fade, 1f, 0f, HudLook.FRAME);
        else {
            g.fill(0, 0, w, h, Glass.a(Glass.GLASS, 0.55f * fade));
            g.fill(w - 1, 0, w, h, Glass.a(Glass.EDGE, 0.8f * fade));
        }
        HudLook.text(g, font, title, 4, 4, Glass.a(Glass.EDGE, fade), true);
        HudLook.text(g, font, count, w - 4 - font.width(count), 4, Glass.a(done == t.rows().size() ? Glass.GOOD : Glass.LABEL, fade), true);
        int ry = 17;
        boolean current = false;
        for (int i = 0; i < t.rows().size(); i++) {
            SignalPayloads.TaskRow r = t.rows().get(i);
            boolean now = !r.done() && !current;
            if (now) current = true;
            // a step that just ticked: one soft flash of green behind it
            long since = i < TICKED.length ? ticks - TICKED[i] : 1000;
            if (since < FLASH) g.fill(2, ry - 1, w - 2, ry + 9, Glass.a(Glass.GOOD, 0.35f * (1 - (since + pt) / FLASH) * fade));
            String mark = r.done() ? "✔" : now ? "▸" : "·";
            int markColour = r.done() ? Glass.GOOD : now ? Glass.AMBER : Glass.LABEL;
            HudLook.text(g, font, mark, 4, ry, Glass.a(markColour, fade), true);
            int textColour = r.done() || !now ? Glass.LABEL : Glass.TEXT;
            drawRow(g, font, r, 13, ry, Glass.a(textColour, fade * (r.done() || now ? 1f : 0.8f)), fade, mc);
            ry += 10;
        }
        g.pose().popMatrix();
    }

    /** The row's width: its words, a key cap for "{key}", its count and its compass. */
    private static int rowWidth(Font font, SignalPayloads.TaskRow r, Minecraft mc) {
        String[] parts = r.text().split("\\{key}", 2);
        int w = font.width(parts[0]);
        if (parts.length > 1) w += keyWidth(font, r.key()) + font.width(parts[1]);
        if (r.need() > 0) w += font.width(" " + r.have() + "/" + r.need());
        if (r.marked()) w += font.width("  " + compass(mc, r));
        return w;
    }

    private static void drawRow(GuiGraphicsExtractor g, Font font, SignalPayloads.TaskRow r, int x, int y, int argb, float fade, Minecraft mc) {
        String[] parts = r.text().split("\\{key}", 2);
        String before = parts[0];
        int cx = x;
        HudLook.text(g, font, before, cx, y, argb, true);
        cx += font.width(before);
        if (parts.length > 1) {
            cx += keyCap(g, font, r.key(), cx, y, fade);
            HudLook.text(g, font, parts[1], cx, y, argb, true);
            cx += font.width(parts[1]);
        }
        if (r.need() > 0) {
            String c = " " + r.have() + "/" + r.need();
            HudLook.text(g, font, c, cx, y, Glass.a(Glass.EDGE, fade), true);
            cx += font.width(c);
        }
        if (r.marked()) HudLook.text(g, font, "  " + compass(mc, r), cx, y, Glass.a(Glass.EDGE, fade), true);
    }

    private static String keyName(String mapping) {
        KeyMapping k = mapping.isEmpty() ? null : KeyMapping.get(mapping);
        return k == null ? "?" : k.getTranslatedKeyMessage().getString();
    }

    private static int keyWidth(Font font, String mapping) {
        return font.width(keyName(mapping)) + 6;
    }

    /** A key drawn as a key: its name in a small bordered cap. Returns its width. */
    private static int keyCap(GuiGraphicsExtractor g, Font font, String mapping, int x, int y, float fade) {
        String name = keyName(mapping);
        int kw = font.width(name) + 4;
        g.fill(x + 1, y - 1, x + 1 + kw, y + 9, Glass.a(Glass.GLASS_TOP, 0.85f * fade));
        g.fill(x + 1, y - 1, x + 1 + kw, y, Glass.a(Glass.EDGE, 0.9f * fade));
        g.fill(x + 1, y + 8, x + 1 + kw, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        g.fill(x + 1, y - 1, x + 2, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        g.fill(x + kw, y - 1, x + 1 + kw, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        HudLook.text(g, font, name, x + 3, y, Glass.a(Glass.TITLE, fade), false);
        return kw + 2;
    }

    /** "↗ 9 m": the way to the step's place from where you look. */
    private static String compass(Minecraft mc, SignalPayloads.TaskRow r) {
        double dx = r.at().getX() + 0.5 - mc.player.getX(), dz = r.at().getZ() + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double bearing = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        int k = Math.floorMod((int) Math.round(bearing / 45.0), 8);
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[k] + " " + dist + " m";
    }
}
