package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.citizens.block.CityFireBlock;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.awaken.citizens.CitizensData.ABSENT;
import static dev.awaken.citizens.CitizensData.INSIDE;
import static dev.awaken.citizens.CitizensData.STAND;
import static dev.awaken.citizens.CitizensData.WALK;

/**
 * The city's own troubles (brief M3 §5, SPEC §16.5), budgeted per play-day and seeded: a burglary most nights, 0-2
 * pickpockets in the market, a fire in the Works about one day in five, now and then a runaway cart on the Quay, and from
 * D-rank a gate tremor outside the wall. Each one is a small state machine in {@link CitizensData} that runs with or
 * without a player and ends on its own; what it leaves is world state only (patent rule): district safety, gratitude,
 * stall stock, repairs for the masons. Citizens are hurt, never killed.
 */
final class CityEvents {
    static final List<String> KINDS = List.of("burglary", "pickpocket", "fire", "cart", "tremor", "rush", "infirmary");
    static final int BURGLARY = 0, PICKPOCKET = 1, FIRE = 2, CART = 3, TREMOR = 4, RUSH = 5, INFIRMARY = 6;
    // phases
    static final int P_GO = 0, P_INSIDE = 1, P_FLEE = 2, P_CAUGHT = 3, P_DONE = 9;
    static final Identifier CAUGHT_THIEF = Awaken.id("caught_thief"), RETURNED = Awaken.id("returned_goods"),
            FOUGHT_FIRE = Awaken.id("fought_fire"), RESCUED = Awaken.id("rescued"), CLEARED_CART = Awaken.id("cleared_cart"),
            CLOSED_GATE = Awaken.id("closed_gate");
    /** The longest any event may run before it resolves itself (no event is ever left hanging). */
    static final int MAX_TICKS = 12000;

    final Citizens c;
    final Ledger l;
    final Roster r;
    final CitizensData d;
    /** Today's plan: kind and the time of day it starts (not saved: re-planned from the play-day's seed). */
    private final List<int[]> plan = new ArrayList<>();
    private long plannedDay = -1;
    private int lastTod = -1;
    /** The last few things the crier can tell: news key, district, play-day. */
    private final List<String[]> news = new ArrayList<>();
    private String about = "";
    long started, resolved, playerHelped;

    CityEvents(Citizens c) {
        this.c = c;
        this.l = c.ledger;
        this.r = c.roster;
        this.d = c.data;
    }

    // ---- planning ---------------------------------------------------------------------------------------------------

    void tick(long now, int tod) {
        long day = PlayDays.current(c.server);
        if (day != plannedDay) planDay(day);
        int step = lastTod < 0 ? 0 : Math.floorMod(tod - lastTod, Ledger.DAY);
        if (step > 0 && step < 600 && !c.level.players().isEmpty()) {
            // only time that really passed starts planned events (a clock jump skips them)
            for (int[] p : plan) {
                if (p[2] != 0) continue;
                int ahead = Math.floorMod(p[1] - lastTod, Ledger.DAY);
                if (ahead > 0 && ahead <= step) {
                    p[2] = 1;
                    start(KINDS.get(p[0]), null, now);
                }
            }
        }
        lastTod = tod;
        for (int k = d.events.size() - 1; k >= 0; k--) step(k, now, tod);
        if (now % 20 == 11) returns(now);
    }

    /** The play-day's events, from its seed: a burglary most nights, 0-2 pickpockets, a fire one day in five, a rare cart. */
    private void planDay(long day) {
        plannedDay = day;
        plan.clear();
        long s = CitizenWork.mixHash(day * 0x9E3779B97F4A7C15L + 77);
        if (Math.floorMod(s, 100) < 85) plan.add(new int[]{BURGLARY, 15500 + (int) Math.floorMod(s >>> 8, 5000L), 0});
        int pp = (int) Math.floorMod(s >>> 20, 100L);
        int pickpockets = pp < 25 ? 0 : pp < 70 ? 1 : 2;
        for (int k = 0; k < pickpockets; k++) plan.add(new int[]{PICKPOCKET, 2000 + (int) Math.floorMod(s >>> (28 + 7 * k), 8000L), 0});
        if (Math.floorMod(s >>> 40, 100L) < 20) plan.add(new int[]{FIRE, 3000 + (int) Math.floorMod(s >>> 44, 6000L), 0});
        if (Math.floorMod(s >>> 50, 100L) < 14) plan.add(new int[]{CART, 2000 + (int) Math.floorMod(s >>> 54, 7000L), 0});
        if (Math.floorMod(s >>> 58, 100L) < 33) plan.add(new int[]{TREMOR, 1500 + (int) Math.floorMod(s >>> 3, 8000L), 0});
        Awaken.LOG.info("[citizens] play-day {} events planned: {}", day, plan.stream().map(p -> KINDS.get(p[0]) + "@" + p[1]).toList());
    }

    /** Tests: no planned events today (only the ones started by hand). */
    void noPlan() {
        plannedDay = PlayDays.current(c.server);
        plan.clear();
    }

    /** CitizenHooks.post: another module tells the city something happened near it. */
    void post(Identifier event, BlockPos pos) {
        long now = c.level.getGameTime();
        switch (event.getPath()) {
            // Carillon's opening (SPEC §16.4)
            case "bell_wrong" -> bellWrong(pos);
            case "gate_rush" -> rush(pos, now, false);
            case "gate_burst" -> rush(pos, now, true);
            case "bell_bridge_down" -> bellBridgeDown();
            case "infirmary_wake" -> infirmary(pos, now);
            case "infirmary_done" -> {
                for (CitizensData.Event e : List.copyOf(d.events)) if (e.kind() == INFIRMARY && e.phase() != P_DONE) finish(e, now);
            }
            default -> {
                String kind = event.getPath().replace("gate_", "");
                if (kind.equals("tremor")) start("tremor", null, now);
            }
        }
    }

    // ---- the opening (SPEC §16.4): the bell rings wrong, the market runs, the bridge falls, the infirmary ----------------

    private static final String[] BELL_LINES = {"The great bell?", "That's not the hour...", "Look - the square!",
            "What is that light?", "Something's wrong with the Choir.", "Get inside!"};
    private static final String[] RUSH_LINES = {"Run!", "Away from the square!", "It's a gate - a gate in the square!",
            "Don't look at it, run!", "Mind the children!", "The Watch is closing the square!"};

    /** The great bell out of time: everyone near a player stops, turns to the square and points; some say so. */
    private void bellWrong(BlockPos square) {
        long seed = CitizenWork.mixHash(c.level.getGameTime() * 7 + 3);
        int k = 0;
        for (CitizenBody b : List.copyOf(c.crowd.bodies)) {
            if (b.nearestSq > 72 * 72) continue;
            b.gesture(Act.POINT_SHOUT, 50 + (int) Math.floorMod(seed >>> (k % 40), 30L));
            b.getLookControl().setLookAt(square.getX() + 0.5, square.getY() + 30, square.getZ() + 0.5, 40f, 40f);
            if (k % 3 == 0) {
                String line = BELL_LINES[(int) Math.floorMod(seed + k, (long) BELL_LINES.length)];
                c.recognition.aloud(b, line, 50, 2, 28, c.level.getGameTime());
            }
            k++;
        }
        c.work.alarm(square.getX(), square.getZ(), 6);
    }

