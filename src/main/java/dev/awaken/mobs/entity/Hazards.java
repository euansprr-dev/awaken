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
        /** How its one strike lands (a cloud's or a field's pulse is PERILOUS; a rising spike is its ability's own class). */
        dev.awaken.core.api.Strikes.Kind kind = dev.awaken.core.api.Strikes.Kind.PERILOUS;
        /** A spike of its owner's own matter out of the floor (SPEC §60.8): 0 none, 1 the stir before it, 2 the spike. */
        int erupt;

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

    /** A cloud that pulses every 10 ticks for {@code life} ticks, after {@code delay} ticks of showing harmless. */
    public static void cloud(ServerLevel level, @Nullable AwakenMob owner, Vec3 pos, float radius, int life, float damage, BoltKind bolt,
                             @Nullable MobEffectInstance effect, int delay) {
        if (ACTIVE.size() >= MAX) return;
        add(new Hazard(level, owner, pos, radius, delay, life, damage, bolt, effect, false));
    }

    /** One strike after {@code delay} ticks (root lances, fuse lines). */
    public static void strike(ServerLevel level, @Nullable AwakenMob owner, Vec3 pos, float radius, int delay, float damage, BoltKind bolt, @Nullable MobEffectInstance effect) {
        if (ACTIVE.size() >= MAX) return;
        add(new Hazard(level, owner, pos, radius, delay, 1, damage, bolt, effect, true));
    }

    /**
     * A gate monster's spike out of the floor, in place of a borrowed fang (SPEC §60.8): its matter stirs on the floor
     * at {@code delay}, and {@code rise} ticks later bursts up and bites whoever stands there, as a blow of {@code kind}.
     */
    public static void rise(ServerLevel level, AwakenMob owner, Vec3 pos, float radius, int delay, int rise, float damage, BoltKind bolt,
                            @Nullable MobEffectInstance effect, dev.awaken.core.api.Strikes.Kind kind) {
        if (ACTIVE.size() >= MAX - 1) return;
        Hazard stir = new Hazard(level, owner, pos, 0, delay, 1, 0, bolt, null, true);
        stir.erupt = 1;
        add(stir);
        Hazard spike = new Hazard(level, owner, pos, radius, delay + rise, 1, damage, bolt, effect, true);
        spike.erupt = 2;
        spike.kind = kind;
        add(spike);
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
                sl.sendParticles(h.bolt.trail(), h.pos.x, h.pos.y + 0.3, h.pos.z, 1, h.radius * 0.4, 0.2, h.radius * 0.4, 0.0);
            }
            if (pulse) apply(sl, h);
        }
    }

    private static void apply(ServerLevel level, Hazard h) {
        AwakenMob owner = h.owner != null && h.owner.isAlive() ? h.owner : null;
        if (h.erupt == 1) {
            Abilities.shed(level, h.owner, h.pos.add(0, 0.08, 0), 4, 0.22, 0.03, 0.22, 0.015);
            return;
        }
        if (h.erupt == 2) {
            // the spike: a column of its matter thrown up out of the floor, gone as fast as it came
            Abilities.shed(level, h.owner, h.pos.add(0, 0.55, 0), 14, 0.14, 0.5, 0.14, 0.09);
            Abilities.shed(level, h.owner, h.pos.add(0, 0.1, 0), 5, 0.3, 0.04, 0.3, 0.05);
            if (h.owner != null) {
                net.minecraft.sounds.SoundEvent s = dev.awaken.mobs.MobSounds.monster(h.owner.def().id, "blow");
                if (s != null) level.playSound(null, h.pos.x, h.pos.y, h.pos.z, s, h.owner.getSoundSource(), 0.3F, 1.25F + level.getRandom().nextFloat() * 0.2F);
            }
        }
        for (LivingEntity e : Abilities.enemiesNear(level, owner, h.pos, h.radius)) {
            // a cloud's or a field's pulse lands as the perilous blow its ability was told as (dodge it, never parry);
            // a spike as the class its own ability was told as
            if (owner != null) {
                dev.awaken.core.api.Strikes.deliver(owner, h.kind, () -> {
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
