package dev.awaken.village.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.citizens.client.CityGlow;
import dev.awaken.living.craft.Craft;
import dev.awaken.living.craft.Crafts;
import dev.awaken.village.trouble.TroubleNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The troubles' light on the client (SPEC §28.7): a ring of light running out over the land from a bell rehung (a band
 * of gold lying on the ground, rising a little, three rings one after another, fading as they go), a trail glowing under
 * a Tracks hunter's eyes (paired prints in the Tracks perk's teal, a pulse running along them toward the lair), a
 * festival's paper lanterns on their strings over the green (lit at dusk, their light in CityGlow's pass), and the rooftop
 * race's marks (a column of light on each roof, the next one bright) with its clock.
 */
public final class TroubleFx {
    private TroubleFx() {}

    // ---- what's showing ---------------------------------------------------------------------------------------------

    private record Ring(double x, double y, double z, int colour, int radius, long start) {}

    private static final List<Ring> RINGS = new ArrayList<>();
    /** A trail's prints: x, ground y, z, the way it runs (radians), its distance along (blocks). */
    private static final Map<Integer, float[]> TRAILS = new HashMap<>();
    private static final Map<Integer, long[]> TRAIL_POINTS = new HashMap<>();
    /** A festival's ropes (points along each, x y z) and lanterns (x, y, z, colour, seed), by village key. */
    private static final Map<String, List<float[]>> ROPES = new HashMap<>();
    private static final Map<String, float[]> LANTERNS = new HashMap<>();
    private static long[] marks = new long[0];
    private static int markNext, markRecord;
    private static long markStart;

    private static final int[] PAPER = {0xFFB8352A, 0xFFE0A030, 0xFFEADAB0, 0xFFC4572E, 0xFFD88A2A, 0xFF7FA35A};
    static long ticks;

    static void reset() {
        RINGS.clear();
        TRAILS.clear();
        TRAIL_POINTS.clear();
        ROPES.clear();
        LANTERNS.clear();
        marks = new long[0];
    }

    // ---- payloads -----------------------------------------------------------------------------------------------------

    static void ring(TroubleNet.Ring m) {
        RINGS.add(new Ring(m.x(), m.y(), m.z(), m.colour(), m.radius(), ticks));
        while (RINGS.size() > 4) RINGS.removeFirst();
    }

    static void trail(TroubleNet.Trail m) {
        if (m.points().length == 0) {
            TRAILS.remove(m.id());
            TRAIL_POINTS.remove(m.id());
        } else {
            TRAIL_POINTS.put(m.id(), m.points());
            TRAILS.remove(m.id()); // its ground is read again as its chunks come in
        }
    }

    static void festoons(TroubleNet.Festoons m) {
        if (m.ends().length < 2) {
            ROPES.remove(m.key());
            LANTERNS.remove(m.key());
            return;
        }
        List<float[]> ropes = new ArrayList<>();
        List<float[]> lan = new ArrayList<>();
        for (int i = 0; i + 1 < m.ends().length; i += 2) {
            BlockPos a = BlockPos.of(m.ends()[i]), b = BlockPos.of(m.ends()[i + 1]);
            double sag = i / 2 >= (m.ends().length / 2) - 3 ? 2.0 : 0.85;
            string(a, b, sag, ropes, lan, i);
        }
        ROPES.put(m.key(), ropes);
        float[] flat = new float[lan.size() * 5];
        for (int i = 0; i < lan.size(); i++) System.arraycopy(lan.get(i), 0, flat, i * 5, 5);
        LANTERNS.put(m.key(), flat);
    }

    static void marks(TroubleNet.Marks m) {
        if (m.points().length == 0 && marks.length > 0) Minecraft.getInstance().gui.hud.setOverlayMessage(Component.empty(), false);
        if (marks.length == 0 && m.points().length > 0) markStart = ticks;
        marks = m.points();
        markNext = m.next();
        markRecord = m.ticks();
    }