    /**
     * The market runs (Act 3), or the arrival's light throws the square's people back ({@code burst}): citizens near the
     * square run west toward the river (toward the runners coming in), more called out of the market's doors; the Watch
     * go to the square's mouths and stand there. Ends by itself after a minute and a half.
     */
    private void rush(BlockPos square, long now, boolean burst) {
        CitizensData.Event live = null;
        for (CitizensData.Event e : d.events) if (e.kind() == RUSH && e.phase() != P_DONE) live = e;
        int id = live != null ? live.id() : newId();
        double sx = square.getX() + 0.5, sz = square.getZ() + 0.5;
        int sqNode = c.net.nearest(sx, sz, true);
        long seed = CitizenWork.mixHash(now * 17 + 41);
        double[] tmp = new double[3];
        int fled = 0, called = 0;
        List<int[]> cand = new ArrayList<>();
        for (int i = 0; i < r.citizens; i++) {
            if (d.role[i] != 0 || d.hurtUntil[i] > 0 || !r.named[i].isEmpty() || r.job[i] == Roster.WATCH) continue;
            boolean out = l.position(i, tmp);
            double dd = out ? Math.hypot(tmp[0] - sx, tmp[2] - sz) : Double.MAX_VALUE;
            if (out && dd <= (burst ? 40 : 90)) cand.add(new int[]{(int) dd, i, 1});
            else if (!burst && d.state[i] == INSIDE && l.body[i] == null && r.pDistrict[Math.max(0, r.home[i])] == CityPlan.MARKET) cand.add(new int[]{500, i, 0});
        }
        cand.sort((a, b) -> Integer.compare(a[0], b[0]));
        for (int[] cnd : cand) {
            if (fled + called >= 26) break;
            int i = cnd[1];
            int goal = nodeWest(sqNode, 70, 130, seed + i);
            if (goal < 0) continue;
            if (cnd[2] == 0) {
                // called out of a market door near the square, out of anyone's sight
                int at = nodeAround(sqNode, 18, 38, seed * 3 + i);
                if (at < 0) continue;
                d.at[i] = at;
                d.next[i] = -1;
                d.prog[i] = 0;
                d.spot[i] = -1;
                d.place[i] = -1;
                called++;
            } else fled++;
            send(i, goal, Roles.FLEE, id);
            CitizenBody b = body(i);
            if (b != null) {
                if (burst && b.distanceToSqr(sx, b.getY(), sz) < 30 * 30) {
                    // thrown back by the light
                    Vec3 away = new Vec3(b.getX() - sx, 0, b.getZ() - sz);
                    double len = Math.max(0.5, away.length());
                    b.setDeltaMovement(away.x / len * 0.9, 0.45, away.z / len * 0.9);
                    b.gesture(Act.HURT, 24);
                }
                if ((fled + called) % 4 == 1) c.recognition.aloud(b, RUSH_LINES[(int) Math.floorMod(seed + i, (long) RUSH_LINES.length)], 50, 2, 30, now);
            }
        }
        // the Watch close the square's mouths
        if (live == null) {
            List<Integer> watch = nearestWatch(sx, sz, 6, 260);
            for (int w = 0; w < watch.size(); w++) {
                double ang = Math.PI * 2 * w / Math.max(1, watch.size()) + 0.4;
                int mouth = c.net.nearest(sx + Math.cos(ang) * 38, sz + Math.sin(ang) * 38, true);
                send(watch.get(w), mouth, Roles.GATE_GUARD, id);
            }
            c.work.alarm(sx, sz, 30);
            put(new CitizensData.Event(id, RUSH, P_GO, now, now + 20L * 90, new int[]{sqNode}));
            news("rush", "Choir Square");
        }
        Awaken.LOG.info("[citizens] the square {}: {} ran, {} called out", burst ? "throws its people back" : "runs", fled, called);
    }

    /** A junction west of {@code from} (toward the river) at a walking distance in [lo, hi], seeded; or any, or -1. */
    private int nodeWest(int from, double lo, double hi, long seed) {
        StreetNet.Column col = c.net.columnNow(from);
        double fx = c.net.x[from];
        int count = 0;
        for (int i = 0; i < c.net.n; i++) {
            if (c.net.kind[i] == StreetNet.JUNCTION && !c.net.wall[i] && col.dm()[i] >= lo * 10 && col.dm()[i] <= hi * 10 && c.net.x[i] < fx - 30) count++;
        }
        if (count == 0) return nodeAround(from, lo, hi, seed);
        int pick = (int) Math.floorMod(CitizenWork.mixHash(seed), (long) count);
        for (int i = 0; i < c.net.n; i++) {
            if (c.net.kind[i] != StreetNet.JUNCTION || c.net.wall[i] || col.dm()[i] < lo * 10 || col.dm()[i] > hi * 10 || c.net.x[i] >= fx - 30) continue;
            if (pick-- == 0) return i;
        }
        return -1;
    }

    private void stepRush(int k, CitizensData.Event e, long now) {
        if (now >= e.until()) finish(e, now);
    }

    /** The Bell Bridge is in the river: nobody's route crosses it any more (world state: it stays down). */
    void bellBridgeDown() {
        String[] tags = CityPlan.get().streetGraph.etag();
        int closed = 0;
        for (int e = 0; e < Math.min(tags.length, c.net.ea.length); e++) {
            if (tags[e].equals("bell_bridge")) {
                c.net.setOff(e, true);
                closed++;
            }
        }
        Awaken.LOG.info("[citizens] the Bell Bridge is down: {} street edges closed", closed);
    }

    /**
     * Someone wakes in the infirmary: Sun the healer at the bedside and an Association officer (a Watch citizen in the
     * Association's colours) at its foot, held there until the opening says they're done.
     */
    private void infirmary(BlockPos side, long now) {
        int id = newId();
        int sun = r.namedIndex("sun");
        List<Integer> watch = nearestWatch(side.getX(), side.getZ(), 1, 600);
        int officer = watch.isEmpty() ? -1 : watch.get(0);
        int[] who = {sun, officer};
        double[][] at = {{side.getX() + 0.5, side.getY(), side.getZ() - 0.4}, {side.getX() - 1.2, side.getY(), side.getZ() + 1.6}};
        for (int k = 0; k < 2; k++) {
            int i = who[k];
            if (i < 0 || d.role[i] != 0) continue;
            l.release(i);
            d.role[i] = Roles.ATTEND;
            d.event[i] = id;
            d.state[i] = STAND;
            d.place[i] = -1;
            d.spot[i] = -1;
            d.at[i] = c.net.nearest(at[k][0], at[k][2], false);
            l.rx[i] = at[k][0];
            l.ry[i] = at[k][1];
            l.rz[i] = at[k][2];
            CitizenBody b = body(i);
            if (b != null) b.teleportTo(at[k][0], at[k][1], at[k][2]);
            d.setDirty();
        }
        put(new CitizensData.Event(id, INFIRMARY, P_GO, now, now + 20L * 60 * 4, who));
        Awaken.LOG.info("[citizens] infirmary: Sun {} and an officer {} at the bed", sun, officer);
    }

    /** Starts an event now; near the player when one is given (commands), else where the city's plan puts it. */
    @Nullable String start(String kind, @Nullable ServerPlayer near, long now) {
        int k = KINDS.indexOf(kind);
        if (k < 0) return null;
        for (CitizensData.Event e : d.events) if (e.kind() == k && e.phase() != P_DONE) return null; // one of each at a time
        String s = switch (k) {
            case BURGLARY -> burglary(near, now);
            case PICKPOCKET -> pickpocket(near, now);
            case FIRE -> fire(near, now);
            case CART -> cart(near, now);
            default -> tremor(near, now);
        };
        if (s != null) {
            started++;
            Awaken.LOG.info("[citizens] event start: {}", s);
        }
        return s;
    }

    private int newId() {
        d.setDirty();
        return (int) d.nextEventId++;
    }

    private void put(CitizensData.Event e) {
        for (int k = 0; k < d.events.size(); k++) {
            if (d.events.get(k).id() == e.id()) {
                d.events.set(k, e);
                d.setDirty();
                return;
            }
        }
        d.events.add(e);
        d.setDirty();
    }

    private static CitizensData.Event phase(CitizensData.Event e, int phase, long since, long until) {
        return new CitizensData.Event(e.id(), e.kind(), phase, since, until, e.data());
    }

    // ---- actors -----------------------------------------------------------------------------------------------------

    /** A free event actor (a thief record), or -1. */
    private int freeActor() {
        for (int i = r.citizens; i < r.records; i++) if (d.state[i] == ABSENT && d.role[i] == 0) return i;
        return -1;
    }

    /** Brings an actor in at a node, heading for another. */
    private void enter(int i, int at, int goal, int role, int eventId) {
        d.at[i] = at;
        d.next[i] = -1;
        d.prog[i] = 0;
        d.place[i] = -1;
        d.spot[i] = -1;
        d.goal[i] = goal;
        d.state[i] = WALK;
        d.role[i] = role;
        d.event[i] = eventId;
        l.rx[i] = Double.NaN;
        d.setDirty();
    }

    /** Sends a citizen (or actor) on an event errand to a node. */
    private void send(int i, int node, int role, int eventId) {
        l.release(i);
        d.place[i] = -1;
        d.goal[i] = node;
        if (d.state[i] != WALK || d.at[i] != node) d.state[i] = WALK;
        d.role[i] = role;
        d.event[i] = eventId;
        l.rx[i] = Double.NaN;
        d.setDirty();
    }

    /** Back to the day: the schedule takes the record where it stands. Actors leave the city. */
    private void release(int i) {
        if (i < 0 || i >= r.records) return;
        d.role[i] = 0;
        d.event[i] = -1;
        l.rx[i] = Double.NaN;
        CitizenBody b = l.body[i];
        if (b != null) b.faceYaw = Float.NaN;
        if (!r.isCitizen(i)) {
            if (b != null) c.crowd.entered(b);
            d.state[i] = ABSENT;
        } else d.seg[i] = -1;
        d.setDirty();
    }

