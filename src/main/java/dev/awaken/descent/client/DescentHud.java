package dev.awaken.descent.client;

import dev.awaken.descent.DescentContent;
import dev.awaken.descent.net.DescentPayloads;
import dev.awaken.descent.player.DeepPack;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * The Descent HUD (top left, only inside the Descent or with a Grave Echo waiting): where you are and what the rule is
 * doing, the noise meter in the Silent Halls, the Deep Pack count, and the way to your Grave Echo.
 */
final class DescentHud {
    private DescentHud() {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        DescentPayloads.GraveMark grave = nearestGrave(mc);
        if (ClientDescent.depth <= 0 && grave == null) return;
        int x = 6, y = 6, w = 0;
        int lines = 0;
        String packLine = null;
        if (ClientDescent.depth > 0) {
            w = Math.max(w, font.width(ClientDescent.status));
            lines++;
            DeepPack pack = mc.player.getData(DescentContent.DEEP_PACK.get());
            packLine = "Deep Pack " + pack.used() + "/" + DeepPack.SIZE + (pack.isEmpty() ? "" : " · not yours yet");
            w = Math.max(w, font.width(packLine));
            lines++;
            if (ClientDescent.noise >= 0) lines++;
        }
        String graveLine = null;
        if (grave != null) {
            graveLine = graveText(mc, grave);
            w = Math.max(w, font.width(graveLine));
            lines++;
        }
        int h = lines * 11 + 6;
        w = Math.max(w + 12, 120);
        Glass.panel(g, x, y, w, h, Glass.EDGE, 0.85f);
        int ty = y + 4;
        if (ClientDescent.depth > 0) {
            g.text(font, ClientDescent.status, x + 6, ty, Glass.a(Glass.TEXT, 1f), false);
            ty += 11;
            g.text(font, packLine, x + 6, ty, Glass.a(Glass.LANTERN, 1f), false);
            ty += 11;
            if (ClientDescent.noise >= 0) {
                ClientDescent.noiseShown += (ClientDescent.noise - ClientDescent.noiseShown) * 0.2f;
                float n = ClientDescent.noiseShown;
                int colour = n >= 70 ? Glass.WARN : n >= 35 ? Glass.AMBER : Glass.GOOD;
                g.text(font, "Noise", x + 6, ty, Glass.a(Glass.LABEL, 1f), false);
                int bx = x + 6 + font.width("Noise") + 6, bw = w - (bx - x) - 8;
                Glass.bar(g, bx, ty + 1, bw, 6, n / 100.0, colour, 1f);
                g.verticalLine(bx + (int) (bw * 0.35), ty, ty + 8, Glass.a(Glass.AMBER, 0.8f));
                g.verticalLine(bx + (int) (bw * 0.70), ty, ty + 8, Glass.a(Glass.WARN, 0.8f));
                ty += 11;
            }
        }
        if (graveLine != null) g.text(font, graveLine, x + 6, ty, Glass.a(Glass.VIOLET, 1f), false);
    }

    static DescentPayloads.GraveMark nearestGrave(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        String dim = mc.level.dimension().identifier().toString();
        DescentPayloads.GraveMark best = null;
        double bd = Double.MAX_VALUE;
        for (DescentPayloads.GraveMark gm : ClientDescent.sync.graves()) {
            if (!gm.dimension().equals(dim)) continue;
            double d = mc.player.distanceToSqr(Vec3.atCenterOf(gm.pos()));
            if (d < bd) {
                bd = d;
                best = gm;
            }
        }
        return best;
    }

    private static String graveText(Minecraft mc, DescentPayloads.GraveMark gm) {
        Vec3 to = Vec3.atCenterOf(gm.pos()).subtract(mc.player.position());
        int dist = (int) Math.round(to.horizontalDistance());
        double targetYaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        double rel = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int i = Math.floorMod((int) Math.round(rel / 45.0), 8);
        String vertical = to.y > 6 ? " ▲" : to.y < -6 ? " ▼" : "";
        return "Grave Echo " + ARROWS[i] + " " + dist + " m" + vertical;
    }

    /** World markers above Grave Echoes (client tick, inside the per-tick gizmo collector). */
    static void markers() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        String dim = mc.level.dimension().identifier().toString();
        for (DescentPayloads.GraveMark gm : ClientDescent.sync.graves()) {
            if (!gm.dimension().equals(dim)) continue;
            double d = Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(gm.pos())));
            if (d > 300) continue;
            float scale = (float) Math.max(1.0, d / 14.0);
            Gizmos.billboardTextOverBlock("Grave Echo · " + (int) d + " m", gm.pos(), 0, Glass.a(Glass.VIOLET, 1f), scale);
            Gizmos.point(Vec3.atCenterOf(gm.pos()).add(0, 0.5, 0), Glass.a(Glass.VIOLET, 0.9f), (float) Math.max(4, d / 6)).setAlwaysOnTop();
        }
    }
}
