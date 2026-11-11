package dev.awaken.together.client;

import dev.awaken.Awaken;
import dev.awaken.together.TogetherNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The party strip, the edge arrows and the pings (SPEC §70.2), in the band's look: a row per party member with their
 * name, health and what they're doing; an arrow on the screen's edge with the distance when they're out of view; and a
 * marker with one line where someone pointed. Hidden with no party. Drawn from what the server last sent, never guessed.
 */
final class PartyStrip {
    private PartyStrip() {}

    static final Identifier LAYER = Awaken.id("party_strip");
    private static final Identifier EDGE = Awaken.id("textures/gui/aim/edge.png"), DIAMOND = Awaken.id("textures/gui/aim/diamond.png");
    private static final int ROW = 17, WIDTH = 132, TOP = 34, LEFT = 6;
    private static final int PANEL = 0xB00D1219, RIM = 0x80243042, INK = 0xFFE9EDF3, DIM = 0xFF93A0B3, GOOD = 0xFF7BD69A, LOW = 0xFFFF5B5B, EMPTY = 0xFF1B212C;

    // ---- the camera, to put a place in the world on the screen ------------------------------------------------------------

    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static final float[] P = new float[2];
    private static Vec3 camPos = Vec3.ZERO;
    private static boolean camReady;

    static void extract(ExtractLevelRenderStateEvent e) {
        CameraRenderState cam = e.getRenderState().cameraRenderState;
        VIEW_PROJ.set(cam.projectionMatrix).mul(cam.viewRotationMatrix);
        camPos = cam.pos;
        camReady = true;
    }

    /** The GUI position of a world point into P; false behind the camera or outside the frame's middle. */
    private static boolean project(Vec3 at, int w, int h, float margin) {
        if (!camReady) return false;
        TMP.set((float) (at.x - camPos.x), (float) (at.y - camPos.y), (float) (at.z - camPos.z), 1F);
        VIEW_PROJ.transform(TMP);
        if (TMP.w <= 0.05F) return false;
        float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
        if (Math.abs(nx) > margin || Math.abs(ny) > margin) return false;
        P[0] = (nx + 1) * 0.5F * w;
        P[1] = (1 - ny) * 0.5F * h;
        return true;
    }

