package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * How a player arrived (SPEC §17 "The Wilds"): the server keeps the last 20 seconds of each player's movement, and a
 * site pays its best reward, its route record and its Flow XP only to someone who got there by moving rather than by
 * building. The wilds module samples walking, sprinting, swimming, climbing, riding, flying through the air and standing
 * on blocks the player placed; movement systems report the states they drive ({@link #report}: a power's move today,
 * the Ascent's tiers later). Installed by the wilds module. The default remembers nothing and calls every arrival a
 * movement one, so rewards pay as they always did without it.
 */
public final class ArrivalHooks {
    private ArrivalHooks() {}

    /** One moment of movement; ordinals are never saved, so new ways can go anywhere before {@link #BUILT}. */
    public enum Way {
        STILL, WALK, SPRINT, SWIM, CLIMB, RIDE, AIRBORNE,
        /** A power's move: a dash, a blink, flight. */
        POWER,
        /** A Runner move the Ascent reports: a wall-run, a ledge hang. */
        ASCENT,
        /**
         * The Ascent's own ladder: a rope out, a zip, the carry after a swing or a leap, wings. A climb site's payoff
         * answers only this (SPEC §17.29): never a pearl, a tower or a hang.
         */
        LINE,
        /** Standing on or climbing blocks the player placed: a tower or a bridge. Always outranks the rest. */
        BUILT;

        /** Whether this is a way of moving (not standing still, not building). */
        public boolean moving() {
            return this != STILL && this != BUILT;
        }
    }

    public interface Tracker {
        /** A movement system drives {@code way} now; it holds for the next {@code ticks} unless reported again. */
        default void report(ServerPlayer p, Way way, int ticks) {}

        /** Whether the player reached where they stand by movement: nothing built on the way in (SPEC §17). */
        default boolean byMovement(ServerPlayer p) { return true; }

        /**
         * Whether the player reached where they stand by the line: by movement, and the Ascent's ladder carried them
         * ({@link Way#LINE}) on the way in or since. What a climb site's crown, core and inner seal answer (SPEC §17.29).
         */
        default boolean byLine(ServerPlayer p) { return byMovement(p); }

        /** The last 20 seconds, oldest first, one entry per quarter second (empty without a tracker). */
        default List<Way> history(ServerPlayer p) { return List.of(); }
    }

    private static Tracker tracker = new Tracker() {};

    public static void install(Tracker t) {
        tracker = t;
    }

    /** The installed tracker (tests swap in a stand-in and put this back in try/finally). */
    public static Tracker installed() {
        return tracker;
    }

    public static void report(ServerPlayer p, Way way, int ticks) {
        tracker.report(p, way, ticks);
    }

    public static boolean byMovement(ServerPlayer p) {
        return tracker.byMovement(p);
    }

    public static boolean byLine(ServerPlayer p) {
        return tracker.byLine(p);
    }

    public static List<Way> history(ServerPlayer p) {
        return tracker.history(p);
    }
}
