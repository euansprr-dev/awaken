package dev.awaken.signal.client;

import dev.awaken.signal.ui.Glossary;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Immediate-mode layout for Status screen sections. One {@code build(ui)} method serves three passes: MEASURE (height),
 * DRAW and CLICK, so the height, the drawing and the hit boxes can never disagree. Every text wraps to the width;
 * glossary words get a faint underline and explain themselves on hover.
 */
public final class Ui {
    private enum Mode { MEASURE, DRAW, CLICK }

    public static final int LINE = 10;
    public static final int BUTTON_H = 13;
    /** Item icons drawn by {@link #itemAt}, {@link Flow#item} and {@link #recipe}: 12 px, the height of a chip. */
    public static final int ICON = 12;

    private final Mode mode;
    private final GuiGraphicsExtractor g;
    private final Font font;
    public final int x, width;
    private final int top;
    private int y;
    private final double mx, my;
    private boolean consumed;
    private boolean tooltipSet;

    private Ui(Mode mode, GuiGraphicsExtractor g, Font font, int x, int y, int width, double mx, double my) {
        this.mode = mode;
        this.g = g;
        this.font = font;
        this.x = x;
        this.y = y;
        this.top = y;
        this.width = Math.max(20, width);
        this.mx = mx;
        this.my = my;
    }

    public static Ui measure(Font font, int width) {
        return new Ui(Mode.MEASURE, null, font, 0, 0, width, -10000, -10000);
    }

    public static Ui draw(GuiGraphicsExtractor g, Font font, int x, int y, int width, int mouseX, int mouseY) {
        return new Ui(Mode.DRAW, g, font, x, y, width, mouseX, mouseY);
    }

    public static Ui click(Font font, int x, int y, int width, double mouseX, double mouseY) {
        return new Ui(Mode.CLICK, null, font, x, y, width, mouseX, mouseY);
    }

    public Font font() { return font; }
    public boolean drawing() { return mode == Mode.DRAW; }
    /** Only valid while {@link #drawing()}. */
    public GuiGraphicsExtractor g() { return g; }
    public int y() { return y; }
    public int height() { return y - top; }
    public boolean consumed() { return consumed; }
    /** The mouse, or far off-screen when it isn't over the content. */
    public int mouseX() { return (int) mx; }
    public int mouseY() { return (int) my; }
    public void gap(int px) { y += px; }

    // ---- text -----------------------------------------------------------------------------------------------------

    public void text(String s, int colour) {
        text(s, colour, 0, false);
    }

    /** Wrapped text, indented by {@code indent} px. Colours are ARGB. */
    public void text(String s, int colour, int indent, boolean shadow) {
        for (String line : SignalText.wrap(s, width - indent, font::width)) {
            if (drawing()) line(line, x + indent, y, colour, shadow);
            y += LINE;
        }
    }

    /** Wrapped text, at most {@code max} lines (the last ends in "…"). */
    public void textAtMost(String s, int colour, int max) {
        for (String line : SignalText.wrapAtMost(s, width, max, font::width)) {
            if (drawing()) line(line, x, y, colour, false);
            y += LINE;
        }
    }

    /** One line at an absolute position, shortened to {@code maxW}. Draw pass only; doesn't move the cursor. */
    public void textAt(String s, int tx, int ty, int maxW, int colour, boolean shadow) {
        if (drawing()) line(SignalText.ellipsize(s, maxW, font::width), tx, ty, colour, shadow);
    }

    /** A label on the left and a value on the right; when both don't fit, the value goes under, indented. */
    public void pair(String left, String right, int leftColour, int rightColour) {
        int lw = font.width(left), rw = font.width(right);
        if (lw + 8 + rw <= width) {
            if (drawing()) {
                line(left, x, y, leftColour, false);
                line(right, x + width - rw, y, rightColour, false);
            }
            y += LINE;
        } else {
            text(left, leftColour);
            text(right, rightColour, 8, false);
        }
    }

    private void line(String s, int lx, int ly, int colour, boolean shadow) {
        if ((colour >>> 24) == 0) colour |= 0xFF000000; // an RGB constant: opaque (alpha 0 would draw nothing)
        g.text(font, s, lx, ly, colour, shadow);
        explain(s, lx, ly);
    }

