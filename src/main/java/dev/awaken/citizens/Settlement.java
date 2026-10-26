package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One settlement's people running in the overworld (SPEC §16.5, §28.4): its ledger and saved state, its street graph,
 * the work that changes its streets, its events and what it remembers of players, on its own {@link Site}. Carillon is
 * settlement 0 (made by {@link Citizens} on the first server tick after the city exists); W3's villages are registered
 * through {@code dev.awaken.citizens.api.Settlements}. Bodies come from the one world pool every settlement shares
 * ({@link Crowd}). Gone when the server stops.
 */
final class Settlement implements dev.awaken.citizens.api.Settlement {
    final int id;
    final String key, name;
    final Site site;
    /** What counts as inside, and how near a player must be for a settlement other than Carillon to wake (+ WAKE). */
    final int radius;
    /** The city's daily events are planned here (Carillon; others when their spec asks). */
    final boolean plannedEvents;
    /** No record of it stands further than this from its origin (the crowd compares its people with players this near). */
    final double reach;

    final MinecraftServer server;
    final ServerLevel level;
    final Roster roster;
    final StreetNet net;
    final CitizensData data;
    final Ledger ledger;
    /** The world's one pool of bodies, shared by every settlement. */
    final Crowd crowd;
    final CitizenWork work;
    final CityEvents events;
    final Recognition recognition;
    final Lanterns lanterns;
    /** Spectators get bodies round them too (tours fly in spectator). */
    final boolean spectatorsEmbody = true;
    /** This settlement's people get bodies (tests that borrow a city's graph turn it off). */
    boolean embody = true;
    /** Ticking its day (Carillon always; others while a player is within radius + WAKE). */
    boolean awake = true;
    /** Registered and running (a body whose settlement stopped goes). */
    boolean live;

    /** How far past its radius a player wakes a settlement; it sleeps again past WAKE + 64. */
    static final int WAKE = 192;

    long ledgerNs, managerNs;

    /** A clock of our own for tests (time of day = base + game ticks since); negative: the world's clock. */
    long clockBase = -1, clockFrom;

    Settlement(ServerLevel level, int id, String key, String name, Site site, int radius, boolean plannedEvents, Roster roster, StreetNet net,
               CitizensData data) {
        this.id = id;
        this.key = key;
        this.name = name;
        this.site = site;
        this.radius = radius;
        this.plannedEvents = plannedEvents;
        this.server = level.getServer();
        this.level = level;
        this.roster = roster;
        this.net = net;
        this.data = data;
        long t0 = System.nanoTime();
        ledger = new Ledger(roster, net, data, site.cx(), site.cz(), site.baseY(), level.getGameTime(), clock(), site.districts());
        crowd = Citizens.crowd(level);
        work = new CitizenWork(this);
        events = new CityEvents(this);
        recognition = new Recognition(this);
        lanterns = new Lanterns(this);
        ledger.work = work;
        // how far from its origin its people can be: its furthest node, and a spot or an event's place beside it
        double far = 0;
        for (int i = 0; i < net.n; i++) far = Math.max(far, Math.hypot(net.x[i] - site.cx(), net.z[i] - site.cz()));
        reach = far + 40;
        if (site.carillon()) {
            Awaken.LOG.info(String.format(Locale.ROOT, "[citizens] Carillon's %d citizens up in %.1f ms (%d inside, %d bakes)", roster.citizens,
                    (System.nanoTime() - t0) / 1e6, ledger.count(CitizensData.INSIDE), net.bakes));
        } else {
            Awaken.LOG.info(String.format(Locale.ROOT, "[citizens] settlement %d %s (%s): %d citizens, %d places, %d nodes up in %.1f ms", id, key, name,
                    roster.citizens, roster.places, net.n, (System.nanoTime() - t0) / 1e6));
        }
    }

    /** Carillon's people on its plan placed at (cx, cz) with the street at streetY (the world's city, or a test's). */
    static Settlement carillon(ServerLevel level, Roster roster, StreetNet net, CitizensData data, int cx, int cz, int streetY) {
        return new Settlement(level, Settlements.CARILLON, Settlements.CARILLON_KEY, "Carillon", new CarillonSite(cx, cz, streetY),
                dev.awaken.city.CityPlan.get().wallRadius, true, roster, net, data);
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

    // ---- server tick (Citizens runs every settlement's ledger, then the crowd, then the rest) ------------------------

    void tickLedger(long now) {
        long t0 = System.nanoTime();
        ledger.tick(now, clock());
        ledgerNs = System.nanoTime() - t0;
    }

    void tickRest(long now) {
        long t0 = System.nanoTime();
        int tod = Ledger.tod(clock());
        work.tick(now, tod);
        events.tick(now, tod);
        recognition.tick(now, tod);
        lanterns.tick(now);
        if (site.carillon()) Soundscape.count(this, now);
        managerNs = System.nanoTime() - t0;
    }

    /** Asleep since no player came near: on waking everyone is where this hour wants them, the streets as the hour has them. */
    void wake(long now) {
        awake = true;
        ledger.reseat(now, clock());
        work.byTime(tod());
        Awaken.LOG.info("[citizens] settlement {} ({}) wakes", id, key);
    }

    void sleep() {
        awake = false;
        crowd.despawnAll(this);
        Awaken.LOG.info("[citizens] settlement {} ({}) sleeps", id, key);
    }

    void stop() {
        live = false;
        lanterns.stop();
        crowd.despawnAll(this);
        work.stop();
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
            if (!roster.isCitizen(rec)) return;
            recognition.witness(p, Recognition.HIT_CITIZEN, b.blockPosition(), -3);
            String line = Lines.get().pick(CitizenWork.mixHash(rec * 31L + level.getGameTime()), tod(), "event.hit");
            CitizensNet.say(p, b, line, 50, 2);
            b.lookAt(p, 80);
        }
    }

