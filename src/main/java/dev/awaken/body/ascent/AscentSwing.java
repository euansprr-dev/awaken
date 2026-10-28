package dev.awaken.body.ascent;

import java.util.Map;

/**
 * The assisted swing (SPEC §17.31): where the rope bites, and how a swing runs, both sides of the same plain maths.
 * <p>
 * The rope looks for an anchor ahead of you and above, off to one side of your line, fits its length so your feet clear
 * the floor at the bottom, and plays the first part of the swing forward to be sure nothing is in its way. The body then
 * swings in the upright plane of your heading, about the anchor's point projected onto that plane: the rope still runs to
 * the real block (the server checks it), but a tower beside you holds the rope and you swing past it, never into it. The
 * swing keeps its speed (the catch keeps most of what a rope would take, the bottom of each arc drives you on), the rope
 * shortens to its fit slowly on the way down and never pulls you in, a wall in the next half second bends the swing round
 * it or lets go early, and the rope lets go at the top on its own, so you never swing back into the anchor.
 * <p>
 * Every number is in {@link Tune}; the jar's tune is {@link Tune#JAR}, and the client may swap in one read from a tuning
 * folder (RopeTuning). Nothing here allocates: the caller passes its own {@link Work}.
 */
public final class AscentSwing {
    private AscentSwing() {}

    private static final double D2R = Math.PI / 180;

    /** The numbers (SPEC §17.31's table). Built from a map so a tuning file can name any of them. */
    public static final class Tune {
        /** The search: rays from the hand, these many degrees up and either side of your heading. */
        public final double[] ups, yaws;
        /** A good swing: the rope this far above level, this long (radius in your plane), this far off your line. */
        public final double elevBest, elevSpread, elevMin, elevMax, lenBest, lenSpread, lenMin, sideBest, sideSpread, sideMax;
        /** The arc's lowest point keeps the feet this far over the floor; a side face this close under its top bites its edge. */
        public final double bottomClear, edgeSnap;
        /** The catch keeps this share of what the rope takes outward. */
        public final double catchKeep;
        /** The swing's own drive through the lower half, forward's pump on top, drag, top speed. */
        public final double drive, pump, drag, max;
        /** A swing starts at least this fast along the heading; slower than the floor speed, the drive is tripled. */
        public final double entryFloor, floorSpeed, floorDrive;
        /** Sideways speed turned into speed along the heading (the swing straightens). */
        public final double straighten;
        /** The rope shortens to its fit at most this a tick, and only on the way down. */
        public final double fit;
        /** The let-go window: rising, best this far past the bottom, good within the spread; a perfect one's boost and lift. */
        public final double winBest, winGood, winFrom, boost, lift;
        /** The rope lets go on its own past this angle from the bottom. */
        public final double apex;
        /** Ticks the swing looks ahead for walls, and the bends it tries round one. */
        public final int lookahead;
        public final double[] bends;
        /** Ticks a fired rope flies before it bites, a block of rope a tick (at least 1, at most 2). */
        public final double travelPer;
        /** A press let go within this many ticks is a tap (a ledge in the look zips you up to it, decision 2). */
        public final int tapTicks;
        /** Ticks the free flight between ropes looks ahead for something to steer round; a let-go into something waits this long at most. */
        public final int carryLook, holdOn;

        public Tune(Map<String, Double> o) {
            ups = new double[] {22, 32, 42, 52, 62, 72};
            yaws = new double[] {0, -12, 12, -24, 24, -36, 36, -50, 50, -66, 66};
            elevBest = v(o, "elevBest", 50);
            elevSpread = v(o, "elevSpread", 15);
            elevMin = v(o, "elevMin", 30);
            elevMax = v(o, "elevMax", 82);
            lenBest = v(o, "lenBest", 26);
            lenSpread = v(o, "lenSpread", 9);
            lenMin = v(o, "lenMin", 8);
            sideBest = v(o, "sideBest", 7);
            sideSpread = v(o, "sideSpread", 5);
            sideMax = v(o, "sideMax", 16);
            bottomClear = v(o, "bottomClear", 2.5);
            edgeSnap = v(o, "edgeSnap", 5);
            catchKeep = v(o, "catchKeep", 0.65);
            drive = v(o, "drive", 0.02);
            pump = v(o, "pump", 0.012);
            drag = v(o, "drag", 0.002);
            max = v(o, "max", 2.6);
            entryFloor = v(o, "entryFloor", 0.75);
            floorSpeed = v(o, "floorSpeed", 0.9);
            floorDrive = v(o, "floorDrive", 3);
            straighten = v(o, "straighten", 0.85);
            fit = v(o, "fit", 0.4);
            winBest = v(o, "winBest", 62);
            winGood = v(o, "winGood", 18);
            winFrom = v(o, "winFrom", 0.6);
            boost = v(o, "boost", 1.14);
            lift = v(o, "lift", 0.08);
            apex = v(o, "apex", 95);
            lookahead = (int) v(o, "lookahead", 10);
            bends = new double[] {8, -8, 16, -16, 24, -24};
            travelPer = v(o, "travelPer", 22);
            tapTicks = (int) v(o, "tapTicks", 5);
            carryLook = (int) v(o, "carryLook", 12);
            holdOn = (int) v(o, "holdOn", 10);
        }

