package dev.awaken.core.client.ui;

import dev.awaken.signal.client.Glass;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The parts every AWAKEN screen is built from (SPEC §57): a thing in its well, lit by its state; what something takes, as
 * the real item with have and need; a recipe drawn as a recipe; a number with its caption; a needle and a distance; key
 * caps and the bar of keys; buttons, chips and the name under the pointer. All on a {@link Sheet}, in sheet pixels. Every
 * icon is the real item, drawn by the game: nothing here draws a picture of its own.
 */
public final class Kit {
    private Kit() {}

    /** How a thing is lit: not found yet, known, ready to act on, yours already, waiting on you, in focus. */
    public enum Lit { NOT_FOUND, KNOWN, READY, YOURS, WAITS, FOCUS }

    private static final int WELL = 0x05080F;

    // ---- a thing --------------------------------------------------------------------------------------------------

    /** The well a thing sits in: dark glass and a rim that says its state. {@code over}: the pointer is on it. */
    public static void well(Sheet s, float x0, float y0, float x1, float y1, Lit lit, boolean over) {
        int rim = switch (lit) {
            case FOCUS -> Glass.a(0xC4E6FF, 1F);
            case WAITS -> Glass.a(Glass.AMBER, s.bright ? 1F : .78F);
            case READY -> Glass.a(Glass.EDGE, .85F);
            case YOURS -> Glass.a(Glass.LANTERN, .6F);
            default -> Glass.a(s.bright ? 0xC4E6FF : Glass.EDGE, s.bright ? .7F : over ? .6F : .16F);
        };
        int fill = lit == Lit.YOURS ? Sheet.argb(0x3A2A12, .55F) : lit == Lit.FOCUS || over ? Sheet.argb(0x1B3350, .62F) : Sheet.argb(WELL, .40F);
        if (lit == Lit.FOCUS) s.glow(x0, y0, x1, y1, Glass.EDGE, .45F);
        else if (lit == Lit.WAITS) s.glow(x0, y0, x1, y1, Glass.AMBER, .22F);
        s.fill(x0, y0, x1, y1, fill);
        s.outline(x0, y0, x1, y1, lit == Lit.FOCUS || lit == Lit.WAITS ? 2 : 1, rim);
    }

    /** A thing: its well and the real item in the middle of it. Not found: the well alone, with a dark mark. */
    public static void thing(Sheet s, ItemStack stack, float x, float y, float size, Lit lit, boolean over) {
        well(s, x, y, x + size, y + size, lit, over);
        float in = Math.round(size * .14F);
        if (lit == Lit.NOT_FOUND || stack.isEmpty()) unknown(s, x + size / 2F, y + size / 2F, size * .22F);
        else s.item(stack, x + in, y + in, size - 2 * in);
    }

    /** What stands in for something you haven't found: a dark diamond with a faint edge. It gives nothing away. */
    // ---- creatures as themselves ------------------------------------------------------------------------------------

    private static final java.util.Map<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.LivingEntity> BODIES = new java.util.HashMap<>();
    private static net.minecraft.world.level.Level bodiesOf;

