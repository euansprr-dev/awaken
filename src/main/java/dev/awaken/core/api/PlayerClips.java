package dev.awaken.core.api;

/**
 * Full-body player clips for any module's client (Player Animation Library, an optional client mod): a clip from
 * {@code assets/awaken/player_animations} played on a player as everyone sees them in third person, on its own layer
 * above the power casts. The powers module installs the player when the library is loaded (and plays each clip's
 * {@code _bent} twin while Bendable Cuboids is loaded too); otherwise nothing is installed, {@link #play} returns false
 * and the caller keeps its own vanilla pose. Client side only; holds no client classes, so common code may name it.
 */
public final class PlayerClips {
    private PlayerClips() {}

    public interface Player {
        /** Starts a clip on a player, blending in over {@code fadeIn} ticks; false when it couldn't (not a player, no clip). */
        boolean play(int entityId, String clip, int fadeIn);

        /** Ends this layer's clip on a player at once. */
        void stop(int entityId);
    }

    private static volatile Player player;

    public static void install(Player p) {
        player = p;
    }

    /** True when clips can play at all (the library is installed and working). */
    public static boolean available() {
        return player != null;
    }

    public static boolean play(int entityId, String clip, int fadeIn) {
        Player p = player;
        return p != null && p.play(entityId, clip, fadeIn);
    }

    public static void stop(int entityId) {
        Player p = player;
        if (p != null) p.stop(entityId);
    }

    // ---- casts, for the modules whose own clip holds the body --------------------------------------------------------

    /**
     * A cast's pose began on a player (any player this client sees; {@code kind} is the powers' CastAnim name, {@code ticks}
     * its length). A module whose clip holds that body above the cast's (the Ascent's flight) shows the cast in its own
     * way (SPEC §62, "a cast shows"). Client side.
     */
    @FunctionalInterface
    public interface CastListener {
        void cast(int entityId, String kind, int ticks);
    }

    private static final java.util.List<CastListener> CASTS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onCast(CastListener l) {
        CASTS.add(l);
    }

    /** The powers' client: a cast's pose began (with or without the animation library). */
    public static void cast(int entityId, String kind, int ticks) {
        for (CastListener l : CASTS) l.cast(entityId, kind, ticks);
    }
}
