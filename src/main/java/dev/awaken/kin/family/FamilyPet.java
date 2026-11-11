package dev.awaken.kin.family;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Awa the cat or Dubu the dog (plan docs/family/PLAN.md), as a body to look at: it stands where an admin command puts
 * it and holds the pose it is told. This is the plan's preview only. Nothing in the game makes one, it has no life of its
 * own yet, and it changes nothing in play; what they do at the Hearth and out with you is built once the plan is approved.
 * Nothing can hurt it.
 */
public class FamilyPet extends PathfinderMob {
    /** The poses, by name, in the order of their numbers (the client picks the clip of the same name, else its idle). */
    public static final String[] ACTS = {"idle", "sit", "rest", "sleep", "roll", "nudge", "stretch", "hide", "carry"};
    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(FamilyPet.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(FamilyPet.class, EntityDataSerializers.INT);

    /** Where it has been told to walk to; null once it is there. Not saved. */
    public @Nullable Vec3 walkTo;

    public FamilyPet(EntityType<? extends FamilyPet> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.FOLLOW_RANGE, 16)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_ACT, 0);
        b.define(DATA_ACT_AT, 0);
    }

    public int act() {
        return entityData.get(DATA_ACT);
    }

    public int actAt() {
        return entityData.get(DATA_ACT_AT);
    }

    /** Starts a pose (again from its beginning when it is already in it). */
    public void act(int a) {
        entityData.set(DATA_ACT, Math.max(0, Math.min(ACTS.length - 1, a)));
        entityData.set(DATA_ACT_AT, tickCount);
    }

    public static int actOf(String name) {
        for (int i = 0; i < ACTS.length; i++) if (ACTS[i].equals(name)) return i;
        return -1;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WalkGoal());
    }

    /** Walks to where it was told, at {@code speed}, then stands. */
    final class WalkGoal extends Goal {
        WalkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return walkTo != null;
        }

        @Override
        public void tick() {
            Vec3 to = walkTo;
            if (to == null) return;
            if (distanceToSqr(to) < 1.0) {
                walkTo = null;
                getNavigation().stop();
            } else if (getNavigation().isDone()) getNavigation().moveTo(to.x, to.y, to.z, walkSpeed);
        }
    }

    /** The navigation's speed factor for the walk it was told (1 a walk, about 1.6 a run). */
    public double walkSpeed = 1.0;

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // nothing hurts them; only what bypasses every protection (the void's own kill, /kill) removes the body
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
