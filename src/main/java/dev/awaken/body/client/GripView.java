package dev.awaken.body.client;

import dev.awaken.body.GripRules;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The grip in first person (SPEC §17.28, §24): both hands closed round the line in the world, where the world has it
 * (P1.1): each hand's heel at its own side of the pipe, the fingers across it and the thumb over its near side, drawn
 * at the size that makes the hand span the pipe on screen. Climbing, a hand stays where it gripped while the body rises
 * past it, then opens, lets go and reaches up past the other for a new hold (hand over hand); held still, both stay put
 * and you can look round them; sliding, both loosen and run down the line with you, shaking with the friction; your
 * feet clamp the line below (look down to see them). On the catch the hands come up into it from below; at the top
 * both hands go up onto the top of the line and press down as vanilla's climb carries you up past them; letting go,
 * they drop away and vanilla's hands come back.
 * <p>
 * The cycle steps once a tick; each frame draws it between ticks. Nothing allocated.
 */
final class GripView {
    private GripView() {}

    /**
     * Where a hand grips, over the eyes (blocks): the high and the low hold. The eyes are about 0.3 from the pipe, so
     * these are the heights that keep both fists in the frame (looking ahead) with their arms rising from below.
     */
    static final double HIGH = 0.04, LOW = -0.12;
    /** A hand lets go once the body has climbed it this far under the eyes. */
    static final double LET_GO = -0.2;
    /** How near the hands are drawn on the line (a fist at rest: 0.85). */
    static final double HAND_DISTANCE = 0.85;
    /** The top-out: ticks the hands stay on the top of the line while the body climbs past them. */
    static final int TOP = 12;
    /** In and out of the grip, ticks. */
    static final float IN = 3, OUT = 4;

    // per hand (0 right, 1 left): its hold's height (world), and a reach in progress
    private static final double[] HOLD = new double[2], FROM = new double[2], TO = new double[2];
    private static final int[] REACH_AT = {-100, -100}, REACH_LEN = {4, 4};
    private static final boolean[] REACHING = new boolean[2];
    // the last line held (kept a few ticks after letting go, for the hands to drop away from)
    private static double cx, cz, fx = 0, fz = 1, radius, holdX, holdZ;
    private static boolean had, topOut;
    private static double topY;
    private static int startedAt = -100, endedAt = -100, wasMode = -1;

    // ---- each tick: the hands' holds --------------------------------------------------------------------------------

    static void tick(LocalPlayer p) {
        int now = ClientMoves.ticks;
        GripRules.Line l = GripMoves.line;
        boolean on = GripMoves.active() && l != null;
        if (on && !had) begin(p, l, now);
        if (!on) {
            if (had) {
                had = false;
                endedAt = now;
                topOut = "top".equals(GripMoves.lastEnd);
                if (topOut) {
                    // the top of the line: the first block above the hands that isn't one
                    var m = new net.minecraft.core.BlockPos.MutableBlockPos();
                    int bx = Mth.floor(holdX + fx * (radius + 0.1)), bz = Mth.floor(holdZ + fz * (radius + 0.1));
                    int y = Mth.floor(p.getEyeY());
                    while (y < p.getEyeY() + 3 && GripRules.lineAt(p.level(), m, bx, bz, y + 0.5)) y++;
                    topY = y;
                }
            }
            return;
        }
        cx = l.cx();
        cz = l.cz();
        fx = l.fx();
        fz = l.fz();
        radius = l.radius();
        holdX = l.x();
        holdZ = l.z();
        double eye = p.getEyeY();
        int mode = GripMoves.mode;
        if (mode == GripMoves.SLIDE || mode == GripMoves.BRAKE) {
            // loose: the hands run down the line with you, the upper one a little lower than a hold
            for (int h = 0; h < 2; h++) {
                REACHING[h] = false;
                HOLD[h] = eye + (h == 0 ? HIGH - 0.08 : LOW + 0.04);
            }
        } else {
            for (int h = 0; h < 2; h++) {
                if (REACHING[h] && now - REACH_AT[h] >= REACH_LEN[h]) {
                    REACHING[h] = false;
                    HOLD[h] = TO[h];
                }
            }
            // climbing past a hand: it lets go and reaches up for a new hold, while the other holds
            double v = Math.max(0, GripMoves.v);
            for (int h = 0; h < 2; h++) {
                int o = 1 - h;
                if (!REACHING[h] && !REACHING[o] && HOLD[h] - eye < LET_GO && HOLD[h] <= HOLD[o]) {
                    int len = Mth.clamp((int) Math.round(0.5 / Math.max(v, 0.08)), 3, 6);
                    REACHING[h] = true;
                    REACH_AT[h] = now;
                    REACH_LEN[h] = len;
                    FROM[h] = HOLD[h];
                    TO[h] = eye + HIGH + v * len;
                }
            }
            // never a hold the body has dropped below (a brake that ended low) or far above the reach
            for (int h = 0; h < 2; h++) if (!REACHING[h]) HOLD[h] = Mth.clamp(HOLD[h], eye + LET_GO - 0.1, eye + HIGH + 0.06);
        }
        if (mode != wasMode && wasMode == GripMoves.SLIDE) {
            // out of a slide: the hands clamp where they are
            for (int h = 0; h < 2; h++) HOLD[h] = eye + (h == 0 ? HIGH - 0.06 : LOW);
        }
        wasMode = mode;
    }

