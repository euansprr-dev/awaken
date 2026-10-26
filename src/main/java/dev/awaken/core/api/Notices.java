package dev.awaken.core.api;

import net.minecraft.network.chat.Component;

/**
 * How AWAKEN tells you things (SPEC §26): every message, caption and discovery cue on screen goes through one director
 * (the Signal's {@code Herald}, client side), which paces them by {@link Tier}. Server code tags a Signal message with
 * its tier; client code that draws its own HUD text (a place caption, a tip) asks {@link Client#claim} first and files
 * what it can't show with {@link Client#file}. Installed by the signal module; until then claims always pass and filing
 * does nothing, so callers never need to check.
 */
public final class Notices {
    private Notices() {}

    /**
     * What a message is worth to the player now. CRITICAL bypasses the pace (a decision, danger to life, a gate
     * panel); IMPORTANT is paced (progress you earned, a find's first time, something worth walking to); AMBIENT never
     * interrupts at the default density: it is filed in the Journal with a soft cue.
     */
    public enum Tier {
        CRITICAL, IMPORTANT, AMBIENT;

        public static Tier byId(int id) {
            Tier[] all = values();
            return id >= 0 && id < all.length ? all[id] : IMPORTANT;
        }
    }

    /** Client side (call only from client code). */
    public interface Client {
        /**
         * Whether something of this tier may take the screen right now, under {@code topic} (a merge key: "region",
         * "tip", ...). True counts as shown, for the pace. False: try again later, or {@link #file} it.
         */
        default boolean claim(Tier tier, String topic) { return true; }

        /** Files a line in the Journal (the quiet home of ambient finds) with the soft cue. */
        default void file(Tier tier, Component title, Component line) {}

        /** True while the director holds non-critical messages (a fight, a screen, a cinematic, the first minutes). */
        default boolean quiet() { return false; }

        /** Holds every paced message while any owner holds (a scene, a scripted camera); release with false. */
        default void hold(Object owner, boolean held) {}
    }

    private static Client client = new Client() {};

    public static void installClient(Client c) {
        client = c;
    }

    public static Client client() {
        return client;
    }
}
