package dev.awaken.mobs.entity;

import dev.awaken.mobs.def.FaunaRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Where the world module's fauna layer plugs into the overworld creatures (the mobs module never depends on the world
 * module). The defaults keep a creature working on its own: content level 0, no census, nothing remembered.
 */
public final class FaunaHooks {
    private FaunaHooks() {}

    /** Server side. Every call is cheap and made from the server thread. */
    public interface Server {
        /** A creature joined a server level (spawned, loaded with its chunk, or changed dimension). */
        default void added(FaunaMob mob) {}

        /** A creature left a server level (died, despawned, unloaded with its chunk). */
        default void removed(FaunaMob mob) {}

        /** The content level a creature spawning here should have (from the nearest player). */
        default int contentLevel(ServerLevel level, BlockPos pos, FaunaRole role) {
            return 0;
        }

        /** Whether a creature spawning here is an elite (the Living Wilds, SPEC §21: 5% of neutrals and hunters). */
        default boolean elite(ServerLevel level, BlockPos pos, FaunaRole role, net.minecraft.util.RandomSource random) {
            return false;
        }

        /** A named beast reports where it is (every two seconds while loaded). */
        default void beastSeen(NamedBeast beast) {}

        /** A named beast is gone for good (killed, or discarded by a command). */
        default void beastGone(NamedBeast beast) {}

        /** A named beast was felled; {@code killer} is the player who gets the credit, if any. */
        default void beastFelled(ServerLevel level, NamedBeast beast, @Nullable ServerPlayer killer) {}

        /** A named beast turned on a player (warned them off first). */
        default void beastAngered(NamedBeast beast, ServerPlayer player) {}
    }

    private static Server server = new Server() {};

    public static void install(Server s) {
        server = s;
    }

    public static Server server() {
        return server;
    }

    /**
     * Client side: whether the local player has awakened. Before that, ambient creatures are only a faint shimmer
     * at the edge of sight (plan v2 decision 2a). Installed by the world module's client; true when absent.
     */
    private static BooleanSupplier viewerAwakened = () -> true;

    public static void installViewer(BooleanSupplier awakened) {
        viewerAwakened = awakened;
    }

    public static boolean viewerAwakened() {
        try {
            return viewerAwakened.getAsBoolean();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