    /** A rope sagging between two lantern-poles' tops, with paper lanterns hung along it (as Carillon's square has). */
    private static void string(BlockPos pa, BlockPos pb, double sag, List<float[]> ropes, List<float[]> lan, int seed) {
        double[] a = {pa.getX() + 0.5, pa.getY() + 0.9, pa.getZ() + 0.5}, b = {pb.getX() + 0.5, pb.getY() + 0.9, pb.getZ() + 0.5};
        double len = Math.hypot(b[0] - a[0], b[2] - a[2]);
        int segs = Math.max(4, (int) (len / 0.8));
        float[] rope = new float[(segs + 1) * 3];
        for (int i = 0; i <= segs; i++) {
            double t = i / (double) segs;
            rope[i * 3] = (float) Mth.lerp(t, a[0], b[0]);
            rope[i * 3 + 1] = (float) (Mth.lerp(t, a[1], b[1]) - sag * 4 * t * (1 - t));
            rope[i * 3 + 2] = (float) Mth.lerp(t, a[2], b[2]);
        }
        ropes.add(rope);
        int count = (int) (len / 1.5);
        for (int i = 1; i < count; i++) {
            double t = i / (double) count;
            float x = (float) Mth.lerp(t, a[0], b[0]), z = (float) Mth.lerp(t, a[2], b[2]);
            float y = (float) (Mth.lerp(t, a[1], b[1]) - sag * 4 * t * (1 - t)) - 0.32F;
            lan.add(new float[]{x, y, z, Float.intBitsToFloat(PAPER[Math.floorMod(seed * 7 + i * 3, PAPER.length)]), seed * 31 + i});
        }
    }

