package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.RosterSource;
import dev.awaken.citizens.api.SettlementSpec;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.city.City;
import dev.awaken.city.CityData;
import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The people engine's settlements on this server (SPEC §16.5, §28.4): one registry, many places. Carillon is settlement
 * 0, started on the first server tick after the city exists (from its jar roster and plan, the plan hash checked);
 * the others are registered through {@code citizens.api.Settlements}. Every tick runs each settlement's ledger, then
 * the one crowd that embodies people for all of them (nearest first, within shared caps), then each settlement's work,
 * events and memory. A settlement other than Carillon sleeps while no player is near it and wakes where the hour
 * wants its people. Overworld only; everything is dropped when the server stops.
 */
final class Citizens {
    private Citizens() {}

    /** Every settlement running; Carillon first when it runs. */
    private static final List<Settlement> LIVE = new ArrayList<>();
    private static final List<Settlement> VIEW = Collections.unmodifiableList(LIVE);
    private static @Nullable Settlement carillon;
    private static @Nullable Crowd crowd;
    private static boolean tried;

    private static long ledgerNs, managerNs, gcBefore, startGame = -1;
    private static final List<java.lang.management.GarbageCollectorMXBean> GCS =
            java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();

    // ---- lookups ------------------------------------------------------------------------------------------------------

    static @Nullable Settlement carillon() {
        return carillon;
    }

    static List<Settlement> live() {
        return VIEW;
    }

    static @Nullable Settlement byId(int id) {
        for (int k = 0; k < LIVE.size(); k++) if (LIVE.get(k).id == id) return LIVE.get(k);
        return null;
    }

    /** The settlement a position belongs to: the smallest running one that contains it. */
    static @Nullable Settlement at(Level level, BlockPos pos) {
        Settlement best = null;
        for (int k = 0; k < LIVE.size(); k++) {
            Settlement s = LIVE.get(k);
            if (s.level != level || !s.site.inside(pos.getX(), pos.getZ())) continue;
            if (best == null || s.radius < best.radius) best = s;
        }
        return best;
    }

    /**
     * Tests in the shared GameTest world: only these players wake settlements and get bodies (the world also holds every
     * earlier test's players, which would otherwise decide for them). Null: every player in the level.
     */
    static @Nullable List<ServerPlayer> onlyPlayers;

    /** The players who wake settlements and get bodies round them. */
    static List<ServerPlayer> players(ServerLevel level) {
        List<ServerPlayer> only = onlyPlayers;
        return only != null ? only : level.players();
    }

    /** The world's one pool of bodies (made with the first settlement). */
    static Crowd crowd(ServerLevel level) {
        if (crowd == null || crowd.level != level) crowd = new Crowd(level);
        return crowd;
    }

    // ---- registration ---------------------------------------------------------------------------------------------

    /** Runs a settlement from now (Carillon first in the list). */
    static void add(Settlement s) {
        if (LIVE.contains(s)) return;
        if (s.id == Settlements.CARILLON) {
            if (carillon != null) remove(carillon);
            carillon = s;
            LIVE.addFirst(s);
        } else LIVE.add(s);
        s.live = true;
        if (startGame < 0) startGame = s.level.getGameTime();
    }

    /** Stops running one (its bodies go; its saved state stays in the world's file). */
    static void remove(Settlement s) {
        if (!LIVE.remove(s)) return;
        if (s == carillon) carillon = null;
        s.stop();
    }

    /**
     * Tests: puts a settlement in Carillon's place (or none), handing back the one that was there without stopping it
     * (a test borrows Carillon's slot for a plan placed at its own ground, then gives it back).
     */
    static @Nullable Settlement swapCarillon(@Nullable Settlement s) {
        Settlement before = carillon;
        if (before != null) LIVE.remove(before); // not stopped: it runs again when it's swapped back
        carillon = null;
        if (s != null) add(s);
        return before;
    }