    private static void begin(LocalPlayer p, GripRules.Line l, int now) {
        had = true;
        startedAt = now;
        double eye = p.getEyeY();
        // the catch: both hands come up from below onto the line (the right a little higher)
        for (int h = 0; h < 2; h++) {
            REACHING[h] = true;
            REACH_AT[h] = now;
            REACH_LEN[h] = h == 0 ? 3 : 4;
            FROM[h] = eye - 0.8;
            TO[h] = eye + (h == 0 ? HIGH : LOW);
            HOLD[h] = TO[h];
        }
        wasMode = GripMoves.mode;
    }

    // ---- each frame: the rig ----------------------------------------------------------------------------------------

    /** The world layer's source: true while the grip (or its letting go, or its top-out) shows. */
    static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return false;
        int now = ClientMoves.ticks;
        float in = Mth.clamp((now - startedAt + pt) / IN, 0, 1);
        float since = now - endedAt + pt;
        boolean topping = !had && topOut && since < TOP;
        float out = had ? 1 : topping ? Mth.clamp((TOP - since) / 4F, 0, 1) : 1 - Mth.clamp(since / OUT, 0, 1);
        if (out <= 0) return false;
        // the eye as the camera has it; the body faces the line
        var cam = mc.gameRenderer.mainCamera().position();
        double ex = cam.x, ey = cam.y, ez = cam.z;
        double rx = -fz, rz = fx; // the body's right, facing (fx, fz)
        rig.fx = fx;
        rig.fz = fz;
        double eye = Mth.lerp(pt, p.yo, p.getY()) + p.getEyeHeight();
        // the fists at a hand's usual size (nearer, their arms fill the frame: the quality pass's tours)
        rig.handDistance = HAND_DISTANCE;
        // the shoulders, a little low and back (the arms come up into view from the frame's lower corners)
        double sy = ey - 0.38;
        float t = now + pt;
        boolean slide = GripMoves.mode == GripMoves.SLIDE || GripMoves.mode == GripMoves.BRAKE;
        for (int h = 0; h < 2; h++) {
            double side = h == 0 ? 1 : -1;
            double y, lift = 0;
            // each fist closed on its own side of the pipe
            double hx = cx + rx * side * (radius + 0.03), hz = cz + rz * side * (radius + 0.03);
            if (topping) {
                // up onto the top of the line, palms down on it either side, pressing as the body climbs past
                float u = Mth.clamp(since / 4F, 0, 1);
                double e = u * u * (3 - 2 * u);
                double from = HOLD[h];
                y = from + (topY + 0.02 - from) * e;
                hx = cx + rx * side * 0.1 - fx * 0.05;
                hz = cz + rz * side * 0.1 - fz * 0.05;
            } else if (REACHING[h]) {
                float u = Mth.clamp((t - REACH_AT[h]) / REACH_LEN[h], 0, 1);
                double e = 1 - (1 - u) * (1 - u) * (1 - u); // out of the old hold fast, into the new one gently
                y = FROM[h] + (TO[h] - FROM[h]) * e;
                lift = 0.09 * Mth.sin(u * Mth.PI); // the hand comes off the line on its way up
            } else {
                y = slide ? eye + (HOLD[h] - p.getEyeY()) : HOLD[h];
            }
            // the slide: the line running through loosened hands, shaking with the friction
            if (GripMoves.mode == GripMoves.SLIDE) {
                double k = Mth.clamp(-GripMoves.v / GripRules.SLIDE_MAX, 0, 1);
                y += 0.018 * k * Mth.sin(t * 3.1F + h * 1.7F);
                double jx = 0.006 * k * Mth.sin(t * 4.7F + h * 2.3F);
                hx += rx * jx;
                hz += rz * jx;
            }
            hx -= fx * lift;
            hz -= fz * lift;
            // in: from below; out: dropping away toward the body
            double rest = ey - 0.85;
            float w = topping ? 1 : Math.min(in, out);
            y = rest + (y - rest) * w;
            hx = holdX + (hx - holdX) * w;
            hz = holdZ + (hz - holdZ) * w;
            double sx = ex + rx * side * 0.33 - fx * 0.12, sz = ez + rz * side * 0.33 - fz * 0.12;
            rig.reach(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, sy, sz, hx, y, hz, FirstPerson.Rig.ARM_REACH,
                    0, 1, 0, out);
        }
        if (topping) return true; // the legs are vanilla's climb's by now
        double gx = cx - fx * (radius + 0.05), gz = cz - fz * (radius + 0.05);
        // the legs: hips under the body, feet clamped on the line below, stepping as the body climbs
        double phase = GripMoves.travelled / 1.6 * Mth.TWO_PI;
        slide = GripMoves.mode == GripMoves.SLIDE;
        double hipY = ey - 0.87;
        for (int h = 0; h < 2; h++) {
            double side = h == 0 ? 1 : -1;
            double step = slide ? 0 : 0.22 * Math.sin(phase + h * Math.PI);
            double fy = ey - 1.5 + step;
            double footX = gx - fx * 0.08 + rx * side * 0.07, footZ = gz - fz * 0.08 + rz * side * 0.07;
            double hx = ex + rx * side * 0.12 - fx * 0.02, hz = ez + rz * side * 0.12 - fz * 0.02;
            rig.reach(h == 0 ? FirstPerson.Limb.RIGHT_LEG : FirstPerson.Limb.LEFT_LEG, hx, hipY, hz, footX, fy, footZ, FirstPerson.Rig.LEG_REACH,
                    fx, 0, fz, out);
        }
        // no torso: from inside the head it would only ever be a slab under the camera
        return true;
    }
}
