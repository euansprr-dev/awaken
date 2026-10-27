package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * What the rest of the game may ask of Arms about patterns (docs/arms-progression/PLAN.md §4 "Public API"): which styles
 * a fighter knows, teaching one, and Hale's issue. Arms installs it at start; before that, and with Arms absent, nothing
 * is known and nothing is taught. Never reach into the arms package for these.
 */
public final class ArmsApi {
    private ArmsApi() {}

    /** Arms' side of the API. */
    public interface Hooks {
        /** Whether this fighter has the style's pattern (the Longsword is everyone's). */
        boolean knows(ServerPlayer player, String style);

        /** Teaches the style: it is known at once and its Blueprint goes into the pack. False if it was already known. */
        boolean learn(ServerPlayer player, String style, String source);

        /** Whether Hale's issue is waiting for this fighter. */
        boolean issuePending(ServerPlayer player);

        /** Opens the pick of a first weapon on the fighter's screen. */
        void openIssue(ServerPlayer player);

        /** Whether the fighter has taken the Association's issue (Hale shows the rack to those who have). */
        boolean issueTaken(ServerPlayer player);

        /** Opens the Armoury at Hale's rack. */
        void openRack(ServerPlayer player);
    }

    private static Hooks hooks;
    private static BiConsumer<ServerPlayer, String> speech = (p, line) -> {};

    public static void install(Hooks h) {
        hooks = h;
    }

    public static boolean knows(ServerPlayer player, String style) {
        return hooks != null && hooks.knows(player, style);
    }

    public static boolean learn(ServerPlayer player, String style, String source) {
        return hooks != null && hooks.learn(player, style, source);
    }

    public static boolean issuePending(ServerPlayer player) {
        return hooks != null && hooks.issuePending(player);
    }

    public static void openIssue(ServerPlayer player) {
        if (hooks != null) hooks.openIssue(player);
    }

    public static boolean issueTaken(ServerPlayer player) {
        return hooks != null && hooks.issueTaken(player);
    }

    public static void openRack(ServerPlayer player) {
        if (hooks != null) hooks.openRack(player);
    }

    /** The living module says what people say ("Hale: ...") on screen; arms asks it to, without knowing how. */
    public static void speaking(BiConsumer<ServerPlayer, String> s) {
        speech = s;
    }

    public static void say(ServerPlayer player, String line) {
        speech.accept(player, line);
    }
}
