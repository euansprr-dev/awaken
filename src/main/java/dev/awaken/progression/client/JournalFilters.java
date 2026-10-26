package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Journal page's filters (SPEC §30.6): EVERYTHING, TASKS, FINDS, MESSAGES as tabs of glass across its top. Click
 * one, or Q / E to step through them. Each section says which it belongs to ({@link StatusSections.Section#shelf}).
 */
final class JournalFilters implements StatusSections.Section {
    private static final int H = 13, GAP = 4;
    private static final StatusSections.@Nullable Shelf[] ORDER = {null, StatusSections.Shelf.TASKS, StatusSections.Shelf.FINDS, StatusSections.Shelf.MESSAGES};

    @Override
    public String title() {
        return "";
    }

    @Override
    public int order() {
        return -100; // above everything on the page
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null; // shows under every filter
    }

    @Override
    public int height(int width) {
        return H + 4;
    }

    private static String label(StatusSections.@Nullable Shelf s) {
        return s == null ? "EVERYTHING" : s.label;
    }

    /** Each tab's width at the label scale. */
    private static int tabW(Font font, StatusSections.@Nullable Shelf s) {
        return Math.round(font.width(label(s)) * StatusSections.micro()) + 10;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float m = StatusSections.micro();
        int tx = x;
        StatusSections.Shelf on = StatusSections.journalFilter();
        for (StatusSections.Shelf s : ORDER) {
            int w = tabW(font, s);
            boolean lit = s == on, hover = mouseX >= tx && mouseX < tx + w && mouseY >= y && mouseY < y + H;
            g.fill(tx, y, tx + w, y + H, Glass.a(lit ? Glass.EDGE : 0x05080F, lit ? 0.22f : hover ? 0.6f : 0.45f));
            g.outline(tx, y, w, H, Glass.a(Glass.EDGE, lit ? 1f : 0.4f));
            g.pose().pushMatrix();
            g.pose().translate(tx + 5, y + (H - 8 * m) / 2f);
            g.pose().scale(m, m);
            g.text(font, label(s), 0, 0, Glass.a(lit ? 0xFFFFFF : Glass.LABEL, 1f), false);
            g.pose().popMatrix();
            tx += w + GAP;
        }
        String keys = "Q / E";
        float kw = font.width(keys) * m;
        if (tx + kw + 4 < x + width) {
            g.pose().pushMatrix();
            g.pose().translate(x + width - kw, y + (H - 8 * m) / 2f);
            g.pose().scale(m, m);
            g.text(font, keys, 0, 0, Glass.a(Glass.LABEL, 0.7f), false);
            g.pose().popMatrix();
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        if (mouseY < y || mouseY >= y + H) return false;
        Font font = Minecraft.getInstance().font;
        int tx = x;
        for (StatusSections.Shelf s : ORDER) {
            int w = tabW(font, s);
            if (mouseX >= tx && mouseX < tx + w) {
                pick(s);
                return true;
            }
            tx += w + GAP;
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent e) {
        int step = e.key() == InputConstants.KEY_Q ? -1 : e.key() == InputConstants.KEY_E ? 1 : 0;
        if (step == 0) return false;
        int at = 0;
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i] == StatusSections.journalFilter()) at = i;
        pick(ORDER[Math.floorMod(at + step, ORDER.length)]);
        return true;
    }

    private static void pick(StatusSections.@Nullable Shelf s) {
        if (s == StatusSections.journalFilter()) return;
        StatusSections.journalFilter(s);
        StatusSections.cue(StatusSections.Cue.PAGE);
    }
}
