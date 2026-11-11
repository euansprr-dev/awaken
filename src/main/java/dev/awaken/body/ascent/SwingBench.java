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
 * <p>
 * Rope and Wings (SPEC §62) adds the places the world mostly is ({@link #WOODS}: an oak and birch wood and a spruce wood
 * at Minecraft's own tree sizes, rolling hills, a street of low roofs), where §17.31's rope never fired, and a second bot,
 * the one a player really is: it only <b>holds</b> the key ({@code hold}), never letting go by hand. The first bot, which
 * lets go in the window, is what §17.31's table measured.
 */
public final class SwingBench {
    private SwingBench() {}

    public static final String[] PLACES = {"lane", "city", "forest", "cliff", "plains", "spire"};
    /** Real-sized places (SPEC §62): trunks 4-7 under 5-wide crowns, spruces 5-8, hills 4-9 high, roofs 5-9 high. */
    public static final String[] WOODS = {"oak", "spruce", "hills", "roofs"};

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
            case "oak" -> {
                Rng r = new Rng(21);
                for (double z = 8; z < 320; z += 4 + Math.floor(r.next() * 4)) {
                    for (int k = 0; k < 2; k++) {
                        double x = (r.next() - 0.5) * 34, h = 4 + Math.floor(r.next() * 4);
                        if (Math.abs(x) < 1.5 && z < 14) continue;
                        double fx = Math.floor(x);
                        b.add(fx, 0, z, fx + 1, h, z + 1, false);
                        b.add(fx - 2, h - 2, z - 2, fx + 3, h + 2, z + 3, true);
                    }
                }
            }
            case "spruce" -> {
                Rng r = new Rng(23);
                for (double z = 8; z < 320; z += 4 + Math.floor(r.next() * 4)) {
                    for (int k = 0; k < 2; k++) {
                        double x = (r.next() - 0.5) * 34, h = 5 + Math.floor(r.next() * 4);
                        if (Math.abs(x) < 1.5 && z < 14) continue;
                        double fx = Math.floor(x);
                        b.add(fx, 0, z, fx + 1, h, z + 1, false);
                        b.add(fx - 2, 2, z - 2, fx + 3, h - 1, z + 3, true);
                        b.add(fx - 1, h - 1, z - 1, fx + 2, h + 2, z + 2, true);
                    }
                }
            }
            case "hills" -> {
                // stepped mounds either side of the way and across it: crests to sling over, slopes to land on
                Rng r = new Rng(27);
                for (double z = 40; z < 320; z += 30 + Math.floor(r.next() * 12)) {
                    double x = (r.next() - 0.5) * 30, h = 4 + Math.floor(r.next() * 6), w = 3 + Math.floor(r.next() * 3);
                    for (int k = 0; k < h; k++) {
                        double g = w + (h - k) * 1.5;
                        b.add(x - g, k, z - g, x + g, k + 1, z + g, false);
                    }
                }
            }
            case "roofs" -> {
                // a village street: houses 5-9 high either side of a 7-wide way, an arch across it
                Rng r = new Rng(29);
                for (int side : new int[] {-1, 1}) {
                    double z = 10;
                    while (z < 320) {
                        double len = 7 + Math.floor(r.next() * 6), h = 5 + Math.floor(r.next() * 5), deep = 6 + Math.floor(r.next() * 4);
                        if (side < 0) b.add(-3.5 - deep, 0, z, -3.5, h, z + len, false);
                        else b.add(3.5, 0, z, 3.5 + deep, h, z + len, false);
                        z += len + 2 + Math.floor(r.next() * 5);
                    }
                }
                b.add(-3.5, 5, 120, 3.5, 6, 122, false);
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
        double z0;
        boolean onRope, zip, sling, wasDown, markerOn;
        /** The sling's aim (a point over the top it bit) and its age. */
        double sx, sy, sz;
        int slingAge;
        // the old rope's: its anchor, length and the length it reels to
        double tax, tay, taz, tlen, ttarget;
        int t, travel, held, ropeStart = -100, cooldown, groundTicks, ground, missAt = -100, stuckTicks, pull, swingId;
        /** Ticks the bot has waited at a held rope's let-go point because letting go would throw it into something. */
        int waited;
        /** The window's quality last tick (the held bot lets go as it stops rising: the glow at its brightest). */
        double lastQ;
        /**
         * The bot that only holds forward and the rope, never jump (how the game is played with one thumb): the rope may
         * throw from the ground, by the game's own rule (AscentMoves: a press, or three ticks down at a run ten ticks
         * after the last throw), with the hop the game gives a throw from the ground, and never a pull up a face.
         */
        boolean keyOnly;
        private boolean groundThrow, blocked;
        int groundFor;
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
                AscentSwing.Pick p = AscentSwing.find(w, k, yaw, AscentRules.TETHER_RANGE, work, pick) ? pick : null;
                // nothing swings: a sling past a top ahead (SPEC §62), before any pull up a wall
                if (p == null && AscentSwing.guide && AscentSwing.findSling(w, k, yaw, AscentRules.TETHER_RANGE, work, pick)) {
                    sling = true;
                    zip = false;
                    sx = pick.px;
                    sy = pick.py;
                    sz = pick.pz;
                    slingAge = 0;
                    travel = 1;
                    onRope = true;
                    run.ropes++;
                    ropeStart = t;
                    return true;
                }
                sling = false;
                // a pull up a face only with a clear, short way to it (SPEC §62)
                if (p == null && !groundThrow && markerOn && marker.zip && (!AscentSwing.guide || marker.len <= AscentSwing.T.wallZip
                        && AscentSwing.zipClear(w, hx, hy, hz, marker))) p = marker;
                if (p != null && !p.zip) {
                    rope.set(p.x, p.y, p.z, p.len, p.fitLen);
                    zip = false;
                    travel = AscentSwing.travel(p.len);
                    AscentSwing.entry(k, yaw);
                } else {
                    if (p == null && (groundThrow || !AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, pick)
                            || AscentSwing.guide && (pick.len > AscentSwing.T.wallZip || !AscentSwing.zipClear(w, hx, hy, hz, pick)))) return false;
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
            waited = 0;
            lastQ = 0;
            work.ahead = -1;
        }

        /** One tick with the bot's keys. */
        void step(boolean ropeKey, double fwd, double yaw, double pitch, boolean jump) {
            boolean pressed = ropeKey && !wasDown;
            wasDown = ropeKey;
            if (cooldown > 0) cooldown--;
            if (!onRope) {
                if (b.onGround) {
                    double hx = AscentSwing.fx(yaw), hz = AscentSwing.fz(yaw);
                    // a sprint on the ground is 0.28; the hopping bots sprint-jump at 0.36
                    double run = keyOnly ? 0.28 : 0.36;
                    b.vx = hx * run * Math.max(0.3, fwd);
                    b.vz = hz * run * Math.max(0.3, fwd);
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
                blocked = lost > 0 && b.onGround;
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
                    } else if (work.blocked && AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, marker)
                            && (!AscentSwing.guide || marker.len <= AscentSwing.T.wallZip
                            && AscentSwing.zipClear(w, b.x, b.y + AscentRules.HAND_HEIGHT, b.z, marker))) {
                        markerOn = true;
                        fire(yaw, pitch);
                        t++;
                        return;
                    } else if (work.blocked && AscentSwing.steerFree(w, k, yaw, true, work)) {
                        b.vx = k.vx;
                        b.vy = k.vy;
                        b.vz = k.vz;
                    } else if (work.blocked && AscentSwing.guide) {
                        AscentSwing.brake(k);
                        b.vx = k.vx;
                        b.vz = k.vz;
                    }
                }
                if (assist && (t % 2 == 0 || !markerOn)) {
                    k.set(b.x, b.y + AscentRules.HAND_HEIGHT, b.z, b.vx, b.vy, b.vz);
                    markerOn = !(b.onGround && !ropeKey) && AscentSwing.find(w, k, yaw, AscentRules.TETHER_RANGE, work, marker);
                    if (!markerOn && AscentSwing.zipTo(w, b.x, b.y + 1.62, b.z, yaw, pitch, AscentRules.TETHER_RANGE, work, marker)) markerOn = true;
                }
                boolean wants = assist ? ropeKey : pressed;
                groundFor = b.onGround ? groundFor + 1 : 0;
                if (wants && cooldown == 0 && b.y > 0.2 && (!b.onGround || !assist)) {
                    if (!fire(yaw, pitch) && assist) cooldown = 2;
                } else if (keyOnly && assist && AscentSwing.guide && wants && cooldown == 0 && b.onGround
                        && (pressed || groundFor >= 3 && t - ropeStart >= 10 && b.vx * b.vx + b.vz * b.vz > 0.15 * 0.15)) {
                    double vy = b.vy;
                    b.vy = Math.max(b.vy, 0.5);
                    // running, only a swing or a sling; stopped against a face, the pull up it too (the game's rule)
                    groundThrow = !blocked && !pressed;
                    if (!fire(yaw, pitch)) {
                        b.vy = vy;
                        cooldown = 2;
                    }
                    groundThrow = false;
                }
            } else {
                // a let-go into something just ahead waits a moment (the rope holds on; the held rope never does)
                boolean hold = !ropeKey && travel == 0 && assist && !zip && !AscentSwing.hold && held < AscentSwing.T.holdOn
                        && !AscentSwing.letGoClear(w, k, rope, yaw, work);
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
                    if (travel == 0 && !zip && !sling) {
                        if (assist) {
                            double r = Math.sqrt(sq(k.x - rope.ax) + sq(k.y - rope.ay) + sq(k.z - rope.az));
                            rope.len = Math.max(AscentRules.TETHER_MIN, Math.min(rope.len, r));
                        } else {
                            double r = Math.sqrt(sq(k.x - tax) + sq(k.y - tay) + sq(k.z - taz));
                            tlen = Math.max(AscentRules.TETHER_MIN, Math.min(tlen, r));
                            yank(yaw);
                        }
                    }
                } else if (sling) {
                    if (AscentSwing.slingBlocked(w, k, sx, sy, sz, slingAge, work)) {
                        // something in the pull's way after all: let go early with a hop
                        AscentSwing.wallHop(k);
                        go = true;
                    } else if (AscentSwing.slingStep(k, sx, sy, sz, ++slingAge)) {
                        go = true;
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
                        if (AscentSwing.zipTo(w, k.x, k.y - AscentRules.HAND_HEIGHT + 1.62, k.z, yaw, pitch, AscentRules.TETHER_RANGE, work, pick)
                                && (!AscentSwing.guide || pick.len <= AscentSwing.T.wallZip && AscentSwing.zipClear(w, k.x, k.y, k.z, pick))) {
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
        return play(place, assist, false, x0, zOff, pitch, speed, ticks);
    }

    /**
     * {@code hold}: the bot never lets go by hand (the rope lets go by itself): how a player holding the key plays. With
     * the held rope ({@link AscentSwing#hold}) the rope never lets go by itself, so this bot lets go of the key where the
     * rope used to let go: as the window lights (where the guide's held rope let go), else at the top of the arc.
     */
    /**
     * How the holding bot lets go of a held rope: as the glow lights ({@link #LIT}), at its brightest (the window's best,
     * or as it starts to fade: {@link #BRIGHT}), or only at the top of the arc ({@link #TOP}). Always at the top too, and
     * before a wall. On 11 Nov the bench measured lit furthest (lane 227, city 253, forest 246; bright 217, 243, 223; top
     * 179, 206, 185): the early, flatter let-go carries further than the full boost at the window's best, so the gates'
     * bot lets go as the glow lights.
     */
    static final int LIT = 0, BRIGHT = 1, TOP = 2;
    static int heldRule = LIT;

    static Run play(String place, boolean assist, boolean hold, double x0, double zOff, double pitch, double speed, int ticks) {
        return play(place, assist, hold, false, x0, zOff, pitch, speed, ticks);
    }

    /**
     * {@code keyOnly}: forward and the rope held from the first tick, jump never pressed, starting on the ground past any
     * launch tower: the player Euan is (the rope on one key, jump on another, one thumb).
     */
    static Run play(String place, boolean assist, boolean hold, boolean keyOnly, double x0, double zOff, double pitch, double speed, int ticks) {
        Sim sim = new Sim(place(place), assist, x0, zOff, speed);
        Body b = sim.b;
        sim.keyOnly = keyOnly;
        if (keyOnly) {
            b.y = 0;
            b.z += 8;
            b.vz = 0.28;
            sim.z0 = b.z;
        }
        while (sim.t < ticks) {
            double yaw = clamp(b.x * 2.5, -15, 15);
            boolean jump = false, rope;
            if (b.onGround && !sim.onRope) {
                sim.ground++;
                // the key-only bot never presses jump; the game's own auto-jump (on by default) hops a block in its way
                jump = keyOnly ? sim.blocked : sim.t < 2 || sim.ground > 4;
                if (jump) sim.ground = 0;
            }
            if (!sim.onRope) {
                rope = keyOnly || (assist ? !b.onGround : !b.onGround && b.vy < 0.2 && !sim.wasDown && sim.t - sim.missAt > 10);
            } else {
                rope = true;
                if (sim.travel == 0 && !sim.zip && !sim.sling && assist && AscentSwing.hold) {
                    // the held rope lets go only by the key (SPEC §62): by hand at the window's best, holding as it lights,
                    // else at the top of the arc, or as a wall comes up ahead (a player sees it coming, before the rope
                    // brakes for it, where the rope used to let go by itself); a few ticks later if that would throw the
                    // bot into something just ahead (a player sees the crown coming), as the rope's own let-go waited before
                    double q = AscentSwing.windowQuality(sim.k, sim.rope, yaw + sim.rope.bend, sim.work);
                    boolean wall = sim.work.ahead >= 0 && sim.work.ahead < 5, lit = switch (heldRule) {
                        case LIT -> q > AscentSwing.T.winFrom + 0.01;
                        case TOP -> false;
                        default -> q > 0.97 || sim.lastQ > AscentSwing.T.winFrom && q < sim.lastQ;
                    };
                    sim.lastQ = q;
                    if ((hold ? lit : q > 0.85) || sim.rope.swung || wall) {
                        if (sim.waited >= AscentSwing.T.holdOn || AscentSwing.letGoClear(sim.w, sim.k, sim.rope, yaw, sim.work)) rope = false;
                        else sim.waited++;
                    }
                } else if (sim.travel == 0 && !sim.zip && !sim.sling && !(hold && assist)) {
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

    // ---- a climb up the sky stair (SPEC §17.9) -----------------------------------------------------------------------

    /** One climb: up (on the deck), the last stone reached of how many (the deck last), ticks, ropes and slings thrown,
     *  hard hits, landings, ticks off the ground, and how it ended. */
    public record Climb(boolean up, int reached, int of, int ticks, int ropes, int slings, int hard, int landings, int air, String end) {}

    /**
     * A climb up a stair to a deck (SPEC §17.9): {@code tops} bottom first, each {x, top, z, rad} (its top block at
     * {@code top}, feet on it at top + 1), the deck last ({x, deck, z, reach}: feet on it within reach of its middle);
     * {@code pieces} each one's boxes in the same order, {@code always} the boxes there the whole way (a chain down the
     * height). The space holds the pieces from three below the next stone to two above it (a rope reaches 44, a step is
     * some 22: the rest are out of reach), so a long stair stays cheap. The body starts on the ground at (sx, sz) running
     * at the first stone. The bot looks at the next stone's middle and holds forward; hopping on each landing (or, key
     * only, never jumping: the game's own throw from the ground), it holds the rope whenever it is off the ground and
     * lets go of a swing as {@link #play}'s holding bot does. It ends on the deck, when it falls well under the last
     * stone it reached, after 400 ticks with no stone gained, or after {@code maxTicks}.
     */
    public static Climb climb(List<double[]> tops, List<List<double[]>> pieces, List<double[]> always, double sx, double sz, double speed,
                              boolean keyOnly, int maxTicks) {
        SwingSpace.Boxes w = new SwingSpace.Boxes();
        int n = tops.size(), deck = n - 1, built = -99;
        Sim sim = new Sim(new Place(w, sx, 0, sz, false), true, 0, 0, speed);
        sim.keyOnly = keyOnly;
        Body b = sim.b;
        double[] first = tops.getFirst();
        double fd = Math.max(1e-6, Math.hypot(first[0] - sx, first[2] - sz)), run = (keyOnly ? 0.28 : 0.36) * speed;
        b.vx = (first[0] - sx) / fd * run;
        b.vz = (first[2] - sz) / fd * run;
        int reached = -1, lastGain = 0, landings = 0, air = 0, slings = 0;
        boolean wasGround = true, wasSling = false, up = false;
        String end = "out of time";
        StringBuilder tr = climbTrace;
        while (sim.t < maxTicks) {
            int target = Math.min(reached + 1, deck);
            if (target != built) {
                built = target;
                w.clear();
                for (double[] q : always) w.add(q[0], q[1], q[2], q[3], q[4], q[5], false);
                for (int i = Math.max(0, target - 3); i <= Math.min(deck, target + 2); i++) {
                    for (double[] q : pieces.get(i)) w.add(q[0], q[1], q[2], q[3], q[4], q[5], false);
                }
            }
            double[] tg = tops.get(target);
            double dx = tg[0] - b.x, dz = tg[2] - b.z, hd = Math.hypot(dx, dz);
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double pitch = -Math.toDegrees(Math.atan2(tg[1] + 1 - (b.y + 1.62), Math.max(0.5, hd)));
            boolean jump = false, rope;
            if (b.onGround && !sim.onRope) {
                sim.ground++;
                jump = keyOnly ? sim.blocked : sim.ground > 3;
                if (jump) sim.ground = 0;
            }
            if (!sim.onRope) rope = keyOnly || !b.onGround;
            else rope = !(sim.travel == 0 && !sim.zip && !sim.sling && AscentSwing.hold && heldLetGo(sim, yaw));
            boolean wasRope = sim.onRope;
            sim.step(rope, 1, yaw, pitch, jump);
            if (tr != null && tr.length() < 1500) {
                double top = tg[1];
                if (!wasRope && sim.onRope) {
                    double ay = sim.sling ? sim.sy : sim.zip ? sim.tay : sim.rope.ay;
                    tr.append(String.format(java.util.Locale.ROOT, " t%d>%d %s y%+.0f h%.0f a%+.0f", sim.t, target,
                            sim.sling ? "sling" : sim.zip ? "zip" : "swing", b.y - top, hd, ay - b.y));
                } else if (wasRope && !sim.onRope) {
                    tr.append(String.format(java.util.Locale.ROOT, " let y%+.0f v%.1f/%.1f", b.y - top, Math.hypot(b.vx, b.vz), b.vy));
                }
                if (b.onGround && !wasGround) {
                    int on = -1;
                    for (int i = 0; i < n; i++) {
                        double[] q = tops.get(i);
                        if (Math.hypot(b.x - q[0], b.z - q[2]) <= q[3] + 0.5 && Math.abs(b.y - q[1] - 1) < 1.5) on = i;
                    }
                    tr.append(on >= 0 ? " ON" + on : String.format(java.util.Locale.ROOT, " down y%.0f", b.y));
                }
            }
            if (sim.sling && !wasSling) slings++;
            wasSling = sim.sling;
            if (!b.onGround) air++;
            if (b.onGround && !wasGround && !sim.onRope) landings++;
            wasGround = b.onGround;
            for (int i = reached + 1; i <= Math.min(deck, reached + 3); i++) {
                double[] q = tops.get(i);
                double h = Math.hypot(b.x - q[0], b.z - q[2]);
                boolean on = i == deck ? b.onGround && h <= q[3] && b.y >= q[1] + 0.95
                        : h <= q[3] + 0.3 && b.y >= q[1] + 0.95 && b.y <= q[1] + 5;
                if (on) {
                    reached = i;
                    lastGain = sim.t;
                }
            }
            if (reached == deck) {
                up = true;
                end = "on the deck";
                break;
            }
            double floor = reached >= 0 ? tops.get(reached)[1] : 0;
            if (reached >= 0 && (b.y < floor - 14 || b.onGround && b.y < 0.5)) {
                end = "fell from stone " + reached;
                break;
            }
            if (sim.t - lastGain > 400) {
                end = "stuck under stone " + (reached + 1);
                break;
            }
        }
        if (tr != null) tr.append(" | ").append(end);
        return new Climb(up, reached, n, sim.t, sim.run.ropes, slings, sim.run.hardOnRope + sim.run.hardFree, landings, air, end);
    }

    /** When set, {@link #climb} writes each throw, let-go and landing into it (the stair bench's first climbs). */
    static StringBuilder climbTrace;

    /**
     * The holding bot's let-go of a held swing ({@link #play}'s rule, {@link #heldRule}): true when it lets go of the
     * key this tick (a let-go that would throw it into something waits up to {@link AscentSwing.Tune#holdOn} ticks).
     */
    static boolean heldLetGo(Sim sim, double yaw) {
        double q = AscentSwing.windowQuality(sim.k, sim.rope, yaw + sim.rope.bend, sim.work);
        boolean wall = sim.work.ahead >= 0 && sim.work.ahead < 5, lit = switch (heldRule) {
            case LIT -> q > AscentSwing.T.winFrom + 0.01;
            case TOP -> false;
            default -> q > 0.97 || sim.lastQ > AscentSwing.T.winFrom && q < sim.lastQ;
        };
        sim.lastQ = q;
        if (!(lit || sim.rope.swung || wall)) return false;
        if (sim.waited >= AscentSwing.T.holdOn || AscentSwing.letGoClear(sim.w, sim.k, sim.rope, yaw, sim.work)) return true;
        sim.waited++;
        return false;
    }

    /**
     * A held swing across a gap over nothing, as a dream's Run has them and its solver plays them (lucidium's
     * {@code AirProofs.swing}): roads one block thick 80 up, {@code len} of open air between them, a lantern {@code up}
     * over the near road and {@code ahead} along the gap, the far road {@code road} long; a sprint jump off the edge, the
     * rope thrown {@code throwAt} ticks into it and held until it lets go by itself. Returns how far past the gap's far
     * edge the feet come down to the roads' height (NaN if the rope finds nothing or never lets go). With
     * {@code groundUnder}, the same over a floor ten blocks down: there the guide's early let-go is free to throw far.
     * {@code atTop}: the key let go at the top of the arc instead (the held rope's, SPEC §62, never lets go by itself).
     */
    static double gapLanding(int len, int up, int ahead, int road, int throwAt, boolean groundUnder) {
        return gapLanding(len, up, ahead, road, throwAt, groundUnder, false);
    }

    static double gapLanding(int len, int up, int ahead, int road, int throwAt, boolean groundUnder, boolean atTop) {
        int y = 80, z = 100;
        SwingSpace.Boxes space = new SwingSpace.Boxes();
        space.add(-1, y - 1, z - 12, 2, y, z, false);
        space.add(-1, y - 1, z + len, 2, y, z + len + road, false);
        space.add(-1, y + up, z + ahead - 1, 2, y + up + 2, z + ahead + 2, false);
        if (groundUnder) space.add(-8, y - 11, z - 12, 9, y - 10, z + len + 80, false);
        // a sprint jump holding forward (vanilla's): up 0.42, along 0.40, the air's pull and drag
        double by = y, bz = z - 0.2, vy = 0.42, vz = 0.40;
        for (int t = 0; t < throwAt; t++) {
            vz += 0.026;
            bz += vz;
            by += vy;
            vy = (vy - AscentRules.G) * 0.98;
            vz *= 0.91;
        }
        AscentRules.Kin k = new AscentRules.Kin().set(0.5, by + AscentRules.HAND_HEIGHT, bz, 0, vy, vz);
        AscentSwing.Work w = new AscentSwing.Work();
        AscentSwing.Pick pick = new AscentSwing.Pick();
        if (!AscentSwing.find(space, k, 0, AscentRules.TETHER_RANGE, w, pick) || pick.zip) return Double.NaN;
        AscentSwing.Rope rope = new AscentSwing.Rope().set(pick.x, pick.y, pick.z, pick.len, pick.fitLen);
        AscentSwing.entry(k, 0);
        double[] sc = new double[3];
        for (int travel = AscentSwing.travel(pick.len); travel > 0; travel--) AscentRules.carryStep(k, 0f, 0.5, 0, sc);
        double d = Math.sqrt(sq(k.x - rope.ax) + sq(k.y - rope.ay) + sq(k.z - rope.az));
        rope.len = Math.max(AscentRules.TETHER_MIN, Math.min(rope.len, d));
        rope.fitLen = Math.min(rope.fitLen, rope.len);
        for (int i = 0, held = 0; ; i++) {
            if (i > 300) return Double.NaN;
            int how = AscentSwing.tick(space, k, rope, 0, 1, w);
            if (how == AscentSwing.WALL || w.braked) return Double.NaN;
            if (atTop && rope.swung) break;
            if (how == AscentSwing.TOP) {
                if (held >= AscentSwing.T.holdOn || AscentSwing.letGoClear(space, k, rope, 0, w)) break;
                held++;
            }
        }
        AscentSwing.letGo(k, rope, 0, w);
        k.y -= AscentRules.HAND_HEIGHT;
        for (int i = 0; i < 300; i++) {
            AscentRules.carryStep(k, 0f, 1, 0, sc);
            if (k.vy < 0 && k.y <= y) return k.z - (z + len);
        }
        return Double.NaN;
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
        return bench(place, assist, false);
    }

    /** {@code hold}: with the bot that only holds the key. */
    public static Row bench(String place, boolean assist, boolean hold) {
        return bench(place, assist, hold, false);
    }

    /** The key-only bot (forward and the rope held from the ground, never jump): {@link Sim#keyOnly}. */
    public static Row benchKey(String place) {
        return bench(place, true, true, true);
    }

    private static Row bench(String place, boolean assist, boolean hold, boolean keyOnly) {
        Rng rnd = new Rng(99);
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double x0 = (rnd.next() - 0.5) * 8, zOff = rnd.next() * 6, pitch = -(10 + rnd.next() * 25), speed = 0.85 + rnd.next() * 0.4;
            runs.add(play(place, assist, hold, keyOnly, x0, zOff, pitch, speed, 300));
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

    /**
     * {@code java ... dev.awaken.body.ascent.SwingBench [classic]}: every place on the old rope, on today's with a bot that
     * lets go by hand, and with one that only holds the key. {@code classic}: the guide off (the rope as §17.31 has it).
     */
    public static void main(String[] args) {
        AscentSwing.guide = args.length == 0 || !args[0].equals("classic");
        // -Dtune=slingPull=0.15,apexHeld=60: numbers to try, by their names in AscentSwing.Tune
        String tune = System.getProperty("tune");
        if (tune != null) {
            java.util.Map<String, Double> m = new java.util.HashMap<>();
            for (String kv : tune.split(",")) m.put(kv.substring(0, kv.indexOf('=')), Double.parseDouble(kv.substring(kv.indexOf('=') + 1)));
            AscentSwing.T = new AscentSwing.Tune(m);
        }
        for (String[] set : new String[][] {PLACES, WOODS}) {
            for (String p : set) {
                System.out.println(bench(p, false));
                System.out.println(bench(p, true));
                System.out.println("hold   " + bench(p, true, true));
                System.out.println("key    " + benchKey(p));
            }
        }
    }
}
