package dev.awaken.body.ascent;

/**
 * The real swing (SPEC §62.9, 12 Nov 2026; Euan: "90% of the time, it's just something that brings you to the point that
 * you aimed at instead of actually swinging you from it"). A real rope in three dimensions about the block it bit: its
 * length is the way to the anchor when it bites, it only ever pulls, and nothing winds you in. The help that made the
 * old rope pull you (a length fitted to the floor, wound in a block a tick) moves to before the bite, where you can't feel
 * it: the swing a throw would give is played forward from your own speed for a handful of points near what you look at,
 * and the press takes the one that swings you under it, else round it (a ball whirled on a string: a 7-high oak beside
 * you holds you up by your speed alone), else, at something tall from low and far, a short hoist up its near side to
 * where a swing from it starts. Four gentle hands stay on the rope: the bite turns what goes in toward the anchor along
 * the way you already go round it (taut from the first tick), the rope eases in a little while you fall (at most 15%,
 * never faster than {@link AscentSwing.Tune#ease} a tick) and the feet skim a block over the floor, forward steers toward
 * your look and pumps the rope, and an arc whose far side has no room loses speed before it gets there.
 * <p>
 * {@link AscentSwing#real} switches it (the client's {@code ropeReal}); off, every rope is the assisted swing exactly as
 * it was. Nothing here allocates: the caller's {@link AscentSwing.Work} holds the scratch.
 */
public final class RealRope {
    private RealRope() {}

    private static final double D2R = Math.PI / 180;

    /** What a real pick does: swings you under its anchor, round it, or hoists you up to a swing first. */
    public static final int UNDER = 1, ROUND = 2, HOIST = 3;
    /** The words for each, for the log and the marker's tip. */
    public static final String[] MOVE = {"a swing", "a swing under", "a swing round", "a hoist into a swing"};

    // ---- the rope ------------------------------------------------------------------------------------------------------

    /** Degrees from straight under the anchor. */
    public static double angle(AscentRules.Kin k, AscentSwing.Rope rope) {
        double dx = k.x - rope.ax, dy = k.y - rope.ay, dz = k.z - rope.az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return d < 1e-9 ? 0 : Math.acos(Math.max(-1, Math.min(1, -dy / d))) / D2R;
    }

    /**
     * The bite (the rope has flown out): its length is the way to the anchor now. What goes in toward the anchor is turned
     * along the way you already go round it ({@link AscentSwing.Tune#biteKeep} of it kept), so the rope is taut from the
     * first tick: going past something, round it; dropping, down the arc (with almost nothing across, the fall itself is
     * turned down the arc). Its least length (the ease) keeps the feet a skim over the floor under the anchor, never under
     * {@link AscentSwing.Tune#easeLeast} of the bite. A hoist's rope is the swing's length and starts hauling instead.
     */
    public static void bite(SwingSpace s, AscentRules.Kin k, AscentSwing.Rope rope, double yaw) {
        AscentSwing.Tune t = AscentSwing.T;
        if (rope.hoist) {
            rope.len = rope.base = rope.fit = rope.hoistLen;
            rope.caught = false;
            rope.hoistAge = 0;
            return;
        }
        double dx = k.x - rope.ax, dy = k.y - rope.ay, dz = k.z - rope.az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        rope.len = rope.base = Math.max(LEAST, d);
        if (d > 1e-6) {
            double ux = dx / d, uy = dy / d, uz = dz / d, o = k.vx * ux + k.vy * uy + k.vz * uz;
            if (o < 0) {
                k.vx -= ux * o;
                k.vy -= uy * o;
                k.vz -= uz * o;
                double tl = k.speed();
                if (tl > 0.15) {
                    k.scaleV((tl - o * t.biteKeep) / tl);
                } else {
                    double gx = ux * uy, gy = -1 + uy * uy, gz = uz * uy, gl = Math.sqrt(gx * gx + gy * gy + gz * gz);
                    if (gl > 1e-6) {
                        double f = -o * t.biteKeep / gl;
                        k.vx += gx * f;
                        k.vy += gy * f;
                        k.vz += gz * f;
                    }
                }
            }
        }
        rope.caught = true;
        double floor = -1e9, fx = AscentSwing.fx(yaw), fz = AscentSwing.fz(yaw);
        for (double o : FIT_AT) floor = Math.max(floor, s.floorUnder(rope.ax - fx * o, rope.ay - 1.2, rope.az - fz * o));
        rope.fit = Math.max(rope.len * t.easeLeast, Math.min(rope.len, rope.ay - floor - t.skim - AscentRules.HAND_HEIGHT));
    }