    private void releaseAll(int eventId) {
        for (int i = 0; i < r.records; i++) if (d.event[i] == eventId && d.role[i] != 0) release(i);
    }

    /** Nodes whose walking distance from {@code from} lies in [lo, hi] blocks: a seeded pick (junctions only), or -1. */
    private int nodeAround(int from, double lo, double hi, long seed) {
        StreetNet.Column col = c.net.columnNow(from);
        int count = 0;
        for (int i = 0; i < c.net.n; i++) if (c.net.kind[i] == StreetNet.JUNCTION && !c.net.wall[i] && col.dm()[i] >= lo * 10 && col.dm()[i] <= hi * 10) count++;
        if (count == 0) return -1;
        int pick = (int) Math.floorMod(CitizenWork.mixHash(seed), (long) count);
        for (int i = 0; i < c.net.n; i++) {
            if (c.net.kind[i] != StreetNet.JUNCTION || c.net.wall[i] || col.dm()[i] < lo * 10 || col.dm()[i] > hi * 10) continue;
            if (pick-- == 0) return i;
        }
        return -1;
    }

    /** Nearest Watch citizens free to answer (not hurt, not busy, not inside asleep), up to n. */
    private List<Integer> nearestWatch(double x, double z, int n, double within) {
        List<Integer> out = new ArrayList<>();
        double[] tmp = new double[3];
        List<double[]> cand = new ArrayList<>();
        for (int i = 0; i < r.citizens; i++) {
            if (r.job[i] != Roster.WATCH || d.role[i] != 0 || d.hurtUntil[i] > 0 || d.state[i] == INSIDE) continue;
            if (!l.position(i, tmp)) continue;
            double dd = Math.hypot(tmp[0] - x, tmp[2] - z);
            if (dd <= within) cand.add(new double[]{dd, i});
        }
        cand.sort((a, b) -> Double.compare(a[0], b[0]));
        for (int k = 0; k < Math.min(n, cand.size()); k++) out.add((int) cand.get(k)[1]);
        return out;
    }

    /** Stands a record at a door: {@code out} blocks from the door cell toward the street (0 = in the doorway). */
    private void frontOf(int door, double out, int i) {
        int street = -1;
        for (int k = c.net.adjStart[door]; k < c.net.adjStart[door + 1]; k++) street = c.net.adj[k];
        double x = c.net.x[door], z = c.net.z[door];
        if (street >= 0 && out > 0) {
            double dx = c.net.x[street] - x, dz = c.net.z[street] - z, len = Math.max(1e-6, Math.hypot(dx, dz));
            x += dx / len * out;
            z += dz / len * out;
        }
        l.rx[i] = x;
        l.ry[i] = c.net.y[door] + 1;
        l.rz[i] = z;
    }

    private @Nullable CitizenBody body(int i) {
        return i >= 0 && i < r.records ? l.body[i] : null;
    }

    private void shout(int i, String key, @Nullable ServerPlayer about, int ticks) {
        CitizenBody b = body(i);
        if (b == null) return;
        String line = Lines.get().pick(CitizenWork.mixHash(i * 13L + c.level.getGameTime() / 40), c.tod(), "event." + key);
        line = Lines.fill(line, about == null ? "friend" : about.getName().getString(), c.recognition.district(b), gateName(i), Recognition.hour(c.tod()));
        c.recognition.aloud(b, line, ticks, 2, 32, c.level.getGameTime());
    }

    private String gateName(int i) {
        return "Mill Gate";
    }

    // ---- burglary ------------------------------------------------------------------------------------------------------

    /** data: 0 house door place, 1 thief, 2 escape node, 3 caught-by-Watch roll (1/0), 4 catch at (per mille of the run),
     *  5 victim, 6 district, 7 bag dropped, 8 by player (1/0), 9 run length (decimetres), 10 watch 1, 11 watch 2 */
    private @Nullable String burglary(@Nullable ServerPlayer near, long now) {
        int thief = freeActor();
        if (thief < 0) return null;
        // a household at home tonight, near the player if asked
        int house = -1;
        double best = Double.MAX_VALUE;
        long seed = CitizenWork.mixHash(now * 31 + 5);
        for (Roster.Household h : r.households) {
            boolean home = false;
            for (int m : h.members()) if (d.state[m] == INSIDE && d.place[m] == h.home()) home = true;
            if (!home && near == null) continue;
            double score;
            if (near != null) {
                l.placeWorld(h.home(), l.tmp);
                score = Math.hypot(l.tmp[0] - near.getX(), l.tmp[2] - near.getZ());
                if (score < 18) score = 1000 + score; // not right under the player's nose
            } else score = Math.floorMod(CitizenWork.mixHash(seed + h.id()), 1000L);
            if (score < best) {
                best = score;
                house = h.home();
            }
        }
        if (house < 0) return null;
        int door = r.pNode[house];
        int start = nodeAround(door, 35, 70, seed + 1);
        int escape = nodeAround(door, 110, 200, seed + 2);
        if (start < 0 || escape < 0) return null;
        int victim = -1;
        for (Roster.Household h : r.households)
            if (h.home() == house) for (int m : h.members()) if (r.age[m] > 0 && victim < 0 && d.state[m] == INSIDE && d.place[m] == house) victim = m;
        int id = newId();
        boolean caught = Math.floorMod(seed >>> 9, 100L) < 50;
        int catchAt = 450 + (int) Math.floorMod(seed >>> 17, 300L);
        int district = CityPlan.get().district((int) Math.floor(c.net.x[door] - l.cx), (int) Math.floor(c.net.z[door] - l.cz));
        int[] data = {house, thief, escape, caught ? 1 : 0, catchAt, victim, district, 0, 0, 0, -1, -1};
        put(new CitizensData.Event(id, BURGLARY, P_GO, now, now + MAX_TICKS, data));
        enter(thief, start, door, Roles.THIEF_SNEAK, id);
        return "burglary #" + id + " at " + r.pRef[house] + " (" + Recognition.districtName(district) + "), thief from node " + start + " escaping to " + escape
                + (caught ? ", the Watch will catch him" : ", he'll get away unless someone stops him");
    }

    private void stepBurglary(int k, CitizensData.Event e, long now) {
        int[] dd = e.data();
        int thief = dd[1];
        switch (e.phase()) {
            case P_GO -> {
                if (d.state[thief] == STAND) { // at the door: in he goes
                    d.state[thief] = INSIDE;
                    d.role[thief] = Roles.THIEF_INSIDE;
                    CitizenBody b = body(thief);
                    if (b != null) c.crowd.entered(b);
                    put(phase(e, P_INSIDE, now, e.until()));
                }
            }
            case P_INSIDE -> {
                if (now - e.since() < 300) return;
                // the household wakes: someone at the door shouting, the thief out and running with the bag
                int victim = dd[5];
                int door = r.pNode[dd[0]];
                if (victim >= 0) {
                    send(victim, door, Roles.SHOUT, e.id());
                    d.at[victim] = door;
                    d.next[victim] = -1;
                    d.state[victim] = STAND;
                    frontOf(door, 1.1, victim);
                }
                d.at[thief] = door;
                d.next[thief] = -1;
                send(thief, dd[2], Roles.THIEF_FLEE, e.id());
                StreetNet.Column col = c.net.columnNow(dd[2]);
                dd[9] = Math.max(1, col.dm()[door]);
                List<Integer> watch = nearestWatch(c.net.x[door], c.net.z[door], 2, 160);
                for (int w = 0; w < watch.size(); w++) {
                    dd[10 + w] = watch.get(w);
                    send(watch.get(w), door, Roles.WATCH_CHASE, e.id());
                }
                put(phase(e, P_FLEE, now, e.until()));
                news("burglary", dd[6]);
                if (victim >= 0) shout(victim, "burglary_wake", null, 80);
            }
            case P_FLEE -> {
                // the Watch run for wherever the thief is now
                int target = d.next[thief] >= 0 ? d.next[thief] : d.at[thief];
                for (int w = 10; w <= 11; w++) {
                    int wr = dd[w];
                    if (wr < 0 || d.role[wr] != Roles.WATCH_CHASE || now % 20 != 0 || d.goal[wr] == target) continue;
                    d.goal[wr] = target;
                    d.state[wr] = WALK;
                }
                if (now % 120 == 0 && dd[10] >= 0) shout(dd[10], "watch_chase", null, 50);
                StreetNet.Column col = c.net.columnNow(dd[2]);
                int left = col.dm()[d.at[thief]];
                int progress = left < 0 ? 1000 : (int) (1000L - 1000L * left / dd[9]);
                if (dd[3] == 1 && progress >= dd[4]) {
                    caught(e, now, null);
                    return;
                }
                if (d.at[thief] == dd[2] || now - e.since() > 3600 + dd[9] / 4) escaped(e, now);
            }
            case P_CAUGHT -> {
                if (now - e.since() > 400) finish(e, now);
            }
            default -> {}
        }
    }

