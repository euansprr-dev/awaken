package dev.awaken.hearth.training;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A translucent runner replaying your best lap. Server-driven (one recorded sample per tick), never saved, cannot be
 * touched; it fades a second after it crosses the line.
 */
public class TrainingGhost extends Mob {
    private float[] samples = new float[0];
    private int index;
    private int linger = 20;

    public TrainingGhost(EntityType<? extends TrainingGhost> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        noPhysics = true;
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes();
    }

    /** Samples are x, y, z, yaw per tick. */
    public void play(float[] samples) {
        this.samples = samples;
        this.index = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;
        int i = index * 4;
        if (i + 3 >= samples.length) {
            if (--linger <= 0) discard();
            return;
        }
        double x = samples[i], y = samples[i + 1], z = samples[i + 2];
        float yaw = samples[i + 3];
        setDeltaMovement(new Vec3(x - getX(), y - getY(), z - getZ()));
        setPos(x, y, z);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        index++;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
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
    protected void doPush(Entity entity) {}

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
