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

    /** The card sits just under the first toast slot; its words wrap at MAX_W (unscaled), two lines a step at most. */
    static final int TOP = SignalLayout.TOAST_H + 4, MAX_W = 190, FLASH = 24;
    /** A touch smaller than the Now card: it's a companion, not the headline. */
    static final float SIZE = 0.85F;

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
        double s = SystemHud.scale() * SIZE;
        Layout lay = layout(t, font, mc);
        int done = lay.done, w = lay.w, h = lay.h;
        String title = lay.title, count = lay.count;
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
            int argb = Glass.a(textColour, fade * (r.done() || now ? 1f : 0.8f));
            if (r.text().contains("{key}")) {
                boolean wrap = lay.lines[i].size() > 1;
                drawRow(g, font, r, 13, ry, argb, fade, lay.keys[i], lay.compass[i], wrap);
                ry += wrap ? 20 : 10;
            } else {
                // a plain step wraps (two lines at most), its count and compass in the band's blue at the end
                List<String> ls = lay.lines[i];
                for (int k = 0; k < ls.size(); k++) {
                    String l = ls.get(k);
                    int tail = k == ls.size() - 1 ? l.indexOf("  ") : -1;
                    if (tail >= 0) {
                        String words = l.substring(0, tail);
                        HudLook.text(g, font, words, 13, ry, argb, true);
                        HudLook.text(g, font, l.substring(tail + 2), 13 + font.width(words + "  "), ry, Glass.a(Glass.EDGE, fade), true);
                    } else HudLook.text(g, font, l, 13, ry, argb, true);
                    ry += 10;
                }
            }
        }
        g.pose().popMatrix();
    }

    /** The card's measured layout, kept until the task, a compass reading or a key binding changes (not per frame). */
    private record Layout(SignalPayloads.Task task, String[] compass, String[] keys, String title, String count, int done, int w, int h,
                          List<String>[] lines) {}

    private static Layout cached;

    @SuppressWarnings("unchecked")
    private static Layout layout(SignalPayloads.Task t, Font font, Minecraft mc) {
        int n = t.rows().size();
        String[] compass = new String[n], keys = new String[n];
        for (int i = 0; i < n; i++) {
            SignalPayloads.TaskRow r = t.rows().get(i);
            compass[i] = r.marked() ? compass(mc, r) : "";
            keys[i] = r.text().contains("{key}") ? keyName(r.key()) : "";
        }
        Layout c = cached;
        if (c != null && c.task == t && java.util.Arrays.equals(c.compass, compass) && java.util.Arrays.equals(c.keys, keys)) return c;
        int done = 0;
        for (SignalPayloads.TaskRow r : t.rows()) if (r.done()) done++;
        String title = t.title().toUpperCase(Locale.ROOT), count = done + "/" + n;
        // the widest line decides the card (unscaled); a step longer than the card wraps once
        int w = font.width(title) + 10 + font.width(count);
        for (int i = 0; i < n; i++) w = Math.max(w, 13 + rowWidth(font, t.rows().get(i), keys[i], compass[i]));
        w = Math.min(MAX_W, w) + 8;
        int room = w - 17;
        int h = 6 + 11 + 2;
        List<String>[] lines = new List[n];
        for (int i = 0; i < n; i++) {
            lines[i] = lines(font, t.rows().get(i), keys[i], compass[i], room);
            h += 10 * lines[i].size();
        }
        return cached = new Layout(t, compass, keys, title, count, done, w, h, lines);
    }

    /**
     * A plain step's lines in {@code room}: its words wrapped at a word break (two lines at most, the second shortened
     * if it must), its count and compass after a no-break marker so they draw in blue.
     */
    private static List<String> lines(Font font, SignalPayloads.TaskRow r, String key, String compass, int room) {
        // a row with a key: one line if it fits, else the words after the key on a second line
        if (r.text().contains("{key}")) return rowWidth(font, r, key, compass) <= room ? List.of(r.text()) : List.of(r.text(), "");
        String extra = (r.need() > 0 ? r.have() + "/" + r.need() : "") + (r.marked() ? (r.need() > 0 ? "  " : "") + compass : "");
        String full = r.text() + (extra.isEmpty() ? "" : "  " + extra);
        if (font.width(full) <= room) return List.of(full);
        int cut = full.lastIndexOf(' ', full.length() - 1);
        while (cut > 0 && font.width(full.substring(0, cut)) > room) cut = full.lastIndexOf(' ', cut - 1);
        if (cut <= 0) cut = Math.max(1, full.length() / 2);
        String first = full.substring(0, cut).trim(), second = full.substring(cut).trim();
        while (font.width(second) > room && second.length() > 2) second = second.substring(0, second.length() - 2) + "…";
        return List.of(first, second);
    }

    /** The row's width: its words, a key cap for "{key}", its count and its compass. */
    private static int rowWidth(Font font, SignalPayloads.TaskRow r, String key, String compass) {
        int at = r.text().indexOf("{key}");
        int w = font.width(at < 0 ? r.text() : r.text().substring(0, at));
        if (at >= 0) w += font.width(key) + 6 + font.width(r.text().substring(at + 5));
        if (r.need() > 0) w += font.width(" " + r.have() + "/" + r.need());
        if (r.marked()) w += font.width("  " + compass);
        return w;
    }

    private static void drawRow(GuiGraphicsExtractor g, Font font, SignalPayloads.TaskRow r, int x, int y, int argb, float fade, String key, String compass,
                                boolean wrap) {
        int at = r.text().indexOf("{key}");
        String before = at < 0 ? r.text() : r.text().substring(0, at);
        int cx = x;
        HudLook.text(g, font, before, cx, y, argb, true);
        cx += font.width(before);
        if (at >= 0) {
            cx += keyCap(g, font, key, cx, y, fade);
            // too wide for the card: what follows the key goes on the next line
            String after = wrap ? r.text().substring(at + 5).trim() : r.text().substring(at + 5);
            if (wrap) {
                cx = x;
                y += 10;
            }
            HudLook.text(g, font, after, cx, y, argb, true);
            cx += font.width(after);
        }
        if (r.need() > 0) {
            String c = " " + r.have() + "/" + r.need();
            HudLook.text(g, font, c, cx, y, Glass.a(Glass.EDGE, fade), true);
            cx += font.width(c);
        }
        if (r.marked()) HudLook.text(g, font, "  " + compass, cx, y, Glass.a(Glass.EDGE, fade), true);
    }

    private static String keyName(String mapping) {
        KeyMapping k = mapping.isEmpty() ? null : KeyMapping.get(mapping);
        return k == null ? "?" : k.getTranslatedKeyMessage().getString();
    }

    /** A key drawn as a key: its name in a small bordered cap. Returns its width. */
    private static int keyCap(GuiGraphicsExtractor g, Font font, String name, int x, int y, float fade) {
        int kw = font.width(name) + 4;
        g.fill(x + 1, y - 1, x + 1 + kw, y + 9, Glass.a(Glass.GLASS_TOP, 0.85f * fade));
        g.fill(x + 1, y - 1, x + 1 + kw, y, Glass.a(Glass.EDGE, 0.9f * fade));
        g.fill(x + 1, y + 8, x + 1 + kw, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        g.fill(x + 1, y - 1, x + 2, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        g.fill(x + kw, y - 1, x + 1 + kw, y + 9, Glass.a(Glass.EDGE, 0.6f * fade));
        HudLook.text(g, font, name, x + 3, y, Glass.a(Glass.TITLE, fade), false);
        return kw + 2;
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** "↗ 9 m": the way to the step's place from where you look. */
    private static String compass(Minecraft mc, SignalPayloads.TaskRow r) {
        double dx = r.at().getX() + 0.5 - mc.player.getX(), dz = r.at().getZ() + 0.5 - mc.player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double bearing = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
        int k = Math.floorMod((int) Math.round(bearing / 45.0), 8);
        return ARROWS[k] + " " + dist + " m";
    }
}