    /** The thief gives up: hands up where he stands; the Watch come to him, then walk him off. */
    private void caught(CitizensData.Event e, long now, @Nullable ServerPlayer by) {
        int[] dd = e.data();
        int thief = dd[1];
        int at = d.next[thief] >= 0 ? d.next[thief] : d.at[thief];
        send(thief, at, Roles.THIEF_CAUGHT, e.id());
        d.at[thief] = at;
        d.next[thief] = -1;
        d.state[thief] = STAND;
        for (int w = 10; w <= 11; w++) if (dd[w] >= 0) send(dd[w], at, Roles.WATCH_ESCORT, e.id());
        if (by != null) dd[8] = 1;
        shout(thief, "thief_caught", null, 60);
        put(phase(e, P_CAUGHT, now, e.until()));
        news("burglary_caught", dd[6]);
    }

    private void escaped(CitizensData.Event e, long now) {
        int[] dd = e.data();
        int district = dd[6];
        d.safety[district] = (byte) Math.max(0, d.safety[district] - 12);
        finish(e, now);
    }

    // ---- pickpocket --------------------------------------------------------------------------------------------------

    /** data: 0 stall, 1 thief, 2 escape node, 3 victim, 4 by player, 5 bag dropped, 6 run length, 7 cried */
    private @Nullable String pickpocket(@Nullable ServerPlayer near, long now) {
        int thief = freeActor();
        if (thief < 0 || r.stalls.isEmpty()) return null;
        long seed = CitizenWork.mixHash(now * 17 + 3);
        int s = (int) Math.floorMod(seed, (long) r.stalls.size());
        if (near != null) {
            double best = Double.MAX_VALUE;
            for (int k = 0; k < r.stalls.size(); k++) {
                l.placeWorld(r.stalls.get(k).front(), l.tmp);
                double dd = Math.hypot(l.tmp[0] - near.getX(), l.tmp[2] - near.getZ());
                if (dd < best) {
                    best = dd;
                    s = k;
                }
            }
        }
        int front = r.stalls.get(s).front();
        int node = r.pNode[front];
        int start = nodeAround(node, 30, 55, seed + 1);
        int escape = nodeAround(node, 70, 110, seed + 2);
        if (start < 0 || escape < 0) return null;
        int id = newId();
        put(new CitizensData.Event(id, PICKPOCKET, P_GO, now, now + MAX_TICKS, new int[]{s, thief, escape, -1, 0, 0, 1, 0}));
        enter(thief, start, node, Roles.PICKPOCKET, id);
        return "pickpocket #" + id + " at stall " + s + " (" + r.stalls.get(s).good() + ")";
    }

    private void stepPickpocket(int k, CitizensData.Event e, long now) {
        int[] dd = e.data();
        int thief = dd[1];
        switch (e.phase()) {
            case P_GO -> {
                if (d.state[thief] == STAND && d.dwellUntil[thief] == 0) {
                    d.dwellUntil[thief] = now + 160; // browsing
                    l.rx[thief] = Double.NaN;
                } else if (d.state[thief] == STAND && now >= d.dwellUntil[thief]) {
                    d.dwellUntil[thief] = 0;
                    // the lift: the keeper (or a shopper) cries out; the thief runs
                    Roster.Stall st = r.stalls.get(dd[0]);
                    int victim = -1;
                    for (int i = 0; i < r.citizens && victim < 0; i++)
                        if ((d.place[i] == st.front() || d.place[i] == st.keeper()) && d.state[i] == STAND && d.role[i] == 0) victim = i;
                    dd[3] = victim;
                    if (victim >= 0) {
                        d.role[victim] = Roles.SHOUT;
                        d.event[victim] = e.id();
                        shout(victim, "pickpocket_cry", null, 70);
                    }
                    send(thief, dd[2], Roles.THIEF_FLEE, e.id());
                    dd[6] = Math.max(1, c.net.columnNow(dd[2]).dm()[d.at[thief]]);
                    put(phase(e, P_FLEE, now, e.until()));
                }
            }
            case P_FLEE -> {
                if (d.at[thief] == dd[2] || now - e.since() > 1800) {
                    // away with it: the stall is short
                    d.stock[dd[0]] = (byte) Math.max(0, d.stock[dd[0]] - 25);
                    news("pickpocket", CityPlan.MARKET);
                    finish(e, now);
                }
            }
            case P_CAUGHT -> {
                if (now - e.since() > 300) finish(e, now);
            }
            default -> {}
        }
    }

    // ---- fire ------------------------------------------------------------------------------------------------------

    /** data: 0 plot id, 1 door place (-1 none), 2 intensity (0..1000), 3 trapped, 4 rescued, 5 player help,
     *  6 line count, 7 door node, 8.. fire block positions as x, y, z triples (up to 8) */
    private @Nullable String fire(@Nullable ServerPlayer near, long now) {
        CityPlan plan = CityPlan.get();
        long seed = CitizenWork.mixHash(now * 13 + 11);
        CityPlan.Plot pick = null;
        double best = Double.MAX_VALUE;
        for (CityPlan.Plot p : plan.plots) {
            if (p.district() != CityPlan.WORKS || !p.use().equals("house") || !p.hasDoor() || p.enter()) continue;
            double score = near != null ? Math.hypot(l.cx + p.doorX() - near.getX(), l.cz + p.doorZ() - near.getZ())
                    : Math.floorMod(CitizenWork.mixHash(seed + p.id()), 1000L);
            if (near != null && score < 14) score += 1000;
            if (score < best) {
                best = score;
                pick = p;
            }
        }
        if (pick == null) return null;
        int doorNode = -1;
        for (int i = 0; i < c.net.n; i++) if (c.net.kind[i] == StreetNet.DOOR && c.net.ref[i] == pick.id()) doorNode = i;
        if (doorNode < 0) return null;
        int[] data = new int[8 + 24];
        data[0] = pick.id();
        data[1] = -1;
        for (Roster.Household h : r.households) if (h.plot() == pick.id()) data[1] = h.home();
        data[2] = 1000;
        data[3] = -1;
        data[7] = doorNode;
        // flames on the roof: six columns over the plot, on top of whatever is highest there
        int n = 0;
        for (int t = 0; t < 8 && n < 8; t++) {
            int x = l.cx + pick.x0() + 1 + (int) Math.floorMod(CitizenWork.mixHash(seed + 40 + t), (long) Math.max(1, pick.x1() - pick.x0() - 1));
            int z = l.cz + pick.z0() + 1 + (int) Math.floorMod(CitizenWork.mixHash(seed + 80 + t), (long) Math.max(1, pick.z1() - pick.z0() - 1));
            BlockPos top = new BlockPos(x, l.streetY + pick.top() + 8, z);
            if (!c.level.isLoaded(top)) continue;
            while (top.getY() > l.streetY + 2 && c.level.getBlockState(top).isAir()) top = top.below();
            BlockPos at = top.above();
            if (!c.level.getBlockState(at).isAir()) continue;
            data[8 + n * 3] = at.getX();
            data[9 + n * 3] = at.getY();
            data[10 + n * 3] = at.getZ();
            n++;
        }
        for (int t = n; t < 8; t++) data[8 + t * 3] = Integer.MIN_VALUE;
        int id = newId();
        put(new CitizensData.Event(id, FIRE, P_GO, now, now + MAX_TICKS, data));
        setFlames(data, 1000);
        c.work.alarm(c.net.x[doorNode], c.net.z[doorNode], 40);
        // someone at home is trapped behind a jammed door
        if (data[1] >= 0) {
            for (Roster.Household h : r.households) {
                if (h.home() != data[1]) continue;
                for (int m : h.members()) if (data[3] < 0 && r.age[m] > 0 && d.hurtUntil[m] == 0) data[3] = m;
            }
        }
        if (data[3] >= 0) {
            int m = data[3];
            send(m, doorNode, Roles.TRAPPED, id);
            d.at[m] = doorNode;
            d.next[m] = -1;
            d.state[m] = STAND;
            frontOf(doorNode, 0.0, m); // in the doorway itself, behind the smoke
        }
        bucketLine(id, data, doorNode);
        news("fire", CityPlan.WORKS);
        return "fire #" + id + " at plot " + pick.id() + " (" + n + " flames" + (data[3] >= 0 ? ", " + r.name(data[3]) + " trapped" : "") + ")";
    }

