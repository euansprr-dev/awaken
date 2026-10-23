package dev.awaken.veil.entity;

import dev.awaken.veil.Veil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One of the dead, standing where it fell. Exists only near a spirit and is only ever sent to players who are out of
 * their bodies, so the living never see it. The Veil places and removes these (see {@code Spirits}).
 */
public class VeilSpirit extends Mob {
    private static final EntityDataAccessor<String> DATA_SPIRIT = SynchedEntityData.defineId(VeilSpirit.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FACTION = SynchedEntityData.defineId(VeilSpirit.class, EntityDataSerializers.INT);

    public VeilSpirit(EntityType<? extends VeilSpirit> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setNoAi(true);
        setSilent(true);
        setPersistenceRequired();
        this.noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPIRIT, "");
        builder.define(DATA_FACTION, 0);
    }

    public String spiritId() {
        return entityData.get(DATA_SPIRIT);
    }

    /** 0 hunter, 1 keeper, 2 sleeper, 3 ascendant: tints the ghost. */
    public int faction() {
        return entityData.get(DATA_FACTION);
    }

    public void setSpirit(String id, int faction) {
        entityData.set(DATA_SPIRIT, id);
        entityData.set(DATA_FACTION, faction);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return Veil.isSpirit(player);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public void checkDespawn() {
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (level().isClientSide() && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.SOUL, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.6,
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }
}
