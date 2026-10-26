package dev.awaken.hearth.client;

import dev.awaken.hearth.net.HomeNet;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The YOUR GROUND card (SPEC §28.2): opens when the coal has taken and the warmth has run out over the land. A name
 * from the land's words is offered in the field; type your own or keep it (Enter, or the button). Closing it keeps the
 * offered name. Small, low on the screen, the world still visible round it.
 */
final class NameScreen extends Screen implements QuietScreen {
    private static final int W = 248, H = 96;

    private final String offered;
    private EditBox field;
    private int px, py;
    private float opened = -1;
    private boolean sent;

    NameScreen(String offered) {
        super(Component.literal("Your ground"));
        this.offered = offered;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = Math.max(8, height * 2 / 3 - H / 2);
        field = new EditBox(font, px + 16, py + 44, W - 32, 16, Component.literal("Name your ground"));
        field.setMaxLength(24);
        field.setValue(offered);
        field.setTextColor(0xFFFFE6B8);
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // no dimming: the land the warmth just ran over stays in view
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (opened < 0 && minecraft != null && minecraft.player != null) opened = minecraft.player.tickCount + a;
        float t = minecraft == null || minecraft.player == null ? 1f : Math.min(1f, (minecraft.player.tickCount + a - opened) / 10f);
        float ease = 1f - (1f - t) * (1f - t);
        Glass.panel(g, px, py, W, H, Glass.LANTERN, ease);
        // the band's words: spaced, in the hearth's gold, with a line of light under them
        String label = spaced("YOUR GROUND");
        int lw = font.width(label);
        g.text(font, label, px + (W - lw) / 2, py + 10, Glass.a(Glass.LANTERN, ease), false);
        int lineW = (int) ((W - 48) * ease);
        g.horizontalLine(px + (W - lineW) / 2, px + (W + lineW) / 2, py + 22, Glass.a(0xFFE2A8, 0.55f * ease));
        String ask = "Name it. The land offers a name; it's yours to change.";
        g.text(font, ask, px + (W - font.width(ask)) / 2, py + 29, Glass.a(Glass.LABEL, ease), false);
        super.extractRenderState(g, mouseX, mouseY, a);
        String keep = "Enter · keep this name";
        boolean over = overKeep(mouseX, mouseY);
        g.text(font, keep, px + (W - font.width(keep)) / 2, py + H - 18, Glass.a(over ? Glass.TITLE : Glass.AMBER, ease), false);
    }

    private boolean overKeep(double mx, double my) {
        return mx >= px + 16 && mx < px + W - 16 && my >= py + H - 22 && my < py + H - 6;
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
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
        send();
        onClose();
    }

    @Override
    public void removed() {
        send();
        HomeHud.place(field == null ? offered : field.getValue());
        super.removed();
    }

    private void send() {
        if (sent) return;
        sent = true;
        String name = field == null ? offered : field.getValue().strip();
        ClientPacketDistributor.sendToServer(new HomeNet.NameHearth(name.isEmpty() ? offered : name));
    }
}
