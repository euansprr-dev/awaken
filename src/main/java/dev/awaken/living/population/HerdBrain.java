package dev.awaken.living.population;

import dev.awaken.living.api.Wildness;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One herd's mind (SPEC §41.2): it thinks once a second and the animals follow it. Its moods: graze (spread round its place),
 * drift (to the next pasture at dawn and dusk), bed (a tight group at night), alert (heads up, bunching), flee and, for a big
 * herd in wild land, stampede. A person on their feet inside its flight distance startles it (wilder herds are warier;
 * crouching, cover and coming from downwind let a hunter nearer), and so does their scent carried downwind, a pack's attack
 * or one of the herd being hit. The alarm ripples from the near side to the far side over half a second, so the herd turns
 * like a wave; it runs away over ground away from people (tamed land turns it), then regroups where it stopped and drifts
 * home over a few minutes. Only animals that stray from their place are steered; the rest graze on their own goals.
 */
final class HerdBrain implements Ledger.Brain {
    enum Mood { GRAZE, DRIFT, BED, ALERT, FLEE, STAMPEDE }

    enum Cause { SEEN, SCENT, HIT, PACK }

    /** Ticks between thoughts; the ripple's spread; how long a flight, a stampede and the wariness after hold. */
    static final int THINK = 20, RIPPLE = 10, FLEE_TICKS = 100, STAMPEDE_TICKS = 140, ALERT_TICKS = 200;
    /** How far a flight and a stampede run; how far a herd may end up from its own place before it walks back. */
    static final double FLEE_DIST = 32, STAMPEDE_DIST = 44, MAX_OFFSET = 96;
    /** Flight distance: crouched, sprinting, and how far the scent carries downwind (times the flight distance). */
    static final double CROUCH = 0.4, SPRINT = 1.3, SCENT_REACH = 2.2, SCENT_CONE = 0.6, ALERT_REACH = 1.6;
    /** A body this far from the herd with no one near it falls behind and rejoins the count. */
    static final double LOST = 64, UNSEEN = 24;
    /** How big a herd and how wild its land must be to stampede rather than flee. */
    static final int STAMPEDE_SIZE = 12;
    static final float TRAMPLE = 4F;
    /** An animal is called back once it is this far past its place's edge (times the edge), and sent well inside it. */
    static final double STRAY = 1.3;

    final Ledger.Pop pop;
    Mood mood = Mood.GRAZE;
    int until;
    double offX, offZ, threatX, threatZ;
    private double dirX, dirZ;
    private final List<Ripple> ripple = new ArrayList<>();
    private final Set<UUID> trampled = new HashSet<>();
    /** Where each animal is running to, while the herd runs (it gets there in legs). */
    private final java.util.Map<UUID, double[]> runTo = new java.util.HashMap<>();

    /** Where the herd last left prints (NaN: not yet), and the play-day it last left its droppings at its bed. */
    final double[] printed = {Double.NaN, Double.NaN};
    long dunged = -1;

    private record Ripple(UUID id, int at, double x, double z, double speed) {}

    HerdBrain(Ledger.Pop pop) {
        this.pop = pop;
    }

    boolean running() {
        return mood == Mood.FLEE || mood == Mood.STAMPEDE;
    }

    @Override
    public void tick(Ledger l, ServerLevel level, long day, long tod, int now) {
        if (!ripple.isEmpty()) runRipple(l, level, now);
        if (mood == Mood.STAMPEDE && now % 4 == 0) trample(l, level);
        if (Math.floorMod(now - pop.id, THINK) == 0) think(l, level, day, tod, now);
    }

