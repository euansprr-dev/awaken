package dev.awaken.hearth.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.net.HearthNet;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * Hold B: who marches with you. Each Echo is either in the field (with you) or at home (working a pad if there is one,
 * resting otherwise). Optional: Echoes sort themselves out if you never open this.
 */
final class EchoScreen extends Screen {
    private static final int ROW = 18;
    private int px, py, pw, ph, scroll;
    private UUID confirmRelease;

    EchoScreen() {
        super(Component.literal("Echoes"));
    }

    private static HearthView view() {
        var p = Minecraft.getInstance().player;
        return p == null ? HearthView.EMPTY : p.getData(HearthContent.VIEW.get());
    }

    @Override
    protected void init() {
        pw = Math.min(360, width - 24);
        ph = Math.min(280, height - 24);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0x90050810, 0xC0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        HearthView v = view();
        Glass.panel(g, px, py, pw, ph, Glass.VIOLET, 1f);
        g.text(font, "◇ ECHOES", px + 12, py + 10, Glass.a(Glass.VIOLET, 1f), false);
        long field = v.echoes().stream().filter(HearthView.EchoView::field).count();
        String caps = "Field " + field + "/" + v.fieldCap() + " · Roster " + v.echoes().size() + "/" + (v.fieldCap() + v.pads());
        g.text(font, caps, px + pw - 12 - font.width(caps), py + 10, Glass.a(Glass.LABEL, 1f), false);

        int listY = py + 28, listH = ph - 64;
        List<HearthView.EchoView> echoes = v.echoes();
        if (echoes.isEmpty()) {
            g.textWithWordWrap(font, Component.literal("No Echoes yet. When an elite falls to you, its Echo lingers for ten seconds. Stand near it and tap B."),
                    px + 12, listY + 4, pw - 24, Glass.a(Glass.TEXT, 1f));
        }
        g.enableScissor(px + 6, listY, px + pw - 6, listY + listH);
        int y = listY - scroll;
        for (HearthView.EchoView e : echoes) {
            boolean hover = Glass.inside(mx, my, px + 8, y, pw - 16, ROW - 2) && my >= listY && my < listY + listH;
            if (hover) g.fill(px + 8, y, px + pw - 8, y + ROW - 2, Glass.a(Glass.VIOLET, 0.10f));
            g.text(font, e.name() + (e.boss() ? " ♛" : ""), px + 12, y + 4, Glass.a(e.down() ? Glass.LABEL : Glass.TITLE, 1f), false);
            g.text(font, "Lv " + e.level(), px + 150, y + 4, Glass.a(Glass.LABEL, 1f), false);
            String post = e.down() ? "reforming" : e.field() ? "with you" : !e.duty().isEmpty() ? "on " + e.duty() + " duty" : "resting at home";
            g.text(font, post, px + 186, y + 4, Glass.a(e.field() ? Glass.GOOD : Glass.AMBER, 0.9f), false);
            int bx = px + pw - 70;
            Glass.button(g, font, e.field() ? "Home" : "March", bx, y + 1, 40, 14, Glass.inside(mx, my, bx, y + 1, 40, 14), true, Glass.VIOLET);
            int rx = px + pw - 26;
            boolean confirming = e.id().equals(confirmRelease);
            Glass.button(g, font, confirming ? "!" : "×", rx, y + 1, 14, 14, Glass.inside(mx, my, rx, y + 1, 14, 14), true, confirming ? Glass.WARN : Glass.LABEL);
            y += ROW;
        }
        g.disableScissor();

        int by = py + ph - 28;
        String army = v.fieldOut() ? "Dismiss army" : "Call army";
        Glass.button(g, font, army, px + 12, by, 100, 18, Glass.inside(mx, my, px + 12, by, 100, 18), !echoes.isEmpty(), Glass.VIOLET);
        String hint = confirmRelease != null ? "Click ! again to release that Echo for good" : "Tap B in the world to call or dismiss · Esc to close";
        g.text(font, hint, px + 120, by + 5, Glass.a(confirmRelease != null ? Glass.WARN : Glass.LABEL, 0.8f), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 1) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        HearthView v = view();
        int listY = py + 28, listH = ph - 64;
        if (my >= listY && my < listY + listH) {
            int y = listY - scroll;
            for (HearthView.EchoView e : v.echoes()) {
                if (Glass.inside(mx, my, px + pw - 70, y + 1, 40, 14)) {
                    ClientPacketDistributor.sendToServer(new HearthNet.EchoField(e.id(), !e.field()));
                    confirmRelease = null;
                    return true;
                }
                if (Glass.inside(mx, my, px + pw - 26, y + 1, 14, 14)) {
                    if (e.id().equals(confirmRelease)) {
                        ClientPacketDistributor.sendToServer(new HearthNet.EchoRelease(e.id()));
                        confirmRelease = null;
                    } else {
                        confirmRelease = e.id();
                    }
                    return true;
                }
                y += ROW;
            }
        }
        int by = py + ph - 28;
        if (!v.echoes().isEmpty() && Glass.inside(mx, my, px + 12, by, 100, 18)) {
            ClientPacketDistributor.sendToServer(new HearthNet.EchoArmy(!v.fieldOut()));
            return true;
        }
        confirmRelease = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int max = Math.max(0, view().echoes().size() * ROW - (ph - 64));
        scroll = (int) Math.max(0, Math.min(max, scroll - scrollY * ROW));
        return true;
    }

    /** The screen opens while B is still held; only a fresh press closes it. */
    private boolean releasedSinceOpen;

    @Override
    public void tick() {
        super.tick();
        if (!com.mojang.blaze3d.platform.InputConstants.isKeyDown(AwakenKeys.BIND.getKey().getValue())) releasedSinceOpen = true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.BIND.matches(event)) {
            if (releasedSinceOpen) onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
