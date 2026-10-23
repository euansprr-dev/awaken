package dev.awaken.world.anomaly;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The heart of a shimmer grove: the shimmering ring the client draws around it, its sparkles, and (whisper groves) the
 * spirit at its centre. The rules themselves are bent by {@link Groves} on the server and, for ore sight, by the client.
 * Never saved; discards itself when its grove is gone.
 */
public class ShimmerEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(ShimmerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ACTIVE = SynchedEntityData.defineId(ShimmerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FADING = SynchedEntityData.defineId(ShimmerEntity.class, EntityDataSerializers.BOOLEAN);
    static final int FADE_TICKS = 30;

    int anomalyId = -1;
    private int fadeTicks = -1;

    // client only
    public int fadingAge = -1;

    public ShimmerEntity(EntityType<? extends ShimmerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void setup(int anomalyId, GroveVariant variant) {
        this.anomalyId = anomalyId;
        entityData.set(DATA_VARIANT, variant.ordinal());
    }

    public GroveVariant variant() {
        return GroveVariant.of(entityData.get(DATA_VARIANT));
    }

    public boolean active() {
        return entityData.get(DATA_ACTIVE);
    }

    public boolean fading() {
        return entityData.get(DATA_FADING);
    }

    void setActive(boolean on) {
        entityData.set(DATA_ACTIVE, on);
    }

    /** The shimmer thins out over a second and a half, then goes. */
    void fade() {
        if (fading()) return;
        entityData.set(DATA_FADING, true);
        fadeTicks = FADE_TICKS;
    }

    /** Horizontal distance from the grove's heart within which the rules are bent. */
    public static double radius() {
        return Groves.RADIUS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_VARIANT, 0);
        b.define(DATA_ACTIVE, false);
        b.define(DATA_FADING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        if (fadeTicks >= 0) {
            if (--fadeTicks <= 0) discard();
            return;
        }
        if (tickCount % 20 == 0 && !Groves.owns((ServerLevel) level(), anomalyId, getUUID())) fade();
    }

    private void clientTick() {
        if (fading()) {
            if (fadingAge < 0) fadingAge = 0;
            fadingAge++;
            return;
        }
        Level l = level();
        GroveVariant v = variant();
        ParticleOptions p = switch (v) {
            case LIGHTFOOT -> ParticleTypes.END_ROD;
            case ORE_SIGHT -> ParticleTypes.WAX_ON;
            case WHISPER -> ParticleTypes.ENCHANT;
            case BLOOM -> ParticleTypes.CHERRY_LEAVES;
        };
        if (!active()) {
            // waiting: a slow trickle at its heart, enough to catch the eye
            if (random.nextInt(3) == 0) {
                l.addParticle(p, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + random.nextDouble() * 1.5, getZ() + (random.nextDouble() - 0.5) * 1.5,
                        0, 0.03, 0);
            }
            return;
        }
        double r = radius();
        for (int i = 0; i < 2; i++) {
            double ang = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            double x = getX() + Math.cos(ang) * d, z = getZ() + Math.sin(ang) * d;
            double y = getY() - 0.6 + random.nextDouble() * 2.4;
            double up = v == GroveVariant.BLOOM ? -0.02 : v == GroveVariant.LIGHTFOOT ? 0.04 : 0.02;
            l.addParticle(p, x, y, z, 0, up, 0);
        }
        if (v == GroveVariant.BLOOM && random.nextInt(6) == 0) {
            l.addParticle(ParticleTypes.HAPPY_VILLAGER, getX() + (random.nextDouble() - 0.5) * r, getY() + random.nextDouble(),
                    getZ() + (random.nextDouble() - 0.5) * r, 0, 0, 0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double xa, double ya, double za) {}

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        anomalyId = in.getIntOr("anomaly", -1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putInt("anomaly", anomalyId);
    }
}
