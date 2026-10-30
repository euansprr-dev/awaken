package dev.awaken.tasks.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.signal.client.Glass;
import dev.awaken.tasks.net.TaskPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;

/**
 * The sets a step offers, worn (SPEC §27.13; Euan 2026-10-30: "a real preview of each set"): one mannequin per set in its
 * pieces, turning to the cursor, its name, role and how much of it you can make now. Click one to choose it; Esc goes
 * back to the band. The band draws on a panel where a figure can't stand, so the figures get a screen of their own.
 */
final class SetPreviewScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int GAP = 10, MAX_W = 120;

    private final String step;
    private final List<ClientMannequin> figures = new ArrayList<>();

    private SetPreviewScreen(String step, String title) {
        super(Component.literal(title));
        this.step = step;
    }

    /** Over the band, which comes back as it was when this closes. */
    static void open(String stepKey) {
        TaskPayloads.StepRow s = TaskCache.step(stepKey);
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) return;
        mc.gui.pushScreenLayer(new SetPreviewScreen(stepKey, s.title()));
    }

    private List<TaskPayloads.ChoiceRow> choices() {
        TaskPayloads.StepRow s = TaskCache.step(step);
        return s == null ? List.of() : s.choices();
    }

    private String chosen() {
        TaskPayloads.StepRow s = TaskCache.step(step);
        return s == null ? "" : s.chosen();
    }

    @Override
    protected void init() {
        if (!figures.isEmpty() || minecraft.level == null) return;
        for (TaskPayloads.ChoiceRow c : choices()) {
            ClientMannequin m = new ClientMannequin(minecraft.level, minecraft.playerSkinRenderCache());
            m.setPos(0, -4096, 0); // far from the camera: no name tag, no line under it
            for (String id : c.show()) {
                ItemStack piece = WhereFrom.icon(id);
                Equippable e = piece.get(DataComponents.EQUIPPABLE);
                if (e != null) m.setItemSlot(e.slot(), piece, true); // no equip sound
            }
            figures.add(m);
        }
    }

    private int columns() {
        return Math.min(figures.size(), choices().size());
    }

    private int colW() {
        int n = Math.max(1, columns());
        return Math.min(MAX_W, (width - 40 - GAP * (n - 1)) / n);
    }

    private int left() {
        int n = columns();
        return (width - (colW() * n + GAP * (n - 1))) / 2;
    }

    private int top() {
        return Math.max(36, height / 7);
    }

    private int boxH() {
        return Math.min(180, (int) (height * 0.48));
    }

    /** The column under the cursor, or -1. */
    private int at(double mx, double my) {
        int w = colW(), x0 = left(), y0 = top(), h = boxH() + 52;
        for (int i = 0; i < columns(); i++) {
            int x = x0 + i * (w + GAP);
            if (mx >= x && mx < x + w && my >= y0 && my < y0 + h) return i;
        }
        return -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0xE0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, title, width / 2, top() - 26, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Click a set to choose it. Esc goes back."), width / 2, top() - 14, Glass.a(Glass.LABEL, 0.9f));
        List<TaskPayloads.ChoiceRow> cs = choices();
        String chosen = chosen();
        int w = colW(), x0 = left(), y0 = top(), bh = boxH(), hover = at(mouseX, mouseY);
        for (int i = 0; i < columns(); i++) {
            TaskPayloads.ChoiceRow c = cs.get(i);
            boolean on = c.id().equals(chosen), lit = i == hover;
            int x = x0 + i * (w + GAP);
            g.fill(x, y0, x + w, y0 + bh, on ? 0xC0102018 : lit ? 0xC0101A28 : 0xC00A0F18);
            g.outline(x, y0, w, bh, Glass.a(on ? Glass.GOOD : Glass.EDGE, on ? 1f : lit ? 0.8f : 0.35f));
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x, y0 + 6, x + w, y0 + bh - 4, (int) (bh * 0.36f), 0.0625F, mouseX, mouseY,
                    figures.get(i));
            int ty = y0 + bh + 5;
            g.centeredText(font, Component.literal(c.title()), x + w / 2, ty, on ? 0xFF000000 | Glass.GOOD : 0xFFFFFFFF);
            g.centeredText(font, Component.literal(c.sub()), x + w / 2, ty + 11, Glass.a(Glass.LABEL, 1f));
            int ready = 0;
            for (TaskPayloads.NeedRow n : c.needs()) if (n.met()) ready++;
            String state = on ? "CHOSEN" : c.needs().isEmpty() ? "Every piece is yours" : ready + " of " + c.needs().size() + " materials ready";
            g.centeredText(font, Component.literal(state), x + w / 2, ty + 24, on ? 0xFF000000 | Glass.GOOD : Glass.a(Glass.LABEL, 0.8f));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        int i = at(event.x(), event.y());
        TaskPayloads.StepRow s = TaskCache.step(step);
        if (i < 0 || s == null || i >= s.choices().size()) return super.mouseClicked(event, doubleClick);
        TaskPage.choose(s, s.choices().get(i));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
