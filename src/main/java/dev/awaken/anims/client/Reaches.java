package dev.awaken.anims.client;

import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Reaches (Hands P4.1; SPEC §24): a touch's hand goes to the thing it touches, in the world, and does the thing there:
 * the palm flat on the door, the fingers closed on the lever's handle and pulling it through, finger and thumb on the
 * stem, the hand on the cow's flank. Each is a few keys in the world, set against where the hand meets the thing (the
 * anchor, followed as it moves) and the way back to you from it: {@code s} across (to your right), {@code u} up,
 * {@code n} out toward you. A key marked rest is the hand under the frame's foot, where
 * it comes from and goes back to.
 * <p>
 * The hands are drawn larger than a fist at rest (nearer, {@link #HAND_DISTANCE}), each where the world has it on screen.
 * The player never moves: past {@link #NEAR} blocks the hand reaches toward the thing (as far as an arm goes) without
 * pretending to touch it, and the view leans in a touch (a narrower field of view) while a hand is out.
 */
final class Reaches {
    private Reaches() {}

    /** How near the hands are drawn during a touch (a fist at rest: 0.85). */
    static final double HAND_DISTANCE = 0.75;
    static final double HAND_DISTANCE_BOTH = 0.85;
    /** Farther than this (blocks from the eye) and a hand reaches toward the thing instead of onto it. */
    static final double NEAR = 2.5;
    /** As far as a hand gets toward a far thing (blocks from the eye). */
    static final double ARM = 1.3;

    /** A key: at tick {@code t} the fist at the anchor plus (s, u, n); or at rest. */
    record Key(int t, float s, float u, float n, boolean rest) {
        static Key rest(int t) {
            return new Key(t, 0, 0, 0, true);
        }

        static Key at(int t, float s, float u, float n) {
            return new Key(t, s, u, n, false);
        }
    }

    /**
     * A touch: its keys, whether both hands do it ({@code apart}: how far each is to its side), when what the hand holds
     * shows (ticks, -1 none), when the hand glows (ticks), how far the view leans in.
     */
    record Touch(String name, Key[] keys, float apart, int itemFrom, int itemTo, int glowFrom, int glowTo) {
        int length() {
            return keys[keys.length - 1].t();
        }

        boolean both() {
            return apart > 0;
        }
    }

    /** Where a touch is, each tick (world): the point the hand meets. Called on the client thread. */
    @FunctionalInterface
    interface Anchor {
        /** Writes the point into {@code out}; false when the thing is gone (the hand goes back). */
        boolean at(double[] out);
    }

    // the one touch in progress (a new one replaces it)
    private static @Nullable Touch touch;
    private static @Nullable Anchor anchor;
    private static int startedAt = -100, ticks;
    /** Which hands (right, left) do it. */
    private static final boolean[] HANDS = new boolean[2];
    private static ItemStack item = ItemStack.EMPTY;
    // the anchor this tick and last (frames draw between them), and the way out to you from it
    private static final double[] A = new double[3], A0 = new double[3], P = new double[3];
    private static double nx, nz = 1;
    private static boolean far;
    /**
     * Turned away: when the thing first stood more than {@link #COS_AWAY} off the middle of the view (0: it has not),
     * and each hand's fist where the view last had it before that (right, up, ahead of the eye). From then the hands
     * are the view's own and go home in {@link #HOME_NS}; they never follow a thing out of the frame.
     */
    private static long awayAt;
    private static final double[] LAST = new double[6];
    private static final boolean[] HAS_LAST = new boolean[2];
    static final double COS_AWAY = 0.80;
    static final long HOME_NS = 120_000_000L;

    static void start(Touch t, Anchor a, boolean right, boolean left, ItemStack held) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !a.at(A)) return;
        touch = t;
        anchor = a;
        startedAt = ticks;
        HANDS[0] = right;
        HANDS[1] = left;
        item = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        System.arraycopy(A, 0, A0, 0, 3);
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double dx = eye.x - A[0], dz = eye.z - A[2], d = Math.sqrt(dx * dx + dz * dz);
        if (d > 1e-3) {
            nx = dx / d;
            nz = dz / d;
        }
        double dy = eye.y - A[1];
        far = dx * dx + dy * dy + dz * dz > NEAR * NEAR;
        awayAt = 0;
        HAS_LAST[0] = HAS_LAST[1] = false;
    }

    static void stop() {
        touch = null;
        anchor = null;
        awayAt = 0;
        HAS_LAST[0] = HAS_LAST[1] = false;
    }

    /** A swing, a screen of its own: the hands are needed elsewhere. */
    static boolean active() {
        return touch != null && ticks - startedAt <= touch.length();
    }

    static @Nullable String playing() {
        return active() ? touch.name() : null;
    }

    static void tick() {
        ticks++;
        if (touch == null) return;
        if (ticks - startedAt > touch.length()) {
            stop();
            return;
        }
        System.arraycopy(A, 0, A0, 0, 3);
        if (anchor != null && !anchor.at(A)) anchor = null; // gone: the hand stays where it last was
    }

    /** How far the view leans in now (0..1): while a hand is out to a thing. */
    static float lean(float pt) {
        Touch t = touch;
        if (t == null || !active()) return 0;
        float age = ticks - startedAt + pt, len = t.length();
        return Mth.clamp(Math.min(age / 4F, (len - age) / 5F), 0, 1);
    }

    private static final Vector3f F = new Vector3f(), U = new Vector3f(), R = new Vector3f();

    /** The world layer's source (interactions, 200): the touch's hands. */
    static boolean pose(float pt, FirstPerson.Rig rig) {
        Touch t = touch;
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null || !active()) return false;
        var cam = mc.gameRenderer.mainCamera();
        Vec3 eye = cam.position();
        F.set(0, 0, -1).rotate(cam.rotation());
        U.set(0, 1, 0).rotate(cam.rotation());
        R.set(1, 0, 0).rotate(cam.rotation());
        float age = ticks - startedAt + pt;
        // the anchor between ticks; far away, the hand only gets an arm's length toward it
        double ax = Mth.lerp(pt, A0[0], A[0]), ay = Mth.lerp(pt, A0[1], A[1]), az = Mth.lerp(pt, A0[2], A[2]);
        if (far) {
            double dx = ax - eye.x, dy = ay - eye.y, dz = az - eye.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double k = ARM / d;
            ax = eye.x + dx * k;
            ay = eye.y + dy * k - 0.12;
            az = eye.z + dz * k;
        }
        // You turned away from it (or it left the middle of the view): the hands stop following the thing. From where
        // the view last had them they go home under the frame in an eighth of a second and the touch is over. A fist
        // held to a place in the world while the view swung away from it was drawn chasing it across the frame.
        long now = System.nanoTime();
        if (awayAt == 0) {
            double tx = ax - eye.x, ty = ay - eye.y, tz = az - eye.z, td = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (td > 1e-4 && (tx * F.x + ty * F.y + tz * F.z) / td < COS_AWAY) awayAt = now;
        }
        double home = 0;
        if (awayAt != 0) {
            home = (now - awayAt) / (double) HOME_NS;
            if (home >= 1) {
                stop();
                return false;
            }
            home = home * home * (3 - 2 * home);
        }
        dev.awaken.anims.client.HandsLog.scene(t.name());
        rig.fx = -nx;
        rig.fz = -nz;
        // both hands at once: drawn a little farther (smaller), or they cover the thing between them
        rig.handDistance = t.both() ? HAND_DISTANCE_BOTH : HAND_DISTANCE;
        Key[] keys = t.keys();
        int i = 0;
        while (i < keys.length - 2 && keys[i + 1].t() <= age) i++;
        Key k0 = keys[i], k1 = keys[i + 1];
        float u = Mth.clamp((age - k0.t()) / Math.max(1, k1.t() - k0.t()), 0, 1);
        u = u * u * (3 - 2 * u);
        boolean posed = false;
        for (int h = 0; h < 2; h++) {
            if (!HANDS[h]) continue;
            double side = h == 0 ? 1 : -1;
            // a two-handed touch: each hand its side of the anchor; mirrored keys for the left
            double off = t.both() ? side * t.apart() : 0;
            key(k0, eye, ax, ay, az, side, off, P);
            double x0 = P[0], y0 = P[1], z0 = P[2];
            key(k1, eye, ax, ay, az, side, off, P);
            double x = Mth.lerp(u, x0, P[0]), y = Mth.lerp(u, y0, P[1]), z = Mth.lerp(u, z0, P[2]);
            int j = h * 3;
            if (awayAt == 0) {
                // where the view has this fist now: kept, for the frame you turn away in
                double rx = x - eye.x, ry = y - eye.y, rz = z - eye.z;
                LAST[j] = rx * R.x + ry * R.y + rz * R.z;
                LAST[j + 1] = rx * U.x + ry * U.y + rz * U.z;
                LAST[j + 2] = rx * F.x + ry * F.y + rz * F.z;
                HAS_LAST[h] = true;
            } else {
                // home from where the view last had it, in the view's own frame (the turn itself moves nothing)
                key(Key.rest(0), eye, ax, ay, az, side, off, P);
                double hx = HAS_LAST[h] ? eye.x + R.x * LAST[j] + U.x * LAST[j + 1] + F.x * LAST[j + 2] : P[0];
                double hy = HAS_LAST[h] ? eye.y + R.y * LAST[j] + U.y * LAST[j + 1] + F.y * LAST[j + 2] : P[1];
                double hz = HAS_LAST[h] ? eye.z + R.z * LAST[j] + U.z * LAST[j + 1] + F.z * LAST[j + 2] : P[2];
                x = Mth.lerp(home, hx, P[0]);
                y = Mth.lerp(home, hy, P[1]);
                z = Mth.lerp(home, hz, P[2]);
            }
            double sx = eye.x + R.x * side * 0.33 - U.x * 0.4 - F.x * 0.1, sy = eye.y + R.y * side * 0.33 - U.y * 0.4 - F.y * 0.1,
                    sz = eye.z + R.z * side * 0.33 - U.z * 0.4 - F.z * 0.1;
            rig.reach(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, sy, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, 1F);
            if (!item.isEmpty() && age >= t.itemFrom() && age < t.itemTo()) rig.held[h] = item;
            posed = true;
        }
        if (age >= t.glowFrom() && age < t.glowTo()) {
            rig.glow = Mth.clamp(Math.min((age - t.glowFrom()) / 3F, (t.glowTo() - age) / 5F), 0, 1);
        }
        return posed;
    }

    /** A key's point (world) for one hand. */
    private static void key(Key k, Vec3 eye, double ax, double ay, double az, double side, double off, double[] out) {
        if (k.rest()) {
            // under the frame's foot on the hand's own side
            out[0] = eye.x + F.x * 0.3 - U.x * 0.75 + R.x * side * 0.35;
            out[1] = eye.y + F.y * 0.3 - U.y * 0.75 + R.y * side * 0.35;
            out[2] = eye.z + F.z * 0.3 - U.z * 0.75 + R.z * side * 0.35;
            return;
        }
        double sx = nz, sz = -nx; // across, to your right as you face the thing
        double s = k.s() * side + off, n = k.n();
        out[0] = ax + sx * s + nx * n;
        out[1] = ay + k.u();
        out[2] = az + sz * s + nz * n;
    }

    static void reset() {
        stop();
    }
}
