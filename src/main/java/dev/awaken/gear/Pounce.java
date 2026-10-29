package dev.awaken.gear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Wildhide's full-set twist, Pounce (SPEC §44.6): crouch half a second, then jump, and you leap far the way you look;
 * the first melee hit soon after lands hard and shoves. Movement is the client's, so the jumping client applies the leap;
 * the server sees the same jump (vanilla replays it to count it) and marks the leap for its first hit.
 */
public final class Pounce {
    private Pounce() {}

    /** The local player's crouch and cooldown, counted by its client (the server keeps its own in {@link GearState}). */
    private static int clientCrouched;
    private static long clientReadyAt;

    static void onPlayerTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (p.level().isClientSide()) {
            if (p.isLocalPlayer()) clientCrouched = p.isShiftKeyDown() ? clientCrouched + 1 : 0;
            return;
        }
        GearState st = GearState.of(p);
        st.crouched = p.isShiftKeyDown() ? st.crouched + 1 : 0;
    }

    static void onJump(LivingEvent.LivingJumpEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        long now = p.level().getGameTime();
        if (p.level().isClientSide()) {
            if (!p.isLocalPlayer() || !SetBonus.pounces(p, clientCrouched, now, clientReadyAt)) return;
            clientReadyAt = now + SetBonus.POUNCE_COOLDOWN;
            clientCrouched = 0;
            p.setDeltaMovement(SetBonus.pounceVelocity(p.getYRot()));
            return;
        }
        if (!(p instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel level)) return;
        GearState st = GearState.of(sp);
        if (!SetBonus.pounces(sp, st.crouched, now, st.pounceReadyAt)) return;
        st.pounceReadyAt = now + SetBonus.POUNCE_COOLDOWN;
        st.pounceUntil = now + SetBonus.POUNCE_WINDOW;
        st.crouched = 0;
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GOAT_LONG_JUMP, SoundSource.PLAYERS, 0.8f, 1.2f);
        level.sendParticles(ParticleTypes.POOF, sp.getX(), sp.getY() + 0.1, sp.getZ(), 6, 0.25, 0.02, 0.25, 0.02);
    }

    /** The leap's first melee hit: harder, and it shoves the target along the leap. Spends the leap. */
    static void strike(LivingIncomingDamageEvent e, ServerPlayer p, LivingEntity victim, ServerLevel level) {
        GearState st = GearState.of(p);
        if (st.pounceUntil == 0 || level.getGameTime() > st.pounceUntil) return;
        st.pounceUntil = 0;
        e.setAmount((float) (e.getAmount() * (1 + SetBonus.POUNCE_BONUS)));
        double a = Math.toRadians(p.getYRot());
        victim.push(-Math.sin(a) * SetBonus.POUNCE_SHOVE, 0.25, Math.cos(a) * SetBonus.POUNCE_SHOVE);
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /** Whether the server holds an unspent leap for this player (tests). */
    static boolean leaping(ServerPlayer p) {
        GearState st = GearState.of(p);
        return st.pounceUntil != 0 && p.level().getGameTime() <= st.pounceUntil;
    }
}
