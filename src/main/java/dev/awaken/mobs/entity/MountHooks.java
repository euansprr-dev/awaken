package dev.awaken.mobs.entity;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Where the world module's mount layer (taming, the whistle, the Hearth, the level road) plugs into the rideable
 * creatures, so the mobs module never depends on the world module. The defaults let a mount work on its own: anyone
 * may tame, nothing is remembered.
 */
public final class MountHooks {
    private MountHooks() {}

    /** Server side, server thread, cheap. */
    public interface Server {
        /** Why this player may not win a creature's trust yet ("level 11"), or null when they may. */
        default @Nullable String tameRefusal(ServerPlayer player, WildMount mount) {
            return null;
        }

        /** A feeding counted: {@code trust} of {@code need}. */
        default void trust(WildMount mount, ServerPlayer player, int trust, int need) {}

        /** The creature trusts this player now (it follows them). */
        default void tamed(WildMount mount, ServerPlayer player) {}

        /** The bridle went on: it can be ridden, and the whistle calls it. */
        default void bridled(WildMount mount, ServerPlayer player) {}

        /** Its owner climbed on. */
        default void mounted(WildMount mount, ServerPlayer player) {}

        /** An owned mount reports where it is (every two seconds while loaded, ridden or not). */
        default void seen(WildMount mount) {}

        /** An owned mount is gone: {@code died} true when it was killed, false when it was discarded. */
        default void gone(WildMount mount, boolean died) {}
    }

    private static Server server = new Server() {};

    public static void install(Server s) {
        server = s;
    }

    public static Server server() {
        return server;
    }
}
