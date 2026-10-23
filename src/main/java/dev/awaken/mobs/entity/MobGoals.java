package dev.awaken.mobs.entity;

import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** The small set of goals every AWAKEN creature is built from. None of them pathfind more than every few ticks. */
final class MobGoals {
    private MobGoals() {}

    /** Holds the mob still while it is dormant, fallen, fusing, watched (Vine Creakers) or winding up a rooted ability. */
    static final class Hold extends Goal {
        private final AwakenMob mob;

        Hold(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override public boolean canUse() { return mob.isHeld(); }
        @Override public boolean canContinueToUse() { return mob.isHeld(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { mob.getNavigation().stop(); }

        @Override
        public void tick() {
            if (mob.isWatchedNow() || mob.flag(AwakenMob.F_FALLEN) || mob.flag(AwakenMob.F_DORMANT)) {
                Vec3 v = mob.getDeltaMovement();
                mob.setDeltaMovement(0, v.y, 0);
            }
            AbilityRunner r = mob.runner();
            LivingEntity t = mob.getTarget();
            if (r != null && r.active() != null && t != null) mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        }
    }

    /** Ground ranged attackers: close to a comfortable distance, back off if crowded, keep line of sight. */
    static final class KeepRange extends Goal {
        private final AwakenMob mob;
        private final float preferred;
        private int repath, strafeTicks;
        private boolean strafeLeft;

        KeepRange(AwakenMob mob, float preferred) {
            this.mob = mob;
            this.preferred = preferred;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { LivingEntity t = mob.getTarget(); return t != null && t.isAlive(); }
        @Override public void stop() { mob.getNavigation().stop(); mob.setAggressive(false); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.setAggressive(true);
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (--repath > 0) return;
            repath = 10;
            double d = mob.distanceTo(t);
            boolean sees = mob.getSensing().hasLineOfSight(t);
            if (d > preferred + 3 || !sees) {
                mob.getNavigation().moveTo(t, 1.0);
            } else if (d < preferred - 4) {
                Vec3 away = mob.position().subtract(t.position()).normalize().scale(5);
                mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.1);
            } else {
                mob.getNavigation().stop();
                if (--strafeTicks <= 0) {
                    strafeTicks = 20 + mob.getRandom().nextInt(30);
                    strafeLeft = !strafeLeft;
                }
                mob.getMoveControl().strafe(0.0F, strafeLeft ? 0.4F : -0.4F);
            }
        }
    }

    /** Fliers: hover around the target, dive in to strike, pull back. Straight-line flight, no pathfinding. */
    static final class FlyHunt extends Goal {
        private final AwakenMob mob;
        private int retarget, attackCooldown;
        private float angle;

        FlyHunt(AwakenMob mob) {
            this.mob = mob;
            this.angle = mob.getRandom().nextFloat() * Mth.TWO_PI;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { LivingEntity t = mob.getTarget(); return t != null && t.isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (attackCooldown > 0) attackCooldown--;
            boolean ranged = mob.def().has(Trait.RANGED);
            double reach = (mob.getBbWidth() + t.getBbWidth()) * 0.5 + 1.3;
            if (!ranged && attackCooldown <= 0) {
                Vec3 eye = t.getEyePosition().subtract(0, t.getBbHeight() * 0.3, 0);
                mob.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.2);
                if (mob.distanceToSqr(t) < reach * reach) {
                    mob.doHurtTarget(getServerLevel(mob), t);
                    attackCooldown = 30 + mob.getRandom().nextInt(20);
                    retarget = 0;
                }
                return;
            }
            if (--retarget > 0) return;
            retarget = 12;
            angle += 0.6F + mob.getRandom().nextFloat() * 0.4F;
            float r = ranged ? 7.0F : 3.5F + mob.getBbWidth();
            double y = t.getY() + 2.0 + mob.getRandom().nextFloat() * 2.0 + (mob.getBbHeight() > 2 ? 1.5 : 0);
            mob.getMoveControl().setWantedPosition(t.getX() + Mth.cos(angle) * r, y, t.getZ() + Mth.sin(angle) * r, 1.0);
        }
    }

    /** Fliers without a target drift around their spot (or their arena). */
    static final class FlyWander extends Goal {
        private final AwakenMob mob;

        FlyWander(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && !mob.getMoveControl().hasWanted() && mob.getRandom().nextInt(reducedTickDelay(30)) == 0;
        }

        @Override public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            BlockPos centre = mob.hasHome() ? mob.getHomePosition() : mob.blockPosition();
            int spread = mob.hasHome() ? Math.min(12, mob.getHomeRadius()) : 8;
            double x = centre.getX() + 0.5 + (mob.getRandom().nextFloat() * 2 - 1) * spread;
            double z = centre.getZ() + 0.5 + (mob.getRandom().nextFloat() * 2 - 1) * spread;
            int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double y = Math.min(ground + 2.0 + mob.getRandom().nextFloat() * 4.0, mob.getY() + 4.0);
            if (ground > mob.getY() + 12) y = mob.getY() + (mob.getRandom().nextFloat() - 0.5) * 2.0;
            mob.getMoveControl().setWantedPosition(x, y, z, 0.6);
        }
    }

    /** Pickpockets: run from the player they robbed. */
    static final class Flee extends Goal {
        private final AwakenMob mob;
        private int repath;

        Flee(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.fleeTicks > 0 && mob.robbed != null; }

        @Override
        public void tick() {
            Player p = mob.robbed;
            if (p == null || --repath > 0) return;
            repath = 15;
            Vec3 away = mob.position().subtract(p.position());
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            away = away.normalize().scale(10);
            mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 1.35);
        }

        @Override public void stop() { mob.robbed = null; }
    }

