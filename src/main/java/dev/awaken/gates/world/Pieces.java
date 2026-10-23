package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Geometry and painting of the set pieces and the Lords' arenas ({@link SetPiece}). Everything is a pure function of
 * the room and a column, evaluated per column (no lists, no allocation), so it runs on worldgen threads and is shared
 * with spawning ({@link RoomGeometry#blocked}) and pathing. Rules every piece keeps:
 * <ul>
 *   <li>the axes from each door to the room's centre stay walkable and level (5 wide);</li>
 *   <li>nothing stands within two blocks of a feature (cache, Lord's spot, exit portal);</li>
 *   <li>a Lord's arena keeps a clear disc round its spot and the way from the door to it.</li>
 * </ul>
 * Room pieces live in {@link PiecesRooms}, arenas in {@link PiecesArenas}; this class dispatches and holds the shared
 * shapes (bells, gears, caps).
 */
public final class Pieces {
    private Pieces() {}

    /** floorDelta: no change, the room's shape decides. */
    public static final int KEEP = Integer.MIN_VALUE;

    // ---- shared frame -----------------------------------------------------------------------------------------------

    static double cx(Room r) {
        return (r.x0() + r.x1() + 1) / 2.0;
    }

    static double cz(Room r) {
        return (r.z0() + r.z1() + 1) / 2.0;
    }

    static double dx(Room r, int x) {
        return x + 0.5 - cx(r);
    }

    static double dz(Room r, int z) {
        return z + 0.5 - cz(r);
    }

    /** Half the interior's width and depth. */
    static double hw(Room r) {
        return (r.x1() - r.x0() + 1) / 2.0;
    }

    static double hd(Room r) {
        return (r.z1() - r.z0() + 1) / 2.0;
    }

    static double radius(Room r) {
        return Math.min(hw(r), hd(r));
    }

    /** Kept clear in every piece: door axes (5 wide) up to its core, a walkway round the core, a margin round features. */
    static boolean clear(Room r, int x, int z) {
        double core = r.piece == null ? 0 : r.piece.core();
        double dd = Math.hypot(dx(r, x), dz(r, z));
        if (core > 0) {
            if (dd < core) return false;
            if (dd < core + 2.6) return true;
        }
        if (RoomGeometry.onDoorAxis(r, x, z, 2)) return true;
        for (Room.Feature f : r.features) {
            if (f.kind() == Room.FeatureKind.UPDRAFT) continue;
            if (Math.abs(f.x() - x) <= 2 && Math.abs(f.z() - z) <= 2) return true;
        }
        if (r.piece != null && r.piece.arena) {
            // the Lord's spot and the exit portal row (centre, centre + 3)
            double dx = dx(r, x), dz = dz(r, z);
            if (dx * dx + dz * dz <= 16) return true;
            if (Math.abs(x - r.centerX()) <= 2 && z >= r.centerZ() + 1 && z <= r.centerZ() + 5) return true;
        }
        return false;
    }

    // ---- dispatch ---------------------------------------------------------------------------------------------------

    /** An obstacle stands on this column (cover, stacks, statues): not walkable, no spawns. */
    public static boolean blocks(Room r, int x, int z) {
        if (r.piece == null || clear(r, x, z)) return false;
        return r.piece.arena ? PiecesArenas.blocks(r, x, z) : PiecesRooms.blocks(r, x, z);
    }

    /** Walking surface relative to the room's floor, or {@link #KEEP}. */
    public static int floorDelta(Room r, int x, int z) {
        if (r.piece == null || clear(r, x, z)) return r.piece != null && r.piece.arena ? 0 : KEEP;
        return r.piece.arena ? PiecesArenas.floorDelta(r, x, z) : PiecesRooms.floorDelta(r, x, z);
    }

    /** Raises (or lowers) the ceiling over this column. */
    public static int ceilingDelta(Room r, int x, int z) {
        if (r.piece == null) return 0;
        return r.piece.arena ? PiecesArenas.ceilingDelta(r, x, z) : PiecesRooms.ceilingDelta(r, x, z);
    }

    /** Floating Isles: island ground under this column. */
    public static boolean isle(Room r, int x, int z) {
        return PiecesRooms.isle(r, x, z);
    }

    /** The piece dresses this wall face itself (no recessed panel here). */
    public static boolean wallClaim(Room r, int x, int z, int d) {
        if (r.piece == null) return false;
        return r.piece.arena ? PiecesArenas.wallClaim(r, x, z, d) : PiecesRooms.wallClaim(r, x, z, d);
    }

    /** Paints a wall face the piece claimed. True when it did. */
    public static boolean paintWall(GatePainter.Ctx c, int bx, int bz, int x, int z, int od) {
        if (c.r.piece == null) return false;
        return c.r.piece.arena ? PiecesArenas.paintWall(c, bx, bz, x, z, od) : PiecesRooms.paintWall(c, bx, bz, x, z, od);
    }

    /** Something standing in a wall niche (row rel above the floor), or null for the kit's own. */
    public static BlockState niche(GatePainter.Ctx c, int x, int z, int rel, int d, boolean big) {
        if (c.r.piece == null) return null;
        return c.r.piece.arena ? PiecesArenas.niche(c, x, z, rel, d, big) : null;
    }

    /**
     * Paints the piece's blocks in an interior column after its floor and ceiling (walk = the absolute Y you stand at,
     * ceil = the ceiling block's Y). Returns true when the column is the piece's and the default furniture must not go in.
     */
    public static boolean paint(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        if (c.r.piece == null) return false;
        return c.r.piece.arena ? PiecesArenas.paint(c, bx, bz, x, z, walk, ceil, fo) : PiecesRooms.paint(c, bx, bz, x, z, walk, ceil, fo);
    }

    // ---- shared shapes ----------------------------------------------------------------------------------------------

    /**
     * Near one of n points evenly spaced on a circle of radius R (point k at angle (k + phase) * 2pi / n), snapped to
     * the block grid so a footprint {@code 2 * half} wide covers whole blocks (odd widths centre on a block, even ones
     * on a block edge). Room centres lie on block edges, so dx and dz are half-integers.
     */
    static boolean ringPoint(double dx, double dz, double R, int n, double phase, double half) {
        double step = Math.PI * 2 / n;
        double k = Math.round(Math.atan2(dz, dx) / step - phase);
        double ang = (k + phase) * step;
        int w = (int) Math.round(half * 2);
        double px = w % 2 == 1 ? Math.floor(Math.cos(ang) * R) + 0.5 : Math.round(Math.cos(ang) * R);
        double pz = w % 2 == 1 ? Math.floor(Math.sin(ang) * R) + 0.5 : Math.round(Math.sin(ang) * R);
        return Math.abs(dx - px) < half && Math.abs(dz - pz) < half;
    }

    /** Which of n spokes (as in {@link #ringPoint}) a column's angle is nearest to. */
    static int spoke(double dx, double dz, int n, double phase) {
        double step = Math.PI * 2 / n;
        return (int) Math.floorMod(Math.round(Math.atan2(dz, dx) / step - phase), (long) n);
    }

    static boolean ring(double dx, double dz, double R, double w) {
        return Math.abs(Math.hypot(dx, dz) - R) < w;
    }

    /**
     * A bell seen from below, as the radius of its shell at height t (0 = lip, 1 = crown) for a bell of lip radius R:
     * flared lip, waist, shoulder, flat crown.
     */
    static double bellRadius(double t, double lip) {
        if (t < 0 || t > 1) return -1;
        double rr = t < 0.15 ? 1.0 - t * 0.9 : t < 0.75 ? 0.865 - (t - 0.15) * 0.42 : 0.613 - (t - 0.75) * 1.4;
        return Math.max(0.6, rr * lip);
    }

    /**
     * Paints a hanging bell in a column: lip radius {@code lip}, {@code tall} blocks, its lip at {@code bottom}, crown at
     * bottom + tall - 1, hung by a chain from {@code top}. The shell is one block thick; a clapper and a light inside.
     */
    static void bell(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int bottom, int tall, double lip, int top,
                     BlockState shell, BlockState inner, BlockState light) {
        double d = Math.hypot(dx, dz);
        for (int i = 0; i < tall; i++) {
            double t = tall == 1 ? 0 : i / (double) (tall - 1);
            double rr = bellRadius(t, lip);
            int y = bottom + i;
            if (i == tall - 1) {
                if (d <= rr + 0.35) c.set(bx, y, bz, shell);
            } else if (d <= rr + 0.35 && d >= rr - 0.85) c.set(bx, y, bz, shell);
            else if (d < rr - 0.85 && i >= tall - 3 && inner != null) c.set(bx, y, bz, inner);
        }
        boolean axis = Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6;
        if (axis) {
            for (int y = bottom + tall; y < top; y++) c.set(bx, y, bz, GatePainter.chain());
            // the clapper and the light that makes the bell glow from inside
            if (light != null) c.set(bx, bottom + tall - 3, bz, light);
            c.set(bx, bottom + 1, bz, shell);
            for (int y = bottom + 2; y < bottom + tall - 3; y++) c.set(bx, y, bz, GatePainter.chain());
        }
    }

    static BlockState s(String spec) {
        return GatePainter.state(spec);
    }

    static BlockState bronze() {
        return GatesRegistry.BELL_BRONZE.get().defaultBlockState();
    }
}