    /** Settlements.register: builds the roster and ground now, finds its saved state by key; null when it can't run. */
    static @Nullable Settlement register(ServerLevel level, SettlementSpec spec) {
        if (level != level.getServer().overworld()) return null;
        for (int k = 0; k < LIVE.size(); k++) if (LIVE.get(k).key.equals(spec.key())) return LIVE.get(k);
        RosterSource.Built built;
        try {
            built = spec.source().build();
        } catch (RuntimeException e) {
            Awaken.LOG.error("[citizens] settlement {}: its roster source failed", spec.key(), e);
            return null;
        }
        Roster r = built.roster();
        if (r == null || built.graph() == null) {
            Awaken.LOG.warn("[citizens] settlement {}: no roster or no street graph", spec.key());
            return null;
        }
        BlockPos o = spec.origin();
        StreetNet net = StreetNet.of(built.graph(), o.getX(), o.getY(), o.getZ());
        String bad = check(r, net);
        if (bad != null) {
            Awaken.LOG.error("[citizens] settlement {}: {}", spec.key(), bad);
            return null;
        }
        CitizensSave save = CitizensSave.get(level.getServer());
        int id = save.idFor(spec.key());
        BuiltSite site = new BuiltSite(spec.name(), o.getX(), o.getY(), o.getZ(), spec.radius(), built, r, net);
        Settlement s = new Settlement(level, id, spec.key(), spec.name(), site, spec.radius(), spec.plannedEvents(), r, net, save.data(spec.key(), id));
        // its saved day may be hours old (it was last run in another session, or before it slept): seated by the hour
        if (!s.ledger.fresh) s.wake(level.getGameTime());
        add(s);
        for (ServerPlayer p : level.players()) s.work.sendLamps(p);
        return s;
    }

    /** A built roster's indices against its graph (a builder's slip fails here, not mid-tick). */
    static @Nullable String check(Roster r, StreetNet net) {
        for (int p = 0; p < r.places; p++) if (r.pNode[p] < 0 || r.pNode[p] >= net.n) return "place " + p + " stands on node " + r.pNode[p] + " of " + net.n;
        for (int e = 0; e < net.m; e++) if (net.ea[e] < 0 || net.ea[e] >= net.n || net.eb[e] < 0 || net.eb[e] >= net.n) return "edge " + e + " joins no node";
        for (int i = 0; i < r.citizens; i++) {
            if (r.home[i] < 0 || r.home[i] >= r.places) return "citizen " + i + " has no home place";
            for (int k = 0; k < r.segCount[i]; k++) {
                int seg = r.segBase[i] + k, t = r.segTarget[seg];
                boolean route = r.segAct[seg] == Roster.ROUND || r.segAct[seg] == Roster.WANDER;
                if (route ? t < 0 || t >= r.routePlaces.length : t >= r.places || t < 0 && r.segAct[seg] != Roster.HOME)
                    return "citizen " + i + "'s segment " + k + " targets " + t;
            }
        }
        for (int[] route : r.routePlaces) for (int p : route) if (p < 0 || p >= r.places) return "a route names place " + p;
        return null;
    }

