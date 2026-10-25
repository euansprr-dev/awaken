package dev.awaken.mobs.entity;

import dev.awaken.mobs.def.BoltKind;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lingering and delayed ground effects (spore clouds, ink, root lances): our own tiny area manager instead of vanilla
 * area-effect clouds, so creatures never poison each other and damage scales with the caster. Ticked once per level
 * tick; pulses every 10 ticks; capped so a bad fight can't grow it without bound.
 */
public final class Hazards {
    private Hazards() {}

    private static final int MAX = 256;

    private static final class Hazard {
        final ServerLevel level;
        final @Nullable AwakenMob owner;
        final Vec3 pos;
        final float radius, damage;
        final BoltKind bolt;
        final @Nullable MobEffectInstance effect;
        int delay, life;
        final boolean once;

        Hazard(ServerLevel level, @Nullable AwakenMob owner, Vec3 pos, float radius, int delay, int life, float damage, BoltKind bolt,
               @Nullable MobEffectInstance effect, boolean once) {
            this.level = level;
            this.owner = owner;
            this.pos = pos;
            this.radius = radius;
            this.delay = delay;
            this.life = life;
            this.damage = damage;
            this.bolt = bolt;
            this.effect = effect;
            this.once = once;
        }
    }

    private static final List<Hazard> ACTIVE = new ArrayList<>();

    /** A cloud that pulses every 10 ticks for {@code life} ticks. */
    public static void cloud(ServerLevel level, @Nullable AwakenMob owner, Vec3 pos, float radius, int life, float damage, BoltKind bolt, @Nullable MobEffectInstance effect) {
        if (ACTIVE.size() >= MAX) return;
        add(new Hazard(level, owner, pos, radius, 0, life, damage, bolt, effect, false));
    }

    /** One strike after {@code delay} ticks (root lances, fuse lines). */
    public static void strike(ServerLevel level, @Nullable AwakenMob owner, Vec3 pos, float radius, int delay, float damage, BoltKind bolt, @Nullable MobEffectInstance effect) {
        if (ACTIVE.size() >= MAX) return;
        add(new Hazard(level, owner, pos, radius, delay, 1, damage, bolt, effect, true));
    }

    /** Hazards created while we iterate (an owner dying mid-tick leaves a death cloud) join on the next tick. */
    private static final java.util.List<Hazard> PENDING = new java.util.ArrayList<>();
    private static boolean ticking;

    private static void add(Hazard h) {
        if (ticking) PENDING.add(h); else ACTIVE.add(h);
    }

    public static void tick(Level level) {
        if (!PENDING.isEmpty() && !ticking) {
            ACTIVE.addAll(PENDING);
            PENDING.clear();
        }
        if (ACTIVE.isEmpty() || !(level instanceof ServerLevel sl)) return;
        ticking = true;
        try {
            tickIn(sl);
        } finally {
            ticking = false;
        }
    }

    private static void tickIn(ServerLevel sl) {
        boolean pulse = sl.getGameTime() % 10 == 0;
        for (Iterator<Hazard> it = ACTIVE.iterator(); it.hasNext(); ) {
            Hazard h = it.next();
            if (h.level != sl) continue;
            if (h.delay > 0) {
                h.delay--;
                continue;
            }
            if (h.once) {
                apply(sl, h);
                it.remove();
                continue;
            }
            if (--h.life <= 0) {
                it.remove();
                continue;
            }
            if (sl.getGameTime() % 4 == 0) {
                sl.sendParticles(new DustParticleOptions(h.owner != null ? h.owner.def().glow : 0x9AFF7A, 1.4F), h.pos.x, h.pos.y + 0.4, h.pos.z,
                        3, h.radius * 0.45, 0.3, h.radius * 0.45, 0.0);
                sl.sendParticles(h.bolt.trail, h.pos.x, h.pos.y + 0.3, h.pos.z, 1, h.radius * 0.4, 0.2, h.radius * 0.4, 0.0);
            }
            if (pulse) apply(sl, h);
        }
    }

    private static void apply(ServerLevel level, Hazard h) {
        AwakenMob owner = h.owner != null && h.owner.isAlive() ? h.owner : null;
        for (LivingEntity e : Abilities.enemiesNear(level, owner, h.pos, h.radius)) {
            // a cloud's or a field's pulse lands as the perilous blow its ability was told as (dodge it, never parry)
            if (owner != null) {
                dev.awaken.core.api.Strikes.deliver(owner, dev.awaken.core.api.Strikes.Kind.PERILOUS, () -> {
                    Abilities.hurt(level, owner, e, h.damage, h.bolt, h.effect == null ? null : new MobEffectInstance(h.effect));
                    return true;
                });
            } else {
                Abilities.hurt(level, null, e, h.damage, h.bolt, h.effect == null ? null : new MobEffectInstance(h.effect));
            }
        }
    }

    /** Drops everything for a level (server stopping, or a level unloading). */
    public static void clear(@Nullable Level level) {
        if (level == null) ACTIVE.clear();
        else ACTIVE.removeIf(h -> h.level == level);
    }
}
