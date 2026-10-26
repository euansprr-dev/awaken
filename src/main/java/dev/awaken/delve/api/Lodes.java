package dev.awaken.delve.api;

import dev.awaken.delve.Ore;
import dev.awaken.delve.Veins;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
}
