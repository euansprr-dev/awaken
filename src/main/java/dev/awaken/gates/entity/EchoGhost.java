package dev.awaken.gates.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Echo Gate: a translucent ghost of you that walks the floor at the pace of your own earlier run (or replays the exact
 * path you took the first time through this floor). Untouchable; it just races you to the Shrine.
 */
public class EchoGhost extends PathfinderMob implements SkinOwner {
    private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(EchoGhost.class, EntityDataSerializers.STRING);

    private final List<Vec3> points = new ArrayList<>();
    private final List<Integer> times = new ArrayList<>();
    private int age;
    private boolean arrived;
    private UUID ownerId;
    public int gateId = -1;
    public int area, floor;

    public EchoGhost(EntityType<? extends EchoGhost> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(OWNER, "");
    }

    /** Waypoints with the tick each one is reached. */
    public void route(UUID owner, List<Vec3> pts, List<Integer> at) {
        this.ownerId = owner;
        this.entityData.set(OWNER, owner.toString());
        points.clear();
        points.addAll(pts);
        times.clear();
        times.addAll(at);
    }

    public boolean arrived() {
        return arrived;
    }

    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        if (level().isClientSide() || points.isEmpty()) {
            if (!level().isClientSide() && points.isEmpty() && tickCount > 40) discard();
            return;
        }
        age++;
        int i = 0;
        while (i + 1 < times.size() && times.get(i + 1) <= age) i++;
        Vec3 pos;
        if (i + 1 >= points.size()) {
            pos = points.get(points.size() - 1);
            if (!arrived) {
                arrived = true;
                ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 1, pos.z, 20, 0.3, 0.6, 0.3, 0.02);
            }
        } else {
            int t0 = times.get(i), t1 = times.get(i + 1);
            double k = t1 == t0 ? 1 : (age - t0) / (double) (t1 - t0);
            pos = points.get(i).lerp(points.get(i + 1), Math.max(0, Math.min(1, k)));
            Vec3 d = points.get(i + 1).subtract(points.get(i));
            if (d.horizontalDistanceSqr() > 1e-4) {
                float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90F;
                setYRot(yaw);
                setYHeadRot(yaw);
                setYBodyRot(yaw);
            }
        }
        setPos(pos.x, pos.y, pos.z);
        if (age % 10 == 0) ((ServerLevel) level()).sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.2, pos.z, 1, 0.1, 0, 0.1, 0);
        if (arrived && age > times.get(times.size() - 1) + 200) discard();
    }

    @Override
    public String skinOwner() {
        return entityData.get(OWNER);
    }

    @Override
    public int skinTint() {
        return 0x9C9FE8FF;
    }

    @Override
    public boolean translucent() {
        return true;
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
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
