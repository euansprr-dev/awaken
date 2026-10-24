package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.city.City;
import dev.awaken.city.CityData;
import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Carillon's people running in one world (the overworld of a world with the city): the ledger and its saved data, the
 * crowd of bodies near players, the work that changes the city, its events and what it remembers of players. Made on
 * the first server tick after the city exists; gone when the server stops.
 */
final class Citizens {
    static @Nullable Citizens active;
    private static boolean tried;

    final MinecraftServer server;
    final ServerLevel level;
    final Roster roster;
    final StreetNet net;
    final CitizensData data;
    final Ledger ledger;
    final Crowd crowd;
    final CitizenWork work;
    final CityEvents events;
    final Recognition recognition;
    /** Spectators get bodies round them too (tours fly in spectator). */
    final boolean spectatorsEmbody = true;

    private long ledgerNs, managerNs, gcBefore, startGame;
    private static final List<java.lang.management.GarbageCollectorMXBean> GCS =
            java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();

    /** A clock of our own for tests (time of day = base + game ticks since); negative: the world's clock. */
    long clockBase = -1, clockFrom;

    Citizens(ServerLevel level, Roster roster, StreetNet net, CitizensData data, int cx, int cz, int streetY) {
        this.server = level.getServer();
        this.level = level;
        this.roster = roster;
        this.net = net;
        this.data = data;
        long t0 = System.nanoTime();
        ledger = new Ledger(roster, net, data, cx, cz, streetY, level.getGameTime(), clock());
        crowd = new Crowd(this, level, ledger);
        work = new CitizenWork(this);
        events = new CityEvents(this);
        recognition = new Recognition(this);
        ledger.work = work;
        startGame = level.getGameTime();
        Awaken.LOG.info(String.format(Locale.ROOT, "[citizens] Carillon's %d citizens up in %.1f ms (%d inside, %d bakes)", roster.citizens,
                (System.nanoTime() - t0) / 1e6, ledger.count(CitizensData.INSIDE), net.bakes));
    }

    long clock() {
        return clockBase >= 0 ? clockBase + (level.getGameTime() - clockFrom) : level.getOverworldClockTime();
    }

    void setClock(long base) {
        clockBase = base;
        clockFrom = level.getGameTime();
    }

    int tod() {
        return Ledger.tod(clock());
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------

    /** Starts the city's people if this world has Carillon (once per server start). */
    static void maybeStart(MinecraftServer server) {
        if (active != null || tried) return;
        CityLayout layout = City.layout();
        if (layout == null) return;
        tried = true;
        Roster roster = Roster.get();
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
            net = StreetNet.of(cd.street, roster.edgeLane);
        } else {
            net = StreetNet.of(CityPlan.get(), layout.cx, layout.cz, layout.streetY, roster.edgeLane);
        }
        active = new Citizens(level, roster, net, CitizensData.get(server), layout.cx, layout.cz, layout.streetY);
    }

    static void stop() {
        Citizens c = active;
        if (c != null) {
            c.crowd.despawnAll();
            c.work.stop();
        }
        active = null;
        tried = false;
    }

    // ---- server tick --------------------------------------------------------------------------------------------------

    static void onTickPre(MinecraftServer server) {
        Crowd.bodyNanos = 0;
        maybeStart(server);
        Citizens c = active;
        if (c == null) return;
        c.gcBefore = gcCount();
        c.pre();
    }

    static void onTickPost(MinecraftServer server) {
        Citizens c = active;
        if (c == null) return;
        long mspt = server.getTickTimesNanos()[server.getTickCount() % 100];
        c.crowd.stats.record(c.ledgerNs, Crowd.bodyNanos + c.managerNs, c.managerNs, mspt, c.crowd.bodies.size(), 0, gcCount() != c.gcBefore);
        long now = c.level.getGameTime();
        if ((now - c.startGame) % 1200 == 1199) Awaken.LOG.info("[citizens] {}", c.line());
    }

    private void pre() {
        long now = level.getGameTime();
        long clk = clock();
        int tod = Ledger.tod(clk);
        net.newTick();
        long t0 = System.nanoTime();
        ledger.tick(now, clk);
        ledgerNs = System.nanoTime() - t0;
        t0 = System.nanoTime();
        if (now % 10 == 0) crowd.rebalance();
        work.tick(now, tod);
        events.tick(now, tod);
        recognition.tick(now, tod);
        managerNs = System.nanoTime() - t0;
    }

    private static long gcCount() {
        long n = 0;
        for (int i = 0; i < GCS.size(); i++) n += GCS.get(i).getCollectionCount();
        return n;
    }

    String line() {
        return String.format(Locale.ROOT, "%s | bodies=%d inview=%d inside=%d | arrivals=%d late=%d early=%d waits=%d noRoute=%d overflow=%d | "
                        + "paths req=%d fail=%d timeouts=%d unstick=%d stuck=%d rescues=%d | columns=%d bakes=%d (%.1f ms) | events=%d",
                crowd.stats.ringSummary(), crowd.bodies.size(), crowd.inView(), ledger.count(CitizensData.INSIDE), ledger.arrivals,
                ledger.late, ledger.early, ledger.waits, ledger.noRoute, ledger.overflow, crowd.pathRequests, crowd.pathFails,
                crowd.tokenTimeouts, crowd.unstickPaths, crowd.stuck, crowd.rescues, net.columns(), net.bakes, net.bakeNanos / 1e6,
                data.events.size());
    }

    // ---- hurt, never killed ----------------------------------------------------------------------------------------

    /** A body took a hit: who did it matters (a bad deed if a player hit a citizen; the thief is caught). */
    void hurt(CitizenBody b, int rec, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer p) {
            if (events.hitByPlayer(p, b, rec)) return;
            if (roster.isCitizen(rec)) recognition.witness(p, Recognition.HIT_CITIZEN, b.blockPosition(), -3);
        }
    }

    /** At the last heart: the citizen stays up, hurt, and goes to the infirmary to recover (SPEC §16.0.7). */
    void knockedDown(CitizenBody b, int rec) {
        if (!roster.isCitizen(rec)) {
            events.actorDown(b, rec);
            return;
        }
        long now = level.getGameTime();
        if (data.hurtUntil[rec] > now) return;
        data.hurtUntil[rec] = now + 6000;
        b.setFlag(CitizenBody.F_HURT, true);
        b.heal(4f);
        data.setDirty();
    }
}
