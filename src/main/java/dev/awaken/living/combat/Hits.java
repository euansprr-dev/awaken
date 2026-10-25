package dev.awaken.living.combat;

import dev.awaken.core.Forces;
import dev.awaken.living.LivingModule;
import dev.awaken.living.LivingNet;
import dev.awaken.powers.Powers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tells the attacker what each hit did (SPEC §21.1: the damage glyphs): the damage the target took, whether it was a crit
 * (vanilla's or the PER roll, read from the crit event that decided the swing), and its Force. Also remembers who was in
 * a fight lately, so the level moment can wait for it to end.
 */
public final class Hits {
    private Hits() {}

    private static final Map<UUID, Long> CRIT_AT = new HashMap<>();
    private static final Map<UUID, Long> FIGHT_AT = new HashMap<>();
    /** A fight: hurt or hitting within this many ticks. */
    public static final int FIGHT_TICKS = 100;

    public static void onCrit(CriticalHitEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.isCriticalHit()) CRIT_AT.put(sp.getUUID(), sp.level().getGameTime());
    }

    public static void onDamage(LivingDamageEvent.Post e) {
        LivingEntity target = e.getEntity();
        if (target instanceof ServerPlayer hurt) {
            FIGHT_AT.put(hurt.getUUID(), hurt.level().getGameTime());
            if (e.getSource().getEntity() instanceof LivingEntity by && by != hurt && LivingModule.shown(hurt.level().getServer())) {
                dev.awaken.core.AwakenNet.send(hurt, new LivingNet.HitFx(by.getId(), e.getInflictedDamage(), false, -1, true));
            }
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || sp == target) return;
        long now = sp.level().getGameTime();
        FIGHT_AT.put(sp.getUUID(), now);
        float amount = e.getInflictedDamage();
        if (amount <= 0 || !LivingModule.shown(sp.level().getServer())) return;
        Long crit = CRIT_AT.remove(sp.getUUID());
        boolean isCrit = crit != null && now - crit <= 1 && e.getSource().getDirectEntity() == sp;
        Set<Forces.Force> forces = Powers.forcesOf(e.getSource());
        int force = forces.isEmpty() ? -1 : forces.iterator().next().ordinal();
        dev.awaken.core.AwakenNet.send(sp, new LivingNet.HitFx(target.getId(), amount, isCrit, force, false));
    }

    /** The player's next blow is a crit (tours, where nothing falls or rolls PER). */
    public static void markCrit(ServerPlayer p) {
        CRIT_AT.put(p.getUUID(), p.level().getGameTime());
    }

    /** Whether the player was hurt or hit something in the last five seconds. */
    public static boolean inFight(ServerPlayer p) {
        Long at = FIGHT_AT.get(p.getUUID());
        return at != null && p.level().getGameTime() - at < FIGHT_TICKS;
    }

    public static void forget(UUID id) {
        CRIT_AT.remove(id);
        FIGHT_AT.remove(id);
    }
}
