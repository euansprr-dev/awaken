package dev.awaken.gates.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hollow Gate: there are no monsters, but something follows you. It only moves while nobody is looking at it. When it
 * reaches you it takes a bite of your health and blinks away. It cannot be hurt.
 */
public class HollowFollower extends Monster implements SkinOwner {
    public int gateId = -1;
    public int area, floor;
    private int cooldown;

    public HollowFollower(EntityType<? extends HollowFollower> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        if (!(level() instanceof ServerLevel sl)) return;
        if (cooldown > 0) cooldown--;
        ServerPlayer target = null;
        double best = 64 * 64;
        boolean watched = false;
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(this);
            if (d > 64 * 64) continue;
            if (seenBy(sl, p)) watched = true;
            if (d < best) {
                best = d;
                target = p;
            }
        }
        if (target == null) return;
        Vec3 to = target.position().subtract(position());
        float yaw = (float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90F;
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        if (watched) return;
        double dist = Math.sqrt(best);
        if (dist < 1.4 && cooldown == 0) {
            target.hurtServer(sl, damageSources().mobAttack(this), Math.max(4F, target.getMaxHealth() * 0.25F));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 0.8F, 1.6F);
            // blink away behind where it came from
            Vec3 back = position().subtract(to.normalize().scale(18));
            setPos(back.x, target.getY(), back.z);
            cooldown = 100;
            return;
        }
        double step = Math.min(dist - 1.0, 0.34);
        if (step > 0) {
            Vec3 n = to.normalize().scale(step);
            setPos(getX() + n.x, getY() + (target.getY() - getY()) * 0.2, getZ() + n.z);
        }
        if (tickCount % 30 == 0 && dist < 18) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.8F);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 2, 0.2, 0.5, 0.2, 0.01);
        }
    }

    /** Looked at (in front of the player's view and in line of sight). */
    private boolean seenBy(ServerLevel sl, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 me = getEyePosition();
        Vec3 dir = me.subtract(eye);
        double len = dir.length();
        if (len < 0.5) return true;
        if (p.getViewVector(1.0F).dot(dir.scale(1 / len)) < 0.72) return false;
        var hit = sl.clip(new ClipContext(eye, me, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= len * len - 1;
    }

    @Override
    public String skinOwner() {
        return "";
    }

    @Override
    public int skinTint() {
        return 0xFF0B0A12;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return !source.isCreativePlayer();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
