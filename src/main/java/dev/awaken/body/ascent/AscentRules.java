package dev.awaken.body.ascent;

import net.minecraft.util.Mth;

/**
 * The Ascent's numbers (SPEC §17) and the plain physics both sides run: a rope swing, the momentum you carry after
 * letting go, a glide on wings, and momentum flight. Units are blocks and ticks; nothing here touches a player or the
 * world, so GameTests call it directly and the client predicts with exactly what the server checks.
 * <p>
 * Every state keeps its own velocity instead of vanilla's (whose air drag of 0.91 a tick would eat a swing's speed in
 * a second): the client sets the body's motion each tick from {@link Kin} and lets vanilla's collision move it.
 */
public final class AscentRules {
    private AscentRules() {}

    /** A player's gravity (vanilla's). Every state falls at this rate; only the wings turn the fall into speed. */
    public static final double G = 0.08;

    // ---- tether: the rope swing ----------------------------------------------------------------------------------
    /** How far a rope reaches, and the shortest rope (shorter ones pull you in instead of swinging). */
    public static final double TETHER_RANGE = 44, TETHER_MIN = 3.0;
    /** Where the rope pulls on the body: the raised hand, above the feet. */
    public static final double HAND_HEIGHT = 1.55;
    /** Air drag per tick while swinging (far less than vanilla's: a swing keeps its speed). */
    public static final double TETHER_DRAG = 0.0025;
    /** Forward input adds this much speed per tick along the swing, most at the bottom of the arc (pumping). */
    public static final double TETHER_PUMP = 0.016;
    /**
     * Flow (0..1, SPEC §17.6): at full flow the pump is 25% stronger and the carry keeps 0.4% more speed a tick.
     * Mastery (levels 0-10 per track): rope reach +0.4 a level, pump +3% a level; the Leaper's double jump +0.012 a level
     * and dash +2% a level; the glide's drag -2% a level.
     */
    public static final double FLOW_PUMP = 0.25, FLOW_CARRY = 0.004;
    public static final double MASTERY_REACH = 0.4, MASTERY_PUMP = 0.03, MASTERY_DOUBLE = 0.012, MASTERY_DASH = 0.02,
            MASTERY_GLIDE_DRAG = 0.02;
    /** Sideways input swings the arc's plane round. */
    public static final double TETHER_STEER = 0.012;
    /** Reeling in (sneak) shortens the rope this much per tick. */
    public static final double TETHER_REEL = 0.34;
    /**
     * A rope too long to clear the ground reels in this fast as it bites: you are pulled up into the swing (a yank,
     * not a drag along the floor).
     */
    public static final double TETHER_FIT_REEL = 1.2;
    /** The tug as a rope bites: this much speed along the swing, in the way you look. */
    public static final double TETHER_YANK = 0.22;
    /** A swing survives this many ticks touching the ground (a scrape) before it counts as landed. */
    public static final int TETHER_GROUND_TICKS = 6;
    public static final double TETHER_MAX_SPEED = 2.6;
    /** The arc's lowest point stays this far above the ground under the anchor (the rope is shortened to fit). */
    public static final double TETHER_CLEARANCE = 1.8;
    /** An anchor less than this above the hand pulls you in (a zip) instead of swinging. */
    public static final double TETHER_SWING_MIN_RISE = 2.0;
    /** Jumping off the rope: this much up, and the swing's speed times this. */
    public static final double LAUNCH_UP = 0.46, LAUNCH_BOOST = 1.12;
    /** Letting go while rising in the front half of the arc keeps a little extra (the release window). */
    public static final double RELEASE_BOOST = 1.06;
    /** A zip pulls you toward the anchor: acceleration per tick, top speed, and where it lets go. */
    public static final double ZIP_ACCEL = 0.24, ZIP_SPEED = 1.45, ZIP_ARRIVE = 2.2;

    // ---- the Leaper's air moves (tier D-C) -------------------------------------------------------------------------
    /** A double jump: once an airtime, this much up on top of any rise you still have. */
    public static final double DOUBLE_JUMP = 0.52;
    /** An air dash: once an airtime, along the look's heading at this speed (or a third faster than you were going). */
    public static final double DASH_SPEED = 0.95, DASH_GAIN = 1.33, DASH_LIFT = 0.08;
    /** A dive slam: from at least this high, straight down at this speed; the landing's shock reaches this far. */
    public static final double SLAM_MIN_HEIGHT = 4, SLAM_SPEED = 1.7, SLAM_RADIUS = 3.5;