    // ---- the layer --------------------------------------------------------------------------------------------------------

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || !TogetherClientConfig.strip()) return; // a hidden HUD draws no layers at all
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        if (ClientParty.together()) {
            int row = 0;
            for (TogetherNet.Member m : ClientParty.members()) {
                if (m.is(TogetherNet.F_SELF)) continue;
                int colour = ClientParty.COLOURS[row % ClientParty.COLOURS.length];
                row(g, font, m, LEFT, TOP + row * ROW, colour);
                if (!m.is(TogetherNet.F_ELSEWHERE)) arrow(g, font, me, m.name(), ClientParty.where(m, pt), pt, w, h, colour, false);
                row++;
            }
        }
        for (ClientParty.Live l : ClientParty.pings()) ping(g, font, me, l, pt, w, h);
        veil(g, font, w, h);
    }

    /** On a knee the screen dims at its edges and says what's happening; down, it says how long a partner has. */
    private static void veil(GuiGraphicsExtractor g, Font font, int w, int h) {
        TogetherNet.Member self = ClientParty.self();
        if (self == null || !(self.is(TogetherNet.F_DOWN) || self.is(TogetherNet.F_KNEEL))) return;
        boolean down = self.is(TogetherNet.F_DOWN);
        int edge = down ? 0xC0100608 : 0x80100608, middle = down ? 0x40100608 : 0x00100608;
        g.fillGradient(0, 0, w, h / 2, edge, middle);
        g.fillGradient(0, h / 2, w, h, middle, edge);
        if (down) {
            g.centeredText(font, "You're down.", w / 2, h / 2 - 24, INK);
            g.centeredText(font, self.down() > 0 ? "Your partner has " + self.down() + " seconds to reach you and lift you." : "Hold on.", w / 2, h / 2 - 12, 0xFFF0B79C);
        }
        // a second wind says itself in the action bar (the server's line): here the edges only dim
    }

    private static String state(TogetherNet.Member m) {
        if (m.is(TogetherNet.F_DOWN)) return "down, " + m.down() + " s";
        if (m.is(TogetherNet.F_KNEEL)) return "on a knee";
        if (m.is(TogetherNet.F_ELSEWHERE)) return m.is(TogetherNet.F_IN_GATE) ? "in a gate, floor " + m.floor() : "elsewhere";
        if (m.is(TogetherNet.F_IN_GATE)) return "floor " + m.floor();
        return m.is(TogetherNet.F_GENTLE) ? "Gentle" : "";
    }

    private static void row(GuiGraphicsExtractor g, Font font, TogetherNet.Member m, int x, int y, int colour) {
        boolean small = TogetherClientConfig.small();
        int width = small ? 78 : WIDTH;
        g.fill(x, y, x + width, y + ROW - 2, PANEL);
        g.outline(x, y, width, ROW - 2, m.is(TogetherNet.F_DOWN) ? LOW : RIM);
        g.fill(x + 3, y + 5, x + 7, y + 9, colour);
        dev.awaken.signal.client.Ui.fitted(g, font, m.name(), x + 11, y + 2, small ? 62 : 56, 1, INK, false);
        // health under the name, a twentieth a step
        int bar = small ? 62 : 56, filled = Math.round(bar * m.health() / 20F);
        g.fill(x + 11, y + 11, x + 11 + bar, y + 13, EMPTY);
        if (filled > 0) g.fill(x + 11, y + 11, x + 11 + filled, y + 13, m.health() > 7 ? GOOD : LOW);
        if (small) return;
        String state = state(m);
        if (!state.isEmpty()) {
            int sw = Math.min(font.width(state), width - 72);
            dev.awaken.signal.client.Ui.fitted(g, font, state, x + width - 3 - sw, y + 4, sw, 1, m.is(TogetherNet.F_DOWN) ? LOW : m.is(TogetherNet.F_GENTLE) && state.equals("Gentle") ? colour : DIM, false);
        }
    }

    /** A marker where the thing is when it's in view (pings only), else an arrow on the frame's edge with how far. */
    private static void arrow(GuiGraphicsExtractor g, Font font, LocalPlayer me, String name, Vec3 at, float pt, int w, int h, int colour, boolean always) {
        Vec3 eye = me.getEyePosition(pt);
        int blocks = (int) Math.round(eye.distanceTo(at));
        if (project(at, w, h, 0.92F)) {
            if (!always) return; // a partner in view is their own marker
            sprite(g, DIAMOND, 32, 32, P[0], P[1], 9F, 9F, 0F, colour);
            return;
        }
        float relDeg = Mth.wrapDegrees((float) (Mth.atan2(at.z - eye.z, at.x - eye.x) * Mth.RAD_TO_DEG) - 90F - me.getYRot(pt));
        float rel = relDeg * Mth.DEG_TO_RAD, ax = Mth.sin(rel), ay = -Mth.cos(rel);
        // ahead but over or under the frame: up or down
        if (Math.abs(relDeg) < 60F) {
            double flat = Math.sqrt((at.x - eye.x) * (at.x - eye.x) + (at.z - eye.z) * (at.z - eye.z));
            float relP = (float) (-(Mth.atan2(at.y - eye.y, flat) * Mth.RAD_TO_DEG)) - me.getXRot(pt);
            ay = Mth.clamp(relP / 30F, -1F, 1F);
        }
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        if (len < 1.0E-3F) return;
        float ex = w / 2F + ax / len * (w / 2F - 18F), ey = h / 2F + ay / len * (h / 2F - 18F);
        sprite(g, EDGE, 128, 32, ex, ey, 36F, 9F, (float) Math.toDegrees(Math.atan2(ax, -ay)), colour);
        String label = name + " · " + blocks;
        int lw = font.width(label);
        int lx = Mth.clamp(Math.round(ex - ax / len * 14F) - lw / 2, 4, w - lw - 4), ly = Mth.clamp(Math.round(ey - ay / len * 14F) - 4, 4, h - 12);
        g.text(font, label, lx, ly, INK, true);
    }

    private static void ping(GuiGraphicsExtractor g, Font font, LocalPlayer me, ClientParty.Live l, float pt, int w, int h) {
        TogetherNet.Ping p = l.ping();
        Vec3 at = ClientParty.where(p, pt);
        int colour = p.kind() == TogetherNet.CAREFUL ? LOW : ClientParty.colourOf(p.by());
        float age = (System.nanoTime() - l.at()) / (float) ClientParty.PING_NANOS;
        if (age > 0.85F) colour = ARGB.color(Math.round(255 * (1F - age) / 0.15F), colour & 0xFFFFFF);
        if (!project(at, w, h, 0.92F)) {
            arrow(g, font, me, p.name(), at, pt, w, h, colour, false);
            return;
        }
        float sx = P[0], sy = P[1];
        // it opens out for a quarter of a second, then sits still (nothing pulses over the fight)
        float open = TogetherClientConfig.reduced() ? 1F : Mth.clamp(age * ClientParty.PING_NANOS / 250_000_000F, 0F, 1F);
        sprite(g, DIAMOND, 32, 32, sx, sy, 6F + 6F * open, 6F + 6F * open, 0F, colour);
        int blocks = (int) Math.round(me.getEyePosition(pt).distanceTo(at));
        String line = p.line() + (blocks >= 8 ? ", " + blocks + " blocks" : "");
        int alpha = (colour >>> 24) < 40 ? 0 : 0xFF000000;
        if (alpha == 0) return;
        g.centeredText(font, p.name(), Math.round(sx), Math.round(sy) - 18, colour | 0xFF000000);
        g.centeredText(font, line, Math.round(sx), Math.round(sy) + 9, INK);
    }

    private static void sprite(GuiGraphicsExtractor g, Identifier tex, int tw, int th, float cx, float cy, float w, float h, float deg, int argb) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        if (deg != 0F) pose.rotate(deg * Mth.DEG_TO_RAD);
        pose.scale(w / tw, h / th);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -tw / 2, -th / 2, 0F, 0F, tw, th, tw, th, argb);
        pose.popMatrix();
    }
}
