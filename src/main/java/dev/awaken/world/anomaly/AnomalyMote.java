package dev.awaken.world.anomaly;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * One light of a spirit procession. It drifts toward the point the director gives it ({@link Processions}), bobbing,
 * and leaves a faint trail. It can't be hurt or pushed, and discards itself when its procession is gone.
 */
public class AnomalyMote extends Entity {
    static final double SPEED = 0.2;

    int anomalyId = -1;
    double goalX, goalY, goalZ;
    private int fadeTicks = -1;

    public AnomalyMote(EntityType<? extends AnomalyMote> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void goal(double x, double y, double z) {
        goalX = x;
        goalY = y;
        goalZ = z;
    }

    void goal(Vec3 v) {
        goal(v.x, v.y, v.z);
    }

    void fadeAfter(int ticks) {
        fadeTicks = ticks;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (random.nextInt(5) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.2, getY() + (random.nextDouble() - 0.5) * 0.2,
                        getZ() + (random.nextDouble() - 0.5) * 0.2, 0, -0.01, 0);
            }
            return;
        }
        boolean fading = fadeTicks >= 0;
        if (fading && --fadeTicks <= 0) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.03);
            discard();
            return;
        }
        if (!fading && tickCount % 20 == 0 && !Processions.owns((ServerLevel) level(), anomalyId, getUUID())) {
            discard();
            return;
        }
        // no allocation per tick: plain arithmetic toward the goal, plus a slow bob
        double dx = goalX - getX(), dy = goalY - getY(), dz = goalZ - getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double k = len > SPEED ? SPEED / len : 1.0;
        double bob = Mth.sin((tickCount + getId() * 7) * 0.12F) * 0.012;
        setPos(getX() + dx * k, getY() + dy * k + bob, getZ() + dz * k);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
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
        return distance < 128.0 * 128.0;
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