        private static double v(Map<String, Double> o, String k, double d) {
            Double x = o.get(k);
            return x == null || !Double.isFinite(x) ? d : x;
        }

        public static final Tune JAR = new Tune(Map.of());
    }

    /** The tune in use (the jar's, or the client's tuning folder's). */
    public static volatile Tune T = Tune.JAR;

    /** A swing's rope: the real anchor, its length now and its fitted length, whether it has caught, its bend round a wall. */
    public static final class Rope {
        public double ax, ay, az, len, fitLen;
        public boolean caught;
        public double bend;

        public Rope set(double ax, double ay, double az, double len, double fitLen) {
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.len = len;
            this.fitLen = fitLen;
            caught = false;
            bend = 0;
            return this;
        }

        void copy(Rope o) {
            ax = o.ax;
            ay = o.ay;
            az = o.az;
            len = o.len;
            fitLen = o.fitLen;
            caught = o.caught;
            bend = o.bend;
        }
    }

    /** A chosen anchor: a swing (with its length and fit) or a zip to a lip (with the lip's top and the face it bit). */
    public static final class Pick {
        public double x, y, z, len, fitLen, score, elev, side, top;
        public int nx, nz;
        public boolean zip;

        public Pick copy(Pick o) {
            x = o.x;
            y = o.y;
            z = o.z;
            len = o.len;
            fitLen = o.fitLen;
            score = o.score;
            elev = o.elev;
            side = o.side;
            top = o.top;
            nx = o.nx;
            nz = o.nz;
            zip = o.zip;
            return this;
        }
    }

    /** The caller's scratch (one per thread that swings): nothing here is allocated per tick. */
    public static final class Work {
        final SwingSpace.Hit hit = new SwingSpace.Hit();
        final AscentRules.Kin k = new AscentRules.Kin(), k2 = new AscentRules.Kin();
        final Rope r = new Rope();
        final double[] pv = new double[4], s = new double[3];
        final Pick[] cands = new Pick[80];
        int n;
        /** The last tick bent the swing round something (for the trace); the carry's look found no way round. */
        public boolean bent, blocked;

        public Work() {
            for (int i = 0; i < cands.length; i++) cands[i] = new Pick();
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    public static double fx(double yaw) {
        return -Math.sin(yaw * D2R);
    }

    public static double fz(double yaw) {
        return Math.cos(yaw * D2R);
    }

    /**
     * The anchor's point projected onto the upright plane of the heading through the body: the swing's pivot. Writes
     * px, py, pz and the anchor's signed distance off that plane (+ to the left) into {@code pv}.
     */
    public static void pivot(double kx, double kz, double ax, double ay, double az, double heading, double[] pv) {
        double hx = fx(heading), hz = fz(heading), lx = -hz, lz = hx;
        double d = (ax - kx) * lx + (az - kz) * lz;
        pv[0] = ax - lx * d;
        pv[1] = ay;
        pv[2] = az - lz * d;
        pv[3] = d;
    }

    /** Degrees from straight under the point. */
    public static double angleFromBottom(AscentRules.Kin k, double px, double py, double pz) {
        double rx = k.x - px, ry = k.y - py, rz = k.z - pz, r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (r < 1e-9) return 0;
        return Math.acos(Math.max(-1, Math.min(1, -ry / r))) / D2R;
    }

    /** Whether the line from p to q is clear (leaves pass, the last {@code skipEnd} doesn't count). */
    public static boolean clear(SwingSpace s, double px, double py, double pz, double qx, double qy, double qz, double skipEnd, Work w) {
        double dx = qx - px, dy = qy - py, dz = qz - pz, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-6) return true;
        if (!s.ray(px, py, pz, dx / l, dy / l, dz / l, l, w.hit)) return true;
        return w.hit.t >= l - skipEnd || w.hit.leaf;
    }

