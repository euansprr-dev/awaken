package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Carillon's opening (SPEC §16.4: the sky shot, the run, the arrival, the infirmary and the Registry) for other modules:
 * the HUD and the Director stay quiet while a player is in it. Installed by the opening module; the defaults mean no
 * opening is running.
 */
public final class OpeningHooks {
    private OpeningHooks() {}

    public interface Access {
        default boolean inOpening(Player p) { return false; }
        /** The current beat's name (e.g. "run"), or null when not in the opening. */
        default @Nullable String stage(Player p) { return null; }
        default void skip(ServerPlayer p) {}
        /** A named place of the opening in this world ("roost", "registry"), or null (no Carillon here). */
        default @Nullable BlockPos place(String name) { return null; }
    }

    private static final List<Consumer<ServerPlayer>> REGISTERED = new CopyOnWriteArrayList<>();

    /** Called once when a player's opening ends or is skipped: the Association has registered them (SPEC §27.6). */
    public static void onRegistered(Consumer<ServerPlayer> listener) {
        REGISTERED.add(listener);
    }

    /** The opening module calls this as a player's opening ends. */
    public static void fireRegistered(ServerPlayer p) {
        for (var l : REGISTERED) l.accept(p);
    }

    public static @Nullable BlockPos place(String name) {
        return access.place(name);
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static boolean inOpening(Player p) {
        return access.inOpening(p);
    }

    public static @Nullable String stage(Player p) {
        return access.stage(p);
    }

    public static void skip(ServerPlayer p) {
        access.skip(p);
    }
}
