package dev.awaken.living.population;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One pack's mind (SPEC §41.2), once a second: rest at the den, prowl toward its herd, stalk from downwind, chase the
 * straggler (the nearest to the pack, the young, the hurt), feed at the kill, and retreat to the den when its alpha is badly
 * hurt. A pack hunts once a day while its herd has bodies near (wolves by day and dusk, our hunters by night); a person who
 * walks into its kill is turned on. The kill is a real death, so the ledger's herd is one fewer.
 */
final class PackBrain implements Ledger.Brain {
    enum Mood { REST, PROWL, STALK, CHASE, FEED, RETREAT }

    static final int THINK = 20, STALK_TICKS = 600, CHASE_TICKS = 600, FEED_TICKS = 1200, RETREAT_TICKS = 600;
    /** How near its herd must be to be stalked; how far downwind of it the pack waits; when a chase is given up. */
    static final double SEE_HERD = 64, STALK_OFF = 18, CLOSE_IN = 22, GIVE_UP = 48, KILL_GUARD = 8;
    static final float RETREAT_AT = 0.3F;

    final Ledger.Pop pop;
    Mood mood = Mood.REST;
    int since, until;
    @Nullable UUID prey;
    double killX, killZ;
    long huntedDay = -1;
    /** Where the pack last left prints (NaN: not yet). */
    final double[] printed = {Double.NaN, Double.NaN};

    PackBrain(Ledger.Pop pop) {
        this.pop = pop;
    }

    /** Wolves hunt by day and dusk; our night hunters by night. */
    boolean huntingTime(long tod) {
        long t = Math.floorMod(tod, 24000L);
        return pop.species.equals(Herds.WOLF) ? t < 13500 : t >= 13000 && t < 23000;
    }

    @Override
    public void tick(Ledger l, ServerLevel level, long day, long tod, int now) {
        if (Math.floorMod(now - pop.id, THINK) == 0) think(l, level, day, tod, now);
    }

    void think(Ledger l, ServerLevel level, long day, long tod, int now) {
        List<PathfinderMob> pack = l.living(level, pop.id);
        if (pack.isEmpty()) return;
        double px = 0, pz = 0;
        for (PathfinderMob m : pack) {
            px += m.getX();
            pz += m.getZ();
        }
        px /= pack.size();
        pz /= pack.size();
        Spoor.step(level, pop, pack, px, pz, printed);
        PathfinderMob alpha = pop.alpha ? pack.getFirst() : null;
        if (alpha != null && mood != Mood.RETREAT && alpha.getHealth() < alpha.getMaxHealth() * RETREAT_AT) {
            mood = Mood.RETREAT;
            until = now + RETREAT_TICKS;
            for (PathfinderMob m : pack) {
                m.setTarget(null);
                l.go(level, m, pop.hx, pop.hz, 1.3);
            }
            return;
        }
        HerdBrain herd = l.herdBrain(pop.herd);
        List<PathfinderMob> herdBodies = herd == null ? List.of() : l.living(level, pop.herd);
        switch (mood) {
            case RETREAT -> {
                for (PathfinderMob m : pack) if (m.getNavigation().isDone() && Math.hypot(m.getX() - pop.hx, m.getZ() - pop.hz) > 6) l.go(level, m, pop.hx, pop.hz, 1.3);
                if (now >= until) mood = Mood.REST;
            }
            case FEED -> {
                for (ServerPlayer p : level.players()) {
                    if (p.isSpectator() || p.isCreative() || Math.hypot(p.getX() - killX, p.getZ() - killZ) > KILL_GUARD) continue;
                    for (PathfinderMob m : pack) if (m.getTarget() == null) m.setTarget(p);
                }
                if (now >= until) {
                    mood = Mood.REST;
                    for (PathfinderMob m : pack) m.setTarget(null);
                }
            }
            case CHASE -> {
                LivingEntity target = find(level, prey);
                if (target == null || now >= until || Math.hypot(target.getX() - px, target.getZ() - pz) > GIVE_UP) {
                    mood = Mood.PROWL;
                    prey = null;
                    for (PathfinderMob m : pack) m.setTarget(null);
                } else {
                    for (PathfinderMob m : pack) {
                        if (m.getTarget() != target) m.setTarget(target);
                        if (m.onGround()) dev.awaken.living.api.Wildness.ground().trample(level, m.blockPosition(), dev.awaken.living.api.Wildness.LIGHT);
                    }
                }
            }
            case STALK -> {
                if (herdBodies.isEmpty()) {
                    mood = Mood.PROWL;
                    break;
                }
                double[] hm = mean(herdBodies);
                // wait downwind of the herd, where the pack's scent blows away from it
                double sx = hm[0] + l.windField.dirX * STALK_OFF, sz = hm[1] + l.windField.dirZ * STALK_OFF;
                for (PathfinderMob m : pack) if (m.getNavigation().isDone() && Math.hypot(m.getX() - sx, m.getZ() - sz) > 4) l.go(level, m, sx, sz, 0.8);
                if (now - since > STALK_TICKS || Math.hypot(hm[0] - px, hm[1] - pz) < CLOSE_IN) chase(l, level, pack, herdBodies, px, pz, day, now);
            }
            default -> {
                if (huntingTime(tod) && huntedDay != day && !herdBodies.isEmpty()) {
                    double[] hm = mean(herdBodies);
                    if (Math.hypot(hm[0] - px, hm[1] - pz) < SEE_HERD) {
                        mood = Mood.STALK;
                        since = now;
                        break;
                    }
                }
                mood = huntingTime(tod) ? Mood.PROWL : Mood.REST;
                double[] at = l.where(pop, day, tod);
                for (PathfinderMob m : pack) {
                    if (m.getTarget() != null || !m.getNavigation().isDone() || Math.hypot(m.getX() - at[0], m.getZ() - at[1]) < 6) continue;
                    l.go(level, m, at[0], at[1], 1.0);
                }
            }
        }
    }