    // ---- the tick -----------------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!RINGS.isEmpty() || !TRAIL_POINTS.isEmpty() || !ROPES.isEmpty()) reset();
            return;
        }
        RINGS.removeIf(r -> ticks - r.start > life(r));
        // a trail's prints: its ground read once each point's chunk is in
        for (var en : TRAIL_POINTS.entrySet()) if (!TRAILS.containsKey(en.getKey()) && ticks % 20 == 0) TRAILS.put(en.getKey(), prints(mc.level, en.getValue()));
        // carrying someone: no running (the server holds it too)
        if (mc.player != null && dev.awaken.village.trouble.Rescuee.carrying(mc.player)) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
        // the race's clock
        if (marks.length > 0 && mc.player != null && ticks % 4 == 0) {
            float t = (ticks - markStart) / 20f;
            mc.gui.hud.setOverlayMessage(Component.literal(String.format(java.util.Locale.ROOT, "Rooftop race  %.1f s  ·  mark %d of %d  ·  to beat %.1f s", t,
                    Math.min(markNext + 1, marks.length), marks.length, markRecord / 20f)), false);
        }
    }

    /** Prints every 1.3 blocks along the trail, on the ground where its chunk is loaded. */
    private static float[] prints(ClientLevel level, long[] pts) {
        List<float[]> out = new ArrayList<>();
        double along = 0;
        for (int i = 1; i < pts.length; i++) {
            BlockPos a = BlockPos.of(pts[i - 1]), b = BlockPos.of(pts[i]);
            double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ(), len = Math.hypot(dx, dz);
            int n = (int) (len / 1.3);
            for (int k = 0; k < n; k++) {
                double t = k / (double) n;
                double x = a.getX() + 0.5 + dx * t, z = a.getZ() + 0.5 + dz * t;
                int bx = Mth.floor(x), bz = Mth.floor(z);
                if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
                float y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 0.03f;
                out.add(new float[]{(float) x, y, (float) z, (float) Math.atan2(dz, dx), (float) (along + len * t)});
            }
            along += len;
        }
        float[] flat = new float[out.size() * 5];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, flat, i * 5, 5);
        return flat;
    }

    private static long life(Ring r) {
        return (long) (r.radius / SPEED) + 70;
    }

    /** How fast a ring runs out (blocks a tick). */
    static final double SPEED = 1.5;

    // ---- drawing --------------------------------------------------------------------------------------------------------

    private static RenderType light;
    private static double cx, cy, cz;
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static int lantLight = LightCoordsUtil.FULL_BRIGHT;

    static void submit(SubmitCustomGeometryEvent e) {
        if (RINGS.isEmpty() && TRAILS.isEmpty() && ROPES.isEmpty() && marks.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        RIGHT.set(1, 0, 0).rotate(state.cameraRenderState.orientation);
        UP.set(0, 1, 0).rotate(state.cameraRenderState.orientation);
        if (light == null) light = RenderTypes.lightning();
        float now = ticks + state.worldPartialTicks;
        if (!RINGS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> rings(vc, level, now)));
        boolean tracks = mc.player != null && Crafts.has(mc.player, Craft.HUNTING, "tracks");
        if (tracks && !TRAILS.isEmpty()) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> trails(vc, now)));
        if (marks.length > 0) e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> columns(vc, now)));
        if (!ROPES.isEmpty()) {
            if (mc.player != null && mc.player.tickCount % 40 == 0) {
                BlockPos p = mc.player.blockPosition();
                lantLight = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, p), level.getBrightness(LightLayer.SKY, p));
            }
            boolean on = lit(level);
            e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.leash(), (pose, vc) -> guard(() -> festoons(vc, on)));
        }
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[village] trouble fx failed", ex);
            reset();
        }
    }

    /** Three bands of light one after another, lying on the land and rising a little, fading as they spread. */
    private static void rings(VertexConsumer vc, ClientLevel level, float now) {
        for (Ring r : RINGS) {
            for (int k = 0; k < 3; k++) {
                double rad = (now - r.start - k * 14) * SPEED;
                if (rad < 1 || rad > r.radius) continue;
                float fade = (float) (1 - rad / r.radius);
                float a = fade * fade * (k == 0 ? 0.95f : 0.6f);
                if (a < 0.02f) continue;
                int segs = Mth.clamp((int) (rad * Math.PI * 2 / 2.2), 24, 420);
                double prevX = 0, prevZ = 0;
                float prevY = 0;
                for (int i = 0; i <= segs; i++) {
                    double ang = i * Math.PI * 2 / segs;
                    double x = r.x + Math.cos(ang) * rad, z = r.z + Math.sin(ang) * rad;
                    if (Math.abs(x - cx) > 260 || Math.abs(z - cz) > 260) {
                        prevX = Double.NaN;
                        continue;
                    }
                    int bx = Mth.floor(x), bz = Mth.floor(z);
                    float y = level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 0.08f : (float) r.y;
                    if (i > 0 && !Double.isNaN(prevX)) band(vc, prevX, prevY, prevZ, x, y, z, r.x, r.z, r.colour, a);
                    prevX = x;
                    prevZ = z;
                    prevY = y;
                }
            }
        }
    }

    /** One stretch of a ring: a flat band on the ground (bright at its leading edge) and a thin veil of light rising off it. */
    private static void band(VertexConsumer vc, double x0, float y0, double z0, double x1, float y1, double z1, double ox, double oz, int rgb, float a) {
        double d0 = Math.hypot(x0 - ox, z0 - oz), d1 = Math.hypot(x1 - ox, z1 - oz);
        double ux0 = (x0 - ox) / d0, uz0 = (z0 - oz) / d0, ux1 = (x1 - ox) / d1, uz1 = (z1 - oz) / d1;
        double w = 1.6;
        int hot = ((int) (a * 255) << 24) | rgb, clear = rgb;
        // the band: from just behind the edge (clear) to the edge (hot)
        quad(vc, x0 - ux0 * w, y0, z0 - uz0 * w, x1 - ux1 * w, y1, z1 - uz1 * w, x1, y1, z1, x0, y0, z0, clear, hot);
        // the veil
        int soft = ((int) (a * 0.55f * 255) << 24) | rgb;
        quad(vc, x0, y0, z0, x1, y1, z1, x1, y1 + 1.4f, z1, x0, y0 + 1.4f, z0, soft, clear);
    }

    private static void quad(VertexConsumer vc, double ax, float ay, double az, double bx, float by, double bz, double cx2, float cy2, double cz2,
                             double dx, float dy, double dz, int low, int high) {
        float x0 = (float) (ax - cx), y0 = (float) (ay - cy), z0 = (float) (az - cz);
        float x1 = (float) (bx - cx), y1 = (float) (by - cy), z1 = (float) (bz - cz);
        float x2 = (float) (cx2 - cx), y2 = (float) (cy2 - cy), z2 = (float) (cz2 - cz);
        float x3 = (float) (dx - cx), y3 = (float) (dy - cy), z3 = (float) (dz - cz);
        vc.addVertex(x0, y0, z0).setColor(low);
        vc.addVertex(x1, y1, z1).setColor(low);
        vc.addVertex(x2, y2, z2).setColor(high);
        vc.addVertex(x3, y3, z3).setColor(high);
        vc.addVertex(x3, y3, z3).setColor(high);
        vc.addVertex(x2, y2, z2).setColor(high);
        vc.addVertex(x1, y1, z1).setColor(low);
        vc.addVertex(x0, y0, z0).setColor(low);
    }

    /** The trail under a Tracks hunter's eyes: paired prints in the perk's teal, a pulse running along them away from the village. */
    private static void trails(VertexConsumer vc, float now) {
        for (float[] p : TRAILS.values()) {
            float pulse = (now * 0.9f) % 60f;
            for (int k = 0; k + 4 < p.length; k += 5) {
                double x = p[k], y = p[k + 1], z = p[k + 2];
                if (Math.abs(x - cx) > 72 || Math.abs(z - cz) > 72) continue;
                float along = p[k + 4];
                float d = (along - pulse) % 60f;
                if (d < 0) d += 60f;
                float glow = 0.35f + 0.65f * Math.max(0f, 1f - Math.min(d, 60 - d) / 6f);
                double ang = p[k + 3];
                double fx = Math.cos(ang), fz = Math.sin(ang), sx = -fz, sz = fx;
                double side = (k / 5) % 2 == 0 ? 0.22 : -0.22;
                int c = ((int) (Mth.clamp(glow, 0, 1) * 255) << 24) | 0x6FE6C8;
                double px = x + sx * side, pz = z + sz * side;
                // a dragged heel's smear: a long low streak
                quad(vc, px - fx * 0.32 - sx * 0.07, (float) y, pz - fz * 0.32 - sz * 0.07, px + fx * 0.32 - sx * 0.07, (float) y, pz + fz * 0.32 - sz * 0.07,
                        px + fx * 0.32 + sx * 0.07, (float) y, pz + fz * 0.32 + sz * 0.07, px - fx * 0.32 + sx * 0.07, (float) y, pz - fz * 0.32 + sz * 0.07, c, c);
                int soft = ((int) (glow * 0.35f * 255) << 24) | 0x6FE6C8;
                quad(vc, px - fx * 0.5 - sx * 0.22, (float) y - 0.005f, pz - fz * 0.5 - sz * 0.22, px + fx * 0.5 - sx * 0.22, (float) y - 0.005f,
                        pz + fz * 0.5 - sz * 0.22, px + fx * 0.5 + sx * 0.22, (float) y - 0.005f, pz + fz * 0.5 + sz * 0.22, px - fx * 0.5 + sx * 0.22,
                        (float) y - 0.005f, pz - fz * 0.5 + sz * 0.22, soft, soft);
            }
        }
    }

    /** The race's marks: a column of light over each roof, the next one gold and tall, the rest faint. */
    private static void columns(VertexConsumer vc, float now) {
        for (int i = markNext; i < marks.length; i++) {
            BlockPos b = BlockPos.of(marks[i]);
            boolean next = i == markNext;
            double x = b.getX() + 0.5, y = b.getY(), z = b.getZ() + 0.5;
            float flick = 0.8f + 0.2f * Mth.sin(now * 0.3f + i);
            int rgb = next ? 0xFFD27A : 0x9FE6D6;
            float a = next ? 0.85f * flick : 0.25f;
            double h = next ? 12 : 4, w = next ? 0.5 : 0.25;
            int hot = ((int) (a * 255) << 24) | rgb;
            quad(vc, x - RIGHT.x * w, (float) y, z - RIGHT.z * w, x + RIGHT.x * w, (float) y, z + RIGHT.z * w, x + RIGHT.x * w, (float) (y + h), z + RIGHT.z * w,
                    x - RIGHT.x * w, (float) (y + h), z - RIGHT.z * w, hot, rgb);
        }
    }

    // ---- festoons ---------------------------------------------------------------------------------------------------------

    static boolean lit(ClientLevel level) {
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        return tod >= 11800 && tod < 19000;
    }

    private static void festoons(VertexConsumer vc, boolean on) {
        for (var en : ROPES.entrySet()) {
            boolean first = true;
            for (float[] rope : en.getValue()) {
                for (int i = 0; i < rope.length / 3; i++) {
                    float x = (float) (rope[i * 3] - cx), y = (float) (rope[i * 3 + 1] - cy), z = (float) (rope[i * 3 + 2] - cz);
                    if (i == 0 && !first) strip(vc, x, y + 0.03F, z, 0xFF2A2018);
                    strip(vc, x, y + 0.03F, z, 0xFF2A2018);
                    strip(vc, x, y - 0.03F, z, 0xFF1C150F);
                    if (i == rope.length / 3 - 1) strip(vc, x, y - 0.03F, z, 0xFF1C150F);
                }
                first = false;
            }
            float[] lan = LANTERNS.get(en.getKey());
            if (lan == null) continue;
            for (int k = 0; k + 4 < lan.length; k += 5) {
                int col = Float.floatToRawIntBits(lan[k + 3]);
                if (on) col = 0xFF000000 | brighten(col);
                paper(vc, (float) (lan[k] - cx), (float) (lan[k + 1] - cy), (float) (lan[k + 2] - cz), col, on);
            }
        }
    }

    private static int brighten(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 70), g = Math.min(255, (c >> 8 & 255) + 60), b = Math.min(255, (c & 255) + 30);
        return r << 16 | g << 8 | b;
    }

    private static void strip(VertexConsumer vc, float x, float y, float z, int col) {
        vc.addVertex(x, y, z).setColor(col).setLight(lantLight);
    }

    private static void paper(VertexConsumer vc, float x, float y, float z, int col, boolean on) {
        int l = on ? LightCoordsUtil.FULL_BRIGHT : lantLight;
        float w = 0.15F, h = 0.21F;
        float rx = RIGHT.x, ry = RIGHT.y, rz = RIGHT.z, ux = UP.x, uy = UP.y, uz = UP.z;
        float bx = x - rx * w - ux * h, by = y - ry * w - uy * h, bz = z - rz * w - uz * h;
        vc.addVertex(bx, by, bz).setColor(col).setLight(l);
        vc.addVertex(bx, by, bz).setColor(col).setLight(l);
        vc.addVertex(x - rx * w + ux * h, y - ry * w + uy * h, z - rz * w + uz * h).setColor(col).setLight(l);
        vc.addVertex(x + rx * w - ux * h, y + ry * w - uy * h, z + rz * w - uz * h).setColor(col).setLight(l);
        float tx = x + rx * w + ux * h, ty = y + ry * w + uy * h, tz = z + rz * w + uz * h;
        vc.addVertex(tx, ty, tz).setColor(col).setLight(l);
        vc.addVertex(tx, ty, tz).setColor(col).setLight(l);
    }

    /** The lanterns' flames and the floor under them, and the rings' glow near them (CityGlow's pass). */
    static final CityGlow.Source GLOW = (out, partial, gx, gy, gz) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (lit(mc.level)) {
            for (float[] lan : LANTERNS.values()) {
                for (int k = 0, i = 0; k + 4 < lan.length; k += 5, i++) {
                    if (out.far(lan[k], lan[k + 1], lan[k + 2])) continue;
                    out.halo(lan[k], lan[k + 1], lan[k + 2], 0.6F, 0.1F, out.flicker((int) lan[k + 4]));
                    if (i % 4 == 1) {
                        int g = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(lan[k]), Mth.floor(lan[k + 2]));
                        if (g < lan[k + 1]) out.pool(lan[k], g + 0.03F, lan[k + 2], 4.6F, 0.11F);
                    }
                }
            }
        }
        float now = ticks + partial;
        for (Ring r : RINGS) {
            double rad = (now - r.start) * SPEED;
            if (rad < 1 || rad > r.radius) continue;
            float fade = (float) (1 - rad / r.radius);
            int segs = Mth.clamp((int) (rad * Math.PI * 2 / 6), 12, 120);
            for (int i = 0; i < segs; i++) {
                double ang = i * Math.PI * 2 / segs;
                double x = r.x + Math.cos(ang) * rad, z = r.z + Math.sin(ang) * rad;
                if (out.far(x, r.y, z)) continue;
                int g = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
                out.pool(x, g + 0.03, z, 3.2F, 0.32F * fade);
            }
        }
    };

    static void clearExpired() {
        for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) if (ticks - it.next().start > 600) it.remove();
    }
}