    void think(Ledger l, ServerLevel level, long day, long tod, int now) {
        List<PathfinderMob> herd = l.living(level, pop.id);
        if (herd.isEmpty()) return;
        double mx = 0, mz = 0;
        for (PathfinderMob m : herd) {
            mx += m.getX();
            mz += m.getZ();
        }
        mx /= herd.size();
        mz /= herd.size();
        // one that fell behind out of everyone's sight rejoins the count (no death)
        for (PathfinderMob m : herd) {
            if (Math.hypot(m.getX() - mx, m.getZ() - mz) > LOST && level.getNearestPlayer(m, UNSEEN) == null) l.rejoin(pop.id, m);
        }
        offX *= 0.99;
        offZ *= 0.99;
        Spoor.step(level, pop, herd, mx, mz, printed);
        long t = Math.floorMod(tod, 24000L);
        if (mood == Mood.BED && dunged != day && t >= 16000 && t < 23000) {
            dunged = day;
            Spoor.bed(level, pop, herd, mx, mz);
        }
        sense(l, level, herd, mx, mz, now);
        if (running() && now >= until) {
            mood = Mood.ALERT;
            until = now + ALERT_TICKS;
            runTo.clear();
            ripple.clear();
        }
        if (running()) runOn(l, level, herd);
        if (!running() && !(mood == Mood.ALERT && now < until)) mood = base(tod);
        double[] at = l.where(pop, day, tod);
        steer(l, level, herd, at[0] + offX, at[1] + offZ);
        wear(level, herd);
    }

    /** Mouthfuls a grazing herd takes a second (half of them land), and how many moving animals press the grass a second. */
    static final int TREAD_MAX = 8;
    private int bite;

    /**
     * Grass that lives (SPEC §41.2): grazing, an animal or two a second crops the grass at its feet, so a herd's pasture
     * becomes a lawn; drifting, fleeing or stampeding, the animals on the move press their way flat, so the routes it walks
     * every day become trails. The grass itself is the plants' ({@link Wildness#ground}).
     */
    void wear(ServerLevel level, List<PathfinderMob> herd) {
        Wildness.Ground g = Wildness.ground();
        int strength = mood == Mood.STAMPEDE ? Wildness.STAMPEDE : running() || mood == Mood.DRIFT ? Wildness.HERD : 0;
        if (mood == Mood.GRAZE) {
            int bites = herd.size() >= 8 ? 2 : 1;
            for (int k = 0; k < bites; k++) {
                PathfinderMob m = herd.get(Math.floorMod(bite++, herd.size()));
                if (m.getNavigation().isDone() && m.onGround() && level.getRandom().nextBoolean()) g.crop(level, m.blockPosition());
            }
        } else if (strength > 0) {
            int pressed = 0;
            for (int i = 0; i < herd.size() && pressed < TREAD_MAX; i++) {
                PathfinderMob m = herd.get(Math.floorMod(bite++, herd.size()));
                if (m.getNavigation().isDone() || !m.onGround()) continue;
                g.trample(level, m.blockPosition(), strength);
                pressed++;
            }
        }
    }

    /** Graze by day, drift between pastures at dawn and dusk, bed down at night (Ledger.where's own day). */
    static Mood base(long tod) {
        long t = Math.floorMod(tod, 24000L);
        if (t >= 13500 && t < 23000) return Mood.BED;
        if (t < 2000 || t >= 12000) return Mood.DRIFT;
        return Mood.GRAZE;
    }

    /** The herd's flight distance from one person: by its land's band, less crouched or in cover, more for a sprinter. */
    double flight(ServerLevel level, ServerPlayer p) {
        double f = Herds.flight(pop.band());
        if (p.isCrouching()) f *= CROUCH;
        if (p.isSprinting()) f *= SPRINT;
        return f * (1 - 0.67 * Wildness.coverAt(level, p.blockPosition()));
    }

