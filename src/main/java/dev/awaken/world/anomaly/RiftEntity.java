package dev.awaken.world.anomaly;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A rift tear: a crack of light hanging over the ground with a core at its foot. Only players can strike the core;
 * each strike (at most one every {@link Rifts#HIT_COOLDOWN} ticks, whatever the weapon or power) takes one hit off, and
 * at zero the rift closes ({@link Rifts#strike}). The director owns everything else (waves, lifetime, rewards); this
 * entity is the visible, hittable part and discards itself when its anomaly is gone.
 */
public class RiftEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MAX = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CLOSING = SynchedEntityData.defineId(RiftEntity.class, EntityDataSerializers.BOOLEAN);
    /** Entity event: the core was struck (client flash). Vanilla's event ids stop well below this. */
    static final byte EVENT_STRUCK = 101;
    static final int CLOSE_TICKS = 20;

    int anomalyId = -1;
    long lastHit = Long.MIN_VALUE / 2;
    private int closeTicks = -1;

    // client only
    public int flash;
    public int closingAge = -1;

    public RiftEntity(EntityType<? extends RiftEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void setup(int anomalyId, int maxHits, int hitsLeft) {
        this.anomalyId = anomalyId;
        entityData.set(DATA_MAX, Math.max(1, maxHits));
        entityData.set(DATA_HITS, Math.max(0, hitsLeft));
    }

    public int hitsLeft() {
        return entityData.get(DATA_HITS);
    }

    public int maxHits() {
        return entityData.get(DATA_MAX);
    }

    public boolean closing() {
        return entityData.get(DATA_CLOSING);
    }

    /** Collapse over a second, then go (the renderer shrinks it). */
    void beginClose() {
        if (closing()) return;
        entityData.set(DATA_CLOSING, true);
        closeTicks = CLOSE_TICKS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_HITS, 5);
        b.define(DATA_MAX, 5);
        b.define(DATA_CLOSING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_CLOSING.equals(accessor) && level().isClientSide() && closing() && closingAge < 0) closingAge = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (closeTicks >= 0) {
            if (--closeTicks <= 0) discard();
            return;
        }
        if (tickCount % 20 == 0 && !Rifts.owns(level, anomalyId, getUUID())) {
            discard();
            return;
        }
        // a low hum that carries ~40 blocks (the drawn light makes it read from further off)
        if (tickCount % 80 == 1) {
            level.playSound(null, getX(), getY() + 1.4, getZ(), AnomalyContent.RIFT_HUM.get(), SoundSource.HOSTILE, 2.5F,
                    0.75F + random.nextFloat() * 0.15F);
        }
    }

    private void clientTick() {
        // the tear's light, motes and flashes are drawn (client.RiftGlow), not vanilla particles
        if (flash > 0) flash--;
        if (closingAge >= 0) closingAge++;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_STRUCK) flash = 6;
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (isRemoved() || closing() || !(source.getEntity() instanceof ServerPlayer player)) return false;
        long now = level.getGameTime();
        if (now - lastHit < Rifts.HIT_COOLDOWN) return false;
        lastHit = now;
        int left = Math.max(0, hitsLeft() - 1);
        entityData.set(DATA_HITS, left);
        level.broadcastEntityEvent(this, EVENT_STRUCK);
        Rifts.strike(level, this, player, left);
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved() && !closing();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double xa, double ya, double za) {
        // the tear hangs where it opened
    }

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