    /** The bucket line: from the nearest water to the door, a spot every 1.6 blocks, filled by whoever is near. */
    private void bucketLine(int id, int[] data, int doorNode) {
        CityPlan plan = CityPlan.get();
        double dx0 = c.net.x[doorNode], dz0 = c.net.z[doorNode];
        double wx = Double.NaN, wz = Double.NaN, wd = Double.MAX_VALUE;
        for (int ox = -40; ox <= 40; ox++) {
            for (int oz = -40; oz <= 40; oz++) {
                int px = (int) Math.floor(dx0) + ox, pz = (int) Math.floor(dz0) + oz;
                if (!CityPlan.water(plan.kind(px - l.cx, pz - l.cz))) continue;
                double dd = ox * ox + oz * oz;
                if (dd < wd) {
                    wd = dd;
                    wx = px + 0.5;
                    wz = pz + 0.5;
                }
            }
        }
        if (Double.isNaN(wx)) return;
        double len = Math.hypot(wx - dx0, wz - dz0);
        int slots = (int) Math.min(8, Math.max(3, len / 1.6));
        List<double[]> spots = new ArrayList<>();
        for (int s = 0; s < slots; s++) {
            double t = (s + 0.5) / slots;
            double x = dx0 + (wx - dx0) * t, z = dz0 + (wz - dz0) * t;
            byte kd = plan.kind((int) Math.floor(x - l.cx), (int) Math.floor(z - l.cz));
            if (!CityPlan.walkable(kd)) continue;
            spots.add(new double[]{Math.floor(x) + 0.5, Math.floor(z) + 0.5});
        }
        // who comes: Works people and the Watch, nearest first, not inside asleep
        List<double[]> cand = new ArrayList<>();
        for (int i = 0; i < r.citizens; i++) {
            if (d.role[i] != 0 || d.hurtUntil[i] > 0 || d.state[i] == INSIDE || r.age[i] == 0) continue;
            if (!l.position(i, l.tmp)) continue;
            double dd = Math.hypot(l.tmp[0] - dx0, l.tmp[2] - dz0);
            if (dd < 110) cand.add(new double[]{dd, i});
        }
        cand.sort((a, b) -> Double.compare(a[0], b[0]));
        int n = 0;
        for (int s = 0; s < spots.size() && s < cand.size(); s++) {
            int i = (int) cand.get(s)[1];
            double[] sp = spots.get(s);
            int node = c.net.nearest(sp[0], sp[1], true);
            send(i, node, Roles.BUCKET, id);
            l.rx[i] = sp[0];
            l.rz[i] = sp[1];
            l.ry[i] = l.streetY + plan.lift((int) Math.floor(sp[0] - l.cx), (int) Math.floor(sp[1] - l.cz)) + 1;
            n++;
        }
        data[6] = n;
    }

