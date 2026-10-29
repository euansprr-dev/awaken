package dev.awaken.powers;

import dev.awaken.core.Forces;
import dev.awaken.powers.fx.FxKind;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Energy bolts simulated on the server without entities (Charged Shot, drones, turrets): each tick a bolt moves,
 * checks blocks and targets along its segment, and draws a short streak. Cheap enough for a swarm of drones.
 */
public final class Bolts {
    private Bolts() {}

    public static final class Bolt {
        final ServerLevel level;
        final ServerPlayer owner;
        final @Nullable Entity shooter;
        Vec3 pos;
        Vec3 vel;
        final float damage;
        final Forces.@Nullable Force force;
        final int argb;
        int life;
        /** Blast radius on impact (0 = single target). */
        final float blast;
        /** Wall bounces left (Ricochet). */
        public int bounces;
        /** Stun ticks on hit. */
        public int stun;
        /** The cast that fired it: its hits run the ladder's riders and its streak carries the power's looks (SPEC 36.4). */
        public @Nullable Cast cast;
        /** Foes it still goes through (a ladder's pierce), each further one taking {@link #pierceShare} of the last. */
        public int pierce;
        public float pierceShare = 1f;
        /** A cast's bolt whose power sends no landing of its own (a drone's shot): the bolt draws it. */
        public boolean land;
        float scaleNext = 1f;
        int hits;
        final Set<Integer> hit = new HashSet<>();

        Bolt(ServerLevel level, ServerPlayer owner, @Nullable Entity shooter, Vec3 pos, Vec3 vel, float damage, Forces.@Nullable Force force,
             int argb, int life, float blast) {
            this.level = level;
            this.owner = owner;
            this.shooter = shooter;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
            this.force = force;
            this.argb = argb;
            this.life = life;
            this.blast = blast;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    public static Bolt fire(ServerLevel level, ServerPlayer owner, @Nullable Entity shooter, Vec3 from, Vec3 dir, float speed, float damage,
                            Forces.@Nullable Force force, int argb, int life, float blast) {
        Bolt b = new Bolt(level, owner, shooter, from, dir.normalize().scale(speed), damage, force, argb, life, blast);
        BOLTS.add(b);
        return b;
    }

    static void tick() {
        if (BOLTS.isEmpty()) return;
        for (int i = BOLTS.size() - 1; i >= 0; i--) {
            Bolt b = BOLTS.get(i);
            if (step(b)) BOLTS.remove(i);
        }
    }

    static void clear() {
        BOLTS.clear();
    }

    /** Returns true when the bolt is spent. */
    private static boolean step(Bolt b) {
        if (--b.life < 0 || b.owner.isRemoved() || b.owner.level() != b.level) return true;
        Vec3 from = b.pos;
        Vec3 to = from.add(b.vel);
        if (!b.level.isLoaded(net.minecraft.core.BlockPos.containing(to))) return true;
        BlockHitResult wall = b.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        LivingEntity target = firstTarget(b, from, end);
        Vec3 streakTo = target != null ? target.getBoundingBox().getCenter() : end;
        if (b.cast != null) Fx.streak(b.level, from, streakTo, b.argb, 2, 3, PowerRegistry.index(b.cast.type) + 1, b.cast.powerLevel);
        else Fx.streak(b.level, from, streakTo, b.argb, 2, 3);
        if (target != null) {
            impact(b, target.getBoundingBox().getCenter(), target);
            if (b.pierce > 0) {
                // a ladder's pierce: through this one and on, weaker for each
                b.pierce--;
                b.scaleNext *= b.pierceShare;
                b.pos = streakTo;
                return false;
            }
            return true;
        }
        if (wall.getType() != HitResult.Type.MISS) {
            if (b.bounces > 0) {
                b.bounces--;
                Direction d = wall.getDirection();
                Vec3 n = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
                b.vel = b.vel.subtract(n.scale(2 * b.vel.dot(n)));
                b.pos = wall.getLocation().add(n.scale(0.05));
                b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.pos.x, b.pos.y, b.pos.z, 4, 0.05, 0.05, 0.05, 0.2);
                return false;
            }
            impact(b, wall.getLocation(), null);
            return true;
        }
        b.pos = to;
        return false;
    }

    private static @Nullable LivingEntity firstTarget(Bolt b, Vec3 from, Vec3 to) {
        AABB box = new AABB(from, to).inflate(1.0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        boolean overcharges = b.force == Forces.Force.CHARGE && b.shooter == b.owner;
        for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, box, x -> x != b.shooter && x != b.owner
                && !b.hit.contains(x.getId()) && (Combat.isFoeOrTarget(b.owner, x) || (overcharges && ForceInteractions.isOwnSummon(b.owner, x))))) {
            AABB eb = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = eb.contains(from) ? Optional.of(from) : eb.clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < bestD) {
                bestD = from.distanceToSqr(hit.get());
                best = e;
            }
        }
        return best;
    }

    private static void impact(Bolt b, Vec3 at, @Nullable LivingEntity target) {
        if (target != null) {
            b.hits++;
            b.hit.add(target.getId());
            float dmg = b.damage * b.scaleNext;
            if (b.cast != null) Combat.hurtBy(b.cast, b.shooter, target, dmg, b.force);
            else Combat.hurtFrom(b.owner, b.shooter, target, dmg, b.force, true);
            if (b.stun > 0) Combat.stun(target, b.stun);
        }
        if (b.blast > 0) {
            for (LivingEntity e : Combat.foesAround(b.owner, at, b.blast)) {
                if (e == target) continue;
                Combat.hurtFrom(b.owner, b.shooter, e, b.damage * 0.5f, b.force, true);
                Combat.knockFrom(e, at, 0.5, 0.25);
            }
            if (b.cast != null) Fx.at(b.cast, FxKind.NOVA, at).scale(b.blast).plain().send();
            else b.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            b.level.playSound(null, at.x, at.y, at.z, PowerSounds.BLAST.get(), SoundSource.PLAYERS, 0.7f, 1.4f);
        }
        if (b.cast != null) {
            // a cast's bolt: the power sends its own landing; the foes a pierce goes on through get theirs here
            if (b.hits > 1 || b.land) Fx.at(b.cast, FxKind.IMPACT, at).dir(b.vel).scale(b.land && b.hits <= 1 ? 0.6 : 0.9).plain().send();
            return;
        }
        b.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.25);
        b.level.sendParticles(Fx.dust(b.argb & 0xFFFFFF, 1.2f), at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
    }
}
