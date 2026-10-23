package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;
import dev.awaken.progression.client.StatusSections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Status screen, Gate Map tab: a top-down map of the gates around you (rank colour, distance, direction, breaks-in)
 * and, inside a gate, a summary of the current run with a button to look into the Satchel.
 */
public final class GateMapSection implements StatusSections.Section {
    private static final String[] DIRS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final int MAP = 150;
    private int satchelX, satchelY, satchelW;

    @Override
    public String title() {
        return "GATE MAP";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public int height(int width) {
        return MAP + 12 + (ClientGates.inRun() ? 74 : 0);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (mc.player == null) return;
        int top = y;
        if (ClientGates.inRun()) top = runSummary(g, font, x, y, width, mouseX, mouseY) + 6;
        boolean overworld = mc.level != null && mc.level.dimension() == Level.OVERWORLD;
        int mx = x, my = top;
        g.fill(mx, my, mx + MAP, my + MAP, 0xC00A1220);
        g.outline(mx, my, MAP, MAP, 0xFF2C3B55);
        int cx = mx + MAP / 2, cy = my + MAP / 2;
        double scale = (MAP / 2.0 - 6) / 1600.0;
        for (int ring : new int[]{500, 1000, 1500}) circle(g, cx, cy, (int) (ring * scale), 0x40FFFFFF);
        g.text(font, "N", cx - 2, my + 3, 0xFF7FC3FF, false);
        if (!overworld) {
            g.centeredText(font, "No pillars here", cx, cy - 4, 0xFF8090A8);
        }
        double px = mc.player.getX(), pz = mc.player.getZ();
        List<GatePayloads.Beacon> list = new ArrayList<>(ClientGates.beacons());
        list.sort(Comparator.comparingDouble(b -> b.pos().distToCenterSqr(px, b.pos().getY(), pz)));
        if (overworld) {
            for (GatePayloads.Beacon b : list) {
                double dx = (b.pos().getX() + 0.5 - px) * scale, dz = (b.pos().getZ() + 0.5 - pz) * scale;
                int bx = cx + (int) Math.round(dx), by = cy + (int) Math.round(dz);
                if (bx < mx + 2 || bx > mx + MAP - 3 || by < my + 2 || by > my + MAP - 3) continue;
                int c = ClientGates.rankColour(b.rank());
                if (b.broken()) {
                    g.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFF7A1018);
                    g.fill(bx - 1, by - 1, bx + 2, by + 2, c);
                } else {
                    g.fill(bx - 2, by - 2, bx + 2, by + 2, c);
                    g.outline(bx - 3, by - 3, 6, 6, 0x80FFFFFF);
                }
            }
            // you
            float yaw = mc.player.getYRot();
            double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
            for (int i = 2; i <= 6; i++) g.fill(cx + (int) Math.round(fx * i), cy + (int) Math.round(fz * i), cx + (int) Math.round(fx * i) + 1, cy + (int) Math.round(fz * i) + 1, 0xFFFFFFFF);
        }
        // list beside the map
        int lx = mx + MAP + 8, ly = my + 2;
        if (list.isEmpty() || !overworld) {
            g.text(font, overworld ? "No gates within 1,600 blocks." : "Gates show from the surface.", lx, ly, 0xFF8090A8, false);
            return;
        }
        int shown = 0;
        for (GatePayloads.Beacon b : list) {
            if (shown >= 9 || ly > my + MAP - 10) break;
            double dx = b.pos().getX() + 0.5 - px, dz = b.pos().getZ() + 0.5 - pz;
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            int d = (int) Math.round(((Math.toDegrees(Math.atan2(dz, dx)) + 90 + 360) % 360) / 45.0) % 8;
            String label = ClientGates.rankLabel(b.rank());
            g.text(font, label, lx, ly, ClientGates.rankColour(b.rank()), false);
            String rest = String.format(" %,d m %s", dist, DIRS[d]);
            g.text(font, rest, lx + font.width(label), ly, 0xFFE6ECF5, false);
            String when = b.broken() ? "BROKEN" : (b.flags() & GatePayloads.Beacon.SCRIPTED) != 0 ? "" : b.breaksIn() == 0 ? "breaks today"
                    : "breaks in " + b.breaksIn() + "d";
            int wc = b.broken() ? 0xFFFF8A7A : b.breaksIn() <= 1 ? 0xFFFFCF7A : 0xFF8090A8;
            if (lx + 150 < x + width) g.text(font, when, x + width - font.width(when) - 4, ly, wc, false);
            else {
                ly += 10;
                g.text(font, "  " + when, lx, ly, wc, false);
            }
            ly += 12;
            shown++;
        }
    }

    private int runSummary(GuiGraphicsExtractor g, Font font, int x, int y, int width, int mouseX, int mouseY) {
        GatePayloads.RunHud h = ClientGates.hud();
        int hgt = 68;
        g.fill(x, y, x + width, y + hgt, 0xC00F1724);
        g.outline(x, y, width, hgt, 0xFF7FC3FF);
        String rank = ClientGates.rankLabel(h.rank());
        g.text(font, rank, x + 6, y + 6, ClientGates.rankColour(h.rank()), false);
        g.text(font, RunHudLayer.themeName(h.theme()) + " · floor " + h.floor() + " of " + h.floors(), x + 10 + font.width(rank), y + 6, 0xFFFFFFFF, false);
        g.text(font, "Satchel " + h.satchelSlots() + " / 27 slots · " + h.satchelXp() + " Satchel XP", x + 6, y + 20, 0xFFFFCF7A, false);
        int s = h.floorSeconds();
        g.text(font, "Kills " + h.kills() + " · " + String.format("%d:%02d", s / 60, s % 60) + " on this floor", x + 6, y + 32, 0xFFB9C7D9, false);
        if (!h.hint().isEmpty()) g.text(font, h.hint(), x + 6, y + 44, 0xFFE2D2FF, false);
        String btn = "[ View Satchel ]";
        satchelW = font.width(btn);
        satchelX = x + width - satchelW - 6;
        satchelY = y + 54;
        boolean hover = mouseX >= satchelX && mouseX < satchelX + satchelW && mouseY >= satchelY && mouseY < satchelY + 10;
        g.text(font, btn, satchelX, satchelY, hover ? 0xFFFFFFFF : 0xFF7FC3FF, false);
        return y + hgt;
    }

    @Override
    public boolean click(double mouseX, double mouseY, int x, int y, int width) {
        if (!ClientGates.inRun()) return false;
        if (mouseX >= satchelX && mouseX < satchelX + satchelW && mouseY >= satchelY && mouseY < satchelY + 10) {
            ClientPacketDistributor.sendToServer(new GatePayloads.OpenSatchel());
            return true;
        }
        return false;
    }

    private static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int colour) {
        int steps = Math.max(24, r);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }
}
