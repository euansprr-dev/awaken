package dev.awaken.citizens.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The people engine's settlements (SPEC §28.4, §12): one engine, many places. Carillon is settlement 0 (started by the
 * citizens module when the world has the city); W3 registers its villages here. Every settlement shares one world pool
 * of bodies (96 a world, 40 a player, nearest first), so a village never starves the city. Overworld only; server
 * thread only. Installed by {@code citizens}; before that (or on a client) every call answers "none".
 */
public final class Settlements {
    private Settlements() {}

    public static final int CARILLON = 0;
    public static final String CARILLON_KEY = "carillon";

    public interface Access {
        Optional<Settlement> register(ServerLevel level, SettlementSpec spec);

        boolean unregister(int id);

        Optional<Settlement> at(Level level, BlockPos pos);

        Optional<Settlement> byId(int id);

        List<Settlement> all();

        int bodies(int id);
    }

    private static @Nullable Access access;

    public static void install(Access a) {
        access = a;
    }

    /**
     * Starts a settlement (or finds the running one with this key): its roster is built now, its saved state found by
     * its key (a new one gets the next id). Empty when the level isn't the overworld or the source yields no roster.
     */
    public static Optional<Settlement> register(ServerLevel level, SettlementSpec spec) {
        return access == null ? Optional.empty() : access.register(level, spec);
    }

    /** Stops one (its bodies go; its saved state is kept for the next registration under its key). Never Carillon. */
    public static boolean unregister(int id) {
        return access != null && id != CARILLON && access.unregister(id);
    }

    /** The settlement a position belongs to (the smallest that contains it), if any. */
    public static Optional<Settlement> at(Level level, BlockPos pos) {
        return access == null ? Optional.empty() : access.at(level, pos);
    }

    public static Optional<Settlement> byId(int id) {
        return access == null ? Optional.empty() : access.byId(id);
    }

    public static List<Settlement> all() {
        return access == null ? List.of() : access.all();
    }

    /** How many of a settlement's people have a body now. */
    public static int bodies(int id) {
        return access == null ? 0 : access.bodies(id);
    }
}
