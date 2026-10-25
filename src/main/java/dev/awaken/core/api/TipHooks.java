package dev.awaken.core.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Just-in-time tips (SPEC §16.0 decision 24, docs/carillon/m4/onboarding.md): the first time a context comes up, a short
 * tip names the player's real keys; it retires once the player has done it a few times. Any module offers its own tips
 * (Carillon's run, the Ascent's tiers, the first stretch outside); tip text and triggers are data. Installed by the
 * signal module; until then every call does nothing, so callers can wire their tips early.
 */
public final class TipHooks {
    private TipHooks() {}

    /** Server side: a context came up for a player, or they did what a tip teaches. */
    public interface Server {
        /** A context came up (one small payload; the client decides whether the tip shows). */
        default void offer(ServerPlayer player, Identifier tip) {}
        /** The player did what the tip teaches (counts toward retiring it; fades it if showing). */
        default void done(ServerPlayer player, Identifier tip) {}
    }

    /** Client side (call only from client code). */
    public interface Client {
        /** The context holds now. Idempotent: call it every tick the context holds. */
        default void offer(Identifier tip) {}
        /** The player did it. */
        default void done(Identifier tip) {}
        /** Holds every tip back while any owner holds (cinematics, scripted cameras); release with false. */
        default void hold(Object owner, boolean held) {}
        /** Whether this tip can still teach this player (tips on, not retired). Callers skip their probe when false. */
        default boolean wants(Identifier tip) { return false; }
    }

    private static Server server = new Server() {};
    private static Client client = new Client() {};

    public static void install(Server s) {
        server = s;
    }

    public static void installClient(Client c) {
        client = c;
    }

    public static void offer(ServerPlayer player, Identifier tip) {
        server.offer(player, tip);
    }

    public static void done(ServerPlayer player, Identifier tip) {
        server.done(player, tip);
    }

    public static Client client() {
        return client;
    }
}
