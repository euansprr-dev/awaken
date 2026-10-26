package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The Holo band as a thing you wear (SPEC §16.4, "The Registry ceremony"): while it isn't on your wrist the Holo is
 * dark (no K menu, no projected look, no wrist). Players who never went through the ceremony (classic worlds, saves
 * from before it) wear one without a slot to put it in, so for them it is always worn. Side-agnostic: the client reads
 * its own player's synced state. Installed by {@code holo.band}; the defaults say "worn", so nothing goes dark without it.
 */
public final class HoloBand {
    private HoloBand() {}

    public interface Access {
        /** Whether the Holo works for this player (the band on the wrist, or no wrist slot at all). */
        default boolean worn(Player p) { return true; }
        /** Whether this player has the wrist slot (they were handed a band, or the opening took it to give it back). */
        default boolean fitted(Player p) { return false; }
        /** Server: the Holo goes dark until a band is worn (the opening, before the Registry hands one over). */
        default void takeAway(ServerPlayer p) {}
        /** Server: a band on the wrist now (skip, commands), from the player's inventory if one is there. */
        default void wear(ServerPlayer p) {}
        /** Server: a band into the player's hands (the Registry), the selected hotbar slot first. True when given. */
        default boolean give(ServerPlayer p) { return false; }
        /** Server: whether a band is anywhere on the player (worn, in the inventory, being carried in a menu). */
        default boolean has(ServerPlayer p) { return false; }
    }

    /** Told when a player's band goes on or comes off (server). */
    @FunctionalInterface
    public interface Listener {
        void changed(ServerPlayer p, boolean worn);
    }

    private static Access access = new Access() {};
    private static final java.util.List<Listener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void install(Access a) {
        access = a;
    }

    public static void listen(Listener l) {
        LISTENERS.add(l);
    }

    /** For the band module: tells every listener (a broken one is skipped). */
    public static void fire(ServerPlayer p, boolean worn) {
        for (Listener l : LISTENERS) {
            try {
                l.changed(p, worn);
            } catch (RuntimeException ignored) {
                // a listener's failure is its own
            }
        }
    }

    public static boolean worn(Player p) {
        return access.worn(p);
    }

    public static boolean fitted(Player p) {
        return access.fitted(p);
    }

    public static void takeAway(ServerPlayer p) {
        access.takeAway(p);
    }

    public static void wear(ServerPlayer p) {
        access.wear(p);
    }

    public static boolean give(ServerPlayer p) {
        return access.give(p);
    }

    public static boolean has(ServerPlayer p) {
        return access.has(p);
    }

    // ---- client ----------------------------------------------------------------------------------------------------

    /** Client: the band just booted on this player's wrist (after its boot plays out) or went dark. */
    @FunctionalInterface
    public interface ClientListener {
        void booted(boolean on);
    }

    private static final java.util.List<ClientListener> CLIENT = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void listenClient(ClientListener l) {
        CLIENT.add(l);
    }

    public static void fireClient(boolean on) {
        for (ClientListener l : CLIENT) {
            try {
                l.booted(on);
            } catch (RuntimeException ignored) {
                // a listener's failure is its own
            }
        }
    }
}
