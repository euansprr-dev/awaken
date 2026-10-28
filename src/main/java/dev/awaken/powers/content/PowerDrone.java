package dev.awaken.powers.content;

import dev.awaken.core.Forces;
import dev.awaken.powers.Bolts;
import dev.awaken.powers.Combat;
import dev.awaken.powers.PowerSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Tech summons. A drone hovers around its owner and fires charge bolts at the owner's foes; a turret sits where it
 * was deployed with longer range and heavier shots. One entity type with a synced mode so the renderer can tell.
 */
public final class PowerDrone extends PowerSummon {
    private static final EntityDataAccessor<Boolean> DATA_TURRET = SynchedEntityData.defineId(PowerDrone.class, EntityDataSerializers.BOOLEAN);

    private int orbitIndex;
    private int fireDelay = 20;
    private int fireInterval = 16;
    private double range = 16;
    private @Nullable LivingEntity focus;
    /** The cast that sent it: its shots carry the ladder's riders, pierce and looks (SPEC 34). */
    public dev.awaken.powers.@Nullable Cast cast;
    /** Burst Fire (Drone 4): shots per burst, three ticks apart, with a longer pause between bursts. */
    public int burst = 1;
    private int burstLeft;
    /** Shield (Turret 4): the share of every hit it shrugs off. */
    public float shield;

    public PowerDrone(EntityType<? extends PowerDrone> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_TURRET, false);
    }

    public boolean isTurret() {
        return entityData.get(DATA_TURRET);
    }

    public double range() {
        return range;
    }

    public void makeTurret(double range, int interval) {
        entityData.set(DATA_TURRET, true);
        setNoGravity(false);
        this.range = range;
        this.fireInterval = interval;
    }

    public void orbit(int index, int interval) {
        this.orbitIndex = index;
        this.fireInterval = interval;
        this.fireDelay = 10 + index * 3;
    }

    /** Drone Swarm: everything focuses this target while it lives. */
    public void focus(@Nullable LivingEntity target) {
        this.focus = target;
    }

    @Override
    public boolean isPushable() {
        return !isTurret();
    }

    @Override
    protected void registerGoals() {
        // Movement and targeting are driven in aiStep: drones orbit, turrets stay put.
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        ServerPlayer owner = ownerPlayer();
        if (owner == null) return;
        if (!isTurret()) {
            double a = (tickCount * 0.06) + orbitIndex * (Math.PI * 2 / 6.0);
            double r = 1.6 + (orbitIndex % 2) * 0.6;
            Vec3 want = owner.position().add(Math.cos(a) * r, 2.1 + Math.sin(tickCount * 0.1 + orbitIndex) * 0.25, Math.sin(a) * r);
            Vec3 d = want.subtract(position());
            if (d.lengthSqr() > 400) {
                snapTo(want.x, want.y, want.z, getYRot(), getXRot());
            } else {
                setDeltaMovement(d.scale(0.25));
            }
        }
        LivingEntity target = pickTarget(sl, owner);
        if (target != null) getLookControl().setLookAt(target, 30f, 30f);
        if (--fireDelay > 0 || target == null) return;
        if (burst > 1) {
            if (burstLeft <= 0) burstLeft = burst;
            fireDelay = --burstLeft > 0 ? 3 : fireInterval * 2;
        } else fireDelay = fireInterval;
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.55, 0);
        Vec3 dir = to.subtract(from).normalize();
        var b = Bolts.fire(sl, owner, this, from, dir, isTurret() ? 2.6f : 2.0f, power(), Forces.Force.CHARGE,
                isTurret() ? 0xFFFFB347 : 0xFF7FE8FF, 30, isTurret() ? 0.8f : 0f);
        if (cast != null && cast.powerLevel > 1) {
            b.cast = cast;
            b.land = true;
            if (cast.kit.pierce != null) {
                b.pierce = cast.kit.pierce.n();
                b.pierceShare = cast.kit.pierce.share();
            }
        }
        sl.playSound(null, getX(), getY(), getZ(), PowerSounds.DRONE_SHOT.get(), SoundSource.PLAYERS, 0.5f, isTurret() ? 0.8f : 1.4f);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 3, 0.05, 0.05, 0.05, 0.05);
    }

    private @Nullable LivingEntity pickTarget(ServerLevel level, ServerPlayer owner) {
        if (focus != null && focus.isAlive() && focus.distanceToSqr(this) < range * range * 1.5) return focus;
        LivingEntity last = owner.getLastHurtMob();
        if (last != null && last.isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() < 200
                && last.distanceToSqr(this) < range * range && Combat.isFoeOrTarget(owner, last) && hasLineOfSight(last)) {
            return last;
        }
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(range), x -> Combat.isFoe(owner, x))) {
            double d = e.distanceToSqr(this);
            if (d < bestD && hasLineOfSight(e)) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }
}
