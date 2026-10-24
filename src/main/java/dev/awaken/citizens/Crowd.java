package dev.awaken.citizens;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Who has a body (SPEC §16.5): every 10 ticks, nearest first, at most 40 bodies per player and 96 in the world, spawned
 * within 56 blocks and kept to 64, at most 8 new ones a pass. Bodies fade in on the client and fade out here before
 * they go, and go in and out through real doors (opened and shut behind them). Holds the vanilla path slots (4, or 6
 * above 60 bodies) and the per-tick timings.
 */
final class Crowd {
    /** Accumulated by every body's tick between the server tick's Pre and Post. */
    static long bodyNanos;
    static final int SPAWNS_PER_PASS = 8, MAX_PLAYERS = 16;
    static final double EMBODY = 56, KEEP = 64;

    final Citizens c;
    final ServerLevel level;
    final Ledger l;
    int capPlayer = 40, capWorld = 96;
    boolean on = true;

    final List<CitizenBody> bodies = new ArrayList<>(128);
    private final CitizenBody[] scratch = new CitizenBody[256];
    private final int[] candIdx, candPlayer;
    private final double[] candDist;
    private final boolean[] want;
    private final int[] perPlayer = new int[MAX_PLAYERS];
    private final double[] tmp = new double[3];
    private int pathsActive;
    /** Doors opened for someone, shut again at the given game time. */
    private final List<long[]> doors = new ArrayList<>();

    // counters
    long spawns, despawns, enters, pathRequests, pathFails, tokenTimeouts, unstickPaths, rescues, thinks, stuck;
    long pathNanos, thinkNanos;
    final CrowdStats stats = new CrowdStats();

    Crowd(Citizens c, ServerLevel level, Ledger l) {
        this.c = c;
        this.level = level;
        this.l = l;
        candIdx = new int[l.n];
        candPlayer = new int[l.n];
        candDist = new double[l.n];
        want = new boolean[l.n];
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

    void rebalance() {
        List<ServerPlayer> players = level.players();
        int np = Math.min(players.size(), MAX_PLAYERS);
        int cc = 0;
        for (int i = 0; i < l.n; i++) {
            CitizenBody b = l.body[i];
            if (b != null && (b.isRemoved() || b.fading())) {
                l.body[i] = null;
                b = null;
            }
            if (!on || np == 0) continue;
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
            for (int p = 0; p < np; p++) {
                ServerPlayer sp = players.get(p);
                if (sp.isSpectator() && !c.spectatorsEmbody) continue;
                double dx = sp.getX() - x, dy = (sp.getY() - y) * 0.5, dz = sp.getZ() - z, dd = dx * dx + dy * dy + dz * dz;
                if (dd < bd) {
                    bd = dd;
                    bp = p;
                }
            }
            double lim = b != null ? KEEP * KEEP : EMBODY * EMBODY;
            if (bp >= 0 && bd < lim) {
                candIdx[cc] = i;
                candDist[cc] = bd;
                candPlayer[cc] = bp;
                cc++;
            }
        }
        // nearest first (insertion sort: a few hundred at most, nearly sorted from the last pass)
        for (int a = 1; a < cc; a++) {
            int ki = candIdx[a], kp = candPlayer[a];
            double kd = candDist[a];
            int b = a - 1;
            while (b >= 0 && candDist[b] > kd) {
                candIdx[b + 1] = candIdx[b];
                candDist[b + 1] = candDist[b];
                candPlayer[b + 1] = candPlayer[b];
                b--;
            }
            candIdx[b + 1] = ki;
            candDist[b + 1] = kd;
            candPlayer[b + 1] = kp;
        }
        Arrays.fill(perPlayer, 0);
        int world = 0;
        for (int k = 0; k < cc; k++) {
            int p = candPlayer[k];
            if (perPlayer[p] < capPlayer && world < capWorld) {
                want[candIdx[k]] = true;
                perPlayer[p]++;
                world++;
            }
        }
        int nb = Math.min(bodies.size(), scratch.length);
        for (int k = 0; k < nb; k++) scratch[k] = bodies.get(k);
        for (int k = 0; k < nb; k++) {
            CitizenBody b = scratch[k];
            scratch[k] = null;
            if (b.fading()) continue;
            if (b.rec < 0 || !want[b.rec]) despawn(b, true);
        }
        int spawned = 0;
        for (int k = 0; k < cc && spawned < SPAWNS_PER_PASS; k++) {
            int i = candIdx[k];
            if (want[i] && l.body[i] == null) {
                if (spawn(i)) spawned++;
            }
        }
        for (int k = 0; k < cc; k++) want[candIdx[k]] = false;
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

    boolean spawn(int i) {
        if (!l.position(i, tmp)) return false;
        CitizenBody b = CitizensContent.CITIZEN.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (b == null) return false;
        StreetNet g = l.g;
        CitizensData d = l.d;
        float yaw;
        int a = d.at[i], nx = d.next[i];
        if (nx >= 0) {
            yaw = (float) (Math.toDegrees(Math.atan2(g.z[nx] - g.z[a], g.x[nx] - g.x[a])) - 90.0);
            if (g.kind[a] == StreetNet.DOOR && d.prog[i] < 120) {
                // coming out of a door: start a step out of it, and open it behind them
                double len = Math.max(1e-6, g.len(a, nx));
                tmp[0] = g.x[a] + (g.x[nx] - g.x[a]) / len * 0.9;
                tmp[2] = g.z[a] + (g.z[nx] - g.z[a]) / len * 0.9;
                openDoorAt(a, 30);
            }
        } else {
            yaw = d.state[i] == CitizensData.STAND ? l.spotYaw(i) : 0f;
        }
        b.snapTo(tmp[0], tmp[1], tmp[2], yaw, 0f);
        b.setYHeadRot(yaw);
        b.setYBodyRot(yaw);
        b.bind(i, l.r.scale[i]);
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
        if (i >= 0 && l.body[i] == b) {
            b.writeBack(l);
            l.body[i] = null;
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
        if (i >= 0 && l.body[i] == b) {
            int at = l.d.at[i];
            if (at >= 0 && l.g.kind[at] == StreetNet.DOOR) openDoorAt(at, 24);
            l.body[i] = null;
        }
        bodies.remove(b);
        b.fadeAway(8);
    }

    /** A body left the level some other way (chunk unload, a command): the ledger takes the record back as it was. */
    void forget(CitizenBody b) {
        int i = b.rec;
        if (i >= 0 && l.body[i] == b) {
            b.writeBack(l);
            l.body[i] = null;
        }
        bodies.remove(b);
    }

    void despawnAll() {
        for (CitizenBody b : bodies.toArray(new CitizenBody[0])) despawn(b, false);
    }

    private void openDoorAt(int node, int ticks) {
        BlockPos pos = BlockPos.containing(l.g.x[node], l.g.y[node] + 1, l.g.z[node]);
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

    /** True when no player can see this body (rescues only teleport out of every player's view). */
    boolean unseen(ServerLevel level, CitizenBody b) {
        Vec3 target = b.getEyePosition();
        for (ServerPlayer p : level.players()) {
            Vec3 eye = p.getEyePosition();
            double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 72) continue;
            Vec3 look = p.getViewVector(1f);
            if (dist > 4 && (dx * look.x + dy * look.y + dz * look.z) / dist < 0.35) continue; // behind them
            var hit = level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) return false;
        }
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

    void resetCounters() {
        spawns = despawns = enters = pathRequests = pathFails = tokenTimeouts = unstickPaths = rescues = thinks = stuck = 0;
        pathNanos = thinkNanos = 0;
    }
}
