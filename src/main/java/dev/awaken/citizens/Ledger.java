package dev.awaken.citizens;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static dev.awaken.citizens.CitizensData.ABSENT;
import static dev.awaken.citizens.CitizensData.INSIDE;
import static dev.awaken.citizens.CitizensData.STAND;
import static dev.awaken.citizens.CitizensData.WALK;
import static dev.awaken.citizens.Roster.CARRY;
import static dev.awaken.citizens.Roster.HOME;
import static dev.awaken.citizens.Roster.ROUND;
import static dev.awaken.citizens.Roster.WANDER;

/**
 * Every citizen's day, everyone at once (SPEC §16.5, after spike S4): a slice of records updates each tick (each record
 * about once a second). The schedule picks the segment in force on the vanilla clock, starting the next one early by
 * the walking distance to its target, and records without a body walk the street graph abstractly. Records with a
 * body leave their position to the body; arrivals, dwells and segment changes still run here. Nothing allocates per
 * tick. The saved state lives in {@link CitizensData}; this class adds what is rebuilt on load (bodies, spot masks).
 */
final class Ledger {
    /** Walking speed in blocks per tick (a body walks this at speed 1; CitizenBody matches it). */
    static final float WALK_SPEED = 0.088f, RUN_SPEED = 0.135f;
    static final int DAY = 24000;
    /** Spare ticks when leaving early, so people arrive a little before they're due. */
    static final int LEAVE_MARGIN = 80;

    /** What the city does when people get somewhere (lamps, stalls, deliveries): CitizenWork, or nothing in tests. */
    interface Work {
        default void arrived(int i, int place, long now) {}

        default void dwellEnd(int i, int place, long now) {}

        default void left(int i, int place, long now) {}

        /** A record an event drives reached its goal. */
        default void roleArrived(int i, long now) {}
    }

    final Roster r;
    final StreetNet g;
    final CitizensData d;
    final int n, cx, cz, streetY;
    final @Nullable CitizenBody[] body;
    /** One bit per spot per place: who stands where. */
    final long[] spotMask;
    /** A spot an event gives a record (a place in a bucket line), or NaN: where it stands when its place has no spot. */
    final double[] rx, ry, rz;
    private final long[] lastGame, lastClock;
    /** The infirmary (the Association's door): where the hurt go. */
    final int infirmary;
    Work work = new Work() {};
    /** The saved data was (re)made for this roster: everyone was seated by the clock. */
    final boolean fresh;
    int slice;
    private int cursor;
    // counters (reset by commands and tests)
    long arrivals, late, noRoute, jumps, waits, overflow, early;
    final double[] tmp = new double[3];

    Ledger(Roster r, StreetNet g, CitizensData d, int cx, int cz, int streetY, long gameTime, long clock) {
        this.r = r;
        this.g = g;
        this.d = d;
        this.n = r.records;
        this.cx = cx;
        this.cz = cz;
        this.streetY = streetY;
        body = new CitizenBody[n];
        spotMask = new long[r.places];
        rx = new double[n];
        ry = new double[n];
        rz = new double[n];
        Arrays.fill(rx, Double.NaN);
        lastGame = new long[n];
        lastClock = new long[n];
        slice = Math.max(1, (n + 19) / 20);
        int inf = -1;
        for (int p = 0; p < r.places; p++) {
            if (r.pKind[p] == Roster.P_LANDMARK && "association".equals(r.pRefId[p])) inf = p;
        }
        infirmary = inf;
        Arrays.fill(lastGame, gameTime);
        Arrays.fill(lastClock, clock);
        boolean fresh = !r.sha256.equals(d.roster) || d.records() != n || d.lamps.length != r.lampPlace.length
                || d.stallOpen.length != r.stalls.size() || d.cloths.length != r.lines.size() || d.bellPos.length != r.bells.size();
        this.fresh = fresh;
        d.beforeSave = data -> store();
        if (fresh) {
            d.size(n, r.lampPlace.length, r.stalls.size(), r.lines.size(), r.bells.size());
            d.roster = r.sha256;
            int tod = tod(clock);
            for (int i = 0; i < n; i++) {
                if (!r.isCitizen(i)) {
                    d.state[i] = ABSENT;
                    continue;
                }
                d.seg[i] = segmentAt(i, tod);
                startSegment(i, d.seg[i], gameTime);
                settle(i, gameTime);
            }
            d.setDirty();
        } else {
            // where each record's last update left it, so a loaded ledger runs on exactly as it would have
            if (d.lastGame.length == n) System.arraycopy(d.lastGame, 0, lastGame, 0, n);
            if (d.lastClock.length == n) System.arraycopy(d.lastClock, 0, lastClock, 0, n);
            cursor = Math.floorMod(d.cursor, n);
            if (d.roleSpot.length == n * 3) {
                for (int i = 0; i < n; i++) {
                    if (d.roleSpot[i * 3] == Integer.MIN_VALUE) continue;
                    rx[i] = d.roleSpot[i * 3] / 100.0;
                    ry[i] = d.roleSpot[i * 3 + 1] / 100.0;
                    rz[i] = d.roleSpot[i * 3 + 2] / 100.0;
                }
            }
            // spots are rebuilt from the saved claims
            for (int i = 0; i < n; i++) {
                int s = d.spot[i];
                if (s >= 0 && d.place[i] >= 0) {
                    int k = s - r.spotStart[d.place[i]];
                    if (k >= 0 && k < 64) spotMask[d.place[i]] |= 1L << k;
                    else d.spot[i] = -1;
                }
            }
        }
    }

