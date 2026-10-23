package dev.awaken.mobs.entity;

import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.BoltKind;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every creature projectile: a Force (damage type), a sprite (a vanilla item, drawn full-bright) and a trail. Flies
 * straight at constant speed for at most five seconds and never breaks blocks.
 */
public class AwBolt extends AbstractHurtingProjectile implements ItemSupplier {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(AwBolt.class, EntityDataSerializers.BYTE);
    private static final ItemStack[] STACKS = new ItemStack[BoltKind.values().length];

    private float damage = 4.0F;
    private @Nullable MobEffectInstance effect;
    private int life;

    public AwBolt(EntityType<? extends AwBolt> type, Level level) {
        super(type, level);
        this.accelerationPower = 0.0;
    }

    public static AwBolt shoot(ServerLevel level, AwakenMob owner, Vec3 from, Vec3 dir, BoltKind kind, float damage, @Nullable MobEffectInstance effect, double speed) {
        AwBolt b = new AwBolt(MobTypes.BOLT.get(), level);
        b.setOwner(owner);
        b.snapTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
        b.entityData.set(DATA_KIND, (byte) kind.ordinal());
        b.damage = damage;
        b.effect = effect;
        b.setDeltaMovement(dir.normalize().scale(speed));
        level.addFreshEntity(b);
        return b;
    }

    public BoltKind kind() {
        return BoltKind.byOrdinal(entityData.get(DATA_KIND));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, (byte) 0);
    }

    @Override
    public ItemStack getItem() {
        BoltKind k = kind();
        ItemStack s = STACKS[k.ordinal()];
        if (s == null) STACKS[k.ordinal()] = s = new ItemStack(k.item);
        return s;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && ++life > 100) discard();
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return kind().trail;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return 0.9F;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof AwakenMob) && !(entity instanceof AwBolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel sl && hit.getEntity() instanceof LivingEntity victim) {
            AwakenMob owner = getOwner() instanceof AwakenMob m ? m : null;
            Abilities.hurtBy(sl, owner, this, victim, damage, kind(), effect);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(kind().trail, getX(), getY(), getZ(), 6, 0.15, 0.15, 0.15, 0.05);
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("damage", damage);
        out.putInt("kind", entityData.get(DATA_KIND));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        damage = in.getFloatOr("damage", 4.0F);
        entityData.set(DATA_KIND, (byte) in.getIntOr("kind", 0));
    }
}
