package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The run HUD, a small glass panel on the left: gate, floor, Satchel, kills, floor clock, how hard the gate is pushing,
 * noise under Silence, the collapse countdown, and the twist's one-line hint.
 */
public final class RunHudLayer {
    private RunHudLayer() {}

    private static final int GLASS = 0xC80F1724, EDGE = 0xFF7FC3FF, PALE = 0xFFB9C7D9, AMBER = 0xFFFFCF7A, WARN = 0xFFFF8A7A;

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || !ClientGates.inRun()) return;
        GatePayloads.RunHud h = ClientGates.hud();
        Font font = mc.font;
        int x = 6, w = 132;
        int lines = 4 + (h.noise() >= 0 ? 1 : 0) + (h.collapseSeconds() >= 0 ? 1 : 0) + (h.hint().isEmpty() ? 0 : 1);
        int hgt = 8 + lines * 11;
        int y = Math.max(40, g.guiHeight() / 2 - hgt / 2 - 20);
        g.fill(x, y, x + w, y + hgt, GLASS);
        g.outline(x, y, w, hgt, (h.flags() & GatePayloads.RunHud.RED) != 0 ? WARN : EDGE);
        int rc = ClientGates.rankColour(h.rank());
        int ty = y + 4;
        g.text(font, ClientGates.rankLabel(h.rank()), x + 5, ty, rc, false);
        g.text(font, themeName(h.theme()), x + 5 + font.width(ClientGates.rankLabel(h.rank())) + 5, ty, 0xFFFFFFFF, false);
        ty += 11;
        String floor = (h.area() == 1 ? "Deeper gate · " : "") + "Floor " + h.floor() + " / " + h.floors()
                + ((h.flags() & GatePayloads.RunHud.CLEARED_FLOOR) != 0 ? "  ✓" : "");
        g.text(font, floor, x + 5, ty, PALE, false);
        ty += 11;
        g.text(font, "Satchel " + h.satchelSlots() + "/27 · " + h.satchelXp() + " XP", x + 5, ty, AMBER, false);
        ty += 11;
        int s = h.floorSeconds();
        String clock = String.format("%d:%02d", s / 60, s % 60);
        int pc = switch (h.pressure()) {
            case 1 -> AMBER;
            case 2, 3 -> WARN;
            default -> PALE;
        };
        g.text(font, "Kills " + h.kills() + " · " + clock, x + 5, ty, PALE, false);
        String p = switch (h.pressure()) {
            case 1 -> "stirring";
            case 2 -> "awake";
            case 3 -> "Warden";
            default -> "";
        };
        if (!p.isEmpty()) g.text(font, p, x + w - 5 - font.width(p), ty, pc, false);
        ty += 11;
        if (h.noise() >= 0) {
            g.text(font, "Noise", x + 5, ty, PALE, false);
            int bx = x + 40, bw = w - 46;
            g.fill(bx, ty + 2, bx + bw, ty + 7, 0x80000000);
            int fill = Math.min(bw, h.noise() * bw / 24);
            g.fill(bx, ty + 2, bx + fill, ty + 7, h.noise() >= 18 ? WARN : 0xFF7FC3FF);
            ty += 11;
        }
        if (h.collapseSeconds() >= 0) {
            int c = h.collapseSeconds();
            g.text(font, c > 0 ? String.format("Collapse in %d:%02d", c / 60, c % 60) : "Collapsing!", x + 5, ty, c < 60 ? WARN : AMBER, false);
            ty += 11;
        }
        if (!h.hint().isEmpty()) {
            String hint = h.hint();
            while (font.width(hint) > w - 10 && hint.length() > 4) hint = hint.substring(0, hint.length() - 2);
            g.text(font, hint, x + 5, ty, (h.flags() & GatePayloads.RunHud.DISGUISED) != 0 ? 0xFF9AE6B4 : 0xFFE2D2FF, false);
        }
    }

    static String themeName(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
