package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.Windways;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A windway seen (SPEC §17.12): curls of pale air flowing along each lane near you, each a short ribbon that wavers as
 * it goes and turns to face you, with a faint sheet of light down the lane's heart that shows its line from a deck's
 * edge; brighter round you while it holds your glide, a rush of air as it takes you. Only the stretch within
 * {@link #VIEW} of the camera; nothing allocated per frame.
 */
public final class WindwayRenderer {
    private WindwayRenderer() {}

    /** How far along a lane it's drawn either side of you, the streaks' spacing, their flow (blocks a tick). */
    static final double VIEW = 280, SPACING = 3.2, FLOW = 1.25;
    private static final int SEGMENTS = 5, HAZE_STEP = 16;
    private static final int COLOUR = 0xE4FBF2, HAZE = 0xCFEFFF;

    private static @Nullable RenderType streakType, hazeType;
    private static double cx, cy, cz, time;
    private static Windways.@Nullable Way riding;
    private static boolean broken;
    private static long heldSince = -1;

    private static final SubmitNodeCollector.CustomGeometryRenderer STREAKS = (pose, vc) -> safe(() -> draw(vc, pose, false));
    private static final SubmitNodeCollector.CustomGeometryRenderer HAZES = (pose, vc) -> safe(() -> draw(vc, pose, true));

    private static void safe(Runnable r) {
        if (broken) return;
        try {
            r.run();
        } catch (RuntimeException ex) {
            broken = true;
            Awaken.LOG.error("Windway rendering failed and is off for this session", ex);
        }
    }

    /** AscentMoves, each glide tick: the lane holding the glide (null: none). The rush of air as one takes you. */
    static void riding(Windways.@Nullable Way way) {
        Minecraft mc = Minecraft.getInstance();
        if (way != null && riding == null && mc.player != null && mc.level != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), AscentContent.UPDRAFT.get(),
                    SoundSource.PLAYERS, 0.7F, 1.35F, false);
            heldSince = mc.level.getGameTime();
        } else if (way != null && mc.player != null && mc.level != null && (mc.level.getGameTime() - heldSince) % 70 == 69) {
            // the stream's breath while it carries you
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(), AscentContent.WIND.get(),
                    SoundSource.PLAYERS, 0.35F, 1.2F, false);
        }
        riding = way;
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || Windways.clientWays().isEmpty()) return;
        if (riding != null && AscentMoves.state() != dev.awaken.body.ascent.AscentState.GLIDE) riding = null;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        time = state.gameTime + state.worldPartialTicks;
        if (streakType == null) {
            streakType = RenderTypes.energySwirl(UpdraftRenderer.STREAM, 0, 0);
            hazeType = RenderTypes.energySwirl(AscentRender.GLOW, 0, 0);
        }
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        out.order(3).submitCustomGeometry(ps, hazeType, HAZES);
        out.order(3).submitCustomGeometry(ps, streakType, STREAKS);
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, boolean haze) {
        List<Windways.Way> ways = Windways.clientWays();
        for (Windways.Way w : ways) {
            if (w.reach(cx, cz) > VIEW + Windways.RADIUS) continue;
            double len = w.length();
            if (len < 1) continue;
            double ux = (w.bx() - w.ax()) / len, uy = (w.by() - w.ay()) / len, uz = (w.bz() - w.az()) / len;
            // a frame round the axis: n1 level and to the side, n2 up-ish
            double hl = Math.sqrt(ux * ux + uz * uz);
            double n1x = hl < 1e-6 ? 1 : -uz / hl, n1y = 0, n1z = hl < 1e-6 ? 0 : ux / hl;
            double n2x = n1y * uz - n1z * uy, n2y = n1z * ux - n1x * uz, n2z = n1x * uy - n1y * ux;
            double here = Math.max(0, Math.min(len, w.along(cx, cy, cz)));
            double s0 = Math.max(0, here - VIEW), s1 = Math.min(len, here + VIEW);
            boolean held = w == riding;
            if (haze) haze(vc, pose, w, ux, uy, uz, len, s0, s1);
            else streaks(vc, pose, w, ux, uy, uz, n1x, n1y, n1z, n2x, n2y, n2z, len, s0, s1, held);
        }
    }

    /** The lane's heart as a faint sheet of light its width, turned to face you, fading with distance and at its ends. */
    private static void haze(VertexConsumer vc, PoseStack.Pose pose, Windways.Way w, double ux, double uy, double uz, double len,
                             double s0, double s1) {
        double r = Windways.RADIUS * 0.9;
        for (double s = Math.floor(s0 / HAZE_STEP) * HAZE_STEP; s < s1; s += HAZE_STEP) {
            double sa = Math.max(s0, s), sb = Math.min(s1, s + HAZE_STEP);
            double ax = w.ax() + ux * sa, ay = w.ay() + uy * sa, az = w.az() + uz * sa;
            double bx = w.ax() + ux * sb, by = w.ay() + uy * sb, bz = w.az() + uz * sb;
            double mx = (ax + bx) / 2 - cx, my = (ay + by) / 2 - cy, mz = (az + bz) / 2 - cz;
            double dist = Math.sqrt(mx * mx + my * my + mz * mz);
            // up close the sheet would fill the view: it fades out inside the tube's reach
            double near = Math.min(1, Math.max(0, (dist - 10) / 30));
            float ka = (float) (0.075 * near * fade(sa, len) * Math.max(0, 1 - dist / VIEW));
            float kb = (float) (0.075 * near * fade(sb, len) * Math.max(0, 1 - dist / VIEW));
            if (ka <= 0.002F && kb <= 0.002F) continue;
            // side: along × toward the camera
            double sx = uy * mz - uz * my, sy = uz * mx - ux * mz, sz = ux * my - uy * mx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-4) continue;
            sx *= r / sl;
            sy *= r / sl;
            sz *= r / sl;
            int ca = tint(HAZE, ka), cb = tint(HAZE, kb);
            vert(vc, pose, ax - sx, ay - sy, az - sz, 0.5F, 0, ca);
            vert(vc, pose, ax + sx, ay + sy, az + sz, 0.5F, 1, ca);
            vert(vc, pose, bx + sx, by + sy, bz + sz, 0.5F, 1, cb);
            vert(vc, pose, bx - sx, by - sy, bz - sz, 0.5F, 0, cb);
        }
    }

    private static void streaks(VertexConsumer vc, PoseStack.Pose pose, Windways.Way w, double ux, double uy, double uz,
                                double n1x, double n1y, double n1z, double n2x, double n2y, double n2z, double len,
                                double s0, double s1, boolean held) {
        // the whole stream slides along; each streak keeps its slot k (and so its look) as it goes
        double shift = time * FLOW;
        long k0 = (long) Math.floor((s0 - shift) / SPACING) - 4, k1 = (long) Math.ceil((s1 - shift) / SPACING);
        for (long k = k0; k <= k1; k++) {
            double h1 = frac(k * 0.6180339), h2 = frac(k * 0.4142136), h3 = frac(k * 0.7320508), h4 = frac(k * 0.2360680);
            if (h4 < 0.22) continue; // gaps: the stream's uneven
            double length = 5 + 6 * h3;
            double start = k * SPACING + shift;
            if (start + length < s0 || start > s1) continue;
            double ang = h1 * Math.PI * 2, rad = Windways.RADIUS * 0.85 * Math.sqrt(0.04 + 0.96 * h2);
            double rcx = Math.cos(ang), rcy = Math.sin(ang);
            double radX = n1x * rcx + n2x * rcy, radY = n1y * rcx + n2y * rcy, radZ = n1z * rcx + n2z * rcy;
            double mid = start + length / 2;
            double px = w.ax() + ux * mid + radX * rad - cx, py = w.ay() + uy * mid + radY * rad - cy, pz = w.az() + uz * mid + radZ * rad - cz;
            double dist = Math.sqrt(px * px + py * py + pz * pz);
            if (dist > VIEW) continue;
            double near = dist < 2 ? dist / 2 : 1;
            double a = (held && dist < 40 ? 0.62 : 0.4) * near * Math.pow(1 - dist / VIEW, 1.5) * fade(mid, len);
            if (a <= 0.01) continue;
            // a ribbon turned toward the camera round the lane's way
            double sx = uy * pz - uz * py, sy = uz * px - ux * pz, sz = ux * py - uy * px, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1e-4) continue;
            double width = (0.16 + 0.16 * frac(k * 0.31)) * (1 + dist / 120);
            sx *= width / sl;
            sy *= width / sl;
            sz *= width / sl;
            double wob = 0.35 + 0.4 * h2, phase = h3 * 6.283 + time * 0.07;
            double lx = 0, ly = 0, lz = 0;
            float la = 0;
            for (int j = 0; j <= SEGMENTS; j++) {
                double f = j / (double) SEGMENTS, s = start + length * f;
                if (s < 0 || s > len) {
                    la = -1;
                    continue;
                }
                // it curls: a little sideways wave along its length, travelling with it
                double side = Math.sin(phase + f * 3.1) * wob;
                double x = w.ax() + ux * s + radX * (rad + side) - cx;
                double y = w.ay() + uy * s + radY * (rad + side) - cy + Math.cos(phase * 0.7 + f * 2.3) * wob * 0.5;
                double z = w.az() + uz * s + radZ * (rad + side) - cz;
                float fa = (float) (a * Math.sin(Math.PI * f));
                if (j > 0 && la >= 0) {
                    int c0 = tint(COLOUR, la), c1 = tint(COLOUR, fa);
                    vertRel(vc, pose, lx - sx, ly - sy, lz - sz, 0, (float) (f - 1.0 / SEGMENTS), c0);
                    vertRel(vc, pose, lx + sx, ly + sy, lz + sz, 1, (float) (f - 1.0 / SEGMENTS), c0);
                    vertRel(vc, pose, x + sx, y + sy, z + sz, 1, (float) f, c1);
                    vertRel(vc, pose, x - sx, y - sy, z - sz, 0, (float) f, c1);
                }
                lx = x;
                ly = y;
                lz = z;
                la = fa;
            }
        }
    }

    /** Rising out of the start, gone over the last stretch where it sets you down. */
    private static double fade(double s, double len) {
        return Math.min(1, s / 30 + 0.1) * Math.min(1, (len - s) / 24);
    }

    /** Additive light ignores alpha (ONE, ONE): the strength goes into the colour itself. */
    private static int tint(int rgb, float a) {
        a = Math.max(0, Math.min(1, a));
        int r = (int) (((rgb >> 16) & 255) * a), g = (int) (((rgb >> 8) & 255) * a), b = (int) ((rgb & 255) * a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    /** A vertex at a world position (made camera-relative here). */
    private static void vert(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vertRel(vc, pose, x - cx, y - cy, z - cz, u, v, argb);
    }

    private static void vertRel(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
