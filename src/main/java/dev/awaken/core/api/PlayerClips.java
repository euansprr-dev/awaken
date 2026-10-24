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
}
