package dev.awaken.citizens;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Who has a body (SPEC §16.5, §28.4): one pool for the world, shared by every settlement. Every 10 ticks, nearest first
 * across all of them, at most 40 bodies per player and 96 in the world, spawned within 56 blocks and kept to 64, at
 * most 8 new ones a pass; a village's people never take the city's bodies by being registered, only by being nearer.
 * Bodies fade in on the client and fade out here before they go, and go in and out through real doors (opened and shut
 * behind them). (SPEC §28.11) Nobody appears in front of anyone: a new body comes out of a door, or where no player
 * is within 24 or looking its way with nothing between; nobody is put inside a block. Holds the vanilla path slots (4,
 * or 6 above 60 bodies: shared too) and the per-tick timings.
 */
final class Crowd {
    /** Accumulated by every body's tick between the server tick's Pre and Post. */
    static long bodyNanos;
    static final int SPAWNS_PER_PASS = 8;
    static final double EMBODY = 56, KEEP = 64;
    /** At most this many places are checked for sight each pass (each a ray or two per player near it). */
    static final int APPEAR_CHECKS = 24;
    /** A player who just came (logged in, respawned, teleported) sees the place load: bodies appear round them this long. */
    int grace = 60;
    /**
     * Off: bodies appear wherever the pool gives them, seen or not, on ground or not (the pool's own tests, which measure
     * caps and nearness on a flat test world the plans weren't made for).
     */
    boolean rules = true;

    final ServerLevel level;
    int capPlayer = 40, capWorld = 96;
    boolean on = true;

    final List<CitizenBody> bodies = new ArrayList<>(128);
    private final CitizenBody[] scratch = new CitizenBody[256];
    /** Candidates this pass: settlement (index into the live list), record, nearest player, squared distance. */
    private int[] candSet = new int[0], candIdx = new int[0], candPlayer = new int[0];
    private double[] candDist = new double[0];
    /** Bodies given to each player this pass, and the players near the settlement being gathered (indices). */
    private int[] perPlayer = new int[16], near = new int[16];
    private final double[] tmp = new double[3];
    private int pathsActive;
    /** Doors opened for someone, shut again at the given game time. */
    private final List<long[]> doors = new ArrayList<>();
    /** Each player's place at the last pass and when their arrival's grace ends (x, z, game time); the players watching now. */
    private final java.util.Map<java.util.UUID, double[]> arrived = new java.util.HashMap<>();
    private final List<ServerPlayer> watchers = new ArrayList<>();

    // counters
    long spawns, despawns, enters, pathRequests, pathFails, tokenTimeouts, unstickPaths, rescues, thinks, stuck, held, nudges;
    long pathNanos, thinkNanos;
    final CrowdStats stats = new CrowdStats();

    Crowd(ServerLevel level) {
        this.level = level;
    }

    int maxPaths() {
        return bodies.size() > 60 ? 6 : 4;
    }

    boolean takePath() {
        if (pathsActive >= maxPaths()) return false;
        pathsActive++;
        return true;
    }

    void releasePath() {
        if (pathsActive > 0) pathsActive--;
    }

    // ---- embodiment ---------------------------------------------------------------------------------------------------

    /** Room for every running settlement's records (grows only when settlements are added). */
    private void capacity(List<Settlement> live) {
        int total = 0;
        for (int s = 0; s < live.size(); s++) total += live.get(s).ledger.n;
        if (candIdx.length >= total) return;
        candSet = new int[total];
        candIdx = new int[total];
        candPlayer = new int[total];
        candDist = new double[total];
    }

