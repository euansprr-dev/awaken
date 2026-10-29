package dev.awaken.powers.ladder;

import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Moves that several ladder steps' own code shares (SPEC 36.3, "a step's code"): a pulse a beat later, a line that erupts, a
 * thrown body that hits what it crashes into, the nearest foes. Damage goes through {@link Combat#hurtFrom} like a rider's, so it
 * never starts riders again; every effect is sent {@code plain()} (no pose, no look layers of its own).
 */
public final class Steps {
    private Steps() {}

    /** After {@code delay} ticks, every foe within {@code radius} of {@code at} takes {@code dmg}, under a ring. */
    public static void pulseAt(Cast c, Vec3 at, double radius, float dmg, int delay, Forces.@Nullable Force force, double knock) {
        Scheduler.after(Math.max(1, delay), () -> {
            if (c.player.isRemoved()) return;
            for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
                Combat.hurtFrom(c.player, null, e, dmg, force, true);
                if (knock > 0) Combat.knockFrom(e, at, knock, 0.3);
            }
            Fx.at(c, FxKind.RING, at.add(0, -0.4, 0)).scale(radius).ticks(10).plain().send();
        });
    }

    /**
     * After {@code delay} ticks the line {@code a -> b} erupts: every foe within {@code width} of it takes {@code dmg} once.
     * {@code look} draws it (LIGHTNING for a snapped line of light, SLAM bursts along it for stone).
     */
    public static void lineAfter(Cast c, Vec3 a, Vec3 b, int delay, float dmg, double width, Forces.@Nullable Force force, FxKind look) {
        Scheduler.after(Math.max(1, delay), () -> {
            if (c.player.isRemoved()) return;
            for (LivingEntity e : Combat.alongLine(c.player, a, b, width)) {
                Combat.hurtFrom(c.player, null, e, dmg, force, true);
                Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).scale(0.6).plain().flag(FxEvent.QUIET).send();
            }
            if (look == FxKind.SLAM) {
                double len = a.distanceTo(b);
                Vec3 step = b.subtract(a).normalize();
                for (double d = 1; d < len; d += 1.6) Fx.at(c, FxKind.SLAM, a.add(step.scale(d))).scale(0.9).plain().flag(FxEvent.QUIET).send();
            } else {
                Fx.at(c, look, a).to(b).ticks(8).scale(1.2).plain().send();
            }
        });
    }

    /**
     * A line of shock rolling out from {@code from} along {@code dir}, a block and a half a tick and hugging the ground: every
     * foe within {@code width} of its front takes {@code dmg} once and is thrown up a little; a stone burst marks the front.
     */
    public static void roll(Cast c, Vec3 from, Vec3 dir, double len, float dmg, double width, Forces.@Nullable Force force) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-6) return;
        rollTick(c, from, flat.normalize(), len, 0, from.y, dmg, width, force, new HashSet<>());
    }

    private static void rollTick(Cast c, Vec3 from, Vec3 dir, double len, double done, double y, float dmg, double width,
                                 Forces.@Nullable Force force, Set<Integer> struck) {
        if (c.player.isRemoved() || done >= len) return;
        double next = Math.min(len, done + 1.5);
        Vec3 a = from.add(dir.scale(done));
        Vec3 b = from.add(dir.scale(next));
        double ground = groundY(c, b.x, y, b.z);
        if (Double.isNaN(ground)) return; // the line breaks off at a cliff or a wall
        b = new Vec3(b.x, ground, b.z);
        for (LivingEntity e : Combat.alongLine(c.player, new Vec3(a.x, y + 0.5, a.z), b.add(0, 0.5, 0), width)) {
            if (!struck.add(e.getId())) continue;
            Combat.hurtFrom(c.player, null, e, dmg, force, true);
            Combat.knockFrom(e, a, 0.6, 0.45);
            Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).scale(0.7).plain().flag(FxEvent.QUIET).send();
        }
        Fx.at(c, FxKind.SLAM, b).scale(1.1).plain().flag(FxEvent.QUIET).send();
        Vec3 at = b;
        Scheduler.after(1, () -> rollTick(c, new Vec3(from.x, at.y, from.z), dir, len, next, at.y, dmg, width, force, struck));
    }

    /** The top of the ground under (x, z) within two blocks of {@code y}, or NaN when there is none. */
    private static double groundY(Cast c, double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        int top = (int) Math.floor(y) + 2;
        for (int by = top; by >= top - 4; by--) {
            p.set(Math.floor(x), by, Math.floor(z));
            if (!c.level.getBlockState(p).getCollisionShape(c.level, p).isEmpty()) {
                return by == top ? Double.NaN : by + 1.0;
            }
        }
        return Double.NaN;
    }

    /** A body thrown hard: for {@code ticks} it hits the first foes it passes (each once, at most three) for {@code dmg}. */
    public static void carry(Cast c, LivingEntity flying, float dmg, int ticks) {
        Set<Integer> struck = new HashSet<>();
        struck.add(flying.getId());
        carryTick(c, flying, dmg, ticks, struck);
    }

    private static void carryTick(Cast c, LivingEntity flying, float dmg, int left, Set<Integer> struck) {
        if (left <= 0 || !flying.isAlive() || c.player.isRemoved() || struck.size() > 3) return;
        if (flying.getDeltaMovement().lengthSqr() > 0.04) {
            for (LivingEntity e : Combat.foesAround(c.player, flying.getBoundingBox().getCenter(), 1.3)) {
                if (!struck.add(e.getId())) continue;
                Combat.hurtFrom(c.player, flying, e, dmg, null, true);
                Combat.knockFrom(e, flying.position(), 0.9, 0.35);
                Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(flying.getDeltaMovement()).scale(0.9).plain().send();
            }
        }
        Scheduler.after(1, () -> carryTick(c, flying, dmg, left - 1, struck));
    }

    /** Up to {@code n} foes nearest {@code at} within {@code radius}, nearest first, without {@code skip}. */
    public static List<LivingEntity> nearest(Cast c, Vec3 at, double radius, int n, @Nullable LivingEntity skip) {
        List<LivingEntity> near = new ArrayList<>(Combat.foesAround(c.player, at, radius));
        if (skip != null) near.remove(skip);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        return near.size() > n ? near.subList(0, n) : near;
    }
}
