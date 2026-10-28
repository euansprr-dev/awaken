package dev.awaken.signal.client;

import dev.awaken.signal.net.SignalPayloads;
import dev.awaken.signal.ui.SignalLayout;
import dev.awaken.signal.ui.TaskWrap;
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
 * compass to a place nearby. While the System's toasts are up it steps down under them, and below a boss bar it would
 * reach. The server ({@code progression.Checklist}) sends it when it changes; an empty title clears it. Never when the
 * HUD is hidden. It heads the top-right stack ({@link #STACK}, {@code UiHooks.TopRight}): the gate's run panel sits
 * under it, or takes the corner when there is no task.
 *
 * <p>Measured once a tick at most, and only when the task, the rows shown, a compass reading, a key binding or the GUI
 * scale changed ({@link Layout}); a frame only draws what the layout holds (the perf pass of 26 Oct found the card
 * measuring itself, splitting with a regex and building compass strings every frame).
 */
final class TaskHud {
    private TaskHud() {}

    /** The widest line (unscaled px) before a step wraps (it was 185 and a long step lost its end; 215 keeps most steps on one line or two); how long a ticked step lingers; the flash. */
    static final int MAX_W = 215, LINGER = 50, FLASH = 24;
    /** Half the Now card's size, but never so small a font pixel is under two screen pixels. */
    static final float SIZE = 0.45F, MIN_PIXEL = 2.0F;
    /** Rows after the current one, and the most lines one step may wrap over. */
    static final int AHEAD = 2, MAX_LINES = 3;

    private static SignalPayloads.Task task = empty();

    private static SignalPayloads.Task empty() {
        return new SignalPayloads.Task("", "", List.of(), net.minecraft.resources.Identifier.withDefaultNamespace("overworld"));
    }
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
        SHOW.changed();
    }

    // ---- the clear screen (SPEC §40.6): 8 s after a change; a step that teaches a key stays until it's pressed ----------

    private static final dev.awaken.signal.ui.CornerShow.TaskCard SHOW = new dev.awaken.signal.ui.CornerShow.TaskCard();
    /** How strongly the card shows (eased a tick at a time: in fast, out slowly). */
    private static float shown = 1F, shownO = 1F;

    private static void tickShow(Minecraft mc) {
        shownO = shown;
        boolean want;
        if (dev.awaken.core.api.HudState.classic() || task.title().isEmpty()) want = !task.title().isEmpty();
        else {
            SignalPayloads.TaskRow cur = null;
            for (SignalPayloads.TaskRow r : task.rows()) {
                if (!r.done()) {
                    cur = r;
                    break;
                }
            }
            boolean teaches = cur != null && cur.text().contains("{key}") && !cur.key().isEmpty();
            KeyMapping k = teaches ? KeyMapping.get(cur.key()) : null;
            want = SHOW.tick(k != null, k != null && k.isDown(), dev.awaken.core.api.HudState.fighting())
                    || dev.awaken.core.api.HudState.glancing();
        }
        shown = want ? Math.min(1F, shown + 0.25F) : Math.max(0F, shown - 1F / 14F);
    }

    /** How strongly the card shows this frame, eased. */
    private static float shown(float pt) {
        float k = Mth.lerp(pt, shownO, shown);
        return k * k * (3 - 2 * k);
    }

    static void tick() {
        ticks++;
        topO = top;
        float want = SignalLayout.MARGIN + SystemHud.liveToasts() * SignalLayout.TOAST_H;
        top += (want - top) * 0.25F;
        Minecraft mc = Minecraft.getInstance();
        if (!task.title().isEmpty() && mc.player != null) layout = measure(task, mc.font, mc, layout);
        tickShow(mc);
    }

    static void clear() {
        task = empty();
        SHOW.reset();
        shown = shownO = 1F;
    }

    /**
     * The current step's place, when it sends you somewhere (SPEC §27.11): the Now line shows it with its compass. A
     * hint nearby (a ledge to try) is the card's own and isn't one.
     */
    static SignalPayloads.@org.jspecify.annotations.Nullable TaskRow place() {
        return dev.awaken.signal.NowCorner.taskPlace(task);
    }

    /** The task as the server last sent it. */
    static SignalPayloads.Task task() {
        return task;
    }

    /** The card points only at a hint nearby; a step's real place has the Now line's compass, top left. */
    private static boolean pointsHere(SignalPayloads.TaskRow r) {
        return r.marked() && r.place().isEmpty();
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

    /** Whether the card draws this frame (the layout is the task's). */
    private static boolean showing(Minecraft mc, SignalPayloads.Task t, @org.jspecify.annotations.Nullable Layout lay) {
        return !t.title().isEmpty() && lay != null && lay.task == t && mc.player != null && !mc.gui.hud.isHidden() && !mc.getDebugOverlay().showDebugScreen();
    }

    /** Half the Now card, following the System's own scale (and so the GUI scale), but always legible. */
    private static double cardScale(int guiScale) {
        return Math.max(SystemHud.scale() * SIZE, MIN_PIXEL / Math.max(1, guiScale));
    }

    /** The top-right stack as the run panel reads it: under the toasts, this card and the boss bars it would reach. */
    static final dev.awaken.core.api.UiHooks.TopRight STACK = new dev.awaken.core.api.UiHooks.TopRight() {
        @Override
        public float below(float partialTick, float width) {
            Minecraft mc = Minecraft.getInstance();
            Layout lay = layout;
            float k = shown(partialTick);
            boolean card = showing(mc, task, lay) && k > 0.01F;
            // a card stepping aside gives its room back as it fades, so the run panel eases up under it
            return SignalLayout.stackBelow(Mth.lerp(partialTick, topO, top), card ? (float) (lay.w * lay.scale) : 0, card ? (float) (lay.h * lay.scale * k) : 0,
                    mc.getWindow().getGuiScaledWidth(), width, TrackerHud.bossBottom());
        }

        @Override
        public float scale() {
            return (float) cardScale(Minecraft.getInstance().getWindow().getGuiScale());
        }
    };

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        SignalPayloads.Task t = task;
        Layout lay = layout;
        if (!showing(mc, t, lay)) return;
        Font font = mc.font;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float k = shown(pt);
        if (k <= 0.01F) return;
        float fade = Mth.clamp((ticks - since + pt) / 10F, 0, 1) * k;
        double s = lay.scale;
        int w = lay.w, h = lay.h;
        int sw = g.guiWidth();
        float y = SignalLayout.clearBossBars(Mth.lerp(pt, topO, top), sw, (float) (w * s), TrackerHud.bossBottom());
        int x = (int) Math.round(sw - w * s - SignalLayout.MARGIN + (1 - k) * 8); // slides in from the edge

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
            if (ago < FLASH) g.fill(2, ry - 1, w - 2, ry - 1 + row.height() - (row.lines.size() > 1 ? 2 : 0), Glass.a(Glass.GOOD, 0.35f * (1 - (ago + pt) / FLASH) * fade));
            int markColour = r.done() ? Glass.GOOD : row.now ? Glass.AMBER : Glass.LABEL;
            HudLook.text(g, font, row.mark, 3, ry, Glass.a(markColour, rowFade), true);
            int textColour = r.done() || !row.now ? Glass.LABEL : Glass.TEXT;
            int argb = Glass.a(textColour, rowFade * (r.done() || row.now ? 1f : 0.8f));
            for (List<TaskWrap.Seg> line : row.lines) {
                int cx = 11;
                for (TaskWrap.Seg seg : line) {
                    if (seg.kind() == TaskWrap.KEY) cx += keyCap(g, font, seg.text(), row.keyW, cx, ry, rowFade);
                    else {
                        HudLook.text(g, font, seg.text(), cx, ry, seg.kind() == TaskWrap.TAIL ? Glass.a(Glass.EDGE, rowFade) : argb, true);
                        cx += seg.w();
                    }
                }
                ry += 10;
            }
            ry += row.lines.size() > 1 ? 2 : 0;
        }
        g.pose().popMatrix();
    }

    /** One shown step, measured: its mark, its words (shortened to fit), a key cap, then the count and compass. */
    private record Row(int index, boolean now, String mark, List<List<TaskWrap.Seg>> lines, int keyW) {
        /** Rows are a line high for each of their lines, and a little more after one that wrapped. */
        int height() {
            return lines.size() * 10 + (lines.size() > 1 ? 2 : 0);
        }
    }

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
            compasses.add(pointsHere(r) ? compass(mc, r) : "");
            keys.add(r.text().contains("{key}") ? keyName(r.key()) : "");
        }
        if (was != null && was.task == t && was.guiScale == gui && was.shown.equals(shown) && was.compasses.equals(compasses) && was.keys.equals(keys)) {
            return was;
        }
        double scale = cardScale(gui);
        int done = 0;
        for (SignalPayloads.TaskRow r : t.rows()) if (r.done()) done++;
        String title = t.title().toUpperCase(Locale.ROOT), count = done + "/" + t.rows().size();
        int countW = font.width(count);
        // the widest line decides the card (unscaled), up to MAX_W; longer steps are shortened to fit
        int w = font.width(title) + 10 + countW;
        for (int k = 0; k < shown.size(); k++) w = Math.max(w, 11 + rowWidth(font, t.rows().get(shown.get(k)), keys.get(k), compasses.get(k)));
        w = Math.min(MAX_W, w) + 8;
        int room = w - 15;
        List<Row> rows = new java.util.ArrayList<>(shown.size());
        boolean current = false;
        int h = 4 + 10 + 2;
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            SignalPayloads.TaskRow r = t.rows().get(i);
            boolean now = !r.done() && !current;
            if (now) current = true;
            String mark = r.done() ? "\u2714" : now ? "\u25B8" : "\u00B7";
            String tail = (r.need() > 0 ? " " + r.have() + "/" + r.need() : "") + (pointsHere(r) ? "  " + compasses.get(k) : "");
            boolean hasKey = r.text().contains("{key}");
            int keyW = hasKey ? font.width(keys.get(k)) + 4 : 0;
            // a long step wraps over up to three lines; the key cap sits in the line like a word
            var lines = TaskWrap.wrap(r.text(), hasKey ? keys.get(k) : null, keyW + 2, tail, room, MAX_LINES, font::width);
            Row row = new Row(i, now, mark, lines, keyW);
            rows.add(row);
            h += row.height();
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
        if (pointsHere(r)) w += font.width("  " + compass);
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
