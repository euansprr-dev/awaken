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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.ToDoubleFunction;

/**
 * The combat side of Hunter gear, from one {@link LivingIncomingDamageEvent} listener (low priority, so it only sees
 * hits nobody cancelled): a focus raises power damage, Gatewrought primes and spends Force strikes and wards off Force
 * damage, Veilwoven wards off Spirit damage and can let a hit pass through (Veil Flicker).
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
        if (isPower(src)) {
            double bonus = bestFocus(p);
            if (bonus > 0) {
                float before = e.getAmount();
                float after = (float) (before * (1 + bonus));
                if (isBoss(victim)) after = Math.min(after, Math.max(before, victim.getMaxHealth() * BOSS_HIT_CAP));
                e.setAmount(after);
                focusEnergy(p, level);
            }
            if (SetBonus.has(p, HunterTier.C, SetBonus.FULL)) {
                GearState st = GearState.of(p);
                st.primedUntil = level.getGameTime() + SetBonus.PRIME_TICKS;
                st.primedForce = Forces.forceOf(src);
            }
            return;
        }
        if (src.is(DamageTypes.PLAYER_ATTACK) && src.getDirectEntity() == p) strike(e, p, victim, level);
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
            if (now >= st.flickerReadyAt && roll.applyAsDouble(vp) < SetBonus.flickerChance(Progression.stat(vp, Stat.SPI))) {
                st.flickerReadyAt = now + SetBonus.FLICKER_COOLDOWN;
                e.setCanceled(true);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, vp.getX(), vp.getY() + 1, vp.getZ(), 24, 0.35, 0.6, 0.35, 0.02);
                level.playSound(null, vp.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.3f);
                return true;
            }
        }
        double mul = 1;
        if (Forces.forceOf(src) != null) mul *= 1 - SetBonus.forceWard(SetBonus.count(vp, HunterTier.C));
        if (veil >= SetBonus.HALF && src.is(Forces.SPIRIT)) mul *= 1 - SetBonus.SPIRIT_WARD;
        if (mul < 1) e.setAmount((float) (e.getAmount() * mul));
        return false;
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
