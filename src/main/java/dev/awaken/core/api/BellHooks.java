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
}