    static int tod(long clock) {
        return (int) Math.floorMod(clock, (long) DAY);
    }

    // ---- schedules ------------------------------------------------------------------------------------------------

    /** The segment in force at a time of day: the last one starting at or before it (the day's last one wraps). */
    int segmentAt(int i, int tod) {
        int base = r.segBase[i], cnt = r.segCount[i], best = -1, bestStart = -1, latest = 0, latestStart = -1;
        for (int k = 0; k < cnt; k++) {
            int s = r.segStart[base + k];
            if (s <= tod && s > bestStart) {
                bestStart = s;
                best = k;
            }
            if (s > latestStart) {
                latestStart = s;
                latest = k;
            }
        }
        return cnt == 0 ? -1 : best >= 0 ? best : latest;
    }

    int nextSegment(int i, int s) {
        int cnt = r.segCount[i];
        if (cnt <= 1) return s;
        // segments are sorted by start in the file; the next by start, wrapping
        int base = r.segBase[i], cur = r.segStart[base + s], best = -1, bestDelta = Integer.MAX_VALUE;
        for (int k = 0; k < cnt; k++) {
            if (k == s) continue;
            int delta = Math.floorMod(r.segStart[base + k] - cur, DAY);
            if (delta == 0) delta = DAY;
            if (delta < bestDelta) {
                bestDelta = delta;
                best = k;
            }
        }
        return best < 0 ? s : best;
    }

    byte act(int i) {
        int s = d.seg[i];
        return s < 0 ? HOME : r.segAct[r.segBase[i] + s];
    }

    int segIndex(int i) {
        return d.seg[i] < 0 ? -1 : r.segBase[i] + d.seg[i];
    }

    /** The place a segment first heads for. */
    int firstPlace(int i, int s) {
        int k = r.segBase[i] + s;
        byte a = r.segAct[k];
        int t = r.segTarget[k];
        return switch (a) {
            case ROUND -> {
                int[] route = r.routePlaces[t];
                yield route.length == 0 ? r.home[i] : route[Math.floorMod(Math.max(0, r.segAlt[k]), route.length)];
            }
            case WANDER -> {
                int[] route = r.routePlaces[t];
                yield route.length == 0 ? r.home[i] : route[rnd(i, 50 + s, route.length)];
            }
            case HOME -> t >= 0 ? t : r.home[i];
            default -> t;
        };
    }

    void startSegment(int i, int s, long now) {
        if (s < 0) return;
        int prevPlace = d.place[i];
        if (prevPlace >= 0 && d.state[i] == STAND) work.left(i, prevPlace, now);
        // still on the way somewhere fixed when the next thing starts: late
        if (d.state[i] == WALK && d.seg[i] >= 0) {
            byte prev = act(i);
            if (prev != ROUND && prev != WANDER && prev != CARRY) late++;
        }
        d.seg[i] = s;
        d.legs[i] = 0;
        d.dwellUntil[i] = 0;
        int k = r.segBase[i] + s;
        d.stop[i] = r.segAct[k] == ROUND ? Math.max(0, r.segAlt[k]) : 0;
        if (r.segAct[k] != CARRY) d.carry[i] = 0;
        goTo(i, firstPlace(i, s));
        d.setDirty();
    }

