package dev.awaken.core.client.dev;

import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Development only (autopilot {@code screen uiprobe}): every {@link Ui} and {@link Glass} primitive on an opaque
 * backdrop, with fixed text and a fixed mouse, plus the Truths tab's sections. Nothing depends on the world, so two
 * builds can be compared pixel for pixel (the check that the {@code UiCanvas} split left the flat screens unchanged).
 */
final class UiProbeScreen extends Screen {
    UiProbeScreen() {
        super(Component.literal("ui probe"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int x = 8, y = 8, w = 200;
        Glass.panel(g, x - 4, y - 4, w + 8, 236, Glass.EDGE, 1f);
        // the fixed mouse sits on the glossary word "Satchel" (second line), so its tooltip draws too
        Ui ui = Ui.draw(g, font, x, y, w, x + 4, y + 13);
        ui.text("PROBE", Glass.a(Glass.EDGE, 0.85f));
        ui.text("Satchel and Seal: carry it out, or go deeper. An Echo waits by the Veil.", Glass.a(Glass.TEXT, 1f));
        ui.text("Indented, with shadow", Glass.a(Glass.LABEL, 1f), 8, true);
        ui.textAtMost("One line at most, however long this sentence happens to run on.", Glass.a(Glass.LABEL, 1f), 1);
        ui.pair("Level 5", "120 / 300 XP", Glass.a(Glass.TITLE, 1f), Glass.a(Glass.LABEL, 1f));
        ui.bar(0.4, Glass.EDGE);
        ui.bar(0.75, Glass.AMBER, 120);
        ui.rule();
        Ui.Flow f = ui.flow();
        f.button("Accept", Glass.GOOD, true);
        f.button("Not now", Glass.LABEL, true);
        f.button("Locked", Glass.WARN, false);
        f.chip("E-rank", Glass.AMBER);
        f.chip("Awakened", Glass.VIOLET);
        f.text("· flow text", Glass.a(Glass.LABEL, 1f));
        f.end();
        ui.buttonAt("Hovered", x, ui.y(), 70, Ui.BUTTON_H, Glass.EDGE, true);
        ui.gap(Ui.BUTTON_H + 4);
        ui.textAt("textAt, cut to fit the width it is given", x, ui.y(), 120, Glass.a(Glass.TEXT, 1f), false);
        ui.gap(12);
        int cy = ui.y();
        Glass.card(g, x, cy, 90, 24, Glass.AMBER, 1f);
        Glass.bar(g, x + 96, cy + 4, 100, 5, 0.6, Glass.GOOD, 1f);
        Glass.button(g, font, "Glass", x + 96, cy + 12, 50, 12, true, true, Glass.EDGE);
        Glass.chip(g, font, "chip", x + 150, cy + 12, Glass.LANTERN);
        // the Truths tab (the TruthList cards and the Paths rows draw through the ported canvas calls)
        int sx = x + w + 16, sy = 8, sw = Math.max(120, width - sx - 8);
        for (StatusSections.Section s : StatusSections.of(StatusSections.Tab.TRUTHS)) {
            if (!s.visible()) continue;
            int h = s.height(sw);
            s.render(g, sx, sy, sw, -10000, -10000, 0f);
            sy += h + 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
