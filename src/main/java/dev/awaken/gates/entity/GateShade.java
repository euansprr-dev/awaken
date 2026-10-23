package dev.awaken.gates.entity;

import dev.awaken.core.Forces;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Mirror modifier: your shadow, wearing a dark copy of your skin and your weapon, hunting only you. It hits as hard as
 * a strong monster of the gate's level (as hard as you under Glass + Mirror).
 */
public class GateShade extends Monster implements SkinOwner {
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(GateShade.class, EntityDataSerializers.STRING);

    private UUID ownerId;

    public GateShade(EntityType<? extends GateShade> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.29).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER, "");
    }

    /** Copies the owner's look and weapon; called once on spawn. */
    public void bind(ServerPlayer owner, double health, double damage) {
        this.ownerId = owner.getUUID();
        this.entityData.set(OWNER, owner.getUUID().toString());
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(10, health));
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(Math.max(2, damage));
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, owner.getMainHandItem().copyWithCount(1));
        setDropChance(EquipmentSlot.MAINHAND, 0);
        Forces.tag(this, Forces.Tag.MIRROR);
        setCustomName(owner.getDisplayName().copy().append("'s shadow"));
        setPersistenceRequired();
    }

    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public String skinOwner() {
        return entityData.get(OWNER);
    }

    @Override
    public int skinTint() {
        return 0xFF2A2340;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HuntOwnerGoal(this));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 20 == 0) {
            Player owner = ownerId == null ? null : level.getPlayerByUUID(ownerId);
            if (owner == null || !owner.isAlive() || owner.level() != level || owner.distanceToSqr(this) > 96 * 96) discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (ownerId != null) out.putString("owner", ownerId.toString());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.getString("owner").ifPresent(s -> {
            try {
                ownerId = UUID.fromString(s);
                entityData.set(OWNER, s);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    /** Targets its owner and nobody else. */
    static final class HuntOwnerGoal extends TargetGoal {
        private final GateShade shade;

        HuntOwnerGoal(GateShade shade) {
            super(shade, false);
            this.shade = shade;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (shade.ownerId == null) return false;
            Player p = shade.level().getPlayerByUUID(shade.ownerId);
            return p instanceof LivingEntity && p.isAlive() && !p.isSpectator() && !p.isCreative();
        }

        @Override
        public void start() {
            shade.setTarget(shade.level().getPlayerByUUID(shade.ownerId));
            super.start();
        }
    }
}
