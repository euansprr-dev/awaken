package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * P3's goals: a trusted mount's rest and follow, the water creatures' school, drift, flight and the otter's fetch, a
 * big swimmer's hunt, the cave creatures' chime walk, vine walk, ceiling cling and the hushling's ears. Every search is
 * a small box or a handful of block probes every second or two; nothing scans the level per tick.
 */
final class Fauna3Goals {
    private Fauna3Goals() {}

    // ---- mounts ------------------------------------------------------------------------------------------------------

    /** A resting mount stays near where it was told to rest (at a Hearth, or anywhere). */
    static final class Rest extends Goal {
        private final WildMount mob;

        Rest(WildMount mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            BlockPos at = mob.restAt();
            return mob.resting() && at != null && !mob.isVehicle() && mob.getTarget() == null && at.distToCenterSqr(mob.position()) > 5 * 5;
        }

        @Override
        public void start() {
            BlockPos at = mob.restAt();
            if (at != null) moveTo(mob, Vec3.atBottomCenterOf(at), 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && !mob.getNavigation().isDone();
        }
    }

    /** A trusted mount follows its owner at a walk (it never wanders off after them more than 40 blocks). */
    static final class FollowOwner extends Goal {
        private final WildMount mob;
        private @Nullable Player owner;
        private int repath;

        FollowOwner(WildMount mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mob.trusted() || mob.resting() || mob.isVehicle() || mob.getTarget() != null) return false;
            UUID id = mob.owner();
            Player p = id == null ? null : mob.level().getPlayerByUUID(id);
            if (p == null || p.isSpectator() || p.isPassenger()) return false;
            double d = p.distanceToSqr(mob);
            if (d < 7 * 7 || d > 40 * 40) return false;
            owner = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && !mob.isVehicle() && !mob.resting() && owner.distanceToSqr(mob) > 4 * 4 && owner.distanceToSqr(mob) < 48 * 48;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
            if (--repath > 0) return;
            repath = 10;
            moveTo(mob, owner.position(), owner.distanceToSqr(mob) > 14 * 14 ? 1.4 : 1.1);
        }
    }

    /** Walks or swims there (navigation), or flies straight (fliers steer their move control). */
    static void moveTo(FaunaMob mob, Vec3 at, double speed) {
        if (mob.def().flies()) {
            double y = Math.max(at.y + 1.2, FaunaMob.groundAt(mob.level(), at.x, at.z) + 1.0);
            mob.getMoveControl().setWantedPosition(at.x, y, at.z, speed);
        } else {
            mob.getNavigation().moveTo(at.x, at.y, at.z, speed);
        }
    }

    // ---- water ---------------------------------------------------------------------------------------------------------

    /**
     * Glimmerfin school: each fish follows the nearest leader (a fish with no leader of its own) at its own small
     * offset; leaders wander the water. Looked up every two seconds in a small box.
     */
    static final class Shoal extends Goal {
        private final WaterFauna mob;
        private int look, repath;

        Shoal(WaterFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.isInWater() || mob.scareTicks > 0) return false;
            if (--look <= 0) {
                look = 40 + mob.getRandom().nextInt(20);
                findLeader();
            }
            return mob.leader != null && mob.leader.isAlive() && mob.leader.distanceToSqr(mob) > 2.5 * 2.5;
        }

        private void findLeader() {
            if (mob.leader != null && mob.leader.isAlive() && mob.leader.leader == null && mob.leader.distanceToSqr(mob) < 16 * 16) return;
            mob.leader = null;
            List<WaterFauna> near = mob.level().getEntitiesOfClass(WaterFauna.class, mob.getBoundingBox().inflate(10),
                    o -> o != mob && o.getType() == mob.getType() && o.isAlive() && o.leader == null);
            WaterFauna best = null;
            double bd = Double.MAX_VALUE;
            for (WaterFauna o : near) {
                if (o.followers() >= 7) continue;
                double d = o.distanceToSqr(mob);
                if (d < bd && o.getId() < mob.getId()) {
                    bd = d;
                    best = o;
                }
            }
            mob.leader = best;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.leader != null && mob.leader.isAlive() && mob.isInWater() && mob.scareTicks <= 0 && mob.leader.distanceToSqr(mob) > 1.5 * 1.5;
        }

