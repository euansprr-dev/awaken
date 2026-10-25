package dev.awaken.arms.client;

import dev.awaken.arms.WeaponMesh;
import dev.awaken.powers.client.fx.FxClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The light that lifts off a weapon (SPEC §25 "Weapons"): Lordscale's molten edges shed embers that rise and cool,
 * the Veilwoven's spirit edge lets go of wisps, motes hang round the Sovereign's stones. Each stream in the mesh lets
 * particles go at its rate (more as the fighter's blade fills with light), from points along its line, through the
 * very transform the weapon is drawn with; they then live in the world on the shared effects engine, so a swing
 * leaves its embers hanging behind the blade. In first person the hands are drawn with their own field of view, so a
 * point on screen is carried out to where the world's view puts it. Nothing is allocated per frame.
 */
final class WeaponMotes {
    private WeaponMotes() {}

    /** The hands' own field of view (vanilla's {@code Camera.BASE_HUD_FOV}). */
    private static final float HAND_FOV = 70F;
    /** No farther than this from the eye (a weapon on a far stand, a mirror): nothing worth the particles. */
    private static final double FAR = 48;

    private static final Vector3f P = new Vector3f();
    private static long seed = 0x9E3779B97F4A7C15L;

    /** Called as a weapon is submitted to draw, with the pose it is drawn by; {@code hand}: the fighter's own, in first person. */
    static void emit(WeaponMesh mesh, Matrix4f pose, boolean hand, float energy) {
        if (mesh.streams().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gui.screen() != null || mc.isPaused()) return;
        // real seconds since the last frame (a frame's share of the rate), never more than a tenth of a second
        float dt = Math.min(0.1F, mc.getDeltaTracker().getRealtimeDeltaTicks() / 20F);
        Camera camera = mc.gameRenderer.mainCamera();
        double cx = camera.position().x, cy = camera.position().y, cz = camera.position().z;
        for (WeaponMesh.Stream s : mesh.streams()) {
            float expected = s.rate() * (1 + 2.5F * energy) * dt;
            int n = (int) expected + (rand() < expected - (int) expected ? 1 : 0);
            for (int i = 0; i < n; i++) {
                point(s, P);
                pose.transformPosition(P);
                if (hand) toWorld(camera, P);
                double x = cx + P.x, y = cy + P.y, z = cz + P.z;
                if (P.lengthSquared() > FAR * FAR) continue;
                float size = (s.kind() == WeaponMesh.WISP ? 0.11F : s.kind() == WeaponMesh.EMBER ? 0.05F : 0.045F) * (hand ? 0.55F : 1F);
                if (s.kind() == WeaponMesh.MOTE && s.count() == 1) {
                    // round a stone: a slow drift about it, sideways in the world
                    float a = rand() * 6.2832F, v = 0.004F + 0.004F * rand();
                    FxClient.weaponMote(FxClient.MOTE_MOTE, x, y, z, Math.cos(a) * v, 0.002, Math.sin(a) * v, s.rgb(), size, hand);
                } else {
                    int kind = s.kind() == WeaponMesh.EMBER ? FxClient.MOTE_EMBER : s.kind() == WeaponMesh.WISP ? FxClient.MOTE_WISP : FxClient.MOTE_MOTE;
                    FxClient.weaponMote(kind, x, y, z, 0, 0, 0, s.rgb(), size, hand);
                }
            }
        }
    }

    /** A point on the stream: along its line by length, then out to its radius in a random direction (item space). */
    private static void point(WeaponMesh.Stream s, Vector3f out) {
        float[] p = s.points();
        int count = s.count();
        if (count == 1) {
            out.set(p[0], p[1], p[2]);
        } else {
            // a segment chosen by its share of the line's length
            float total = 0;
            for (int k = 1; k < count; k++) total += dist(p, k - 1, k);
            float at = rand() * total;
            int k = 1;
            for (; k < count - 1; k++) {
                float d = dist(p, k - 1, k);
                if (at <= d) break;
                at -= d;
            }
            float d = Math.max(1e-6F, dist(p, k - 1, k)), f = Math.min(1F, at / d);
            int a = (k - 1) * 3, b = k * 3;
            out.set(p[a] + (p[b] - p[a]) * f, p[a + 1] + (p[b + 1] - p[a + 1]) * f, p[a + 2] + (p[b + 2] - p[a + 2]) * f);
        }
        float r = s.radius() * (float) Math.sqrt(rand());
        float u = rand() * 2 - 1, phi = rand() * 6.2832F, q = (float) Math.sqrt(1 - u * u);
        out.add(r * q * (float) Math.cos(phi), r * q * (float) Math.sin(phi), r * u);
    }

    private static float dist(float[] p, int i, int j) {
        float dx = p[j * 3] - p[i * 3], dy = p[j * 3 + 1] - p[i * 3 + 1], dz = p[j * 3 + 2] - p[i * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A point in the hands' space (world-aligned, from the eye, seen through the hands' field of view) to the world's:
     * the same place on screen through the world's field of view, at the same depth.
     */
    static void toWorld(Camera camera, Vector3f p) {
        var f = camera.forwardVector();
        var l = camera.leftVector();
        var u = camera.upVector();
        float df = p.dot(f), dl = p.dot(l), du = p.dot(u);
        float r = (float) (Math.tan(Math.toRadians(camera.getFov() * 0.5)) / Math.tan(Math.toRadians(HAND_FOV * 0.5)));
        p.set(f.x() * df + (l.x() * dl + u.x() * du) * r, f.y() * df + (l.y() * dl + u.y() * du) * r, f.z() * df + (l.z() * dl + u.z() * du) * r);
    }

    /** A cheap random float in [0, 1) (xorshift; the client thread only). */
    private static float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) / (float) (1L << 24);
    }
}
