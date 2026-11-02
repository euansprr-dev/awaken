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
import net.minecraft.world.entity.player.Player;
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
 * Every creature projectile: a Force (damage type), its class (SPEC §46.2: the shot's core glows the colour of how you
 * answer it, its speed by the same: white-gold 0.9, amber 0.7, red 0.6 blocks a tick) and a trail. Drawn by
 * {@code mobs.client.ShotRenderer} as a glowing core with a tapering tail, humming as it flies. Flies straight at constant
 * speed for at most five seconds and never breaks blocks. A white-gold one deflected flies back at its maker, faster,
 * and strikes its stance (SPEC §46.3); an amber or red one breaks on the blade.
 */
public class AwBolt extends AbstractHurtingProjectile implements ItemSupplier {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(AwBolt.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_CLASS = SynchedEntityData.defineId(AwBolt.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_THEME = SynchedEntityData.defineId(AwBolt.class, EntityDataSerializers.INT);
    private static final ItemStack[] STACKS = new ItemStack[BoltKind.values().length];
    /** A seep heart's shard is a real Deepglass shard (the delve module's item), else an amethyst one. */
    private static final net.minecraft.resources.Identifier DEEPGLASS_SHARD = dev.awaken.Awaken.id("deepglass_shard");

    /** A sent-back shot's speed (blocks a tick: faster than it came) and what it does to its maker. */
    public static final double RETURN_SPEED = 1.15;
    public static final float RETURN_DAMAGE = 1.0F;

    private float damage = 4.0F;
    /** Sent back by a deflect: who sent it (it then strikes our creatures, never a player), and that it turned this tick. */
    private @Nullable Player returnedBy;
    private boolean turned;
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
        b.entityData.set(DATA_CLASS, (byte) b.strike.ordinal());
        b.entityData.set(DATA_THEME, owner.def().glow);
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

    /** The class it lands as, as the client sees it (its core's colour). */
    public dev.awaken.core.api.Strikes.Kind shownClass() {
        dev.awaken.core.api.Strikes.Kind[] v = dev.awaken.core.api.Strikes.Kind.values();
        int i = entityData.get(DATA_CLASS);
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }

    /** Its maker's theme colour (the trail's smoke and sparks). */
    public int theme() {
        return entityData.get(DATA_THEME);
    }

    /** Ticks since it was fired (the client's own count, for its look). */
    public int age() {
        return tickCount;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, (byte) 0);
        b.define(DATA_CLASS, (byte) 0);
        b.define(DATA_THEME, 0xFFFFFF);
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
        turned = false; // a turn holds only for the hit that turned it
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
        if (entity instanceof AwBolt) return false;
        if (returnedBy != null) return entity instanceof AwakenMob && entity.isAlive() && !entity.isSpectator();
        return super.canHitEntity(entity) && !(entity instanceof AwakenMob);
    }

    /**
     * Deflected by {@code by}'s blade (SPEC §46.3): a white-gold shot turns and flies back at its maker's chest; false for
     * an amber or red one (it breaks on the blade), one already sent back, or a maker gone.
     */
    public boolean sendBack(Player by) {
        if (strike != dev.awaken.core.api.Strikes.Kind.LIGHT || returnedBy != null || !(getOwner() instanceof AwakenMob maker) || !maker.isAlive()) return false;
        returnedBy = by;
        turned = true;
        Vec3 at = new Vec3(by.getX(), by.getY() + by.getBbHeight() * 0.7, by.getZ());
        Vec3 to = Abilities.chest(maker).subtract(at);
        if (to.lengthSqr() < 1.0E-4) return false;
        Vec3 dir = to.normalize();
        setPos(at.add(dir.scale(by.getBbWidth() * 0.5 + 0.6)));
        setDeltaMovement(dir.scale(RETURN_SPEED));
        life = 0;
        effect = null; // it carries nothing back but the blow
        entityData.set(DATA_THEME, dev.awaken.core.api.Strikes.Kind.LIGHT.colour());
        needsSync = true;
        level().playSound(null, getX(), getY(), getZ(), dev.awaken.mobs.MobSounds.SHOT_RELEASE.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.35F);
        return true;
    }

    /** Whether a deflect sent it back. */
    public boolean returned() {
        return returnedBy != null;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (!(level() instanceof ServerLevel sl) || !(hit.getEntity() instanceof LivingEntity victim)) return;
        if (returnedBy != null) {
            // its own shot back at it: the blow is the sender's, and its stance takes it like a deflect
            if (victim.hurtServer(sl, sl.damageSources().thrown(this, returnedBy), damage * RETURN_DAMAGE)) {
                dev.awaken.core.api.Strikes.posture(victim, RETURNED_POSTURE, returnedBy);
            }
            return;
        }
        AwakenMob owner = getOwner() instanceof AwakenMob m ? m : null;
        Abilities.hurtBy(sl, owner, this, victim, damage, kind(), effect);
    }

    /** What a sent-back shot does to its maker's stance (Arms' RETURNED_SHOT_POSTURE). */
    static final int RETURNED_POSTURE = 22;

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (turned) { // turned on a blade this tick: it flies on
            turned = false;
            return;
        }
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
        out.putInt("class", strike.ordinal());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        damage = in.getFloatOr("damage", 4.0F);
        entityData.set(DATA_KIND, (byte) in.getIntOr("kind", 0));
        dev.awaken.core.api.Strikes.Kind[] v = dev.awaken.core.api.Strikes.Kind.values();
        strike = v[Math.max(0, Math.min(v.length - 1, in.getIntOr("class", 0)))];
        entityData.set(DATA_CLASS, (byte) strike.ordinal());
    }
}