    /** Glossary words: faint underline and a wrapped tooltip on hover. */
    private void explain(String line, int lx, int ly) {
        int n = line.length();
        int i = 0;
        while (i < n) {
            if (!Glossary.wordChar(line.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && Glossary.wordChar(line.charAt(j))) j++;
            Optional<String> def = Glossary.lookup(line.substring(i, j));
            if (def.isPresent()) {
                int tx = lx + font.width(line.substring(0, i)), tw = font.width(line.substring(i, j));
                g.horizontalLine(tx, tx + tw - 1, ly + 9, Glass.a(Glass.EDGE, 0.35f));
                if (!tooltipSet && mx >= tx && mx < tx + tw && my >= ly - 1 && my < ly + 10) {
                    g.setTooltipForNextFrame(font, font.split(Component.literal(def.get()), 200), (int) mx, (int) my);
                    tooltipSet = true;
                }
            }
            i = j;
        }
    }

    // ---- shapes ---------------------------------------------------------------------------------------------------

    public void bar(double fraction, int colour) {
        bar(fraction, colour, width);
    }

    public void bar(double fraction, int colour, int w) {
        if (drawing()) Glass.bar(g, x, y, Math.min(w, width), 4, fraction, colour, 1f);
        y += 6;
    }

    public void rule() {
        if (drawing()) g.horizontalLine(x, x + width - 1, y + 2, Glass.a(Glass.EDGE, 0.12f));
        y += 5;
    }

    public boolean hover(int hx, int hy, int hw, int hh) {
        return Glass.inside(mx, my, hx, hy, hw, hh);
    }

    /** A button at an absolute position. Returns true in the click pass when it was clicked. */
    public boolean buttonAt(String label, int bx, int by, int bw, int bh, int accent, boolean enabled) {
        boolean hover = hover(bx, by, bw, bh);
        if (drawing()) {
            Glass.button(g, font, SignalText.ellipsize(label, bw - 6, font::width), bx, by, bw, bh, hover, enabled, accent);
            return false;
        }
        if (mode == Mode.CLICK && enabled && hover && !consumed) {
            consumed = true;
            return true;
        }
        return false;
    }

    // ---- containers and items ---------------------------------------------------------------------------------------

    /**
     * A glass card around {@code body} (a coloured bar on the left), laid out in every pass: measured first so the
     * background fits, then drawn or clicked with the same {@code body}. Clicks inside count as this Ui's.
     */
    public void card(int accent, Consumer<Ui> body) {
        Ui m = new Ui(Mode.MEASURE, null, font, 0, 0, width - 12, -10000, -10000);
        body.accept(m);
        int h = m.height() + 8;
        if (drawing()) Glass.card(g, x, y, width, h, accent, 1f);
        if (mode != Mode.MEASURE) {
            Ui in = new Ui(mode, g, font, x + 8, y + 4, width - 12, mx, my);
            in.tooltipSet = tooltipSet;
            body.accept(in);
            consumed |= in.consumed;
            tooltipSet |= in.tooltipSet;
        }
        y += h;
    }

    /** An item icon at an absolute position, {@code size} px square, with the item's tooltip on hover. Draw pass only. */
    public void itemAt(ItemStack stack, int ix, int iy, int size) {
        if (!drawing() || stack.isEmpty()) return;
        g.pose().pushMatrix();
        g.pose().translate(ix, iy);
        g.pose().scale(size / 16f, size / 16f);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
        if (!tooltipSet && hover(ix, iy, size, size)) {
            g.setTooltipForNextFrame(font, stack, (int) mx, (int) my);
            tooltipSet = true;
        }
    }

    /**
     * A crafting grid: {@code cells} row by row ({@code w} wide, {@code h} high; an empty stack is an empty slot), an
     * arrow, and what it makes with its count.
     */
    public void recipe(int w, int h, List<ItemStack> cells, ItemStack result, int count) {
        int slot = ICON + 1, gx = x + 4, gy = y + 1;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int sx = gx + col * slot, sy = gy + row * slot, i = row * w + col;
                if (drawing()) {
                    g.fill(sx - 1, sy - 1, sx + ICON + 1, sy + ICON + 1, Glass.a(0x0A1020, 0.9f));
                    g.outline(sx - 1, sy - 1, ICON + 2, ICON + 2, Glass.a(Glass.EDGE, 0.18f));
                }
                if (i < cells.size()) itemAt(cells.get(i), sx, sy, ICON);
            }
        }
        int ay = gy + h * slot / 2 - 6;
        textAt("→", gx + w * slot + 4, ay + 2, 12, Glass.a(Glass.LABEL, 1f), false);
        itemAt(result, gx + w * slot + 16, ay, ICON);
        if (count > 1) textAt("×" + count, gx + w * slot + 18 + ICON, ay + 2, 30, Glass.a(Glass.LABEL, 1f), false);
        y += h * slot + 3;
    }

    /** A left-to-right row of buttons and chips that wraps onto new lines. Call {@link Flow#end()} when done. */
    public Flow flow() {
        return new Flow();
    }

    public final class Flow {
        private int fx = x;
        private int rowH;

        private void place(int w, int h) {
            if (fx > x && fx + w > x + width) {
                y += rowH + 3;
                fx = x;
                rowH = 0;
            }
            rowH = Math.max(rowH, h);
        }

        public boolean button(String label, int accent, boolean enabled) {
            int bw = Math.min(width, font.width(label) + 12);
            place(bw, BUTTON_H);
            boolean hit = buttonAt(label, fx, y, bw, BUTTON_H, accent, enabled);
            fx += bw + 4;
            return hit;
        }

        public void chip(String label, int colour) {
            String l = SignalText.ellipsize(label, width - 8, font::width);
            int cw = font.width(l) + 8;
            place(cw, 11);
            if (drawing()) Glass.chip(g, font, l, fx, y, colour);
            fx += cw + 4;
        }

        /** An item icon with a short label after it (a checklist line, a price). */
        public void item(ItemStack stack, String label, int colour) {
            String l = SignalText.ellipsize(label, width - ICON - 3, font::width);
            int w = ICON + 3 + font.width(l);
            place(w, ICON);
            itemAt(stack, fx, y, ICON);
            if (drawing()) line(l, fx + ICON + 3, y + 2, colour, false);
            fx += w + 8;
        }

        public void text(String s, int colour) {
            String l = SignalText.ellipsize(s, width, font::width);
            int tw = font.width(l);
            place(tw, 9);
            if (drawing()) line(l, fx, y + 2, colour, false);
            fx += tw + 6;
        }

        public void end() {
            if (rowH > 0) y += rowH + 3;
            rowH = 0;
            fx = x;
        }
    }

    /** Pretty-prints an id: "awaken:move_speed" → "Move speed". */
    public static String pretty(String id) {
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        s = s.replace('_', ' ').replace('/', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static List<String> wrap(Font font, String s, int w) {
        return SignalText.wrap(s, w, font::width);
    }
}
