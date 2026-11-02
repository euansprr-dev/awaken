package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bells' answer to the rest of the game (SPEC §43.14): whether the Deep has answered a Hundred Peal (gates break
 * sooner and harder, the Gnawed spread, the Sovereigns wake angrier), whether a player has rung their Hundred Peal (the
 * Wake needs it), and where the Kinsong has rung lately (the Gnawed don't take hold there). Installed by the bells
 * module; the defaults are a world before any of it.
 */
public final class BellHooks {
    private BellHooks() {}

    public interface Access {
        default boolean deepAnswered(MinecraftServer server) { return false; }
        default boolean pealed(ServerPlayer player) { return false; }
        default boolean kinsongNear(ServerLevel level, BlockPos at, int reach, int days) { return false; }
        default String belfry(ServerPlayer player) { return ""; }
        default void told(ServerPlayer player) {}
        default boolean quiet(ServerLevel level, BlockPos at) { return false; }
        default boolean relay(ServerPlayer player, BlockPos relay) { return false; }
        default @org.jspecify.annotations.Nullable BlockPos roost(ServerLevel level, BlockPos anchor) { return null; }
        default boolean tower(BlockPos anchor) { return false; }
        default int song(BlockPos anchor) { return 0; }
        default boolean toll(ServerLevel level, BlockPos anchor) { return false; }
        default void raised(ServerLevel level, BlockPos pos) {}
        default void spirit(ServerPlayer player, String spirit, String name, BlockPos at) {}
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    /** Whether the Deep has answered a Hundred Peal anywhere in this world. */
    public static boolean deepAnswered(MinecraftServer server) {
        return access.deepAnswered(server);
    }

    /** Whether this player's home has rung its Hundred Peal (or they were there when one rang). */
    public static boolean pealed(ServerPlayer player) {
        return access.pealed(player);
    }

    /** The Great Belfry's line for this player's Hearth page: what it still needs, or that it stands; "" before A rank. */
    public static String belfry(ServerPlayer player) {
        return access.belfry(player);
    }

    /** Whether the Kinsong rang within {@code reach} blocks of {@code at} in the last {@code days} play-days. */
    public static boolean kinsongNear(ServerLevel level, BlockPos at, int reach, int days) {
        return access.kinsongNear(level, at, reach, days);
    }

    /** Ilse has told this player about the hundred (SPEC §43.15): the Bell Book opens on the Ember's lead. */
    public static void told(ServerPlayer player) {
        access.told(player);
    }

    /** Whether a woken bell or a home's tower can be heard at {@code at}: no new gate opens there (SPEC §43.15). */
    public static boolean quiet(ServerLevel level, BlockPos at) {
        return access.quiet(level, at);
    }

    /**
     * A Signal Relay near this player's Hearth listened (SPEC §27.13). Since §43.16 no bell lies in the open to hear, so
     * it's always false.
     */
    public static boolean relay(ServerPlayer player, BlockPos relay) {
        return access.relay(player, relay);
    }

    /**
     * Whether the home at {@code anchor} has a bell tower (SPEC §43.16 stage 3): a bell hanging 8 or more above its fire, or
     * a belfry, tower or Great Belfry standing round its frame. The Hearth's tier II asks for it, and it warns of breaks
     * and keeps Deep Tide nights calm. False on a client or with no server.
     */
    public static boolean tower(BlockPos anchor) {
        return access.tower(anchor);
    }

    /** How many bells are in the song of the home at {@code anchor} (hung there, and the far six that answer it). */
    public static int song(BlockPos anchor) {
        return access.song(anchor);
    }

    /** The home at {@code anchor}'s highest bell tolls from its hook (a break's warning). False if none hangs. */
    public static boolean toll(ServerLevel level, BlockPos anchor) {
        return access.toll(level, anchor);
    }

    /**
     * The Thatchbacks set a Bell Beam or Great Beam at {@code pos} in a Belfry or Bell Tower Work Plan (SPEC §43.16 stage
     * 3): its bells carry wherever it stands, even if the plan was laid lower than the fire.
     */
    public static void raised(ServerLevel level, BlockPos pos) {
        access.raised(level, pos);
    }

    /**
     * A spirit standing at {@code at} finished telling this player its story (SPEC §43.16 stage 5): it may hold one of the
     * Veil's bells for them, and says what it stayed for.
     */
    public static void spirit(ServerPlayer player, String spirit, String name, BlockPos at) {
        access.spirit(player, spirit, name, at);
    }

    /** Where the Lampwing's old bell hangs near the home at {@code anchor}, while it does (SPEC §43.16), or null. */
    public static @org.jspecify.annotations.Nullable BlockPos roost(ServerLevel level, BlockPos anchor) {
        return access.roost(level, anchor);
    }
}
