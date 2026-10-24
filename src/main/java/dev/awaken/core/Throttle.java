package dev.awaken.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player rate limits for client-sent actions (a dedicated server must not do work as fast as a client can send
 * packets). Server thread only; forgotten on logout and server stop. Time is the overworld's game time.
 */
public final class Throttle {
    private Throttle() {}

    /** key → {window start, count in window}. */
    private static final Map<UUID, Map<String, long[]>> SEEN = new HashMap<>();

    /** True at most once per {@code ticks} for this player and key. */
    public static boolean allow(ServerPlayer p, String key, int ticks) {
        return allowBurst(p, key, 1, ticks);
    }

    /** True at most {@code max} times per {@code window} ticks for this player and key. */
    public static boolean allowBurst(ServerPlayer p, String key, int max, int window) {
        long now = p.level().getServer().overworld().getGameTime();
        long[] w = SEEN.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).computeIfAbsent(key, k -> new long[]{now, 0});
        if (now - w[0] >= window || now < w[0]) {
            w[0] = now;
            w[1] = 0;
        }
        if (w[1] >= max) return false;
        w[1]++;
        return true;
    }

    static void forget(UUID player) {
        SEEN.remove(player);
    }

    static void clear() {
        SEEN.clear();
    }
}