    private void sense(Ledger l, ServerLevel level, List<PathfinderMob> herd, double mx, double mz, int now) {
        ServerPlayer startler = null, watcher = null;
        Cause cause = null;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || Math.hypot(p.getX() - mx, p.getZ() - mz) > 96) continue;
            double near = Double.MAX_VALUE;
            for (PathfinderMob m : herd) near = Math.min(near, Math.hypot(p.getX() - m.getX(), p.getZ() - m.getZ()));
            double flight = flight(level, p);
            if (near < flight) {
                startler = p;
                cause = Cause.SEEN;
                break;
            }
            // the wind carries a person's scent to a herd downwind of them, crouched or not
            double ux = mx - p.getX(), uz = mz - p.getZ(), len = Math.max(1e-6, Math.hypot(ux, uz));
            double down = (ux * l.windField.dirX + uz * l.windField.dirZ) / len;
            if (down > SCENT_CONE && near < Herds.flight(pop.band()) * SCENT_REACH) {
                startler = p;
                cause = Cause.SCENT;
                break;
            }
            if (near < flight * ALERT_REACH) watcher = p;
        }
        if (startler != null) {
            alarm(l, level, startler.getX(), startler.getZ(), cause, now, startler.isSprinting());
        } else if (watcher != null && !running()) {
            mood = Mood.ALERT;
            until = now + ALERT_TICKS;
            threatX = watcher.getX();
            threatZ = watcher.getZ();
        }
    }

    void alarm(Ledger l, ServerLevel level, double fx, double fz, Cause cause, int now) {
        alarm(l, level, fx, fz, cause, now, false);
    }

    /** Something startled the herd from (fx, fz): it runs (or stampedes) away, the near side first. */
    void alarm(Ledger l, ServerLevel level, double fx, double fz, Cause cause, int now, boolean sprinter) {
        List<PathfinderMob> herd = l.living(level, pop.id);
        if (herd.isEmpty()) return;
        threatX = fx;
        threatZ = fz;
        if (running() && now < until - 40) return; // already away: it keeps going
        double mx = 0, mz = 0;
        for (PathfinderMob m : herd) {
            mx += m.getX();
            mz += m.getZ();
        }
        mx /= herd.size();
        mz /= herd.size();
        double dx = mx - fx, dz = mz - fz, len = Math.hypot(dx, dz);
        if (len < 1e-3) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
        } else {
            dx /= len;
            dz /= len;
        }
        boolean stampede = herd.size() >= STAMPEDE_SIZE && pop.band().ordinal() >= Wildness.Band.WILD.ordinal()
                && (cause == Cause.HIT || cause == Cause.PACK || sprinter);
        double dist = stampede ? STAMPEDE_DIST : FLEE_DIST, speed = stampede ? 2.0 : 1.6;
        double[] dir = awayFromPeople(level, mx, mz, dx, dz, dist);
        dirX = dir[0];
        dirZ = dir[1];
        mood = stampede ? Mood.STAMPEDE : Mood.FLEE;
        until = now + (stampede ? STAMPEDE_TICKS : FLEE_TICKS);
        trampled.clear();
        ripple.clear();
        runTo.clear();
        // the near side hears it first: each animal's delay follows how far along the run it already stands
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (PathfinderMob m : herd) {
            double p = (m.getX() - fx) * dirX + (m.getZ() - fz) * dirZ;
            lo = Math.min(lo, p);
            hi = Math.max(hi, p);
        }
        for (int i = 0; i < herd.size(); i++) {
            PathfinderMob m = herd.get(i);
            double p = (m.getX() - fx) * dirX + (m.getZ() - fz) * dirZ;
            int delay = (int) Math.round((p - lo) / (hi - lo + 1e-6) * RIPPLE);
            double side = ((i % 7) - 3) * 1.4;
            ripple.add(new Ripple(m.getUUID(), now + delay, m.getX() + dirX * dist - dirZ * side, m.getZ() + dirZ * dist + dirX * side, speed));
        }
        offX = clampOff(offX + dirX * dist * 0.8);
        offZ = clampOff(offZ + dirZ * dist * 0.8);
    }

    private static double clampOff(double v) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, v));
    }

    /** The way to run: straight away, else the nearest turn that doesn't end in tamed land (a settlement, a Hearth). */
    static double[] awayFromPeople(ServerLevel level, double x, double z, double dx, double dz, double dist) {
        int[] turns = {0, 30, -30, 60, -60, 90, -90, 120, -120};
        for (int t : turns) {
            double a = Math.toRadians(t), cx = dx * Math.cos(a) - dz * Math.sin(a), cz = dx * Math.sin(a) + dz * Math.cos(a);
            int px = (int) Math.floor(x + cx * dist), pz = (int) Math.floor(z + cz * dist);
            if (Wildness.at(level, px, pz) >= Wildness.TAMED) return new double[]{cx, cz};
        }
        return new double[]{dx, dz};
    }

    /** The alarm reaches each animal in its turn (and when a path is free: a wave held back keeps its order). */
    private void runRipple(Ledger l, ServerLevel level, int now) {
        var it = ripple.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            if (r.at > now) continue;
            if (!(level.getEntity(r.id) instanceof PathfinderMob m) || !m.isAlive() || m.getTarget() != null || m.entityTags().contains(Ledger.LIMPING)) {
                it.remove();
                continue;
            }
            if (!l.go(level, m, r.x, r.z, r.speed)) return;
            runTo.put(r.id, new double[]{r.x, r.z, r.speed});
            it.remove();
        }
    }

    /** Animals that finished a leg and aren't where they're running to yet go on. */
    private void runOn(Ledger l, ServerLevel level, List<PathfinderMob> herd) {
        for (PathfinderMob m : herd) {
            double[] t = runTo.get(m.getUUID());
            if (t == null || !m.getNavigation().isDone() || m.getTarget() != null || m.entityTags().contains(Ledger.LIMPING)) continue;
            if (Math.hypot(t[0] - m.getX(), t[1] - m.getZ()) < 3) {
                runTo.remove(m.getUUID());
                continue;
            }
            if (!l.go(level, m, t[0], t[1], t[2])) return;
        }
    }

    /** A stampede runs people down: a hard knock the first time each one is caught. */
    private void trample(Ledger l, ServerLevel level) {
        for (PathfinderMob m : l.living(level, pop.id)) {
            if (m.getNavigation().isDone()) continue;
            if (m.onGround()) Wildness.ground().trample(level, m.blockPosition(), Wildness.STAMPEDE);
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative() || trampled.contains(p.getUUID())) continue;
                if (Math.hypot(p.getX() - m.getX(), p.getZ() - m.getZ()) > 1.3 || Math.abs(p.getY() - m.getY()) > 1.5) continue;
                trampled.add(p.getUUID());
                var src = level.damageSources().mobAttack(m);
                p.hurtServer(level, src, TRAMPLE);
                p.knockback(0.9, -dirX, -dirZ, src, TRAMPLE);
            }
        }
    }

    /** Animals that strayed from their place walk back to a spot of their own in it; the rest graze on their own. */
    private void steer(Ledger l, ServerLevel level, List<PathfinderMob> herd, double cx, double cz) {
        if (running()) return; // already away: it keeps going
        int n = herd.size();
        double r0 = 4 + 1.6 * Math.sqrt(n);
        double reach = switch (mood) {
            case BED -> 2 + 0.8 * Math.sqrt(n);
            case ALERT -> r0 * 0.7;
            default -> r0;
        };
        double speed = switch (mood) {
            case DRIFT -> 1.0;
            case ALERT -> 1.1;
            case BED -> 0.7;
            default -> 0.8;
        };
        for (int i = 0; i < n; i++) {
            PathfinderMob m = herd.get(i);
            if (m.getTarget() != null || m.entityTags().contains(Ledger.LIMPING)) continue;
            if (mood == Mood.ALERT) m.getLookControl().setLookAt(threatX, m.getEyeY(), threatZ);
            if (Math.hypot(m.getX() - cx, m.getZ() - cz) <= reach * STRAY || !m.getNavigation().isDone()) continue;
            double rr = reach * 0.6 * Math.sqrt((i + 0.5) / n), a = i * 2.39996;
            if (!l.go(level, m, cx + Math.cos(a) * rr, cz + Math.sin(a) * rr, speed)) return; // the rest next time
        }
    }
}
