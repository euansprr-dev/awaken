package dev.awaken.veil;

import dev.awaken.core.api.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spirit sight never reveals another player's base: nothing within 48 blocks of someone else's Hearth or bed is ever
 * sent. Checked on the server, so the client cannot be talked into an x-ray.
 */
public final class RevealFilter {
    public static final int RADIUS = 48;
    private static final long R2 = (long) RADIUS * RADIUS;

    public static final RevealFilter OPEN = new RevealFilter(List.of());

    private final List<BlockPos> zones;

    private RevealFilter(List<BlockPos> zones) {
        this.zones = zones;
    }

    /** A filter over explicit foreign base positions (tests, or callers that already know them). */
    public static RevealFilter of(List<BlockPos> foreignBases) {
        return foreignBases.isEmpty() ? OPEN : new RevealFilter(List.copyOf(foreignBases));
    }

    public List<BlockPos> zones() {
        return zones;
    }

    public boolean allowed(BlockPos pos) {
        return allowed(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean allowed(long packed) {
        return allowed(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
    }

    public boolean allowed(double x, double y, double z) {
        for (BlockPos b : zones) {
            double dx = x - b.getX(), dy = y - b.getY(), dz = z - b.getZ();
            if (dx * dx + dy * dy + dz * dz <= R2) return false;
        }
        return true;
    }

    /**
     * Every foreign base that could matter around {@code center}: Hearths found by probing {@link HearthHooks#nearest}
     * over the sight area, and the beds and Hearths of the other players online.
     */
    public static RevealFilter build(ServerLevel level, ServerPlayer viewer, Vec3 center, int radius) {
        List<BlockPos> zones = new ArrayList<>();
        Optional<BlockPos> own = level.dimension() == Level.OVERWORLD ? HearthHooks.hearthOf(viewer) : Optional.empty();
        int reach = radius + RADIUS;
        int half = Math.max(8, radius / 2);
        // nearest() returns one Hearth, so probe across the disc: your own Hearth must never hide someone else's
        int[][] probes = {{0, 0}, {half, 0}, {-half, 0}, {0, half}, {0, -half}, {half, half}, {-half, half}, {half, -half}, {-half, -half}};
        for (int[] p : probes) {
            BlockPos probe = BlockPos.containing(center.x + p[0], center.y, center.z + p[1]);
            HearthHooks.nearest(level, probe, reach).ifPresent(h -> {
                if (own.map(o -> !o.equals(h)).orElse(true) && !zones.contains(h)) zones.add(h.immutable());
            });
        }
        double reach2 = (double) reach * reach;
        for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
            if (other == viewer) continue;
            var cfg = other.getRespawnConfig();
            if (cfg != null && cfg.respawnData().dimension() == level.dimension()) {
                BlockPos bed = cfg.respawnData().pos();
                if (bed.distToCenterSqr(center) <= reach2 && !zones.contains(bed)) zones.add(bed.immutable());
            }
            if (level.dimension() == Level.OVERWORLD) {
                HearthHooks.hearthOf(other).ifPresent(h -> {
                    if (h.distToCenterSqr(center) <= reach2 && !zones.contains(h) && own.map(o -> !o.equals(h)).orElse(true)) zones.add(h.immutable());
                });
            }
        }
        return of(zones);
    }
}
