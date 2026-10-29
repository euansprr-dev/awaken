package dev.awaken.core.api;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.CoreModule;
import dev.awaken.core.CorePayloads;
import dev.awaken.core.EdgeHold;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Awaken's own teleports wait for their land off the server thread (SPEC §28.9, "Arrivals"). A teleport into chunks not
 * made yet waits on the server thread while they generate (24 s and 51 s on the CI runner, run 40741943440): the whole
 * world stops. {@link #go} moves the player at once when the land is there; when it isn't, it puts a ticket on the 3×3
 * chunks round the arrival, waits for them asynchronously (no join on the server thread), holds the player in a short
 * transition of their own on the client (a dusk with the land drawing itself, input held, never a frozen frame; nothing
 * hurts them meanwhile) and moves them once the land is made. A newer move replaces an older one; logging out, dying,
 * respawning or a command's teleport drops it; past
 * {@link #MOST_MS} the move runs anyway, as it would have without this (logged). Vanilla's /tp and other mods' teleports
 * aren't routed here: the packet cap and the watchdog cover them. Server side; the client half is
 * {@code core.client.PassageClient}.
 */
public final class Passage {
    private Passage() {}

    /** The land loaded before the move: the chunks this far round the arrival's, at FULL (a 3×3). */
    public static final int RADIUS = 1;
    /** The longest wait (a minute, wall clock: the test server ticks back to back): past it the move runs anyway. */
    public static final long MOST_MS = 60_000;

    private static final class Wait {
        final ServerPlayer player;
        final ServerLevel to;
        final double x, z;
        final Runnable move, dropped;
        final long since = Util.getMillis();
        boolean done;

        Wait(ServerPlayer player, ServerLevel to, double x, double z, Runnable move, Runnable dropped) {
            this.player = player;
            this.to = to;
            this.x = x;
            this.z = z;
            this.move = move;
            this.dropped = dropped;
        }

        /** Not moving after all: whatever the caller does in that case. */
        void drop() {
            done = true;
            try {
                dropped.run();
            } catch (RuntimeException e) {
                Awaken.LOG.error("[passage] the dropped move for {} failed", player.getGameProfile().name(), e);
            }
        }
    }

    /**
     * On the GameTest server Awaken's tests read where a teleport left the player in the same tick, so there the move
     * runs at once, as it did before ({@code core_passage_far} turns the wait on for itself, in a batch of its own).
     */
    private static boolean waitInTests;

    /** Moves under way, by player (server thread only). */
    private static final Map<UUID, Wait> WAITS = new HashMap<>();

    /** {@code move} once the land round (x, z) in {@code to} is made: now if it is, else off the tick, with the transition. */
    public static void go(ServerPlayer p, ServerLevel to, double x, double z, Runnable move) {
        go(p, to, x, z, move, () -> {});
    }

    /**
     * {@link #go} with {@code dropped}, run instead of {@code move} if the move is dropped (the player logged out, died
     * or respawned, or a newer move replaced it): for a caller whose bookkeeping must happen either way.
     */
    public static void go(ServerPlayer p, ServerLevel to, double x, double z, Runnable move, Runnable dropped) {
        Wait old = WAITS.remove(p.getUUID());
        if (old != null) old.drop();
        if (ready(to, x, z) || !waitInTests && to.getServer() instanceof GameTestServer) {
            if (old != null) send(p, false);
            move.run();
            return;
        }
        Wait w = new Wait(p, to, x, z, move, dropped);
        WAITS.put(p.getUUID(), w);
        if (old == null) send(p, true);
        MinecraftServer server = to.getServer();
        to.getChunkSource().addTicketAndLoadWithRadius(CoreModule.ARRIVAL_TICKET.get(), chunk(x, z), RADIUS)
                .thenRunAsync(() -> arrive(w, false), server);
    }

    /** {@link #go} for a {@link TeleportTransition}: the player's {@code teleport(t)} once its land is made. */
    public static void go(ServerPlayer p, TeleportTransition t) {
        Vec3 at = t.position();
        go(p, t.newLevel(), at.x, at.z, () -> p.teleport(t));
    }

    /**
     * Starts making the land round (x, z) without moving anyone, for a move the game makes itself later (a respawn at
     * a Hearth: warmed when the player falls, ready by the time they wake). The ticket lets go 15 s after the land is made.
     */
    public static void warm(ServerLevel level, double x, double z) {
        if (!ready(level, x, z)) level.getChunkSource().addTicketWithRadius(CoreModule.ARRIVAL_TICKET.get(), chunk(x, z), RADIUS);
    }

    /**
     * Whether a move to (x, z) in {@code level} would find its land: every chunk within {@link EdgeHold#BODY} of it
     * (the chunks the arrival itself and the reads round the player's box touch) loaded at FULL. Server thread only.
     */
    public static boolean ready(ServerLevel level, double x, double z) {
        int x0 = Mth.floor(x - EdgeHold.BODY) >> 4, x1 = Mth.floor(x + EdgeHold.BODY) >> 4;
        int z0 = Mth.floor(z - EdgeHold.BODY) >> 4, z1 = Mth.floor(z + EdgeHold.BODY) >> 4;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
            }
        }
        return true;
    }

    /** Whether the player is in a passage now, waiting for their land. */
    public static boolean waiting(ServerPlayer p) {
        return WAITS.containsKey(p.getUUID());
    }

    private static ChunkPos chunk(double x, double z) {
        return new ChunkPos(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }

    /** The land came (or the wait ran out): the move, unless it was replaced or the player is gone. */
    private static void arrive(Wait w, boolean late) {
        if (w.done) return;
        w.done = true;
        WAITS.remove(w.player.getUUID(), w);
        ServerPlayer p = w.player;
        if (!present(p)) {
            w.drop();
            if (!p.hasDisconnected()) send(current(p), false);
            return;
        }
        long waited = Util.getMillis() - w.since;
        try {
            w.move.run();
        } catch (RuntimeException e) {
            Awaken.LOG.error("[passage] the move for {} failed", p.getGameProfile().name(), e);
        }
        // a move that starts another passage (a second leg) keeps the dusk up
        if (!waiting(p)) send(current(p), false);
        Awaken.LOG.info("[passage] {} waited {} ms for the land at {} {} in {}{}", p.getGameProfile().name(), waited, Mth.floor(w.x),
                Mth.floor(w.z), w.to.dimension().identifier(), late ? " (not made yet: moved anyway)" : "");
    }

    /** Whether the player the move was for is still here and alive (not logged out, not respawned as a new player). */
    private static boolean present(ServerPlayer p) {
        return !p.isRemoved() && !p.hasDisconnected() && p.isAlive() && current(p) == p;
    }

    private static ServerPlayer current(ServerPlayer p) {
        ServerPlayer now = p.level().getServer().getPlayerList().getPlayer(p.getUUID());
        return now != null ? now : p;
    }

    private static void send(ServerPlayer p, boolean on) {
        if (!p.hasDisconnected()) AwakenNet.send(p, new CorePayloads.PassageWait(on));
    }

    /** Each server tick: drops moves whose player is gone and runs the late ones. */
    public static void tick(MinecraftServer server) {
        if (WAITS.isEmpty()) return;
        long now = Util.getMillis();
        List<Wait> late = null;
        for (Iterator<Wait> it = WAITS.values().iterator(); it.hasNext(); ) {
            Wait w = it.next();
            if (!present(w.player)) {
                it.remove();
                w.drop();
                if (!w.player.hasDisconnected()) send(current(w.player), false);
            } else if (now - w.since > MOST_MS) {
                if (late == null) late = new ArrayList<>();
                late.add(w);
            }
        }
        if (late != null) for (Wait w : late) arrive(w, true);
    }

    /**
     * A command's teleport ({@code /tp}, {@code /spreadplayers}) wins over a move still waiting, which is dropped: else the
     * move, once its land came, would undo it (CI tour 40759045249: a camp's move 16 s late put a tour back on the road).
     */
    public static void commanded(EntityTeleportEvent e) {
        if (WAITS.isEmpty() || e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        Wait w = WAITS.remove(p.getUUID());
        if (w == null) return;
        w.drop();
        send(p, false);
    }

    /** A player in a passage can't act, so nothing hurts them while they wait (a kill command still does). */
    public static void hurt(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !WAITS.isEmpty() && waiting(p)
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    /** {@code core_passage_far} only: whether moves on the GameTest server wait for their land. */
    public static void waitInTests(boolean wait) {
        waitInTests = wait;
    }

    public static void clear() {
        for (Wait w : WAITS.values()) w.done = true;
        WAITS.clear();
    }
}
