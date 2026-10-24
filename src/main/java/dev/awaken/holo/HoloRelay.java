package dev.awaken.holo;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server side of {@code awaken:holo_state}: which panel each player has open, relayed to the players tracking them.
 * At most {@link #MAX_PER_WINDOW} changes per {@link #WINDOW_TICKS} are relayed; a change over the limit waits and
 * is sent when the window frees up, so the last state always arrives. Unknown kinds are refused. Nothing is saved.
 */
public final class HoloRelay {
    private HoloRelay() {}

    public static final int MAX_PER_WINDOW = 4, WINDOW_TICKS = 20;

    /** The production delivery filter: every tracking player whose client knows the payload (mock players don't). */
    static final Predicate<ServerPlayer> SEND = p -> p.connection != null && p.connection.hasChannel(HoloNet.Relay.TYPE);

    public enum Result { RELAYED, UNCHANGED, DEFERRED, REJECTED }

    private static final class Entry {
        HoloKind kind = HoloKind.NONE;
        final int[] stamps = new int[MAX_PER_WINDOW];
        int next;
        HoloKind pending;
    }

    private static final Map<UUID, Entry> STATE = new HashMap<>();
    private static final List<UUID> WAITING = new ArrayList<>();

    public static HoloKind kind(ServerPlayer p) {
        Entry e = STATE.get(p.getUUID());
        return e == null ? HoloKind.NONE : e.kind;
    }

    /** A player's client says it opened or closed a panel. {@code deliver} filters (and, in tests, records) recipients. */
    public static Result receive(ServerPlayer p, int kindId, Predicate<ServerPlayer> deliver) {
        HoloKind kind = HoloKind.byId(kindId);
        if (kind == null) return Result.REJECTED;
        Entry e = STATE.computeIfAbsent(p.getUUID(), u -> new Entry());
        int now = p.level().getServer().getTickCount();
        if (e.pending == null && kind == e.kind) return Result.UNCHANGED;
        // the oldest of the last four relays: if it is inside the window, this one waits
        int oldest = e.stamps[e.next];
        boolean full = oldest != 0 && now - oldest < WINDOW_TICKS;
        if (full) {
            if (e.pending == null) WAITING.add(p.getUUID());
            e.pending = kind;
            return Result.DEFERRED;
        }
        e.pending = null;
        if (kind == e.kind) return Result.UNCHANGED;
        relay(p, e, kind, now, deliver);
        return Result.RELAYED;
    }

    private static void relay(ServerPlayer p, Entry e, HoloKind kind, int now, Predicate<ServerPlayer> deliver) {
        e.kind = kind;
        e.stamps[e.next] = Math.max(1, now);
        e.next = (e.next + 1) % MAX_PER_WINDOW;
        if (p.level().getChunkSource() instanceof ServerChunkCache cache) {
            cache.sendToTrackingPlayersFiltered(p, new ClientboundCustomPayloadPacket(new HoloNet.Relay(p.getId(), kind.id)), deliver);
        }
    }

    /** Sends deferred changes whose window has freed up. Called every server tick (cheap: only waiting players). */
    static void flush(MinecraftServer server, Predicate<ServerPlayer> deliver) {
        if (WAITING.isEmpty()) return;
        int now = server.getTickCount();
        for (int i = WAITING.size() - 1; i >= 0; i--) {
            UUID id = WAITING.get(i);
            Entry e = STATE.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (e == null || e.pending == null || p == null) {
                WAITING.remove(i);
                continue;
            }
            int oldest = e.stamps[e.next];
            if (oldest != 0 && now - oldest < WINDOW_TICKS) continue;
            HoloKind kind = e.pending;
            e.pending = null;
            WAITING.remove(i);
            if (kind != e.kind) relay(p, e, kind, now, deliver);
        }
    }

    /** Someone started seeing a player who has a panel open: tell them now, not at the next change. */
    static void onStartTracking(ServerPlayer tracker, Entity target) {
        if (!(target instanceof ServerPlayer p)) return;
        HoloKind k = kind(p);
        if (k != HoloKind.NONE && SEND.test(tracker)) PacketDistributor.sendToPlayer(tracker, new HoloNet.Relay(p.getId(), k.id));
    }

    static void forget(ServerPlayer p) {
        STATE.remove(p.getUUID());
    }

    /** Respawn or dimension change: the panel closed with the old body. */
    static void reset(ServerPlayer p) {
        Entry e = STATE.get(p.getUUID());
        if (e != null) {
            e.kind = HoloKind.NONE;
            e.pending = null;
        }
    }
}
