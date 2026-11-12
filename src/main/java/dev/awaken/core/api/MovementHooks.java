package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Awakened Body's moves (roll, tactical sprint, slide, mantle, and the Runner's wall-run and ledge hang) as other modules see them: powers asks whether the
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
        /** Running along a wall (the Runner's wall-run or Wall Stride). */
        default boolean wallRunning(Player p) { return false; }
        /** Hanging from a ledge (shimmying, or on the way into or between holds). */
        default boolean hanging(Player p) { return false; }
        /** Sliding down a run of stairs or slabs (a slide, so {@link #sliding} is true too; SPEC §17.22). */
        default boolean stairSliding(Player p) { return false; }
        /** A dream remembered these moves for the body (Move keys; SPEC §9.4): THE BODY marks them. Server. */
        default void remember(ServerPlayer p, java.util.List<String> moves) {}
        /** Whether a dream remembered the move (server, and the client for its own player). */
        default boolean remembered(Player p, String move) { return false; }
        /** Mastery points on an Ascent track (RUNNER, TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN), no chain link. Server. */
        default void addMastery(ServerPlayer p, String track, int points) {}
        /**
         * A push on a player the body holds in the air (a rope, wings, flight; SPEC §62): true when the body took it (it
         * joins that state's velocity on the player's client). False: the caller pushes the player the game's way. Server.
         */
        default boolean push(ServerPlayer p, Vec3 push) { return false; }
    }

    /** Client side: the local player's power key sent a cast. */
    public interface ClientAccess {
        /**
         * Called after a movement power's motion was predicted (so the body can let it win and keep a slide's way) and
         * before the cast's effects are predicted. {@code family} is the power family's enum name.
         */
        default void castStarting(Player p, String powerId, String family, boolean motionPredicted) {}

        /**
         * The movement key belongs to the body for this player: the movement slot's power steps aside and never casts
         * from it. Since the rope got its own key (SPEC §17.30) only when a player binds the rope and the movement power
         * to the same key, once the Tether is held.
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

    /** Running along a wall (server side, and the client for its own player). */
    public static boolean wallRunning(Player p) {
        return access.wallRunning(p);
    }

    /** Hanging from a ledge (server side, and the client for its own player). */
    public static boolean hanging(Player p) {
        return access.hanging(p);
    }

    /** Sliding down stairs or slabs (server side, and the client for its own player): first-person animation hooks here. */
    public static boolean stairSliding(Player p) {
        return access.stairSliding(p);
    }

    public static void castStarting(ServerPlayer p) {
        access.castStarting(p);
    }

    /** A dream remembered moves for the body (SPEC §9.4). */
    public static void remember(ServerPlayer p, java.util.List<String> moves) {
        access.remember(p, moves);
    }

    public static boolean remembered(Player p, String move) {
        return access.remembered(p, move);
    }

    /** Mastery on an Ascent track from outside the body (a dream's finish), without a chain link. */
    public static void addMastery(ServerPlayer p, String track, int points) {
        access.addMastery(p, track, points);
    }

    /**
     * Pushes a player (server): added to their motion as the game does, and synced. While the Ascent holds the body (on a
     * rope, on wings, in flight) its own client moves it, so the push is carried into that state's velocity instead of
     * being lost (SPEC §62, "a push carries"). Use this rather than setting a player's motion directly.
     */
    public static void push(ServerPlayer p, Vec3 push) {
        if (access.push(p, push)) return;
        p.setDeltaMovement(p.getDeltaMovement().add(push));
        p.needsSync = true;
    }

    public static void clientCastStarting(Player p, String powerId, String family, boolean motionPredicted) {
        client.castStarting(p, powerId, family, motionPredicted);
    }

    /** Client: whether the body owns the movement key for its own player (see {@link ClientAccess#ownsMovementKey}). */
    public static boolean clientOwnsMovementKey(Player p) {
        return client.ownsMovementKey(p);
    }

    private static final java.util.List<java.util.function.Predicate<Player>> BURDENS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** A module's burden: something a player carries that stops the body's moves (SPEC §28.7: a person on their back). */
    public static void addBurden(java.util.function.Predicate<Player> burden) {
        BURDENS.add(burden);
    }

    /** Whether a player is burdened (both sides): no move starts, a running one ends, no sprinting. */
    public static boolean burdened(Player p) {
        for (int i = 0; i < BURDENS.size(); i++) if (BURDENS.get(i).test(p)) return true;
        return false;
    }
}
