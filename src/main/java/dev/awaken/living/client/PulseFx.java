package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.living.LivingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Happenings told from afar (SPEC §21.6), drawn from their tells alone: a dust haze with birds wheeling over a
 * migration, a dust line behind a stampede, a murmuration's living cloud, kites circling carrion, the Wild Hunt's ghost
 * light, the Deep Tide's creeping fog, a caravan's lanterns and smoke, a duel's dust. A storm is a cell of its own
 * ({@link StormSky}): Lens 2 marches it in its clouds, or {@link StormFallback} builds it of cloud cells.
 * Each in two passes: alpha-blended and unfogged (so it reads against a noon sky), then additive light. A tell past
 * the render distance is pulled in along its line of sight and shrunk to match, like the gate pillars, so it sits on
 * the horizon where it really is. Nothing allocates per frame but the per-tell state when a tell first arrives.
 */
final class PulseFx {
    private PulseFx() {}

    /** A tell as the client keeps it: where it was and where it's going (the server's centre every 2 s). */
    static final class Shown {
        String tell = "haze";
        double px, py, pz, x, y, z;
        float radius, progress, dx, dz;
        float since; // client ticks when the newest centre arrived
        float born;
    }

    static final Map<Integer, Shown> SHOWN = new HashMap<>();
    static final float LERP = 40;

    /** The Wayfarer camp's fire block (SPEC §27.6), and when the server last said it burns (it's drawn for 15 s after). */
    private static net.minecraft.core.BlockPos camp;
    private static float campSeen = -1000;

    static void camp(net.minecraft.core.BlockPos at) {
        camp = at;
        campSeen = LivingClient.ticks;
    }

    /** The High Gate's shot is on the smoke (SPEC §27.6): it reads stronger until then (client ticks). */
    private static float featuredUntil = -1000;

    static void feature(int ticks) {
        featuredUntil = LivingClient.ticks + ticks;
        campSeen = LivingClient.ticks;
    }

    static void onTells(LivingNet.Happenings msg) {
        float now = LivingClient.ticks;
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        for (LivingNet.HappeningTell t : msg.tells()) {
            keep.add(t.id());
            Shown s = SHOWN.get(t.id());
            if (s == null) {
                s = new Shown();
                s.px = t.x();
                s.py = t.y();
                s.pz = t.z();
                s.born = now;
                SHOWN.put(t.id(), s);
            } else {
                // start from where it's drawn now, so a late update never jumps
                float k = Mth.clamp((now - s.since) / LERP, 0, 1);
                s.px = Mth.lerp(k, s.px, s.x);
                s.py = Mth.lerp(k, s.py, s.y);
                s.pz = Mth.lerp(k, s.pz, s.z);
            }
            s.tell = t.tell();
            s.x = t.x();
            s.y = t.y();
            s.z = t.z();
            s.radius = t.radius();
            s.progress = t.progress();
            float len = Mth.sqrt(t.dx() * t.dx() + t.dz() * t.dz());
            s.dx = len > 0 ? t.dx() / len : 0;
            s.dz = len > 0 ? t.dz() / len : 0;
            s.since = now;
        }
        SHOWN.keySet().retainAll(keep);
    }