        @Override
        public void tick() {
            if (--repath > 0 || mob.leader == null) return;
            repath = 6;
            int id = mob.getId();
            double ox = ((id * 37) % 5 - 2) * 0.7, oy = ((id * 13) % 3 - 1) * 0.5, oz = ((id * 23) % 5 - 2) * 0.7;
            Vec3 at = mob.leader.position().add(ox, oy, oz);
            mob.getMoveControl().setWantedPosition(at.x, at.y, at.z, 1.1);
        }
    }

    /** A startled swimmer darts away from what startled it. */
    static final class SwimFlee extends Goal {
        private final WaterFauna mob;
        private int repath;

        SwimFlee(WaterFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.scareTicks > 0 && mob.scareFrom != null && mob.isInWater();
        }

        @Override
        public void tick() {
            if (--repath > 0 || mob.scareFrom == null) return;
            repath = 8;
            Vec3 away = mob.position().subtract(mob.scareFrom);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(mob.getRandom().nextGaussian(), 0, mob.getRandom().nextGaussian());
            Vec3 to = mob.position().add(away.normalize().scale(6.0)).add(0, (mob.getRandom().nextFloat() - 0.6) * 2.0, 0);
            if (!mob.level().getFluidState(BlockPos.containing(to)).is(FluidTags.WATER)) to = mob.position().add(away.normalize().scale(3.0));
            mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.6);
        }
    }

    /** A jellyfish drifts slowly, a few blocks at a time; at night it rises toward the surface. */
    static final class Drift extends Goal {
        private final WaterFauna mob;
        private int wait;

        Drift(WaterFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!mob.isInWater() || --wait > 0) return false;
            wait = 60 + mob.getRandom().nextInt(60);
            return true;
        }

        @Override
        public void start() {
            boolean night = mob.level().isDarkOutside();
            int up = Swim.depthBelowSurface(mob.level(), mob.blockPosition(), 16);
            double dy = night ? Math.min(3, Math.max(0, up - 2)) : (mob.getRandom().nextFloat() - 0.5) * 3.0;
            Vec3 to = mob.position().add((mob.getRandom().nextFloat() - 0.5) * 6.0, dy, (mob.getRandom().nextFloat() - 0.5) * 6.0);
            if (mob.level().getFluidState(BlockPos.containing(to)).is(FluidTags.WATER)) mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, 0.6);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    /** The otter's fetch, driven by {@link WaterFauna#fetchStep}: dive, rummage, bring it to the bank, drop it. */
    static final class Fetch extends Goal {
        private final WaterFauna mob;

        Fetch(WaterFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.fetchStage > 0 && mob.getTarget() == null;
        }

        @Override
        public void tick() {
            mob.fetchStep();
        }

        @Override
        public void stop() {
            if (mob.getTarget() != null) mob.fetchAbort();
        }
    }

    /** A swimmer goes for its target through the water and bites when it is close (no path needed). */
    static final class SwimHunt extends Goal {
        private final FaunaMob mob;
        private int cooldown, repath;

        SwimHunt(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.isInWater();
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (cooldown > 0) cooldown--;
            double reach = mob.getBbWidth() * 0.5 + t.getBbWidth() * 0.5 + 1.2;
            if (--repath <= 0) {
                repath = 5;
                double y = t.getY() + t.getBbHeight() * 0.4;
                // stay in the water: a target above the surface is struck from just below it
                int surface = mob.getBlockY() + Swim.depthBelowSurface(mob.level(), mob.blockPosition(), 24);
                y = Math.min(y, surface + 0.4);
                mob.getMoveControl().setWantedPosition(t.getX(), y, t.getZ(), 1.3);
            }
            if (cooldown <= 0 && mob.distanceToSqr(t) < reach * reach && !mob.isHeld() && mob.level() instanceof ServerLevel level) {
                mob.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
                mob.doHurtTarget(level, t);
                cooldown = 24;
            }
        }
    }

    // ---- caves ---------------------------------------------------------------------------------------------------------

    /** A chime cricket that knows of ore walks to it and stays beside it (where it sings). */
    static final class Chime extends Goal {
        private final CaveFauna mob;
        private int repath;

        Chime(CaveFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.lure != null && mob.scareTicks <= 0 && mob.lure.distToCenterSqr(mob.position()) > 2.5 * 2.5;
        }

        @Override
        public void tick() {
            if (--repath > 0 || mob.lure == null) return;
            repath = 20;
            mob.getNavigation().moveTo(mob.lure.getX() + 0.5, mob.lure.getY(), mob.lure.getZ() + 0.5, 1.0);
        }
    }

    /** A wickling walks under the vine it found and ripens it ({@link CaveFauna#ripen}). */
    static final class Ripen extends Goal {
        private final CaveFauna mob;
        private int repath, ticks;

        Ripen(CaveFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.lure != null && mob.scareTicks <= 0 && mob.getTarget() == null;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void tick() {
            BlockPos at = mob.lure;
            if (at == null) return;
            if (++ticks > 400) {
                mob.lure = null; // could not get there
                return;
            }
            double dx = at.getX() + 0.5 - mob.getX(), dz = at.getZ() + 0.5 - mob.getZ();
            if (dx * dx + dz * dz < 1.6 * 1.6 && mob.getY() <= at.getY()) {
                mob.getNavigation().stop();
                mob.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
                if (mob.level() instanceof ServerLevel level) mob.ripen(level, at);
                return;
            }
            if (--repath > 0) return;
            repath = 20;
            mob.getNavigation().moveTo(at.getX() + 0.5, mob.getY(), at.getZ() + 0.5, 1.0);
        }
    }

    /**
     * A drip lurker leaps up to the cave roof and hangs there like a dripstone fang; it drops on a player who walks
     * beneath it in the dark. A player standing in torchlight is never dropped on (the light rule).
     */
    static final class Cling extends Goal {
        private final CaveFauna mob;
        private int cooldown, climb;
        private int ceiling;

        Cling(CaveFauna mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.hanging()) return true;
            if (--cooldown > 0 || mob.getTarget() != null || mob.isInWater() || !mob.onGround()) return false;
            cooldown = 60 + mob.getRandom().nextInt(80);
            int c = mob.ceilingAbove(3, 9);
            if (c == Integer.MIN_VALUE) return false;
            ceiling = c;
            return true;
        }

        @Override
        public void start() {
            if (mob.hanging()) return;
            climb = 1;
            double h = ceiling - (mob.getY() + mob.getBbHeight());
            mob.getNavigation().stop();
            mob.setDeltaMovement(0, 0.25 + 0.115 * h, 0);
            mob.needsSync = true;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.hanging() || (climb > 0 && climb <= 30 && mob.getTarget() == null);
        }

        @Override
        public void stop() {
            climb = 0;
        }

        @Override
        public void tick() {
            if (!mob.hanging()) {
                climb++;
                boolean there = mob.getY() + mob.getBbHeight() >= ceiling - 0.3;
                if (there || (climb > 3 && mob.verticalCollision && mob.getDeltaMovement().y >= -0.01 && !mob.onGround())) mob.hang(ceiling);
                else if (climb > 3 && mob.onGround()) climb = 31; // fell short
                return;
            }
            if ((mob.tickCount + mob.getId()) % 5 != 0 || !(mob.level() instanceof ServerLevel level)) return;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isCreative()) continue;
                double dx = p.getX() - mob.getX(), dz = p.getZ() - mob.getZ(), dy = mob.getY() - p.getY();
                if (dx * dx + dz * dz < 1.8 * 1.8 && dy > 0 && dy < 9 && FaunaMob.inDarkness(p) && mob.hasLineOfSight(p)) {
                    mob.drop(p);
                    return;
                }
            }
        }
    }

    /** A hushling hears: the noisiest player within 20 blocks who stands in the dark; close enough and it attacks. */
    static final class HearInDark extends Goal {
        private final AwakenMob mob;
        private int scan;

        HearInDark(AwakenMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (--scan > 0) return false;
            scan = 5;
            if (!(mob.level() instanceof ServerLevel level)) return false;
            Player loudest = null;
            double best = 20 * 20;
            for (ServerPlayer p : level.players()) {
                double d = p.distanceToSqr(mob);
                if (d < best && MobGoals.HearTarget.noisy(p) && FaunaMob.inDarkness(p)) {
                    best = d;
                    loudest = p;
                }
            }
            if (loudest == null) return false;
            if (mob.heardPos == null) mob.playLevelSound(dev.awaken.mobs.MobSounds.LISTENER_HEAR.get(), 0.6F, 1.3F);
            mob.heardPos = loudest.position();
            mob.heardTicks = 120;
            if (best < 8 * 8) mob.setTarget(loudest);
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