    /** At the last heart: the citizen stays up, hurt, and goes to the infirmary (or home) to recover (SPEC §16.0.7). */
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

    // ---- the API's view (citizens.api.Settlement) -------------------------------------------------------------------

    @Override
    public int id() {
        return id;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public BlockPos origin() {
        return new BlockPos(site.cx(), site.baseY(), site.cz());
    }

    @Override
    public boolean inside(BlockPos pos) {
        return site.inside(pos.getX(), pos.getZ());
    }

    @Override
    public int people() {
        return roster.citizens;
    }

    @Override
    public int bodies() {
        int n = 0;
        for (CitizenBody b : ledger.body) if (b != null && !b.isRemoved() && !b.fading()) n++;
        return n;
    }

    @Override
    public boolean awake() {
        return awake;
    }

    @Override
    public int standing(Player player) {
        return recognition.tier(player);
    }

    @Override
    public List<BlockPos> stations(String kind) {
        List<BlockPos> out = new ArrayList<>();
        int k = Arrays.asList(Roster.PLACE_KINDS).indexOf(kind);
        double[] at = new double[3];
        if (k >= 0) {
            for (int p = 0; p < roster.places; p++) {
                if (roster.pKind[p] != k) continue;
                ledger.placeWorld(p, at);
                out.add(BlockPos.containing(at[0], at[1], at[2]));
            }
            return out;
        }
        for (Site.Lot lot : site.lots(kind)) {
            if (lot.doorX() == Integer.MIN_VALUE) continue;
            int x = site.cx() + lot.doorX(), z = site.cz() + lot.doorZ();
            out.add(new BlockPos(x, site.ground(x, z) + 1, z));
        }
        return out;
    }

    @Override
    public String district(BlockPos pos) {
        return site.districtName(site.district(pos.getX(), pos.getZ()));
    }

    @Override
    public int records() {
        return roster.records;
    }

    @Override
    public String job(int rec) {
        return rec >= 0 && roster.isCitizen(rec) ? Roster.JOBS[roster.job[rec]] : "";
    }

    @Override
    public String personName(int rec) {
        return rec >= 0 && roster.isCitizen(rec) ? roster.name(rec) : "";
    }

    @Override
    public int age(int rec) {
        return rec >= 0 && roster.isCitizen(rec) ? roster.age[rec] : 2;
    }

    @Override
    public net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity body(int rec) {
        CitizenBody b = rec >= 0 && rec < ledger.body.length ? ledger.body[rec] : null;
        return b == null || b.isRemoved() ? null : b;
    }

    @Override
    public void say(ServerPlayer to, int rec, String line, int ticks) {
        CitizenBody b = rec >= 0 && rec < ledger.body.length ? ledger.body[rec] : null;
        if (b != null && !b.isRemoved()) {
            CitizensNet.say(to, b, line, ticks, 0);
            b.lookAt(to, 60);
        }
    }

    @Override
    public void acquaint(ServerPlayer p, int amount, int ceiling) {
        CitizensData.Memory m = data.memory(p.getUUID(), p.getName().getString(), true);
        for (int i = 0; i < roster.citizens; i++) if (m.know[i] < ceiling) m.know[i] = Math.min(ceiling, m.know[i] + amount);
        data.setDirty();
    }

    // ---- (SPEC §28.7) troubles and festivals ---------------------------------------------------------------------------

    @Override
    public void stage(int rec, net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 at, float yaw, String act, String prop) {
        events.stage(rec, at, yaw, act, prop);
    }

    @Override
    public void away(int rec, boolean away) {
        events.away(rec, away);
    }

    @Override
    public boolean isAway(int rec) {
        return rec >= 0 && rec < roster.records && data.role[rec] == Roles.AWAY;
    }

    @Override
    public void alarm(BlockPos near, int strokes) {
        work.alarm(near.getX() + 0.5, near.getZ() + 0.5, strokes);
    }

    @Override
    public net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 where(int rec) {
        if (rec < 0 || rec >= roster.records) return null;
        CitizenBody b = ledger.body[rec];
        if (b != null && !b.isRemoved()) return b.position();
        double[] at = new double[3];
        return ledger.position(rec, at) ? new net.minecraft.world.phys.Vec3(at[0], at[1], at[2]) : null;
    }

    @Override
    public Look look(int rec) {
        Roster carillon = Roster.get();
        int k = id == Settlements.CARILLON ? rec : rec >= 0 && rec < roster.records && roster.isCitizen(rec)
                ? Looks.pick(key, rec, roster.job[rec], roster.build[rec], roster.age[rec]) : -1;
        if (carillon == null || k < 0 || k >= carillon.records) return new Look(Awaken.id("textures/entity/townsfolk/oren.png"), false);
        if (!carillon.named[k].isEmpty()) return new Look(Awaken.id("textures/entity/townsfolk/" + carillon.named[k] + ".png"), false);
        String path = k >= carillon.citizens ? String.format(Locale.ROOT, "textures/entity/citizen/t%d.png", k - carillon.citizens)
                : String.format(Locale.ROOT, "textures/entity/citizen/c%03d.png", k);
        return new Look(Awaken.id(path), carillon.build[k] == 1);
    }
}
