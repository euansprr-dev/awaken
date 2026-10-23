package dev.awaken.powers;

import com.mojang.math.Transformation;
import dev.awaken.core.Forces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thrown terrain and walls (Terrain Rip, Collapse, Gravity + Architect): a vanilla block display moved by the server
 * like a projectile. Nothing is placed when it lands, so thrown chunks never change the world. Displays carry the
 * {@link #TEMP_TAG} tag and are discarded if they are ever loaded from disk.
 */
public final class FlyingBlocks {
    private FlyingBlocks() {}

    public static final String TEMP_TAG = "awaken_temp";

    public static final class Flyer {
        final ServerLevel level;
        final ServerPlayer owner;
        final Display.BlockDisplay display;
        final BlockState state;
        Vec3 pos;
        Vec3 vel;
        double gravity = 0.06;
        final float damage;
        final Forces.@Nullable Force force;
        final float radius;
        final float size;
        int life = 80;
        /** Keeps flying through targets it hits (wall rams). */
        public boolean pierce;
        public int stun;
        final Set<Integer> hit = new HashSet<>();

        Flyer(ServerLevel level, ServerPlayer owner, Display.BlockDisplay display, BlockState state, Vec3 pos, Vec3 vel, float damage,
              Forces.@Nullable Force force, float radius, float size) {
            this.level = level;
            this.owner = owner;
            this.display = display;
            this.state = state;
            this.pos = pos;
            this.vel = vel;
            this.damage = damage;
            this.force = force;
            this.radius = radius;
            this.size = size;
        }

        public Flyer gravity(double g) {
            this.gravity = g;
            return this;
        }
    }

    private static final List<Flyer> FLYERS = new ArrayList<>();

    /** Spawns a flying block. size = edge length in blocks; radius = splash radius on impact. */
    public static @Nullable Flyer launch(ServerLevel level, ServerPlayer owner, BlockState state, Vec3 from, Vec3 vel, float damage,
                                         Forces.@Nullable Force force, float radius, float size) {
        Display.BlockDisplay display = spawnDisplay(level, state, from, size, level.getRandom().nextFloat() * 0.6f);
        if (display == null) return null;
        Flyer f = new Flyer(level, owner, display, state, from, vel, damage, force, radius, size);
        FLYERS.add(f);
        return f;
    }

    /** A block display centred on {@code at}, tilted a little so thrown chunks don't look grid-locked. */
    public static Display.@Nullable BlockDisplay spawnDisplay(ServerLevel level, BlockState state, Vec3 at, float size, float tilt) {
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        Quaternionf rot = new Quaternionf().rotateXYZ(tilt, tilt * 1.7f, tilt * 0.6f);
        Vector3f half = new Vector3f(-size / 2f, -size / 2f, -size / 2f);
        rot.transform(half);
        Transformation t = new Transformation(half, rot, new Vector3f(size, size, size), new Quaternionf());
        tag.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE, t).getOrThrow());
        tag.putInt("teleport_duration", 2);
        tag.putFloat("view_range", 2.0f);
        Entity e = EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
            ent.snapTo(at.x, at.y, at.z, 0f, 0f);
            return ent;
        });
        if (!(e instanceof Display.BlockDisplay d)) return null;
        d.addTag(TEMP_TAG);
        level.addFreshEntity(d);
        return d;
    }

    static void tick() {
        if (FLYERS.isEmpty()) return;
        for (int i = FLYERS.size() - 1; i >= 0; i--) {
            Flyer f = FLYERS.get(i);
            if (step(f)) {
                f.display.discard();
                FLYERS.remove(i);
            }
        }
    }

    static void clear() {
        for (Flyer f : FLYERS) f.display.discard();
        FLYERS.clear();
    }

    private static boolean step(Flyer f) {
        if (--f.life < 0 || f.display.isRemoved() || f.owner.isRemoved()) return true;
        Vec3 from = f.pos;
        Vec3 to = from.add(f.vel);
        if (!f.level.isLoaded(BlockPos.containing(to))) return true;
        f.vel = f.vel.scale(0.985).add(0, -f.gravity, 0);
        BlockHitResult wall = f.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        double half = f.size * 0.5 + 0.2;
        AABB sweep = new AABB(from, end).inflate(half);
        boolean struck = false;
        for (LivingEntity e : f.level.getEntitiesOfClass(LivingEntity.class, sweep, x -> !f.hit.contains(x.getId()) && Combat.isFoeOrTarget(f.owner, x))) {
            f.hit.add(e.getId());
            Combat.hurtFrom(f.owner, f.display, e, f.damage, f.force, true);
            Combat.knockFrom(e, from, 0.9, 0.35);
            if (f.stun > 0) Combat.stun(e, f.stun);
            struck = true;
            if (!f.pierce) break;
        }
        f.pos = end;
        f.display.setPos(end.x, end.y, end.z);
        if (f.level.getGameTime() % 2 == 0) {
            f.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, f.state), end.x, end.y, end.z, 1, 0.2, 0.2, 0.2, 0);
        }
        if ((struck && !f.pierce) || wall.getType() != HitResult.Type.MISS) {
            land(f, end);
            return true;
        }
        return false;
    }

    private static void land(Flyer f, Vec3 at) {
        if (f.radius > 0) {
            for (LivingEntity e : Combat.foesAround(f.owner, at, f.radius)) {
                if (f.hit.contains(e.getId())) continue;
                Combat.hurtFrom(f.owner, f.display, e, f.damage * 0.6f, f.force, true);
                Combat.knockFrom(e, at, 0.6, 0.3);
            }
        }
        f.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, f.state), at.x, at.y, at.z, (int) (12 * f.size), 0.4 * f.size, 0.3, 0.4 * f.size, 0.15);
        f.level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.3, 0.2, 0.3, 0.02);
        f.level.playSound(null, at.x, at.y, at.z, PowerSounds.IMPACT.get(), SoundSource.PLAYERS, Math.min(1.2f, 0.5f + f.size * 0.3f), 0.8f + f.level.getRandom().nextFloat() * 0.3f);
        Fx.quake(f.level, at, 10 + f.size * 4, 1.2f * f.size);
    }
}