    // ---- carry: the momentum you keep after letting go ------------------------------------------------------------
    public static final double CARRY_DRAG_H = 0.988, CARRY_DRAG_V = 0.98;
    /** Air control: input bends the path, never speeds it past what you had (or this, when slow). */
    public static final double CARRY_STEER = 0.02, CARRY_STEER_FLOOR = 0.24;

    // ---- glide: wings of light ----------------------------------------------------------------------------------
    /** Drag per tick, times speed squared: a 6° dive holds about 1.15 blocks a tick, a vertical one tops out near 3.6. */
    public static final double GLIDE_DRAG = 0.0062;
    /** How much of the gap to the look the path closes each tick at full speed (less when slow: lift needs air). */
    public static final double GLIDE_TURN = 0.16;
    /** Below this speed the wings can't hold the line: the path sags into a sink. */
    public static final double GLIDE_STALL = 0.42;
    /** Unfolding the wings catches a fall: the descent keeps this share, the rest turns forward. */
    public static final double GLIDE_CATCH = 0.45;
    /** The sonic boom fires crossing this speed, and arms again below the second. */
    public static final double BOOM_SPEED = 2.2, BOOM_REARM = 1.6;

    // ---- soar: momentum flight (Skyborn) --------------------------------------------------------------------------
    /** Thrust per tick holding forward, and boosting; drag times speed squared (cruise ≈ 1.25, boost ≈ 2.25). */
    public static final double SOAR_THRUST = 0.0098, SOAR_BOOST = 0.032, SOAR_DRAG = 0.0063;
    public static final double SOAR_TURN = 0.2;
    /** With no forward input the body hovers: speed bleeds by this each tick and climbs or sinks on jump and sneak. */
    public static final double SOAR_HOVER_DAMP = 0.88, SOAR_CLIMB = 0.32, SOAR_SINK = 0.36;
    public static final double SOAR_MAX = 3.2;
    /** A wing beat (jump while soaring) lifts you this much. */
    public static final double SOAR_FLAP = 0.38;

    /** A body's point and velocity: the hand point on a rope, the feet otherwise. Plain fields, nothing allocated. */
    public static final class Kin {
        public double x, y, z, vx, vy, vz;

        public Kin set(double x, double y, double z, double vx, double vy, double vz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            return this;
        }

        public double speed() {
            return Math.sqrt(vx * vx + vy * vy + vz * vz);
        }

        public double hspeed() {
            return Math.sqrt(vx * vx + vz * vz);
        }

        void scaleV(double f) {
            vx *= f;
            vy *= f;
            vz *= f;
        }

        void capSpeed(double max) {
            double s = speed();
            if (s > max) scaleV(max / s);
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** The look's x, z for a yaw (Minecraft's: yaw 0 looks along +z). */
    static double fx(float yaw) {
        return -Mth.sin(yaw * Mth.DEG_TO_RAD);
    }

    static double fz(float yaw) {
        return Mth.cos(yaw * Mth.DEG_TO_RAD);
    }

    /** Input (forward -1..1, right -1..1) as a horizontal world direction, unnormalised. Writes x, z into out[0..1]. */
    static void inputDir(float yaw, double fwd, double right, double[] out) {
        double ax = fx(yaw), az = fz(yaw);
        // facing +z, your right hand points to -x
        double rx = -az, rz = ax;
        out[0] = ax * fwd + rx * right;
        out[1] = az * fwd + rz * right;
    }

    // ---- tether -------------------------------------------------------------------------------------------------

