package dev.awaken.mobs.client.kit;

import dev.awaken.mobs.client.anim.HeroRig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Function;

/**
 * One kit body's motion state between frames (SPEC §35.10), kept per entity by {@link KitRenderer}:
 * <ul>
 *   <li><b>No snaps.</b> When what it plays changes (a move begins, a string links on, a reaction starts, the kneel),
 *   the gap between the pose it showed last frame and the pose it must show now is kept as an offset that eases out
 *   on a critically damped spring (inertialization). A string's next blow eases instead on a cubic that starts at
 *   the last pose and speed and reaches nothing exactly at its peak: its wind-up starts where the last blow ended.</li>
 *   <li><b>Secondary parts.</b> The rig's springs (a chain, tassets, veils) lag behind their parents' turn and swing
 *   back, a little past, and settle.</li>
 *   <li><b>The hit pause.</b> Its move clock stops for {@link #PAUSE} seconds when a blow lands, either way.</li>
 * </ul>
 * Allocation-free per frame once its arrays exist.
 */
public final class KitBlend {
    /** The half-life (seconds) of a transition's offset: most of it gone in a fifth of a second. */
    static final float HALF_LIFE = 0.07F;
    /** The hit pause: two frames at 60 frames a second, a touch more so it reads at 40. */
    static final float PAUSE = 0.04F;
    /** The furthest a secondary part swings from where its parents hold it, radians (about 40°). */
    static final float SWING = 0.7F;

    private ModelPart[] parts = new ModelPart[0];
    private float[] out = new float[0], off = new float[0], vel = new float[0], outVel = new float[0];
    private boolean seeded;
    private int key = Integer.MIN_VALUE;
    private float last = Float.NaN;
    // a link (a string's next blow): the offset eases out on a cubic over this many seconds, to the new move's peak
    private float link, linkSince;
    // springs: the part, the parts above it, and per spring its swing (x and z) and the parents' last turn
    private ModelPart[] springPart = new ModelPart[0];
    private ModelPart[][] springUp = new ModelPart[0][];
    private float[] springGain = new float[0], springW = new float[0], springZeta = new float[0];
    private float[] sx = new float[0], sv = new float[0], lastUp = new float[0];
    private float lastYaw = Float.NaN;
    // the hit pause and the shake, per move start
    long pauseUntil;
    float paused;
    int pausedFor = Integer.MIN_VALUE, shookFor = Integer.MIN_VALUE, hurtSeen = Integer.MIN_VALUE, playerHurtSeen = Integer.MIN_VALUE;
    float lastReal = Float.NaN;
    /** Set by the renderer each frame: seconds to the peak if what plays now is a string's next blow, else 0. */
    float nextLink;
    int lastMove = -1, lastStart = Integer.MIN_VALUE;

    /** Binds the model's parts (once; a body's model never changes). */
    public void bind(ModelPart root, List<ModelPart> all, HeroRig rig) {
        if (parts.length == all.size()) return;
        Function<String, ModelPart> lookup = root.createPartLookup();
        parts = all.toArray(new ModelPart[0]);
        out = new float[parts.length * 6];
        off = new float[parts.length * 6];
        vel = new float[parts.length * 6];
        outVel = new float[parts.length * 6];
        int n = rig.springs.size();
        springPart = new ModelPart[n];
        springUp = new ModelPart[n][];
        springGain = new float[n];
        springW = new float[n];
        springZeta = new float[n];
        sx = new float[n * 2];
        sv = new float[n * 2];
        lastUp = new float[n * 2];
        for (int i = 0; i < n; i++) {
            HeroRig.Spring sp = rig.springs.get(i);
            springPart[i] = lookup.apply(sp.bone());
            int b = rig.bone(sp.bone());
            int depth = 0;
            for (int p = b < 0 ? -1 : rig.bones.get(b).parent(); p >= 0; p = rig.bones.get(p).parent()) depth++;
            springUp[i] = new ModelPart[depth];
            int k = 0;
            for (int p = b < 0 ? -1 : rig.bones.get(b).parent(); p >= 0; p = rig.bones.get(p).parent()) springUp[i][k++] = lookup.apply(rig.bones.get(p).name());
            springGain[i] = sp.gain();
            springW[i] = Mth.TWO_PI * sp.hz();
            springZeta[i] = sp.damping();
        }
        seeded = false;
    }

