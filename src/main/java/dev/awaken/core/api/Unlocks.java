package dev.awaken.core.api;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Unlock title (SPEC §71.2; Euan, 2026-11-12: "the big title, and it says you just unlocked double jump. Press space
 * in the air again to use it ... And maybe every time you unlock a major thing in the game, it actually shows you that
 * as well"). One kit for every major unlock: a new way to move, a new power, a new rank, a new layer of the world.
 * A module registers what can be unlocked (on both sides, in its common init) and shows it by id; the System draws it
 * as the discovery title's bigger sibling, alone on the screen, never in a fight, never paced and never filed.
 *
 * <p>Installed by the signal module; until then every call does nothing, so callers can wire their unlocks early.
 */
public final class Unlocks {
    private Unlocks() {}

    /** The label over a name. */
    public static final String UNLOCKED = "Unlocked";
    /** A new way to move: the Lesson's gold. A new power, rank or layer passes its own colour. */
    public static final int GOLD = 0xF0C766;

    /**
     * One thing that can be unlocked. {@code name}: its plain name, at most 22 letters to stay at full size on a 427 wide
     * screen ("Double Jump"). {@code how}: one short line of how to use it; "{key.jump}" (a key mapping's name in
     * braces) is drawn as the player's own key cap, and "{hold}" before a key as the Hold cap ("Press {key.jump} again in
     * the air", "{hold}{key.awaken.rope} to swing"). Empty: no line. {@code colour}: RGB.
     */
    public record Unlock(Identifier id, String label, String name, String how, int colour) {
        public static Unlock of(Identifier id, String name, String how) {
            return new Unlock(id, UNLOCKED, name, how, GOLD);
        }
    }

    private static final Map<Identifier, Unlock> DEFS = new ConcurrentHashMap<>();

    /** Registers an unlock (common init: both sides must know it). The same id again replaces it. */
    public static Unlock register(Unlock u) {
        DEFS.put(u.id(), u);
        return u;
    }

    public static @Nullable Unlock get(Identifier id) {
        return DEFS.get(id);
    }

    /** Server side. */
    public interface Server {
        /** This player has just unlocked it: its title plays as soon as they are free (out of a fight, no screen). */
        default void show(ServerPlayer player, Identifier unlock) {}
    }

    /** Client side (call only from client code). */
    public interface Client {
        /** Shows it as soon as the player is free; several queue and play one after the other. */
        default void show(Unlock unlock) {}
        /** True while a title is on screen: other HUD that would share the screen (a run's clock, a card) steps aside. */
        default boolean showing() { return false; }
        /** True while a title is on screen or waiting for its moment: what follows an unlock (its lesson) waits. */
        default boolean pending() { return false; }
    }

    private static Server server = new Server() {};
    private static Client client = new Client() {};

    public static void install(Server s) {
        server = s;
    }

    public static void installClient(Client c) {
        client = c;
    }

    public static void show(ServerPlayer player, Identifier unlock) {
        server.show(player, unlock);
    }

    public static Client client() {
        return client;
    }
}
