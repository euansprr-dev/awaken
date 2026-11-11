package dev.awaken.lucidium.trial;

import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentSwing;
import net.minecraft.world.level.BlockGetter;

/**
 * The air Runs' proofs (SPEC §9.6): the leap, the rope and the wings crossing each gap by the Ascent's own rules
 * ({@link AscentRules}, {@link AscentSwing}), from a sprint jump off the edge, at mastery 0 and no flow, holding forward.
 * Each returns the ticks from the take-off to the landing, or -1 with {@link #why} saying what fell short. Each also
 * proves the plain jump doesn't make it, so the Run teaches the move. One per solve (off-thread), so nothing is shared.
 */
final class AirProofs {
    /** A sprint jump's take-off (vanilla's), the pace it keeps in the air holding forward, and the air's drag. */
    static final double JUMP_UP = 0.42, JUMP_SPEED = 0.40, AIR_ACCEL = 0.026, AIR_DRAG = 0.91, AIR_DRAG_V = 0.98;
    /** Taking off this far before the edge; landing at least this far onto the far side. */
    static final double TAKE_OFF = 0.2, ONTO = 0.3;

    /** What fell short, after a -1. */
    String why = "";

    /** One tick of vanilla flight holding forward along +z (feet, velocity): {x, y, z, vx, vy, vz}. */
    private static void air(double[] b) {
        b[5] += AIR_ACCEL;
        b[2] += b[5];
        b[1] += b[4];
        b[4] = (b[4] - AscentRules.G) * AIR_DRAG_V;
        b[5] *= AIR_DRAG;
    }

    /** Where a plain sprint jump comes down to {@code landY} (feet), as z; NaN if it never rises above it. */
    static double plainJump(double x, double y, double z, double landY) {
        double[] b = {x, y, z, 0, JUMP_UP, JUMP_SPEED};
        for (int t = 0; t < 200; t++) {
            air(b);
            if (b[4] < 0 && b[1] <= landY) return t > 0 ? b[2] : Double.NaN;
        }
        return Double.NaN;
    }

    // ---- the leap ----------------------------------------------------------------------------------------------------

    /**
     * A leap across {@code len} to a road {@code rise} higher, {@code road} long: the sprint jump, the double jump at its
     * top, then the air dash as it starts to fall if the double jump alone comes down short, then the carry (the
     * Leaper's, {@code AscentMoves.leaperMove}). The landing has to be on that road, not past it.
     */
    int leap(double x, int y, int z, int len, int rise, int road) {
        double landY = y + rise, need = z + len + ONTO, end = z + len + road - ONTO;
        double plain = plainJump(x, y, z - TAKE_OFF, landY);
        if (!Double.isNaN(plain) && plain >= need) return fail("a plain jump crosses it (" + fmt(plain - z) + ")");
        int t = leapOnce(x, y, z, landY, false);
        if (t > 0 && lastZ >= need && lastZ <= end) return t;
        if (t > 0 && lastZ > end) return fail("the double jump alone carries past the road");
        t = leapOnce(x, y, z, landY, true);
        if (t < 0) return t;
        if (lastZ < need) return fail("the leap comes down " + fmt(need - lastZ) + " short");
        if (lastZ > end) return fail("the leap carries " + fmt(lastZ - end) + " past the road");
        return t;
    }

    /** Where the last flight came down (z). */
    private double lastZ;

    private int leapOnce(double x, int y, int z, double landY, boolean dash) {
        double[] b = {x, y, z - TAKE_OFF, 0, JUMP_UP, JUMP_SPEED};
        AscentRules.Kin k = new AscentRules.Kin();
        double[] sc = new double[3];
        boolean carry = false, dashed = !dash;
        double top = y;
        for (int t = 1; t <= 200; t++) {
            if (!carry) {
                air(b);
                if (b[4] <= 0.05) {
                    // the double jump near the top of the jump: the carry starts from the body's own motion
                    carry = true;
                    k.set(b[0], b[1], b[2], 0, Math.max(b[4], 0) + AscentRules.DOUBLE_JUMP, b[5]);
                }
                top = Math.max(top, b[1]);
                continue;
            }
            if (!dashed && k.vy <= 0) {
                dashed = true;
                double speed = Math.max(AscentRules.DASH_SPEED, k.hspeed() * AscentRules.DASH_GAIN);
                k.vx = 0;
                k.vz = speed;
                k.vy = Math.max(k.vy, AscentRules.DASH_LIFT);
            }
            AscentRules.carryStep(k, 0f, 1, 0, sc);
            top = Math.max(top, k.y);
            if (k.vy < 0 && k.y <= landY) {
                lastZ = k.z;
                if (top < landY + 0.2) return fail("the leap tops out " + fmt(landY - top) + " under the road");
                return t;
            }
        }
        return fail("the leap never comes down");
    }

    // ---- the rope ------------------------------------------------------------------------------------------------------

    /** Ticks into the jump the rope is thrown: early (rising off the edge) and late (falling into the gap). */
    static final int THROW_EARLY = 2, THROW_LATE = 9;