    /** The floor under a rope's anchor is looked for here along the look (blocks behind and ahead of it). */
    private static final double[] FIT_AT = {-1.5, 0, 1.5, 3};
    /** The shortest rope (blocks): an anchor nearer than this bites at this length and catches you on the way out. */
    static final double LEAST = 1.5;

    /**
     * One tick of the real rope on the hand point {@code k} looking along {@code yaw}, {@code fwd} the forward key
     * (-1..1). Forward steers the speed across toward the look (only within {@link AscentSwing.Tune#steerWithin} of the
     * way you go: swinging back, the look doesn't turn it) and pumps the rope (drawn in through the bottom, paid back out
     * past 45 degrees); back slows the swing to a hang. The first arc keeps the old swing's drive along its lower half.
     * The rope eases in only while you fall; a rope going taut takes only what goes out along it; the feet skim the floor
     * with the speed turned along the way you go; no arc goes over the rope's {@link AscentSwing.Rope#cap}.
     */
    public static void step(SwingSpace s, AscentRules.Kin k, AscentSwing.Rope rope, double yaw, double fwd) {
        AscentSwing.Tune t = AscentSwing.T;
        double lfx = AscentSwing.fx(yaw), lfz = AscentSwing.fz(yaw);
        if (fwd > 0) {
            double h = k.hspeed();
            if (h > 0.05) {
                double want = Math.atan2(lfx, lfz), have = Math.atan2(k.vx, k.vz), d = want - have;
                d = Math.IEEEremainder(d, 2 * Math.PI);
                if (Math.abs(d) <= t.steerWithin * D2R) {
                    double m = Math.max(-t.steer * D2R, Math.min(t.steer * D2R, d)), a = have + m;
                    k.vx = Math.sin(a) * h;
                    k.vz = Math.cos(a) * h;
                }
            }
        }
        k.vy -= AscentRules.G;
        double dx = k.x - rope.ax, dy = k.y - rope.ay, dz = k.z - rope.az, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double below = d > 1e-6 ? -dy / d : 0;
        // the first arc's drive (the old swing's, kept so the pace stays): along the motion square to the rope
        if (rope.caught && !rope.swung && below > 0.2) {
            double sp = k.speed();
            if (sp > 1e-6) {
                double f = Math.min(1, (below - 0.2) / 0.5), g = (t.drive + t.pump * Math.max(0, fwd)) * f;
                if (sp < t.floorSpeed) g *= t.floorDrive;
                double ux = dx / d, uy = dy / d, uz = dz / d, o = k.vx * ux + k.vy * uy + k.vz * uz;
                double tx = k.vx - ux * o, ty = k.vy - uy * o, tz = k.vz - uz * o, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl > 1e-6) {
                    k.vx += tx / tl * g;
                    k.vy += ty / tl * g;
                    k.vz += tz / tl * g;
                }
            }
        }
        // back slows it toward a hang (a held rope's own brake)
        if (fwd < 0 && rope.caught) k.scaleV(1 - BACK * -fwd);
        // forward's pump: in through the bottom (where the pull is greatest: speed gained), back out past 45 degrees
        double ang = d > 1e-9 ? Math.acos(Math.max(-1, Math.min(1, below))) / D2R : 0;
        if (fwd > 0 && ang < 30 && rope.len > rope.base - t.pumpMax) {
            rope.len = Math.max(rope.base - t.pumpMax, rope.len - t.pumpIn);
        } else if (ang > 45 && rope.len < rope.base) {
            rope.len = Math.min(rope.base, rope.len + t.pumpIn);
        }
        // the ease: a rope a little long for the floor under it shortens only while falling, never a pull in
        if (k.vy < 0 && rope.len > rope.fit + 1e-3) {
            rope.len = Math.max(rope.fit, rope.len - t.ease);
            rope.base = rope.len;
        }
        k.scaleV(1 - t.drag);
        // the far side's room: rising past the bottom, the arc never goes over its cap
        if (k.vy > 0 && d > rope.len - 0.05) {
            double cap2 = 2 * AscentRules.G * rope.len * Math.max(0, below - Math.cos(rope.cap * D2R)), sp = k.speed();
            if (sp * sp > cap2 && sp > 1e-6) {
                double vc = Math.sqrt(cap2);
                k.scaleV((vc + (sp - vc) * 0.5) / sp);
            }
        }
        double nx = k.x + k.vx, ny = k.y + k.vy, nz = k.z + k.vz;
        dx = nx - rope.ax;
        dy = ny - rope.ay;
        dz = nz - rope.az;
        d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > rope.len) {
            // taut: back onto the sphere, only what goes out along the rope taken (a first catch keeps a share as speed)
            double ux = dx / d, uy = dy / d, uz = dz / d;
            nx = rope.ax + ux * rope.len;
            ny = rope.ay + uy * rope.len;
            nz = rope.az + uz * rope.len;
            double o = k.vx * ux + k.vy * uy + k.vz * uz;
            if (o > 0) {
                k.vx -= ux * o;
                k.vy -= uy * o;
                k.vz -= uz * o;
                if (!rope.caught) {
                    double sp = k.speed();
                    if (sp > 1e-6) k.scaleV((sp + o * t.catchKeep) / sp);
                }
            }
            rope.caught = true;
            k.capSpeed(t.max);
        }
        // the skim: the feet never under a block over the floor, the speed down turned along the way you go
        if (k.vy < 0) {
            double lowest = s.floorUnder(nx, ny - AscentRules.HAND_HEIGHT + 0.6, nz) + t.skim + AscentRules.HAND_HEIGHT;
            if (ny < lowest) {
                double sp = k.speed(), h;
                ny = lowest;
                k.vy = 0;
                h = k.hspeed();
                if (h > 1e-6) {
                    k.vx *= sp / h;
                    k.vz *= sp / h;
                }
                rope.skimming++;
            }
        }
        // the first arc has topped out past the anchor (its drive ends; a rope not held asks to let go here)
        if (rope.caught && !rope.swung && k.vy <= 0 && angle(k, rope) > 20 && (k.x - rope.ax) * lfx + (k.z - rope.az) * lfz > 0) rope.swung = true;
        k.x = nx;
        k.y = ny;
        k.z = nz;
    }

    /** Back's brake on a held real rope: this share of the speed a tick at full back. */
    static final double BACK = 0.025;

    /**
     * The hoist (SPEC §62.9): the body drawn up to where the swing starts, its speed toward it at most
     * {@link AscentSwing.Tune#hoistTop} and slowing to arrive (a jump's top, never a crash), gravity carried by the rope.
     * There (or after {@link #HOIST_MOST} ticks, or with something in the way), the rope is the swing's, taut, and what
     * still goes in along it is spent. True on the tick it arrives.
     */
    static boolean hoistStep(SwingSpace s, AscentRules.Kin k, AscentSwing.Rope rope) {
        AscentSwing.Tune t = AscentSwing.T;
        double dx = rope.hx - k.x, dy = rope.hy - k.y, dz = rope.hz - k.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean there = d < HOIST_NEAR || ++rope.hoistAge > HOIST_MOST;
        if (!there) {
            double want = Math.min(t.hoistTop, Math.sqrt(2 * t.hoistPull * 0.8 * Math.max(0, d - 0.2)));
            double ex = dx / d * want - k.vx, ey = dy / d * want - k.vy, ez = dz / d * want - k.vz, e = Math.sqrt(ex * ex + ey * ey + ez * ez);
            double f = e > 1e-9 ? Math.min(1, t.hoistPull / e) : 0;
            double vx = k.vx + ex * f, vy = k.vy + ey * f, vz = k.vz + ez * f;
            if (s != null && AscentSwing.swept(s, k.x, k.y, k.z, k.x + vx, k.y + vy, k.z + vz)) {
                there = true;
            } else {
                k.set(k.x + vx, k.y + vy, k.z + vz, vx, vy, vz);
            }
        }
        if (!there) return false;
        rope.hoist = false;
        double ax = k.x - rope.ax, ay = k.y - rope.ay, az = k.z - rope.az, r = Math.max(LEAST, Math.sqrt(ax * ax + ay * ay + az * az));
        rope.len = rope.base = rope.fit = r;
        rope.caught = true;
        double o = (k.vx * ax + k.vy * ay + k.vz * az) / r;
        if (o < 0) {
            k.vx -= ax / r * o;
            k.vy -= ay / r * o;
            k.vz -= az / r * o;
        }
        return true;
    }

    /** The hoist arrives within this of its point, and gives up hauling after this many ticks. */
    static final double HOIST_NEAR = 0.5;
    static final int HOIST_MOST = 40;

    /** Whether the rope is hauling you up its near side now. */
    public static boolean hoisting(AscentSwing.Rope rope) {
        return rope.real && rope.hoist;
    }

    /**
     * One whole tick of a real rope (AscentSwing.tick's, for {@link AscentSwing.Rope#real}): the hoist while it hauls,
     * else the step and its look ahead. Something met within the look turns the speed a few degrees either way where that
     * clears it (the bend kept only for the tick); a held rope ({@link AscentSwing#hold}) brakes for what it still meets
     * within 4 ticks, so it meets it softly and swings back, and never lets go by itself. Not held, it asks to let go
     * before such a wall, or once its first arc has topped out.
     */
    static int tick(SwingSpace s, AscentRules.Kin k, AscentSwing.Rope rope, double yaw, double fwd, AscentSwing.Work w) {
        w.bent = false;
        w.braked = false;
        if (rope.hoist) {
            hoistStep(s, k, rope);
            w.ahead = -1;
            return AscentSwing.GO;
        }
        step(s, k, rope, yaw, fwd);
        int ahead = ahead(s, k, rope, yaw, w);
        if (ahead >= 0) {
            double vx = k.vx, vz = k.vz;
            for (double b : AscentSwing.T.bends) {
                double c = Math.cos(b * D2R), sn = Math.sin(b * D2R);
                k.vx = vx * c + vz * sn;
                k.vz = -vx * sn + vz * c;
                if (ahead(s, k, rope, yaw, w) < 0) {
                    ahead = -1;
                    w.bent = true;
                    break;
                }
            }
            if (!w.bent) {
                k.vx = vx;
                k.vz = vz;
            }
        }
        w.ahead = ahead;
        if (AscentSwing.hold) {
            if (ahead >= 0 && ahead < 4) {
                AscentSwing.brake(k);
                w.braked = true;
            }
            return AscentSwing.GO;
        }
        if (ahead >= 0 && ahead < 4) return AscentSwing.WALL;
        return rope.swung ? AscentSwing.TOP : AscentSwing.GO;
    }

    /** The tick within the look-ahead this real swing would meet something (no key held), or -1. */
    static int ahead(SwingSpace s, AscentRules.Kin from, AscentSwing.Rope rope, double yaw, AscentSwing.Work w) {
        AscentRules.Kin k = w.k.set(from.x, from.y, from.z, from.vx, from.vy, from.vz);
        w.r.copy(rope);
        for (int i = 0; i < AscentSwing.T.lookahead; i++) {
            double x = k.x, y = k.y, z = k.z;
            step(s, k, w.r, yaw, 0);
            if (AscentSwing.swept(s, x, y, z, k.x, k.y, k.z)) return i;
        }
        return -1;
    }

    /** How well a let-go now is timed, 0..1: rising and moving away from under the anchor, best {@link AscentSwing.Tune#realWin} up. */
    static double window(AscentRules.Kin k, AscentSwing.Rope rope) {
        if (!rope.caught || rope.hoist || k.vy <= 0) return 0;
        if ((k.x - rope.ax) * k.vx + (k.z - rope.az) * k.vz <= 0) return 0;
        AscentSwing.Tune t = AscentSwing.T;
        return Math.max(0, 1 - Math.abs(angle(k, rope) - t.realWin) / t.winGood);
    }

    // ---- where it bites: the swing played forward ----------------------------------------------------------------------

    /** A swing under is played this long (its first arc and back) and a swing round this long (you let go within it). */
    static final int SPAN_UNDER = 45, SPAN_ROUND = 20;
    /** Ticks of skimming the floor a swing may have within its span. */
    static final int SKIM_MOST = 4;
    /** A swing round must turn you this much within its span (degrees). */
    static final double ROUND_TURN = 45;
    /** The far side's room: a held arc stops this short (degrees) of where the played swing first met something. */
    static final double ROOM_MARGIN = 12;

    /**
     * The real rope thrown from the hand at (x, y, z), played forward: its flight out, the bite, then
     * {@link #SPAN_UNDER} ticks with forward held. {@link #UNDER} when it passes under the anchor and its whole first arc
     * and back meets nothing; {@link #ROUND} when it passes beside it, turns you at least {@link #ROUND_TURN} within
     * {@link #SPAN_ROUND} ticks on a rope no longer than {@link AscentSwing.Tune#roundMost} (a longer, flatter rope off
     * something low can't hold you up), and that much meets nothing; 0 otherwise. Neither may skim the floor more than
     * {@link #SKIM_MOST} ticks. Writes the swing's top speed (its score) and the far side's room (its cap) into
     * {@code w.playScore}, {@code w.playCap}.
     */
    static int play(SwingSpace s, AscentRules.Kin hand, double yaw, double x, double y, double z, AscentSwing.Work w) {
        AscentSwing.Tune t = AscentSwing.T;
        AscentRules.Kin k = w.rk.set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
        double lfx = AscentSwing.fx(yaw), lfz = AscentSwing.fz(yaw), llx = -lfz, llz = lfx;
        AscentSwing.entry(k, yaw);
        double dist = Math.sqrt((x - k.x) * (x - k.x) + (y - k.y) * (y - k.y) + (z - k.z) * (z - k.z));
        for (int i = AscentSwing.travel(dist); i > 0; i--) {
            double x0 = k.x, y0 = k.y, z0 = k.z;
            k.vy = (k.vy - AscentRules.G) * AscentRules.CARRY_DRAG_V;
            k.vx *= AscentRules.CARRY_DRAG_H;
            k.vz *= AscentRules.CARRY_DRAG_H;
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
            if (AscentSwing.swept(s, x0, y0, z0, k.x, k.y, k.z)) return 0;
        }
        AscentSwing.Rope r = w.rr.set(x, y, z, dist, dist);
        r.real = true;
        r.cap = t.holdCap;
        bite(s, k, r, yaw);
        double biteLen = r.len, h0 = Math.atan2(k.vx, k.vz), turn = 0, top = k.speed();
        boolean crossed = false, passed = false, under = false;
        int hitAt = -1, skimRound = 0;
        double hitAng = 180;
        for (int i = 1; i <= SPAN_UNDER; i++) {
            double x0 = k.x, y0 = k.y, z0 = k.z;
            step(s, k, r, yaw, 1);
            if (AscentSwing.swept(s, x0, y0, z0, k.x, k.y, k.z)) {
                hitAt = i;
                hitAng = angle(k, r);
                break;
            }
            top = Math.max(top, k.speed());
            if (i <= SPAN_ROUND) {
                skimRound = r.skimming;
                if (k.hspeed() > 0.05) turn = Math.max(turn, Math.abs(Math.IEEEremainder(Math.atan2(k.vx, k.vz) - h0, 2 * Math.PI)) / D2R);
            }
            double along = (k.x - x) * lfx + (k.z - z) * lfz;
            if (!crossed && along >= 0) {
                crossed = true;
                under = Math.abs((k.x - x) * llx + (k.z - z) * llz) < CROSS_UNDER;
            }
            if (along > 0.5) passed = true;
        }
        if (!passed) return 0;
        int kind = crossed && under ? UNDER : ROUND;
        int span = kind == UNDER ? SPAN_UNDER : SPAN_ROUND;
        if (hitAt >= 0 && hitAt <= span) return 0;
        if ((kind == UNDER ? r.skimming : skimRound) > SKIM_MOST) return 0;
        if (kind == ROUND && (biteLen > t.roundMost || turn < ROUND_TURN)) return 0;
        w.playScore = top;
        w.playCap = Math.max(HELD_LEAST, Math.min(t.holdCap, hitAng - ROOM_MARGIN));
        return kind;
    }

    /** A swing that crosses the anchor's line nearer than this (blocks, across the look) swings under it. */
    static final double CROSS_UNDER = 1.5;
    /** No real arc's room is less than this (degrees from the bottom). */
    static final double HELD_LEAST = 30;

    /**
     * Whether a pick by the heading's own search (AscentSwing.find) swings as a real rope: played forward, a swing under
     * or round; its move and room go into the pick.
     */
    static boolean fits(SwingSpace s, AscentRules.Kin hand, double heading, AscentSwing.Pick c, AscentSwing.Work w) {
        int kind = play(s, hand, heading, c.x, c.y, c.z, w);
        if (kind == 0) return false;
        c.move = kind;
        c.cap = w.playCap;
        c.reel = false;
        return true;
    }

    /**
     * Where you look, for the real rope (AscentSwing.aimed's, SPEC §62.9): the look's ray meets a block within reach;
     * the points round it are the hit, the top over it (and 1.5 and 3 further in along the look where the thing goes on,
     * each at the top of its own column),
     * the underside over open air in its column, and each of those 2 and 3 blocks either side across the look where there
     * is something to bite. Each is played forward ({@link #play}): the fastest swing under wins, else the fastest swing
     * round. Else, for something tall, the hoist ({@link #hoist}). Else, with {@code slings}, the rest as the aimed rope
     * has it: a sling past it (thrown past its side, so the next press swings round it), onto its top, up under it.
     */
    static boolean aimed(SwingSpace s, AscentRules.Kin hand, double ex, double ey, double ez, double yaw, double pitch, double reach,
            boolean slings, AscentSwing.Work w, AscentSwing.Pick out) {
        SwingSpace.Hit a = w.aim;
        double cp = Math.cos(pitch * D2R);
        w.aimT = Double.NaN;
        w.aimSling = w.aimOnto = 0;
        w.findState = -1;
        if (!s.ray(ex, ey, ez, AscentSwing.fx(yaw) * cp, -Math.sin(pitch * D2R), AscentSwing.fz(yaw) * cp, reach, a)) return false;
        w.aimT = a.t;
        int n = points(s, a, yaw, w);
        double[] p = w.rpts;
        double bestUnder = -1, bestRound = -1;
        int under = -1, round = -1;
        AscentSwing.Pick c = w.rc;
        for (int i = 0; i < n; i++) {
            c.x = p[i * 3];
            c.y = p[i * 3 + 1];
            c.z = p[i * 3 + 2];
            if (!AscentSwing.bites(s, c)) continue;
            double dx = c.x - hand.x, dy = c.y - hand.y, dz = c.z - hand.z;
            if (dx * dx + dy * dy + dz * dz > reach * reach || !AscentSwing.clear(s, hand.x, hand.y, hand.z, c.x, c.y, c.z, 0.5, w)) continue;
            int kind = play(s, hand, yaw, c.x, c.y, c.z, w);
            if (kind == UNDER && w.playScore > bestUnder) {
                bestUnder = w.playScore;
                under = i;
                w.rbest[0] = c.x;
                w.rbest[1] = c.y;
                w.rbest[2] = c.z;
                w.rbest[3] = w.playCap;
            } else if (kind == ROUND && w.playScore > bestRound) {
                bestRound = w.playScore;
                round = i;
                w.rbest[4] = c.x;
                w.rbest[5] = c.y;
                w.rbest[6] = c.z;
                w.rbest[7] = w.playCap;
            }
        }
        if (under >= 0 || round >= 0) {
            int o = under >= 0 ? 0 : 4;
            swing(hand, yaw, w.rbest[o], w.rbest[o + 1], w.rbest[o + 2], under >= 0 ? UNDER : ROUND, w.rbest[o + 3], under >= 0 ? bestUnder : bestRound, out);
            return true;
        }
        if (hoist(s, hand, yaw, n, w, out)) return true;
        if (!slings) return false;
        // where nothing swings: the aimed rope's own answers, its sling thrown past the side you're on (decision 3)
        double top = a.ny > 0 ? a.y : s.topThrough(a, AscentSwing.AIM_TOP_UP);
        double side = (hand.x - a.x) * -AscentSwing.fz(yaw) + (hand.z - a.z) * AscentSwing.fx(yaw);
        w.aimSling = AscentSwing.slingPast(s, hand, a, top, yaw, w, out, 0, side >= 0 ? SLING_ASIDE : -SLING_ASIDE);
        if (w.aimSling == 0) return true;
        w.aimOnto = AscentSwing.ontoTop(s, hand, a, top, reach, w, out, false);
        if (w.aimOnto == 0 || AscentSwing.underIt(s, hand, a, reach, w, out, false)) return true;
        return w.aimSling == AscentSwing.SLING_CLOSED && AscentSwing.slingPast(s, hand, a, top, yaw, w, out, AscentSwing.AIM_OPEN_TICKS, 0) == 0
                || w.aimOnto == AscentSwing.ONTO_CLOSED && AscentSwing.ontoTop(s, hand, a, top, reach, w, out, true) == 0
                || AscentSwing.underIt(s, hand, a, reach, w, out, true);
    }

    /** Where nothing swings, a sling's pull point is moved this far across the look to the side you're on (blocks). */
    static final double SLING_ASIDE = 3;

    /** The points tried round what the look met ({@link #aimed}), into {@code w.rpts}; how many. */
    private static int points(SwingSpace s, SwingSpace.Hit a, double yaw, AscentSwing.Work w) {
        double[] p = w.rpts;
        double lfx = AscentSwing.fx(yaw), lfz = AscentSwing.fz(yaw), llx = -lfz, llz = lfx;
        int n = 0;
        n = put(p, n, a.x, a.y, a.z);
        int first = n;
        double top = s.topThrough(a, TOP_UP);
        if (!Double.isNaN(top)) {
            n = put(p, n, a.x, top - 0.3, a.z);
            // a little in from the edge the look met: over the thing, not its lip, at the top of its own column (an oak's
            // crown is two wide layers and a narrow top 2 higher: the wide layer's top is too low to swing round)
            for (double in : IN) {
                double qx = a.x + lfx * in, qz = a.z + lfz * in;
                if (solid(s, qx, top - 0.5, qz)) n = put(p, n, qx, columnTop(s, qx, top - 0.5, qz) - 0.3, qz);
            }
        }
        if (a.ny == 0) {
            double bottom = s.bottomUnder(a, AscentSwing.AIM_UNDER_DOWN, AscentSwing.AIM_UNDER_ROOM);
            if (!Double.isNaN(bottom)) n = put(p, n, a.x - a.nx * 0.3, bottom - 0.04, a.z - a.nz * 0.3);
        }
        // beside: the same points moved across the look where there's still something there (the swing passes its trunk)
        int last = n;
        for (int i = first; i < last; i++) {
            double x = p[i * 3], y = p[i * 3 + 1], z = p[i * 3 + 2];
            for (double o : ASIDE) {
                double qx = x + llx * o, qz = z + llz * o;
                if (n * 3 + 3 <= p.length && (solid(s, qx, y, qz) || solid(s, qx, y - 1, qz) || solid(s, qx, y + 1, qz))) n = put(p, n, qx, y, qz);
            }
        }
        return n;
    }

    /** How far over the cell the look met its top is looked for (blocks). */
    static final double TOP_UP = 12;
    private static final double[] IN = {1.5, 3}, ASIDE = {-2, 2, -3, 3};

    private static int put(double[] p, int n, double x, double y, double z) {
        p[n * 3] = x;
        p[n * 3 + 1] = y;
        p[n * 3 + 2] = z;
        return n + 1;
    }

    /** The top of the run of solid cells from the one at (x, y, z) up ({@link #TOP_UP} at most). */
    private static double columnTop(SwingSpace s, double x, double y, double z) {
        double cy = Math.floor(y);
        for (int i = 0; i < TOP_UP && solid(s, x, cy + 1.5, z); i++) cy += 1;
        return cy + 1;
    }

    private static boolean solid(SwingSpace s, double x, double y, double z) {
        double fx = Math.floor(x), fy = Math.floor(y), fz = Math.floor(z);
        return s.boxHits(fx + 0.3, fy + 0.3, fz + 0.3, fx + 0.7, fy + 0.7, fz + 0.7);
    }

    /** A real swing from (x, y, z) into {@code out}. */
    private static void swing(AscentRules.Kin hand, double yaw, double x, double y, double z, int kind, double cap, double score, AscentSwing.Pick out) {
        double dx = x - hand.x, dy = y - hand.y, dz = z - hand.z, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double fx = AscentSwing.fx(yaw), fz = AscentSwing.fz(yaw), ahead = dx * fx + dz * fz;
        out.x = x;
        out.y = y;
        out.z = z;
        out.len = out.fitLen = len;
        out.score = score;
        out.elev = Math.atan2(dy, Math.max(0.01, Math.abs(ahead))) / D2R;
        out.side = Math.abs(dx * -fz + dz * fx);
        out.top = Double.NaN;
        out.zip = out.sling = out.reel = out.hoist = false;
        out.move = kind;
        out.cap = cap;
    }

    /**
     * The hoist (SPEC §62.9): nothing swings from where you are, but the thing is tall enough for a swing under it from
     * up its near side. For each top (then the hit), the swing's length keeps the feet a skim over the floor where you
     * stand ({@link AscentSwing.Tune#hoistLong} at most, 5 at least); its start is {@link AscentSwing.Tune#hoistStart}
     * degrees up the near side along the look. The start must have room for the body and a clear way from the hand,
     * and the hoist and the swing after it, played forward, must meet nothing for {@link #HOIST_SPAN} ticks and carry
     * you 3 past the anchor.
     */
    private static boolean hoist(SwingSpace s, AscentRules.Kin hand, double yaw, int n, AscentSwing.Work w, AscentSwing.Pick out) {
        AscentSwing.Tune t = AscentSwing.T;
        double[] p = w.rpts;
        double lfx = AscentSwing.fx(yaw), lfz = AscentSwing.fz(yaw);
        double floor = s.floorUnder(hand.x, hand.y - AscentRules.HAND_HEIGHT + 0.6, hand.z);
        double sn = Math.sin(t.hoistStart * D2R), cs = Math.cos(t.hoistStart * D2R);
        AscentSwing.Pick c = w.rc;
        for (int j = 0; j < n; j++) {
            int i = (j + 1) % n; // the tops and undersides first, the hit itself last
            c.x = p[i * 3];
            c.y = p[i * 3 + 1];
            c.z = p[i * 3 + 2];
            if (!AscentSwing.bites(s, c)) continue;
            double rad = Math.min(c.y - floor - t.skim - AscentRules.HAND_HEIGHT, t.hoistLong);
            if (rad < HOIST_LEAST) continue;
            double hx = c.x - lfx * rad * sn, hy = c.y - rad * cs, hz = c.z - lfz * rad * sn;
            double dx = c.x - hand.x, dy = c.y - hand.y, dz = c.z - hand.z;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) > AscentRules.TETHER_RANGE) continue;
            double fy = hy - AscentRules.HAND_HEIGHT, half = SwingSpace.HALF + 0.2;
            if (s.boxHits(hx - half, fy, hz - half, hx + half, fy + SwingSpace.TALL, hz + half)) continue;
            if (!AscentSwing.clear(s, hand.x, hand.y, hand.z, hx, hy, hz, 0, w) || !AscentSwing.clear(s, hand.x, hand.y, hand.z, c.x, c.y, c.z, 0.5, w)) continue;
            if (!playHoist(s, hand, yaw, c.x, c.y, c.z, rad, hx, hy, hz, w)) continue;
            swing(hand, yaw, c.x, c.y, c.z, HOIST, w.playCap, w.playScore, out);
            out.hoist = true;
            out.hoistLen = rad;
            out.hx = hx;
            out.hy = hy;
            out.hz = hz;
            return true;
        }
        return false;
    }

    /** A hoisted swing is at least this long (blocks); the hoist and its swing are played this long. */
    static final double HOIST_LEAST = 5;
    static final int HOIST_SPAN = 55;

    /** The hoist up to (hx, hy, hz) and the swing from there on a rope of {@code rad}, played forward (see {@link #hoist}). */
    private static boolean playHoist(SwingSpace s, AscentRules.Kin hand, double yaw, double x, double y, double z, double rad,
            double hx, double hy, double hz, AscentSwing.Work w) {
        AscentSwing.Tune t = AscentSwing.T;
        AscentRules.Kin k = w.rk.set(hand.x, hand.y, hand.z, hand.vx, hand.vy, hand.vz);
        double lfx = AscentSwing.fx(yaw), lfz = AscentSwing.fz(yaw);
        double dist = Math.sqrt((x - k.x) * (x - k.x) + (y - k.y) * (y - k.y) + (z - k.z) * (z - k.z));
        for (int i = AscentSwing.travel(dist); i > 0; i--) {
            k.vy = (k.vy - AscentRules.G) * AscentRules.CARRY_DRAG_V;
            k.vx *= AscentRules.CARRY_DRAG_H;
            k.vz *= AscentRules.CARRY_DRAG_H;
            k.x += k.vx;
            k.y += k.vy;
            k.z += k.vz;
        }
        AscentSwing.Rope r = w.rr.set(x, y, z, dist, dist);
        r.real = true;
        r.cap = t.holdCap;
        r.hoist = true;
        r.hoistLen = rad;
        r.hx = hx;
        r.hy = hy;
        r.hz = hz;
        bite(s, k, r, yaw);
        double top = 0;
        boolean passed = false;
        for (int i = 1; i <= HOIST_SPAN; i++) {
            double x0 = k.x, y0 = k.y, z0 = k.z;
            if (r.hoist) {
                hoistStep(null, k, r);
            } else {
                step(s, k, r, yaw, 1);
                top = Math.max(top, k.speed());
            }
            if (AscentSwing.swept(s, x0, y0, z0, k.x, k.y, k.z)) return false;
            if (!r.hoist && (k.x - x) * lfx + (k.z - z) * lfz > 3) passed = true;
        }
        if (!passed || r.skimming > SKIM_MOST) return false;
        w.playScore = top;
        w.playCap = t.holdCap;
        return true;
    }

    /**
     * A real pick the marker showed a few ticks ago, played again from where you are now: true when it still gives a
     * swing (its move and room brought up to date). A hoist is taken as it was (its start doesn't move).
     */
    public static boolean still(SwingSpace s, AscentRules.Kin hand, double yaw, AscentSwing.Pick pk, AscentSwing.Work w) {
        if (pk.zip || pk.sling || pk.hoist) return true;
        int kind = play(s, hand, yaw, pk.x, pk.y, pk.z, w);
        if (kind == 0) return false;
        pk.move = kind;
        pk.cap = w.playCap;
        return true;
    }
}
