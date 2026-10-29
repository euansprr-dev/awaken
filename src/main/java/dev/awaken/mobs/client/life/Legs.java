package dev.awaken.mobs.client.life;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * One creature's feet (SPEC §23, "Motion"): where each stands in the world, which are stepping and how far along, and
 * the model-space spot each leg must reach this frame ({@link LegRig#apply}). Made once per creature with its
 * {@link Life}; nothing allocated after.
 *
 * <p>A planted foot stays where it stood in the world while the body moves over it, so it never slides. A gait clock
 * runs with the ground the body covers (and the arc its feet sweep when it turns on the spot); each foot lifts when the
 * clock reaches its place in the pattern (one at a time from the back when slow, alternating sets when fast) and swings
 * on an arc to where its home will be halfway through its next stand (or further, by the gait's lead), on the ground
 * under that spot, never further than its place in a steady stride. Setting off or changing gait, the first steps are
 * short and grow to the full stride. A foot left too far behind (a shove, a slide, a sudden start) steps at once; when
 * the body stops, the feet left out of place step back under it one
 * at a time; a calm creature now and then lifts a foot and sets it down again. Off the ground, hanging, swimming or
 * dying, the clips own the legs (the weight eases to 0) and the feet stand at home again when it lands.
 */
public final class Legs {
    final LegRig rig;
    // the feet in the world (blocks): where each stands or is this frame, and where a swing lifted off
    private final double[] fx, fy, fz, sx, sy, sz;
    /** Seconds ahead a planted foot's home is read to see which way it is going. */
    private static final float PEEK = 0.1F;
    /** tan 40°: the furthest a rigid leg leans from its shoulder before its planted foot slides. */
    public static final float SLIP = 0.8391F;
    private final boolean[] swinging;
    private final float[] u, dur, rise, prevQ;
    /** This frame's spot for each foot, model px (the leg reaches for it). */
    public final float[] tx, ty, tz;
    /** 0..1: how much the feet own the legs over the clips; how much the front pairs, and one more in a roar, have let go to them (a rear). */
    float weight, rear, rear2;
    /** The body over its feet: px it sinks (the ground under them, a step's share), pitch and roll (radians, model). */
    float groundY, groundPitch, groundRoll, gaitY, gaitRoll, gaitPitch;
    /** How far the front pair's feet are through their steps (0 planted, 1 a whole step up): the head nods with them. */
    float foreLift;
    /** Px the body drops so a long step's planted feet stay in reach (its hips vault over them). */
    float vault;
    /**
     * Degrees the body bone turns on the root toward its look (Life's twist): its hips go round with it, so the feet
     * stand and step under the turned body, though the model space they're given in stays the root's.
     */
    public float face;
    private boolean init, fast;
    private double ex, ez, hx, hz;
    private float lastYaw, clock, rate, still, going, nextRegrip, vx, vz, yawRate, t;
    private long seed;
    private Ground floor;

    /** {@code floor}: where the feet find the ground, or null for the client's world. */
    public Legs(LegRig rig, int seed, Ground floor) {
        this(rig, seed);
        this.floor = floor;
    }

    Legs(LegRig rig, int seed) {
        this.rig = rig;
        int n = rig.n;
        fx = new double[n];
        fy = new double[n];
        fz = new double[n];
        sx = new double[n];
        sy = new double[n];
        sz = new double[n];
        swinging = new boolean[n];
        u = new float[n];
        dur = new float[n];
        rise = new float[n];
        prevQ = new float[n];
        tx = new float[n];
        ty = new float[n];
        tz = new float[n];
        this.seed = 0x2545F4914F6CDD1DL ^ (seed * 0x9E3779B97F4A7C15L);
        nextRegrip = 2.0F + 3.0F * rand();
        clock = rand();
    }

    private float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) / (float) (1L << 24);
    }

    /**
     * Steps the feet to this frame. {@code bodyScale}: the renderer's scale on top of the entity's; {@code on}: the feet
     * may own the legs (on the ground, nothing else holds them);
     * {@code stride}: blocks a gait cycle covers; {@code run} 0..1 how much it runs; {@code calm}: it may re-grip.
     */
    void update(LivingEntityRenderState s, float dt, float bodyScale, boolean on, float stride, float run, boolean calm) {
        update(s.x, s.y, s.z, s.bodyRot, s.scale * bodyScale, dt, on, stride, run, calm);
    }

    /** The same for a body at (x, y, z) turned to yaw (degrees) and drawn at scale. */
    public void update(double x, double y, double z, float yaw, float scale, float dt, boolean on, float stride, float run, boolean calm) {
        t += dt;
        float rootYaw = yaw;
        yaw += face;
        MotionProfile.Gait g = rig.gait;
        float want = on ? 1.0F : 0.0F;
        weight += (want - weight) * Math.min(1.0F, dt / (want > weight ? 0.15F : 0.12F));
        float sc = Math.max(0.05F, scale);
        if (!on) {
            // the clips take the legs back; the feet stay planted while they do, then stand at home on landing
            if (weight < 0.01F) init = false;
            else if (init) out(x, y, z, rootYaw, sc);
            gaitY *= 0.8F;
            gaitRoll *= 0.8F;
            gaitPitch *= 0.8F;
            vault *= 0.8F;
            return;
        }
        double jump = (x - ex) * (x - ex) + (z - ez) * (z - ez);
        if (!init || jump > 9.0) { // a first sight, a landing or a teleport: the feet stand at home under the body
            ex = x;
            ez = z;
            lastYaw = yaw;
            vx = vz = yawRate = rate = 0;
            for (int i = 0; i < rig.n; i++) {
                home(i, x, z, yaw, sc);
                fx[i] = hx;
                fy[i] = ground(hx, y, hz);
                fz[i] = hz;
                swinging[i] = false;
            }
            init = true;
        }
        double mx = x - ex, mz = z - ez;
        float dyaw = Mth.wrapDegrees(yaw - lastYaw);
        ex = x;
        ez = z;
        lastYaw = yaw;
        float a = Math.min(1.0F, dt / 0.1F);
        vx += ((float) mx / dt - vx) * a;
        vz += ((float) mz / dt - vz) * a;
        yawRate += (dyaw / dt - yawRate) * a;
        float move = (float) Math.sqrt(mx * mx + mz * mz);
        float turn = Math.abs(dyaw) * Mth.DEG_TO_RAD * rig.turnRadius * sc;
        boolean wasFast = fast;
        fast = switch (g.pattern()) {
            case ALTERNATE, HOP -> true;
            case RIPPLE -> false;
            case RIPPLE_ALTERNATE, BOUND -> fast ? run > 0.25F : run > 0.45F;
        };
        // from a stand, or into a new gait, the first steps are short and quick and grow to the full stride over most of
        // a second (FA's swing grows with the pace the same way): the feet still waiting their turn are never left far behind
        if (fast != wasFast) going = Math.min(going, 0.45F);
        going = rate < 0.03F ? 0.0F : going + dt;
        float strideB = Math.max(0.2F, stride * sc) * Mth.lerp(Math.min(1.0F, going / 0.9F), 0.4F, 1.0F);
        float adv = (move + turn) / strideB;
        clock += adv;
        if (clock > 4096) clock -= 4096;
        rate += (adv / dt - rate) * a;
        float duty = Mth.lerp(run, g.dutyWalk(), g.dutyRun());
        float sw = 1.0F - duty;
        float lift = Mth.lerp(run, g.liftWalk(), g.liftRun()) / 16.0F * sc;
        float cycle = rate > 0.05F ? 1.0F / rate : 20.0F;
        float[] off = !fast ? rig.offRipple : g.pattern() == MotionProfile.Pattern.BOUND ? rig.offBound
                : g.pattern() == MotionProfile.Pattern.HOP ? rig.offHop : rig.offAlternate;
        if (fast != wasFast) for (int i = 0; i < rig.n; i++) prevQ[i] = frac(clock - adv + off[i]);
        boolean moving = rate > 0.15F;
        // a landing a foot aims for: its home halfway through the stand after this swing (or further, by the lead), never
        // further than its place in a steady stride nor than it can reach; a foot is lifted off that far behind
        float fwd = duty * strideB * (0.5F + g.lead()), back = duty * strideB * (0.5F - g.lead());
        float ahead = moving ? Math.min(3.0F, duty * cycle * (0.5F + g.lead())) : 0.0F;
        // the feet's own pace: the body's way plus its turn (a cow swinging round on the spot carries its feet round too)
        float way = Mth.sqrt(vx * vx + vz * vz), round = Math.abs(yawRate) * Mth.DEG_TO_RAD * rig.turnRadius * sc;
        float speed = way + round;
        // swinging round on the spot: no stride to lag by, so a foot steps as soon as it is a few settles off its spot
        // (front and back feet trail opposite ways, and a long lag crosses the legs)
        boolean pivot = round > 0.05F * sc && way < round;
        float furthest = Math.min(g.reach() * 0.8F * sc, fwd + 0.05F * strideB);
        if (speed * ahead > furthest) ahead = furthest / speed;
        int lifted = 0;
        for (int i = 0; i < rig.n; i++) if (swinging[i]) lifted++;
        for (int i = 0; i < rig.n; i++) {
            float q = frac(clock + off[i]);
            if (swinging[i]) {
                if (moving) dur[i] = Math.min(dur[i], Math.max(0.08F, sw * cycle));
                u[i] = Math.min(1.0F, u[i] + dt / dur[i]);
                swing(i, x, y, z, yaw, sc, moving ? (1.0F - u[i]) * dur[i] + ahead : 0.0F);
            } else {
                boolean due = adv > 0 && q < prevQ[i] && q < sw;
                // how far behind its home along the way that home goes, the body's turn included: turning on the spot
                // leaves the feet behind as surely as walking does
                home(i, x + vx * PEEK, z + vz * PEEK, yaw + yawRate * PEEK, sc);
                double nx = hx, nz = hz;
                home(i, x, z, yaw, sc);
                float hvx = (float) (nx - hx), hvz = (float) (nz - hz), hs = Mth.sqrt(hvx * hvx + hvz * hvz);
                float ddx = (float) (fx[i] - hx), ddz = (float) (fz[i] - hz);
                float far = Mth.sqrt(ddx * ddx + ddz * ddz);
                float trail = hs > 0.05F * PEEK ? -(ddx * hvx + ddz * hvz) / hs : 0.0F;
                if (due && far > 0.03F * sc) {
                    lift(i, Mth.clamp(sw * cycle, 0.08F, g.swing()), lift);
                    lifted++;
                } else if ((far > g.reach() * sc || moving && trail > back + 0.3F * strideB || pivot && far > 3.0F * g.settle() * sc)
                        && (!nearSwinging(i) || moving && trail > back + 0.38F * strideB)) { // dragging is worse than two up
                    lift(i, Mth.clamp(0.75F * sw * cycle, 0.12F, g.swing()), lift); // left behind: catch up now
                    lifted++;
                }
            }
            prevQ[i] = q;
        }
        // standing still: feet out of place step back under the body, the furthest first, up to half of them at once but
        // never two neighbours (after a turn, one at a time left the last leg leaning for a second and a half); a calm one
        // re-grips now and then
        still = rate < 0.03F ? still + dt : 0.0F;
        if (still > 0.2F) {
            int stepped = 0;
            while (lifted < Math.max(1, rig.n / 2)) {
                int worst = -1;
                float most = g.settle() * sc;
                for (int i = 0; i < rig.n; i++) {
                    if (swinging[i] || nearSwinging(i)) continue;
                    home(i, x, z, yaw, sc);
                    float ddx = (float) (fx[i] - hx), ddz = (float) (fz[i] - hz);
                    float far = Mth.sqrt(ddx * ddx + ddz * ddz);
                    if (far > most) {
                        most = far;
                        worst = i;
                    }
                }
                if (worst < 0) break;
                lift(worst, 0.24F, Math.max(lift, 1.5F / 16.0F * sc));
                lifted++;
                stepped++;
            }
            if (stepped == 0 && lifted == 0 && calm && g.regripMax() > 0 && still > 1.5F && t >= nextRegrip) {
                int i = Math.min(rig.n - 1, (int) (rand() * rig.n));
                if (!nearSwinging(i)) lift(i, 0.3F, (1.2F + 1.5F * rand()) / 16.0F * sc);
                nextRegrip = t + g.regripMin() + (g.regripMax() - g.regripMin()) * rand();
            }
        }
        if (!calm) nextRegrip = Math.max(nextRegrip, t + g.regripMin());
        // a planted foot left too far slides after the body, as FA's feet slide: a rigid leg never leans past SLIP from
        // its shoulder, and no foot trails more than four settles while the body swings round on the spot (its steps
        // can't keep up with a quick turn); a blocky leg thrown out sideways or legs crossed under the body read as broken
        if (rig.anyStiff || pivot) {
            for (int i = 0; i < rig.n; i++) {
                if (swinging[i]) continue;
                home(i, x, z, yaw, sc);
                double ddx = fx[i] - hx, ddz = fz[i] - hz, far = Math.sqrt(ddx * ddx + ddz * ddz);
                double most = rig.stiff[i] ? (rig.len[i] + rig.lift[i]) * SLIP / 16.0F * sc : Double.MAX_VALUE;
                if (pivot) most = Math.min(most, 4.0F * g.settle() * sc);
                if (far > most) {
                    fx[i] = hx + ddx * most / far;
                    fz[i] = hz + ddz * most / far;
                }
            }
        }
        out(x, y, z, rootYaw, sc);
        carriage(y, sc, dt, g);
    }

    /** Whether foot i is in the air this frame. */
    public boolean stepping(int i) {
        return swinging[i];
    }

    private boolean nearSwinging(int i) {
        for (int j : rig.near[i]) if (swinging[j]) return true;
        return false;
    }

    private void lift(int i, float seconds, float height) {
        swinging[i] = true;
        u[i] = 0;
        dur[i] = seconds;
        rise[i] = height;
        sx[i] = fx[i];
        sy[i] = fy[i];
        sz[i] = fz[i];
    }

    /** A foot in the air: on an arc from where it lifted to its landing (re-aimed each frame), planted at the end. */
    private void swing(int i, double x, double y, double z, float yaw, float sc, float ahead) {
        home(i, x + vx * ahead, z + vz * ahead, yaw + yawRate * ahead, sc);
        double gy = ground(hx, y, hz);
        float k = u[i];
        float e = k * k * (3.0F - 2.0F * k);
        // a rigid leg has no knee to fold: its hoof swings through a touch past its spot and settles back onto it
        if (rig.stiff[i]) e += 0.25F * k * Mth.sin(Mth.PI * k);
        // up quickly, down a touch slower: the arc peaks before the middle of the swing
        float arc = Mth.sin(Mth.PI * (float) Math.pow(k, 0.75));
        fx[i] = sx[i] + (hx - sx[i]) * e;
        fy[i] = sy[i] + (gy - sy[i]) * e + rise[i] * arc;
        fz[i] = sz[i] + (hz - sz[i]) * e;
        if (k >= 1.0F) {
            fx[i] = hx;
            fy[i] = gy;
            fz[i] = hz;
            swinging[i] = false;
        }
    }

    /** Leg i's home in the world for a body at (x, z) turned to yaw (degrees), into hx, hz. */
    private void home(int i, double x, double z, float yaw, float sc) {
        float th = (180.0F - yaw) * Mth.DEG_TO_RAD, c = Mth.cos(th), sn = Mth.sin(th);
        float lx = -rig.homeX[i] / 16.0F * sc, lz = rig.homeZ[i] / 16.0F * sc;
        hx = x + c * lx + sn * lz;
        hz = z - sn * lx + c * lz;
    }

    /** The feet's spots in model px this frame (the inverse of the renderer's yaw, flip and lift). */
    private void out(double x, double y, double z, float yaw, float sc) {
        float th = (180.0F - yaw) * Mth.DEG_TO_RAD, c = Mth.cos(th), sn = Mth.sin(th);
        for (int i = 0; i < rig.n; i++) {
            float wx = (float) (fx[i] - x), wy = (float) (fy[i] - y), wz = (float) (fz[i] - z);
            float lx = (c * wx - sn * wz) / sc, lz = (sn * wx + c * wz) / sc, ly = wy / sc;
            tx[i] = -lx * 16.0F;
            ty[i] = (1.501F - ly) * 16.0F;
            tz[i] = lz * 16.0F;
        }
    }

    /**
     * The body rides its feet: a little lower over a dip, nose up a rise, leaning across a slope (from the planted
     * feet), sinking a touch while feet are up and leaning off a lifted one (the step's weight shift).
     */
    private void carriage(double y, float sc, float dt, MotionProfile.Gait g) {
        float sum = 0, front = 0, back = 0, left = 0, right = 0, up = 0, lean = 0, fore = 0, hind = 0, need = 0;
        int nf = 0, nb = 0, nl = 0, nr = 0, np = 0;
        for (int i = 0; i < rig.n; i++) {
            if (swinging[i]) {
                float arc = Mth.sin(Mth.PI * u[i]);
                up += arc;
                // a quadruped's forequarters carry more of its weight: a diagonal pair up still tips it off the forefoot
                lean -= rig.side[i] * arc * (rig.perSide == 2 && rig.rank[i] == 1 ? 0.6F : 1.0F);
                if (rig.rank[i] == 0) fore += arc;
                else if (rig.rank[i] == rig.perSide - 1) hind += arc;
                continue;
            }
            float h = (float) (fy[i] - y) / sc;
            sum += h;
            np++;
            need = Math.max(need, rig.sink(i, tx[i], ty[i], tz[i]));
            if (rig.rank[i] == 0) {
                front += h;
                nf++;
            } else if (rig.rank[i] == rig.perSide - 1) {
                back += h;
                nb++;
            }
            if (rig.side[i] > 0) {
                left += h;
                nl++;
            } else {
                right += h;
                nr++;
            }
        }
        float wantY = 0, wantP = 0, wantR = 0;
        if (np > 0) {
            wantY = Mth.clamp(-sum / np * 8.0F, -4.0F, 4.0F);
            if (nf > 0 && nb > 0 && rig.perSide > 1) wantP = Mth.clamp(-(float) Math.atan2(front / nf - back / nb, rig.span), -0.35F, 0.35F);
            if (nl > 0 && nr > 0) wantR = Mth.clamp(-(float) Math.atan2(left / nl - right / nr, rig.width), -0.35F, 0.35F);
        }
        float a = Math.min(1.0F, dt / 0.12F);
        groundY += (wantY - groundY) * a;
        groundPitch += (wantP - groundPitch) * a;
        groundRoll += (wantR - groundRoll) * a;
        float b = Math.min(1.0F, dt / 0.05F);
        gaitY += (g.bob() * up / Math.max(1.0F, rig.n * 0.5F) - gaitY) * b;
        gaitRoll += (g.roll() * lean - gaitRoll) * b;
        // a forefoot up dips the nose, a hind foot up the rump: the body rocks fore and aft twice a stride; a hop noses up
        // as it leaves the ground and down as it comes in (its single re-steps don't rock it)
        float pitch;
        if (g.pattern() == MotionProfile.Pattern.HOP) {
            float flight = 0;
            for (int i = 0; i < rig.n; i++) flight += swinging[i] ? u[i] : 0.0F;
            pitch = np == 0 && rig.n > 0 ? -g.rock() * Mth.cos(Mth.PI * flight / rig.n) : 0.0F;
        } else {
            pitch = rig.perSide > 1 ? g.rock() * (fore - hind) : 0.0F;
        }
        gaitPitch += (pitch - gaitPitch) * b;
        foreLift += (fore - foreLift) * b;
        // the vault: past what the crouch and the ground already sink it, the hips drop to the furthest planted foot
        vault += (Mth.clamp(need - g.crouch() - groundY, 0.0F, rig.vaultMax) - vault) * b;
    }

    private double ground(double x, double y, double z) {
        if (floor == null) floor = new LegGround();
        return floor.top(x, y, z);
    }

    /** Where a foot finds the ground under (x, z) for a body standing at y. */
    public interface Ground {
        double top(double x, double y, double z);
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }
}
