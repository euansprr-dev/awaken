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
    /** A seep heart's shard is a real Deepglass shard (the delve module's item), else an amethyst one. */
    private static final net.minecraft.resources.Identifier DEEPGLASS_SHARD = dev.awaken.Awaken.id("deepglass_shard");

    private float damage = 4.0F;
    /** The class of the ability that fired it (core.api.Strikes): it lands as that blow, whenever it lands. */
    private dev.awaken.core.api.Strikes.Kind strike = dev.awaken.core.api.Strikes.Kind.LIGHT;
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
        var fired = dev.awaken.core.api.Strikes.delivering();
        if (fired != null) b.strike = fired;
        b.setDeltaMovement(dir.normalize().scale(speed));
        level.addFreshEntity(b);
        return b;
    }

    /** The class this bolt lands as. */
    public dev.awaken.core.api.Strikes.Kind strike() {
        return strike;
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
        if (s == null) {
            net.minecraft.world.item.Item item = k.item;
            if (k == BoltKind.DEEP) {
                net.minecraft.world.item.Item shard = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(DEEPGLASS_SHARD);
                if (shard != net.minecraft.world.item.Items.AIR) item = shard;
            }
            STACKS[k.ordinal()] = s = new ItemStack(item);
        }
        return s;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && ++life > 100) discard();
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return kind() == BoltKind.DEEP ? dev.awaken.mobs.MobParticles.SEEP_MOTE.get() : kind().trail;
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
            if (kind() == BoltKind.DEEP) { // the shard shatters into slivers and light
                sl.sendParticles(dev.awaken.mobs.MobParticles.SEEP_SHARD.get(), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.18);
                sl.sendParticles(dev.awaken.mobs.MobParticles.SEEP_MOTE.get(), getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.06);
            } else {
                sl.sendParticles(kind().trail, getX(), getY(), getZ(), 6, 0.15, 0.15, 0.15, 0.05);
            }
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
