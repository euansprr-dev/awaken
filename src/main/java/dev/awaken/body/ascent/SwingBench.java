package dev.awaken.body.ascent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The swing bench (SPEC §17.31): six places as boxes (the sandbox lane, a city avenue, a forest, a cliff ahead, open
 * plains, a spire ahead), a body moved through them the way vanilla moves a player (a 0.6 by 1.8 box, axis by axis), and
 * a bot on each rope: the rope as it was before 28 Oct 2026 (the crosshair's block, the reel, the yank: AscentRules'
 * own functions) and the assisted swing ({@link AscentSwing}). Each place runs 40 seeded starts of 15 s per rope; the
 * GameTest {@code ascent_swing_bench} holds the assisted rope to the plan's gates. The same places and seeds as the
 * proof (docs/rope-swing/proof), so its numbers line up with the plan's page.
 */
public final class SwingBench {
    private SwingBench() {}

    public static final String[] PLACES = {"lane", "city", "forest", "cliff", "plains", "spire"};

    /** A hit at least this fast (blocks a tick of horizontal speed lost at once) counts; a hard one at 1. */
    public static final double HIT = 0.45, HARD = 1.0;

    // ---- the places -------------------------------------------------------------------------------------------------

    /** The proof's random (a 32-bit LCG), so the places and starts are the page's. */
    static final class Rng {
        private long s;

        Rng(long seed) {
            s = seed & 0xFFFFFFFFL;
        }

        double next() {
            s = (s * 1664525L + 1013904223L) & 0xFFFFFFFFL;
            return s / 4294967296.0;
        }
    }

    /** Where a place starts (feet), and whether it starts on a launch tower. */
    record Place(SwingSpace.Boxes boxes, double sx, double sy, double sz, boolean launch) {}

    static Place place(String key) {
        SwingSpace.Boxes b = new SwingSpace.Boxes();
        switch (key) {
            case "lane" -> {
                // AscentSandbox's lane: 14 giants 17 apart, 6-10 off the lane, alternating sides, 26-38 tall, a crown each
                Rng r = new Rng(7);
                b.add(-4.5, 0, -4.5, 5.5, 46, 5.5, false);
                for (int i = 0; i < 14; i++) {
                    double along = 18 + i * 17 + Math.floor(r.next() * 5) - 2, side = (i % 2 == 0 ? 1 : -1) * (6 + Math.floor(r.next() * 5));
                    double h = 26 + Math.floor(r.next() * 13);
                    b.add(side - 1, 0, along - 1, side + 2, h, along + 2, false);
                    b.add(side - 6, h - 3, along - 6, side + 7, h + 5, along + 7, true);
                }
                return new Place(b, 0.5, 46, 0.5, true);
            }
            case "city" -> {
                Rng r = new Rng(11);
                for (int side : new int[] {-1, 1}) {
                    double z = 8;
                    while (z < 230) {
                        double len = 14 + Math.floor(r.next() * 8), h = 22 + Math.floor(r.next() * 18);
                        if (side < 0) b.add(-26, 0, z, -8, h, z + len, false);
                        else b.add(8, 0, z, 26, h, z + len, false);
                        z += len + 5 + Math.floor(r.next() * 3);
                    }
                }
                b.add(-8, 15, 96, 8, 18, 100, false);
                b.add(-26, 0, 238, 26, 40, 256, false);
            }
            case "forest" -> {
                Rng r = new Rng(5);
                for (double z = 10; z < 240; z += 7 + Math.floor(r.next() * 5)) {
                    for (int k = 0; k < 2; k++) {
                        double x = (r.next() - 0.5) * 34, h = 13 + Math.floor(r.next() * 9);
                        if (Math.abs(x) < 1.5 && z < 14) continue;
                        double fx = Math.floor(x);
                        b.add(fx, 0, z, fx + 1, h, z + 1, false);
                        b.add(fx - 3, h - 2, z - 3, fx + 4, h + 3, z + 4, true);
                    }
                }
            }
            case "cliff" -> {
                b.add(-80, 0, 60, 80, 30, 200, false);
                b.add(-80, 25, 57, 80, 30, 60, false);
            }
            case "plains" -> {
                b.add(-3, 0, 70, 1, 3, 74, false);
                b.add(6, 0, 150, 9, 2, 153, false);
            }
            case "spire" -> b.add(-2, 0, 46, 2, 40, 50, false);
            default -> throw new IllegalArgumentException(key);
        }
        return new Place(b, 0.5, 0, 0.5, false);
    }

