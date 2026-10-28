package dev.awaken.powers.ladder;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The ladder's shared mechanics (SPEC 34.3), on the server. {@link #onHit} runs a cast's on-hit riders after one of its hits
 * landed (Combat.hurtBy). Rider damage goes through {@link Combat#hurtFrom}, so it keeps the boss cap and the ally rules and
 * never starts riders again. Echo, aftershock and linger fire at most once per cast every {@link #THROTTLE} ticks (a held
 * beam pulses five times a second); a chain throws at most 2n arcs a tick, however many foes a blast caught.
 */
public final class Riders {
    private Riders() {}

    public static final int THROTTLE = 10;

    /** Foes a rider marked: they glow with the caster's rune and take 10% more from that caster until the tick given. */
    private record Mark(UUID owner, long until) {}

    private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();
    private static int depth;

    public static void onHit(Cast c, LivingEntity target, float amount, Forces.@Nullable Force force) {
        if (depth > 0 || c.kit.onHit.length == 0) return;
        long now = c.now();
        boolean free = now - c.riderTick >= THROTTLE;
        boolean used = false;
        depth++;
        try {
            Vec3 at = target.getBoundingBox().getCenter();
            for (Rider r : c.kit.onHit) {
                switch (r.kind()) {
                    case CHAIN -> chain(c, target, at, amount, force, r, now);
                    case ECHO -> {
                        if (free) {
                            echo(c, target, amount, force, r);
                            used = true;
                        }
                    }
                    case AFTERSHOCK -> {
                        if (free) {
                            aftershock(c, at, amount, force, r);
                            used = true;
                        }
                    }
                    case LINGER -> {
                        if (free) {
                            linger(c, target.position(), amount, r);
                            used = true;
                        }
                    }
                    case STATUS -> status(c, target, r, now);
                    case PUSH -> Combat.knockFrom(target, c.player.position(), r.force(), r.lift());
                    case REFUND -> {
                        if ((!r.kill() || !target.isAlive()) && !c.refunded) {
                            Powers.restoreEnergy(c.player, r.energy());
                            c.refunded = !r.kill(); // a kill refund pays per kill, a hit refund once a cast
                        }
                    }
                    default -> {
                    }
                }
            }
        } finally {
            depth--;
        }
        if (used) c.riderTick = now;
    }

    /** Arcs from the foe hit to the nearest others, in the power's colour. */
    private static void chain(Cast c, LivingEntity from, Vec3 at, float amount, Forces.@Nullable Force force, Rider r, long now) {
        if (c.chainTick != now) {
            c.chainTick = now;
            c.chainCount = 0;
        }
        int cap = Math.max(2, r.n() * 2);
        if (c.chainCount >= cap) return;
        List<LivingEntity> near = new ArrayList<>(Combat.foesAround(c.player, at, r.reach() > 0 ? r.reach() : 6));
        near.remove(from);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        int done = 0;
        for (LivingEntity e : near) {
            if (done >= r.n() || c.chainCount >= cap) break;
            Vec3 to = e.getBoundingBox().getCenter();
            Combat.hurtFrom(c.player, null, e, amount * r.share(), force, true);
            Fx.at(c, FxKind.LIGHTNING, at).to(to).ticks(6).scale(0.9).plain().flag(FxEvent.QUIET).send();
            done++;
            c.chainCount++;
        }
    }

    /** The same foe struck again a moment later, fainter. */
    private static void echo(Cast c, LivingEntity target, float amount, Forces.@Nullable Force force, Rider r) {
        Scheduler.after(Math.max(1, r.delay()), () -> {
            if (!target.isAlive() || c.player.isRemoved()) return;
            Combat.hurtFrom(c.player, null, target, amount * r.share(), force, true);
            Fx.at(c, FxKind.IMPACT, target.getBoundingBox().getCenter()).dir(target.position().subtract(c.player.position()))
                    .scale(0.8).plain().flag(FxEvent.QUIET).send();
        });
    }

    /** A pulse round the hit after a beat. */
    private static void aftershock(Cast c, Vec3 at, float amount, Forces.@Nullable Force force, Rider r) {
        Scheduler.after(Math.max(1, r.delay()), () -> {
            if (c.player.isRemoved()) return;
            for (LivingEntity e : Combat.foesAround(c.player, at, r.radius())) {
                Combat.hurtFrom(c.player, null, e, amount * r.share(), force, true);
                Combat.knockFrom(e, at, 0.5, 0.25);
            }
            Fx.at(c, FxKind.RING, at.add(0, -0.6, 0)).scale(r.radius()).ticks(10).plain().send();
            Fx.quake(c.level, at, r.radius() * 3, 1.2f);
        });
    }

    /** A patch of ground that keeps working: every half second for its life. */
    private static void linger(Cast c, Vec3 at, float amount, Rider r) {
        lingerAt(c, at, r.sub(), r.ticks(), r.radius(), r.dps() > 0 ? r.dps() : amount * 0.15f);
    }

    /**
     * A patch of ground at {@code at} that burns, slows, freezes, pulls, holds, heals allies or shocks ({@code kind}, as the
     * table's linger) every half second for {@code ticks}, {@code dps} a second. Steps' own code lays these too (a crater rim).
     */
    public static void lingerAt(Cast c, Vec3 at, String kind, int ticks, float radius, float dps) {
        lingerAt(c, at, kind, ticks, radius, dps, true);
    }

    /** As above; {@code show} false when the step draws its own ground (a fissure that burns). */
    public static void lingerAt(Cast c, Vec3 at, String kind, int ticks, float radius, float dps, boolean show) {
        int life = Math.max(10, ticks);
        for (int t = 10; t <= life; t += 10) {
            boolean last = t + 10 > life;
            Scheduler.after(t, () -> lingerPulse(c, at, radius, kind, dps * 0.5f, last));
        }
        if (!show) return;
        Fx.at(c, FxKind.GLYPH, at).scale(radius).ticks(life).plain().flag(FxEvent.QUIET).send();
        switch (kind) {
            case "burn" -> Fx.at(c, FxKind.FIRE_TRAIL, at).ticks(life).plain().flag(FxEvent.QUIET).send();
            case "frost" -> Fx.at(c, FxKind.FROST, at).scale(radius).plain().flag(FxEvent.QUIET).send();
            case "void" -> Fx.at(c, FxKind.VORTEX, at.add(0, 0.6, 0)).scale(radius).ticks(life).plain().flag(FxEvent.QUIET).send();
            default -> {
            }
        }
    }

    private static void lingerPulse(Cast c, Vec3 at, float radius, String kind, float dmg, boolean last) {
        if (c.player.isRemoved()) return;
        if (kind.equals("heal")) {
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, net.minecraft.world.phys.AABB.ofSize(at, radius * 2, 3, radius * 2),
                    e -> e == c.player || Combat.isAlly(c.player, e))) {
                e.heal(dmg);
            }
            return;
        }
        for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
            switch (kind) {
                case "burn" -> {
                    Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.HEAT, true);
                    e.igniteForSeconds(1);
                }
                case "frost" -> {
                    Moves.effect(e, MobEffects.SLOWNESS, 20, 2);
                    e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 20, e.getTicksFrozen() + 30));
                    if (dmg > 0) Combat.hurtFrom(c.player, null, e, dmg * 0.5f, null, true);
                }
                case "void" -> {
                    Vec3 pull = at.subtract(e.position());
                    if (pull.lengthSqr() > 0.5) Combat.push(e, pull.normalize().scale(0.35));
                    Combat.hurtFrom(c.player, null, e, dmg * 0.6f, null, true);
                }
                case "hold" -> Moves.effect(e, MobEffects.SLOWNESS, 20, 5);
                case "shock" -> {
                    Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.CHARGE, true);
                    if (last) Combat.stun(e, 6);
                }
                default -> Moves.effect(e, MobEffects.SLOWNESS, 20, 1);
            }
        }
    }

    private static void status(Cast c, LivingEntity target, Rider r, long now) {
        int ticks = Math.max(1, r.ticks());
        switch (r.sub()) {
            case "burn" -> target.igniteForSeconds(Math.max(1, ticks / 20f));
            case "slow" -> Moves.effect(target, MobEffects.SLOWNESS, ticks, Math.max(0, r.amp()));
            case "weak" -> Moves.effect(target, MobEffects.WEAKNESS, ticks, Math.max(0, r.amp()));
            case "stun" -> Combat.stun(target, ticks);
            case "root" -> Moves.effect(target, MobEffects.SLOWNESS, ticks, 6);
            case "lift" -> Combat.push(target, new Vec3(0, 0.55 + 0.1 * r.amp(), 0));
            case "blind" -> {
                Moves.effect(target, MobEffects.BLINDNESS, ticks, 0);
                if (target instanceof Mob m && m.getTarget() == c.player) m.setTarget(null);
            }
            case "mark" -> {
                Mark old = MARKS.get(target);
                boolean fresh = old == null || old.until() < now || !old.owner().equals(c.player.getUUID());
                MARKS.put(target, new Mark(c.player.getUUID(), now + ticks));
                // the rune over its head, for everyone near (once per mark, not on every pulse of a beam)
                if (fresh) Fx.at(c, FxKind.GLYPH, target.position().add(0, target.getBbHeight() + 0.4, 0)).caster(target).scale(0.5).ticks(ticks)
                        .flag(FxEvent.FOLLOW).plain().flag(FxEvent.QUIET).send();
            }
            default -> {
            }
        }
    }

    /** Damage from this player to a foe they marked is 10% higher (PowerEvents). */
    /** Marks {@code victim} for {@code by} until {@code until} (a step's mark outside a cast hit: Dominate's Marked Prey). */
    public static void mark(ServerPlayer by, LivingEntity victim, long until) {
        Mark old = MARKS.get(victim);
        if (old == null || old.until() < until) MARKS.put(victim, new Mark(by.getUUID(), until));
    }

    public static boolean marked(ServerPlayer by, LivingEntity victim, long now) {
        Mark m = MARKS.get(victim);
        return m != null && m.until() >= now && m.owner().equals(by.getUUID());
    }

    /** Clears marks (tests). */
    public static void clear() {
        MARKS.clear();
    }
}
