package dev.awaken.binoculars;

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
 * Server side of {@code awaken:binoculars} (SPEC §39.6): whose binoculars are up, relayed to the players tracking them.
 * At most {@link #MAX_PER_WINDOW} changes per {@link #WINDOW_TICKS} go out; one over the limit waits and is sent when the
 * window frees, so the last state always arrives. A dead player's word is ignored; death, logout, respawn and a dimension
 * change put them down. Only the sender's own pose can change. Nothing is saved.
 */
public final class BinocularsRelay {
    private BinocularsRelay() {}

    public static final int MAX_PER_WINDOW = 4, WINDOW_TICKS = 20;

    /** The production delivery filter: every tracking player whose client knows the payload (mock players don't). */
    static final Predicate<ServerPlayer> SEND = p -> p.connection != null && p.connection.hasChannel(BinocularsNet.Relay.TYPE);

    public enum Result { RELAYED, UNCHANGED, DEFERRED, REJECTED }

    private static final class Entry {
        boolean up;
        final int[] stamps = new int[MAX_PER_WINDOW];
        int next;
        Boolean pending;
    }

    private static final Map<UUID, Entry> STATE = new HashMap<>();
    private static final List<UUID> WAITING = new ArrayList<>();

    public static boolean up(ServerPlayer p) {
        Entry e = STATE.get(p.getUUID());
        return e != null && e.up;
    }

    /** Whether they're looking through them now: a change still waiting on the rate limit counts (spotting, §39.10). */
    public static boolean looking(ServerPlayer p) {
        Entry e = STATE.get(p.getUUID());
        return e != null && (e.pending != null ? e.pending : e.up);
    }

    /** A player's client says theirs went up or down. {@code deliver} filters (and, in tests, records) recipients. */
    public static Result receive(ServerPlayer p, boolean up, Predicate<ServerPlayer> deliver) {
        if (p.isDeadOrDying() || p.isRemoved()) return Result.REJECTED;
        Entry e = STATE.computeIfAbsent(p.getUUID(), u -> new Entry());
        int now = p.level().getServer().getTickCount();
        if (e.pending == null && up == e.up) return Result.UNCHANGED;
        // the oldest of the last four relays: if it is inside the window, this one waits
        int oldest = e.stamps[e.next];
        if (oldest != 0 && now - oldest < WINDOW_TICKS) {
            if (e.pending == null) WAITING.add(p.getUUID());
            e.pending = up;
            return Result.DEFERRED;
        }
        e.pending = null;
        if (up == e.up) return Result.UNCHANGED;
        relay(p, e, up, now, deliver);
        return Result.RELAYED;
    }

    private static void relay(ServerPlayer p, Entry e, boolean up, int now, Predicate<ServerPlayer> deliver) {
        e.up = up;
        e.stamps[e.next] = Math.max(1, now);
        e.next = (e.next + 1) % MAX_PER_WINDOW;
        if (p.level().getChunkSource() instanceof ServerChunkCache cache) {
            cache.sendToTrackingPlayersFiltered(p, new ClientboundCustomPayloadPacket(new BinocularsNet.Relay(p.getId(), up)), deliver);
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
            boolean up = e.pending;
            e.pending = null;
            WAITING.remove(i);
            if (up != e.up) relay(p, e, up, now, deliver);
        }
    }

    /** Someone started seeing a player whose binoculars are up: tell them now, not at the next change. */
    static void onStartTracking(ServerPlayer tracker, Entity target) {
        if (target instanceof ServerPlayer p && up(p) && SEND.test(tracker)) PacketDistributor.sendToPlayer(tracker, new BinocularsNet.Relay(p.getId(), true));
    }

    static void forget(ServerPlayer p) {
        STATE.remove(p.getUUID());
    }

    /** Death, respawn or a dimension change: down, and the players who saw them up are told (rate limit aside). */
    static void reset(ServerPlayer p, Predicate<ServerPlayer> deliver) {
        Entry e = STATE.get(p.getUUID());
        if (e == null) return;
        e.pending = null;
        if (!e.up) return;
        e.up = false;
        if (p.level().getChunkSource() instanceof ServerChunkCache cache) {
            cache.sendToTrackingPlayersFiltered(p, new ClientboundCustomPayloadPacket(new BinocularsNet.Relay(p.getId(), false)), deliver);
        }
    }
}
