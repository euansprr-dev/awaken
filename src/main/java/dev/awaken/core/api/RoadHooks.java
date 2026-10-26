package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The world's roads for other modules (SPEC §28.9): a stretch of a real road near a place, walked from one settlement
 * toward the next (the World Pulse's Caravan walks one), and word that a caravan came in. Installed by the village
 * module; the defaults know no roads, so a caravan walks open ground as it did before.
 */
public final class RoadHooks {
    private RoadHooks() {}

    /**
     * A stretch of road: its points in walking order on the road's surface, where it comes from and where it's bound
     * (names and settlement keys, "" where an end is no settlement), and whether its last point is that settlement's edge.
     */
    public record Route(List<Vec3> points, String from, String to, String fromKey, String toKey, boolean arrives) {}

    public interface Access {
        /** A stretch of about {@code length} blocks of a road laid near {@code pos}, its middle {@code min}..{@code max} away. */
        default Optional<Route> stretch(ServerLevel level, BlockPos pos, int min, int max, int length, long seed) {
            return Optional.empty();
        }

        /** A caravan reached a settlement along its road: its trade and its news come in with it. */
        default void arrived(ServerLevel level, String settlementKey, String from, boolean defended) {}
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static Optional<Route> stretch(ServerLevel level, BlockPos pos, int min, int max, int length, long seed) {
        return access.stretch(level, pos, min, max, length, seed);
    }

    public static void arrived(ServerLevel level, String settlementKey, String from, boolean defended) {
        access.arrived(level, settlementKey, from, defended);
    }
}