    static boolean unregister(int id) {
        Settlement s = byId(id);
        if (s == null || s.id == Settlements.CARILLON) return false;
        remove(s);
        return true;
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------

    /** Starts the city's people if this world has Carillon (once per server start). */
    static void maybeStart(MinecraftServer server) {
        if (carillon != null || tried) return;
        CityLayout layout = City.layout();
        if (layout == null) return;
        tried = true;
        Roster roster = RosterSource.jar().build().roster();
        if (roster == null) return;
        ServerLevel level = server.overworld();
        CityData cd = CityData.peek(server);
        StreetNet net;
        if (cd != null && cd.built()) {
            if (!cd.planHash.equals(roster.planSha256)) {
                Awaken.LOG.warn("[citizens] this world's city was built from plan {} but the roster is for {}: no citizens",
                        cd.planHash.substring(0, Math.min(12, cd.planHash.length())), roster.planSha256.substring(0, Math.min(12, roster.planSha256.length())));
                return;
            }
            net = StreetNet.of(cd.street, roster.edgeLane).withWall(roster, layout.cx, layout.cz, layout.streetY);
        } else {
            net = StreetNet.of(CityPlan.get(), layout.cx, layout.cz, layout.streetY, roster.edgeLane).withWall(roster, layout.cx, layout.cz, layout.streetY);
        }
        CitizensData data = CitizensSave.get(server).data(Settlements.CARILLON_KEY, Settlements.CARILLON);
        add(Settlement.carillon(level, roster, net, data, layout.cx, layout.cz, layout.streetY));
        // a new world's first player logs in before the city's people start (the pregen runs first): send what login
        // would have (the site, the smoke and lamp lists), or the client's animals, festoons and lamp light never begin
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSites(p);
    }

    /** What a player's client draws for itself: Carillon's site and emitters first (they replace), then the others' lamps. */
    static void sendSites(ServerPlayer p) {
        if (carillon != null) carillon.work.sendChimneys(p);
        for (int k = 0; k < LIVE.size(); k++) if (LIVE.get(k) != carillon) LIVE.get(k).work.sendLamps(p);
    }

    static void stop() {
        for (Settlement s : List.copyOf(LIVE)) s.stop();
        LIVE.clear();
        carillon = null;
        crowd = null;
        onlyPlayers = null;
        tried = false;
        startGame = -1;
    }

    // ---- server tick --------------------------------------------------------------------------------------------------

    static void onTickPre(MinecraftServer server) {
        Crowd.bodyNanos = 0;
        maybeStart(server);
        if (LIVE.isEmpty()) return;
        gcBefore = gcCount();
        StreetNet.newServerTick();
        long now = server.overworld().getGameTime();
        if (now % 20 == 5) wakeOrSleep(now);
        ledgerNs = 0;
        for (int k = 0; k < LIVE.size(); k++) {
            Settlement s = LIVE.get(k);
            if (!s.awake) continue;
            s.tickLedger(s.level.getGameTime());
            ledgerNs += s.ledgerNs;
        }
        long t0 = System.nanoTime();
        Crowd c = crowd;
        if (c != null && c.level.getGameTime() % 10 == 0) c.rebalance();
        managerNs = System.nanoTime() - t0;
        for (int k = 0; k < LIVE.size(); k++) {
            Settlement s = LIVE.get(k);
            if (!s.awake) continue;
            s.tickRest(s.level.getGameTime());
            managerNs += s.managerNs;
        }
    }

    static void onTickPost(MinecraftServer server) {
        Crowd c = crowd;
        if (LIVE.isEmpty() || c == null) return;
        long mspt = server.getTickTimesNanos()[server.getTickCount() % 100];
        c.stats.record(ledgerNs, Crowd.bodyNanos + managerNs, managerNs, mspt, c.bodies.size(), 0, gcCount() != gcBefore);
        long now = c.level.getGameTime();
        if ((now - startGame) % 1200 == 1199) Awaken.LOG.info("[citizens] {}", line());
    }

    /** Settlements other than Carillon tick only while someone is near (radius + WAKE; asleep again 64 further out). */
    private static void wakeOrSleep(long now) {
        for (int k = 0; k < LIVE.size(); k++) {
            Settlement s = LIVE.get(k);
            if (s.site.carillon()) continue;
            double reach = s.radius + Settlement.WAKE + (s.awake ? 64 : 0);
            boolean near = false;
            for (ServerPlayer p : players(s.level)) {
                double dx = p.getX() - s.site.cx(), dz = p.getZ() - s.site.cz();
                if (dx * dx + dz * dz < reach * reach) {
                    near = true;
                    break;
                }
            }
            if (near && !s.awake) s.wake(now);
            else if (!near && s.awake) s.sleep();
        }
    }

    private static long gcCount() {
        long n = 0;
        for (int i = 0; i < GCS.size(); i++) n += GCS.get(i).getCollectionCount();
        return n;
    }

    /** The minute's log line: Carillon's (as it always was), with the other settlements' totals when there are any. */
    static String line() {
        Settlement first = carillon != null ? carillon : LIVE.getFirst();
        if (LIVE.size() == 1) return first.line();
        int records = 0, awake = 0;
        for (Settlement s : LIVE) {
            records += s.ledger.n;
            if (s.awake) awake++;
        }
        return first.line() + String.format(Locale.ROOT, " | settlements=%d awake=%d records=%d", LIVE.size(), awake, records);
    }
}