    static void reset() {
        SHOWN.clear();
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    private static RenderType alpha, light;
    private static double cx, cy, cz;
    private static float now;
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();
    private static final Quaternionf CAM = new Quaternionf();
    /** This tell's pull toward the camera (1: where it is) and its anchor after the pull. */
    private static double k, ax, ay, az;

    static void submit(SubmitCustomGeometryEvent e) {
        boolean fire = camp != null && (LivingClient.ticks - campSeen < 300 || LivingClient.ticks < featuredUntil);
        if ((SHOWN.isEmpty() || !LivingClient.shown) && !fire) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        now = LivingClient.ticks + state.worldPartialTicks;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        if (alpha == null) alpha = RenderTypes.debugQuads();
        if (light == null) light = RenderTypes.lightning();
        float clamp = Math.max(64, mc.options.getEffectiveRenderDistance() * 16 * 0.7F);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), alpha, (pose, vc) -> guard(() -> pass(vc, clamp, false)));
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), light, (pose, vc) -> guard(() -> pass(vc, clamp, true)));
        if (!dev.awaken.lens.client.LensClient.drawsStormCells()) StormFallback.submit(e);
    }

    private static void guard(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Awaken.LOG.error("[living] tell fx failed", ex);
        }
    }

    private static void pass(VertexConsumer vc, float clamp, boolean add) {
        if (camp != null && (LivingClient.ticks - campSeen < 300 || LivingClient.ticks < featuredUntil)) {
            double x = camp.getX() + 0.5, y = camp.getY(), z = camp.getZ() + 0.5;
            double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
            k = dist > clamp ? clamp / dist : 1;
            ax = x;
            ay = y;
            az = z;
            campfire(vc, add);
        }
        if (!LivingClient.shown) return;
        for (Shown s : SHOWN.values()) {
            float lk = Mth.clamp((now - s.since) / LERP, 0, 1);
            double x = Mth.lerp(lk, s.px, s.x), y = Mth.lerp(lk, s.py, s.y), z = Mth.lerp(lk, s.pz, s.z);
            double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
            k = dist > clamp ? clamp / dist : 1;
            ax = x;
            ay = y;
            az = z;
            float fadeIn = Mth.clamp((now - s.born) / 40F, 0, 1);
            float a = fadeIn * Math.min(1, (1 - s.progress) * 12); // fades in, and out over its last moments
            switch (s.tell) {
                case "haze" -> haze(vc, s, a, add, false);
                case "dustline" -> haze(vc, s, a, add, true);
                case "flock" -> flock(vc, s, a, add);
                case "circle" -> circle(vc, s, a, add);
                case "storm" -> {} // its own cell: StormSky
                case "ghost" -> ghost(vc, s, a, add);
                case "fog" -> fog(vc, s, a, add);
                case "lanterns" -> lanterns(vc, s, a, add);
                case "roar" -> roar(vc, s, a, add);
                default -> {}
            }
        }
    }

    // ---- the tells --------------------------------------------------------------------------------------------------

    /** A herd's dust: soft tan veils over it, layered and drifting; birds wheeling high above. A dust line trails it. */
    private static void haze(VertexConsumer vc, Shown s, float a, boolean add, boolean line) {
        float r = s.radius;
        if (!add) {
            int puffs = line ? 14 : 10;
            for (int i = 0; i < puffs; i++) {
                float f = i / (float) puffs;
                double ox, oz;
                if (line) { // trailing back along its track, widening
                    ox = -s.dx * f * r * 1.8 + Mth.sin(i * 2.3F + now * 0.02F) * r * 0.25 * (0.4 + f);
                    oz = -s.dz * f * r * 1.8 + Mth.cos(i * 1.7F + now * 0.02F) * r * 0.25 * (0.4 + f);
                } else {
                    ox = Mth.sin(i * 2.4F + now * 0.006F) * r * 0.7;
                    oz = Mth.cos(i * 1.9F + now * 0.005F) * r * 0.7;
                }
                double oy = 2 + (i % 4) * 2.2 + Mth.sin(now * 0.01F + i) * 0.8;
                float size = r * (0.55F + 0.25F * ((i * 37) % 10) / 10F);
                soft(vc, ox, oy, oz, size, argb(0.27F * a, 0xB89A6E));
            }
            if (line) return;
            // the birds: dark wings wheeling in a loose ring over the dust
            for (int i = 0; i < 9; i++) {
                float t = now * 0.025F + i * 0.7F;
                double ox = Mth.cos(t) * (r * 0.9 + i % 3 * 3), oz = Mth.sin(t) * (r * 0.9 + i % 3 * 3), oy = r * 0.9 + 6 + Mth.sin(t * 2.3F) * 2;
                bird(vc, ox, oy, oz, 0.55F, t, argb(0.8F * a, 0x1E1A16));
            }
        }
    }

    /** A murmuration: hundreds of finches as one cloud, folding and stretching over the water. */
    private static void flock(VertexConsumer vc, Shown s, float a, boolean add) {
        if (add) return;
        float R = s.radius;
        float t = now * 0.05F;
        double sway = Mth.sin(t * 0.21F) * R * 0.6, swayZ = Mth.cos(t * 0.17F) * R * 0.5;
        double dist = Math.sqrt((ax - cx) * (ax - cx) + (az - cz) * (az - cz)) * k;
        float size = (float) Math.max(0.22, dist * 0.0045);
        int col = argb(0.85F * a, 0x14161C);
        for (int i = 0; i < 420; i++) {
            float u = ((i * 0.618034F) % 1F), v = ((i * 0.41421F) % 1F);
            float th = u * Mth.TWO_PI + t * 0.6F, ph = v * Mth.PI;
            float rr = R * (0.55F + 0.45F * Mth.sin(3 * th + t * 1.3F) * Mth.cos(2 * ph - t * 0.7F));
            double ox = rr * Mth.sin(ph) * Mth.cos(th) + sway;
            double oy = 14 + rr * 0.42F * Mth.cos(ph) + 3 * Mth.sin(th * 2 + t);
            double oz = rr * Mth.sin(ph) * Mth.sin(th) * 0.55F + swayZ;
            dot(vc, ox, oy, oz, size, col);
        }
    }

    /** Kites circling high over something dead, banking as they turn. */
    private static void circle(VertexConsumer vc, Shown s, float a, boolean add) {
        if (add) return;
        for (int i = 0; i < 6; i++) {
            float t = now * (0.018F + i * 0.002F) + i * 1.05F;
            float rr = 7 + (i % 3) * 2.5F;
            double ox = Mth.cos(t) * rr, oz = Mth.sin(t) * rr, oy = 24 + (i % 2) * 5 + Mth.sin(t * 1.5F) * 1.5;
            bird(vc, ox, oy, oz, 0.9F, t, argb(0.9F * a, 0x231C18));
        }
    }

    /** The Wild Hunt: a cold blue light running over the land, a ghost glow round it, a ring at its feet. */
    private static void ghost(VertexConsumer vc, Shown s, float a, boolean add) {
        float pulse = 0.75F + 0.25F * Mth.sin(now * 0.3F);
        if (!add) {
            soft(vc, 0, 3, 0, s.radius * 1.1F, argb(0.22F * a, 0x6FA8D8));
            soft(vc, 0, 1.5, 0, 4F, argb(0.6F * a * pulse, 0xCFEFFF));
            return;
        }
        column(vc, 0, 0, 0, 42, 2.6F, argb(0.65F * a * pulse, 0x8FD0FF));
        column(vc, 0, 0, 0, 24, 0.8F, argb(0.9F * a * pulse, 0xE6F6FF));
        soft(vc, 0, 2.5, 0, 9F, argb(0.6F * a * pulse, 0xA8E0FF));
        soft(vc, 0, 12, 0, 20F, argb(0.2F * a, 0x6FB8FF));
    }

    /** The Deep Tide: grey fog fingers creeping out along the ground, longer as the night goes on. */
    private static void fog(VertexConsumer vc, Shown s, float a, boolean add) {
        if (add) return;
        float grow = 0.65F + 0.35F * Mth.clamp(s.progress * 3, 0, 1);
        for (int f = 0; f < 9; f++) {
            float ang = f * Mth.TWO_PI / 9 + Mth.sin(now * 0.004F + f) * 0.35F;
            for (int j = 0; j < 7; j++) {
                float d = (j + 1) / 7F * s.radius * grow;
                // fingers curl a little as they creep, hugging the ground
                double curl = Mth.sin(now * 0.006F + f * 1.3F + j * 0.5F) * j * 0.6;
                double ox = Mth.cos(ang) * d - Mth.sin(ang) * curl, oz = Mth.sin(ang) * d + Mth.cos(ang) * curl;
                double oy = 0.7 + Mth.sin(now * 0.02F + j + f) * 0.3;
                soft(vc, ox, oy, oz, 4.5F + j * 1.1F, argb(0.16F * a * (1 - j / 8F), 0x4A5058));
            }
        }
        soft(vc, 0, 1.2, 0, s.radius * 0.45F, argb(0.2F * a, 0x40464E));
    }

    /**
     * The Wayfarers' fire (SPEC §27.6): a column of smoke you see from the High Gate, rising a hundred and ten blocks (the
     * Lumen canopy hides anything under about sixty at that range), widening, paling and leaning with the wind as it goes,
     * over a warm glow at its foot. Its own look, drawn from afar: vanilla's campfire smoke only rises near you.
     */
    private static void campfire(VertexConsumer vc, boolean add) {
        if (!add) {
            for (int i = 0; i < 28; i++) {
                float rise = (now * 0.0025F + i / 28F) % 1F;
                float lean = rise * rise;
                double ox = Mth.sin(now * 0.004F + i * 0.7F) * 1.4 * rise + lean * 18, oz = Mth.cos(now * 0.005F + i) * 1.4 * rise + lean * 7;
                float a = 0.42F * (float) Math.pow(1 - rise, 0.8) * Mth.clamp(rise * 10, 0, 1)
                        * (LivingClient.ticks < featuredUntil ? 1.45F : 1F);
                int grey = (int) Mth.lerp(rise, 0x9E, 0xD2);
                soft(vc, ox, 1.5 + rise * 110, oz, 1.6F + rise * 11F, argb(a, grey << 16 | (grey - 4) << 8 | (grey - 10)));
            }
            return;
        }
        float flick = 0.8F + 0.2F * Mth.sin(now * 0.6F) * Mth.sin(now * 0.23F + 1);
        soft(vc, 0, 0.8, 0, 0.9F, argb(0.9F * flick, 0xFFD27A));
        soft(vc, 0, 1.0, 0, 4.0F, argb(0.22F * flick, 0xFFA040));
    }

    /** A caravan: warm lanterns swinging on its wagons, a thin line of cook-smoke rising from it. */
    private static void lanterns(VertexConsumer vc, Shown s, float a, boolean add) {
        if (!add) {
            for (int i = 0; i < 8; i++) {
                float rise = ((now * 0.01F + i / 8F) % 1F);
                double ox = Mth.sin(now * 0.01F + i) * 0.8 * rise * 3, oz = Mth.cos(now * 0.013F + i) * 0.8 * rise * 3;
                soft(vc, ox, 3 + rise * 22, oz, 1.4F + rise * 3, argb(0.28F * a * (1 - rise), 0x9A9590));
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            double ox = (i - 1.5) * 2.4 * s.dx, oz = (i - 1.5) * 2.4 * s.dz;
            float sw = Mth.sin(now * 0.12F + i * 1.7F) * 0.15F;
            float flick = 0.8F + 0.2F * Mth.sin(now * 0.7F + i * 3.1F);
            soft(vc, ox + sw, 2.4, oz, 0.6F, argb(0.95F * a * flick, 0xFFD27A));
            soft(vc, ox + sw, 2.4, oz, 3.2F, argb(0.30F * a * flick, 0xFFB050));
        }
    }

    /** A duel: dust bursting up where the beasts crash together, a warm glow of torn earth. */
    private static void roar(VertexConsumer vc, Shown s, float a, boolean add) {
        float beat = (now % 60) / 60F;
        if (!add) {
            for (int i = 0; i < 9; i++) {
                float f = (beat + i / 9F) % 1F;
                double ox = Mth.sin(i * 2.7F) * (2 + f * s.radius), oz = Mth.cos(i * 1.9F) * (2 + f * s.radius);
                soft(vc, ox, 1 + f * 6, oz, 2 + f * 5, argb(0.28F * a * (1 - f), 0x9C8064));
            }
            return;
        }
        soft(vc, 0, 1.5, 0, 6 + beat * 4, argb(0.18F * a * (1 - beat), 0xFF9A5A));
    }

    // ---- primitives (camera-facing, relative to the tell's anchor, pulled in by k) ------------------------------------

    private static int argb(float alpha, int rgb) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A point of the tell, relative to its anchor, in camera-relative space after the pull. */
    private static float rx(double ox) {
        return (float) ((ax + ox - cx) * k);
    }

    private static float ry(double oy) {
        return (float) ((ay + oy - cy) * k);
    }

    private static float rz(double oz) {
        return (float) ((az + oz - cz) * k);
    }

    /** A soft round veil: bright at its middle, nothing at its rim (a fan of triangles as quads). */
    private static void soft(VertexConsumer vc, double ox, double oy, double oz, float r, int argb) {
        if ((argb >>> 24) == 0) return;
        float x = rx(ox), y = ry(oy), z = rz(oz), rr = (float) (r * k);
        int rim = argb & 0x00FFFFFF;
        int n = 10;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * rr, s0 = Mth.sin(a0) * rr, c1 = Mth.cos(a1) * rr, s1 = Mth.sin(a1) * rr;
            quad(vc, x, y, z, argb, x, y, z, argb,
                    x + RIGHT.x * c1 + UP.x * s1, y + RIGHT.y * c1 + UP.y * s1, z + RIGHT.z * c1 + UP.z * s1, rim,
                    x + RIGHT.x * c0 + UP.x * s0, y + RIGHT.y * c0 + UP.y * s0, z + RIGHT.z * c0 + UP.z * s0, rim);
        }
    }

    /** A small solid square facing the camera (a bird of the flock at distance). */
    private static void dot(VertexConsumer vc, double ox, double oy, double oz, float size, int argb) {
        float x = rx(ox), y = ry(oy), z = rz(oz), h = (float) (size * k * 0.5);
        quad(vc, x - RIGHT.x * h - UP.x * h, y - RIGHT.y * h - UP.y * h, z - RIGHT.z * h - UP.z * h, argb,
                x + RIGHT.x * h - UP.x * h, y + RIGHT.y * h - UP.y * h, z + RIGHT.z * h - UP.z * h, argb,
                x + RIGHT.x * h + UP.x * h, y + RIGHT.y * h + UP.y * h, z + RIGHT.z * h + UP.z * h, argb,
                x - RIGHT.x * h + UP.x * h, y - RIGHT.y * h + UP.y * h, z - RIGHT.z * h + UP.z * h, argb);
    }

    /** A bird seen from below: two wings in a shallow V, flapping, never smaller than a speck. */
    private static void bird(VertexConsumer vc, double ox, double oy, double oz, float span, float t, int col) {
        double dist = Math.sqrt((ax + ox - cx) * (ax + ox - cx) + (az + oz - cz) * (az + oz - cz)) * k;
        float w = (float) Math.max(span, dist * 0.006);
        float flap = Mth.sin(now * 0.5F + t * 3) * 0.35F * w;
        float x = rx(ox), y = ry(oy), z = rz(oz), kk = (float) k;
        float ws = w * kk, fl = flap * kk, th = 0.12F * ws;
        // left wing, right wing
        quad(vc, x, y, z, col, x, y - th, z, col,
                x - RIGHT.x * ws + UP.x * fl, y - RIGHT.y * ws + UP.y * fl - th, z - RIGHT.z * ws + UP.z * fl, col,
                x - RIGHT.x * ws + UP.x * fl, y - RIGHT.y * ws + UP.y * fl, z - RIGHT.z * ws + UP.z * fl, col);
        quad(vc, x, y, z, col, x, y - th, z, col,
                x + RIGHT.x * ws + UP.x * fl, y + RIGHT.y * ws + UP.y * fl - th, z + RIGHT.z * ws + UP.z * fl, col,
                x + RIGHT.x * ws + UP.x * fl, y + RIGHT.y * ws + UP.y * fl, z + RIGHT.z * ws + UP.z * fl, col);
    }

    /** A vertical streak from a to b, turned to the camera. */
    private static void streak(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1, float width, int argb) {
        float ax0 = rx(x0), ay0 = ry(y0), az0 = rz(z0), ax1 = rx(x1), ay1 = ry(y1), az1 = rz(z1);
        float hw = (float) (width * k * 0.5);
        int clear = argb & 0x00FFFFFF;
        quad(vc, ax0 - RIGHT.x * hw, ay0, az0 - RIGHT.z * hw, clear, ax0 + RIGHT.x * hw, ay0, az0 + RIGHT.z * hw, clear,
                ax1 + RIGHT.x * hw, ay1, az1 + RIGHT.z * hw, argb, ax1 - RIGHT.x * hw, ay1, az1 - RIGHT.z * hw, argb);
    }

    /** A column of light standing up from the anchor. */
    private static void column(VertexConsumer vc, double ox, double oy, double oz, double h, float width, int argb) {
        streak(vc, ox, oy + h, oz, ox, oy, oz, width, argb);
    }

    /** Both windings, so it reads from either side. */
    private static void quad(VertexConsumer vc, float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1,
                             float x2, float y2, float z2, int c2, float x3, float y3, float z3, int c3) {
        vc.addVertex(x0, y0, z0).setColor(c0);
        vc.addVertex(x1, y1, z1).setColor(c1);
        vc.addVertex(x2, y2, z2).setColor(c2);
        vc.addVertex(x3, y3, z3).setColor(c3);
        vc.addVertex(x3, y3, z3).setColor(c3);
        vc.addVertex(x2, y2, z2).setColor(c2);
        vc.addVertex(x1, y1, z1).setColor(c1);
        vc.addVertex(x0, y0, z0).setColor(c0);
    }

    static List<Shown> shown() {
        return List.copyOf(SHOWN.values());
    }
}
