package dev.awaken.mobs.entity;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * One of the twelve kneeling statues of the ritual hall. Like the Seated One it has no AI of its own: the story's
 * ritual director moves it and cues its stages (kneel, rise, stand, advance, wind up, strike, glare). It can't be hurt.
 */
public final class RitualStatue extends Mob {
    public static final int KNEEL = 0, RISE = 1, STAND = 2, WALK = 3, WINDUP = 4, STRIKE = 5, GLARE = 6;

    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(RitualStatue.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SINCE = SynchedEntityData.defineId(RitualStatue.class, EntityDataSerializers.INT);

    public RitualStatue(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setPermanentlyInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STAGE, KNEEL);
        b.define(SINCE, 0);
    }

    public int stage() {
        return entityData.get(STAGE);
    }

    public int stageSince() {
        return entityData.get(SINCE);
    }

    public void setStage(int stage) {
        if (stage == stage()) {
            if (stage == WINDUP || stage == STRIKE) entityData.set(SINCE, (int) level().getGameTime()); // a new swing restarts the clip
            return;
        }
        entityData.set(STAGE, stage);
        entityData.set(SINCE, (int) level().getGameTime());
    }

    public float stageAge(float partial) {
        return Math.max(0, level().getGameTime() - (long) stageSince() + partial);
    }

    /** Turns the whole statue toward a point at most {@code maxStep} degrees this tick. */
    public void face(double x, double z, float maxStep) {
        float want = (float) (Math.toDegrees(Math.atan2(z - getZ(), x - getX())) - 90.0);
        float next = getYRot() + Mth.clamp(Mth.wrapDegrees(want - getYRot()), -maxStep, maxStep);
        setYRot(next);
        yBodyRot = next;
        setYHeadRot(next);
    }

    /** Turns only the head (the glare). */
    public void glareAt(double x, double z, float maxStep) {
        float want = (float) (Math.toDegrees(Math.atan2(z - getZ(), x - getX())) - 90.0);
        float rel = Mth.clamp(Mth.wrapDegrees(want - yBodyRot), -80F, 80F);
        float cur = Mth.wrapDegrees(yHeadRot - yBodyRot);
        setYHeadRot(yBodyRot + cur + Mth.clamp(rel - cur, -maxStep, maxStep));
    }

    /** One step of a slow, heavy walk toward a point: the director calls it every tick while the statue advances. */
    public void stepToward(Vec3 to, double speed) {
        Vec3 d = to.subtract(position()).multiply(1, 0, 1);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 step = d.scale(Math.min(len, speed) / len);
        move(net.minecraft.world.entity.MoverType.SELF, step);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            yBodyRot = getYRot();
        }
    }

    @Override
    protected void tickHeadTurn(float yBodyRotT) {}

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
        return distance < 160 * 160;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("awaken_stage", stage());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(STAGE, in.getIntOr("awaken_stage", KNEEL));
    }
}