    /**
     * A swing across {@code len}: the sprint jump, the rope thrown {@code throwAt} ticks into the air (the assisted
     * rope's pick, the client's {@code AscentMoves.fire}), its flight out, then the swing. The held rope (SPEC §62) lets
     * go only when the key is let go, so every tick of the arc from the catch to its top is a let-go the player may
     * choose: each is played (its window boost, then the carry to the far road at the same height, {@code road} long),
     * and the one that lands, best in the window (where the rope glows), is the proof. The arc's top is the last tried
     * (where the rope used to let go by itself), so a gap laid for that let-go still proves.
     */
    int swing(BlockGetter v, double x, int y, int z, int len, int road, int throwAt) {
        double need = z + len + ONTO, end = z + len + road - ONTO, mid = (need + end) / 2;
        double plain = plainJump(x, y, z - TAKE_OFF, y);
        if (!Double.isNaN(plain) && plain >= need) return fail("a plain jump crosses it");
        CourseSpace space = new CourseSpace(v);
        double[] b = {x, y, z - TAKE_OFF, 0, JUMP_UP, JUMP_SPEED};
        int t = 0;
        for (; t < throwAt; t++) air(b);
        AscentRules.Kin k = new AscentRules.Kin().set(b[0], b[1] + AscentRules.HAND_HEIGHT, b[2], 0, b[4], b[5]);
        AscentSwing.Work w = new AscentSwing.Work();
        AscentSwing.Pick pick = new AscentSwing.Pick();
        String when = throwAt == THROW_EARLY ? "thrown early" : "thrown late";
        if (!AscentSwing.find(space, k, 0, AscentRules.TETHER_RANGE, w, pick) || pick.zip) return fail("the rope " + when + " finds nothing to swing from");
        AscentSwing.Rope rope = new AscentSwing.Rope().set(pick.x, pick.y, pick.z, pick.len, pick.fitLen);
        AscentSwing.entry(k, 0);
        double[] sc = new double[3];
        for (int travel = AscentSwing.travel(pick.len); travel > 0; travel--, t++) AscentRules.carryStep(k, 0f, 0.5, 0, sc);
        double d = Math.sqrt(sq(k.x - rope.ax) + sq(k.y - rope.ay) + sq(k.z - rope.az));
        rope.len = Math.max(AscentRules.TETHER_MIN, Math.min(rope.len, d));
        rope.fitLen = Math.min(rope.fitLen, rope.len);
        AscentRules.Kin c = new AscentRules.Kin();
        int best = -1;
        double bestScore = -1e9, nearest = Double.NaN;
        for (int i = 0; ; i++, t++) {
            if (i > 300) return fail("the swing (" + when + ") never tops out");
            int how = AscentSwing.tick(space, k, rope, 0, 1, w);
            if (how == AscentSwing.WALL || w.braked) {
                if (best >= 0) break;
                return fail("the swing (" + when + ") runs into something");
            }
            if (k.y - AscentRules.HAND_HEIGHT < y - 40) return fail("the swing (" + when + ") falls away");
            boolean top = how == AscentSwing.TOP || rope.swung;
            if (rope.caught) {
                // the key let go now: where it comes down
                c.set(k.x, k.y, k.z, k.vx, k.vy, k.vz);
                double q = AscentSwing.letGo(c, rope, 0, w);
                c.y -= AscentRules.HAND_HEIGHT; // the carry is the feet's
                for (int j = 1; j <= 200; j++) {
                    AscentRules.carryStep(c, 0f, 1, 0, sc);
                    if (c.vy < 0 && c.y <= y) {
                        double score = q - 0.01 * Math.abs(c.z - mid);
                        if (c.z >= need && c.z <= end && score > bestScore) {
                            bestScore = score;
                            best = t + j;
                        }
                        if (Double.isNaN(nearest) || Math.abs(c.z - mid) < Math.abs(nearest - mid)) nearest = c.z;
                        break;
                    }
                }
            }
            if (top) break;
        }
        if (best >= 0) return best;
        if (Double.isNaN(nearest)) return fail("the swing (" + when + ") never comes down");
        if (nearest < need) return fail("the swing (" + when + ") lets go " + fmt(need - nearest) + " short at best");
        return fail("the swing (" + when + ") flies " + fmt(nearest - end) + " past the far road at least");
    }

    // ---- the wings -----------------------------------------------------------------------------------------------------

    /** Ticks of a fall before the wings open (the client opens them on jump once falling). */
    static final int WINGS_AFTER = 3;

    /**
     * A glide to an island {@code drop} below and {@code len} on: off the edge with a sprint jump, the wings opened in the
     * fall (the catch), then the glide steered at the island's middle (looking at it, as you would) until the feet
     * touch its top. {@code island} is its length along the way.
     */
    int glide(double x, int y, int z, int len, int drop, int island) {
        double landY = y - drop, from = z + len, to = z + len + island;
        double plain = plainJump(x, y, z - TAKE_OFF, landY);
        if (!Double.isNaN(plain) && plain >= from) return fail("a plain jump reaches the island");
        double[] b = {x, y, z - TAKE_OFF, 0, JUMP_UP, JUMP_SPEED};
        int t = 0, falling = 0;
        while (falling < WINGS_AFTER) {
            air(b);
            t++;
            if (b[4] < 0) falling++;
        }
        AscentRules.Kin k = new AscentRules.Kin().set(b[0], b[1], b[2], 0, b[4], b[5]);
        double aimZ = (from + to) / 2, aimY = landY + 1;
        AscentRules.glideCatch(k, 0f, pitchTo(k, aimY, aimZ));
        for (int i = 0; i < 1200; i++, t++) {
            AscentRules.glideStep(k, 0f, pitchTo(k, aimY, aimZ));
            if (k.y <= landY) {
                if (k.z < from + ONTO) return fail("the glide comes down " + fmt(from - k.z) + " short");
                if (k.z > to) return fail("the glide overshoots the island");
                return t;
            }
        }
        return fail("the glide never lands");
    }

    /** The pitch (down positive) from the feet to a point ahead along +z. */
    private static float pitchTo(AscentRules.Kin k, double y, double z) {
        double dz = Math.max(0.5, z - k.z);
        return (float) Math.toDegrees(Math.atan2(k.y - y, dz));
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private int fail(String s) {
        why = s;
        return -1;
    }

    private static double sq(double a) {
        return a * a;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
