package dev.awaken.signal.client;

import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A Status screen section written once with {@link Ui}: {@link #build} lays it out for measuring, drawing and
 * clicking alike, so nothing overlaps or clips at any width.
 */
public abstract class UiSection implements StatusSections.Section {
    /** Lay out the section. In the click pass, buttons return true when hit: act on them there. */
    protected abstract void build(Ui ui);

    @Override
    public int height(int width) {
        Ui ui = Ui.measure(Minecraft.getInstance().font, width);
        build(ui);
        return ui.height();
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        build(Ui.draw(g, Minecraft.getInstance().font, x, y, width, mouseX, mouseY));
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        Ui ui = Ui.click(Minecraft.getInstance().font, x, y, width, mouseX, mouseY);
        build(ui);
        return ui.consumed();
    }
}
