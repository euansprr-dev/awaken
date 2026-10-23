package dev.awaken.world.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The light of a falling star, streaking down from high in the sky to its crater in about two seconds with a long
 * trail. It is only the show: when it arrives it tells {@link Stars#land} (the director lands it anyway if this entity
 * never gets there), then goes. Never saved, can't be hurt.
 */
public class FallingStarEntity extends Entity {
    int anomalyId = -1;
    private double fromX, fromY, fromZ, toX, toY, toZ;
    private int duration = Stars.FALL_TICKS;
    private int age;

    public FallingStarEntity(EntityType<? extends FallingStarEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void setup(int anomalyId, Vec3 from, Vec3 to, int duration) {
        this.anomalyId = anomalyId;
        this.fromX = from.x;
        this.fromY = from.y;
        this.fromZ = from.z;
        this.toX = to.x;
        this.toY = to.y;
        this.toZ = to.z;
        this.duration = Math.max(1, duration);
        setPos(from.x, from.y, from.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // a long trail: sparks that hang where it passed, visible from far off
            Level l = level();
            for (int i = 0; i < 3; i++) {
                l.addAlwaysVisibleParticle(ParticleTypes.FIREWORK, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + (random.nextDouble() - 0.5) * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.02, 0.01, (random.nextDouble() - 0.5) * 0.02);
            }
            l.addAlwaysVisibleParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
            if (random.nextBoolean()) l.addAlwaysVisibleParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0.02, 0);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        age++;
        if (age >= duration) {
            setPos(toX, toY, toZ);
            Stars.land(level, anomalyId);
            discard();
            return;
        }
        if (age % 20 == 0 && !Stars.owns(level, anomalyId, getUUID())) {
            discard();
            return;
        }
        // no allocation per tick: plain interpolation (it speeds up a little as it falls)
        double t = age / (double) duration;
        double k = t * t * 0.35 + t * 0.65;
        setPos(fromX + (toX - fromX) * k, fromY + (toY - fromY) * k, fromZ + (toZ - fromZ) * k);
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
        return distance < 320.0 * 320.0;
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
