package dev.awaken.gear;

import dev.awaken.core.Forces;
import dev.awaken.core.api.NowHooks;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Short-lived per-player combat state (never saved): a primed strike, the Veil Flicker cooldown, the Now cache. */
final class GearState {
    private static final Map<UUID, GearState> STATES = new HashMap<>();

    /** Game time until which the next melee hit is Force-primed (Gatewrought, 4 pieces); 0 = not primed. */
    long primedUntil;
    /** The Force of the power hit that primed it, or null for a plain (Kinetic) power. */
    Forces.@Nullable Force primedForce;
    /** Game time from which Veil Flicker can save you again (Veilwoven, 4 pieces). */
    long flickerReadyAt;
    /** Last meditation heal (Veilwoven, 2 pieces). */
    long lastHeal;
    /** Last energy a focus gave back for a power hit (at most once a second). */
    long focusEnergyAt = Long.MIN_VALUE / 2;
    /** Power hits per Force since the last Overflow (Lordscale, 4 pieces): the five Forces, then Kinetic. */
    final int[] forceHits = new int[6];
    /** True while an Overflow burst is being dealt, so the burst never feeds itself, a focus or a primed strike. */
    boolean bursting;
    /** Fracture end before a death was handled (Unbowed restores it), or -1. */
    long fractureBefore = -1;
    /** The Now tracker's last answer and when it was worked out. */
    List<NowHooks.Suggestion> now = List.of();
    long nowAt = Long.MIN_VALUE;

    static GearState of(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new GearState());
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void clear() {
        STATES.clear();
    }
}
