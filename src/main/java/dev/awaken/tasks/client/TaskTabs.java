package dev.awaken.tasks.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Tasks page's tabs (SPEC §27.13): NOW, then one per goal, as glass across the top like the Journal's filters. Click
 * one, or Q / E. A goal with a step you haven't seen yet carries a dot; a goal with nothing open is dimmed.
 */
final class TaskTabs implements StatusSections.Section {
    private static final int H = 13, GAP = 4;

    @Override
    public String title() {
        return "";
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null;
    }

    @Override
    public boolean visible() {
        return !TaskCache.view.steps().isEmpty();
    }

    @Override
    public int height(int width) {
        return H + 4;
    }

    private static String label(int t) {
        if (t == 0) return "NOW";
        Goal g = TaskCache.TABS[t - 1];
        return g == Goal.RANK ? "GATES" : g.label.toUpperCase(java.util.Locale.ROOT);
    }

    private static int count(int t) {
        if (t == 0) return 1;
        int n = 0;
        for (var s : TaskCache.view.steps()) if (s.goal() == TaskCache.TABS[t - 1].ordinal()) n++;
        return n;
    }

    private static int tabW(Font font, int t) {
        return Math.round(font.width(label(t)) * StatusSections.micro()) + 12;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float m = StatusSections.micro();
        int tx = x;
        for (int t = 0; t <= TaskCache.TABS.length; t++) {
            int w = tabW(font, t);
            boolean lit = t == TaskCache.tab, empty = count(t) == 0, hover = mouseX >= tx && mouseX < tx + w && mouseY >= y && mouseY < y + H;
            g.fill(tx, y, tx + w, y + H, Glass.a(lit ? Glass.EDGE : 0x05080F, lit ? 0.22f : hover ? 0.6f : 0.45f));
            g.outline(tx, y, w, H, Glass.a(Glass.EDGE, lit ? 1f : empty ? 0.18f : 0.4f));
            g.pose().pushMatrix();
            g.pose().translate(tx + 5, y + (H - 8 * m) / 2f);
            g.pose().scale(m, m);
            g.text(font, label(t), 0, 0, Glass.a(lit ? 0xFFFFFF : Glass.LABEL, empty ? 0.45f : 1f), false);
            g.pose().popMatrix();
            if (t > 0 && TaskCache.fresh(TaskCache.TABS[t - 1]) > 0) g.fill(tx + w - 4, y + 2, tx + w - 2, y + 4, Glass.a(Glass.AMBER, 1f));
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
        for (int t = 0; t <= TaskCache.TABS.length; t++) {
            int w = tabW(font, t);
            if (mouseX >= tx && mouseX < tx + w) {
                pick(t);
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
        pick(Math.floorMod(TaskCache.tab + step, TaskCache.TABS.length + 1));
        return true;
    }

    static void pick(int t) {
        if (t == TaskCache.tab) return;
        TaskCache.pick(t);
        StatusSections.cue(StatusSections.Cue.PAGE);
        StatusSections.narrate(label(t).charAt(0) + label(t).substring(1).toLowerCase(java.util.Locale.ROOT) + " tab");
    }
}