    void rebalance() {
        List<Settlement> live = Citizens.live();
        capacity(live);
        List<ServerPlayer> players = Citizens.players(level);
        int np = players.size();
        if (perPlayer.length < np) {
            perPlayer = new int[np + 16];
            near = new int[np + 16];
        }
        int cc = 0;
        for (int si = 0; si < live.size(); si++) {
            Settlement c = live.get(si);
            Ledger l = c.ledger;
            boolean embodies = on && c.embody && c.awake && c.level == level;
            // (SPEC §28.4) only the players near this settlement are compared with its people: any number of players
            // online, each settlement's records against the few within its reach (a player further off can't be 56 from one)
            int nn = 0;
            if (embodies) {
                double reach = c.reach + KEEP;
                for (int p = 0; p < np; p++) {
                    ServerPlayer sp = players.get(p);
                    if (sp.isSpectator() && !c.spectatorsEmbody) continue;
                    double dx = sp.getX() - c.site.cx(), dz = sp.getZ() - c.site.cz();
                    if (dx * dx + dz * dz <= reach * reach) near[nn++] = p;
                }
            }
            for (int i = 0; i < l.n; i++) {
                CitizenBody b = l.body[i];
                if (b != null && (b.isRemoved() || b.fading())) {
                    l.body[i] = null;
                    b = null;
                }
                if (!embodies || nn == 0) continue;
                double x, y, z;
                if (b != null) {
                    x = b.getX();
                    y = b.getY();
                    z = b.getZ();
                } else {
                    if (!l.position(i, tmp)) continue;
                    x = tmp[0];
                    y = tmp[1];
                    z = tmp[2];
                }
                int bp = -1;
                double bd = Double.MAX_VALUE;
                for (int q = 0; q < nn; q++) {
                    int p = near[q];
                    ServerPlayer sp = players.get(p);
                    double dx = sp.getX() - x, dy = (sp.getY() - y) * 0.5, dz = sp.getZ() - z, dd = dx * dx + dy * dy + dz * dz;
                    if (dd < bd) {
                        bd = dd;
                        bp = p;
                    }
                }
                double lim = b != null ? KEEP * KEEP : EMBODY * EMBODY;
                if (bp >= 0 && bd < lim) {
                    candSet[cc] = si;
                    candIdx[cc] = i;
                    candDist[cc] = bd;
                    candPlayer[cc] = bp;
                    cc++;
                }
            }
        }
        // nearest first, whichever settlement (insertion sort: a few hundred at most, nearly sorted from the last pass)
        for (int a = 1; a < cc; a++) {
            int ks = candSet[a], ki = candIdx[a], kp = candPlayer[a];
            double kd = candDist[a];
            int b = a - 1;
            while (b >= 0 && candDist[b] > kd) {
                candSet[b + 1] = candSet[b];
                candIdx[b + 1] = candIdx[b];
                candDist[b + 1] = candDist[b];
                candPlayer[b + 1] = candPlayer[b];
                b--;
            }
            candSet[b + 1] = ks;
            candIdx[b + 1] = ki;
            candDist[b + 1] = kd;
            candPlayer[b + 1] = kp;
        }
        Arrays.fill(perPlayer, 0);
        int world = 0;
        // nearest first; (SPEC §28.11) someone without a body yet takes a place only where nobody sees them come (a few
        // checked a pass), so a nearer one never pops up in front of anyone
        watchers(players);
        int checks = 0, fresh = 0;
        for (int k = 0; k < cc; k++) {
            Settlement c = live.get(candSet[k]);
            int i = candIdx[k], p = candPlayer[k];
            if (perPlayer[p] >= capPlayer || world >= capWorld) continue;
            if (c.ledger.body[i] == null) {
                if (fresh >= SPAWNS_PER_PASS || checks >= APPEAR_CHECKS) continue;
                checks++;
                if (!canAppear(c, i)) {
                    held++;
                    continue;
                }
                fresh++;
            }
            c.ledger.want[i] = true;
            perPlayer[p]++;
            world++;
        }
        int nb = Math.min(bodies.size(), scratch.length);
        for (int k = 0; k < nb; k++) scratch[k] = bodies.get(k);
        for (int k = 0; k < nb; k++) {
            CitizenBody b = scratch[k];
            scratch[k] = null;
            if (b.fading()) continue;
            Settlement home = b.home;
            if (b.rec < 0 || home == null || !home.live || !live.contains(home) || !home.ledger.want[b.rec]) despawn(b, true);
        }
        int spawned = 0;
        for (int k = 0; k < cc && spawned < SPAWNS_PER_PASS; k++) {
            Settlement c = live.get(candSet[k]);
            int i = candIdx[k];
            if (c.ledger.want[i] && c.ledger.body[i] == null) {
                if (spawn(c, i)) spawned++;
            }
        }
        for (int k = 0; k < cc; k++) live.get(candSet[k]).ledger.want[candIdx[k]] = false;
        // shut the doors people went through
        long now = level.getGameTime();
        for (int k = doors.size() - 1; k >= 0; k--) {
            long[] dd = doors.get(k);
            if (now >= dd[1]) {
                doors.remove(k);
                door(BlockPos.of(dd[0]), false);
            }
        }
    }

