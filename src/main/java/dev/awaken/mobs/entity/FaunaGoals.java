package dev.awaken.mobs.entity;

import dev.awaken.mobs.def.FaunaTalent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * The overworld creatures' goals (see {@link FaunaMob}). Like {@link MobGoals}: straight-line flight for fliers, a
 * path at most every ten ticks for walkers, no entity scans except the finches' flock check every three seconds.
 */
final class FaunaGoals {
    private FaunaGoals() {}

    /** Run (or fly) from whatever scared it: a hit, a player charging at a flock, a torch for a night hunter. */
    static final class Scatter extends Goal {
        private final FaunaMob mob;
        private int repath;
        private boolean fluttered;

        Scatter(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.scareTicks > 0 && mob.scareFrom != null && mob.getTarget() == null && !mob.burrowing(); }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void start() {
            repath = 0;
            fluttered = false;
        }

        @Override
        public void tick() {
            Vec3 from = mob.scareFrom;
            if (from == null || --repath > 0) return;
            repath = 10;
            Vec3 away = mob.position().subtract(from);
            if (away.lengthSqr() < 1.0E-3) away = new Vec3(mob.getRandom().nextGaussian(), 0, mob.getRandom().nextGaussian());
            away = new Vec3(away.x, 0, away.z).normalize();
            if (mob.def().flies()) {
                double x = mob.getX() + away.x * 7, z = mob.getZ() + away.z * 7;
                double y = Math.max(FaunaMob.groundAt(mob.level(), x, z) + 2.5, mob.getY() + 1.0);
                mob.getMoveControl().setWantedPosition(x, Math.min(y, mob.getY() + 4.0), z, 1.5);
                return;
            }
            if (mob.talent() == FaunaTalent.FLOCK && !fluttered && mob.onGround()) {
                // A finch takes to the air for a few blocks.
                fluttered = true;
                mob.setDeltaMovement(away.x * 0.45, 0.55, away.z * 0.45);
                mob.playLevelSound(net.minecraft.sounds.SoundEvents.PARROT_FLY, 0.6F, 1.4F);
            }
            Vec3 p = DefaultRandomPos.getPosAway(mob, 10, 4, from);
            if (p != null) mob.getNavigation().moveTo(p.x, p.y, p.z, 1.5);
        }

        @Override
        public void stop() {
            if (!mob.def().flies()) mob.getNavigation().stop();
        }
    }

    /** Night hunters creep up on their prey crouched and slow, then let the melee goal and the pounce take over. */
    static final class Stalk extends Goal {
        private final FaunaMob mob;
        private int repath;

        Stalk(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.distanceToSqr(t) > 5.5 * 5.5 && mob.distanceToSqr(t) < 28 * 28;
        }

        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void start() {
            repath = 0;
            mob.stalking = true;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 20.0F, 20.0F);
            if (--repath > 0) return;
            repath = 10;
            mob.getNavigation().moveTo(t, 0.62);
        }

