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
 */
final class TaskHud {
    private TaskHud() {}

    /** The widest line (unscaled px) before a step is shortened; how long a ticked step lingers; the flash. */
    static final int MAX_W = 150, LINGER = 50, FLASH = 24;
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
        if (t.title().isEmpty() || mc.player == null || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float fade = Mth.clamp((ticks - since + pt) / 10F, 0, 1);
        // half the Now card, following the System's own scale (and so the GUI scale), but always legible
        double gui = Math.max(1, mc.getWindow().getGuiScale());
        double s = Math.max(SystemHud.scale() * SIZE, MIN_PIXEL / gui);
        int done = 0;
        for (SignalPayloads.TaskRow r : t.rows()) if (r.done()) done++;
        String title = t.title().toUpperCase(Locale.ROOT), count = done + "/" + t.rows().size();
        List<Integer> rows = shown(t);
        // the widest line decides the card (unscaled), up to MAX_W; longer steps are shortened to fit
        int w = font.width(title) + 10 + font.width(count);
        for (int i : rows) w = Math.max(w, 11 + rowWidth(font, t.rows().get(i), mc));
        w = Math.min(MAX_W, w) + 8;
        int room = w - 15;
        int h = 4 + 10 + rows.size() * 10 + 2;
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
        String shortTitle = SignalTextFit.fit(font, title, w - 12 - font.width(count));
        HudLook.text(g, font, shortTitle, 4, 3, Glass.a(Glass.EDGE, fade), true);
        HudLook.text(g, font, count, w - 4 - font.width(count), 3, Glass.a(done == t.rows().size() ? Glass.GOOD : Glass.LABEL, fade), true);
        int ry = 14;
        boolean current = false;
        for (int i : rows) {
            SignalPayloads.TaskRow r = t.rows().get(i);
            boolean now = !r.done() && !current;
            if (now) current = true;
            // a step that just ticked: one soft flash of green, then it fades out of the list
            long ago = i < TICKED.length ? ticks - TICKED[i] : 1000;
            float rowFade = fade * (r.done() && ago < LINGER ? Mth.clamp((LINGER - ago - pt) / 16F, 0, 1) : 1f);
            if (ago < FLASH) g.fill(2, ry - 1, w - 2, ry + 9, Glass.a(Glass.GOOD, 0.35f * (1 - (ago + pt) / FLASH) * fade));
            String mark = r.done() ? "\u2714" : now ? "\u25B8" : "\u00B7";
            int markColour = r.done() ? Glass.GOOD : now ? Glass.AMBER : Glass.LABEL;
            HudLook.text(g, font, mark, 3, ry, Glass.a(markColour, rowFade), true);
            int textColour = r.done() || !now ? Glass.LABEL : Glass.TEXT;
            drawRow(g, font, r, 11, ry, Glass.a(textColour, rowFade * (r.done() || now ? 1f : 0.8f)), rowFade, mc, room);
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

    /** One line: the words (shortened to fit), a key cap for "{key}", then the count and compass in the band's blue. */
    private static void drawRow(GuiGraphicsExtractor g, Font font, SignalPayloads.TaskRow r, int x, int y, int argb, float fade, Minecraft mc, int room) {
        String tail = (r.need() > 0 ? " " + r.have() + "/" + r.need() : "") + (r.marked() ? "  " + compass(mc, r) : "");
        int left = room - font.width(tail);
        String[] parts = r.text().split("\\{key}", 2);
        int cx = x;
        String before = SignalTextFit.fit(font, parts[0], left);
        HudLook.text(g, font, before, cx, y, argb, true);
        cx += font.width(before);
        left -= font.width(before);
        if (parts.length > 1 && left > keyWidth(font, r.key())) {
            int kw = keyCap(g, font, r.key(), cx, y, fade);
            cx += kw;
            left -= kw;
            String after = SignalTextFit.fit(font, parts[1], left);
            HudLook.text(g, font, after, cx, y, argb, true);
            cx += font.width(after);
        }
        if (!tail.isEmpty()) HudLook.text(g, font, tail, cx, y, Glass.a(Glass.EDGE, fade), true);
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
