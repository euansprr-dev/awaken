package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Carillon, the walled city (SPEC §16), as other modules see it: whether a spot is inside the city, which district,
 * whether its blocks are civic (protected), where a new player starts and where Choir Square is. Installed by the city
 * module; the defaults know no city, so every caller behaves as in a classic world without it.
 */
public final class CityHooks {
    private CityHooks() {}

    public interface Access {
        default boolean inCity(Level level, BlockPos pos) { return false; }
        /** The district id ("bellwatch", "quay", ...) at a spot inside the city, else null. */
        default @Nullable String district(Level level, BlockPos pos) { return null; }
        /** Civic blocks are protected from players (SPEC §16.0.6); a player's own plots are not. */
        default boolean isCivic(Level level, BlockPos pos) { return false; }
        /** Where this player's opening starts (the Runners' Roost), or null outside a Carillon world. */
        default @Nullable BlockPos startPoint(ServerPlayer p) { return null; }
        /** The centre of Choir Square, or null outside a Carillon world. */
        default @Nullable BlockPos square(Level level) { return null; }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    /** The installed access (tests swap in a stand-in and put this back in try/finally). */
    public static Access installed() {
        return access;
    }

    public static boolean inCity(Level level, BlockPos pos) {
        return access.inCity(level, pos);
    }

    public static @Nullable String district(Level level, BlockPos pos) {
        return access.district(level, pos);
    }

    public static boolean isCivic(Level level, BlockPos pos) {
        return access.isCivic(level, pos);
    }

    public static @Nullable BlockPos startPoint(ServerPlayer p) {
        return access.startPoint(p);
    }

    public static @Nullable BlockPos square(Level level) {
        return access.square(level);
    }
}
