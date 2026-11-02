package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Journal's categories (SPEC §27.13) as tabs of glass across its top, each with how many you've found ("12/40", or a
 * count where the set is open-ended), YOUR STORY last. Click one, or Q / E to step through them; the tabs wrap to a
 * second row on a narrow band. Each section says which category it belongs to ({@link StatusSections.Section#shelf}).
 */
final class JournalFilters implements StatusSections.Section {
    private static final int H = 13, GAP = 4;
    private static final StatusSections.Shelf[] ORDER = StatusSections.Shelf.values();

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
        return null; // shows under every category
    }

    @Override
    public int height(int width) {
        return rows(Minecraft.getInstance().font, width) * (H + GAP) + 2;
    }

    private static String label(StatusSections.Shelf s) {
        String n = Catalogue.tally(s);
        return n.isEmpty() ? s.label : s.label + "  " + n;
    }

    /** Each tab's width at the label scale. */
    private static int tabW(Font font, StatusSections.Shelf s) {
        return Math.round(font.width(label(s)) * StatusSections.micro()) + 10;
    }

    private static int rows(Font font, int width) {
        int rows = 1, tx = 0;
        for (StatusSections.Shelf s : ORDER) {
            int w = tabW(font, s);
            if (tx > 0 && tx + w > width) {
                rows++;
                tx = 0;
            }
            tx += w + GAP;
        }
        return rows;
    }

    /** Where each tab sits: {x, y, w} relative to the section, wrapping at {@code width}. */
    private static int[][] layout(Font font, int width) {
        int[][] out = new int[ORDER.length][];
        int tx = 0, ty = 0;
        for (int i = 0; i < ORDER.length; i++) {
            int w = tabW(font, ORDER[i]);
            if (tx > 0 && tx + w > width) {
                tx = 0;
                ty += H + GAP;
            }
            out[i] = new int[]{tx, ty, w};
            tx += w + GAP;
        }
        return out;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float m = StatusSections.micro();
        StatusSections.Shelf on = StatusSections.journalFilter();
        int[][] at = layout(font, width);
        for (int i = 0; i < ORDER.length; i++) {
            StatusSections.Shelf s = ORDER[i];
            int tx = x + at[i][0], ty = y + at[i][1], w = at[i][2];
            boolean lit = s == on, hover = mouseX >= tx && mouseX < tx + w && mouseY >= ty && mouseY < ty + H;
            g.fill(tx, ty, tx + w, ty + H, Glass.a(lit ? Glass.EDGE : 0x05080F, lit ? 0.22f : hover ? 0.6f : 0.45f));
            g.outline(tx, ty, w, H, Glass.a(Glass.EDGE, lit ? 1f : 0.4f));
            g.pose().pushMatrix();
            g.pose().translate(tx + 5, ty + (H - 8 * m) / 2f);
            g.pose().scale(m, m);
            g.text(font, label(s), 0, 0, Glass.a(lit ? 0xFFFFFF : Glass.LABEL, 1f), false);
            g.pose().popMatrix();
        }
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        int[][] at = layout(Minecraft.getInstance().font, width);
        for (int i = 0; i < ORDER.length; i++) {
            int tx = x + at[i][0], ty = y + at[i][1];
            if (mouseX >= tx && mouseX < tx + at[i][2] && mouseY >= ty && mouseY < ty + H) {
                pick(ORDER[i]);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean key(KeyEvent e) {
        int step = e.key() == InputConstants.KEY_Q ? -1 : e.key() == InputConstants.KEY_E ? 1 : 0;
        if (step == 0) return false;
        pick(ORDER[Math.floorMod(StatusSections.journalFilter().ordinal() + step, ORDER.length)]);
        return true;
    }

    private static void pick(StatusSections.Shelf s) {
        if (s == StatusSections.journalFilter()) return;
        StatusSections.journalFilter(s);
        StatusSections.cue(StatusSections.Cue.PAGE);
    }
}
