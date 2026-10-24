package dev.awaken.signal;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends each player their Horizon line ({@link HorizonHooks#collect}, SPEC §17) every {@link #PERIOD} ticks, and only
 * when it changed: the client works out the compass and distance itself, like the Now tracker's.
 */
public final class HorizonSync {
    private HorizonSync() {}

    public static final int PERIOD = 40;

    private record Sent(Identifier dimension, Optional<HorizonHooks.Line> line) {}

    private static final Map<UUID, Sent> LAST = new HashMap<>();

    /** The wilds setting (on when the config isn't loaded yet). */
    static boolean on() {
        try {
            return AwakenConfig.WILDS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 23) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (AwakenNet.canReceive(p, SignalPayloads.Horizon.TYPE)) update(p);
        }
    }

    /** Collects now and sends if different from the last payload. Returns whether it sent. */
    static boolean update(ServerPlayer p) {
        Sent next = new Sent(p.level().dimension().identifier(), on() ? HorizonHooks.collect(p) : Optional.empty());
        if (next.equals(LAST.get(p.getUUID()))) return false;
        LAST.put(p.getUUID(), next);
        AwakenNet.send(p, new SignalPayloads.Horizon(next.dimension(), next.line()));
        return true;
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clear() {
        LAST.clear();
    }
}
