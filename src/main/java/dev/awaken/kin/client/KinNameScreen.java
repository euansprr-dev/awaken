package dev.awaken.kin.client;

import dev.awaken.kin.KinNet;
import dev.awaken.kin.KinPlates;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Naming one of your beasts from the Hearth page (SPEC §43.17): a small card in the hearth's gold over the page, its
 * name in the field (24 letters at most; empty takes its name away). Enter or the line under it keeps it; Esc goes back
 * to the page without changing anything. The server checks it's yours; the page and its plate use the name from then on.
 */
final class KinNameScreen extends Screen implements QuietScreen {
    private static final int W = 248, H = 96;

    private final @Nullable Screen parent;
    private final String id, kind, current;
    private EditBox field;
    private int px, py;

    KinNameScreen(@Nullable Screen parent, String id, String kind, String current) {
        super(Component.literal("Name your " + kind));
        this.parent = parent;
        this.id = id;
        this.kind = kind;
        this.current = current;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = Math.max(8, height / 2 - H / 2);
        field = new EditBox(font, px + 16, py + 44, W - 32, 16, Component.literal("Its name"));
        field.setMaxLength(KinPlates.NAME_MAX);
        field.setValue(current);
        field.setTextColor(0xFFFFE6B8);
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Glass.panel(g, px, py, W, H, Glass.LANTERN, 1f);
        String label = "NAME YOUR " + kind.toUpperCase(Locale.ROOT);
        g.text(font, label, px + (W - font.width(label)) / 2, py + 10, Glass.a(Glass.LANTERN, 1f), false);
        g.horizontalLine(px + 24, px + W - 24, py + 22, Glass.a(0xFFE2A8, 0.55f));
        String ask = current.isEmpty() ? "What would you like to call it?" : "Change its name, or clear it to take it away.";
        g.text(font, ask, px + (W - font.width(ask)) / 2, py + 29, Glass.a(Glass.LABEL, 1f), false);
        super.extractRenderState(g, mouseX, mouseY, a);
        String keep = "Enter · keep this name";
        g.text(font, keep, px + (W - font.width(keep)) / 2, py + H - 18, Glass.a(overKeep(mouseX, mouseY) ? Glass.TITLE : Glass.AMBER, 1f), false);
    }

    private boolean overKeep(double mx, double my) {
        return mx >= px + 16 && mx < px + W - 16 && my >= py + H - 22 && my < py + H - 6;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            keep();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (overKeep(event.x(), event.y())) {
            keep();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void keep() {
        String name = field.getValue().strip();
        if (!name.equals(current)) ClientPacketDistributor.sendToServer(new KinNet.Name(id, name));
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }
}
