package dev.awaken.powers.content;

import dev.awaken.powers.Combat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Base for temporary summons made by powers (shadow soldiers, drones, turrets). They belong to a player, never save
 * to disk, never hurt or get hurt by their owner's side, and fade when their time runs out.
 */
public abstract class PowerSummon extends PathfinderMob implements OwnableEntity {
    private @Nullable EntityReference<LivingEntity> owner;
    protected int life = 400;
    /** Damage per hit, set by the power that made this summon (already scaled for the owner). */
    protected float power = 4f;
    private boolean overcharged;

    protected PowerSummon(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public void setup(ServerPlayer owner, int lifeTicks, float damage) {
        this.owner = EntityReference.of(owner);
        this.life = lifeTicks;
        this.power = damage;
        setPersistenceRequired();
    }

    public float power() {
        return overcharged ? power * 2f : power;
    }

    public int lifeLeft() {
        return life;
    }

    /** Charge + Echoes: hits twice as hard, then expires sooner. */
    public void overcharge(int ticks) {
        overcharged = true;
        life = Math.min(life, ticks);
        setGlowingTag(true);
    }

    public boolean isOvercharged() {
        return overcharged;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return owner;
    }

    public @Nullable ServerPlayer ownerPlayer() {
        return getOwner() instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            if (--life <= 0 || (owner != null && getOwner() == null && tickCount > 40)) {
                fadeOut(sl);
                return;
            }
            if (overcharged && tickCount % 4 == 0) {
                sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight() * 0.6, getZ(), 2, 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    protected void fadeOut(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, 0.2, 0.4, 0.2, 0.01);
        discard();
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        LivingEntity o = getOwner();
        if (other == o || other instanceof Player) return true;
        if (other instanceof OwnableEntity oe && o != null && oe.getOwner() == o) return true;
        return super.considersEntityAsAlly(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        ServerPlayer o = ownerPlayer();
        return target != getOwner() && !isAlliedTo(target) && (o == null || Combat.isFoe(o, target)) && super.canAttack(target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (attacker == getOwner() || isAlliedTo(attacker))) return false;
        return super.hurtServer(level, source, amount);
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
    public boolean canBeLeashed() {
        return false;
    }

    /** Hits a target with this summon's power, credited to the owner. */
    protected boolean strike(LivingEntity target, float amount, dev.awaken.core.Forces.@Nullable Force force) {
        ServerPlayer o = ownerPlayer();
        if (o == null || !(level() instanceof ServerLevel)) return false;
        return Combat.hurtFrom(o, this, target, amount, force, true);
    }
}