    /**
     * One tick of a swing. {@code k} is the hand point and its velocity; (ax, ay, az) the anchor; {@code len} the rope.
     * Gravity and a little air act, forward input pumps along the swing near the bottom, sideways input turns the
     * arc. A taut rope takes the outward part of the velocity (returned as the rope's pull: 0 while slack) and the
     * hand moves along the rope's sphere on a great circle, its velocity turned with it, so the swing keeps its
     * energy exactly but for drag and input. The new position is written into {@code k}.
     */
    public static double tetherStep(Kin k, double ax, double ay, double az, double len, float yaw, double fwd, double right,
                                    double[] scratch) {
        k.vy -= G;
        double rx = k.x - ax, ry = k.y - ay, rz = k.z - az;
        double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (r < 1e-6) {
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
            return 0;
        }
        double nx = rx / r, ny = ry / r, nz = rz / r;
        double below = -ny; // 1 straight under the anchor, 0 level with it
        if (fwd > 0 && below > 0.35) {
            // pump along where you're already going (or where you face when barely moving)
            double hx = k.vx, hz = k.vz, h = Math.sqrt(hx * hx + hz * hz);
            if (h < 0.08) {
                hx = fx(yaw);
                hz = fz(yaw);
                h = 1;
            }
            double gain = TETHER_PUMP * fwd * Math.min(1, (below - 0.35) / 0.5);
            k.vx += hx / h * gain;
            k.vz += hz / h * gain;
        }
        if (right != 0) {
            inputDir(yaw, 0, right, scratch);
            k.vx += scratch[0] * TETHER_STEER;
            k.vz += scratch[1] * TETHER_STEER;
        }
        k.scaleV(1 - TETHER_DRAG);
        double out = k.vx * nx + k.vy * ny + k.vz * nz;
        if (r >= len - 1e-6 && out > 0) {
            // taut: the rope takes the outward part
            k.vx -= nx * out;
            k.vy -= ny * out;
            k.vz -= nz * out;
            k.capSpeed(TETHER_MAX_SPEED);
            double s = k.speed();
            if (s > 1e-9) {
                // along the great circle through the velocity, by the arc the speed covers
                double axx = (ny * k.vz - nz * k.vy) / s, axy = (nz * k.vx - nx * k.vz) / s, axz = (nx * k.vy - ny * k.vx) / s;
                double ang = s / len, c = Math.cos(ang), sn = Math.sin(ang);
                rotate(nx * len, ny * len, nz * len, axx, axy, axz, c, sn, scratch);
                k.x = ax + scratch[0];
                k.y = ay + scratch[1];
                k.z = az + scratch[2];
                rotate(k.vx, k.vy, k.vz, axx, axy, axz, c, sn, scratch);
                k.vx = scratch[0];
                k.vy = scratch[1];
                k.vz = scratch[2];
            } else {
                k.x = ax + nx * len;
                k.y = ay + ny * len;
                k.z = az + nz * len;
            }
            return out + s * s / len;
        }
        // slack: free flight, until the rope catches
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
        double dx = k.x - ax, dy = k.y - ay, dz = k.z - az;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > len) {
            nx = dx / d;
            ny = dy / d;
            nz = dz / d;
            k.x = ax + nx * len;
            k.y = ay + ny * len;
            k.z = az + nz * len;
            double o = k.vx * nx + k.vy * ny + k.vz * nz;
            if (o > 0) {
                k.vx -= nx * o;
                k.vy -= ny * o;
                k.vz -= nz * o;
                return o; // the catch: how hard the rope snapped taut
            }
        }
        return 0;
    }

    /** Rodrigues: (x, y, z) turned about the unit axis by the angle whose cosine and sine are given, into out[0..2]. */
    static void rotate(double x, double y, double z, double kx, double ky, double kz, double c, double s, double[] out) {
        double cx = ky * z - kz * y, cy = kz * x - kx * z, cz = kx * y - ky * x;
        double d = kx * x + ky * y + kz * z;
        out[0] = x * c + cx * s + kx * d * (1 - c);
        out[1] = y * c + cy * s + ky * d * (1 - c);
        out[2] = z * c + cz * s + kz * d * (1 - c);
    }

    /**
     * Reeling in: the rope shortens and the swing speeds up as its radius shrinks (angular momentum kept), up to the
     * rope's top speed. Returns the new length.
     */
    public static double reel(Kin k, double ax, double ay, double az, double len, double by) {
        double newLen = Math.max(TETHER_MIN, len - by);
        if (newLen >= len) return len;
        double rx = k.x - ax, ry = k.y - ay, rz = k.z - az;
        double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (r < len - 0.05 || r < 1e-6) return newLen; // slack: nothing to pull yet
        double ux = rx / r, uy = ry / r, uz = rz / r;
        double out = k.vx * ux + k.vy * uy + k.vz * uz;
        double tx = k.vx - ux * out, ty = k.vy - uy * out, tz = k.vz - uz * out;
        double f = Math.min(len / newLen, 1.15);
        k.vx = tx * f - ux * (len - newLen) * 0.6;
        k.vy = ty * f - uy * (len - newLen) * 0.6;
        k.vz = tz * f - uz * (len - newLen) * 0.6;
        k.capSpeed(TETHER_MAX_SPEED);
        return newLen;
    }

