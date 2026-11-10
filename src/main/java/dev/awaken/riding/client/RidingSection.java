package dev.awaken.riding.client;

import dev.awaken.core.api.RidingHooks;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.riding.RidingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The K menu's way in (SPEC §55), on the Status page: anyone near you, with Ride and Bring beside their name; once tied,
 * who with, how far, and Let go. No key of its own.
 */
final class RidingSection implements StatusSections.Section {
    private static final String INTRO = "Ride along with someone, or bring them along with you. A rider can't go down while they stay close, but their hits count for 40% and they earn nothing towards their rank.";
    private static final int ROW = 13, MAX_ROWS = 3, PAD = 6, BONDED_H = 36;
    private static final String RIDE = "[ Ride ]", BRING = "[ Bring ]", LET_GO = "[ Let go ]";

    private record Button(int x, int y, int w, int kind, UUID other) {
        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + 10;
        }
    }

    private final List<Button> buttons = new ArrayList<>();

    @Override
    public String title() {
        return "RIDING ALONG";
    }

    @Override
    public int order() {
        return 16; // just below the run, when there is one
    }

    private static List<AbstractClientPlayer> nearby() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) return List.of();
        List<AbstractClientPlayer> out = new ArrayList<>();
        double range = RidingHooks.RANGE * RidingHooks.RANGE;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p != me && !p.isSpectator() && p.distanceToSqr(me) <= range && ClientRiding.of(p.getUUID()) == null) out.add(p);
        }
        return out;
    }

    private static RidingNet.Entry mine() {
        LocalPlayer me = Minecraft.getInstance().player;
        return me == null ? null : ClientRiding.of(me.getUUID());
    }

    @Override
    public boolean visible() {
        return mine() != null || !nearby().isEmpty();
    }

    private static List<FormattedCharSequence> intro(Font font, int width) {
        return font.split(Component.literal(INTRO), width - PAD * 2);
    }

    @Override
    public int height(int width) {
        if (mine() != null) return BONDED_H;
        Font font = Minecraft.getInstance().font;
        return PAD + intro(font, width).size() * 10 + 4 + Math.min(MAX_ROWS, nearby().size()) * ROW + 3;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int h = height(width);
        g.fill(x, y, x + width, y + h, 0xC00F1724);
        g.outline(x, y, width, h, 0xFFF0A48C);
        buttons.clear();
        RidingNet.Entry m = mine();
        if (m != null) bonded(g, font, m, x, y, width, mouseX, mouseY);
        else choosing(g, font, x, y, width, mouseX, mouseY);
    }

    private void bonded(GuiGraphicsExtractor g, Font font, RidingNet.Entry m, int x, int y, int width, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        boolean riding = m.rider().equals(me.getUUID());
        UUID otherId = riding ? m.anchor() : m.rider();
        String name = ClientRiding.nameOf(otherId);
        g.text(font, riding ? "You're riding along with " + name : name + " is riding along with you", x + PAD, y + PAD, 0xFFF3E4D4, false);
        Player other = mc.level == null ? null : mc.level.getPlayerByUUID(otherId);
        boolean inReach = other != null && other.distanceToSqr(me) <= RidingHooks.RANGE * RidingHooks.RANGE;
        String where = other == null ? "Too far away for the thread to hold" : (int) other.distanceTo(me) + " blocks away" + (inReach ? "" : ", too far for the thread to hold");
        g.text(font, where, x + PAD, y + PAD + 12, inReach ? 0xFFB9C7D9 : 0xFFE4484F, false);
        button(g, font, LET_GO, x + width - PAD - font.width(LET_GO), y + PAD + 12, RidingNet.LET_GO, otherId, mouseX, mouseY);
    }

    private void choosing(GuiGraphicsExtractor g, Font font, int x, int y, int width, int mouseX, int mouseY) {
        int ty = y + PAD;
        for (FormattedCharSequence l : intro(font, width)) {
            g.text(font, l, x + PAD, ty, 0xFFB9C7D9, false);
            ty += 10;
        }
        ty += 4;
        int bringX = x + width - PAD - font.width(BRING), rideX = bringX - 6 - font.width(RIDE);
        int shown = 0;
        for (AbstractClientPlayer p : nearby()) {
            if (shown++ == MAX_ROWS) break;
            dev.awaken.signal.client.Ui.fitted(g, font, p.getGameProfile().name(), x + PAD, ty, rideX - x - PAD - 6, 1, 0xFFFFFFFF, false);
            button(g, font, RIDE, rideX, ty, RidingNet.RIDE, p.getUUID(), mouseX, mouseY);
            button(g, font, BRING, bringX, ty, RidingNet.BRING, p.getUUID(), mouseX, mouseY);
            ty += ROW;
        }
    }

    private void button(GuiGraphicsExtractor g, Font font, String label, int x, int y, int kind, UUID other, int mouseX, int mouseY) {
        Button b = new Button(x, y, font.width(label), kind, other);
        buttons.add(b);
        g.text(font, label, x, y, b.over(mouseX, mouseY) ? 0xFFFFFFFF : 0xFFF0A48C, false);
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        for (Button b : buttons) {
            if (!b.over(mouseX, mouseY)) continue;
            ClientPacketDistributor.sendToServer(new RidingNet.Ask(b.kind(), b.other()));
            return true;
        }
        return false;
    }
}
