package dev.awaken.mobs.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The finishing blow's truce (SPEC §68.4, server). A body beaten to its last point harms nobody: whatever of its is still
 * in the air (a wave, a field, a shot, a fang) lands on no one. And the one who struck the finishing blow can't be hurt
 * while its ending plays: their view may be elsewhere, and nothing should spoil the moment.
 */
public final class Finishers {
    private Finishers() {}

    private static final Map<UUID, Long> SAFE = new HashMap<>();

    /** {@code striker} takes no harm until game time {@code until}. */
    static void safe(ServerPlayer striker, long until) {
        SAFE.put(striker.getUUID(), until);
    }

    /** First of the listeners: a blow that the truce stops does nothing at all. */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (e.getSource().getEntity() instanceof AwakenBoss b && b.beaten()) {
            e.setCanceled(true);
            return;
        }
        if (SAFE.isEmpty() || !(e.getEntity() instanceof Player p)) return;
        Long until = SAFE.get(p.getUUID());
        if (until == null) return;
        if (p.level().getGameTime() > until) SAFE.remove(p.getUUID());
        else e.setCanceled(true);
    }

    public static void forget(UUID player) {
        SAFE.remove(player);
    }

    public static void clear() {
        SAFE.clear();
    }
}
