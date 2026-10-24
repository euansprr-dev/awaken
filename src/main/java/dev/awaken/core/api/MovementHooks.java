package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Awakened Body's moves (roll, tactical sprint, slide, mantle) as other modules see them: powers asks whether the
 * caster is sliding for its family twists and chain refunds, and tells the body when a cast goes out (a cast ends a
 * tactical sprint; a mid-slide cast never ends the slide). Installed by the body module; the defaults know no moves,
 * so powers works the same without it.
 */
public final class MovementHooks {
    private MovementHooks() {}

    /** A Velocity Dash out of a slide carries this much further (the client stretches the predicted motion; SPEC §13). */
    public static final double DASH_OUT_STRETCH = 1.3;

    /** Server side, and the client for its own player. */
    public interface Access {
        default boolean sliding(Player p) { return false; }
        default boolean tacticalSprinting(Player p) { return false; }
        default boolean mantling(Player p) { return false; }
        /** Overworld game time of the last tick a move ran (a landing roll counts once), or {@link Long#MIN_VALUE}. */
        default long lastMoveTick(Player p) { return Long.MIN_VALUE; }
        /** The slide's way (unit, horizontal) while sliding, else null. */
        default @Nullable Vec3 slideDirection(Player p) { return null; }
        /** A power is being cast (server, before its checks): a tactical sprint ends and the cast goes off at once. */
        default void castStarting(ServerPlayer p) {}
    }

    /** Client side: the local player's power key sent a cast. */
    public interface ClientAccess {
        /**
         * Called after a movement power's motion was predicted (so the body can let it win and keep a slide's way) and
         * before the cast's effects are predicted. {@code family} is the power family's enum name.
         */
        default void castStarting(Player p, String powerId, String family, boolean motionPredicted) {}

        /**
         * The movement key belongs to the body for this player (the Ascent's rope, once the Tether is held): the
         * movement slot's power steps aside and never casts from it.
         */
        default boolean ownsMovementKey(Player p) { return false; }
    }

    private static Access access = new Access() {};
    private static ClientAccess client = new ClientAccess() {};

    public static void install(Access a) {
        access = a;
    }

    /** The installed access (tests swap in a stand-in and put this back in try/finally). */
    public static Access installed() {
        return access;
    }

    public static void installClient(ClientAccess c) {
        client = c;
    }

    public static boolean sliding(Player p) {
        return access.sliding(p);
    }

    public static boolean tacticalSprinting(Player p) {
        return access.tacticalSprinting(p);
    }

    public static boolean mantling(Player p) {
        return access.mantling(p);
    }

    public static long lastMoveTick(Player p) {
        return access.lastMoveTick(p);
    }

    public static @Nullable Vec3 slideDirection(Player p) {
        return access.slideDirection(p);
    }

    public static void castStarting(ServerPlayer p) {
        access.castStarting(p);
    }

    public static void clientCastStarting(Player p, String powerId, String family, boolean motionPredicted) {
        client.castStarting(p, powerId, family, motionPredicted);
    }

    /** Client: whether the body owns the movement key for its own player (see {@link ClientAccess#ownsMovementKey}). */
    public static boolean clientOwnsMovementKey(Player p) {
        return client.ownsMovementKey(p);
    }
}
