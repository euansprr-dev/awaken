package dev.awaken.arms;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Inertialization for the first-person swings (SPEC §25 "Swing Studio"; Bollo, GDC 2018): when the source of a pose
 * changes (a chained strike starting from its own rest pose, a move ending or refused mid-swing) the pose on screen
 * does not jump to the new source. The gap between them, and the old motion's speed along it, decays to nothing over a
 * few ticks on a quintic that never overshoots, so the next strike leaves from wherever the blade really is (Euan
 * 2026-10-28: "each strike starts from rest... it kind of feels like it glitches or teleports"). Plain maths, so a
 * GameTest can hold it to its rules; one instance per pose it carries (each hand, the view's lean).
 */
public final class SwingBlend {
    /** Below this gap (blocks, or radians) a change of source needs no blend. */
    private static final float EPS = 1e-4F;

    /** One offset decaying from x0 to 0 over T ticks, leaving with velocity v0, no overshoot. */
    public static final class Curve {
        private float a0, a, b, c, v0, x0, length;
        private boolean on;

        /** Starts the decay. A velocity away from 0 is dropped (it would carry the pose further off first). */
        public void set(float x0, float v0, float maxTicks) {
            if (x0 < EPS || maxTicks <= 0) {
                on = false;
                return;
            }
            float v = Math.min(0F, v0);
            float t = maxTicks;
            if (v < 0) t = Math.min(t, -5F * x0 / v);
            float t2 = t * t;
            this.a0 = Math.max(0F, (-8F * v * t - 20F * x0) / t2);
            this.a = -(a0 * t2 + 6F * v * t + 12F * x0) / (2F * t2 * t2 * t);
            this.b = (3F * a0 * t2 + 16F * v * t + 30F * x0) / (2F * t2 * t2);
            this.c = -(3F * a0 * t2 + 12F * v * t + 20F * x0) / (2F * t2 * t);
            this.v0 = v;
            this.x0 = x0;
            this.length = t;
            this.on = true;
        }

        /** The offset {@code t} ticks into the decay (0 from its end on). */
        public float at(float t) {
            if (!on || t >= length) return 0F;
            t = Math.max(0F, t);
            return (((a * t + b) * t + c) * t + a0 * 0.5F) * t * t + v0 * t + x0;
        }

        public float length() {
            return on ? length : 0F;
        }

        public void off() {
            on = false;
        }
    }

    private final Curve pos = new Curve(), rot = new Curve();
    private final Vector3f lastD = new Vector3f(), velD = new Vector3f(), velW = new Vector3f(), dir = new Vector3f(),
            axis = new Vector3f(), tmp = new Vector3f();
    private final Quaternionf lastQ = new Quaternionf(), dq = new Quaternionf(), off = new Quaternionf();
    private float lastT = Float.NaN, start;

    /**
     * The source changed at {@code now} (ticks): its pose there is {@code d}, {@code q}, moving at {@code vd} (blocks a
     * tick) and turning at {@code w} (radians a tick about an axis). The pose last shown is carried into it over at
     * most {@code ticks}.
     */
    public void begin(float now, Vector3f d, Quaternionf q, Vector3f vd, Vector3f w, float ticks) {
        start = now;
        if (Float.isNaN(lastT)) {
            pos.off();
            rot.off();
            return;
        }
        // the move from the new pose to the old one, and the old motion against the new along it
        dir.set(lastD).sub(d);
        float x = dir.length();
        if (x > EPS) {
            dir.div(x);
            pos.set(x, tmp.set(velD).sub(vd).dot(dir), ticks);
        } else {
            pos.off();
        }
        // the turn from the new pose to the old one (last = off . new), the shorter way round
        q.conjugate(dq);
        lastQ.mul(dq, off);
        if (off.w < 0) off.set(-off.x, -off.y, -off.z, -off.w);
        float s = (float) Math.sqrt(Math.max(0F, 1F - off.w * off.w));
        float angle = 2F * (float) Math.acos(Math.min(1F, off.w));
        if (s > 1e-6F && angle > EPS) {
            axis.set(off.x / s, off.y / s, off.z / s);
            rot.set(angle, tmp.set(velW).sub(w).dot(axis), ticks);
        } else {
            rot.off();
        }
    }

    /** This frame's pose from the source ({@code d}, {@code q}, in place) with what is left of the blend added. */
    public void apply(float now, Vector3f d, Quaternionf q) {
        float t = now - start;
        float p = pos.at(t);
        if (p != 0) d.fma(p, dir);
        float r = rot.at(t);
        if (r != 0) q.premul(off.fromAxisAngleRad(axis.x, axis.y, axis.z, r));
        remember(now, d, q);
    }

    /** Whether any of the blend is left at {@code now}. */
    public boolean active(float now) {
        float t = now - start;
        return t < pos.length() || t < rot.length();
    }

    /** Forgets what was shown (the next change of source starts clean). */
    public void reset() {
        lastT = Float.NaN;
        pos.off();
        rot.off();
        velD.zero();
        velW.zero();
    }

    private void remember(float now, Vector3f d, Quaternionf q) {
        float dt = now - lastT;
        if (Float.isNaN(lastT) || dt > 2F || dt < 0) {
            velD.zero();
            velW.zero();
        } else if (dt > 1e-3F) {
            // the speed shown: over the last frame (a frame drawn twice keeps the one before)
            velD.set(d).sub(lastD).div(dt);
            lastQ.conjugate(dq);
            q.mul(dq, dq); // dq = q . last^-1, the turn over the frame
            if (dq.w < 0) dq.set(-dq.x, -dq.y, -dq.z, -dq.w);
            float s = (float) Math.sqrt(Math.max(0F, 1F - dq.w * dq.w));
            float angle = 2F * (float) Math.acos(Math.min(1F, dq.w));
            if (s > 1e-6F) velW.set(dq.x / s, dq.y / s, dq.z / s).mul(angle / dt);
            else velW.zero();
        } else {
            return;
        }
        lastD.set(d);
        lastQ.set(q);
        lastT = now;
    }

    /** One vector carried the same way (the view's lean, degrees). */
    public static final class Vec {
        private final Curve curve = new Curve();
        private final Vector3f last = new Vector3f(), vel = new Vector3f(), dir = new Vector3f(), tmp = new Vector3f();
        private float lastT = Float.NaN, start;

        public void begin(float now, Vector3f v, Vector3f velocity, float ticks) {
            start = now;
            if (Float.isNaN(lastT)) {
                curve.off();
                return;
            }
            dir.set(last).sub(v);
            float x = dir.length();
            if (x <= EPS) {
                curve.off();
                return;
            }
            dir.div(x);
            curve.set(x, tmp.set(vel).sub(velocity).dot(dir), ticks);
        }

        public void apply(float now, Vector3f v) {
            float c = curve.at(now - start);
            if (c != 0) v.fma(c, dir);
            float dt = now - lastT;
            if (Float.isNaN(lastT) || dt > 2F || dt < 0) vel.zero();
            else if (dt > 1e-3F) vel.set(v).sub(last).div(dt);
            else return;
            last.set(v);
            lastT = now;
        }

        public boolean active(float now) {
            return now - start < curve.length();
        }
    }
}
