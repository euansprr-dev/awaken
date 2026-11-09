package dev.awaken.riding.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.core.api.RidingHooks;
import dev.awaken.gates.client.RigPipelines;
import dev.awaken.riding.RidingNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The bond you can see (SPEC §55): one thin thread of ivory light with a rose-gold halo, sagging and swaying between the
 * two, with three beads travelling from rider to anchor. It pulls straight and turns amber as they drift apart, and past
 * its reach it frays into red dashes. A catch streams into the partner's feet and a ring round them burns down the four
 * seconds. All of it is drawn in the world as light; none of it is a particle.
 */
final class ThreadFx {
    private ThreadFx() {}

    private static final int IVORY = 0xFFF3E4, ROSE = 0xF0A48C, AMBER = 0xFFB24A, RED = 0xE4484F;
    /** From here on the thread is under strain (blocks). */
    private static final double STRAIN_FROM = 36;
    private static final int BEADS = 3, MOTES = 12;
    private static final float CORE_WIDTH = 0.035F, HALO_WIDTH = 0.13F, STREAM_TICKS = 12F;

    private static double cx, cy, cz;
    private static float pt, time;
    private static long now;
    private static final Quaternionf CAM = new Quaternionf();
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || (ClientRiding.threads().isEmpty() && ClientRiding.BURSTS.isEmpty())) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        pt = state.worldPartialTicks;
        now = level.getGameTime();
        time = (now % 100_000L) + pt;
        CAM.set(state.cameraRenderState.orientation);
        RIGHT.set(1, 0, 0).rotate(CAM);
        UP.set(0, 1, 0).rotate(CAM);
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RigPipelines.light(), (pose, vc) -> {
            try {
                for (RidingNet.Entry t : ClientRiding.threads()) thread(level, vc, t);
                for (ClientRiding.Folding f : ClientRiding.FOLDS.values()) folding(level, vc, f);
                for (ClientRiding.Burst b : ClientRiding.BURSTS) burst(level, vc, b);
            } catch (RuntimeException ex) {
                Awaken.LOG.error("[riding] the thread failed to draw", ex);
            }
        });
    }

    // ---- the thread ----------------------------------------------------------------------------------------------------

    private static Vec3 chest(Player p) {
        return p.getPosition(pt).add(0, p.getBbHeight() * 0.62, 0);
    }

    private static void thread(ClientLevel level, VertexConsumer vc, RidingNet.Entry t) {
        Player rider = level.getPlayerByUUID(t.rider()), anchor = level.getPlayerByUUID(t.anchor());
        if (rider == null || anchor == null || ClientRiding.folding(t.rider())) return;
        Vec3 a = chest(rider), b = chest(anchor);
        double len = a.distanceTo(b);
        if (len < 0.8) return;
        boolean frayed = len > RidingHooks.RANGE;
        float strain = Mth.clamp((float) ((len - STRAIN_FROM) / (RidingHooks.RANGE - STRAIN_FROM)), 0, 1);
        int core = frayed ? RED : mix(IVORY, AMBER, strain), halo = frayed ? RED : mix(ROSE, AMBER, strain);
        double sag = Math.min(1.5, len * 0.05) * (1 - strain);
        double sway = Math.min(0.25, len * 0.012) * (1 - strain);
        Vec3 along = b.subtract(a);
        Vec3 side = new Vec3(-along.z, 0, along.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        int n = Mth.clamp((int) (len * 1.2), 10, 40);
        Vec3 prev = point(a, along, side, 0, sag, sway, frayed);
        for (int i = 1; i <= n; i++) {
            double u = (double) i / n;
            Vec3 cur = point(a, along, side, u, sag, sway, frayed);
            if (!frayed || i % 3 != 0) {
                double mid = u - 0.5 / n;
                float alpha = Math.min(1F, (float) Math.min(mid, 1 - mid) / 0.08F) * nearFade(prev.add(cur).scale(0.5));
                ribbon(vc, prev, cur, HALO_WIDTH, argb(halo, 0.28F * alpha));
                ribbon(vc, prev, cur, CORE_WIDTH, argb(core, 0.9F * alpha));
            }
            prev = cur;
        }
        if (frayed) return;
        for (int k = 0; k < BEADS; k++) {
            double u = ((time / 40F) + (float) k / BEADS) % 1F;
            float alpha = Math.min(1F, (float) Math.min(u, 1 - u) / 0.1F);
            Vec3 p = point(a, along, side, u, sag, sway, false);
            alpha *= nearFade(p);
            flare(vc, p, 0.16F, argb(halo, 0.5F * alpha));
            flare(vc, p, 0.06F, argb(core, alpha));
        }
    }

    private static Vec3 point(Vec3 a, Vec3 along, Vec3 side, double u, double sag, double sway, boolean frayed) {
        double belly = 4 * u * (1 - u);
        double s = sway * belly * Math.sin(time * 0.05 + u * 3);
        if (frayed) s += 0.05 * Math.sin(u * 40 + time * 0.7);
        return new Vec3(a.x + along.x * u + side.x * s, a.y + along.y * u - sag * belly, a.z + along.z * u + side.z * s);
    }

    /** Your own end of the thread starts at your chest: it must not smear across the camera. */
    private static float nearFade(Vec3 p) {
        double d = Math.sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy) + (p.z - cz) * (p.z - cz));
        return Mth.clamp((float) ((d - 1.0) / 2.0), 0, 1);
    }

    // ---- a catch -------------------------------------------------------------------------------------------------------

    private static void folding(ClientLevel level, VertexConsumer vc, ClientRiding.Folding f) {
        Player anchor = level.getPlayerByUUID(f.anchor());
        if (anchor == null) return;
        Vec3 feet = anchor.getPosition(pt).add(0, 0.06, 0);
        float age = now - f.start() + pt, life = Math.max(1, f.end() - f.start());
        arc(vc, feet, 1.0F, 1.22F, Mth.clamp(1F - age / life, 0, 1), argb(ROSE, 0.85F));
        flare(vc, feet.add(0, 0.2, 0), 0.75F, argb(ROSE, 0.16F));
        if (age >= STREAM_TICKS + 8) return;
        // the rider streams along the ground of the air into the partner's feet, each mote a little behind the last
        for (int k = 0; k < MOTES; k++) {
            float t = Mth.clamp((age - k * 0.6F) / STREAM_TICKS, 0, 1), tail = Mth.clamp(t - 0.2F, 0, 1);
            if (t <= 0 || tail >= 1) continue;
            float alpha = Mth.sin(t * Mth.PI);
            ribbon(vc, stream(f.from(), feet, tail, k), stream(f.from(), feet, t, k), 0.09F, argb(IVORY, 0.85F * alpha));
        }
    }

    private static Vec3 stream(Vec3 from, Vec3 to, float t, int k) {
        double e = t * t * (3 - 2 * t);
        double angle = k * 2.4 + t * 5, r = 0.65 * (1 - e);
        return new Vec3(from.x + (to.x - from.x) * e + Math.cos(angle) * r, from.y + (to.y - from.y) * e + Math.sin(t * Math.PI) * 0.5, from.z + (to.z - from.z) * e + Math.sin(angle) * r);
    }

    private static void burst(ClientLevel level, VertexConsumer vc, ClientRiding.Burst b) {
        Player anchor = level.getPlayerByUUID(b.anchor());
        if (anchor == null) return;
        float t = Mth.clamp((now - b.start() + pt) / ClientRiding.BURST_TICKS, 0, 1), fade = 1 - t;
        Vec3 feet = anchor.getPosition(pt).add(0, 0.06, 0);
        arc(vc, feet, 0.9F + 2.6F * t, 1.15F + 2.9F * t, 1F, argb(IVORY, 0.8F * fade));
        flare(vc, feet.add(0, 0.9, 0), 1.3F * fade + 0.2F, argb(ROSE, 0.45F * fade));
    }

    // ---- shapes --------------------------------------------------------------------------------------------------------

    /** A flat ring on the ground, the given fraction of the way round. */
    private static void arc(VertexConsumer vc, Vec3 c, float r0, float r1, float fraction, int argb) {
        int rim = argb & 0x00FFFFFF, n = Math.max(1, Mth.ceil(48 * fraction));
        float mid = (r0 + r1) * 0.5F, sweep = Mth.TWO_PI * fraction / n;
        for (int i = 0; i < n; i++) {
            float a0 = i * sweep - Mth.HALF_PI, a1 = (i + 1) * sweep - Mth.HALF_PI;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(vc, c.x + c0 * mid, c.y, c.z + s0 * mid, c.x + c1 * mid, c.y, c.z + s1 * mid, c.x + c1 * r1, c.y, c.z + s1 * r1, c.x + c0 * r1, c.y, c.z + s0 * r1, argb, rim);
            quad(vc, c.x + c0 * mid, c.y, c.z + s0 * mid, c.x + c1 * mid, c.y, c.z + s1 * mid, c.x + c1 * r0, c.y, c.z + s1 * r0, c.x + c0 * r0, c.y, c.z + s0 * r0, argb, rim);
        }
    }

    private static void ribbon(VertexConsumer vc, Vec3 a, Vec3 b, float width, int argb) {
        if ((argb >>> 24) == 0) return;
        Vec3 d = b.subtract(a);
        Vec3 n = d.cross(a.add(d.scale(0.5)).subtract(cx, cy, cz));
        double len = n.length();
        if (len < 1e-6) return;
        n = n.scale(width * 0.5 / len);
        int rim = argb & 0x00FFFFFF;
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x + n.x, b.y + n.y, b.z + n.z, a.x + n.x, a.y + n.y, a.z + n.z, argb, rim);
        quad(vc, a.x, a.y, a.z, b.x, b.y, b.z, b.x - n.x, b.y - n.y, b.z - n.z, a.x - n.x, a.y - n.y, a.z - n.z, argb, rim);
    }

    private static void flare(VertexConsumer vc, Vec3 p, float r, int argb) {
        if (r <= 0.001F || (argb >>> 24) == 0) return;
        int rim = argb & 0x00FFFFFF, n = r < 1F ? 6 : 12;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            quad(vc, p.x, p.y, p.z, p.x, p.y, p.z,
                    p.x + RIGHT.x * c1 + UP.x * s1, p.y + RIGHT.y * c1 + UP.y * s1, p.z + RIGHT.z * c1 + UP.z * s1,
                    p.x + RIGHT.x * c0 + UP.x * s0, p.y + RIGHT.y * c0 + UP.y * s0, p.z + RIGHT.z * c0 + UP.z * s0, argb, rim);
        }
    }

    private static void quad(VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex((float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex((float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    private static int argb(int rgb, float a) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int l = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | l;
    }
}
