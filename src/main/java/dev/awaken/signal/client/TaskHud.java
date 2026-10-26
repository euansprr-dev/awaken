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
 * The current task, top right (SPEC §27.10): a small card tucked into the corner, in the Now card's glass and margin, at
 * about half the Now card's size (Euan, 2026-10-26: "WAY too big, 2x smaller and closer to the corner"). Its title and
 * a done/total count, then only what matters now: the current step bright, the next one or two dim, each on one line
 * (a long one shortened; the Journal has the whole of it), and a step that just ticked, which flashes green and fades
 * away. Keys are drawn as key caps ("Double-tap [Left Control] to sprint harder"), a count where a step has one, a
 * compass to a place nearby. While the System's toasts are up it steps down under them. The server
 * ({@code progression.Checklist}) sends it when it changes; an empty title clears it. Never when the HUD is hidden.
 *
 * <p>Measured once a tick at most, and only when the task, the rows shown, a compass reading, a key binding or the GUI
 * scale changed ({@link Layout}); a frame only draws what the layout holds (the perf pass of 26 Oct found the card
 * measuring itself, splitting with a regex and building compass strings every frame).
 */
final class TaskHud {
    private TaskHud() {}

    /** The widest line (unscaled px) before a step is shortened; how long a ticked step lingers; the flash. */
    static final int MAX_W = 185, LINGER = 50, FLASH = 24;
    /** Half the Now card's size, but never so small a font pixel is under two screen pixels. */
    static final float SIZE = 0.45F, MIN_PIXEL = 2.0F;
    /** Rows after the current one. */
    static final int AHEAD = 2;

    private static SignalPayloads.Task task = new SignalPayloads.Task("", "", List.of());
    private static long since, ticks;
    private static final long[] TICKED = new long[16];
    /** Where the card sits (GUI px from the top), easing down under toasts and back. */
    private static float top = SignalLayout.MARGIN, topO = SignalLayout.MARGIN;

    static void onTask(SignalPayloads.Task t) {
        boolean same = t.id().equals(task.id());
        for (int i = 0; i < t.rows().size() && i < TICKED.length; i++) {
            boolean was = same && i < task.rows().size() && task.rows().get(i).done();
            if (t.rows().get(i).done() && !was && same) TICKED[i] = ticks;
            else if (!same || !t.rows().get(i).done()) TICKED[i] = -1000;
        }
        if (!same) since = ticks;
        task = t;
    }

    static void tick() {
        ticks++;
        topO = top;
        float want = SignalLayout.MARGIN + SystemHud.liveToasts() * SignalLayout.TOAST_H;
        top += (want - top) * 0.25F;
        Minecraft mc = Minecraft.getInstance();
        if (!task.title().isEmpty() && mc.player != null) layout = measure(task, mc.font, mc, layout);
    }

    static void clear() {
        task = new SignalPayloads.Task("", "", List.of());
    }

