package dev.awaken.mobs.entity;

import dev.awaken.mobs.MobParticles;
import dev.awaken.mobs.MobTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A burst of roots out of the floor (the seep heart's Rootfall and Root Lash): thorned roots of black-violet growth punch
 * up through the rock with the stones they broke, hold, and sink back. Only a look (the blow is the heart's): no
 * collision, never saved, gone in {@value #LIFE} ticks. Drawn by mobs.client.SeepRootRenderer (rig "seep_root", clip
 * "erupt", from tools/art/specs/fauna3.py).
 */
public class SeepRoot extends Entity {
    public static final int LIFE = 28;
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(SeepRoot.class, EntityDataSerializers.FLOAT);

    public SeepRoot(EntityType<? extends SeepRoot> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Roots burst from the floor at {@code at} (its top), turned {@code yaw} degrees, {@code size} times their size. */
    public static void spawn(ServerLevel level, Vec3 at, float yaw, float size) {
        SeepRoot r = new SeepRoot(MobTypes.SEEP_ROOT.get(), level);
        r.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        r.entityData.set(DATA_SIZE, size);
        level.addFreshEntity(r);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_SIZE, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            if (tickCount >= LIFE) discard();
            return;
        }
        if (tickCount == 1) {
            // the floor breaks where they come through
            BlockState s = level().getBlockState(BlockPos.containing(getX(), getY() - 0.5, getZ()));
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2, v = 0.08 + random.nextDouble() * 0.12;
                if (!s.isAir()) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s), getX() + Math.cos(a) * 0.3, getY() + 0.1, getZ() + Math.sin(a) * 0.3,
                            Math.cos(a) * v, 0.25 + random.nextDouble() * 0.2, Math.sin(a) * v);
                }
                if (i < 5) level().addParticle(MobParticles.SEEP_SHARD.get(), getX(), getY() + 0.4, getZ(), Math.cos(a) * v, 0.2 + random.nextDouble() * 0.2, Math.sin(a) * v);
            }
        }
        if (tickCount < 16 && tickCount % 3 == 0) {
            level().addParticle(MobParticles.SEEP_MOTE.get(), getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 0.3 + random.nextDouble() * 0.9 * size(),
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
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
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {}
}