    /**
     * A creature drawn as itself in a box, turned by {@code turn} (radians, 0 faces you) and tipped by {@code tilt}: a beast
     * that lives with you, a creature you've found (SPEC §57.7). Only ever something the player has met: what isn't found
     * is {@link #unknown}. One body per kind is kept for the world; a kind that can't stand in a box (or whose renderer
     * balks at a body that was never in the world) returns false, once, and its item is drawn instead.
     */
    public static boolean creature(Sheet s, net.minecraft.world.entity.EntityType<?> type, float x0, float y0, float x1, float y1, float turn, float tilt) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || type == null) return false;
        if (bodiesOf != mc.level) {
            BODIES.clear();
            bodiesOf = mc.level;
        }
        net.minecraft.world.entity.LivingEntity e = BODIES.get(type);
        if (e == null) {
            if (BODIES.containsKey(type)) return false;
            try {
                e = type.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.LOAD) instanceof net.minecraft.world.entity.LivingEntity l ? l : null;
            } catch (RuntimeException ex) {
                e = null;
            }
            // a body of its own number, below every real one: code that reads an entity's id while it is drawn (a name plate)
            // gets an answer instead of "ID before ID assignment" (seen on the first tour: every body fell back to its egg)
            if (e != null) e.setId(-1000 - BODIES.size());
            BODIES.put(type, e);
            if (e == null) return false;
        }
        // as big as the box takes: its height, or its length where it is longer than tall (a body is wider than its hit box)
        float span = Math.max(e.getBbHeight(), e.getBbWidth() * 1.5F), box = Math.min(x1 - x0, y1 - y0);
        int size = Math.max(4, Math.round(box * .74F / Math.max(.3F, span) * s.k));
        try {
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsAngle(s.g, s.gx(x0), s.gy(y0), s.gx(x1), s.gy(y1),
                    size, .0625F, turn, tilt, e);
        } catch (RuntimeException ex) {
            BODIES.put(type, null);
            return false;
        }
        return true;
    }

    public static void unknown(Sheet s, float cx, float cy, float r) {
        s.g.pose().pushMatrix().translate(cx, cy).rotate((float) (Math.PI / 4));
        int n = Math.round(r);
        s.g.fill(-n, -n, n, n, Sheet.argb(0x02040A, .9F));
        s.g.fill(-n, -n, n, -n + 1, Glass.a(Glass.EDGE, .35F));
        s.g.fill(-n, n - 1, n, n, Glass.a(Glass.EDGE, .35F));
        s.g.fill(-n, -n + 1, -n + 1, n - 1, Glass.a(Glass.EDGE, .35F));
        s.g.fill(n - 1, -n + 1, n, n - 1, Glass.a(Glass.EDGE, .35F));
        s.g.pose().popMatrix();
    }

    /** A mark: a diamond, lit when it's yours, rimmed in amber when it's close, dark when it isn't yet. */
    public static void mark(Sheet s, float cx, float cy, float r, int rgb, float fill, float rim, boolean focus) {
        if (focus) s.glow(cx - r, cy - r, cx + r, cy + r, Glass.EDGE, .5F);
        s.g.pose().pushMatrix().translate(cx, cy).rotate((float) (Math.PI / 4));
        int n = Math.round(r * .7F);
        s.g.fill(-n, -n, n, n, fill > 0F ? Glass.a(rgb, fill) : Sheet.argb(0x05080F, .7F));
        int edge = focus ? Glass.a(0xC4E6FF, 1F) : Glass.a(rgb, rim);
        s.g.fill(-n, -n, n, -n + 2, edge);
        s.g.fill(-n, n - 2, n, n, edge);
        s.g.fill(-n, -n + 2, -n + 2, n - 2, edge);
        s.g.fill(n - 2, -n + 2, n, n - 2, edge);
        s.g.pose().popMatrix();
    }

    /** The band's lit floor under a thing brought into focus: a flat disc of light, {@code half} wide each way from cx. */
    public static void floor(Sheet s, float cx, float cy, float half) {
        int rows = 9;
        for (int i = -rows; i <= rows; i++) {
            float t = i / (float) rows, w = half * (float) Math.sqrt(1F - t * t);
            s.fill(cx - w, cy + i * 2, cx + w, cy + i * 2 + 2, Glass.a(Glass.EDGE, .075F));
            if (Math.abs(i) < rows - 2) s.fill(cx - w * .62F, cy + i * 2, cx + w * .62F, cy + i * 2 + 2, Glass.a(Glass.EDGE, .06F));
        }
    }

    /** A condition a step asks for that isn't a thing ("In one closed room"): a tick when it holds, a ring when it doesn't. */
    public static void condition(Sheet s, String words, boolean met, float x, float y) {
        if (met) s.text("✓", x, y, Sheet.BODY, Glass.a(Glass.GOOD, 1F));
        else s.outline(x + 2, y + 2, x + 14, y + 14, 2, Glass.a(s.label, .8F));
        s.text(words, x + 24, y, Sheet.BODY, Glass.a(met ? Glass.GOOD : Glass.TEXT, 1F));
    }

    /** The glint on something new, or (amber) something that waits on you. */
    /** As {@link #condition}, with every line of its words (SPEC §57.6): returns the y under it. */
    public static float conditionLines(Sheet s, String words, boolean met, float x, float y, float width) {
        if (met) s.text("✓", x, y, Sheet.BODY, Glass.a(Glass.GOOD, 1F));
        else s.outline(x + 2, y + 2, x + 14, y + 14, 2, Glass.a(s.label, .8F));
        return s.lines(words, x + 24, y, width - 24, Sheet.BODY, Glass.a(met ? Glass.GOOD : Glass.TEXT, 1F)) + 6;
    }

    /** Pips for a count out of a few (a home's Comfort, a room's reading): {@code of} squares, {@code met} of them lit in {@code rgb}. */
    public static float pips(Sheet s, float x, float y, int met, int of, float size, int rgb) {
        float step = size + Math.max(2F, Math.round(size * .4F));
        for (int i = 0; i < of; i++) s.fill(x + i * step, y, x + i * step + size, y + size, i < met ? Glass.a(rgb, 1F) : Sheet.argb(0x6F829C, .45F));
        return of * step;
    }

    public static void glint(Sheet s, float cx, float cy, int rgb) {
        s.glow(cx - 5, cy - 5, cx + 5, cy + 5, rgb, .5F);
        s.g.pose().pushMatrix().translate(cx, cy).rotate((float) (Math.PI / 4));
        s.g.fill(-4, -4, 4, 4, Glass.a(rgb, 1F));
        s.g.pose().popMatrix();
    }

    // ---- what it takes ----------------------------------------------------------------------------------------------

    public static final float NEED_W = 112F;

    /** How tall {@link #need} draws: the item, the count if it has one, and every line of its name. */
    public static float needHeight(Sheet s, String name, int need) {
        return 62 + (need > 0 ? 28 : 0) + s.tall(name, NEED_W, Sheet.LABEL) + 4;
    }

    /**
     * One need: the real item, how many you have of how many it takes, and its name under that, every line of it. Returns
     * whether the pointer is on it (its tip is the caller's to draw, last).
     */
    public static boolean need(Sheet s, ItemStack icon, String name, int have, int need, float x, float y, float mx, float my) {
        boolean met = have >= need, over = Sheet.in(mx, my, x, y, x + NEED_W, y + needHeight(s, name, need));
        float cx = x + NEED_W / 2F;
        if (over) s.glow(cx - 30, y, cx + 30, y + 60, Glass.EDGE, .3F);
        s.item(icon, cx - 28, y + 2, 56);
        if (need > 0) s.centre(Math.min(have, need) + "/" + need, cx, y + 62, Sheet.TITLE, Glass.a(met ? Glass.GOOD : Glass.WARN, 1F));
        s.linesCentred(name, cx, y + (need > 0 ? 90 : 62), NEED_W, Sheet.LABEL, Glass.a(s.label, 1F));
        return over;
    }

    /** How wide {@link #recipe} draws: the grid, the arrow and what it makes. */
    public static float recipeWidth(int w, float cell, float result) {
        return w * (cell + 2) + 44 + result;
    }

    /** How tall: the grid or what it makes, whichever is taller. */
    public static float recipeHeight(int h, float cell, float result) {
        return Math.max(h * (cell + 2) - 2, result);
    }

    /**
     * A recipe as Minecraft draws one: the grid ({@code w} across, {@code h} down, {@code cell} sheet pixels a square; an
     * empty stack is an empty square), an arrow, and what it makes, {@code result} sheet pixels across.
     */
    public static void recipe(Sheet s, int w, int h, List<ItemStack> cells, ItemStack made, float x, float y, float cell, float result) {
        float gh = h * (cell + 2) - 2, tall = Math.max(gh, result), gy = y + (tall - gh) / 2F, in = Math.round(cell * .11F);
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int i = row * w + col;
                float cx = x + col * (cell + 2), cy = gy + row * (cell + 2);
                s.fill(cx, cy, cx + cell, cy + cell, Sheet.argb(WELL, .6F));
                s.outline(cx, cy, cx + cell, cy + cell, 1, Glass.a(Glass.EDGE, .22F));
                if (i < cells.size()) s.item(cells.get(i), cx + in, cy + in, cell - 2 * in);
            }
        }
        float ax = x + w * (cell + 2) + 8, ay = y + tall / 2F;
        s.fill(ax, ay - 2, ax + 20, ay + 2, Glass.a(Glass.EDGE, .8F));
        for (int i = 0; i < 8; i++) s.fill(ax + 20 + i, ay - 8 + i, ax + 21 + i, ay + 8 - i, Glass.a(Glass.EDGE, .8F));
        s.item(made, ax + 36, y + (tall - result) / 2F, result);
    }

    // ---- numbers and places -----------------------------------------------------------------------------------------

    /** A number and what it is: the value at size n, its caption in spaced capitals under it. Returns its width. */
    public static float number(Sheet s, String value, String caption, float x, float y, float n, int rgb) {
        s.lit(value, x, y, n, Glass.a(rgb, 1F));
        s.caps(caption, x, y + 8 * s.snap(n) + 6, Glass.a(s.label, 1F));
        return Math.max(s.width(value, n), s.width(caption.toUpperCase(java.util.Locale.ROOT), Sheet.LABEL));
    }

    /** A ring with a needle to a place: up is the way you face. {@code degrees} NaN: the way isn't known (no needle). */
    public static void needle(Sheet s, float cx, float cy, float r, float degrees) {
        int ring = Glass.a(Glass.EDGE, .5F);
        int steps = Math.max(16, Math.round(r * .9F));
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps, a1 = Math.PI * 2 * (i + 1) / steps;
            s.ray(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 2F, ring);
        }
        if (Float.isNaN(degrees)) return;
        float t = Math.max(3F, r / 14F);
        s.g.pose().pushMatrix().translate(cx, cy).rotate((float) Math.toRadians(degrees));
        s.g.fill(Math.round(-t / 2), Math.round(-r * .82F), Math.round(t / 2 + .5F), 0, Glass.a(Glass.AMBER, 1F));
        s.g.fill(Math.round(-t / 2), 0, Math.round(t / 2 + .5F), Math.round(r * .3F), Glass.a(s.label, .5F));
        s.g.pose().popMatrix();
    }

    /** Dots for how far along something is: {@code met} of {@code of}. */
    public static void pips(Sheet s, float x, float y, int met, int of) {
        for (int i = 0; i < Math.min(of, 8); i++) {
            float px = x + i * 11;
            s.fill(px, y, px + 8, y + 8, i < met ? Glass.a(Glass.GOOD, 1F) : Sheet.argb(0x6F829C, .55F));
        }
    }

    // ---- keys and the bar of keys -----------------------------------------------------------------------------------

    private static final float KEY_H = 26F;

    /** One key cap; amber for one you hold. Returns its width. */
    public static float key(Sheet s, String key, float x, float y, boolean hold) {
        float tw = s.width(key, Sheet.LABEL), w = Math.max(KEY_H, tw + 14);
        s.fill(x, y, x + w, y + KEY_H, Sheet.argb(0x05080F, .7F));
        int rim = hold ? Glass.a(Glass.AMBER, 1F) : Glass.a(s.label, .6F);
        s.outline(x, y, x + w, y + KEY_H, 1, rim);
        s.fill(x, y + KEY_H, x + w, y + KEY_H + 2, hold ? Glass.a(Glass.AMBER, .5F) : Glass.a(s.label, .35F));
        s.text(key, x + (w - tw) / 2F, y + (KEY_H - 7 * s.snap(Sheet.LABEL)) / 2F, Sheet.LABEL, Glass.a(hold ? Glass.AMBER : Glass.TITLE, 1F));
        return w;
    }

    /** What one hint says: its keys ("Hold" first for a held one), and what they do, in a few plain words. */
    // ---- the same caps where a screen is laid out in GUI pixels (a card, the bag, the Look screen: SPEC §57.9) -------

    /** A key cap in GUI pixels, the sheet's cap at the game's own text size: returns its width. Ten pixels tall, from y - 1. */
    public static int cap(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String key, int x, int y, float a, boolean hold) {
        int tw = font.width(key), w = Math.max(11, tw + 6);
        g.fill(x, y - 1, x + w, y + 9, Sheet.argb(0x05080F, .7F * a));
        int rim = hold ? Glass.a(Glass.AMBER, a) : Glass.a(Glass.LABEL, .6F * a);
        g.fill(x, y - 1, x + w, y, rim);
        g.fill(x, y + 8, x + w, y + 9, rim);
        g.fill(x, y, x + 1, y + 8, rim);
        g.fill(x + w - 1, y, x + w, y + 8, rim);
        g.fill(x, y + 9, x + w, y + 10, hold ? Glass.a(Glass.AMBER, .5F * a) : Glass.a(Glass.LABEL, .35F * a));
        g.text(font, key, x + (w - tw) / 2 + (tw % 2 == 0 ? 0 : 1), y, Glass.a(hold ? Glass.AMBER : Glass.TITLE, a), false);
        return w;
    }

    /** A line of hints in GUI pixels from x: each its caps, then its words. Returns the x after the last. */
    public static int hintLine(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, List<Hint> hints, int x, int y, float a) {
        for (Hint h : hints) {
            if (h.hold()) x += cap(g, font, "Hold", x, y, a, true) + 2;
            for (String k : h.keys()) x += cap(g, font, k, x, y, a, false) + 2;
            g.text(font, h.label(), x + 2, y, Glass.a(Glass.LABEL, a), false);
            x += font.width(h.label()) + 12;
        }
        return x;
    }

    public static int hintLineWidth(net.minecraft.client.gui.Font font, List<Hint> hints) {
        int w = 0;
        for (Hint h : hints) {
            if (h.hold()) w += Math.max(11, font.width("Hold") + 6) + 2;
            for (String k : h.keys()) w += Math.max(11, font.width(k) + 6) + 2;
            w += font.width(h.label()) + 12;
        }
        return Math.max(0, w - 10);
    }

    public record Hint(String label, boolean hold, String... keys) {
        public static Hint of(String label, String... keys) {
            return new Hint(label, false, keys);
        }

        public static Hint hold(String label, String... keys) {
            return new Hint(label, true, keys);
        }
    }

    /** One hint from x: its key caps, then its words. Returns the x after it. */
    public static float hint(Sheet s, Hint h, float x, float y) {
        if (h.hold) x += key(s, "Hold", x, y, true) + 4;
        for (String k : h.keys) x += key(s, k, x, y, false) + 4;
        x += 4;
        s.text(h.label, x, y + (KEY_H - 7 * s.snap(Sheet.BODY)) / 2F, Sheet.BODY, Glass.a(s.label, 1F));
        return x + s.width(h.label, Sheet.BODY);
    }

    public static float hintWidth(Sheet s, Hint h) {
        float w = 4 + s.width(h.label, Sheet.BODY);
        if (h.hold) w += Math.max(KEY_H, s.width("Hold", Sheet.LABEL) + 14) + 4;
        for (String k : h.keys) w += Math.max(KEY_H, s.width(k, Sheet.LABEL) + 14) + 4;
        return w;
    }

    /** The bar of keys: {@code left} from x0 on, {@code right} ending at x1. The same place on every screen. */
    public static void hints(Sheet s, List<Hint> left, List<Hint> right, float x0, float x1, float y) {
        float x = x0;
        for (Hint h : left) x = hint(s, h, x, y) + 26;
        float w = 0;
        for (Hint h : right) w += hintWidth(s, h) + 26;
        x = x1 - Math.max(0, w - 26);
        for (Hint h : right) x = hint(s, h, x, y) + 26;
    }

    // ---- buttons and chips ------------------------------------------------------------------------------------------

    public static final float BUTTON_H = 44F;

    /** What a button looks like: the one to take, a plain one, one that's on (tracked), a quiet one. */
    public enum Look { PRIMARY, PLAIN, ON, QUIET }

    public static float buttonWidth(Sheet s, String key, String label) {
        return 36 + s.width(label, Sheet.BODY) + (key.isEmpty() ? 0 : Math.max(KEY_H, s.width(key, Sheet.LABEL) + 14) + 10);
    }

    /**
     * A button from (x, y): an optional key cap, then its words. {@code held} 0..1 fills it for a hold. Returns its width;
     * the caller adds its rectangle to its {@link Hot} zones with what a click does.
     */
    public static float button(Sheet s, String key, String label, float x, float y, Look look, boolean over, boolean enabled, float held) {
        float w = buttonWidth(s, key, label);
        if (look != Look.QUIET) {
            int rgb = look == Look.ON ? Glass.AMBER : Glass.EDGE;
            int fill = !enabled ? Sheet.argb(0x1A2232, .7F) : look == Look.ON ? Glass.a(Glass.AMBER, .14F)
                    : over ? Glass.a(Glass.EDGE, .28F) : look == Look.PRIMARY ? Glass.a(Glass.EDGE, .20F) : Sheet.argb(0x1B2A44, .75F);
            if (enabled && (over || look == Look.PRIMARY || look == Look.ON)) s.glow(x, y, x + w, y + BUTTON_H, rgb, over ? .35F : .2F);
            s.fill(x, y, x + w, y + BUTTON_H, fill);
            if (held > 0F) s.fill(x, y, x + w * Math.min(1F, held), y + BUTTON_H, Glass.a(Glass.AMBER, .35F));
            s.outline(x, y, x + w, y + BUTTON_H, look == Look.PLAIN ? 1 : 2, Glass.a(enabled ? rgb : s.label, enabled ? (over ? 1F : look == Look.PLAIN ? .6F : .9F) : .3F));
        }
        float tx = x + 18;
        if (!key.isEmpty()) tx += key(s, key, tx, y + (BUTTON_H - KEY_H) / 2F - 1, false) + 10;
        int ink = !enabled ? Glass.a(s.label, .6F) : look == Look.ON ? Glass.a(Glass.AMBER, 1F) : look == Look.QUIET && !over ? Glass.a(s.label, 1F) : Glass.a(Glass.TITLE, 1F);
        s.text(label, tx, y + (BUTTON_H - 7 * s.snap(Sheet.BODY)) / 2F, Sheet.BODY, ink);
        return w;
    }

    /** A chip: where you are, or what waits. Returns its width. */
    public static float chip(Sheet s, ItemStack icon, String words, float x, float y, int rgb) {
        String t = words.toUpperCase(java.util.Locale.ROOT);
        float w = 24 + s.width(t, Sheet.LABEL) + (icon.isEmpty() ? 0 : 28);
        s.fill(x, y, x + w, y + 30, Glass.a(rgb, .10F));
        s.outline(x, y, x + w, y + 30, 1, Glass.a(rgb, .6F));
        float tx = x + 12;
        if (!icon.isEmpty()) {
            s.item(icon, tx, y + 5, 20);
            tx += 28;
        }
        s.text(t, tx, y + (30 - 7 * s.snap(Sheet.LABEL)) / 2F, Sheet.LABEL, Glass.a(rgb, 1F));
        return w;
    }

    /** The name under the pointer, in the band's glass: a white line, then quieter ones. Drawn last, on a stratum of its own. */
    public static void tip(Sheet s, float mx, float my, String title, List<String> more) {
        java.util.ArrayList<FormattedCharSequence> head = new java.util.ArrayList<>(s.wrap(title, 480F, Sheet.BODY)), lines = new java.util.ArrayList<>();
        for (String m : more) lines.addAll(s.wrap(m, 480F, Sheet.LABEL));
        float w = 0;
        for (FormattedCharSequence l : head) w = Math.max(w, s.font.width(l) * s.snap(Sheet.BODY));
        for (FormattedCharSequence l : lines) w = Math.max(w, s.font.width(l) * s.snap(Sheet.LABEL));
        float h = 20 + head.size() * s.line(Sheet.BODY) + lines.size() * s.line(Sheet.LABEL) - 4;
        float x = Math.max(8, Math.min(mx + 18, Sheet.W - w - 34)), y = Math.max(8, Math.min(my + 18, Sheet.H - h - 8));
        s.g.nextStratum();
        s.fill(x, y, x + w + 24, y + h, Sheet.argb(0x0F1724, .96F));
        s.outline(x, y, x + w + 24, y + h, 1, Glass.a(Glass.EDGE, .6F));
        float ty = y + 10;
        for (FormattedCharSequence l : head) {
            s.text(l, x + 12, ty, Sheet.BODY, Glass.a(Glass.TITLE, 1F));
            ty += s.line(Sheet.BODY);
        }
        for (FormattedCharSequence l : lines) {
            s.text(l, x + 12, ty, Sheet.LABEL, Glass.a(s.label, 1F));
            ty += s.line(Sheet.LABEL);
        }
    }

    /** A thin bar at the right of something that scrolls: how far down {@code scroll} is of {@code tall}, seen through {@code view}. */
    public static void scrollbar(Sheet s, float x, float y0, float y1, float scroll, float tall) {
        float view = y1 - y0;
        if (tall <= view) return;
        float bar = Math.max(40F, view * view / tall), by = y0 + (view - bar) * scroll / (tall - view);
        s.fill(x, by, x + 4, by + bar, Glass.a(Glass.EDGE, .5F));
    }

    /** A hairline that fades to the right: under a heading, between groups. */
    public static void hair(Sheet s, float x0, float x1, float y) {
        float step = (x1 - x0) / 8F;
        for (int i = 0; i < 8; i++) s.fill(x0 + i * step, y, x0 + (i + 1) * step, y + 1, Glass.a(Glass.EDGE, .45F * (1F - i / 8F)));
    }
}
