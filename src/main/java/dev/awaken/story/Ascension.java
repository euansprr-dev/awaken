package dev.awaken.story;

import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.StoryHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Ascension per player (SPEC "Together"): a creature is scaled for the tier of the player nearest it when it appears,
 * and every hit between it and another player is evened out to that player's own tier. A friend who hasn't reached the
 * world's ending meets tier 0 creatures beside a veteran who meets the world's tier: the veteran's hits land as if the
 * creature had the veteran's tier of health, the friend's as if it had theirs; its melee hits each as hard as their
 * own tier says (projectiles were never scaled). In a world without Ascension nothing here runs.
 */
final class Ascension {
    private Ascension() {}

    /** +30% health and +15% damage per tier (SPEC §13 "Become the Signal"), matching {@link MobHooks}. */
    static final double HEALTH = 0.3, DAMAGE = 0.15;

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (!(target.level() instanceof ServerLevel level) || StoryWorld.get(level.getServer()).ascension <= 0) return;
        Entity src = e.getSource().getEntity();
        if (target instanceof ServerPlayer sp && src instanceof LivingEntity mob && !(src instanceof Player)) {
            if (e.getSource().getDirectEntity() != src) return; // melee only: its damage attribute carries its tier
            int baked = MobHooks.scaledAscension(mob);
            if (baked < 0) return;
            e.setAmount(e.getAmount() * (float) damageToPlayer(baked, StoryHooks.ascensionOf(sp)));
        } else if (src instanceof ServerPlayer sp && !(target instanceof Player)) {
            int baked = MobHooks.scaledAscension(target);
            if (baked < 0) return;
            e.setAmount(e.getAmount() * (float) damageToCreature(baked, StoryHooks.ascensionOf(sp)));
        }
    }

    /** A creature scaled for tier {@code baked} hits a player of tier {@code mine}: this factor on its damage. */
    static double damageToPlayer(int baked, int mine) {
        return mine == baked ? 1.0 : (1 + DAMAGE * mine) / (1 + DAMAGE * baked);
    }

    /** A player of tier {@code mine} hits a creature scaled for tier {@code baked}: this factor (its health as their tier). */
    static double damageToCreature(int baked, int mine) {
        return mine == baked ? 1.0 : (1 + HEALTH * baked) / (1 + HEALTH * mine);
    }
}
