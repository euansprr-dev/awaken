package dev.awaken.hearth.echo;

import dev.awaken.hearth.HearthContent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * The mind every Echo gets. Its old goals are cleared (it no longer hates you) and replaced with: follow the owner,
 * strike whatever hurt the owner or whatever the owner struck, hunt nearby monsters, and never touch an ally. Works for
 * any mob type: walking, flying, melee or ranged.
 */
public final class EchoGoals {
    private EchoGoals() {}

    public static void install(Mob mob, boolean duty) {
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.setTarget(null);
        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(1, new Maintain(mob));
        mob.goalSelector.addGoal(2, new Attack(mob));
        mob.goalSelector.addGoal(9, new RandomLookAroundGoal(mob));
        if (!duty) {
            mob.goalSelector.addGoal(4, new FollowOwner(mob));
            mob.targetSelector.addGoal(1, new OwnerTarget(mob, true));
            mob.targetSelector.addGoal(2, new OwnerTarget(mob, false));
            mob.targetSelector.addGoal(3, new Hunt(mob, 10, null));
        }
    }

    static @Nullable ServerPlayer owner(Mob mob) {
        return Echoes.owner(mob);
    }

    /** Housekeeping every tick: no burning, no bad targets, a wisp now and then; strays without an owner dissolve. */
    static final class Maintain extends Goal {
        private final Mob mob;

        Maintain(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override public boolean canUse() { return true; }
        @Override public boolean canContinueToUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            if (mob.isOnFire()) mob.clearFire();
            LivingEntity t = mob.getTarget();
            if (t != null && !Echoes.canTarget(mob, t)) mob.setTarget(null);
            if (!(mob.level() instanceof ServerLevel sl)) return;
            if (mob.tickCount % 40 == 0) {
                sl.sendParticles(HearthContent.ECHO_WISP.get(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 1,
                        mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.005);
            }
            if (mob.tickCount % 20 == 0 && !Echoes.link(mob).duty()) {
                ServerPlayer o = owner(mob);
                if (o == null || o.level() != mob.level() || !o.isAlive()) {
                    Echoes.forget(Echoes.link(mob).echoId());
                    mob.discard();
                }
            }
        }
    }

    /** Melee or ranged, depending on what the Echo used to be. Strikes are credited to the owner. */
    static final class Attack extends Goal {
        private final Mob mob;
        private int cooldown;
        private int repath;

        Attack(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && Echoes.canTarget(mob, t);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null || !(mob.level() instanceof ServerLevel level)) return;
            mob.getLookControl().setLookAt(t, 30f, 30f);
            double d = mob.distanceToSqr(t);
            if (cooldown > 0) cooldown--;
            boolean ranged = mob instanceof RangedAttackMob;
            if (ranged && d > 9 && d < 256 && mob.getSensing().hasLineOfSight(t)) {
                mob.getNavigation().stop();
                if (cooldown <= 0) {
                    ((RangedAttackMob) mob).performRangedAttack(t, 1.0f);
                    cooldown = 40;
                }
                return;
            }
            double reach = mob.getBbWidth() * 0.5 + t.getBbWidth() * 0.5 + 1.3;
            if (d <= reach * reach) {
                if (cooldown <= 0) {
                    mob.swingForAttack(InteractionHand.MAIN_HAND);
                    Echoes.strike(level, mob, t);
                    cooldown = 20;
                }
            } else if (--repath <= 0) {
                repath = 10;
                mob.getNavigation().moveTo(t, 1.25);
            }
        }
    }

    /** Stay close to the owner; catch up by stepping out of the shadows when left far behind. */
    static final class FollowOwner extends Goal {
        private final Mob mob;
        private int repath;

        FollowOwner(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = owner(mob);
            return o != null && mob.getTarget() == null && o.level() == mob.level() && mob.distanceToSqr(o) > 36;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer o = owner(mob);
            return o != null && mob.getTarget() == null && o.level() == mob.level() && mob.distanceToSqr(o) > 9;
        }

        @Override
        public void tick() {
            ServerPlayer o = owner(mob);
            if (o == null) return;
            if (mob.distanceToSqr(o) > 24 * 24) {
                Vec3 at = o.position().add(o.getRandom().nextDouble() * 2 - 1, 0, o.getRandom().nextDouble() * 2 - 1);
                mob.snapTo(at.x, at.y, at.z, mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                mob.getNavigation().moveTo(o, 1.2);
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }
    }

    /** defend = whoever hurt the owner; otherwise whatever the owner just hit. */
    static final class OwnerTarget extends Goal {
        private final Mob mob;
        private final boolean defend;
        private int stamp;

        OwnerTarget(Mob mob, boolean defend) {
            this.mob = mob;
            this.defend = defend;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = owner(mob);
            if (o == null) return false;
            LivingEntity pick = defend ? o.getLastHurtByMob() : o.getLastHurtMob();
            int s = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
            return s != stamp && pick != null && o.tickCount - s < 100 && Echoes.canTarget(mob, pick) && mob.distanceToSqr(pick) < 32 * 32;
        }

        @Override
        public void start() {
            ServerPlayer o = owner(mob);
            if (o == null) return;
            mob.setTarget(defend ? o.getLastHurtByMob() : o.getLastHurtMob());
            stamp = defend ? o.getLastHurtByMobTimestamp() : o.getLastHurtMobTimestamp();
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    /** Picks the nearest monster within {@code radius} (of the owner, or of {@code centre} when given). */
    static final class Hunt extends Goal {
        private final Mob mob;
        private final double radius;
        private final @Nullable Vec3 centre;
        private int wait;

        Hunt(Mob mob, double radius, @Nullable Vec3 centre) {
            this.mob = mob;
            this.radius = radius;
            this.centre = centre;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || --wait > 0) return false;
            wait = 10;
            Vec3 c = centre;
            if (c == null) {
                ServerPlayer o = owner(mob);
                c = o != null && o.level() == mob.level() ? o.position() : mob.position();
            }
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                    e -> e instanceof Enemy && e.isAlive() && !Echoes.isEcho(e))) {
                if (!Echoes.canTarget(mob, e) || !mob.getSensing().hasLineOfSight(e)) continue;
                double d = mob.distanceToSqr(e);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best == null) return false;
            mob.setTarget(best);
            return false;
        }
    }

    /** Hunting around a fixed spot (a Guard at home, or a break it went to hold). */
    public static Goal huntAround(Mob mob, double radius, Vec3 centre) {
        return new Hunt(mob, radius, centre);
    }
}