    /** The chase begins: the straggler is marked, the whole pack goes for it, and the herd runs. */
    void chase(Ledger l, ServerLevel level, List<PathfinderMob> pack, List<PathfinderMob> herd, double px, double pz, long day, int now) {
        PathfinderMob s = straggler(herd, px, pz);
        if (s == null) return;
        prey = s.getUUID();
        mood = Mood.CHASE;
        until = now + CHASE_TICKS;
        huntedDay = day;
        for (PathfinderMob m : pack) m.setTarget(s);
        HerdBrain hb = l.herdBrain(pop.herd);
        if (hb != null) hb.alarm(l, level, px, pz, HerdBrain.Cause.PACK, now);
    }

    /** The one a pack goes for: the nearest to it (the herd's rear as it runs), the young, the hurt. */
    static @Nullable PathfinderMob straggler(List<PathfinderMob> herd, double px, double pz) {
        PathfinderMob best = null;
        double bestScore = -Double.MAX_VALUE;
        for (PathfinderMob m : herd) {
            double score = -Math.hypot(m.getX() - px, m.getZ() - pz) + (m.isBaby() ? 6 : 0) + (1 - m.getHealth() / m.getMaxHealth()) * 6;
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
        }
        return best;
    }

    /** A body died somewhere: the pack's prey falling ends the chase at the kill. */
    void died(Ledger l, LivingEntity dead) {
        if (prey == null || !prey.equals(dead.getUUID()) || !(dead.level() instanceof ServerLevel level)) return;
        mood = Mood.FEED;
        killX = dead.getX();
        killZ = dead.getZ();
        Ledger.Pop herd = l.pop(pop.herd);
        Spoor.of(level).lay(Spoor.Kind.KILL, killX, dead.getY(), killZ, dead.getYRot(), pop, l.living(level, pop.id).size(), level.getGameTime(),
                herd != null ? herd.species : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString(), 0);
        until = level.getServer().getTickCount() + FEED_TICKS;
        prey = null;
        for (PathfinderMob m : l.living(level, pop.id)) {
            m.setTarget(null);
            l.go(level, m, killX, killZ, 1.0);
        }
    }

    private static @Nullable LivingEntity find(ServerLevel level, @Nullable UUID id) {
        return id != null && level.getEntity(id) instanceof LivingEntity e && e.isAlive() ? e : null;
    }

    private static double[] mean(List<PathfinderMob> list) {
        double x = 0, z = 0;
        for (PathfinderMob m : list) {
            x += m.getX();
            z += m.getZ();
        }
        return new double[]{x / list.size(), z / list.size()};
    }
}