    /** Heads a record for a place (staying put if it's already there: that counts as arriving). */
    void goTo(int i, int place) {
        if (place < 0) return;
        boolean same = place == d.place[i] && (d.state[i] == STAND || d.state[i] == INSIDE) && d.at[i] == r.pNode[place];
        if (same) {
            work.arrived(i, place, lastGame[i]);
            return;
        }
        release(i);
        d.place[i] = place;
        d.goal[i] = r.pNode[place];
        if (d.state[i] != ABSENT) d.state[i] = WALK;
    }

    /** Puts a record where its segment wants it, as if it had walked there (creation, clock jumps). */
    void settle(int i, long now) {
        int p = d.place[i];
        if (p < 0) return;
        release(i);
        d.at[i] = r.pNode[p];
        d.next[i] = -1;
        d.prog[i] = 0;
        d.dwellUntil[i] = 0;
        arrive(i, now, false);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    void tick(long now, long clock) {
        int tod = tod(clock);
        for (int k = 0; k < slice; k++) {
            int i = cursor;
            cursor = cursor + 1 == n ? 0 : cursor + 1;
            update(i, now, clock, tod);
        }
    }

    void update(int i, long now, long clock, int tod) {
        long dGame = now - lastGame[i], dClock = clock - lastClock[i];
        lastGame[i] = now;
        lastClock[i] = clock;
        if (d.state[i] == ABSENT && d.role[i] == 0) return;
        boolean jumped = dClock < 0 || dClock > dGame + 200;
        if (d.role[i] != 0) {
            // an event drives this record; it only walks (abstractly) here
            if (body[i] == null) advance(i, now, dGame);
            return;
        }
        if (d.hurtUntil[i] > 0) {
            if (now < d.hurtUntil[i]) {
                if (infirmary >= 0 && d.place[i] != infirmary) {
                    d.seg[i] = -1;
                    goTo(i, infirmary);
                }
                if (body[i] == null) advance(i, now, dGame);
                return;
            }
            d.hurtUntil[i] = 0;
            d.seg[i] = -1; // recovered: back to the day
        }
        int sched = segmentAt(i, tod);
        if (jumped) {
            // someone moved the clock: everyone is already where this hour wants them
            jumps++;
            CitizenBody b = body[i];
            if (b != null) b.discardForJump();
            if (d.seg[i] != sched) startSegment(i, sched, now);
            settle(i, now);
            return;
        }
        int cur = d.seg[i];
        if (cur != sched && cur != nextSegment(i, sched)) {
            startSegment(i, sched, now);
        } else if (cur == sched) {
            leaveEarly(i, sched, tod, now);
        }
        if (body[i] != null) {
            return; // the body walks and arrives
        }
        if (d.state[i] == STAND && d.dwellUntil[i] > 0 && now >= d.dwellUntil[i]) afterDwell(i, now);
        advance(i, now, dGame);
    }

    /** Start the next segment early by the walking time to its first place. */
    private void leaveEarly(int i, int s, int tod, long now) {
        int nx = nextSegment(i, s);
        if (nx == s) return;
        int k = r.segBase[i] + nx;
        int until = Math.floorMod(r.segStart[k] - tod, DAY);
        if (until > 9000) return; // far off: nothing to check
        int target = firstPlace(i, nx);
        if (target < 0) return;
        if (d.state[i] == INSIDE && d.at[i] == r.pNode[target] && target == d.place[i]) return;
        int from = d.next[i] >= 0 ? d.next[i] : d.at[i];
        double dist = g.distance(from, r.pNode[target]);
        if (dist == -2) {
            waits++;
            return;
        }
        if (dist < 0) dist = 0;
        // at the pace of the next thing (a runner off duty walks)
        double travel = dist / speed(i, r.segAct[k]) + LEAVE_MARGIN + (r.pPath[target] ? 60 : 0);
        if (until <= travel) {
            early++;
            startSegment(i, nx, now);
        }
    }

    float speed(int i) {
        return speed(i, act(i));
    }

    float speed(int i, byte act) {
        // runners on their courier rounds, and lamplighters hurrying lamp to lamp before dark (their tours are 180-300
        // blocks against a dusk of about 2,300 ticks), go at a run
        boolean run = (r.job[i] == Roster.RUNNER || r.job[i] == Roster.LAMPLIGHTER) && act == ROUND || d.role[i] != 0 && Roles.runs(d.role[i]);
        return (run ? RUN_SPEED : WALK_SPEED) * r.speed[i];
    }

    private void advance(int i, long now, long ticks) {
        if (d.state[i] != WALK) return;
        double budget = speed(i) * Math.max(0, ticks);
        int guard = 0;
        while (budget > 0 && guard++ < 64) {
            if (d.next[i] < 0) {
                if (d.at[i] == d.goal[i]) {
                    arrive(i, now, true);
                    return;
                }
                int h = g.nextHop(d.at[i], d.goal[i]);
                if (h == -2) {
                    waits++;
                    return;
                }
                if (h < 0) {
                    noRoute++;
                    // unreachable from here: put them on their goal (never seen: bodies don't carry this)
                    d.at[i] = d.goal[i];
                    arrive(i, now, true);
                    return;
                }
                d.next[i] = h;
                d.prog[i] = 0;
            }
            double len = g.len(d.at[i], d.next[i]);
            double prog = d.prog[i] / 100.0;
            if (prog + budget >= len) {
                budget -= len - prog;
                d.at[i] = d.next[i];
                d.next[i] = -1;
                d.prog[i] = 0;
            } else {
                d.prog[i] = (int) Math.round((prog + budget) * 100);
                budget = 0;
            }
        }
    }

    /** Arrived at the goal's node: go inside, stand on a spot, or begin a stop of a round, a carry or a wander. */
    void arrive(int i, long now, boolean count) {
        if (count) arrivals++;
        int p = d.place[i];
        d.next[i] = -1;
        d.prog[i] = 0;
        if (d.role[i] != 0) {
            d.state[i] = STAND;
            work.roleArrived(i, now);
            return;
        }
        if (d.hurtUntil[i] > 0 || p < 0) {
            d.state[i] = INSIDE;
            return;
        }
        if (r.pKind[p] == Roster.P_DOOR || r.pKind[p] == Roster.P_LANDMARK) {
            byte a = act(i);
            if (a == HOME || a == Roster.INSIDE) {
                d.state[i] = INSIDE;
                d.setDirty();
                work.arrived(i, p, now);
                return;
            }
        }
        d.state[i] = STAND;
        claim(i, p);
        int k = segIndex(i);
        byte a = act(i);
        if (k >= 0 && (a == ROUND || a == CARRY || a == WANDER)) {
            int dwell = Math.max(20, r.segDwell[k]);
            d.dwellUntil[i] = now + dwell;
        }
        work.arrived(i, p, now);
        d.setDirty();
    }

    /** A stop's dwell is over: the round's next stop, the carry's other end, another errand. */
    void afterDwell(int i, long now) {
        int k = segIndex(i);
        int p = d.place[i];
        d.dwellUntil[i] = 0;
        if (k < 0) return;
        work.dwellEnd(i, p, now);
        byte a = r.segAct[k];
        int t = r.segTarget[k];
        switch (a) {
            case ROUND -> {
                int[] route = r.routePlaces[t];
                if (route.length == 0) return;
                int st = d.stop[i] + 1;
                boolean loop = (r.segFlags[k] & 1) != 0;
                if (st >= route.length) {
                    if (!loop) {
                        d.stop[i] = route.length;
                        return; // done: stand at the last stop until the next segment
                    }
                    st = 0;
                }
                d.stop[i] = st;
                work.left(i, p, now);
                goTo(i, route[st]);
            }
            case CARRY -> {
                int[] drops = r.segAlt[k] >= 0 && r.segAlt[k] < r.routePlaces.length ? r.routePlaces[r.segAlt[k]] : new int[0];
                d.legs[i]++;
                work.left(i, p, now);
                if ((d.legs[i] & 1) == 1 && drops.length > 0) {
                    d.carry[i] = 1;
                    goTo(i, drops[(d.legs[i] / 2 + i) % drops.length]);
                } else {
                    d.carry[i] = 0;
                    goTo(i, t);
                }
            }
            case WANDER -> {
                int[] route = r.routePlaces[t];
                if (route.length == 0) return;
                d.legs[i]++;
                int pick = route[rnd(i, 100 + d.legs[i], route.length)];
                if (pick == p && route.length > 1) pick = route[(rnd(i, 100 + d.legs[i], route.length) + 1) % route.length];
                work.left(i, p, now);
                goTo(i, pick);
            }
            default -> {}
        }
        d.setDirty();
    }

    // ---- spots --------------------------------------------------------------------------------------------------

    void claim(int i, int place) {
        release(i);
        int cnt = Math.min(64, r.spots(place));
        if (cnt == 0) return;
        long m = spotMask[place];
        int start = rnd(i, 400 + d.seg[i] + d.legs[i], cnt);
        for (int k = 0; k < cnt; k++) {
            int b = (start + k) % cnt;
            if ((m & (1L << b)) == 0) {
                spotMask[place] = m | (1L << b);
                d.spot[i] = r.spotStart[place] + b;
                return;
            }
        }
        overflow++;
    }

    void release(int i) {
        int s = d.spot[i];
        if (s >= 0) {
            int p = d.place[i];
            if (p >= 0) {
                int k = s - r.spotStart[p];
                if (k >= 0 && k < 64) spotMask[p] &= ~(1L << k);
            }
            d.spot[i] = -1;
        }
    }

    // ---- positions --------------------------------------------------------------------------------------------------

    /** Where a record stands or walks (world x, feet y, z into out); false when inside or absent. */
    boolean position(int i, double[] out) {
        byte st = d.state[i];
        if (st == INSIDE || st == ABSENT) return false;
        int a = d.at[i], b = d.next[i];
        if (st == STAND) {
            int s = d.spot[i];
            if (s >= 0) {
                spotWorld(s, d.place[i], out);
                return true;
            }
            if (!Double.isNaN(rx[i])) {
                out[0] = rx[i];
                out[1] = ry[i];
                out[2] = rz[i];
                return true;
            }
            out[0] = g.x[a];
            out[1] = g.y[a] + 1;
            out[2] = g.z[a];
            // nobody stands exactly on the node when a place is full: a seeded half-block aside
            double ang = rnd(i, 7, 360) * Math.PI / 180.0;
            out[0] += Math.cos(ang) * 0.9;
            out[2] += Math.sin(ang) * 0.9;
            return true;
        }
        if (b < 0) {
            out[0] = g.x[a];
            out[1] = g.y[a] + 1;
            out[2] = g.z[a];
            return true;
        }
        double len = g.len(a, b), t = len < 1e-6 ? 0 : Math.min(1, d.prog[i] / 100.0 / len);
        double dx = g.x[b] - g.x[a], dz = g.z[b] - g.z[a];
        out[0] = g.x[a] + dx * t;
        out[2] = g.z[a] + dz * t;
        out[1] = g.y[a] + (g.y[b] - g.y[a]) * t + 1;
        float lane = g.lane(a, b);
        if (lane > 0 && len > 1e-6) {
            out[0] += -dz / len * lane;
            out[2] += dx / len * lane;
        }
        return true;
    }

    /** A spot's world position (feet). */
    void spotWorld(int s, int place, double[] out) {
        out[0] = cx + r.spotX[s];
        out[2] = cz + r.spotZ[s];
        out[1] = streetY + (place >= 0 ? r.pRy[place] : 0) + 1;
    }

    /** A place's own world position (feet). */
    void placeWorld(int place, double[] out) {
        out[0] = cx + r.pX[place];
        out[2] = cz + r.pZ[place];
        out[1] = streetY + r.pRy[place] + 1;
    }

    float spotYaw(int i) {
        int s = d.spot[i];
        if (s >= 0) return r.spotYaw[s];
        int p = d.place[i];
        return p >= 0 ? r.pYaw[p] : 0f;
    }

    // ---- misc ---------------------------------------------------------------------------------------------------

    int rnd(int i, int salt, int bound) {
        long z = 0xC171_2E45L ^ (i * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (int) Math.floorMod(z, (long) Math.max(1, bound));
    }

    int embodied() {
        int c = 0;
        for (CitizenBody b : body) if (b != null) c++;
        return c;
    }

    int count(byte state) {
        int c = 0;
        for (int i = 0; i < n; i++) if (d.state[i] == state && r.isCitizen(i)) c++;
        return c;
    }

    /** Before a save: the transient timing and event spots go into the data. */
    void store() {
        if (d.lastGame.length != n) d.lastGame = new long[n];
        if (d.lastClock.length != n) d.lastClock = new long[n];
        System.arraycopy(lastGame, 0, d.lastGame, 0, n);
        System.arraycopy(lastClock, 0, d.lastClock, 0, n);
        d.cursor = cursor;
        if (d.roleSpot.length != n * 3) d.roleSpot = new int[n * 3];
        for (int i = 0; i < n; i++) {
            boolean set = !Double.isNaN(rx[i]);
            d.roleSpot[i * 3] = set ? (int) Math.round(rx[i] * 100) : Integer.MIN_VALUE;
            d.roleSpot[i * 3 + 1] = set ? (int) Math.round(ry[i] * 100) : 0;
            d.roleSpot[i * 3 + 2] = set ? (int) Math.round(rz[i] * 100) : 0;
        }
    }

    void resetCounters() {
        arrivals = late = noRoute = jumps = waits = overflow = early = 0;
    }
}
