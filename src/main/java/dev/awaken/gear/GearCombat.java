package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.ToDoubleFunction;

/**
 * The combat side of Hunter gear, from one {@link LivingIncomingDamageEvent} listener (low priority, so it only sees
 * hits nobody cancelled): a focus raises power damage, Gatewrought primes and spends Force strikes and wards off Force
 * damage, Veilwoven wards off Spirit damage and can let a hit pass through (Veil Flicker), Lordscale wards off Gravity
 * and Charge and overflows every fifth power hit of a Force, Sovereign's Measure lifts power hits. Unbowed (Sovereign
 * Weave) listens to deaths on either side of the gates' own handler.
 */
public final class GearCombat {
    private GearCombat() {}

    /** Extra power damage per temper level on a focus (the Forge's temper adds melee damage to everything else). */
    public static final double FOCUS_PER_TEMPER = 0.02;
    /** A power hit on a boss never takes more than this share of its health (powers' own cap, SPEC §13). */
    private static final float BOSS_HIT_CAP = 0.2f;

    /** The Veil Flicker roll; tests swap it for a fixed value. */
    static ToDoubleFunction<ServerPlayer> roll = p -> p.getRandom().nextDouble();

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource src = e.getSource();
        if (victim instanceof ServerPlayer vp && defend(e, vp, src, level)) return;
        if (src.getEntity() instanceof ServerPlayer p && p != victim) attack(e, p, victim, src, level);
    }

    // ---- attacking --------------------------------------------------------------------------------------------------

    /** Whether a damage source is power damage (one of the Five Forces or plain Kinetic power damage). */
    public static boolean isPower(DamageSource src) {
        return src.is(Forces.KINETIC) || Forces.forceOf(src) != null;
    }

    private static void attack(LivingIncomingDamageEvent e, ServerPlayer p, LivingEntity victim, DamageSource src, ServerLevel level) {
        if (GearState.of(p).bursting) return; // an Overflow burst is the bonus itself
        if (isPower(src)) {
            double bonus = bestFocus(p);
            double measure = SetBonus.measurePower(p);
            if (bonus > 0 || measure > 0) {
                float before = e.getAmount();
                float after = (float) (before * (1 + bonus) * (1 + measure));
                if (isBoss(victim)) after = Math.min(after, Math.max(before, victim.getMaxHealth() * BOSS_HIT_CAP));
                e.setAmount(after);
                if (bonus > 0) focusEnergy(p, level);
            }
            if (SetBonus.has(p, HunterTier.C, SetBonus.FULL)) {
                GearState st = GearState.of(p);
                st.primedUntil = level.getGameTime() + SetBonus.PRIME_TICKS;
                st.primedForce = Forces.forceOf(src);
            }
            if (SetBonus.has(p, HunterTier.A, SetBonus.FULL)) overflow(e.getAmount(), p, victim, src, level);
            return;
        }
        if (src.is(DamageTypes.PLAYER_ATTACK) && src.getDirectEntity() == p) {
            Pounce.strike(e, p, victim, level);
            strike(e, p, victim, level);
        }
    }

    /** Spends a primed Force strike on a melee hit (Gatewrought, four pieces). */
    private static void strike(LivingIncomingDamageEvent e, ServerPlayer p, LivingEntity victim, ServerLevel level) {
        GearState st = GearState.of(p);
        if (st.primedUntil == 0 || level.getGameTime() > st.primedUntil || !SetBonus.has(p, HunterTier.C, SetBonus.FULL)) return;
        Forces.Force f = st.primedForce;
        st.primedUntil = 0;
        st.primedForce = null;
        float amount = (float) (e.getAmount() * (1 + SetBonus.PRIME_BONUS + (f == Forces.Force.CHARGE ? SetBonus.PRIME_CHARGE_EXTRA : 0)));
        e.setAmount(amount);
        ParticleOptions fx = ParticleTypes.CRIT;
        if (f != null) {
            switch (f) {
                case HEAT -> {
                    victim.igniteForTicks(60);
                    fx = ParticleTypes.FLAME;
                }
                case FROST -> {
                    victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), p);
                    fx = ParticleTypes.SNOWFLAKE;
                }
                case CHARGE -> fx = ParticleTypes.ELECTRIC_SPARK;
                case GRAVITY -> {
                    victim.push(0, 0.45, 0);
                    fx = ParticleTypes.REVERSE_PORTAL;
                }
                case SPIRIT -> {
                    p.heal((float) (amount * SetBonus.PRIME_SPIRIT_LEECH));
                    fx = ParticleTypes.SOUL;
                }
            }
        }
        level.sendParticles(fx, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 14, 0.3, 0.35, 0.3, 0.04);
        level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.7f);
    }

    // ---- Overflow (Lordscale, four pieces) ------------------------------------------------------------------------

    /**
     * Every fifth power hit of one Force (Kinetic counts as its own) bursts around the target: half the hit to every foe
     * within 4 blocks, with the Force's touch. Players, Echoes, townsfolk and tamed animals are never caught in it.
     */
    static void overflow(float hit, ServerPlayer p, LivingEntity victim, DamageSource src, ServerLevel level) {
        Forces.Force f = Forces.forceOf(src);
        GearState st = GearState.of(p);
        int k = f == null ? 5 : f.ordinal();
        if (++st.forceHits[k] < SetBonus.OVERFLOW_EVERY) return;
        st.forceHits[k] = 0;
        float burst = (float) (hit * SetBonus.OVERFLOW_SHARE * (f == Forces.Force.CHARGE ? 1 + SetBonus.OVERFLOW_CHARGE_EXTRA : 1));
        DamageSource bs = level.damageSources().source(f == null ? Forces.KINETIC : Forces.key(f), p);
        float dealt = 0;
        st.bursting = true;
        try {
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(SetBonus.OVERFLOW_RADIUS),
                    t -> overflowTarget(p, victim, t))) {
                float amount = isBoss(t) ? Math.min(burst, t.getMaxHealth() * BOSS_HIT_CAP) : burst;
                if (!t.hurtServer(level, bs, amount)) continue;
                dealt += amount;
                if (f == null) {
                    var away = t.position().subtract(victim.position()).normalize().scale(0.45);
                    t.push(away.x, 0.15, away.z);
                } else {
                    switch (f) {
                        case HEAT -> t.igniteForTicks(40);
                        case FROST -> t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), p);
                        case GRAVITY -> t.push(0, 0.35, 0);
                        default -> {}
                    }
                }
            }
        } finally {
            st.bursting = false;
        }
        if (f == Forces.Force.SPIRIT && dealt > 0) p.heal(Math.min(4f, (float) (dealt * SetBonus.OVERFLOW_SPIRIT_LEECH)));
        ParticleOptions fx = f == null ? ParticleTypes.SWEEP_ATTACK : switch (f) {
            case HEAT -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case CHARGE -> ParticleTypes.ELECTRIC_SPARK;
            case GRAVITY -> ParticleTypes.REVERSE_PORTAL;
            case SPIRIT -> ParticleTypes.SOUL;
        };
        double cx = victim.getX(), cy = victim.getY() + victim.getBbHeight() * 0.5, cz = victim.getZ();
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            level.sendParticles(fx, cx, cy, cz, 0, Math.cos(a), 0.05, Math.sin(a), 0.35);
        }
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2A541), cx, cy, cz, 1, 0, 0, 0, 0);
        level.playSound(null, victim.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    /** Who an Overflow can catch: living foes around the target, never the caster's side. */
    static boolean overflowTarget(ServerPlayer p, LivingEntity victim, LivingEntity t) {
        if (t == victim || t == p || !t.isAlive() || t instanceof Player) return false;
        MobRole.Role role = MobRole.of(t).role();
        if (role == MobRole.Role.ECHO || role == MobRole.Role.SPIRIT) return false;
        if (t instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() != null) return false;
        return t instanceof net.minecraft.world.entity.monster.Enemy || role != MobRole.Role.NONE;
    }

    /** Whether the player's next melee hit is Force-primed right now. */
    public static boolean primed(ServerPlayer p) {
        GearState st = GearState.of(p);
        return st.primedUntil != 0 && p.level().getGameTime() <= st.primedUntil;
    }

    /**
     * Energy a focus gives back for a power hit that lands, at most once a second: E 0.5, D 1, C 1.5, B 2. Modest on
     * purpose (regen is 4.4/s at INT 5): a focus keeps a fight going a little longer, it never pays for a cast.
     */
    public static float focusEnergy(ItemStack stack) {
        if (!(stack.getItem() instanceof HunterGearItem g) || g.piece != Piece.FOCUS) return 0;
        return 0.5f * (g.tier.ordinal() + 1);
    }

    /** Ticks between two focus refunds. */
    static final int FOCUS_ENERGY_GAP = 20;

    private static void focusEnergy(ServerPlayer p, ServerLevel level) {
        float amount = Math.max(focusEnergy(p.getMainHandItem()), focusEnergy(p.getOffhandItem()));
        if (amount <= 0) return;
        GearState st = GearState.of(p);
        long now = level.getGameTime();
        if (now - st.focusEnergyAt < FOCUS_ENERGY_GAP) return;
        st.focusEnergyAt = now;
        dev.awaken.powers.Powers.restoreEnergy(p, amount);
    }

    /** The best focus bonus in either hand (they don't stack). */
    public static double bestFocus(Player p) {
        return Math.max(focusBonus(p.getMainHandItem()), focusBonus(p.getOffhandItem()));
    }

    /** A focus's power damage bonus: its tier's, plus {@link #FOCUS_PER_TEMPER} per Forge temper level. */
    public static double focusBonus(ItemStack stack) {
        if (!(stack.getItem() instanceof HunterGearItem g) || g.piece != Piece.FOCUS) return 0;
        return g.tier.focus.powerBonus() + FOCUS_PER_TEMPER * temper(stack);
    }

    private static boolean isBoss(LivingEntity e) {
        return e.getExistingData(CoreApi.MOB_ROLE.get()).map(r -> r.role() == MobRole.Role.BOSS).orElse(false);
    }

    // ---- defending --------------------------------------------------------------------------------------------------

    /** Wards and the Veil Flicker. Returns true when the hit was cancelled. */
    private static boolean defend(LivingIncomingDamageEvent e, ServerPlayer vp, DamageSource src, ServerLevel level) {
        int veil = SetBonus.count(vp, HunterTier.B);
        if (veil >= SetBonus.FULL && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && vp.getHealth() < vp.getMaxHealth() * SetBonus.FLICKER_BELOW) {
            GearState st = GearState.of(vp);
            long now = level.getGameTime();
            if (now >= st.flickerReadyAt && roll.applyAsDouble(vp) < SetBonus.flickerChance(SetBonus.stat(vp, Stat.SPI))) {
                st.flickerReadyAt = now + SetBonus.FLICKER_COOLDOWN;
                e.setCanceled(true);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, vp.getX(), vp.getY() + 1, vp.getZ(), 24, 0.35, 0.6, 0.35, 0.02);
                level.playSound(null, vp.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.3f);
                return true;
            }
        }
        double mul = 1;
        Forces.Force force = Forces.forceOf(src);
        if (force != null) mul *= 1 - SetBonus.forceWard(SetBonus.count(vp, HunterTier.C));
        if (force == Forces.Force.GRAVITY || force == Forces.Force.CHARGE) mul *= 1 - SetBonus.lordWard(SetBonus.count(vp, HunterTier.A));
        if (veil >= SetBonus.HALF && src.is(Forces.SPIRIT)) mul *= 1 - SetBonus.SPIRIT_WARD;
        if (mul < 1) e.setAmount((float) (e.getAmount() * mul));
        return false;
    }

    // ---- Unbowed (Sovereign Weave, two pieces) ---------------------------------------------------------------------

    /** Before anything handles a player's death: remember their Fracture. */
    static void beforeDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && SetBonus.count(sp, HunterTier.S) >= SetBonus.HALF) {
            GearState.of(sp).fractureBefore = Progression.profile(sp).fractureUntil();
        }
    }

    /**
     * After the gates handled the death: if the Hunter fell to a gate below their rank, the Fracture that death left is
     * taken back (one from before the death stays).
     */
    static void afterDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        GearState st = GearState.of(sp);
        long before = st.fractureBefore;
        st.fractureBefore = -1;
        if (before < 0) return;
        long now = level.getGameTime(), after = Progression.profile(sp).fractureUntil();
        if (after <= Math.max(before, now)) return; // this death didn't Fracture
        int gateRank = gateRank(level, e.getSource().getEntity(), sp.getLastHurtByMob());
        if (!SetBonus.waivesFracture(SetBonus.count(sp, HunterTier.S), Progression.rank(sp).ordinal(), gateRank)) return;
        if (before > now) Progression.fracture(sp, (int) Math.min(Integer.MAX_VALUE, before - now));
        else Progression.clearFracture(sp);
        dev.awaken.signal.Signal.toast(sp, net.minecraft.network.chat.Component.literal("Unbowed · a lesser gate can't Fracture you"));
    }

    /** The rank of the gate whose monster killed (or last hurt) the player, or -1 if it wasn't a gate's. */
    static int gateRank(ServerLevel level, net.minecraft.world.entity.@Nullable Entity killer, @Nullable LivingEntity lastHurt) {
        for (net.minecraft.world.entity.Entity e : new net.minecraft.world.entity.Entity[]{killer, lastHurt}) {
            if (e == null) continue;
            String gate = MobRole.of(e).gateId();
            if (gate.isEmpty() || gate.startsWith("trial:")) continue;
            var view = dev.awaken.core.api.GateHooks.get(level.getServer().overworld(), gate);
            if (view.isPresent()) return view.get().rank();
        }
        return -1;
    }

    // ---- the Forge's temper, read by id so gear never depends on the hearth module's classes ----------------------

    private static @Nullable DataComponentType<Integer> temperType;
    private static boolean temperLooked;

    @SuppressWarnings("unchecked")
    public static int temper(ItemStack stack) {
        if (!temperLooked) {
            temperType = (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Awaken.id("temper"));
            temperLooked = true;
        }
        if (temperType == null) return 0;
        Integer v = stack.get(temperType);
        return v == null ? 0 : v;
    }
}
