package dev.awaken.story;

import net.minecraft.server.level.ServerPlayer;

/** Public story API for other modules (SPEC section 12). */
public final class Story {
    private Story() {}

    /**
     * Shows the Revelation {@code data/awaken/awaken/revelation/<key>.json} once for this player and records it in their
     * Codex. Returns false if it was already seen or doesn't exist.
     */
    public static boolean revelation(ServerPlayer player, String key) {
        if (StoryContent.revelation(key) == null || Prologue.progress(player).seen().contains("revelation:" + key)) return false;
        Beats.fire(player, key);
        return true;
    }
}