    /** The rows shown now: a step that just ticked (while it lingers), the current one, and the next {@link #AHEAD}. */
    private static List<Integer> shown(SignalPayloads.Task t) {
        List<Integer> out = new java.util.ArrayList<>(4);
        int open = 0;
        for (int i = 0; i < t.rows().size(); i++) {
            SignalPayloads.TaskRow r = t.rows().get(i);
            if (r.done()) {
                if (i < TICKED.length && ticks - TICKED[i] < LINGER) out.add(i);
            } else if (open++ <= AHEAD) out.add(i);
        }
        // all done: the last step stays, ticked, until the card goes
        if (out.isEmpty() && !t.rows().isEmpty()) out.add(t.rows().size() - 1);
        return out;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        SignalPayloads.Task t = task;
        Layout lay = layout;
        if (t.title().isEmpty() || lay == null || lay.task != t || mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float fade = Mth.clamp((ticks - since + pt) / 10F, 0, 1);
        double s = lay.scale;
        int w = lay.w, h = lay.h;
        int sw = g.guiWidth();
        float y = Mth.lerp(pt, topO, top);
        int x = (int) Math.round(sw - w * s - SignalLayout.MARGIN);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale((float) s, (float) s);
        if (HudLook.holo()) HudLook.card(g, 0, 0, w, h, Glass.EDGE, fade, 1f, 0f, HudLook.FRAME);
        else {
            g.fill(0, 0, w, h, Glass.a(Glass.GLASS, 0.55f * fade));
            g.fill(w - 1, 0, w, h, Glass.a(Glass.EDGE, 0.8f * fade));
        }
        HudLook.text(g, font, lay.title, 4, 3, Glass.a(Glass.EDGE, fade), true);
        HudLook.text(g, font, lay.count, w - 4 - lay.countW, 3, Glass.a(lay.allDone ? Glass.GOOD : Glass.LABEL, fade), true);
        int ry = 14;
        for (Row row : lay.rows) {
            int i = row.index;
            SignalPayloads.TaskRow r = t.rows().get(i);
            // a step that just ticked: one soft flash of green, then it fades out of the list
            long ago = i < TICKED.length ? ticks - TICKED[i] : 1000;
            float rowFade = fade * (r.done() && ago < LINGER ? Mth.clamp((LINGER - ago - pt) / 16F, 0, 1) : 1f);
            if (ago < FLASH) g.fill(2, ry - 1, w - 2, ry + 9, Glass.a(Glass.GOOD, 0.35f * (1 - (ago + pt) / FLASH) * fade));
            int markColour = r.done() ? Glass.GOOD : row.now ? Glass.AMBER : Glass.LABEL;
            HudLook.text(g, font, row.mark, 3, ry, Glass.a(markColour, rowFade), true);
            int textColour = r.done() || !row.now ? Glass.LABEL : Glass.TEXT;
            int argb = Glass.a(textColour, rowFade * (r.done() || row.now ? 1f : 0.8f));
            int cx = 11;
            HudLook.text(g, font, row.before, cx, ry, argb, true);
            cx += row.beforeW;
            if (row.key != null) {
                cx += keyCap(g, font, row.key, row.keyW, cx, ry, rowFade);
                HudLook.text(g, font, row.after, cx, ry, argb, true);
                cx += row.afterW;
            }
            if (!row.tail.isEmpty()) HudLook.text(g, font, row.tail, cx, ry, Glass.a(Glass.EDGE, rowFade), true);
            ry += 10;
        }
        g.pose().popMatrix();
    }

    /** One shown step, measured: its mark, its words (shortened to fit), a key cap, then the count and compass. */
    private record Row(int index, boolean now, String mark, String before, int beforeW, @org.jspecify.annotations.Nullable String key, int keyW,
                       String after, int afterW, String tail) {}

    /** The card as measured: kept until what it shows or the GUI scale changes. */
    private record Layout(SignalPayloads.Task task, List<Integer> shown, List<String> compasses, List<String> keys, int guiScale, double scale,
                          String title, String count, int countW, boolean allDone, int w, int h, List<Row> rows) {}

    private static @org.jspecify.annotations.Nullable Layout layout;

    /** The layout for now: {@code was} itself when nothing it depends on changed (no strings measured, no allocation). */
    private static Layout measure(SignalPayloads.Task t, Font font, Minecraft mc, @org.jspecify.annotations.Nullable Layout was) {
        List<Integer> shown = shown(t);
        int gui = mc.getWindow().getGuiScale();
        List<String> compasses = new java.util.ArrayList<>(shown.size()), keys = new java.util.ArrayList<>(shown.size());
        for (int i : shown) {
            SignalPayloads.TaskRow r = t.rows().get(i);
            compasses.add(r.marked() ? compass(mc, r) : "");
            keys.add(r.text().contains("{key}") ? keyName(r.key()) : "");
        }
        if (was != null && was.task == t && was.guiScale == gui && was.shown.equals(shown) && was.compasses.equals(compasses) && was.keys.equals(keys)) {
            return was;
        }
        // half the Now card, following the System's own scale (and so the GUI scale), but always legible
        double scale = Math.max(SystemHud.scale() * SIZE, MIN_PIXEL / Math.max(1, gui));
        int done = 0;
        for (SignalPayloads.TaskRow r : t.rows()) if (r.done()) done++;
        String title = t.title().toUpperCase(Locale.ROOT), count = done + "/" + t.rows().size();
        int countW = font.width(count);
        // the widest line decides the card (unscaled), up to MAX_W; longer steps are shortened to fit
        int w = font.width(title) + 10 + countW;
        for (int k = 0; k < shown.size(); k++) w = Math.max(w, 11 + rowWidth(font, t.rows().get(shown.get(k)), keys.get(k), compasses.get(k)));
        w = Math.min(MAX_W, w) + 8;
        int room = w - 15;
        int h = 4 + 10 + shown.size() * 10 + 2;
        List<Row> rows = new java.util.ArrayList<>(shown.size());
        boolean current = false;
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            SignalPayloads.TaskRow r = t.rows().get(i);
            boolean now = !r.done() && !current;
            if (now) current = true;
            String mark = r.done() ? "\u2714" : now ? "\u25B8" : "\u00B7";
            String tail = (r.need() > 0 ? " " + r.have() + "/" + r.need() : "") + (r.marked() ? "  " + compasses.get(k) : "");
            int left = room - font.width(tail);
            int at = r.text().indexOf("{key}");
            String before = SignalTextFit.fit(font, at < 0 ? r.text() : r.text().substring(0, at), left);
            int beforeW = font.width(before);
            left -= beforeW;
            String key = null, after = "";
            int keyW = 0, afterW = 0;
            if (at >= 0 && left > font.width(keys.get(k)) + 6) {
                key = keys.get(k);
                keyW = font.width(key) + 4;
                left -= keyW + 2;
                after = SignalTextFit.fit(font, r.text().substring(at + 5), left);
                afterW = font.width(after);
            }
            rows.add(new Row(i, now, mark, before, beforeW, key, keyW, after, afterW, tail));
        }
        String fitTitle = SignalTextFit.fit(font, title, w - 12 - countW);
        return new Layout(t, shown, compasses, keys, gui, scale, fitTitle, count, countW, done == t.rows().size(), w, h, rows);
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

    private static String keyName(String mapping) {
        KeyMapping k = mapping.isEmpty() ? null : KeyMapping.get(mapping);
        return k == null ? "?" : k.getTranslatedKeyMessage().getString();
    }

    /** A key drawn as a key: its name in a small bordered cap. Returns its width. */
    private static int keyCap(GuiGraphicsExtractor g, Font font, String name, int kw, int x, int y, float fade) {
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
