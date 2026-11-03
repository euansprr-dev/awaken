package dev.awaken.lucidium.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.lucidium.data.DreamLedger;
import dev.awaken.lucidium.net.LucidiumNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A trial's rings of soft gold light across the course (SPEC §9.3, its own look, not end rods): the next one bright and
 * breathing, the ones ahead softer, the ones passed a faint trace. And a pale glow over the end of the road, so the door
 * (or the bell) can be seen from your room, small and far off.
 */
final class TrialRings {
    private TrialRings() {}

    static final float RADIUS = 2.2f, BAND = 0.16f, HALO = 0.9f;
    static final int SEGMENTS = 40;
    private static double cx, cy, cz;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientTrial.active()) return;
        LucidiumNet.TrialStart s = ClientTrial.start;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        float time = (mc.level.getGameTime() + state.worldPartialTicks) / 20f;
        int passed = ClientTrial.hud.ring();
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.lightning(), (pose, vc) -> {
            try {
                for (int i = 0; i < s.rings().size(); i++) {
                    int index = i + 1; // ring 0 is the start in your room
                    float a;
                    if (index <= passed) a = 0.12f;
                    else if (index == passed + 1) a = 0.75f + 0.25f * Mth.sin(time * 3f);
                    else a = 0.45f;
                    ring(vc, s.rings().get(i), a, time + i);
                }
                float pulse = 0.55f + 0.2f * Mth.sin(time * 1.4f);
                Vec3 end = ClientTrial.end;
                if (end != null) flare(vc, end.add(0, s.bell() ? 1.2 : 2.0, 0), s.bell() ? 2.2f : 3.6f, argb(0xF6F0FF, pulse * 0.5f));
                LucidiumNet.TrialGhosts g = ClientTrial.ghosts;
                if (g != null) {
                    float t = (mc.level.getGameTime() - ClientTrial.ghostStart + state.worldPartialTicks) / DreamLedger.GHOST_EVERY;
                    for (LucidiumNet.Ghost ghost : g.ghosts()) ghost(vc, g.origin(), ghost, t, time);
                }
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[lucidium] trial rings failed", ex);
            }
        });
    }

    /** A ring across the course (+z), facing along it: a bright band fading out to either side. */
    private static void ring(VertexConsumer vc, Vec3 c, float alpha, float phase) {
        int core = argb(0xF5D896, alpha), rim = argb(0xF5D896, 0f), glow = argb(0xFFE9B8, alpha * 0.35f);
        float r = RADIUS + 0.05f * Mth.sin(phase * 2f);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            // the band, bright on its line, soft to either side
            both(vc, c.x + c0 * r, c.y + s0 * r, c.z, c.x + c1 * r, c.y + s1 * r, c.z,
                    c.x + c1 * (r + BAND), c.y + s1 * (r + BAND), c.z, c.x + c0 * (r + BAND), c.y + s0 * (r + BAND), c.z, core, rim);
            both(vc, c.x + c0 * r, c.y + s0 * r, c.z, c.x + c1 * r, c.y + s1 * r, c.z,
                    c.x + c1 * (r - BAND), c.y + s1 * (r - BAND), c.z, c.x + c0 * (r - BAND), c.y + s0 * (r - BAND), c.z, core, rim);
            // a wide faint halo inside it
            both(vc, c.x + c0 * r, c.y + s0 * r, c.z, c.x + c1 * r, c.y + s1 * r, c.z,
                    c.x + c1 * (r - HALO), c.y + s1 * (r - HALO), c.z, c.x + c0 * (r - HALO), c.y + s0 * (r - HALO), c.z, glow, rim);
        }
    }

    /** Pearl for your own ghost, rose for a friend's. */
    static final int GHOST_OWN = 0xF2EEFF, GHOST_FRIEND = 0xFFC4D8;
    /** A finished ghost waits at the end this many samples, fading. */
    static final float GHOST_LINGER = 10;

    /**
     * A ghost: a soft standing figure of light (head and body) where the run was at this moment, a short trail of fading
     * motes behind it. {@code t} counts samples since the doorway.
     */
    private static void ghost(VertexConsumer vc, Vec3 origin, LucidiumNet.Ghost g, float t, float time) {
        int n = g.samples().size();
        if (n == 0 || t < 0 || t > n - 1 + GHOST_LINGER) return;
        float fade = t > n - 1 ? 1 - (t - (n - 1)) / GHOST_LINGER : Math.min(1, t / 3f);
        int rgb = g.own() ? GHOST_OWN : GHOST_FRIEND;
        float a = (0.42f + 0.06f * Mth.sin(time * 4f)) * fade;
        double x = at(g, t, 0) + origin.x, y = at(g, t, 1) + origin.y, z = at(g, t, 2) + origin.z;
        flare(vc, new Vec3(x, y + 0.45, z), 0.5f, argb(rgb, a));
        flare(vc, new Vec3(x, y + 1.0, z), 0.48f, argb(rgb, a));
        flare(vc, new Vec3(x, y + 1.58, z), 0.32f, argb(rgb, a * 1.2f));
        for (int i = 1; i <= 4; i++) {
            float back = t - i * 0.75f;
            if (back < 0) break;
            flare(vc, new Vec3(at(g, back, 0) + origin.x, at(g, back, 1) + origin.y + 0.9, at(g, back, 2) + origin.z),
                    0.3f - i * 0.05f, argb(rgb, a * (0.5f - i * 0.1f)));
        }
    }

    /** One axis of a ghost's place at {@code t} samples, between the two samples either side. */
    private static double at(LucidiumNet.Ghost g, float t, int axis) {
        int n = g.samples().size();
        int i = Mth.clamp((int) Math.floor(t), 0, n - 1), j = Math.min(n - 1, i + 1);
        float f = Mth.clamp(t - i, 0, 1);
        int a = g.samples().get(i), b = g.samples().get(j);
        double va = axis == 0 ? DreamLedger.unpackX(a) : axis == 1 ? DreamLedger.unpackY(a) : DreamLedger.unpackZ(a);
        double vb = axis == 0 ? DreamLedger.unpackX(b) : axis == 1 ? DreamLedger.unpackY(b) : DreamLedger.unpackZ(b);
        return va + (vb - va) * f;
    }

    /** A soft round glow facing the camera. */
    private static void flare(VertexConsumer vc, Vec3 p, float r, int argb) {
        int rim = argb & 0x00FFFFFF;
        for (int i = 0; i < 16; i++) {
            float a0 = i * Mth.TWO_PI / 16, a1 = (i + 1) * Mth.TWO_PI / 16;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            quad(vc, p.x, p.y, p.z, p.x, p.y, p.z,
                    p.x + RIGHT.x * c1 + UP.x * s1, p.y + RIGHT.y * c1 + UP.y * s1, p.z + RIGHT.z * c1 + UP.z * s1,
                    p.x + RIGHT.x * c0 + UP.x * s0, p.y + RIGHT.y * c0 + UP.y * s0, p.z + RIGHT.z * c0 + UP.z * s0, argb, rim);
        }
    }

    /** A quad seen from either side (a ring is passed through from behind as well as in front). */
    private static void both(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        quad(vc, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, inner, outer);
        quad(vc, x1, y1, z1, x0, y0, z0, x3, y3, z3, x2, y2, z2, inner, outer);
    }

    /** One quad, camera-relative: the first two corners take {@code inner}, the last two {@code outer}. */
    private static void quad(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex((float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    private static int argb(int rgb, float a) {
        return (Mth.clamp(Math.round(a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
