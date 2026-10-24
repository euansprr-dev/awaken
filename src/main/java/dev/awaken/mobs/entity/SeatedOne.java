package dev.awaken.mobs.entity;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * The Seated One: the god carved into the far wall of the prologue's ritual hall (docs/proposals/the-ritual.md). It
 * never moves, never takes damage and never dies; the story's ritual director (story/Ritual) drives what it does by
 * setting a stage. Its position is the floor under it, so anything measured from the mob (boss bars, distances) is
 * measured at floor level. Role {@link MobRole.Role#RITUAL}: never a trophy, an Echo, a kill or a boss count.
 */
public final class SeatedOne extends Mob {
    /** Eyes closed, a statue. */
    public static final int DORMANT = 0;
    /** The eyes open (a long drone), dust falls. */
    public static final int WAKING = 1;
    /** Awake: the head follows whoever it judges. */
    public static final int AWAKE = 2;
    /** The gaze charges (the eyes flare) and fires. */
    public static final int GAZE = 3;
    /** One hand rises and the hall's light drains into its palm. */
    public static final int RAISE = 4;
    /** The hand closes. */
    public static final int CLOSE = 5;
    /** After: the eyes close again. */
    public static final int SPENT = 6;

    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(SeatedOne.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SINCE = SynchedEntityData.defineId(SeatedOne.class, EntityDataSerializers.INT);

    /** Client: the local game time when the current stage was first seen (synced stage changes arrive late or on join). */
    public long clientStageSeen = Long.MIN_VALUE;
    private int lastSeenStage = -1;

    public SeatedOne(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
        setPermanentlyInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STAGE, DORMANT);
        b.define(SINCE, 0);
    }

    public int stage() {
        return entityData.get(STAGE);
    }

    /** Game time (as an int) the stage began, server clock. */
    public int stageSince() {
        return entityData.get(SINCE);
    }

    public void setStage(int stage) {
        if (stage == stage()) return;
        entityData.set(STAGE, stage);
        entityData.set(SINCE, (int) level().getGameTime());
    }

    /** Ticks into the current stage, on either side. */
    public float stageAge(float partial) {
        return Math.max(0, level().getGameTime() - (long) stageSince() + partial);
    }

    /** Turns the head toward a point (body stays put); the director calls this every tick it wants the head to move. */
    public void lookToward(double x, double y, double z, float maxStep) {
        double dx = x - getX(), dz = z - getZ(), dy = y - getEyeY();
        float want = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float rel = net.minecraft.util.Mth.wrapDegrees(want - yBodyRot);
        rel = net.minecraft.util.Mth.clamp(rel, -55F, 55F);
        float cur = net.minecraft.util.Mth.wrapDegrees(yHeadRot - yBodyRot);
        float next = cur + net.minecraft.util.Mth.clamp(rel - cur, -maxStep, maxStep);
        setYHeadRot(yBodyRot + next);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        setXRot(getXRot() + net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.clamp(pitch, -10F, 60F) - getXRot(), -maxStep, maxStep));
    }

    @Override
    public void tick() {
        super.tick();
        // body fixed to the throne whatever the head does
        yBodyRot = yBodyRotO = getYRot();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (level().isClientSide() && stage() != lastSeenStage) {
            lastSeenStage = stage();
            clientStageSeen = level().getGameTime();
        }
    }

    @Override
    protected void tickHeadTurn(float yBodyRotT) {
        // the head turns only when the director says so
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide()) setData(CoreApi.MOB_ROLE.get(), new MobRole(MobRole.Role.RITUAL, 0, "awaken:bell_crypt", Optional.empty(), ""));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_stage", stage());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(STAGE, in.getIntOr("awaken_stage", DORMANT));
    }
}