    private void setFlames(int[] data, int intensity) {
        if (!c.work.edits) return; // a borrowed graph (the events test): the fire burns in the ledger only
        int want = (int) Math.ceil(intensity / 1000.0 * 8);
        for (int t = 0; t < 8; t++) {
            if (data[8 + t * 3] == Integer.MIN_VALUE) continue;
            BlockPos p = new BlockPos(data[8 + t * 3], data[9 + t * 3], data[10 + t * 3]);
            if (!c.level.isLoaded(p)) continue;
            BlockState s = c.level.getBlockState(p);
            boolean on = t < want;
            int flame = Math.clamp(intensity / 250, 0, 3);
            if (on && (s.isAir() || s.is(CitizensContent.CITY_FIRE.get())))
                c.level.setBlock(p, CitizensContent.CITY_FIRE.get().defaultBlockState().setValue(CityFireBlock.FLAME, flame), Block.UPDATE_ALL);
            else if (!on && s.is(CitizensContent.CITY_FIRE.get())) {
                c.level.removeBlock(p, false);
                c.level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private void stepFire(int k, CitizensData.Event e, long now) {
        int[] dd = e.data();
        if (e.phase() == P_DONE) return;
        // the line works the fire down whether anyone watches or not
        int working = 0;
        for (int i = 0; i < r.citizens; i++) if (d.event[i] == e.id() && d.role[i] == Roles.BUCKET && d.state[i] == STAND) working++;
        int before = dd[2];
        // six on the line put it out in about two minutes; left alone it burns for the event's whole length
        if (now % 15 == 0) dd[2] = Math.max(0, dd[2] - working);
        if (now % 20 == 0) dd[2] = Math.max(0, dd[2] - 1);
        if (now % 10 == 0) {
            for (int t = 0; t < 8; t++) {
                if (dd[8 + t * 3] == Integer.MIN_VALUE) continue;
                double x = dd[8 + t * 3] + 0.5, y = dd[9 + t * 3] + 0.6, z = dd[10 + t * 3] + 0.5;
                if (t < Math.ceil(dd[2] / 1000.0 * 8)) {
                    c.level.sendParticles(CitizensContent.CHIMNEY_SMOKE.get(), x, y + 0.8, z, 1, 0.3, 0.2, 0.3, 0.0);
                    c.level.sendParticles(CitizensContent.FIRE_EMBER.get(), x, y, z, 2, 0.3, 0.3, 0.3, 0.01);
                }
            }
        }
        if (before / 125 != dd[2] / 125) setFlames(dd, dd[2]);
        if (now % 160 == 0) {
            for (int i = 0; i < r.citizens; i++) if (d.event[i] == e.id() && d.role[i] == Roles.BUCKET && d.state[i] == STAND && (i + now / 160) % 4 == 0) {
                shout(i, "fire_cry", null, 50);
                break;
            }
            if (dd[3] >= 0 && dd[4] == 0) shout(dd[3], "fire_trapped", null, 60);
        }
        // the Watch get the trapped one out once the fire is low
        if (dd[3] >= 0 && dd[4] == 0 && dd[2] < 300) {
            dd[4] = 2;
            hurtOut(dd[3], now);
        }
        if (dd[2] == 0 || now - e.since() > 9000) {
            // out: what burned stays scorched until the masons rebuild it over two play-days
            setFlames(dd, 0);
            scorch(dd, now);
            if (dd[3] >= 0 && dd[4] == 0) hurtOut(dd[3], now);
            int any = -1;
            for (int i = 0; i < r.citizens; i++) if (d.event[i] == e.id() && d.role[i] == Roles.BUCKET) any = i;
            if (any >= 0) shout(any, "fire_out", null, 70);
            d.safety[CityPlan.WORKS] = (byte) Math.max(0, d.safety[CityPlan.WORKS] - 6);
            finish(e, now);
            return;
        }
        d.setDirty();
    }

    /** The trapped citizen comes out hurt (to the infirmary), rescued by someone. */
    private void hurtOut(int i, long now) {
        d.role[i] = 0;
        d.event[i] = -1;
        d.hurtUntil[i] = now + 6000;
        d.seg[i] = -1;
        d.setDirty();
    }

    private void scorch(int[] dd, long now) {
        int helped = dd[5];
        int burn = helped > 6 ? 6 : 16;
        int n = 0;
        for (int t = 0; t < 8 && n < burn; t++) {
            if (dd[8 + t * 3] == Integer.MIN_VALUE) continue;
            BlockPos fire = new BlockPos(dd[8 + t * 3], dd[9 + t * 3], dd[10 + t * 3]);
            for (BlockPos p : BlockPos.betweenClosed(fire.offset(-1, -2, -1), fire.offset(1, -1, 1))) {
                if (n >= burn || !c.work.edits || !c.level.isLoaded(p)) continue;
                BlockState s = c.level.getBlockState(p);
                if (s.isAir() || s.is(CitizensContent.CITY_FIRE.get()) || CitizenWork.burnt(s)) continue;
                if (Math.floorMod(CitizenWork.mixHash(p.asLong()), 3L) != 0) continue;
                // rebuilt over two play-days: each block's turn comes a little later
                c.work.addRepair(p.immutable(), s, now + 1200 + (long) n * (2L * PlayDays.DAY / Math.max(1, burn)));
                c.level.setBlock(p, (n & 1) == 0 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
                n++;
            }
        }
    }

    /** A player beat out a flame (CitizensModule's click listener): less fire, and they were seen helping. */
    boolean beatFlame(ServerPlayer p, BlockPos pos) {
        for (CitizensData.Event e : d.events) {
            if (e.kind() != FIRE || e.phase() == P_DONE) continue;
            int[] dd = e.data();
            for (int t = 0; t < 8; t++) {
                if (dd[8 + t * 3] == pos.getX() && dd[9 + t * 3] == pos.getY() && dd[10 + t * 3] == pos.getZ()) {
                    dd[2] = Math.max(0, dd[2] - 90);
                    dd[5]++;
                    c.level.removeBlock(pos, false);
                    c.level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.1f);
                    if (dd[5] == 1 || dd[5] % 3 == 0) c.recognition.witness(p, FOUGHT_FIRE, pos, 2);
                    if (dd[5] >= 3) about = p.getName().getString();
                    playerHelped++;
                    d.setDirty();
                    return true;
                }
            }
        }
        return false;
    }

    /** A player pulls the jammed door open (or reaches the trapped citizen): rescued, remembered. */
    boolean rescue(ServerPlayer p, BlockPos at) {
        for (CitizensData.Event e : d.events) {
            if (e.kind() != FIRE || e.phase() == P_DONE) continue;
            int[] dd = e.data();
            if (dd[3] < 0 || dd[4] != 0) continue;
            int door = dd[7];
            if (Math.hypot(c.net.x[door] - (at.getX() + 0.5), c.net.z[door] - (at.getZ() + 0.5)) > 3.5) continue;
            dd[4] = 1;
            dd[5] += 4;
            int m = dd[3];
            CitizenBody b = body(m);
            if (b != null) {
                String line = Lines.get().pick(CitizenWork.mixHash(m + c.level.getGameTime()), c.tod(), "event.fire_rescued");
                CitizensNet.say(p, b, Lines.fill(line, p.getName().getString(), "", "", 0), 90, 2);
            }
            hurtOut(m, c.level.getGameTime());
            c.recognition.witness(p, RESCUED, at, 6);
            about = p.getName().getString();
            playerHelped++;
            put(e);
            return true;
        }
        return false;
    }

    // ---- runaway cart --------------------------------------------------------------------------------------------------

    /** data: 0 edge, 1 crates left, 2 player help, 3.. crate positions (x, y, z) up to 10 */
    private @Nullable String cart(@Nullable ServerPlayer near, long now) {
        CityPlan plan = CityPlan.get();
        long seed = CitizenWork.mixHash(now * 7 + 19);
        int best = -1;
        double bs = Double.MAX_VALUE;
        for (int e = 0; e < c.net.m; e++) {
            int a = c.net.ea[e], b = c.net.eb[e];
            if (c.net.kind[a] != StreetNet.JUNCTION || c.net.kind[b] != StreetNet.JUNCTION || c.net.elen[e] < 7) continue;
            double mx = (c.net.x[a] + c.net.x[b]) / 2, mz = (c.net.z[a] + c.net.z[b]) / 2;
            if (plan.district((int) Math.floor(mx - l.cx), (int) Math.floor(mz - l.cz)) != CityPlan.QUAY_D) continue;
            double score = near != null ? Math.hypot(mx - near.getX(), mz - near.getZ()) : Math.floorMod(CitizenWork.mixHash(seed + e), 1000L);
            if (near != null && score < 10) score += 1000;
            if (score < bs) {
                bs = score;
                best = e;
            }
        }
        if (best < 0) return null;
        int a = c.net.ea[best], b = c.net.eb[best];
        double mx = (c.net.x[a] + c.net.x[b]) / 2, mz = (c.net.z[a] + c.net.z[b]) / 2;
        int y = c.net.y[a] + 1;
        int[] data = new int[3 + 30];
        int n = 0;
        for (int t = 0; t < 24 && n < 10; t++) {
            int x = (int) Math.floor(mx) + (int) Math.floorMod(CitizenWork.mixHash(seed + t * 3), 7L) - 3;
            int z = (int) Math.floor(mz) + (int) Math.floorMod(CitizenWork.mixHash(seed + t * 3 + 1), 7L) - 3;
            BlockPos p = new BlockPos(x, y, z);
            if (!c.work.edits || !c.level.isLoaded(p) || !c.level.getBlockState(p).isAir() || !c.level.getBlockState(p.below()).isSolid()) continue;
            if (!CityPlan.walkable(plan.kind(x - l.cx, z - l.cz))) continue;
            c.level.setBlock(p, CitizensContent.CARGO_CRATE.get().defaultBlockState(), Block.UPDATE_ALL);
            data[3 + n * 3] = x;
            data[4 + n * 3] = y;
            data[5 + n * 3] = z;
            n++;
        }
        for (int t = n; t < 10; t++) data[3 + t * 3] = Integer.MIN_VALUE;
        data[0] = best;
        data[1] = n;
        c.net.setOff(best, true);
        BlockPos crash = new BlockPos((int) Math.floor(mx), y, (int) Math.floor(mz));
        c.level.playSound(null, crash, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.2f, 0.7f);
        c.level.playSound(null, crash, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.5f, 0.6f);
        c.level.sendParticles(ParticleTypes.CLOUD, mx, y + 0.5, mz, 20, 1.5, 0.4, 1.5, 0.02);
        int lamp = c.work.nearestLamp(mx, mz, 9);
        if (lamp >= 0 && Math.floorMod(seed >>> 5, 2L) == 0) c.work.breakLamp(lamp, now);
        int id = newId();
        put(new CitizensData.Event(id, CART, P_GO, now, now + MAX_TICKS, data));
        // porters come to clear it; whoever's near gawps and shouts
        List<double[]> cand = new ArrayList<>();
        for (int i = 0; i < r.citizens; i++) {
            if (r.job[i] != Roster.PORTER || d.role[i] != 0 || d.hurtUntil[i] > 0 || d.state[i] == INSIDE) continue;
            if (l.position(i, l.tmp)) cand.add(new double[]{Math.hypot(l.tmp[0] - mx, l.tmp[2] - mz), i});
        }
        cand.sort((p, q) -> Double.compare(p[0], q[0]));
        int node = c.net.nearest(mx, mz, true);
        for (int s = 0; s < Math.min(3, cand.size()); s++) send((int) cand.get(s)[1], node, Roles.CLEAR, id);
        for (int i = 0; i < r.citizens; i++) {
            CitizenBody bb = body(i);
            if (bb != null && d.role[i] == 0 && bb.distanceToSqr(mx, y, mz) < 14 * 14) {
                shout(i, "cart_cry", null, 50);
                break;
            }
        }
        news("cart", CityPlan.QUAY_D);
        return "runaway cart #" + id + " on the Quay: " + n + " crates across edge " + best;
    }

    private void stepCart(int k, CitizensData.Event e, long now) {
        int[] dd = e.data();
        if (e.phase() == P_DONE) return;
        if (now % 100 == 0) {
            // each porter standing at the spill carries one crate off
            for (int i = 0; i < r.citizens; i++) {
                if (d.event[i] != e.id() || d.role[i] != Roles.CLEAR || d.state[i] != STAND) continue;
                for (int t = 0; t < 10; t++) {
                    if (dd[3 + t * 3] == Integer.MIN_VALUE) continue;
                    BlockPos p = new BlockPos(dd[3 + t * 3], dd[4 + t * 3], dd[5 + t * 3]);
                    if (!c.level.isLoaded(p)) break;
                    if (c.level.getBlockState(p).is(CitizensContent.CARGO_CRATE.get())) {
                        c.level.removeBlock(p, false);
                        c.level.playSound(null, p, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6f, 1.2f);
                    }
                    dd[3 + t * 3] = Integer.MIN_VALUE;
                    dd[1]--;
                    break;
                }
            }
        }
        if (dd[1] <= 0 || now - e.since() > 4000) {
            for (int t = 0; t < 10; t++) {
                if (dd[3 + t * 3] == Integer.MIN_VALUE) continue;
                BlockPos p = new BlockPos(dd[3 + t * 3], dd[4 + t * 3], dd[5 + t * 3]);
                if (c.level.isLoaded(p) && c.level.getBlockState(p).is(CitizensContent.CARGO_CRATE.get())) c.level.removeBlock(p, false);
            }
            c.net.setOff(dd[0], false);
            finish(e, now);
            return;
        }
        d.setDirty();
    }

    /** A player broke a spilled crate (CitizensModule lets them): one fewer for the porters, and they saw. */
    boolean clearCrate(ServerPlayer p, BlockPos pos) {
        for (CitizensData.Event e : d.events) {
            if (e.kind() != CART || e.phase() == P_DONE) continue;
            int[] dd = e.data();
            for (int t = 0; t < 10; t++) {
                if (dd[3 + t * 3] == pos.getX() && dd[4 + t * 3] == pos.getY() && dd[5 + t * 3] == pos.getZ()) {
                    dd[3 + t * 3] = Integer.MIN_VALUE;
                    dd[1]--;
                    dd[2]++;
                    c.level.destroyBlock(pos, false, p);
                    if (dd[2] == 3) {
                        c.recognition.witness(p, CLEARED_CART, pos, 2);
                        for (int i = 0; i < r.citizens; i++) if (d.event[i] == e.id() && body(i) != null) {
                            CitizenBody b = body(i);
                            String line = Lines.get().pick(CitizenWork.mixHash(i + pos.asLong()), c.tod(), "event.cart_thanks");
                            CitizensNet.say(p, b, Lines.fill(line, p.getName().getString(), "", "", 0), 80, 2);
                            break;
                        }
                    }
                    playerHelped++;
                    put(e);
                    return true;
                }
            }
        }
        return false;
    }

    // ---- gate tremor ---------------------------------------------------------------------------------------------------

    /** data: 0 gate index (0 High, 1 Dawn, 2 Mill), 1 player helped, 2.. watch records (3), 5 x, 6 z; the gate's id is in news */
    private final java.util.Map<Integer, String> gateIds = new java.util.HashMap<>();

    private @Nullable String tremor(@Nullable ServerPlayer near, long now) {
        ServerPlayer hunter = near;
        if (hunter == null) {
            for (ServerPlayer p : c.level.players()) if (Progression.rank(p).ordinal() >= Rank.D.ordinal() && inCity(p)) hunter = p;
        }
        if (hunter == null || Progression.rank(hunter).ordinal() < Rank.D.ordinal() && near == null) return null;
        long seed = CitizenWork.mixHash(now * 3 + 23);
        int g = (int) Math.floorMod(seed, 3L);
        // outside the gate along its road, in the fields
        double ang = switch (g) {
            case 0 -> -Math.PI / 2;
            case 1 -> 0;
            default -> Math.PI / 2;
        };
        double dist = 270 + Math.floorMod(seed >>> 8, 30L);
        int x = l.cx + (int) Math.round(Math.cos(ang) * dist) + (int) Math.floorMod(seed >>> 16, 13L) - 6;
        int z = l.cz + (int) Math.round(Math.sin(ang) * dist) + (int) Math.floorMod(seed >>> 24, 13L) - 6;
        int y = c.level.isLoaded(new BlockPos(x, 0, z)) ? c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : l.streetY + 1;
        Optional<String> gate = GateHooks.openWandering(hunter, new BlockPos(x, y, z), 1, 20 * 60 * 12);
        if (gate.isEmpty()) return null;
        int id = newId();
        gateIds.put(id, gate.get());
        int[] data = {g, 0, -1, -1, -1, x, z};
        // the Watch go to that gate
        int gateNode = c.net.nearest(l.cx + Math.cos(ang) * 226, l.cz + Math.sin(ang) * 226, true);
        List<Integer> watch = nearestWatch(c.net.x[gateNode], c.net.z[gateNode], 3, 400);
        for (int w = 0; w < watch.size(); w++) {
            data[2 + w] = watch.get(w);
            send(watch.get(w), gateNode, Roles.GATE_GUARD, id);
        }
        put(new CitizensData.Event(id, TREMOR, P_GO, now, now + 20L * 60 * 14, data));
        c.work.alarm(c.net.x[gateNode], c.net.z[gateNode], 24);
        for (ServerPlayer p : c.level.players()) {
            if (!inCity(p)) continue;
            c.level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.AMBIENT, 0.6f, 0.5f);
            p.sendSystemMessage(Component.literal("The ground shudders. Bells ring at the " + gateNameOf(g) + ".").withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
        news("tremor", gateNameOf(g));
        return "gate tremor #" + id + " past the " + gateNameOf(g) + " (gate " + gate.get() + ")";
    }

    static String gateNameOf(int g) {
        return switch (g) {
            case 0 -> "High Gate";
            case 1 -> "Dawn Gate";
            default -> "Mill Gate";
        };
    }

    private boolean inCity(ServerPlayer p) {
        return dev.awaken.core.api.CityHooks.inCity(p.level(), p.blockPosition());
    }

    private void stepTremor(int k, CitizensData.Event e, long now) {
        if (now % 100 != 0) return;
        int[] dd = e.data();
        String gid = gateIds.get(e.id());
        Optional<GateHooks.GateView> v = gid == null ? Optional.empty() : GateHooks.get(c.level, gid);
        String state = v.map(GateHooks.GateView::state).orElse("RELEASED");
        if (state.equals("CLEARED")) {
            // closed by a hunter: the Watch on the gate saw it done
            List<Integer> guards = new ArrayList<>();
            for (int w = 2; w <= 4; w++) if (dd[w] >= 0) guards.add(dd[w]);
            for (ServerPlayer p : c.level.players()) {
                if (p.distanceToSqr(dd[5], p.getY(), dd[6]) < 96 * 96) {
                    c.recognition.credit(p, guards, CLOSED_GATE, 6);
                    about = p.getName().getString();
                    dd[1] = 1;
                }
            }
            if (!guards.isEmpty()) shout(guards.getFirst(), "tremor_cleared", null, 80);
            finish(e, now);
        } else if (state.equals("RELEASED") || state.equals("BROKEN") || now > e.until()) {
            // held by the Watch; a field lost for a while
            scorchField(dd[5], dd[6], now);
            int any = dd[2];
            if (any >= 0) shout(any, "tremor_held", null, 80);
            finish(e, now);
        }
    }

    private void scorchField(int x, int z, long now) {
        int y0 = c.level.isLoaded(new BlockPos(x, 0, z)) ? c.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : -999;
        if (y0 == -999 || !c.work.edits) return;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos top = new BlockPos(x + dx, y0, z + dz);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos p = top.above(dy);
                BlockState s = c.level.getBlockState(p);
                if (s.is(Blocks.FARMLAND) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT)) {
                    c.work.addRepair(p.immutable(), s, now + 3L * PlayDays.DAY);
                    c.level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                    if (!c.level.getBlockState(p.above()).isAir() && c.level.getBlockState(p.above()).canBeReplaced())
                        c.level.removeBlock(p.above(), false);
                    break;
                }
            }
        }
    }

    // ---- stepping and ending ---------------------------------------------------------------------------------------

    private void step(int k, long now, int tod) {
        if (k >= d.events.size()) return;
        CitizensData.Event e = d.events.get(k);
        if (e.phase() == P_DONE) {
            d.events.remove(k);
            d.setDirty();
            return;
        }
        if (now > e.since() + MAX_TICKS + 2000) {
            // nothing hangs: whatever this was, it's over
            finish(e, now);
            return;
        }
        switch (e.kind()) {
            case BURGLARY -> stepBurglary(k, e, now);
            case PICKPOCKET -> stepPickpocket(k, e, now);
            case FIRE -> stepFire(k, e, now);
            case CART -> stepCart(k, e, now);
            case RUSH -> stepRush(k, e, now);
            case INFIRMARY -> {
                if (now >= e.until()) finish(e, now);
            }
            default -> stepTremor(k, e, now);
        }
    }

    private void finish(CitizensData.Event e, long now) {
        releaseAll(e.id());
        gateIds.remove(e.id());
        put(phase(e, P_DONE, now, now));
        resolved++;
        Awaken.LOG.info("[citizens] event end: {} #{} after {} ticks", KINDS.get(e.kind()), e.id(), now - e.since());
    }

    void roleArrived(int i, long now) {
        int role = d.role[i];
        if (role == Roles.THIEF_FLEE || role == Roles.WATCH_CHASE) return; // the steps decide
        if (role == Roles.WATCH_ESCORT || role == Roles.CLEAR || role == Roles.GATE_GUARD || role == Roles.BUCKET || role == Roles.FLEE
                || role == Roles.ATTEND) d.state[i] = STAND;
    }

    // ---- what event bodies show ----------------------------------------------------------------------------------------

    void show(CitizenBody b, int i, long now) {
        int role = d.role[i];
        Act act = Act.NONE;
        int main = Props.NONE, off = Props.NONE;
        boolean walking = d.state[i] == WALK;
        switch (role) {
            case Roles.THIEF_SNEAK -> act = Act.NONE;
            case Roles.PICKPOCKET -> act = walking ? Act.NONE : Act.LOOK_AROUND;
            case Roles.THIEF_FLEE -> {
                act = Act.THIEF_RUN;
                main = bagDropped(i) ? Props.NONE : Props.BAG;
            }
            case Roles.THIEF_CAUGHT -> act = walking ? Act.HURT : Act.SURRENDER;
            case Roles.WATCH_CHASE, Roles.WATCH_ESCORT -> {
                act = Act.HALBERD;
                main = Props.HALBERD;
            }
            case Roles.GATE_GUARD -> {
                act = walking ? Act.HALBERD : Act.HALBERD_STAND;
                main = Props.HALBERD;
            }
            case Roles.SHOUT -> act = Act.POINT_SHOUT;
            case Roles.BUCKET -> {
                act = walking ? Act.NONE : Act.BUCKET_PASS;
                main = Props.BUCKET;
            }
            case Roles.TRAPPED -> act = Act.TRAPPED;
            case Roles.FLEE -> act = walking ? Act.RUN : Act.LOOK_AROUND;
            case Roles.ATTEND -> act = r.namedIndex("sun") == i ? Act.TALK : Act.HANDS_BEHIND;
            case Roles.CLEAR -> act = walking ? Act.NONE : Act.SET_DOWN;
            default -> {}
        }
        b.setAct(act);
        b.hold(EquipmentSlot.MAINHAND, main);
        b.hold(EquipmentSlot.OFFHAND, off);
        b.setFlag(CitizenBody.F_CRATE, false);
    }

    private boolean bagDropped(int thief) {
        for (CitizensData.Event e : d.events) {
            if (e.id() != d.event[thief]) continue;
            return e.kind() == BURGLARY ? e.data()[7] != 0 : e.kind() == PICKPOCKET && e.data()[5] != 0;
        }
        return false;
    }

    // ---- players and thieves -------------------------------------------------------------------------------------------

    /** A player hit a body: a thief drops the bag at the first hit and gives up at the second. True when it was a thief. */
    boolean hitByPlayer(ServerPlayer p, CitizenBody b, int rec) {
        if (rec < r.citizens) return false;
        int role = d.role[rec];
        if (!Roles.thief(role)) return false;
        for (CitizensData.Event e : d.events) {
            if (e.id() != d.event[rec] || e.phase() == P_DONE) continue;
            int[] dd = e.data();
            boolean burglary = e.kind() == BURGLARY;
            int dropIdx = burglary ? 7 : 5;
            if (dd[dropIdx] == 0) {
                dd[dropIdx] = 1;
                ItemStack bag = CitizensContent.STOLEN_BAG.get().getDefaultInstance();
                CompoundTag tag = new CompoundTag();
                tag.putInt("event", e.id());
                tag.putInt("kind", e.kind());
                tag.putInt("owner", burglary ? dd[0] : r.stalls.get(dd[0]).keeper());
                bag.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                b.spawnAtLocation(c.level, bag);
                put(e);
            } else if (e.phase() == P_FLEE || e.phase() == P_GO) {
                if (burglary) caught(e, c.level.getGameTime(), p);
                else {
                    dd[4] = 1;
                    send(rec, d.at[rec], Roles.THIEF_CAUGHT, e.id());
                    d.state[rec] = STAND;
                    put(phase(e, P_CAUGHT, c.level.getGameTime(), e.until()));
                }
                shout(rec, "thief_caught", p, 60);
                c.recognition.witness(p, CAUGHT_THIEF, b.blockPosition(), 4);
                about = p.getName().getString();
                playerHelped++;
            }
            return true;
        }
        return true;
    }

    /** An actor knocked down to its last heart: it gives up where it fell. */
    void actorDown(CitizenBody b, int rec) {
        b.heal(10f);
    }

    /** Players carrying a stolen bag near its owners' door give it back (each second). */
    private void returns(long now) {
        for (ServerPlayer p : c.level.players()) {
            var inv = p.getInventory();
            for (int s = 0; s < inv.getContainerSize(); s++) {
                ItemStack st = inv.getItem(s);
                if (!st.is(CitizensContent.STOLEN_BAG.get())) continue;
                CustomData cd = st.get(DataComponents.CUSTOM_DATA);
                if (cd == null) continue;
                CompoundTag tag = cd.copyTag();
                int owner = tag.getIntOr("owner", -1), kind = tag.getIntOr("kind", 0);
                if (owner < 0 || owner >= r.places) continue;
                l.placeWorld(owner, l.tmp);
                if (Math.hypot(l.tmp[0] - p.getX(), l.tmp[2] - p.getZ()) > 4.5 || Math.abs(l.tmp[1] - p.getY()) > 4) continue;
                st.shrink(1);
                giveBack(p, owner, kind);
                return;
            }
        }
    }

    private void giveBack(ServerPlayer p, int ownerPlace, int kind) {
        // the household (or the stallkeeper) remembers; a stallkeeper gives something for the trouble
        List<Integer> who = new ArrayList<>();
        for (Roster.Household h : r.households) if (h.home() == ownerPlace) for (int m : h.members()) who.add(m);
        for (int i = 0; i < r.citizens; i++) if (d.place[i] == ownerPlace && !who.contains(i)) who.add(i);
        CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
        for (int i : who) {
            m.know[i] = Math.min(100, m.know[i] + 45);
            m.regard[i] = Math.min(100, m.regard[i] + 40);
        }
        d.setDirty();
        CitizenBody speaker = null;
        for (int i : who) if (body(i) != null) speaker = body(i);
        String key = kind == BURGLARY ? "burglary_thanks" : "pickpocket_thanks";
        if (speaker != null) {
            String line = Lines.get().pick(CitizenWork.mixHash(ownerPlace + c.level.getGameTime()), c.tod(), "event." + key);
            CitizensNet.say(p, speaker, Lines.fill(line, p.getName().getString(), "", "", 0), 100, 2);
            speaker.gesture(Act.CHEER, 24);
        }
        c.recognition.witness(p, RETURNED, p.blockPosition(), 5);
        if (kind == PICKPOCKET) p.getInventory().add(new ItemStack(Items.BREAD, 3));
        about = p.getName().getString();
        Awaken.LOG.info("[citizens] {} returned stolen goods to place {} ({} remember)", p.getName().getString(), ownerPlace, who.size());
    }

    // ---- news ----------------------------------------------------------------------------------------------------------

    private void news(String key, int district) {
        news(key, Recognition.districtName(district));
    }

    private void news(String key, String where) {
        news.add(new String[]{key, where, Long.toString(PlayDays.current(c.server))});
        while (news.size() > 6) news.removeFirst();
    }

    /** The crier's line: today's news first, then about a hunter the city knows, else the city's own. */
    String news(long seed, int tod) {
        Lines lines = Lines.get();
        long day = PlayDays.current(c.server);
        int pick = (int) Math.floorMod(CitizenWork.mixHash(seed), 10L);
        if (!news.isEmpty() && pick < 5) {
            String[] n = news.get((int) Math.floorMod(seed >>> 7, (long) news.size()));
            if (Long.parseLong(n[2]) >= day - 1) {
                String line = lines.pick(seed, tod, "news." + n[0]);
                return line.replace("{district}", n[1]).replace("{gate}", n[1]);
            }
        }
        if (!about.isEmpty() && pick < 8) return lines.pick(seed, tod, "news.about");
        return lines.pick(seed, tod, "news.city");
    }

    String aboutName() {
        return about.isEmpty() ? "a hunter" : about;
    }

    /** Where an event is happening (for the tour's framing). */
    boolean where(CitizensData.Event e, double[] out) {
        int[] dd = e.data();
        int rec = switch (e.kind()) {
            case BURGLARY, PICKPOCKET -> dd[1];
            case FIRE -> -1;
            default -> -1;
        };
        if (rec >= 0) {
            CitizenBody b = body(rec);
            if (b != null) {
                out[0] = b.getX();
                out[1] = b.getY() + 1.2;
                out[2] = b.getZ();
                return true;
            }
            if (l.position(rec, out)) {
                out[1] += 1.2;
                return true;
            }
            if (e.kind() == BURGLARY) {
                l.placeWorld(dd[0], out);
                return true;
            }
        }
        if (e.kind() == FIRE) {
            // the flames on the roof if any, else over the door
            int door = dd[7];
            if (dd[8] != Integer.MIN_VALUE) {
                out[0] = dd[8] + 0.5;
                out[1] = dd[9];
                out[2] = dd[10] + 0.5;
            } else {
                out[0] = c.net.x[door];
                out[1] = c.net.y[door] + 4;
                out[2] = c.net.z[door];
            }
            return true;
        }
        if (e.kind() == CART) {
            int a = c.net.ea[dd[0]];
            out[0] = c.net.x[a];
            out[1] = c.net.y[a] + 1.5;
            out[2] = c.net.z[a];
            return true;
        }
        out[0] = dd[5];
        out[1] = l.streetY + 4;
        out[2] = dd[6];
        return true;
    }
}