        @Override
        public void stop() {
            mob.stalking = false;
        }
    }

    /**
     * Lumen moths: drawn to a player holding a light, otherwise they circle the spot above the ore they found below,
     * otherwise they drift low. Flight is straight-line (no pathfinding); a new point every second or so.
     */
    static final class Dowse extends Goal {
        private final FaunaMob mob;
        private int repath;
        private float angle;

        Dowse(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 14 + mob.getRandom().nextInt(10);
            angle += 1.1F + mob.getRandom().nextFloat() * 0.8F;
            ServerLevel level = getServerLevel(mob);
            Player bearer = FaunaMob.lightBearer(level, mob, 12.0);
            if (bearer != null) {
                orbit(bearer.getX(), bearer.getEyeY() + 0.4, bearer.getZ(), 1.3F + mob.getRandom().nextFloat() * 0.6F, 0.8);
                return;
            }
            BlockPos ore = mob.lure;
            if (ore != null) {
                orbit(ore.getX() + 0.5, ore.getY() + 1.4 + mob.getRandom().nextFloat() * 1.4, ore.getZ() + 0.5, 1.6F, 0.6);
                return;
            }
            double x = mob.getX() + (mob.getRandom().nextFloat() * 2 - 1) * 5;
            double z = mob.getZ() + (mob.getRandom().nextFloat() * 2 - 1) * 5;
            double y = FaunaMob.groundAt(mob.level(), x, z) + 1.5 + mob.getRandom().nextFloat() * 2.5;
            mob.getMoveControl().setWantedPosition(x, Math.min(y, mob.getY() + 3), z, 0.55);
        }

        private void orbit(double cx, double cy, double cz, float r, double speed) {
            mob.getMoveControl().setWantedPosition(cx + Mth.cos(angle) * r, cy + Mth.sin(angle * 1.7F) * 0.35, cz + Mth.sin(angle) * r, speed);
        }
    }

    /** Bloom sprites fly to the crop they found, hover over it for two seconds, and it grows. */
    static final class Tend extends Goal {
        private final FaunaMob mob;
        private int hover, repath;

        Tend(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.lure != null && mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void start() {
            hover = 0;
            repath = 0;
        }

        @Override
        public void tick() {
            BlockPos crop = mob.lure;
            if (crop == null) return;
            double cx = crop.getX() + 0.5, cy = crop.getY() + 1.1, cz = crop.getZ() + 0.5;
            if (mob.distanceToSqr(cx, cy, cz) > 1.6 * 1.6) {
                if (--repath <= 0) {
                    repath = 10;
                    mob.getMoveControl().setWantedPosition(cx, cy, cz, 0.8);
                }
                if (mob.distanceToSqr(cx, cy, cz) > 30 * 30) mob.lure = null;
                return;
            }
            ServerLevel level = getServerLevel(mob);
            if (hover++ % 5 == 0) level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, cx, cy - 0.4, cz, 2, 0.25, 0.1, 0.25, 0.0);
            if (hover >= 40) {
                mob.tend(level, crop);
                mob.lure = null;
            }
        }
    }

    /** Frostfinches: hop towards a lit campfire they found, otherwise keep near their flock. */
    static final class Flock extends Goal {
        private final FaunaMob mob;
        private int check;

        Flock(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.scareTicks > 0 || !mob.getNavigation().isDone()) return false;
            if (--check > 0) return false;
            check = 60 + mob.getRandom().nextInt(40);
            BlockPos fire = mob.lure;
            if (fire != null && mob.distanceToSqr(Vec3.atCenterOf(fire)) > 3.5 * 3.5) {
                double a = mob.getRandom().nextFloat() * Mth.TWO_PI;
                return mob.getNavigation().moveTo(fire.getX() + 0.5 + Mth.cos((float) a) * 2.2, fire.getY(), fire.getZ() + 0.5 + Mth.sin((float) a) * 2.2, 0.9);
            }
            if (fire != null) {
                mob.graze = 40 + mob.getRandom().nextInt(60);
                return false;
            }
            List<FaunaMob> flock = getServerLevel(mob).getEntitiesOfClass(FaunaMob.class, FaunaMob.around(mob, 10),
                    m -> m != mob && m.getType() == mob.getType());
            if (flock.isEmpty()) return false;
            double x = 0, z = 0;
            for (FaunaMob m : flock) {
                x += m.getX();
                z += m.getZ();
            }
            x /= flock.size();
            z /= flock.size();
            if (mob.distanceToSqr(x, mob.getY(), z) < 4 * 4) return false;
            return mob.getNavigation().moveTo(x, mob.getY(), z, 0.9);
        }

        @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone() && mob.scareTicks <= 0; }

        @Override
        public void tick() {
            // Small hops: birds don't walk.
            if (mob.onGround() && mob.getRandom().nextInt(6) == 0) mob.setDeltaMovement(mob.getDeltaMovement().add(0, 0.28, 0));
        }
    }

    /** Dune rays: wide loops a block or two over the sand, the loop's centre drifting slowly. */
    static final class Glide extends Goal {
        private final FaunaMob mob;
        private float angle;
        private double cx, cz;
        private int repath;
        private boolean centred;

        Glide(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public boolean canContinueToUse() { return canUse(); }

        @Override
        public void start() {
            if (!centred) {
                cx = mob.getX();
                cz = mob.getZ();
                centred = true;
            }
        }

        @Override
        public void tick() {
            if (--repath > 0) return;
            repath = 8;
            angle += 0.42F;
            cx += Mth.cos(angle * 0.07F) * 0.4;
            cz += Mth.sin(angle * 0.05F) * 0.4;
            if (mob.hasHome()) {
                BlockPos h = mob.getHomePosition();
                if (h.distToCenterSqr(cx, h.getY(), cz) > 400) {
                    cx = h.getX();
                    cz = h.getZ();
                }
            }
            float r = 6.0F + Mth.sin(angle * 0.3F) * 2.0F;
            double x = cx + Mth.cos(angle) * r, z = cz + Mth.sin(angle) * r;
            double y = FaunaMob.groundAt(mob.level(), x, z) + 1.3 + Mth.sin(angle * 0.9F) * 0.4;
            mob.getMoveControl().setWantedPosition(x, y, z, 0.7);
        }
    }

    /** Tortoises and stags stop to graze now and then (and the tortoise sleeps at night). */
    static final class Graze extends Goal {
        private final FaunaMob mob;

        Graze(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.scareTicks > 0 || !mob.onGround()) return false;
            if (mob.graze > 0) return true;
            int odds = mob.level().isDarkOutside() && mob.talent() == FaunaTalent.GRAZE ? 60 : 300;
            if (mob.getRandom().nextInt(reducedTickDelay(odds)) != 0) return false;
            mob.graze = mob.level().isDarkOutside() && mob.talent() == FaunaTalent.GRAZE ? 600 : 70 + mob.getRandom().nextInt(80);
            return true;
        }

        @Override public boolean canContinueToUse() { return mob.graze > 0 && mob.getTarget() == null && mob.scareTicks <= 0; }
        @Override public void start() { mob.getNavigation().stop(); }
        @Override public void stop() { mob.graze = 0; }
    }

    /** A burrowing scarab holds still while it digs in. */
    static final class Burrow extends Goal {
        private final FaunaMob mob;

        Burrow(FaunaMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override public boolean canUse() { return mob.burrowing(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { mob.getNavigation().stop(); }

        @Override
        public void tick() {
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(0, v.y, 0);
        }
    }
}