    /**
     * The release window: letting go while the hand rises in the front half of the arc (going up and away from under
     * the anchor) keeps a little extra speed. 0..1, how well the release was timed.
     */
    public static double releaseQuality(Kin k, double ax, double ay, double az) {
        double rx = k.x - ax, ry = k.y - ay, rz = k.z - az;
        double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double s = k.speed();
        if (r < 1e-6 || s < 0.3 || k.vy <= 0) return 0;
        // rising, and the angle from straight under: best around 35-70 degrees past the bottom
        double angle = Math.toDegrees(Math.acos(Mth.clamp(-ry / r, -1, 1)));
        double h = Math.sqrt(rx * rx + rz * rz);
        boolean ahead = h > 1e-6 && (rx * k.vx + rz * k.vz) > 0;
        if (!ahead) return 0;
        return Mth.clamp(1 - Math.abs(angle - 52) / 30, 0, 1);
    }

    /** One tick of a zip: pulled toward the anchor, faster each tick up to its top speed. True once it arrives. */
    public static boolean zipStep(Kin k, double ax, double ay, double az) {
        double dx = ax - k.x, dy = ay - k.y, dz = az - k.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < ZIP_ARRIVE) return true;
        double s = Math.min(ZIP_SPEED, k.speed() + ZIP_ACCEL);
        // the path bends toward the anchor rather than snapping to it
        double tx = dx / d * s, ty = dy / d * s, tz = dz / d * s;
        k.vx += (tx - k.vx) * 0.55;
        k.vy += (ty - k.vy) * 0.55;
        k.vz += (tz - k.vz) * 0.55;
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
        return false;
    }

    // ---- Aether (Skyborn's flight meter; SPEC §17.8) ------------------------------------------------------------------

    /**
     * Per tick, as a share of a full meter: cruising drains it in a minute, boosting in about 19 s, hovering in 38 s,
     * and each wingbeat takes 1.2%. Out of flight it fills in the sun in 20 s, in shade or at night in 90 s; an
     * updraft fills it in 6 s even while flying. Flight needs 8% to begin. Skyborn mastery trims the drain 3% a level.
     */
    public static final double AETHER_CRUISE = 1.0 / 1200, AETHER_BOOST = 3.2 / 1200, AETHER_HOVER = 1.6 / 1200, AETHER_FLAP = 0.012;
    public static final double AETHER_SUN = 1.0 / 400, AETHER_SHADE = 1.0 / 1800, AETHER_UPDRAFT = 1.0 / 120;
    public static final double AETHER_START = 0.08, MASTERY_AETHER = 0.03;

    /** One tick of the meter: the drain while flying (a rate above), the refill out of it or in an updraft. */
    public static double aetherStep(double a, boolean flying, double drain, boolean sun, boolean updraft, int skybornLevel) {
        if (flying) a -= drain * (1 - MASTERY_AETHER * skybornLevel);
        if (updraft) a += AETHER_UPDRAFT;
        else if (!flying) a += sun ? AETHER_SUN : AETHER_SHADE;
        return Math.min(1, a);
    }

    // ---- moving air (core.api.AirHooks) -------------------------------------------------------------------------------

    /** How much of the air each free state takes: the carry all of it, open wings more (they catch it), flight less. */
    public static final double AIR_CARRY = 1.0, AIR_GLIDE = 1.25, AIR_SOAR = 0.6;

    /** The air's push for one tick (blocks a tick², summed by AirHooks), scaled by the state's share. */
    public static void air(Kin k, double[] a, double share) {
        k.vx += a[0] * share;
        k.vy += a[1] * share;
        k.vz += a[2] * share;
    }

    // ---- carry ------------------------------------------------------------------------------------------------------

    /** One tick of flying free after letting go: gravity, light drag, and input that bends the path without adding speed. */
    public static void carryStep(Kin k, float yaw, double fwd, double right, double[] scratch) {
        carryStep(k, yaw, fwd, right, scratch, CARRY_DRAG_H);
    }

    /** The carry with its horizontal drag given (Flow eases it a little). */
    public static void carryStep(Kin k, float yaw, double fwd, double right, double[] scratch, double dragH) {
        if (fwd != 0 || right != 0) {
            double h = k.hspeed();
            inputDir(yaw, fwd, right, scratch);
            double il = Math.sqrt(scratch[0] * scratch[0] + scratch[1] * scratch[1]);
            if (il > 1e-6) {
                k.vx += scratch[0] / il * CARRY_STEER;
                k.vz += scratch[1] / il * CARRY_STEER;
                double cap = Math.max(h, CARRY_STEER_FLOOR), h2 = k.hspeed();
                if (h2 > cap) {
                    k.vx *= cap / h2;
                    k.vz *= cap / h2;
                }
            }
        }
        k.vy -= G;
        k.vx *= dragH;
        k.vz *= dragH;
        k.vy *= CARRY_DRAG_V;
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
    }

    // ---- glide ------------------------------------------------------------------------------------------------------

    /** Unfolding the wings mid-fall: most of the descent is caught, and turned toward where you look. */
    public static void glideCatch(Kin k, float yaw, float pitch) {
        double s = k.speed();
        double lx = fx(yaw) * Mth.cos(pitch * Mth.DEG_TO_RAD), lz = fz(yaw) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        if (k.vy < 0) {
            double caught = -k.vy * (1 - GLIDE_CATCH);
            k.vy *= GLIDE_CATCH;
            // half of what was caught goes forward: the fall becomes a swoop
            k.vx += lx * caught * 0.5;
            k.vz += lz * caught * 0.5;
        }
        if (k.speed() < 0.35 && s < 0.35) {
            k.vx += lx * 0.25;
            k.vz += lz * 0.25;
        }
    }

    /**
     * One tick of a glide. The path turns toward the look (quicker at speed); speed comes only from height: diving
     * along the path gains, climbing pays for itself, and drag takes the rest. Too slow and the path sags into a sink.
     */
    public static void glideStep(Kin k, float yaw, float pitch) {
        glideStep(k, yaw, pitch, 1);
    }

    /** The glide with its drag scaled (Wings mastery trims it). */
    public static void glideStep(Kin k, float yaw, float pitch, double dragScale) {
        double s = k.speed();
        double dx, dy, dz;
        double cp = Mth.cos(pitch * Mth.DEG_TO_RAD);
        double lx = fx(yaw) * cp, ly = -Mth.sin(pitch * Mth.DEG_TO_RAD), lz = fz(yaw) * cp;
        if (s > 1e-4) {
            dx = k.vx / s;
            dy = k.vy / s;
            dz = k.vz / s;
        } else {
            dx = lx;
            dy = ly;
            dz = lz;
        }
        double authority = Mth.clamp(s / 0.9, 0.25, 1.0);
        double t = GLIDE_TURN * authority;
        dx += (lx - dx) * t;
        dy += (ly - dy) * t;
        dz += (lz - dz) * t;
        if (s < GLIDE_STALL) {
            double sag = (GLIDE_STALL - s) / GLIDE_STALL * 0.35;
            dy += (-0.8 - dy) * sag;
        }
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl;
        dy /= dl;
        dz /= dl;
        s += -dy * G - GLIDE_DRAG * dragScale * s * s;
        if (s < 0.05) s = 0.05;
        k.vx = dx * s;
        k.vy = dy * s;
        k.vz = dz * s;
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
    }

    // ---- soar ---------------------------------------------------------------------------------------------------------

    /**
     * One tick of momentum flight. Holding forward thrusts along the look and the path turns after it (dives gain,
     * climbs pay, drag caps the speed); boosting thrusts harder. With no forward input you hover: speed bleeds away,
     * jump climbs and sneak sinks. Sideways input drifts.
     */
    public static void soarStep(Kin k, float yaw, float pitch, double fwd, double right, boolean up, boolean down, boolean boost,
                                double[] scratch) {
        double cp = Mth.cos(pitch * Mth.DEG_TO_RAD);
        double lx = fx(yaw) * cp, ly = -Mth.sin(pitch * Mth.DEG_TO_RAD), lz = fz(yaw) * cp;
        double s = k.speed();
        if (fwd > 0.1 || boost) {
            double dx, dy, dz;
            if (s > 0.15) {
                dx = k.vx / s;
                dy = k.vy / s;
                dz = k.vz / s;
            } else {
                dx = lx;
                dy = ly;
                dz = lz;
            }
            dx += (lx - dx) * SOAR_TURN;
            dy += (ly - dy) * SOAR_TURN;
            dz += (lz - dz) * SOAR_TURN;
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
            double thrust = boost ? SOAR_BOOST : SOAR_THRUST * Math.min(1, fwd);
            s += thrust - dy * G * 0.55 - SOAR_DRAG * s * s;
            s = Mth.clamp(s, 0, SOAR_MAX);
            k.vx = dx * s;
            k.vy = dy * s;
            k.vz = dz * s;
            if (up) k.vy += 0.03;
            if (down) k.vy -= 0.03;
        } else {
            k.vx *= SOAR_HOVER_DAMP;
            k.vz *= SOAR_HOVER_DAMP;
            double ty = up ? SOAR_CLIMB : down ? -SOAR_SINK : 0;
            k.vy += (ty - k.vy) * 0.18;
        }
        if (right != 0) {
            inputDir(yaw, 0, right, scratch);
            k.vx += scratch[0] * 0.025;
            k.vz += scratch[1] * 0.025;
        }
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
    }
}