    // ---- choosing the anchor --------------------------------------------------------------------------------------

    /** The swing an anchor point would give from the hand heading this way, into {@code out}; false when it's no swing. */
    static boolean plan(SwingSpace s, double hx, double hy, double hz, double heading, double reach, double x, double y, double z, Pick out) {
        Tune t = T;
        double dx = x - hx, dy = y - hy, dz = z - hz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dy < 4 || len > reach || len < t.lenMin) return false;
        double fx = fx(heading), fz = fz(heading);
        double ahead = dx * fx + dz * fz, side = Math.abs(dx * -fz + dz * fx);
        if (ahead < 2 || side > t.sideMax) return false;
        // the swing in your plane: the rope's angle over level there
        double elev = Math.atan2(dy, Math.max(0.01, ahead)) / D2R;
        if (elev < t.elevMin || elev > t.elevMax) return false;
        double px = hx + fx * ahead, pz = hz + fz * ahead;
        double floor = 0;
        for (double o : FLOOR_AT) floor = Math.max(floor, s.floorUnder(px + fx * o, y - 1.2, pz + fz * o));
        // the radius that keeps the feet clear of that floor at the bottom, as a rope to the real anchor
        double rad = Math.min(Math.sqrt(dy * dy + ahead * ahead), y - floor - t.bottomClear - AscentRules.HAND_HEIGHT);
        double fitLen = Math.min(len, Math.sqrt(rad * rad + side * side));
        if (rad < t.lenMin - 2 || fitLen < len * 0.7) return false;
        out.score = gauss(elev, t.elevBest, t.elevSpread) * gauss(rad, t.lenBest, t.lenSpread)
                * (0.45 + 0.55 * gauss(side, t.sideBest, t.sideSpread));
        out.x = x;
        out.y = y;
        out.z = z;
        out.len = len;
        out.fitLen = fitLen;
        out.elev = elev;
        out.side = side;
        out.zip = false;
        return true;
    }

    private static final double[] FLOOR_AT = {0, 1.5, -1.5, 3};

    private static double gauss(double v, double best, double spread) {
        double q = (v - best) / spread;
        return Math.exp(-q * q);
    }

    /** The swing's first ticks played forward from the hand: false if the body would meet something before its window. */
    static boolean arcClear(SwingSpace s, AscentRules.Kin from, Pick c, double heading, Work w) {
        AscentRules.Kin k = w.k;
        k.set(from.x, from.y, from.z, from.vx, from.vy, from.vz);
        w.r.set(c.x, c.y, c.z, c.len, c.fitLen);
        for (int i = 0; i < 40; i++) {
            double x = k.x, y = k.y, z = k.z;
            step(k, w.r, heading, 0, w);
            if (swept(s, x, y, z, k.x, k.y, k.z)) return false;
            if (windowQuality(k, w.r, heading, w) > 0.5) return true;
        }
        return true;
    }

