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
