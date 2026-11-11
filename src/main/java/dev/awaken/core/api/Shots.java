package dev.awaken.core.api;

/**
 * The game's one cinematic camera, for any module that needs the view to leave the player's eyes for a moment (client;
 * SPEC §16.4, §67.4). The opening owns the camera itself (a client-only body the view follows, the hands and the
 * controls held while a shot runs, one way back whatever interrupts it) and installs it here; a module hands it a
 * {@link Shot}. Until installed, {@link #take} refuses and nothing moves.
 */
public final class Shots {
    private Shots() {}

    /** A camera move: where the eyes are and where they look at time t (ticks since the shot began, with the frame's fraction). */
    public interface Shot {
        /** out: x, y, z (the eyes), yaw, pitch, roll, fov scale. */
        void pose(float t, double[] out);

        /** Ticks the shot lasts; it ends itself then. */
        int length();

        /** Once a tick (age in ticks). */
        default void tick(int age) {}

        /** The shot ended: why ("done", "skip", "taken", "logout", "error", "replaced"). */
        default void finished(String reason) {}
    }

    public interface Director {
        /** Starts the shot; false when it couldn't (no player, no level). A running shot is replaced. */
        boolean take(Shot shot);

        /** Ends the running shot: the one way back to the player's own eyes. */
        void finish(String reason);

        boolean active();
    }

    private static Director director = new Director() {
        @Override public boolean take(Shot shot) { return false; }
        @Override public void finish(String reason) {}
        @Override public boolean active() { return false; }
    };

    public static void install(Director d) {
        director = d;
    }

    public static boolean take(Shot shot) {
        return director.take(shot);
    }

    public static void finish(String reason) {
        director.finish(reason);
    }

    public static boolean active() {
        return director.active();
    }
}