    /**
     * After every layer and the feet: eases out what changed since the last frame, swings the secondary parts.
     * {@code now} is the body's clock in seconds, {@code key} names what it plays (a change is a transition),
     * {@code yaw} the body's turn in degrees.
     */
    public void apply(float now, int key, float yaw) {
        float dt = Float.isNaN(last) ? 0 : now - last;
        last = now;
        if (dt < 0 || dt > 0.25F) { // unseen for a while, or the clock went back: start clean
            seeded = false;
            dt = 0;
        }
        int n = parts.length;
        if (!seeded) {
            java.util.Arrays.fill(off, 0);
            java.util.Arrays.fill(vel, 0);
            java.util.Arrays.fill(outVel, 0);
            this.key = key;
            link = 0;
        } else if (key != this.key) {
            // a transition: keep showing last frame's pose, as an offset over the new one that eases out, still moving
            // the way it moved (the new pose is taken to start still: every clip starts and ends on a still key)
            this.key = key;
            link = nextLink;
            linkSince = 0;
            for (int i = 0; i < n; i++) {
                ModelPart p = parts[i];
                int o = i * 6;
                off[o] = out[o] - p.xRot;
                off[o + 1] = out[o + 1] - p.yRot;
                off[o + 2] = out[o + 2] - p.zRot;
                off[o + 3] = out[o + 3] - p.x;
                off[o + 4] = out[o + 4] - p.y;
                off[o + 5] = out[o + 5] - p.z;
                for (int c = 0; c < 3; c++) off[o + c] = Mth.wrapDegrees(off[o + c] * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                System.arraycopy(outVel, o, vel, o, 6);
            }
        }
        // critically damped decay of every offset toward nothing; on a link, the cubic (off and vel keep where it began)
        float w = 2 * 0.6931F / HALF_LIFE, e = (float) Math.exp(-w * dt);
        float h00 = 0, h10 = 0;
        if (link > 0) {
            linkSince += dt;
            float a = Math.min(1, linkSince / link);
            h00 = 2 * a * a * a - 3 * a * a + 1;
            h10 = (a * a * a - 2 * a * a + a) * link;
            if (a >= 1) {
                link = 0;
                java.util.Arrays.fill(off, 0);
                java.util.Arrays.fill(vel, 0);
            }
        }
        for (int i = 0; i < n; i++) {
            ModelPart p = parts[i];
            int o = i * 6;
            for (int c = 0; c < 6; c++) {
                float x = off[o + c], v = vel[o + c];
                if (x == 0 && v == 0) continue;
                if (link > 0) {
                    float d = h00 * x + h10 * v;
                    switch (c) {
                        case 0 -> p.xRot += d;
                        case 1 -> p.yRot += d;
                        case 2 -> p.zRot += d;
                        case 3 -> p.x += d;
                        case 4 -> p.y += d;
                        default -> p.z += d;
                    }
                    continue;
                }
                float j = v + w * x;
                off[o + c] = (x + j * dt) * e;
                vel[o + c] = (v - w * j * dt) * e;
                if (Math.abs(off[o + c]) < 1e-5F && Math.abs(vel[o + c]) < 1e-4F) off[o + c] = vel[o + c] = 0;
            }
            if (link <= 0) {
                p.xRot += off[o];
                p.yRot += off[o + 1];
                p.zRot += off[o + 2];
                p.x += off[o + 3];
                p.y += off[o + 4];
                p.z += off[o + 5];
            }
            if (dt > 0) {
                outVel[o] = (p.xRot - out[o]) / dt;
                outVel[o + 1] = (p.yRot - out[o + 1]) / dt;
                outVel[o + 2] = (p.zRot - out[o + 2]) / dt;
                outVel[o + 3] = (p.x - out[o + 3]) / dt;
                outVel[o + 4] = (p.y - out[o + 4]) / dt;
                outVel[o + 5] = (p.z - out[o + 5]) / dt;
            }
            out[o] = p.xRot;
            out[o + 1] = p.yRot;
            out[o + 2] = p.zRot;
            out[o + 3] = p.x;
            out[o + 4] = p.y;
            out[o + 5] = p.z;
        }
        // the springs last, on the eased pose (a transition's jump never jolts them) and outside what is kept
        springs(dt, yaw);
        seeded = true;
    }

    /**
     * Each secondary part lags its parents' pitch and roll (and the body's turn) and swings back on its spring. Every
     * spring reads its parents before any spring moves them (a chain's links never feed each other), and its swing
     * eases into its limit instead of stopping on it.
     */
    private void springs(float dt, float yaw) {
        float dYaw = Float.isNaN(lastYaw) || dt == 0 ? 0 : Mth.wrapDegrees(yaw - lastYaw) * Mth.DEG_TO_RAD;
        lastYaw = yaw;
        int n = springPart.length;
        for (int i = 0; i < n; i++) {
            float ax = 0, az = 0;
            for (ModelPart u : springUp[i]) {
                if (u == null) continue;
                ax += u.xRot;
                az += u.zRot;
            }
            int o = i * 2;
            if (seeded && dt > 0) {
                // the parents turned by (dax, daz): the part is left behind by gain of that, then springs back
                float dax = Mth.wrapDegrees((ax - lastUp[o]) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                float daz = Mth.wrapDegrees((az - lastUp[o + 1]) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD - dYaw * 0.5F;
                sx[o] -= springGain[i] * dax;
                sx[o + 1] -= springGain[i] * daz;
                float w = springW[i], z = springZeta[i];
                for (int c = 0; c < 2; c++) {
                    float a = -w * w * sx[o + c] - 2 * z * w * sv[o + c];
                    sv[o + c] += a * dt;
                    sx[o + c] += sv[o + c] * dt;
                }
            } else {
                sx[o] = sx[o + 1] = sv[o] = sv[o + 1] = 0;
            }
            lastUp[o] = ax;
            lastUp[o + 1] = az;
        }
        for (int i = 0; i < n; i++) {
            ModelPart p = springPart[i];
            if (p == null) continue;
            p.xRot += SWING * (float) Math.tanh(sx[i * 2] / SWING);
            p.zRot += SWING * (float) Math.tanh(sx[i * 2 + 1] / SWING);
        }
    }

    /** Starts a hit pause now (wall clock), for the move that began at {@code moveStart}. */
    void pause(int moveStart) {
        if (pausedFor != moveStart) {
            pausedFor = moveStart;
            paused = 0;
        }
        pauseUntil = System.nanoTime() + (long) (PAUSE * 1e9F);
    }
}
