package dev.awaken.descent.entity;

import dev.awaken.descent.DescentContent;
import dev.awaken.progression.Progression;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Rule 9 of the Hollow Court: your own shadow, with your face, your weapon and roughly your strength. It hunts only
 * you, copies your sneaking, and fades if you leave. Nothing it holds can drop (loot is never copied).
 */
public class ShadowSelf extends Monster {
    private static final EntityDataAccessor<ResolvableProfile> PROFILE = SynchedEntityData.defineId(ShadowSelf.class, EntityDataSerializers.RESOLVABLE_PROFILE);

    private @Nullable UUID owner;
    private int lostTicks;

    public ShadowSelf(EntityType<? extends ShadowSelf> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 0);
    }

    /** Copies the player's look and roughly their strength. */
    public void mirror(ServerPlayer player) {
        this.owner = player.getUUID();
        entityData.set(PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(20, player.getMaxHealth()));
        var dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(Math.max(3, player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8));
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(player.getAttributeValue(Attributes.ARMOR) * 0.5);
        setHealth(getMaxHealth());
        setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copyWithCount(1));
        setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem().copyWithCount(1));
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0.0F);
        setCustomName(net.minecraft.network.chat.Component.literal("Shadow of " + player.getGameProfile().name()));
        setTarget(player);
    }

    public ResolvableProfile profile() {
        return entityData.get(PROFILE);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PROFILE, ResolvableProfile.createUnresolved("Steve"));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                (t, lvl) -> owner != null && owner.equals(t.getUUID())));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        boolean present = p != null && p.level() == level && p.isAlive() && distanceToSqr(p) < 64 * 64;
        if (!present) {
            if (++lostTicks > 100) fade(level);
            return;
        }
        lostTicks = 0;
        setShiftKeyDown(p.isShiftKeyDown());
        if (getTarget() == null) setTarget(p);
        if (tickCount % 4 == 0) level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 2, 0.25, 0.5, 0.25, 0.0);
    }

    private void fade(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl && source.getEntity() instanceof ServerPlayer killer) {
            Progression.count(killer, "awaken:shadows_defeated");
            if (killer.getUUID().equals(owner)) {
                Progression.grantLevels(killer, 0.12, Progression.XpSource.DESCENT);
            }
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 24, 0.3, 0.6, 0.3, 0.03);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return DescentContent.SND_SHADOW.get();
    }
}
