package dev.awaken.delve.api;

import dev.awaken.delve.LodePlans;
import dev.awaken.delve.Ore;
import dev.awaken.delve.Veins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The lodes (SPEC §12, §28.3) for other modules: where the nearest lode of an ore is, and whether a block is a lode's
 * heart. Answered from the world seed (no scan, no chunk loaded); a lode whose blocks are loaded and plainly gone (an old
 * world's terrain from before lodes, or mined out) is passed over. Overworld only.
 */
public final class Lodes {
    private Lodes() {}

    /**
     * One lode as seen from a point: its id, ore, heart, how many blocks it was laid with, the block of it nearest the
     * point and how far that is (blocks), and whether its heart has been broken.
     */
    public record View(long id, Ore ore, BlockPos heart, int size, BlockPos nearest, double distance, boolean heartBroken) {}

    /** The nearest lode of {@code ore} with a block within {@code radius} of {@code pos}. */
    public static Optional<View> nearest(ServerLevel level, BlockPos pos, Ore ore, int radius) {
        return Veins.nearest(level, pos, ore, radius);
    }

    /** The nearest lode of {@code ore} within 48 blocks (the Tuning Fork's reach). */
    public static Optional<View> nearest(ServerLevel level, BlockPos pos, Ore ore) {
        return nearest(level, pos, ore, 48);
    }

    /** The lode whose heart is at {@code pos}, if one is planned there. */
    public static Optional<View> heart(ServerLevel level, BlockPos pos) {
        return Veins.heartAt(level, pos);
    }

    /**
     * Where each ore is at home underground (SPEC §42.9), for a world with places of its own down there: the caves install
     * it when a world made with the cave regions loads, and lodes favour those places. Asked by worldgen and worker threads.
     */
    public interface Homes {
        /** Whether (x, y, z) is in a place where {@code ore} is at home. Pure; any thread. */
        boolean favours(Ore ore, int x, int y, int z);

        /** The place at (x, y, z) by name ("the Ochre Deep"), or null. Pure; any thread. */
        @Nullable String place(int x, int y, int z);
    }

    /** The overworld's homes for its ores (on its load; null when the server stops). Plans made after follow them. */
    public static void homes(long seed, @Nullable Homes homes) {
        LodePlans.homes(seed, homes);
    }
}