    /** Cord-Eaters: home in on players projected into the Veil and bite their cord. */
    static final class SpiritHunt extends Goal {
        private final AwakenMob mob;
        private @Nullable ServerPlayer prey;
        private int scan, biteCooldown;

        SpiritHunt(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--scan > 0) return false;
            scan = 10;
            prey = null;
            double best = 32 * 32;
            for (ServerPlayer p : getServerLevel(mob).players()) {
                if (!Mobs.spiritHooks().isSpirit(p)) continue;
                double d = p.distanceToSqr(mob);
                if (d < best) { best = d; prey = p; }
            }
            return prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            return prey != null && prey.isAlive() && Mobs.spiritHooks().isSpirit(prey) && prey.distanceToSqr(mob) < 40 * 40;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            ServerPlayer p = prey;
            if (p == null) return;
            if (biteCooldown > 0) biteCooldown--;
            mob.getLookControl().setLookAt(p, 30.0F, 30.0F);
            mob.getMoveControl().setWantedPosition(p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(), 1.3);
            if (biteCooldown <= 0 && mob.distanceToSqr(p) < 2.2 * 2.2) {
                biteCooldown = 40;
                ServerLevel level = getServerLevel(mob);
                level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
                mob.playLevelSound(MobSounds.CORD_BITE.get(), 1.0F, 0.8F + mob.getRandom().nextFloat() * 0.3F);
                Mobs.spiritHooks().onCordBitten(p, mob);
            }
        }
    }

    /** Listeners are blind: every few ticks they listen for noisy players and remember where they heard them. */
    static final class HearTarget extends Goal {
        private final AwakenMob mob;
        private int scan;

        HearTarget(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        static boolean noisy(Player p) {
            if (p.isSpectator() || p.isCreative()) return false;
            if (p.isShiftKeyDown()) return p.hurtTime > 0;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            return p.isSprinting() || dx * dx + dz * dz > 0.012 || p.isSwinging() || p.hurtTime > 0 || !p.onGround();
        }

        @Override
        public boolean canUse() {
            if (--scan > 0) return false;
            scan = 5;
            ServerLevel level = getServerLevel(mob);
            Player loudest = null;
            double best = 24 * 24;
            for (ServerPlayer p : level.players()) {
                double d = p.distanceToSqr(mob);
                if (d < best && noisy(p)) { best = d; loudest = p; }
            }
            if (loudest == null) return false;
            if (mob.heardPos == null) mob.playLevelSound(MobSounds.LISTENER_HEAR.get(), 0.8F, 1.0F);
            mob.heardPos = loudest.position();
            mob.heardTicks = 120;
            if (best < 5 * 5) mob.setTarget(loudest);
            return false;
        }

        @Override public boolean canContinueToUse() { return false; }
    }

    /** Listeners walk to the last place they heard something. */
    static final class SeekSound extends Goal {
        private final AwakenMob mob;
        private int repath;

        SeekSound(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.heardPos != null && mob.getTarget() == null;
        }

        @Override
        public void tick() {
            Vec3 p = mob.heardPos;
            if (p == null || --repath > 0) return;
            repath = 10;
            if (mob.position().distanceToSqr(p) < 2.0) {
                mob.heardPos = null;
                return;
            }
            mob.getNavigation().moveTo(p.x, p.y, p.z, 1.1);
            LivingEntity t = mob.getTarget();
            if (t instanceof Player pl && !HearTarget.noisy(pl)) mob.setTarget(null);
        }
    }
}
