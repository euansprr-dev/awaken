package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Where the player chose to go (SPEC §16.8, §27.11): one place picked on the Atlas, kept per player on the server and
 * shown on the Now line with its compass until they get there. The signal module installs the server side and the
 * client's copy; the Atlas asks for a change through {@link #request}. Before it is installed nothing is set.
 */
public final class Destinations {
    private Destinations() {}

    /** Within this many blocks across (and {@link #ARRIVED_UP} up or down) of it, you've arrived and it clears itself. */
    public static final int ARRIVED = 16, ARRIVED_UP = 48;

    /**
     * A destination: its name as the Now line says it ("E-rank gate", "Mossbank"), its dimension, the block, and what
     * kind of thing it is ({@code gate}, {@code mark}, {@code rumour}, {@code region}, {@code pin}, {@code spot}).
     */
    public record Target(String name, Identifier dimension, BlockPos at, String kind) {}

    public interface Server {
        Optional<Target> get(ServerPlayer p);

        void set(ServerPlayer p, @Nullable Target t);
    }

    private static Server server = new Server() {
        @Override public Optional<Target> get(ServerPlayer p) { return Optional.empty(); }
        @Override public void set(ServerPlayer p, @Nullable Target t) {}
    };

    public static void install(Server s) {
        server = s;
    }

    public static Optional<Target> get(ServerPlayer p) {
        return server.get(p);
    }

    /** Sets (or with null clears) the player's destination, saves it and tells their client. */
    public static void set(ServerPlayer p, @Nullable Target t) {
        server.set(p, t);
    }

    public static void clear(ServerPlayer p) {
        server.set(p, null);
    }

    // ---- the client's copy (no client classes here: the signal module's client fills it and sends the requests) -----

    private static volatile @Nullable Target mine;
    private static volatile Consumer<Optional<Target>> sender = t -> {};

    /** Client: the local player's destination as the server last said it. */
    public static Optional<Target> mine() {
        return Optional.ofNullable(mine);
    }

    /** Client: asks the server to make {@code t} the destination (empty clears it). */
    public static void request(Optional<Target> t) {
        sender.accept(t);
    }

    /** Client, the signal module: the server's word on the destination. */
    public static void accept(@Nullable Target t) {
        mine = t;
    }

    /** Client, the signal module: how a request reaches the server. */
    public static void installSender(Consumer<Optional<Target>> s) {
        sender = s;
    }
}