    /** The players whose sight counts this pass: everyone but those who just came (their view is still loading). */
    private void watchers(List<ServerPlayer> players) {
        long now = level.getGameTime();
        watchers.clear();
        for (int k = 0; k < players.size(); k++) {
            ServerPlayer p = players.get(k);
            double[] a = arrived.get(p.getUUID());
            if (a == null) arrived.put(p.getUUID(), a = new double[]{p.getX(), p.getZ(), now + grace});
            else if (sq(p.getX() - a[0]) + sq(p.getZ() - a[1]) > 48 * 48) a[2] = now + grace; // teleported, respawned
            a[0] = p.getX();
            a[1] = p.getZ();
            if (now >= a[2]) watchers.add(p);
        }
        if (arrived.size() > players.size() + 8) {
            arrived.keySet().removeIf(id -> {
                for (int k = 0; k < players.size(); k++) if (players.get(k).getUUID().equals(id)) return false;
                return true;
            });
        }
    }

    /** Whether a record may get a body where it is now: stepping out of a door, or where no player sees it come. */
    boolean canAppear(Settlement c, int i) {
        Ledger l = c.ledger;
        if (!l.position(i, tmp)) return false;
        if (!rules || doorStep(l, i)) return true;
        return Footing.unseen(level, watchers, tmp[0], tmp[1], tmp[2]);
    }

    private static boolean doorStep(Ledger l, int i) {
        CitizensData d = l.d;
        return l.g.kind[d.at[i]] == StreetNet.DOOR && d.state[i] == CitizensData.WALK && d.prog[i] < 120;
    }

    private static double sq(double v) {
        return v * v;
    }

    boolean spawn(Settlement c, int i) {
        Ledger l = c.ledger;
        if (!l.position(i, tmp)) return false;
        StreetNet g = l.g;
        CitizensData d = l.d;
        float yaw;
        int a = d.at[i], nx = d.next[i];
        if (nx >= 0) yaw = (float) (Math.toDegrees(Math.atan2(g.z[nx] - g.z[a], g.x[nx] - g.x[a])) - 90.0);
        else yaw = d.state[i] == CitizensData.STAND ? l.spotYaw(i) : 0f;
        boolean door = doorStep(l, i);
        if (door) {
            // coming out of a door: start a step out of it toward the street, and open it behind them
            int out = nx >= 0 ? nx : g.adj[g.adjStart[a]];
            double len = Math.max(1e-6, g.len(a, out));
            // a step and a half out: clear of the door's own cell and its panel
            double step = Math.min(1.5, len);
            tmp[0] = g.x[a] + (g.x[out] - g.x[a]) / len * step;
            tmp[2] = g.z[a] + (g.z[out] - g.z[a]) / len * step;
            yaw = (float) (Math.toDegrees(Math.atan2(g.z[out] - g.z[a], g.x[out] - g.x[a])) - 90.0);
        }
        // (SPEC §28.11) never into a block: the place itself if it's free, else the nearest free cell, else not yet
        int fx = (int) Math.floor(tmp[0]), fy = (int) Math.floor(tmp[1] + 0.01), fz = (int) Math.floor(tmp[2]);
        if (!level.isLoaded(new BlockPos(fx, fy, fz))) return false;
        if (rules && !Footing.standable(level, fx, fy, fz) && !Footing.nearest(level, tmp[0], tmp[1], tmp[2], Footing.NUDGE, tmp, Footing.NOBODY)) return false;
        CitizenBody b = CitizensContent.CITIZEN.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (b == null) return false;
        if (door) openDoorAt(g, a, 30);
        b.snapTo(tmp[0], tmp[1], tmp[2], yaw, 0f);
        b.setYHeadRot(yaw);
        b.setYBodyRot(yaw);
        b.bind(c, i, l.r.scale[i]);
        l.body[i] = b;
        bodies.add(b);
        c.work.dress(b, i, level.getGameTime());
        level.addFreshEntity(b);
        spawns++;
        return true;
    }