    /**
     * Whether the body meets anything moving its hand from one point to the next: checked every 0.6 blocks of the way
     * (a fast swing can't step over a trunk between two ticks), the body a little wider than it is (a graze counts).
     */
    static boolean swept(SwingSpace s, double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = (int) Math.max(1, Math.ceil(d / 0.6));
        double half = SwingSpace.HALF + 0.12;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n, x = x0 + dx * f, y = y0 + dy * f - AscentRules.HAND_HEIGHT, z = z0 + dz * f;
            if (s.boxHits(x - half, y + 0.001, z - half, x + half, y + SwingSpace.TALL, z + half)) return true;
        }
        return false;
    }

    /**
     * The best anchor to swing from, from the hand point and velocity in {@code hand} heading this way (SPEC §17.31): a
     * fan of rays up and either side, each hit (a side face near its top bites the top's edge) planned as a swing,
     * the best few played forward until one is clear. False when nothing gives a swing.
     */
    public static boolean find(SwingSpace s, AscentRules.Kin hand, double heading, double reach, Work w, Pick out) {
        Tune t = T;
        SwingSpace.Hit h = w.hit;
        w.n = 0;
        for (double up : t.ups) {
            double cp = Math.cos(up * D2R), sp = Math.sin(up * D2R);
            for (double dyaw : t.yaws) {
                double yaw = heading + dyaw;
                if (!s.ray(hand.x, hand.y, hand.z, fx(yaw) * cp, sp, fz(yaw) * cp, reach, h)) continue;
                double x = h.x, y = h.y, z = h.z;
                if (h.ny == 0 && !h.leaf) {
                    double top = s.topOver(h, t.edgeSnap);
                    if (!Double.isNaN(top)) y = top - 0.3;
                }
                if (w.n >= w.cands.length) break;
                Pick c = w.cands[w.n];
                if (plan(s, hand.x, hand.y, hand.z, heading, reach, x, y, z, c) && clear(s, hand.x, hand.y, hand.z, x, y, z, 0.5, w)) w.n++;
            }
        }
        // the best four, best first (a partial selection sort: no list, no comparator)
        for (int i = 0; i < Math.min(4, w.n); i++) {
            int b = i;
            for (int j = i + 1; j < w.n; j++) if (w.cands[j].score > w.cands[b].score) b = j;
            Pick q = w.cands[i];
            w.cands[i] = w.cands[b];
            w.cands[b] = q;
            if (arcClear(s, hand, w.cands[i], heading, w)) {
                out.copy(w.cands[i]);
                return true;
            }
        }
        return false;
    }

    /**
     * When nothing swings: the look's own ray, climbed up the face it meets, to a lip with room over it (a ledge, a
     * cliff's edge, a roof) within reach: a zip up to it and over (SPEC §17.6's zip to a top). False when there's none.
     */
    public static boolean zipTo(SwingSpace s, double ex, double ey, double ez, double yaw, double pitch, double reach, Work w, Pick out) {
        SwingSpace.Hit h = w.hit;
        for (double p = Math.min(pitch, -5); p >= -72; p -= 6) {
            double cp = Math.cos(p * D2R);
            if (!s.ray(ex, ey, ez, fx(yaw) * cp, -Math.sin(p * D2R), fz(yaw) * cp, reach, h)) continue;
            if (h.leaf || h.ny != 0) continue;
            double top = s.topOver(h, 6);
            if (Double.isNaN(top) || top < ey + 2) continue;
            double hx = h.x, hy = h.y, hz = h.z;
            int nx = h.nx, nz = h.nz;
            // room over the top just past the lip
            double tx = hx - nx * 0.8, tz = hz - nz * 0.8;
            if (s.boxHits(tx - 0.3, top + 0.01, tz - 0.3, tx + 0.3, top + 2, tz + 0.3)) continue;
            if (!clear(s, ex, ey, ez, hx, top - 0.3, hz, 0.5, w)) continue;
            out.x = hx;
            out.y = top - 0.3;
            out.z = hz;
            out.top = top;
            out.nx = nx;
            out.nz = nz;
            out.zip = true;
            out.len = out.fitLen = Math.sqrt((hx - ex) * (hx - ex) + (top - 0.3 - ey) * (top - 0.3 - ey) + (hz - ez) * (hz - ez));
            return true;
        }
        return false;
    }

    // ---- the swing ------------------------------------------------------------------------------------------------

    /** Ticks a rope this long flies before it bites. */
    public static int travel(double len) {
        return (int) Math.max(1, Math.min(2, Math.ceil(len / T.travelPer)));
    }

    /** A swing never starts slower than a jog along the heading (no dead hang from a slow entry). */
    public static void entry(AscentRules.Kin k, double heading) {
        double hx = fx(heading), hz = fz(heading), along = k.vx * hx + k.vz * hz, floor = T.entryFloor;
        if (along < floor) {
            k.vx += hx * (floor - along);
            k.vz += hz * (floor - along);
        }
    }

    /**
     * One tick of a swing: the body (the hand point {@code k}) swings in the upright plane of the heading about the
     * anchor's projection on it (radius from the real rope: sqrt(len² - d²)); sideways speed is turned into speed along
     * the heading; through the lower half the swing drives you on (more when it is too slow to reach its window); the
     * rope shortens toward its fit on the way down, speed kept, never a pull in.
     */
    public static void step(AscentRules.Kin k, Rope rope, double heading, double fwd, Work w) {
        Tune t = T;
        double hx = fx(heading), hz = fz(heading), lx = -hz, lz = hx;
        // straighten: the sideways speed goes into speed along the heading (a turn of the look turns the swing)
        double sideV = k.vx * lx + k.vz * lz;
        k.vx -= lx * sideV;
        k.vz -= lz * sideV;
        double along = k.vx * hx + k.vz * hz;
        if (along >= 0) {
            k.vx += hx * Math.abs(sideV) * t.straighten;
            k.vz += hz * Math.abs(sideV) * t.straighten;
        }
        k.vy -= AscentRules.G;
        if (rope.len > rope.fitLen + 1e-3 && k.vy < 0) rope.len = Math.max(rope.fitLen, rope.len - t.fit);
        double[] pv = w.pv;
        pivot(k.x, k.z, rope.ax, rope.ay, rope.az, heading, pv);
        double px = pv[0], py = pv[1], pz = pv[2];
        double rad = Math.sqrt(Math.max(0.25, rope.len * rope.len - pv[3] * pv[3]));
        double rx = k.x - px, ry = k.y - py, rz = k.z - pz, r = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (r < 1e-6) {
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
            return;
        }
        double nx = rx / r, ny = ry / r, nz = rz / r, below = -ny;
        if (below > 0.2) {
            double o = hx * nx + hz * nz;
            double tx = hx - nx * o, ty = -ny * o, tz = hz - nz * o, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl > 1e-3) {
                double g = (t.drive + t.pump * Math.max(0, fwd)) * Math.min(1, (below - 0.2) / 0.5);
                // the momentum floor: a swing too slow to reach its window gets more drive (never a dead hang)
                if (k.speed() < t.floorSpeed && k.vx * hx + k.vz * hz >= 0) g *= t.floorDrive;
                k.vx += tx / tl * g;
                k.vy += ty / tl * g;
                k.vz += tz / tl * g;
            }
        }
        k.vx *= 1 - t.drag;
        k.vy *= 1 - t.drag;
        k.vz *= 1 - t.drag;
        double out = k.vx * nx + k.vy * ny + k.vz * nz;
        if (r >= rad - 1e-6 && out > 0) {
            double s0 = k.speed();
            k.vx -= nx * out;
            k.vy -= ny * out;
            k.vz -= nz * out;
            if (!rope.caught) {
                rope.caught = true;
                double s = k.speed(), keep = Math.min(s0, s + out * t.catchKeep);
                if (s > 1e-6) k.scaleV(keep / s);
            }
            k.capSpeed(t.max);
            circle(k, px, py, pz, rad, nx, ny, nz, w.s);
            return;
        }
        k.x += k.vx;
        k.y += k.vy;
        k.z += k.vz;
        if (catchOn(k, px, py, pz, rad, rope.caught ? 0 : t.catchKeep) > 0) rope.caught = true;
    }

    /** One taut step along the great circle through the velocity about (ax, ay, az) at radius len (as tetherStep's). */
    private static void circle(AscentRules.Kin k, double ax, double ay, double az, double len, double nx, double ny, double nz, double[] o) {
        double s = k.speed();
        if (s > 1e-9) {
            double axx = (ny * k.vz - nz * k.vy) / s, axy = (nz * k.vx - nx * k.vz) / s, axz = (nx * k.vy - ny * k.vx) / s;
            double al = Math.sqrt(axx * axx + axy * axy + axz * axz);
            if (al < 1e-9) al = 1;
            double ang = s / len, c = Math.cos(ang), sn = Math.sin(ang);
            AscentRules.rotate(nx * len, ny * len, nz * len, axx / al, axy / al, axz / al, c, sn, o);
            k.x = ax + o[0];
            k.y = ay + o[1];
            k.z = az + o[2];
            AscentRules.rotate(k.vx, k.vy, k.vz, axx / al, axy / al, axz / al, c, sn, o);
            k.vx = o[0];
            k.vy = o[1];
            k.vz = o[2];
        } else {
            k.x = ax + nx * len;
            k.y = ay + ny * len;
            k.z = az + nz * len;
        }
    }

    /** A slack rope going taut: back onto the sphere; {@code keep} of the outward speed lost turns into speed along. */
    private static double catchOn(AscentRules.Kin k, double ax, double ay, double az, double len, double keep) {
        double dx = k.x - ax, dy = k.y - ay, dz = k.z - az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= len) return 0;
        double ux = dx / d, uy = dy / d, uz = dz / d;
        k.x = ax + ux * len;
        k.y = ay + uy * len;
        k.z = az + uz * len;
        double o = k.vx * ux + k.vy * uy + k.vz * uz;
        if (o <= 0) return 0;
        k.vx -= ux * o;
        k.vy -= uy * o;
        k.vz -= uz * o;
        if (keep > 0) {
            double s = k.speed();
            if (s > 1e-6) k.scaleV((s + o * keep) / s);
        }
        return o;
    }

    /** How well a let-go now is timed, 0..1: rising and moving on, best {@link Tune#winBest} past the bottom. */
    public static double windowQuality(AscentRules.Kin k, Rope rope, double heading, Work w) {
        double[] pv = w.pv;
        pivot(k.x, k.z, rope.ax, rope.ay, rope.az, heading, pv);
        double rx = k.x - pv[0], rz = k.z - pv[2];
        if (k.vy <= 0 || rx * k.vx + rz * k.vz <= 0) return 0;
        double ang = angleFromBottom(k, pv[0], pv[1], pv[2]);
        return Math.max(0, 1 - Math.abs(ang - T.winBest) / T.winGood);
    }

    /** The tick within the look-ahead this swing would meet something, or -1. */
    public static int wallAhead(SwingSpace s, AscentRules.Kin from, Rope rope, double heading, Work w) {
        AscentRules.Kin k = w.k;
        k.set(from.x, from.y, from.z, from.vx, from.vy, from.vz);
        w.r.copy(rope);
        w.r.caught = true;
        for (int i = 0; i < T.lookahead; i++) {
            double x = k.x, y = k.y, z = k.z;
            step(k, w.r, heading, 0, w);
            if (swept(s, x, y, z, k.x, k.y, k.z)) return i;
        }
        return -1;
    }

    // ---- flying free between ropes ----------------------------------------------------------------------------------

    /**
     * The carry played forward from the feet (gravity and its drag, no input): the tick it would meet something within
     * {@link Tune#carryLook}, or -1.
     */
    public static int carryAhead(SwingSpace s, AscentRules.Kin from, Work w) {
        AscentRules.Kin k = w.k;
        k.set(from.x, from.y, from.z, from.vx, from.vy, from.vz);
        for (int i = 0; i < T.carryLook; i++) {
            double x = k.x, y = k.y, z = k.z;
            k.vy = (k.vy - AscentRules.G) * AscentRules.CARRY_DRAG_V;
            k.vx *= AscentRules.CARRY_DRAG_H;
            k.vz *= AscentRules.CARRY_DRAG_H;
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
            // the feet (swept() takes a hand point); coming down onto a floor is a landing, not a hit
            if (swept(s, x, y + AscentRules.HAND_HEIGHT, z, k.x, k.y + AscentRules.HAND_HEIGHT, k.z)) {
                return k.vy < 0 && !swept(s, x, y + AscentRules.HAND_HEIGHT + 0.05, z, k.x, y + AscentRules.HAND_HEIGHT + 0.05, k.z) ? -1 : i;
            }
        }
        return -1;
    }

    // sideways and down (a bend round it), then the harder ones; HOP: up over a lip just over the head
    private static final double[][] NUDGE = {{0.12, 0}, {-0.12, 0}, {0, -0.14}, {0.24, 0}, {-0.24, 0}, {0.12, -0.14}, {-0.12, -0.14},
            {0, 0.14}, {0.36, 0}, {-0.36, 0}, {0, -0.3}, {0, -0.5}, {0.36, -0.3}, {-0.36, -0.3}, {0, -0.8}, {0.36, -0.6}, {-0.36, -0.6},
            {0, -1.1}};
    private static final double[][] HOP = {{0, 0.3}, {0, 0.45}};

    /**
     * Flying free with the rope held (the next swing coming): something in the way within the look gets the path bent
     * round it, a little sideways or down, the speed kept ({@code hop}: a hop up over a lip instead, tried once a zip over
     * it isn't there). True when it bent the path; false when it was clear, or nothing small enough helps.
     */
    public static boolean steerFree(SwingSpace s, AscentRules.Kin k, double yaw, boolean hop, Work w) {
        int hit = carryAhead(s, k, w);
        w.blocked = hit >= 0;
        if (hit < 0) return false;
        double lx = -fz(yaw), lz = fx(yaw), h = k.hspeed();
        for (double[] n : hop ? HOP : NUDGE) {
            double vx = k.vx, vy = k.vy, vz = k.vz;
            k.vx += lx * n[0];
            k.vz += lz * n[0];
            k.vy += n[1];
            // the speed along stays what it was: the bend turns it
            double h2 = k.hspeed();
            if (h2 > 1e-6) {
                k.vx *= h / h2;
                k.vz *= h / h2;
            }
            if (carryAhead(s, k, w) < 0) {
                w.blocked = false;
                return true;
            }
            k.vx = vx;
            k.vy = vy;
            k.vz = vz;
        }
        return false;
    }

    /** What a swing tick asks for: go on, let go at the top, or let go before a wall it can't bend round. */
    public static final int GO = 0, TOP = 1, WALL = 2;

    /**
     * One whole swing tick: the step, then the look ahead (a wall in the way bends the swing a few degrees either side,
     * the bend kept), then whether the rope should let go: at the top of the arc (past the pivot along the heading with
     * forward speed spent, or past {@link Tune#apex}), or before a wall less than 4 ticks off that nothing bends round.
     */
    public static int tick(SwingSpace s, AscentRules.Kin k, Rope rope, double yaw, double fwd, Work w) {
        Tune t = T;
        double hd = yaw + rope.bend;
        step(k, rope, hd, fwd, w);
        int ahead = wallAhead(s, k, rope, hd, w);
        w.bent = false;
        if (ahead >= 0) {
            for (double b : t.bends) {
                if (wallAhead(s, k, rope, yaw + b, w) < 0) {
                    rope.bend = b;
                    ahead = -1;
                    w.bent = true;
                    break;
                }
            }
        }
        double[] pv = w.pv;
        pivot(k.x, k.z, rope.ax, rope.ay, rope.az, hd, pv);
        double ang = angleFromBottom(k, pv[0], pv[1], pv[2]);
        boolean past = (k.x - pv[0]) * fx(hd) + (k.z - pv[2]) * fz(hd) > 0;
        double fwdSpeed = k.vx * fx(hd) + k.vz * fz(hd);
        if (ahead >= 0 && ahead < 4) return WALL;
        if (past && (ang > t.apex || fwdSpeed < 0.15 || k.vy < -0.02 && ang > 8)) return TOP;
        return GO;
    }

    /** Letting go before a wall: a hop up and away from it. */
    public static void wallHop(AscentRules.Kin k) {
        k.vy = Math.max(k.vy, 0) + 0.3;
    }

    /**
     * Whether letting go now flies clear: the let-go played (its boost too) and the free flight looked ahead a few ticks,
     * with the bends it may take. False when it would meet something: the rope holds a moment longer (up to
     * {@link Tune#holdOn} ticks), so a let-go never throws you into the crown or wall just ahead.
     */
    public static boolean letGoClear(SwingSpace s, AscentRules.Kin hand, Rope rope, double yaw, Work w) {
        AscentRules.Kin k = w.k2;
        k.set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
        letGo(k, rope, yaw, w);
        k.y -= AscentRules.HAND_HEIGHT;
        int look = T.carryLook;
        // a short look: only what the free flight can't bend round in time
        int hit = carryAhead(s, k, w);
        if (hit < 0 || hit >= look / 2) return true;
        return steerFree(s, k, yaw, false, w);
    }

    /** Letting go: a let-go in the window keeps its boost (speed along and a lift). Returns the window's quality. */
    public static double letGo(AscentRules.Kin k, Rope rope, double yaw, Work w) {
        Tune t = T;
        double q = windowQuality(k, rope, yaw + rope.bend, w);
        if (q > t.winFrom) {
            double f = Math.min(1, (q - t.winFrom) / 0.3);
            k.vx *= 1 + (t.boost - 1) * f;
            k.vz *= 1 + (t.boost - 1) * f;
            k.vy += t.lift * f;
        }
        return q;
    }
}
