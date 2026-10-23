package dev.awaken.powers;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.powers.content.PowerSummon;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Five Forces table (docs/plan.html, Section 4). Forces react the same way wherever they meet, whoever caused
 * them: any damage carrying a force is checked against what the target is (tagged, wet, burning, frozen, an Echo).
 *
 * <pre>
 * Heat    + Spores        chain explosion through every spore-tagged thing nearby
 * Charge  + Water         everything standing in water is stunned (the caster too, if they're in it)
 * Gravity + Volatile      volatile mobs pulled together detonate each other
 * Spirit  + Mirror        mirror-things shatter; spirit steps walk through mirror blocks (see Spirit powers)
 * Heat    + Frost         steam: blinds mobs and hides anyone sneaking inside it
 * Charge  + Echoes        your Echoes and summons are overcharged: hit twice as hard, then expire
 * Gravity + Architect     your walls become a battering ram (Telekinesis / Gravity Well on constructs)
 * </pre>
 * Every reaction a player causes counts {@code awaken:force_interactions}.
 */
public final class ForceInteractions {
    private ForceInteractions() {}

    public enum Reaction {
        NONE("", ""),
        SPORE_CHAIN("Heat meets Spores", "chain explosion"),
        STUN_SPREAD("Charge meets Water", "everything in the water is stunned"),
        VOLATILE_DETONATION("Gravity meets Volatile", "they detonate each other"),
        MIRROR_SHATTER("Spirit meets Mirror", "the mirror shatters"),
        STEAM("Heat meets Frost", "steam"),
        OVERCHARGE("Charge meets Echoes", "overcharged"),
        WALL_THROW("Gravity meets Architect", "your wall is a battering ram");

        public final String title;
        public final String effect;

        Reaction(String title, String effect) {
            this.title = title;
            this.effect = effect;
        }
    }

    /** What a target is, for the table. Pure data so the rules can be tested without a world. */
    public record Subject(Set<Forces.Tag> tags, boolean wet, boolean burning, boolean frozen, boolean echo, boolean volatileMob) {
        public static Subject of(LivingEntity e, @Nullable ServerPlayer attacker) {
            EnumSet<Forces.Tag> tags = EnumSet.noneOf(Forces.Tag.class);
            int bits = e.getExistingData(Forces.TAGS.get()).orElse(0);
            for (Forces.Tag t : Forces.Tag.values()) if ((bits & (1 << t.ordinal())) != 0) tags.add(t);
            boolean echo = attacker != null && isOwnEcho(attacker, e);
            return new Subject(tags, e.isInWater() || tags.contains(Forces.Tag.WATER), e.isOnFire(),
                    e.getTicksFrozen() > 0 || tags.contains(Forces.Tag.FROSTED), echo, tags.contains(Forces.Tag.VOLATILE) || e instanceof Creeper);
        }
    }

    /** The table itself. Order matters only when a target qualifies twice; the more dramatic row wins. */
    public static Reaction reactionFor(Forces.Force force, Subject s) {
        return switch (force) {
            case HEAT -> s.tags().contains(Forces.Tag.SPORES) ? Reaction.SPORE_CHAIN : s.frozen() ? Reaction.STEAM : Reaction.NONE;
            case FROST -> s.burning() ? Reaction.STEAM : Reaction.NONE;
            case CHARGE -> s.echo() ? Reaction.OVERCHARGE : s.wet() ? Reaction.STUN_SPREAD : Reaction.NONE;
            case GRAVITY -> s.volatileMob() ? Reaction.VOLATILE_DETONATION : Reaction.NONE;
            case SPIRIT -> s.tags().contains(Forces.Tag.MIRROR) ? Reaction.MIRROR_SHATTER : Reaction.NONE;
        };
    }

    public static boolean isOwnEcho(ServerPlayer player, Entity e) {
        if (e instanceof PowerSummon s) return s.getOwner() == player;
        var role = e.getExistingData(CoreApi.MOB_ROLE.get());
        return role.isPresent() && role.get().role() == MobRole.Role.ECHO;
    }

    private static int depth;
    private static final Map<Integer, Long> RECENT = new HashMap<>();
    private static final Map<UUID, Map<Reaction, Long>> TOASTED = new HashMap<>();

    // ---- event hooks ------------------------------------------------------------------------------------------------

    static void onIncoming(LivingIncomingDamageEvent event) {
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) return;
        if (Forces.forceOf(src) != Forces.Force.CHARGE) return;
        LivingEntity target = event.getEntity();
        if (!isOwnEcho(player, target)) return;
        event.setCanceled(true);
        overcharge(player, target);
    }

    static void onDamaged(LivingDamageEvent.Post event) {
        if (depth > 0) return;
        DamageSource src = event.getSource();
        Forces.Force force = Forces.forceOf(src);
        if (force == null) return;
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        ServerPlayer player = src.getEntity() instanceof ServerPlayer sp ? sp : null;
        Reaction r = reactionFor(force, Subject.of(target, player));
        if (r == Reaction.NONE || r == Reaction.OVERCHARGE) return;
        long now = level.getGameTime();
        Long last = RECENT.get(target.getId());
        if (last != null && now - last < 15) return;
        RECENT.put(target.getId(), now);
        if (RECENT.size() > 512) RECENT.entrySet().removeIf(e -> now - e.getValue() > 40);
        depth++;
        try {
            apply(r, level, target, player, event.getInflictedDamage(), src.getEntity());
        } finally {
            depth--;
        }
    }

    // ---- reactions --------------------------------------------------------------------------------------------------

    static void apply(Reaction r, ServerLevel level, LivingEntity target, @Nullable ServerPlayer player, float damage, @Nullable Entity cause) {
        float amp = player == null ? 1f : (float) (1 + PowerState.of(player).forceAmp);
        switch (r) {
            case SPORE_CHAIN -> sporeChain(level, target, Math.max(4f, damage * 0.6f) * amp, cause);
            case STUN_SPREAD -> stunSpread(level, target, Math.max(2f, damage * 0.3f) * amp, cause);
            case VOLATILE_DETONATION -> {
                if (!detonateNear(level, target, Math.max(8f, damage) * amp, cause)) return;
            }
            case MIRROR_SHATTER -> shatter(level, target, Math.max(4f, damage) * amp, cause);
            case STEAM -> {
                Forces.untag(target, Forces.Tag.FROSTED);
                target.setTicksFrozen(0);
                target.clearFire();
                Zones.steam(level, target.position(), 4.0, 160);
            }
            default -> {
                return;
            }
        }
        if (player != null) count(player, r);
    }

    /** Records a reaction for a player: the counter, the Director log, and a toast the first time in a while. */
    public static void count(ServerPlayer player, Reaction r) {
        Progression.count(player, Counters.FORCE_INTERACTIONS);
        DirectorLog.log(player, "forces", r.title + ": " + r.effect);
        long now = player.level().getGameTime();
        Map<Reaction, Long> seen = TOASTED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long last = seen.get(r);
        if (last == null || now - last > 20 * 60) {
            seen.put(r, now);
            Signal.toast(player, Component.literal(r.title + " — " + r.effect));
        }
    }

    /** Heat meets Spores: each spore-tagged thing bursts in turn, spreading to the next within 6 blocks. */
    static void sporeChain(ServerLevel level, LivingEntity first, float damage, @Nullable Entity cause) {
        Set<Integer> done = new HashSet<>();
        Deque<LivingEntity> wave = new ArrayDeque<>();
        wave.add(first);
        done.add(first.getId());
        burstStep(level, wave, done, damage, cause, 0);
    }

    private static void burstStep(ServerLevel level, Deque<LivingEntity> wave, Set<Integer> done, float damage, @Nullable Entity cause, int step) {
        if (wave.isEmpty() || step > 12) return;
        Deque<LivingEntity> next = new ArrayDeque<>();
        for (LivingEntity e : wave) {
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 20, 1.2, 0.8, 1.2, 0.05);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.8, 0.6, 0.8, 0.08);
            level.playSound(null, at.x, at.y, at.z, PowerSounds.BLAST.get(), SoundSource.PLAYERS, 1.0f, 0.9f + level.getRandom().nextFloat() * 0.3f);
            for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 7, 5, 7), x -> x.isAlive() && x != cause)) {
                if (o instanceof ServerPlayer && o == cause) continue;
                if (o.distanceToSqr(at) > 3.5 * 3.5) continue;
                if (cause instanceof ServerPlayer sp && Combat.isAlly(sp, o)) continue;
                Combat.hurtRaw(level, o, damage, Forces.Force.HEAT, cause);
                o.igniteForSeconds(3);
            }
            for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 12, 8, 12),
                    x -> x.isAlive() && !done.contains(x.getId()) && Forces.has(x, Forces.Tag.SPORES))) {
                done.add(o.getId());
                next.add(o);
            }
        }
        if (!next.isEmpty()) {
            Scheduler.after(4, () -> {
                depth++;
                try {
                    burstStep(level, next, done, damage * 0.9f, cause, step + 1);
                } finally {
                    depth--;
                }
            });
        }
    }

    /** Charge meets Water: every living thing in water within 8 blocks is stunned; the shocked take extra damage. */
    static void stunSpread(ServerLevel level, LivingEntity origin, float damage, @Nullable Entity cause) {
        Vec3 c = origin.position();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c, 16, 6, 16),
                x -> x.isAlive() && (x.isInWater() || Forces.has(x, Forces.Tag.WATER)))) {
            if (e.distanceToSqr(c) > 64) continue;
            boolean caster = e == cause;
            Combat.stun(e, caster ? 20 : 50);
            if (!caster && !(cause instanceof ServerPlayer sp && Combat.isAlly(sp, e))) Combat.hurtRaw(level, e, damage, Forces.Force.CHARGE, cause);
            Fx.lightning(level, c.add(0, 0.2, 0), e.position().add(0, 0.3, 0), 4);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.2, e.getZ(), 10, 0.5, 0.1, 0.5, 0.15);
        }
        level.playSound(null, c.x, c.y, c.z, PowerSounds.ZAP.get(), SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** Gravity meets Volatile: if another volatile thing is close, both go off. Returns false when alone. */
    static boolean detonateNear(ServerLevel level, LivingEntity a, float damage, @Nullable Entity cause) {
        LivingEntity b = null;
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(3.0),
                x -> x != a && x.isAlive() && (Forces.has(x, Forces.Tag.VOLATILE) || x instanceof Creeper))) {
            b = o;
            break;
        }
        if (b == null) return false;
        detonate(level, a, damage, cause);
        detonate(level, b, damage, cause);
        return true;
    }

    /** One volatile mob goes off: kills it and blasts everything around (never breaks blocks). */
    public static void detonate(ServerLevel level, LivingEntity v, float damage, @Nullable Entity cause) {
        Vec3 at = v.position().add(0, v.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, PowerSounds.BLAST.get(), SoundSource.PLAYERS, 1.4f, 0.8f);
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 9, 7, 9), x -> x.isAlive() && x != v)) {
            if (o.distanceToSqr(at) > 4.5 * 4.5) continue;
            if (cause instanceof ServerPlayer sp && (o == sp || Combat.isAlly(sp, o))) continue;
            Combat.hurtRaw(level, o, damage, Forces.Force.GRAVITY, cause);
            Combat.knockFrom(o, at, 1.0, 0.5);
        }
        Combat.hurtRaw(level, v, Math.max(damage * 3, v.getMaxHealth()), Forces.Force.GRAVITY, cause);
        Forces.untag(v, Forces.Tag.VOLATILE);
    }

    /** Spirit meets Mirror: the mirror-thing shatters (bonus damage) and the shock jumps to mirrors nearby. */
    static void shatter(ServerLevel level, LivingEntity target, float damage, @Nullable Entity cause) {
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Combat.hurtRaw(level, target, damage, Forces.Force.SPIRIT, cause);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()), at.x, at.y, at.z, 30, 0.5, 0.6, 0.5, 0.2);
        level.playSound(null, at.x, at.y, at.z, PowerSounds.SHATTER.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 12, 6, 12),
                x -> x != target && x.isAlive() && Forces.has(x, Forces.Tag.MIRROR))) {
            Combat.hurtRaw(level, o, damage * 0.5f, Forces.Force.SPIRIT, cause);
            Fx.line(level, at, o.position().add(0, o.getBbHeight() * 0.5, 0), ParticleTypes.END_ROD, 0.5);
        }
    }

    /** Charge meets Echoes: an Echo or summon hit by your charge hits twice as hard for a while, then expires. */
    public static void overcharge(ServerPlayer player, LivingEntity echo) {
        ServerLevel level = player.level();
        if (echo instanceof PowerSummon s) {
            if (s.isOvercharged()) return;
            s.overcharge(200);
        } else {
            var dmg = echo.getAttribute(Attributes.ATTACK_DAMAGE);
            if (dmg == null || dmg.hasModifier(OVERCHARGE_ID)) return;
            dmg.addOrUpdateTransientModifier(new AttributeModifier(OVERCHARGE_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            echo.setGlowingTag(true);
            echo.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
            Scheduler.after(200, () -> {
                if (echo.isAlive()) {
                    if (echo.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, echo.getX(), echo.getY() + 1, echo.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
                    }
                    echo.discard();
                }
            });
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, echo.getX(), echo.getY() + echo.getBbHeight() * 0.5, echo.getZ(), 24, 0.4, 0.6, 0.4, 0.25);
        level.playSound(null, echo.getX(), echo.getY(), echo.getZ(), PowerSounds.ZAP.get(), SoundSource.PLAYERS, 1.0f, 1.5f);
        count(player, Reaction.OVERCHARGE);
    }

    private static final net.minecraft.resources.Identifier OVERCHARGE_ID = dev.awaken.Awaken.id("overcharged");

    /** Charge powers with an area also overcharge your own Echoes and summons inside it. */
    public static void chargeEchoes(ServerPlayer player, Vec3 centre, double radius) {
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(centre, radius * 2, radius * 2, radius * 2),
                x -> x.isAlive() && x.position().distanceToSqr(centre) <= radius * radius && isOwnEcho(player, x))) {
            overcharge(player, e);
        }
    }

    static void clear() {
        RECENT.clear();
        TOASTED.clear();
        depth = 0;
    }
}
