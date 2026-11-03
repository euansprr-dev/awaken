package dev.awaken.mobs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    // ---- on the way (SPEC "Fliers find their way") ------------------------------------------------------------------

    /** The most clearance a flier keeps round its hitbox on the way, in blocks (any more and no wood would let it through). */
    static final double PAD_MAX = 1.0;

    /** The clearance a flier keeps round its hitbox on the way: what its drawn wings or coils reach past it. */
    public static double pad(AwakenMob m) {
        return Mth.clamp(reach(m) - m.getBbWidth() * 0.5, 0.15, PAD_MAX);
    }

    /** The hitbox at (x, y, z) with {@code pad} round its sides and over it, half that under it (fliers skim low). */
    static AABB padded(AwakenMob m, double x, double y, double z, double pad) {
        double r = m.getBbWidth() * 0.5 + pad;
        return new AABB(x - r, y - pad * 0.5, z - r, x + r, y + m.getBbHeight() + pad, z + r);
    }

    /** Whether the hitbox with {@code pad} round it is clear of blocks at (x, y, z). */
    public static boolean passes(AwakenMob m, double x, double y, double z, double pad) {
        return m.level().noBlockCollision(m, padded(m, x, y, z, pad));
    }

    /**
     * Where a flier comes out of a blink aimed at {@code at}: there or up to two blocks over it, where its body and half
     * its clearance fit (else its bare body); null when nothing there fits, so it never blinks into the leaves.
     */
    public static @Nullable Vec3 landing(AwakenMob m, Vec3 at) {
        double half = pad(m) * 0.5;
        for (int pass = 0; pass < 2; pass++) {
            for (int lift = 0; lift <= 2; lift++) {
                if (passes(m, at.x, at.y + lift, at.z, pass == 0 ? half : 0.05)) return at.add(0, lift, 0);
            }
        }
        return null;
    }

    /** Whether the hitbox with {@code pad} round it flies straight from where it is to (x, y, z) without touching a block. */
    public static boolean clearWay(AwakenMob m, double x, double y, double z, double pad) {
        return sweep(m, padded(m, m.getX(), m.getY(), m.getZ(), pad), new Vec3(x - m.getX(), y - m.getY(), z - m.getZ())) >= 1.0;
    }

    /**
     * How much (0..1) of {@code delta} the box travels before it touches a block: 1 when the way is clear. A block the
     * box is already in is no obstacle (vanilla's collision lets a body back out of what it is in, never into anything
     * new). One collision query over the swept hull, then a slab test per shape: no stepping, nothing loaded.
     */
    static double sweep(AwakenMob m, AABB box, Vec3 delta) {
        AABB hull = box.expandTowards(delta);
        double hx = box.getXsize() * 0.5 - 1.0E-4, hy = box.getYsize() * 0.5 - 1.0E-4, hz = box.getZsize() * 0.5 - 1.0E-4;
        double cx = (box.minX + box.maxX) * 0.5, cy = (box.minY + box.maxY) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
        double first = 1.0;
        for (VoxelShape shape : m.level().getBlockCollisions(m, hull)) {
            for (AABB o : shape.toAabbs()) {
                // the block grown by the box's half size: the box becomes its centre point
                double x0 = o.minX - hx, y0 = o.minY - hy, z0 = o.minZ - hz, x1 = o.maxX + hx, y1 = o.maxY + hy, z1 = o.maxZ + hz;
                if (cx > x0 && cx < x1 && cy > y0 && cy < y1 && cz > z0 && cz < z1) continue; // already in it
                first = Math.min(first, enter(cx, cy, cz, delta, x0, y0, z0, x1, y1, z1));
            }
        }
        return first;
    }

    /** When (0..1) a point moving by {@code d} enters the box, or 1 if it never does. */
    private static double enter(double px, double py, double pz, Vec3 d, double x0, double y0, double z0, double x1, double y1, double z1) {
        double lo = 0.0, hi = 1.0;
        if (Math.abs(d.x) < 1.0E-9) {
            if (px <= x0 || px >= x1) return 1.0;
        } else {
            double a = (x0 - px) / d.x, b = (x1 - px) / d.x;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        if (Math.abs(d.y) < 1.0E-9) {
            if (py <= y0 || py >= y1) return 1.0;
        } else {
            double a = (y0 - py) / d.y, b = (y1 - py) / d.y;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        if (Math.abs(d.z) < 1.0E-9) {
            if (pz <= z0 || pz >= z1) return 1.0;
        } else {
            double a = (z0 - pz) / d.z, b = (z1 - pz) / d.z;
            lo = Math.max(lo, Math.min(a, b));
            hi = Math.min(hi, Math.max(a, b));
        }
        return lo < hi ? lo : 1.0;
    }
}
