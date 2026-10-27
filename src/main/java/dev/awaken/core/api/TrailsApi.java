package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Trails (docs/trails/PLAN.md): short optional lists of things to try, opened by a moment, followed by choice. A module
 * adds its own words to them here: a family of condition tokens ("room:sanctuary" belongs to the Hearth) and the kinds of
 * place a step can point at. The trails module reads them; until it is installed the hooks do nothing.
 */
public final class TrailsApi {
    private TrailsApi() {}

    private static final Map<String, BiPredicate<ServerPlayer, String>> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String, Function<ServerPlayer, @Nullable BlockPos>> PLACES = new ConcurrentHashMap<>();

    /** Adds a family of tokens: "family:rest" holds when {@code test(player, rest)} does. */
    public static void condition(String family, BiPredicate<ServerPlayer, String> test) {
        CONDITIONS.put(family, test);
    }

    public static @Nullable BiPredicate<ServerPlayer, String> conditionFor(String family) {
        return CONDITIONS.get(family);
    }

    /** Adds a kind of place a step's {@code where} can name ("hearth"); null from the function: no place now. */
    public static void place(String kind, Function<ServerPlayer, @Nullable BlockPos> where) {
        PLACES.put(kind, where);
    }

    public static boolean hasPlace(String kind) {
        return PLACES.containsKey(kind);
    }

    public static @Nullable BlockPos placeOf(String kind, ServerPlayer player) {
        var f = PLACES.get(kind);
        return f == null ? null : f.apply(player);
    }

    public interface Hooks {
        /** Opens a trail now, whatever its tokens say (a moment that can't be written as one). */
        void unlock(ServerPlayer player, String id);

        /** One line for someone who is asked "Anything for me?": the best next step of the player's trails, or null. */
        @Nullable String suggest(ServerPlayer player);

        /** Stops following (something else took the task card). */
        void unfollow(ServerPlayer player);

        /** Something changed that may tick a step: look again soon. */
        void poke(ServerPlayer player);
    }

    private static Hooks hooks;

    public static void install(Hooks h) {
        hooks = h;
    }

    public static void unlock(ServerPlayer player, String id) {
        if (hooks != null) hooks.unlock(player, id);
    }

    public static @Nullable String suggest(ServerPlayer player) {
        return hooks == null ? null : hooks.suggest(player);
    }

    public static void unfollow(ServerPlayer player) {
        if (hooks != null) hooks.unfollow(player);
    }

    public static void poke(ServerPlayer player) {
        if (hooks != null) hooks.poke(player);
    }
}
