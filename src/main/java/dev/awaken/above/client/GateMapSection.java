package dev.awaken.above.client;

import dev.awaken.above.net.AboveNet;
import dev.awaken.progression.Rank;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Gate Map tab: the gates your Gate Scanner read last, on a small radar around you (2,000 blocks to the edge) and as a
 * list. Only what a scanner has read is shown.
 */
final class GateMapSection implements StatusSections.Section {
    private static final int RADAR = 120;
    private static final double RANGE = 2000.0;

    @Override public String title() { return "GATE SCANNER · LAST READING"; }
    @Override public int order() { return 300; }
    @Override public boolean visible() { return !ClientAbove.gateMarks.isEmpty(); }

    @Override
    public int height(int width) {
        return RADAR + 8 + ClientAbove.gateMarks.size() * 20;
    }

    private static String compass(int dx, int dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] n = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return n[(int) Math.round(ang / 45.0) % 8];
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (mc.player == null) return;
        List<AboveNet.GateMark> marks = ClientAbove.gateMarks;
        int rx = x, ry = y;
        Glass.panel(g, rx, ry, RADAR, RADAR, Glass.EDGE, 0.9f);
        int c = RADAR / 2;
        g.horizontalLine(rx + 4, rx + RADAR - 5, ry + c, Glass.a(Glass.EDGE, 0.15f));
        g.verticalLine(rx + c, ry + 4, ry + RADAR - 5, Glass.a(Glass.EDGE, 0.15f));
        g.text(font, "N", rx + c - 2, ry + 3, Glass.a(Glass.LABEL, 0.8f), false);
        g.fill(rx + c - 1, ry + c - 1, rx + c + 2, ry + c + 2, Glass.a(Glass.TITLE, 1f));
        double px = mc.player.getX(), pz = mc.player.getZ();
        for (AboveNet.GateMark m : marks) {
            double dx = (m.x() - px) / RANGE * (c - 6), dz = (m.z() - pz) / RANGE * (c - 6);
            int gx = rx + c + (int) Math.max(-(c - 4), Math.min(c - 4, dx));
            int gy = ry + c + (int) Math.max(-(c - 4), Math.min(c - 4, dz));
            int col = Rank.of(m.rank()).colour;
            g.fill(gx - 2, gy - 2, gx + 3, gy + 3, Glass.a(col, 1f));
            if (Glass.inside(mouseX, mouseY, gx - 3, gy - 3, 7, 7)) {
                g.setTooltipForNextFrame(font, Component.literal(Rank.of(m.rank()).label + "-rank · " + m.name()), mouseX, mouseY);
            }
        }
        int ly = y + RADAR + 8;
        for (AboveNet.GateMark m : marks) {
            int dx = (int) (m.x() - px), dz = (int) (m.z() - pz);
            int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            Glass.chip(g, font, Rank.of(m.rank()).label, x, ly, Rank.of(m.rank()).colour);
            g.text(font, m.name().isEmpty() ? "Unknown gate" : m.name(), x + 22, ly + 1, Glass.a(Glass.TITLE, 1f), false);
            String detail = String.format("%,d m %s · breaks in %d play-day%s", dist, compass(dx, dz), m.breaksIn(), m.breaksIn() == 1 ? "" : "s")
                    + (m.omen().isEmpty() ? "" : " · “" + m.omen() + "”");
            g.text(font, font.plainSubstrByWidth(detail, width - 22), x + 22, ly + 10, Glass.a(Glass.LABEL, 1f), false);
            ly += 20;
        }
    }
}