    // ---- the body ---------------------------------------------------------------------------------------------------

    /** The feet, velocity and ground of the body; {@link #move} is the collision. */
    static final class Body {
        double x, y, z, vx, vy, vz;
        boolean onGround;
    }

    /** Moves the body (dx, dy, dz) axis by axis in small steps; returns the horizontal speed a wall took. */
    static double move(SwingSpace s, Body b, double dx, double dy, double dz) {
        int steps = (int) Math.max(1, Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.12));
        boolean hx = false, hy = false, hz = false;
        for (int i = 0; i < steps; i++) {
            if (!hy) {
                double ny = b.y + dy / steps;
                if (s.bodyHits(b.x, ny, b.z)) hy = true;
                else b.y = ny;
            }
            if (!hx) {
                double nx = b.x + dx / steps;
                if (s.bodyHits(nx, b.y, b.z)) hx = true;
                else b.x = nx;
            }
            if (!hz) {
                double nz = b.z + dz / steps;
                if (s.bodyHits(b.x, b.y, nz)) hz = true;
                else b.z = nz;
            }
        }
        double before = Math.sqrt(b.vx * b.vx + b.vz * b.vz);
        if (hx) b.vx = 0;
        if (hz) b.vz = 0;
        b.onGround = hy && dy < 0;
        if (hy) b.vy = 0;
        return before - Math.sqrt(b.vx * b.vx + b.vz * b.vz);
    }

    // ---- the rope as it was (AscentAnchors.findAimed and AscentMoves' fire, over the boxes) ----------------------------

    static boolean todayAnchor(SwingSpace s, double ex, double ey, double ez, double yaw, double pitch, SwingSpace.Hit h, double[] out) {
        double cp = Math.cos(Math.toRadians(pitch));
        boolean fallback = false;
        double fbx = 0, fby = 0, fbz = 0;
        if (s.ray(ex, ey, ez, AscentSwing.fx(yaw) * cp, -Math.sin(Math.toRadians(pitch)), AscentSwing.fz(yaw) * cp, AscentRules.TETHER_RANGE, h)) {
            double rise = h.y - ey, dist = Math.sqrt((h.x - ex) * (h.x - ex) + rise * rise + (h.z - ez) * (h.z - ez));
            if (rise >= AscentAnchors.AIMED_RISE || rise < AscentRules.TETHER_SWING_MIN_RISE && dist >= AscentAnchors.ZIP_MIN) {
                out[0] = h.x;
                out[1] = h.y;
                out[2] = h.z;
                return true;
            }
            if (dist >= AscentAnchors.ZIP_MIN) {
                fallback = true;
                fbx = h.x;
                fby = h.y;
                fbz = h.z;
            }
        }
        double bs = 0;
        boolean any = false;
        for (double up : new double[] {20, 30, 40, 50, 62}) {
            for (double dy : new double[] {0, -14, 14, -28, 28}) {
                double c = Math.cos(Math.toRadians(-up));
                if (!s.ray(ex, ey, ez, AscentSwing.fx(yaw + dy) * c, Math.sin(Math.toRadians(up)), AscentSwing.fz(yaw + dy) * c, AscentRules.TETHER_RANGE, h)) continue;
                double dx = h.x - ex, ddy = h.y - ey, dz = h.z - ez, hh = Math.sqrt(dx * dx + dz * dz);
                if (ddy < 4 || hh < 3) continue;
                double fx = AscentSwing.fx(yaw), fz = AscentSwing.fz(yaw);
                double ahead = (dx * fx + dz * fz) / hh;
                if (ahead < 0.45) continue;
                double side = Math.abs(dx * -fz + dz * fx);
                double sideFit = 0.25 + 0.75 * clamp((side - 1.5) / 2, 0, 1) * (1 - clamp((side - 9) / 6, 0, 1));
                double sc = ahead * Math.exp(-sq((ddy - 14) / 9)) * Math.exp(-sq((hh - 13) / 9)) * sideFit;
                if (sc > bs) {
                    bs = sc;
                    out[0] = h.x;
                    out[1] = h.y;
                    out[2] = h.z;
                    any = true;
                }
            }
        }
        if (any) return true;
        if (fallback) {
            out[0] = fbx;
            out[1] = fby;
            out[2] = fbz;
        }
        return fallback;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double sq(double v) {
        return v * v;
    }

    // ---- a run ------------------------------------------------------------------------------------------------------

    /** One run's numbers: distance along the way, hits (and the worst), ropes, let-goes in the window, hits on the rope. */
    public static final class Run {
        public double distance, worst;
        public int ticks, hits, hardOnRope, hardFree, ropes, window;
        public boolean stuck;
    }

    /** One body on one rope in one place, stepped by the bot (the proof's Sim). */
    static final class Sim {
        final SwingSpace.Boxes w;
        final boolean assist;
        final Body b = new Body();
        final AscentRules.Kin k = new AscentRules.Kin();
        final AscentSwing.Rope rope = new AscentSwing.Rope();
        final AscentSwing.Work work = new AscentSwing.Work();
        final AscentSwing.Pick pick = new AscentSwing.Pick(), marker = new AscentSwing.Pick();
        final SwingSpace.Hit hit = new SwingSpace.Hit();
        final double[] s = new double[3], a = new double[3];
        final double z0;
        boolean onRope, zip, wasDown, markerOn;
        // the old rope's: its anchor, length and the length it reels to
        double tax, tay, taz, tlen, ttarget;
        int t, travel, held, ropeStart, cooldown, groundTicks, ground, missAt = -100, stuckTicks, pull, swingId;
        double lastZ, pullTop, pullNx, pullNz;
        final Run run = new Run();

        Sim(Place pl, boolean assist, double x0, double zOff, double speed) {
            this.w = pl.boxes();
            this.assist = assist;
            b.x = pl.sx() + x0;
            b.y = pl.sy();
            b.z = pl.sz() + zOff;
            b.vz = (pl.launch() ? 0.4 : 0.36) * speed;
            b.onGround = true;
            z0 = lastZ = b.z;
        }

        void hitBy(double lost) {
            if (lost <= HIT) return;
            run.hits++;
            run.worst = Math.max(run.worst, lost);
            if (lost >= HARD) {
                if (onRope) run.hardOnRope++;
                else run.hardFree++;
            }
        }

        boolean fire(double yaw, double pitch) {
            double hx = b.x, hy = b.y + AscentRules.HAND_HEIGHT, hz = b.z;
            k.set(hx, hy, hz, b.vx, b.vy, b.vz);
            if (!assist) {
                if (!todayAnchor(w, b.x, b.y + 1.62, b.z, yaw, pitch, hit, a)) {
                    missAt = t;
                    return false;
                }
                double dx = a[0] - hx, dy = a[1] - hy, dz = a[2] - hz, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                boolean overhead = dy > AscentRules.ZIP_UP_MIN_RISE && Math.sqrt(dx * dx + dz * dz) < dy * AscentRules.ZIP_UP_SLOPE;
                zip = !(dy > AscentRules.TETHER_SWING_MIN_RISE && !overhead);
                tax = a[0];
                tay = a[1];
                taz = a[2];
                tlen = ttarget = dist;
                if (!zip) {
                    double h = Math.sqrt(dx * dx + dz * dz), bx = h > 1e-3 ? a[0] - dx / h * 1.5 : a[0], bz = h > 1e-3 ? a[2] - dz / h * 1.5 : a[2];
                    double floor = Math.max(w.floorUnder(hx, hy, hz), w.floorUnder(bx, a[1], bz));
                    ttarget = clamp(Math.min(dist, a[1] - floor - AscentRules.TETHER_CLEARANCE - AscentRules.HAND_HEIGHT), AscentRules.TETHER_MIN, dist);
                }
                travel = (int) clamp(Math.ceil(dist / 16), 1, 3);
            } else {
                // the marker shows where a press would bite; the press itself looks again from here
                AscentSwing.Pick p = AscentSwing.find(w, k, yaw, AscentRules.TETHER_RANGE, work, pick) ? pick : markerOn && marker.zip ? marker : null;
                if (p != null && !p.zip) {
                    rope.set(p.x, p.y, p.z, p.len, p.fitLen);
                    zip = false;
                    travel = AscentSwing.travel(p.len);
                    AscentSwing.entry(k, yaw);
                } else {
                    if (p == null && !AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, pick)) return false;
                    AscentSwing.Pick z = p != null ? p : pick;
                    tax = z.x;
                    tay = z.y;
                    taz = z.z;
                    pullTop = z.top;
                    pullNx = z.nx;
                    pullNz = z.nz;
                    zip = true;
                    travel = 1;
                }
            }
            onRope = true;
            run.ropes++;
            ropeStart = t;
            return true;
        }

        void letGo() {
            b.vx = k.vx;
            b.vy = k.vy;
            b.vz = k.vz;
            onRope = false;
            cooldown = 2;
            groundTicks = 0;
        }

        /** One tick with the bot's keys. */
        void step(boolean ropeKey, double fwd, double yaw, double pitch, boolean jump) {
            boolean pressed = ropeKey && !wasDown;
            wasDown = ropeKey;
            if (cooldown > 0) cooldown--;
            if (!onRope) {
                if (b.onGround) {
                    double hx = AscentSwing.fx(yaw), hz = AscentSwing.fz(yaw);
                    b.vx = hx * 0.36 * Math.max(0.3, fwd);
                    b.vz = hz * 0.36 * Math.max(0.3, fwd);
                    if (jump) b.vy = 0.42;
                } else {
                    b.vy -= AscentRules.G;
                    b.vy *= AscentRules.CARRY_DRAG_V;
                    b.vx *= AscentRules.CARRY_DRAG_H;
                    b.vz *= AscentRules.CARRY_DRAG_H;
                    double sp = Math.sqrt(b.vx * b.vx + b.vz * b.vz);
                    if (sp > 0.05 && fwd > 0) {
                        b.vx += (AscentSwing.fx(yaw) * sp - b.vx) * 0.04;
                        b.vz += (AscentSwing.fz(yaw) * sp - b.vz) * 0.04;
                    }
                    if (pull > 0) {
                        pull--;
                        boolean over = b.y >= pullTop - 0.05;
                        b.vx = -pullNx * (over ? 0.3 : 0.04);
                        b.vz = -pullNz * (over ? 0.3 : 0.04);
                    }
                }
                double lost = move(w, b, b.vx, b.vy, b.vz);
                hitBy(lost);
                if (lost > 0 && b.y - w.floorUnder(b.x, b.y + 0.01, b.z) < 3) {
                    // a bot against a trunk or a boulder low down steps round it, as a player would
                    for (double sx : SIDESTEP) {
                        if (!w.bodyHits(b.x + sx, b.y, b.z + 0.5)) {
                            b.x += sx;
                            break;
                        }
                    }
                }
                if (assist && ropeKey && !b.onGround && pull == 0) {
                    // the rope held between swings: the free flight bends round what's ahead, or zips over a wall
                    k.set(b.x, b.y, b.z, b.vx, b.vy, b.vz);
                    if (AscentSwing.steerFree(w, k, yaw, false, work)) {
                        b.vx = k.vx;
                        b.vy = k.vy;
                        b.vz = k.vz;
                    } else if (work.blocked && AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, marker)) {
                        markerOn = true;
                        fire(yaw, pitch);
                        t++;
                        return;
                    } else if (work.blocked && AscentSwing.steerFree(w, k, yaw, true, work)) {
                        b.vx = k.vx;
                        b.vy = k.vy;
                        b.vz = k.vz;
                    }
                }
                if (assist && (t % 2 == 0 || !markerOn)) {
                    k.set(b.x, b.y + AscentRules.HAND_HEIGHT, b.z, b.vx, b.vy, b.vz);
                    markerOn = !(b.onGround && !ropeKey) && AscentSwing.find(w, k, yaw, AscentRules.TETHER_RANGE, work, marker);
                    if (!markerOn && AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, marker)) markerOn = true;
                }
                boolean wants = assist ? ropeKey : pressed;
                if (wants && cooldown == 0 && b.y > 0.2 && (!b.onGround || !assist)) {
                    if (!fire(yaw, pitch) && assist) cooldown = 2;
                }
            } else {
                // a let-go into something just ahead waits a moment (the rope holds on)
                boolean hold = !ropeKey && travel == 0 && assist && !zip && held < AscentSwing.T.holdOn && !AscentSwing.letGoClear(w, k, rope, yaw, work);
                held = hold ? held + 1 : 0;
                if (!ropeKey && travel == 0 && !hold) {
                    if (!assist) {
                        double q = zip ? 0 : AscentRules.releaseQuality(k, tax, tay, taz);
                        double f = 1 + (AscentRules.RELEASE_BOOST - 1) * q;
                        k.vx *= f;
                        k.vy *= f;
                        k.vz *= f;
                    } else if (!zip) {
                        if (AscentSwing.letGo(k, rope, yaw, work) > AscentSwing.T.winFrom) run.window++;
                    }
                    letGo();
                    t++;
                    return;
                }
                boolean go = false;
                if (travel > 0) {
                    travel--;
                    k.vy -= AscentRules.G;
                    k.vy *= AscentRules.CARRY_DRAG_V;
                    k.vx *= AscentRules.CARRY_DRAG_H;
                    k.vz *= AscentRules.CARRY_DRAG_H;
                    k.x += k.vx;
                    k.y += k.vy;
                    k.z += k.vz;
                    if (travel == 0 && !zip) {
                        if (assist) {
                            double r = Math.sqrt(sq(k.x - rope.ax) + sq(k.y - rope.ay) + sq(k.z - rope.az));
                            rope.len = Math.max(AscentRules.TETHER_MIN, Math.min(rope.len, r));
                        } else {
                            double r = Math.sqrt(sq(k.x - tax) + sq(k.y - tay) + sq(k.z - taz));
                            tlen = Math.max(AscentRules.TETHER_MIN, Math.min(tlen, r));
                            yank(yaw);
                        }
                    }
                } else if (zip) {
                    double dx = tax - k.x, dy = tay - k.y, dz = taz - k.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < AscentRules.ZIP_ARRIVE) {
                        go = true;
                        if (assist) {
                            // up and over the lip (the game's vault, simplified)
                            double rise = pullTop + 0.6 - (k.y - AscentRules.HAND_HEIGHT);
                            k.vy = Math.min(1.1, Math.sqrt(2 * AscentRules.G * Math.max(0.5, rise)) + 0.05);
                            k.vx *= 0.2;
                            k.vz = 0.02;
                            pull = 16;
                        } else {
                            k.vx *= 0.45;
                            k.vz *= 0.45;
                            k.vy = Math.max(k.vy * 0.3, 0.48);
                        }
                    } else {
                        AscentRules.zipStep(k, tax, tay, taz);
                    }
                } else if (!assist) {
                    if (tlen > ttarget + 1e-3) tlen = AscentRules.reel(k, tax, tay, taz, tlen, Math.min(AscentRules.TETHER_FIT_REEL, tlen - ttarget));
                    AscentRules.tetherStep(k, tax, tay, taz, tlen, (float) yaw, fwd, 0, s);
                } else {
                    int why = AscentSwing.tick(w, k, rope, yaw, fwd, work);
                    if (why == AscentSwing.WALL) {
                        if (AscentSwing.zipTo(w, k.x, k.y - AscentRules.HAND_HEIGHT + 1.62, k.z, yaw, pitch, AscentRules.TETHER_RANGE, work, pick)) {
                            // a wall nothing bends round: up it and over, on a new rope
                            run.ropes++;
                            tax = pick.x;
                            tay = pick.y;
                            taz = pick.z;
                            pullTop = pick.top;
                            pullNx = pick.nx;
                            pullNz = pick.nz;
                            zip = true;
                            travel = 1;
                            ropeStart = t;
                        } else {
                            AscentSwing.wallHop(k);
                            go = true;
                        }
                    } else if (why == AscentSwing.TOP) {
                        // the top of the arc lets go, unless that throws you into something: then a moment longer
                        go = held >= AscentSwing.T.holdOn || AscentSwing.letGoClear(w, k, rope, yaw, work);
                        held = go ? 0 : held + 1;
                    }
                }
                b.vx = k.vx;
                b.vy = k.vy;
                b.vz = k.vz;
                double lost = move(w, b, k.x - b.x, k.y - AscentRules.HAND_HEIGHT - b.y, k.z - b.z);
                hitBy(lost);
                k.x = b.x;
                k.y = b.y + AscentRules.HAND_HEIGHT;
                k.z = b.z;
                k.vx = b.vx;
                k.vz = b.vz;
                if (b.onGround) {
                    k.vy = Math.max(0, k.vy);
                    groundTicks++;
                } else groundTicks = 0;
                if (groundTicks >= AscentRules.TETHER_GROUND_TICKS || t - ropeStart > 140) go = true;
                if (go) letGo();
            }
            if (Math.abs(b.z - lastZ) < 0.02) stuckTicks++;
            else stuckTicks = 0;
            lastZ = b.z;
            t++;
        }

        /** The old rope's tug as it bites (AscentMoves.bite). */
        private void yank(double yaw) {
            double lx = AscentSwing.fx(yaw), lz = AscentSwing.fz(yaw), rx = k.x - tax, ry = k.y - tay, rz = k.z - taz;
            double r = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (r < 1e-3) return;
            double o = (lx * rx + lz * rz) / r, tx = lx - rx / r * o, ty = -ry / r * o, tz = lz - rz / r * o, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl > 1e-3) {
                k.vx += tx / tl * AscentRules.TETHER_YANK;
                k.vy += ty / tl * AscentRules.TETHER_YANK;
                k.vz += tz / tl * AscentRules.TETHER_YANK;
            }
        }

        private static final double[] SIDESTEP = {1.5, -1.5, 3, -3, 4.5, -4.5};
    }

    /**
     * The bot (the proof's): looks down the way, steering back to its middle, at {@code pitch}, holding forward, hopping
     * to start. On the old rope it presses when airborne and falling and lets go at its best window; on the assisted one
     * it holds the rope and lets go in the window.
     */
    static Run play(String place, boolean assist, double x0, double zOff, double pitch, double speed, int ticks) {
        Sim sim = new Sim(place(place), assist, x0, zOff, speed);
        Body b = sim.b;
        while (sim.t < ticks) {
            double yaw = clamp(b.x * 2.5, -15, 15);
            boolean jump = false, rope;
            if (b.onGround && !sim.onRope) {
                sim.ground++;
                jump = sim.t < 2 || sim.ground > 4;
                if (jump) sim.ground = 0;
            }
            if (!sim.onRope) {
                rope = assist ? !b.onGround : !b.onGround && b.vy < 0.2 && !sim.wasDown && sim.t - sim.missAt > 10;
            } else {
                rope = true;
                if (sim.travel == 0 && !sim.zip) {
                    double q = assist ? AscentSwing.windowQuality(sim.k, sim.rope, yaw + sim.rope.bend, sim.work)
                            : AscentRules.releaseQuality(sim.k, sim.tax, sim.tay, sim.taz);
                    if (q > 0.85) rope = false;
                }
            }
            sim.step(rope, 1, yaw, pitch, jump);
            if (sim.stuckTicks > 60) {
                sim.run.stuck = true;
                break;
            }
        }
        Run r = sim.run;
        r.ticks = sim.t;
        r.distance = b.z - sim.z0;
        return r;
    }

    /** A place's 40 runs on one rope, summed up. */
    public record Row(String place, boolean assist, double medianDistance, double hitsPerMinute, int hardRuns, int stuckRuns,
                      int hardOnRope, int hardFree, double ropesPerRun, double windowShare) {
        @Override
        public String toString() {
            return String.format("%-6s %-6s %6.1f blocks  %4.1f hits/min  hard %2d/40 (rope %d, free %d)  stopped %2d/40  ropes %.1f  window %.0f%%",
                    place, assist ? "assist" : "today", medianDistance, hitsPerMinute, hardRuns, hardOnRope, hardFree, stuckRuns, ropesPerRun, windowShare * 100);
        }
    }

    public static Row bench(String place, boolean assist) {
        Rng rnd = new Rng(99);
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double x0 = (rnd.next() - 0.5) * 8, zOff = rnd.next() * 6, pitch = -(10 + rnd.next() * 25), speed = 0.85 + rnd.next() * 0.4;
            runs.add(play(place, assist, x0, zOff, pitch, speed, 300));
        }
        double[] d = runs.stream().mapToDouble(r -> r.distance).toArray();
        Arrays.sort(d);
        int hits = 0, hard = 0, stuck = 0, ropes = 0, win = 0, onRope = 0, free = 0, ticks = 0;
        for (Run r : runs) {
            hits += r.hits;
            if (r.worst >= HARD) hard++;
            if (r.stuck) stuck++;
            ropes += r.ropes;
            win += r.window;
            onRope += r.hardOnRope;
            free += r.hardFree;
            ticks += r.ticks;
        }
        return new Row(place, assist, d[d.length / 2], hits / (ticks / 1200.0), hard, stuck, onRope, free,
                ropes / (double) runs.size(), ropes > 0 ? win / (double) ropes : 0);
    }

    /** {@code java ... dev.awaken.body.ascent.SwingBench}: every place on both ropes. */
    public static void main(String[] args) {
        for (String p : PLACES) {
            System.out.println(bench(p, false));
            System.out.println(bench(p, true));
        }
    }
}
