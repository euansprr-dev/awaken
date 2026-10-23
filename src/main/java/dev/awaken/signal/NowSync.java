package dev.awaken.signal;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.NowHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the Now tracker's suggestions ({@link NowHooks#collect}) to each player every {@link #PERIOD} ticks, and only
 * when they changed. The client works out the compass and distance itself, so walking around costs nothing.
 */
public final class NowSync {
    private NowSync() {}

    public static final int PERIOD = 40;
    public static final int MAX = 3;

    private record Sent(Identifier dimension, List<NowHooks.Suggestion> suggestions) {}

    private static final Map<UUID, Sent> LAST = new HashMap<>();

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 11) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!AwakenNet.canReceive(p, SignalPayloads.Now.TYPE)) continue;
            update(p);
        }
    }

    /** Collects now and sends if different from the last payload. Returns whether it sent. */
    static boolean update(ServerPlayer p) {
        Sent next = new Sent(p.level().dimension().identifier(), NowHooks.collect(p, MAX));
        if (!changed(LAST.get(p.getUUID()), next)) return false;
        LAST.put(p.getUUID(), next);
        AwakenNet.send(p, new SignalPayloads.Now(next.dimension(), next.suggestions()));
        return true;
    }

    private static boolean changed(Sent before, Sent next) {
        return before == null || !before.equals(next);
    }

    /** Whether two suggestion lists would look different to the player (for tests and the sync). */
    public static boolean differs(List<NowHooks.Suggestion> a, List<NowHooks.Suggestion> b) {
        return !a.equals(b);
    }

    static void forget(UUID player) {
        LAST.remove(player);
    }

    static void clear() {
        LAST.clear();
    }
}