    /** Takes the body away (out of range, or the clock jumped); its record walks on in the ledger. */
    void despawn(CitizenBody b, boolean fade) {
        int i = b.rec;
        Settlement c = b.home;
        if (c != null && i >= 0 && c.ledger.body[i] == b) {
            b.writeBack(c.ledger);
            c.ledger.body[i] = null;
        }
        bodies.remove(b);
        despawns++;
        if (fade) b.fadeAway(CitizenBody.FADE_TICKS);
        else {
            b.rec = -1;
            b.discard();
        }
    }

    /** A body reached its door: it goes in (the door opens and shuts behind it) and fades as it steps through. */
    void entered(CitizenBody b) {
        enters++;
        int i = b.rec;
        Settlement c = b.home;
        if (c != null && i >= 0 && c.ledger.body[i] == b) {
            int at = c.ledger.d.at[i];
            if (at >= 0 && c.net.kind[at] == StreetNet.DOOR) openDoorAt(c.net, at, 24);
            c.ledger.body[i] = null;
        }
        bodies.remove(b);
        b.fadeAway(8);
    }

    /** A body left the level some other way (chunk unload, a command): the ledger takes the record back as it was. */
    void forget(CitizenBody b) {
        int i = b.rec;
        Settlement c = b.home;
        if (c != null && i >= 0 && c.ledger.body[i] == b) {
            b.writeBack(c.ledger);
            c.ledger.body[i] = null;
        }
        bodies.remove(b);
    }

    void despawnAll() {
        for (CitizenBody b : bodies.toArray(new CitizenBody[0])) despawn(b, false);
    }

    /** One settlement's bodies go (it stopped or fell asleep). */
    void despawnAll(Settlement c) {
        for (CitizenBody b : bodies.toArray(new CitizenBody[0])) if (b.home == c) despawn(b, false);
    }

    private void openDoorAt(StreetNet g, int node, int ticks) {
        BlockPos pos = BlockPos.containing(g.x[node], g.y[node] + 1, g.z[node]);
        if (door(pos, true)) doors.add(new long[]{pos.asLong(), level.getGameTime() + ticks});
    }

    /** Opens or shuts a wooden door at pos (its lower half); true when it was a door that changed. */
    private boolean door(BlockPos pos, boolean open) {
        if (!level.isLoaded(pos)) return false;
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof DoorBlock door) || !DoorBlock.isWoodenDoor(s)) return false;
        if (door.isOpen(s) == open) return false;
        door.setOpen(null, level, s, pos, open);
        return true;
    }

    int inView() {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return 0;
        ServerPlayer p = players.get(0);
        var look = p.getViewVector(1f);
        double ex = p.getX(), ey = p.getEyeY(), ez = p.getZ();
        int n = 0;
        for (CitizenBody b : bodies) {
            double dx = b.getX() - ex, dy = b.getY() + 1.0 - ey, dz = b.getZ() - ez, dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dd < 1e-6 || (dx * look.x + dy * look.y + dz * look.z) / dd > Math.cos(Math.toRadians(50))) n++;
        }
        return n;
    }

    /** Bodies of one settlement now. */
    int bodiesOf(Settlement c) {
        int n = 0;
        for (int k = 0; k < bodies.size(); k++) if (bodies.get(k).home == c) n++;
        return n;
    }

    void resetCounters() {
        spawns = despawns = enters = pathRequests = pathFails = tokenTimeouts = unstickPaths = rescues = thinks = stuck = held = nudges = 0;
        pathNanos = thinkNanos = 0;
    }
}
