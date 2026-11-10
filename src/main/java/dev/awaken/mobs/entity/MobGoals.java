package dev.awaken.mobs.entity;

import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.core.api.Strikes;
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

    /**
     * Melee with a tell (SPEC §24): within reach the creature winds up first, plants its feet and glints (LIGHT), then
     * strikes if you are still in reach; step out and it whiffs. Lords and wardens throw every third blow as a HEAVY
     * haymaker (a longer, amber tell, half as hard again). Replaces vanilla's untold swing, which gave nothing to read.
     *
     * <p>A creature with a rhythm of its own (SPEC §60.8, {@code MobDef.blow}) tells each blow for its own time and may
     * string two or three together: after a blow, while its target is still near, it follows through for
     * {@link dev.awaken.mobs.def.MobDef#STRING_GAP} ticks, steps in and tells the next. Each blow of a rhythm is weighed
     * ({@code MobDef.blowWeight}) so the string does per second what one plain swing did.
     */
    static final class ToldMelee extends net.minecraft.world.entity.ai.goal.MeleeAttackGoal {
        private static final net.minecraft.resources.Identifier HAYMAKER = dev.awaken.Awaken.id("mobs_haymaker");
        private static final net.minecraft.resources.Identifier RHYTHM = dev.awaken.Awaken.id("mobs_rhythm");
        /** How far past its reach a target may be for the string to go on (it steps in after it). */
        private static final double STRING_REACH = 2.0;
        private final AwakenMob mob;
        private int tellLeft, swings, stringAt, gapLeft;
        private Strikes.Kind telling = Strikes.Kind.NONE;

        ToldMelee(AwakenMob mob) {
            this(mob, 1.0, false);
        }

        ToldMelee(AwakenMob mob, double speed, boolean followUnseen) {
            super(mob, speed, followUnseen);
            this.mob = mob;
        }

        /** Ticks a blow of this creature is told for: its own rhythm's, else its rank's. */
        static int tellTicks(AwakenMob mob, Strikes.Kind kind) {
            int own = mob.def().blowTell;
            int base = own > 0 ? own : rankTell(mob);
            return kind == Strikes.Kind.HEAVY ? base + 6 : base;
        }

        /** Ticks a plain blow is told for by rank (SPEC §24). */
        static int rankTell(AwakenMob mob) {
            return switch (mob.syncedRole()) {
                case BOSS, BREAK, WARDEN, NIGHTMARE -> 10;
                case ELITE -> 9;
                default -> 8;
            };
        }

        private boolean big() {
            return switch (mob.syncedRole()) {
                case BOSS, BREAK, WARDEN, NIGHTMARE -> true;
                default -> false;
            };
        }

        /**
         * It stands still to wind up, and standing still is not the chase ending: vanilla's goal gives up when its
         * path is done, which our own stop for the tell made true two ticks into every wind-up, so in play no plain blow
         * ever landed (the test drove the goal by hand and never met the selector). While it tells a blow, or follows
         * one through into the next of a string, it goes on as long as its target lives.
         */
        @Override
        public boolean canContinueToUse() {
            if (tellLeft > 0 || gapLeft > 0) {
                LivingEntity t = mob.getTarget();
                return t != null && t.isAlive();
            }
            return super.canContinueToUse();
        }

        @Override
        public void stop() {
            super.stop();
            if (tellLeft > 0) mob.setTell(0, Strikes.Kind.NONE);
            tellLeft = 0;
            endString();
        }

        private void endString() {
            stringAt = 0;
            gapLeft = 0;
            mob.blowAt = 0;
            mob.blowLast = true;
        }

        @Override
        public void tick() {
            if (mob.staggered()) {
                tellLeft = 0;
                endString();
                return;
            }
            if (gapLeft > 0) {
                // between two blows of a string: the first one's follow-through, then it steps in and tells the next
                LivingEntity t = mob.getTarget();
                AbilityRunner r = mob.runner();
                if (t == null || !t.isAlive() || (r != null && r.active() != null)) {
                    endString();
                    return;
                }
                mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
                mob.getNavigation().stop();
                if (--gapLeft == 0) {
                    if (!mob.isWithinMeleeAttackRange(t)) {
                        Vec3 to = t.position().subtract(mob.position()).multiply(1, 0, 1);
                        if (to.lengthSqr() > 1.0E-4) {
                            to = to.normalize().scale(0.42);
                            mob.setDeltaMovement(to.x, mob.getDeltaMovement().y, to.z);
                        }
                    }
                    tell();
                }
                return;
            }
            if (tellLeft > 0) {
                LivingEntity t = mob.getTarget();
                if (t != null) mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
                mob.getNavigation().stop();
                if (--tellLeft > 0) {
                    if ((tellLeft & 1) == 0) mob.setTell(tellLeft, telling);
                    return;
                }
                release(t);
                return;
            }
            super.tick();
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            AbilityRunner r = mob.runner();
            if (!canPerformAttack(target) || (r != null && r.active() != null)) return;
            stringAt = 0;
            tell();
        }

        /** Tells the blow it is about to throw (the first of a string, or the next). */
        private void tell() {
            telling = big() && ++swings % 3 == 0 ? Strikes.Kind.HEAVY : Strikes.Kind.LIGHT;
            tellLeft = tellTicks(mob, telling);
            mob.blowAt = stringAt;
            mob.setTell(tellLeft, telling);
            mob.getNavigation().stop();
            mob.playTell(telling, telling == Strikes.Kind.HEAVY, 1.0F);
        }

        private void release(@Nullable LivingEntity target) {
            Strikes.Kind kind = telling;
            mob.setTell(0, Strikes.Kind.NONE);
            resetAttackCooldown();
            // its string goes on while there are blows left in it and its target has not got clear away
            boolean more = stringAt + 1 < mob.def().blowChain && target != null && target.isAlive()
                    && mob.distanceToSqr(target) <= sq(Math.sqrt(reachSqr(target)) + STRING_REACH);
            mob.blowLast = !more;
            mob.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (more) {
                stringAt++;
                gapLeft = dev.awaken.mobs.def.MobDef.STRING_GAP;
            } else {
                stringAt = 0;
                // the blow is thrown: it takes up the chase again (its rest before the next is the attack cooldown)
                if (target != null && target.isAlive()) mob.getNavigation().moveTo(target, 1.0);
            }
            if (target == null || !target.isAlive() || !mob.isWithinMeleeAttackRange(target)) return; // stepped out: a whiff
            var dmg = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            boolean heavy = kind == Strikes.Kind.HEAVY && dmg != null;
            float weight = mob.def().blowWeight(rankTell(mob));
            boolean weighed = weight != 1.0F && dmg != null;
            if (heavy) dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HAYMAKER, 0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if (weighed) dmg.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RHYTHM, weight - 1.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            try {
                Strikes.deliver(mob, kind, () -> mob.doHurtTarget(getServerLevel(mob), target));
            } finally {
                if (heavy) dmg.removeModifier(HAYMAKER);
                if (weighed) dmg.removeModifier(RHYTHM);
            }
        }

        /** The square of how far its blow reaches this target (the same measure isWithinMeleeAttackRange uses, near enough). */
        private double reachSqr(LivingEntity target) {
            double r = mob.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.0;
            return r * r;
        }

        private static double sq(double v) {
            return v * v;
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
        private int retarget, attackCooldown, tellLeft = -1, diveTicks, airTicks;
        private double groundY = Double.MIN_VALUE;
        /** A kit flier's feet over the floor between blows: its move control flies no lower, and a dip takes it down to strike. */
        private static final double CLEARANCE = 0.35;
        private float angle;
        private boolean far;

        FlyHunt(AwakenMob mob) {
            this.mob = mob;
            this.angle = mob.getRandom().nextFloat() * Mth.TWO_PI;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { LivingEntity t = mob.getTarget(); return t != null && t.isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void stop() {
            // its target is gone mid-dive: the dive is over, and so is whatever it was telling
            mob.diving = false;
            if (tellLeft >= 0) mob.setTell(0, Strikes.Kind.NONE);
            tellLeft = -1;
        }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            if (mob.scriptedFight()) {
                scripted(t);
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (mob.grounded()) {
                // brought down: the dive it was telling is lost, and it waits a moment once up again
                if (tellLeft >= 0) mob.setTell(0, Strikes.Kind.NONE);
                tellLeft = -1;
                mob.diving = false;
                attackCooldown = Math.max(attackCooldown, 10);
                retarget = 0;
                return;
            }
            if (attackCooldown > 0) attackCooldown--;
            boolean ranged = mob.def().has(Trait.RANGED);
            double reach = (mob.getBbWidth() + t.getBbWidth()) * 0.5 + 1.3;
            // it dives only down a clear line: never into the leaves after you (it waits for you to come out)
            if (!ranged && attackCooldown <= 0 && !mob.staggered() && tellLeft < 0 && !diveClear(t, reach)) attackCooldown = 10;
            if (!ranged && attackCooldown <= 0 && !mob.staggered()) {
                // a told dive (SPEC §24): it rears for its tell, then dives; it gives up a dive that takes too long
                if (tellLeft < 0) {
                    tellLeft = ToldMelee.tellTicks(mob, Strikes.Kind.LIGHT);
                    mob.setTell(tellLeft, Strikes.Kind.LIGHT);
                    mob.playTell(Strikes.Kind.LIGHT, false, 1.0F);
                }
                if (tellLeft > 0) {
                    if (--tellLeft > 0 && (tellLeft & 1) == 0) mob.setTell(tellLeft, Strikes.Kind.LIGHT);
                    else if (tellLeft == 0) {
                        mob.setTell(0, Strikes.Kind.NONE);
                        diveTicks = 30;
                        mob.diving = true; // its body holds the dive's shape until it hits or gives it up
                    }
                    mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.6);
                    return;
                }
                Vec3 eye = t.getEyePosition().subtract(0, t.getBbHeight() * 0.3, 0);
                mob.getMoveControl().setWantedPosition(eye.x, eye.y, eye.z, 1.2);
                boolean hit = mob.distanceToSqr(t) < reach * reach;
                if (hit) Strikes.deliver(mob, Strikes.Kind.LIGHT, () -> mob.doHurtTarget(getServerLevel(mob), t));
                if (hit || --diveTicks <= 0) {
                    attackCooldown = 30 + mob.getRandom().nextInt(20);
                    tellLeft = -1;
                    retarget = 0;
                    mob.diving = false;
                }
                return;
            }
            if (--retarget > 0) return;
            retarget = 12;
            float r = ranged ? 7.0F : 3.5F + mob.getBbWidth();
            double pad = FlightRoom.pad(mob);
            for (int i = 0; i < 4; i++) {
                angle += 0.6F + mob.getRandom().nextFloat() * 0.4F;
                double x = t.getX() + Mth.cos(angle) * r, z = t.getZ() + Mth.sin(angle) * r;
                double y = t.getY() + 2.0 + mob.getRandom().nextFloat() * 2.0 + (mob.getBbHeight() > 2 ? 1.5 : 0);
                // round the target only where its body and its wings' clearance fit, and it can fly straight there
                if (FlightRoom.passes(mob, x, y, z, pad) && FlightRoom.clearWay(mob, x, y, z, Math.min(pad, 0.3))) {
                    mob.getMoveControl().setWantedPosition(x, y, z, 1.0);
                    return;
                }
            }
            // no room round it (it stands under the trees): a big flier hangs over the canopy above it, watching
            Vec3 over = FlightRoom.big(mob) ? FlightRoom.roomAbove(mob, t.getX(), t.getY() + 2.0, t.getZ()) : null;
            if (over != null && FlightRoom.clearWay(mob, over.x, over.y, over.z, 0.05)) {
                mob.getMoveControl().setWantedPosition(over.x, over.y, over.z, 0.8);
            } else {
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.6);
            }
        }

        /** Whether its body flies straight at the target until it is within reach, touching nothing on the way. */
        private boolean diveClear(LivingEntity t, double reach) {
            Vec3 eye = t.getEyePosition().subtract(0, t.getBbHeight() * 0.3, 0), d = eye.subtract(mob.position());
            double len = d.length();
            if (len <= reach) return true;
            return FlightRoom.sweep(mob, mob.getBoundingBox().inflate(0.1), d.scale((len - reach) / len)) >= 1.0;
        }

        /**
         * A kit flier (SPEC §35.3): its fight script's moves are its attacks, so no dives. It holds still while a move
         * plays (the capsules are drawn from where it is) and between moves drifts low round the target, its body floating
         * in its own model, now close enough to lash, now out where its gap-closer and its ranged moves reach.
         */
        private void scripted(LivingEntity t) {
            if (mob.fightBusy()) {
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.6);
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (--retarget > 0) return;
            retarget = 12;
            if (mob.getRandom().nextInt(4) == 0) far = !far;
            angle += 0.4F + mob.getRandom().nextFloat() * 0.3F;
            float r = (mob.getBbWidth() + t.getBbWidth()) * 0.5F + (far ? 6.0F : 1.0F);
            // level with where you stand (its body floats in its own model), so its blows reach; through a jump it holds
            // that height, and it follows you up only if you stay up
            if (t.onGround()) {
                groundY = t.getY();
                airTicks = 0;
            } else if (groundY == Double.MIN_VALUE) groundY = t.getY();
            else airTicks += 12;
            double y = (airTicks > 60 ? t.getY() - 1.0 : groundY) + CLEARANCE;
            mob.getMoveControl().setWantedPosition(t.getX() + Mth.cos(angle) * r, y, t.getZ() + Mth.sin(angle) * r, far ? 1.0 : 0.8);
        }
    }

    /**
     * Fliers without a target drift around their spot (or their arena), only to spots with room for their body and its
     * wings that they can reach in a straight line (FlightRoom). A big flier circles its home slowly, holds in open air
     * (its clearing, or over the canopy) and now and then settles on something solid for a few seconds.
     */
    static final class FlyWander extends Goal {
        /** Picks in a row that found nowhere to go before a wedged big flier works its way clear. */
        static final int WEDGED_AFTER = 3;
        private final AwakenMob mob;
        private float orbit;
        private int misses;
        private boolean perching;
        private int restUntil;

        FlyWander(AwakenMob mob) {
            this.mob = mob;
            this.orbit = mob.getRandom().nextFloat() * Mth.TWO_PI;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null || mob.idleHeld() || mob.getMoveControl().hasWanted()) return false;
            if (mob.getMoveControl() instanceof FlyerMoveControl flight && flight.gettingOut()) return false;
            if (perching) {
                // landed: rest a while, wings folding and opening
                perching = false;
                restUntil = mob.tickCount + 100 + mob.getRandom().nextInt(120);
            }
            if (mob.tickCount < restUntil) return false;
            return mob.getRandom().nextInt(reducedTickDelay(FlightRoom.big(mob) ? 20 : 30)) == 0;
        }

        @Override public boolean canContinueToUse() { return false; }

        @Override
        public void start() {
            Vec3 to = pick();
            if (to != null) {
                misses = 0;
                mob.getMoveControl().setWantedPosition(to.x, to.y, to.z, FlightRoom.big(mob) ? 0.45 : 0.6);
            } else if (++misses >= WEDGED_AFTER) {
                misses = 0;
                unwedge();
            }
        }

        /** The next idle spot, or null if none of a few tries has room and a clear way there. */
        @Nullable Vec3 pick() {
            boolean big = FlightRoom.big(mob);
            BlockPos centre = mob.hasHome() ? mob.getHomePosition() : mob.blockPosition();
            int spread = mob.hasHome() ? Math.min(12, mob.getHomeRadius()) : 8;
            for (int tries = 0; tries < 6; tries++) {
                double x, z;
                if (big) {
                    // slow circling round the home, nearer or wider each lap
                    orbit += 0.5F + mob.getRandom().nextFloat() * 0.5F;
                    double r = spread * (0.35 + mob.getRandom().nextFloat() * 0.65);
                    x = centre.getX() + 0.5 + Mth.cos(orbit) * r;
                    z = centre.getZ() + 0.5 + Mth.sin(orbit) * r;
                    if (mob.getRandom().nextFloat() < 0.15F) {
                        Vec3 perch = FlightRoom.perch(mob, x, z);
                        if (perch != null && FlightRoom.clearLine(mob, perch.x, perch.y, perch.z)) {
                            perching = true;
                            return perch;
                        }
                    }
                } else {
                    x = centre.getX() + 0.5 + (mob.getRandom().nextFloat() * 2 - 1) * spread;
                    z = centre.getZ() + 0.5 + (mob.getRandom().nextFloat() * 2 - 1) * spread;
                }
                int ground = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                double y = Math.min(ground + 2.0 + mob.getRandom().nextFloat() * 4.0, mob.getY() + 4.0);
                if (ground > mob.getY() + 12) y = mob.getY() + (mob.getRandom().nextFloat() - 0.5) * 2.0;
                if (big) y = Math.max(y, centre.getY() + 1.5 + mob.getRandom().nextFloat() * 3.0);
                Vec3 at = FlightRoom.spot(mob, x, y, z);
                if (at == null) continue;
                if (FlightRoom.clearLine(mob, at.x, at.y, at.z)) return at;
                // over the canopy but not straight there: rise out of the clearing first, cross next time
                if (big && at.y > mob.getY() + 1.0 && FlightRoom.fits(mob, mob.getX(), at.y, mob.getZ())
                        && FlightRoom.clearLine(mob, mob.getX(), at.y, mob.getZ())) {
                    return new Vec3(mob.getX(), at.y, mob.getZ());
                }
            }
            if (big) {
                // nowhere around has room: climb straight up out of where it is, if the way up is clear
                Vec3 up = FlightRoom.roomAbove(mob, mob.getX(), mob.getY(), mob.getZ());
                if (up != null && up.y > mob.getY() + 0.5 && FlightRoom.clearLine(mob, up.x, up.y, up.z)) return up;
            }
            return null;
        }

        /**
         * A big flier with no room and no way out it can fly straight (one saved inside a canopy, or boxed in under it)
         * works its way out by moving: out the nearest open side, or up through the leaves, slowly, the leaves shaking
         * (FlyerMoveControl, Unwedge). Never a teleport. A home with no room anywhere over it moves to where it got out.
         */
        private void unwedge() {
            if (!FlightRoom.big(mob) || FlightRoom.free(mob) || !(mob.getMoveControl() instanceof FlyerMoveControl flight)) return;
            flight.getClear();
            if (mob.hasHome() && FlightRoom.roomAbove(mob, mob.getHomePosition().getX() + 0.5, mob.getHomePosition().getY(), mob.getHomePosition().getZ() + 0.5) == null) {
                Vec3 up = FlightRoom.roomAbove(mob, mob.getX(), mob.getY(), mob.getZ());
                if (up != null) mob.setHomeTo(BlockPos.containing(up), mob.getHomeRadius());
            }
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

    /** To the last place it heard something, or last saw the one it lost (SPEC §47.11); there it looks about. */
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
