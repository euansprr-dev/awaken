package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a flier may hold (SPEC "Fliers hold in open air"): a spot whose room, the body plus the reach of its wings or
 * coils, is free of blocks, and that the body can reach in a straight line (fliers steer straight, they don't
 * pathfind). Euan, 2026-10-28: the Empress Moth sat still inside a birch canopy; its idle spot was the heightmap top,
 * which counts leaves, and a straight flight into leaves never arrives, so it never chose again.
 */
public final class FlightRoom {
    private FlightRoom() {}

    /** Blocks of clear air a flier keeps over its head. */
    static final double HEADROOM = 0.75;
    /** How far above the canopy a big flier holds when its own clearing has no room. */
    static final int OVER_CANOPY = 3;
    /** How far up a spot is searched for room before giving up on it. */
    static final int SCAN_UP = 28;

    /** Horizontal reach of the drawn body from its centre, in blocks: wings and coils are far wider than the hitbox. */
    public static double reach(AwakenMob m) {
        double drawn = switch (m.def().model) {
            case EMPRESS_MOTH -> 2.2;           // the wings, 1.9 blocks out from the body, spread wider in the beat
            case PLUMED_COIL, DUNE_WYRM -> 2.6; // the coils trail and swing behind the head
            default -> 0.0;
        };
        return Math.max(m.getBbWidth() * 0.5 + 0.25, drawn * m.getScale());
    }

    /** A big flier keeps to open air: above the canopy or in a clearing, never threading between trunks. */
    public static boolean big(AwakenMob m) {
        return reach(m) >= 1.5;
    }

    /** The room the mob needs with its body's bottom centre at (x, y, z). */
    public static AABB room(AwakenMob m, double x, double y, double z) {
        double r = reach(m);
        return new AABB(x - r, y, z - r, x + r, y + m.getBbHeight() + HEADROOM, z + r);
    }

    /** Whether the mob could hold with its body's bottom centre at (x, y, z) without touching a block. */
    public static boolean fits(AwakenMob m, double x, double y, double z) {
        return m.level().noBlockCollision(m, room(m, x, y, z));
    }

    /** Whether the body flies straight from where it is to (x, y, z) without striking a block (a block at a time). */
    public static boolean clearLine(AwakenMob m, double x, double y, double z) {
        double dx = x - m.getX(), dy = y - m.getY(), dz = z - m.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, Mth.ceil(len));
        AABB body = m.getBoundingBox().inflate(0.2);
        Level level = m.level();
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            if (!level.noBlockCollision(m, body.move(dx * t, dy * t, dz * t))) return false;
        }
        return true;
    }

    /**
     * The nearest height at or above {@code from} over column (x, z) where the mob fits, searching up to the canopy and a
     * little over it; null if there is none within {@link #SCAN_UP} blocks.
     */
    public static @Nullable Vec3 roomAbove(AwakenMob m, double x, double from, double z) {
        Level level = m.level();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        double y = Math.max(from, level.getMinY() + 1);
        double last = Math.min(Math.max(y, top + OVER_CANOPY) + 6, y + SCAN_UP);
        for (; y <= last; y += 1.0) {
            if (fits(m, x, y, z)) return new Vec3(x, y, z);
        }
        return null;
    }

    /**
     * An idle spot near (x, z) for a mob that wants to hold at about {@code wantY}: there if it fits, else the nearest room
     * above it (for a big flier, over the canopy). Null when nowhere close fits.
     */
    public static @Nullable Vec3 spot(AwakenMob m, double x, double wantY, double z) {
        if (fits(m, x, wantY, z)) return new Vec3(x, wantY, z);
        if (!big(m)) {
            // small fliers keep their height: a step up or down, never a climb out of the wood
            for (int dy = 1; dy <= 3; dy++) {
                if (fits(m, x, wantY + dy, z)) return new Vec3(x, wantY + dy, z);
                if (fits(m, x, wantY - dy, z)) return new Vec3(x, wantY - dy, z);
            }
            return null;
        }
        return roomAbove(m, x, wantY, z);
    }

    /** A perch near (x, z): the top of something solid with room over it (a branch, a stone, the lair's crown), or null. */
    public static @Nullable Vec3 perch(AwakenMob m, double x, double z) {
        Level level = m.level();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) - 1, Mth.floor(z));
        if (!level.getBlockState(p).isFaceSturdy(level, p, Direction.UP)) return null;
        double y = p.getY() + 1.0;
        return fits(m, x, y, z) ? new Vec3(x, y, z) : null;
    }

    /** Whether the mob, where it is now, has its room (its wings or coils clear of every block). */
    public static boolean free(AwakenMob m) {
        return fits(m, m.getX(), m.getY(), m.getZ());
    }
}
