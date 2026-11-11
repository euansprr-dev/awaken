package dev.awaken.kin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Aya the cat or Dubu the dog (SPEC §69): the Hearth's family. They walk up to a household's first Hearth, live there
 * doing nothing useful, come out with whoever asks them and stay when told. Nothing can hurt one, nothing can take one
 * as a target, and neither ever fights. What one is doing is a synced pose the client turns into its clips; what it
 * decides is {@link FamilyBrain}; where it belongs is the world's record ({@link FamilyWorld}), which a body obeys: a
 * body the record does not name removes itself, and a body that is lost is made again from it.
 *
 * <p>A body with no owner is one an admin placed to be looked at ({@code /awaken admin family place}): it holds its pose.
 */
public class FamilyPet extends PathfinderMob implements OwnableEntity {
    /** The poses, by name, in the order of their numbers (the client picks the clip of the same name, else its idle). */
    public static final String[] ACTS = {"idle", "sit", "rest", "sleep", "roll", "nudge", "stretch", "hide", "carry", "song"};
    public static final int IDLE = 0, SIT = 1, REST = 2, SLEEP = 3, ROLL = 4, NUDGE = 5, STRETCH = 6, HIDE = 7, CARRY = 8, SONG = 9;
    private static final EntityDataAccessor<Integer> DATA_ACT = SynchedEntityData.defineId(FamilyPet.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACT_AT = SynchedEntityData.defineId(FamilyPet.class, EntityDataSerializers.INT);

    private @Nullable FamilyKind kind;
    /** Whose Hearth it belongs to (the Hearth's owner); null for a body placed to be looked at. */
    public @Nullable UUID owner;

    // ---- what it has in mind (never saved: a body that loads starts its day afresh) -------------------------------------
    /** Its own day at home: what it is doing, until when, and where. */
    public int activity;
    public long until;
    public @Nullable BlockPos spot;
    /** Who it is walking up to for the first time, and how far that has got. */
    public @Nullable UUID arriveTo;
    public int arriveStage;
    public long arriveUntil;
    /** Who has just come home, to be greeted, and from when. */
    public @Nullable UUID greet;
    public long greetAt;
    /** Who Dubu is walking to the yard's edge. */
    public @Nullable UUID escort;
    public long escortUntil;
    /** The tower's song: until when, and where it rings from. */
    public long songUntil;
    public @Nullable BlockPos songAt;
    /** A fight near: it keeps back until this passes, then comes back to you. */
    public long waryUntil;
    public boolean wasWary, returning;
    /** When the one it is with last moved (they sit once you have stopped a while). */
    public long stillSince;
    /** Brain ticks without getting anywhere, and where it was last. */
    public int stuck, leaving;
    public Vec3 lastAt = Vec3.ZERO;
    /** Petting has a moment's rest between two. */
    public long pettedAt;
    /** Told to walk somewhere by an admin ({@code /awaken admin family walk}); null once there. */
    public @Nullable Vec3 walkTo;
    public double walkSpeed = 1.0;

    public FamilyPet(EntityType<? extends FamilyPet> type, Level level) {
        super(type, level);
        // lava and fire are never walked into; Aya will not swim, Dubu will
        setPathfindingMalus(PathType.LAVA, -1F);
        setPathfindingMalus(PathType.FIRE, -1F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, 8F);
        setPathfindingMalus(PathType.DAMAGING, -1F);
        if (kind() == FamilyKind.AYA) setPathfindingMalus(PathType.WATER, -1F);
    }

    public FamilyKind kind() {
        if (kind == null) kind = getType() == FamilyContent.DUBU.get() ? FamilyKind.DUBU : FamilyKind.AYA;
        return kind;
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 1.0);
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

    /** Takes a pose, or keeps the one it is in. */
    public void act(int a) {
        if (act() != a) again(a);
    }

    /** Starts a pose from its beginning, even the one it is in (a second hello still plays). */
    public void again(int a) {
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
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        FamilyBrain.tick(level, this);
    }

    // ---- yours ----------------------------------------------------------------------------------------------------------

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return owner == null ? null : EntityReference.of(owner);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || owner == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) FamilyService.used(sp, this, sp.isSecondaryUseActive());
        return InteractionResult.SUCCESS;
    }

    // ---- nothing hurts them ---------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) && owner != null) {
            // the void: it is put back where it belongs instead
            FamilyService.rescue(level, this);
            return false;
        }
        // only what bypasses every protection (/kill) takes the body, and the record makes it again
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
    }

    @Override
    public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
        // nothing knocks them about
    }

    @Override
    protected void doPush(Entity entity) {
        // they never push you, a foe or each other
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    // ---- saving (schema 1): only whose it is; the rest is the world's record -------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("family_schema", 1);
        out.storeNullable("family_owner", UUIDUtil.STRING_CODEC, owner);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        owner = in.read("family_owner", UUIDUtil.STRING_CODEC).orElse(null);
    }
}
