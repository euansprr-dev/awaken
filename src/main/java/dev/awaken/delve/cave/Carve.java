package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The cave places' shared strokes: pure hashing and value noise (the same answer in every chunk), carving that keeps
 * the bedrock, rough blobs, walkable tunnels, the rock of a depth and the small dressings (vines, lichen, roots,
 * water). Every stroke is clipped by the {@link Sink} and skips a box that holds none of it.
 */
final class Carve {
    private Carve() {}

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    /** Falling water (a waterfall's column): flowing water at level 8. */
    static final BlockState FALLING = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);

    // ---- hashing and noise ------------------------------------------------------------------------------------------

    static long hash(long seed, int x, int y, int z) {
        long h = seed ^ 0x9E3779B97F4A7C15L;
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 31) * 0x165667B19E3779F9L;
        h ^= y * 0x27D4EB2F165667C5L;
        h = Long.rotateLeft(h, 27) * 0x94D049BB133111EBL;
        h ^= z * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 32);
    }

    /** 0..1, fixed for the cell. */
    static double rand(long seed, int x, int y, int z) {
        return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise, 0..1, cells {@code scale} blocks across. */
    static double noise(long seed, double x, double y, double z, double scale) {
        double fx = x / scale, fy = y / scale, fz = z / scale;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy), iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix), ty = smooth(fy - iy), tz = smooth(fz - iz);
        double c000 = rand(seed, ix, iy, iz), c100 = rand(seed, ix + 1, iy, iz), c010 = rand(seed, ix, iy + 1, iz), c110 = rand(seed, ix + 1, iy + 1, iz);
        double c001 = rand(seed, ix, iy, iz + 1), c101 = rand(seed, ix + 1, iy, iz + 1), c011 = rand(seed, ix, iy + 1, iz + 1), c111 = rand(seed, ix + 1, iy + 1, iz + 1);
        double a = lerp(tx, c000, c100), b = lerp(tx, c010, c110), c = lerp(tx, c001, c101), d = lerp(tx, c011, c111);
        return lerp(tz, lerp(ty, a, b), lerp(ty, c, d));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    // ---- the rock ---------------------------------------------------------------------------------------------------

    /** The rock at a height: stone above y 0, deepslate below (the world's own). */
    static BlockState rock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    /** Dressed rock: the Delvers' cut faces (flat, so the Runner can run them). */
    static BlockState dressed(int y) {
        return y < 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    static BlockState bricks(int y) {
        return y < 0 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
    }

    static BlockState rubble(int y) {
        return y < 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    static BlockState slab(int y) {
        return (y < 0 ? Blocks.DEEPSLATE_TILE_SLAB : Blocks.STONE_BRICK_SLAB).defaultBlockState();
    }

    static BlockState stairs(int y) {
        return (y < 0 ? Blocks.DEEPSLATE_TILE_STAIRS : Blocks.STONE_BRICK_STAIRS).defaultBlockState();
    }

    /** Whether carving may take this block: everything but the bedrock (and the air there already). */
    static boolean carvable(BlockState s) {
        return !s.is(Blocks.BEDROCK) && !s.isAir();
    }

    // ---- strokes ----------------------------------------------------------------------------------------------------

    static void put(Sink s, int x, int y, int z, BlockState state) {
        if (s.inside(x, y, z)) s.set(x, y, z, state);
    }

    static void air(Sink s, int x, int y, int z) {
        if (!s.inside(x, y, z)) return;
        if (carvable(s.get(x, y, z))) s.set(x, y, z, AIR);
    }

    /** Solid rock back where carving would have opened it (a lode's heart stays in the wall). */
    static void solid(Sink s, int x, int y, int z) {
        if (s.inside(x, y, z) && !s.get(x, y, z).isSolidRender()) s.set(x, y, z, rock(y));
    }

    static void fill(Sink s, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int ax = Math.max(Math.min(x0, x1), s.minX()), bx = Math.min(Math.max(x0, x1), s.maxX());
        int az = Math.max(Math.min(z0, z1), s.minZ()), bz = Math.min(Math.max(z0, z1), s.maxZ());
        int ay = Math.max(Math.min(y0, y1), s.minY()), by = Math.min(Math.max(y0, y1), s.maxY());
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = ay; y <= by; y++) s.set(x, y, z, state);
    }

    static void carveBox(Sink s, int x0, int y0, int z0, int x1, int y1, int z1) {
        int ax = Math.max(Math.min(x0, x1), s.minX()), bx = Math.min(Math.max(x0, x1), s.maxX());
        int az = Math.max(Math.min(z0, z1), s.minZ()), bz = Math.min(Math.max(z0, z1), s.maxZ());
        int ay = Math.max(Math.min(y0, y1), s.minY()), by = Math.min(Math.max(y0, y1), s.maxY());
        for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = ay; y <= by; y++) {
            if (carvable(s.get(x, y, z))) s.set(x, y, z, AIR);
        }
    }

    /**
     * A rough ellipsoid of air: radii (rx, ry, rz) pushed in and out by up to {@code rough} of themselves with noise
     * {@code scale} across; {@code floorY} (Integer.MIN_VALUE for none) cuts it flat below, so a room has a floor to walk.
     */
    static void blob(Sink s, double cx, double cy, double cz, double rx, double ry, double rz, long seed, double rough, double scale, int floorY) {
        int x0 = (int) Math.floor(cx - rx * (1 + rough)) - 1, x1 = (int) Math.ceil(cx + rx * (1 + rough)) + 1;
        int z0 = (int) Math.floor(cz - rz * (1 + rough)) - 1, z1 = (int) Math.ceil(cz + rz * (1 + rough)) + 1;
        int y0 = Math.max((int) Math.floor(cy - ry * (1 + rough)) - 1, floorY), y1 = (int) Math.ceil(cy + ry * (1 + rough)) + 1;
        if (!s.touches(x0, z0, x1, z1)) return;
        for (int x = Math.max(x0, s.minX()); x <= Math.min(x1, s.maxX()); x++) {
            for (int z = Math.max(z0, s.minZ()); z <= Math.min(z1, s.maxZ()); z++) {
                for (int y = Math.max(y0, s.minY()); y <= Math.min(y1, s.maxY()); y++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry, dz = (z + 0.5 - cz) / rz;
                    double d = dx * dx + dy * dy + dz * dz;
                    double k = 1 + rough * (2 * noise(seed, x, y, z, scale) - 1);
                    if (d <= k * k && carvable(s.get(x, y, z))) s.set(x, y, z, AIR);
                }
            }
        }
    }

    /**
     * A walkable passage from a to b (feet levels): a round bore {@code r} across, wandering by up to {@code wander}
     * sideways, its floor never steeper than a block a block (a long drop is stretched along its length), at least 3
     * high. Returns nothing; the floor is the rock it leaves under it.
     */
    static void tunnel(Sink s, double ax, double ay, double az, double bx, double by, double bz, double r, double wander, long seed) {
        double dx = bx - ax, dz = bz - az, dy = by - ay;
        double h = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(Math.max(h, Math.abs(dy)) * 2) + 1;
        double nx = h < 1e-6 ? 0 : -dz / h, nz = h < 1e-6 ? 0 : dx / h;
        int bx0 = (int) Math.floor(Math.min(ax, bx) - r - wander - 2), bx1 = (int) Math.ceil(Math.max(ax, bx) + r + wander + 2);
        int bz0 = (int) Math.floor(Math.min(az, bz) - r - wander - 2), bz1 = (int) Math.ceil(Math.max(az, bz) + r + wander + 2);
        if (!s.touches(bx0, bz0, bx1, bz1)) return;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            // the wander fades in and out at the ends, so the bore meets its rooms square
            double side = (2 * noise(seed, t * 40, 0, 0, 9) - 1) * wander * Math.sin(Math.PI * t);
            double x = ax + dx * t + nx * side, z = az + dz * t + nz * side, y = ay + dy * t;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z), iy = (int) Math.floor(y);
            int ri = (int) Math.ceil(r);
            for (int ox = -ri; ox <= ri; ox++) for (int oz = -ri; oz <= ri; oz++) {
                double hd = Math.sqrt((ix + ox + 0.5 - x) * (ix + ox + 0.5 - x) + (iz + oz + 0.5 - z) * (iz + oz + 0.5 - z));
                if (hd > r) continue;
                // three high in the middle, two at the edge of the bore
                int top = hd < r - 0.6 ? 2 : 1;
                for (int oy = 0; oy <= top; oy++) air(s, ix + ox, iy + oy, iz + oz);
            }
        }
    }

    // ---- dressings ---------------------------------------------------------------------------------------------------

    /** A vine on the face of the solid block on side {@code toWall} of (x, y, z), if that cell is air. */
    static void vine(Sink s, int x, int y, int z, Direction toWall) {
        if (!s.inside(x, y, z) || !s.get(x, y, z).isAir()) return;
        BlockState v = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(toWall), true);
        s.set(x, y, z, v);
    }

    /** Glow lichen on whichever faces of (x, y, z) touch solid rock (air cells only). */
    static void lichen(Sink s, int x, int y, int z) {
        if (!s.inside(x, y, z) || !s.get(x, y, z).isAir()) return;
        BlockState l = Blocks.GLOW_LICHEN.defaultBlockState();
        boolean any = false;
        for (Direction d : Direction.values()) {
            int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
            if (!s.inside(nx, ny, nz)) continue;
            BlockState n = s.get(nx, ny, nz);
            if (n.isSolidRender()) {
                l = l.setValue(MultifaceBlock.getFaceProperty(d), true);
                any = true;
            }
        }
        if (any) s.set(x, y, z, l);
    }

    /** Hanging roots under a solid roof block, in air. */
    static void roots(Sink s, int x, int y, int z) {
        if (!s.inside(x, y, z) || !s.get(x, y, z).isAir() || !s.inside(x, y + 1, z) || !s.get(x, y + 1, z).isSolidRender()) return;
        s.set(x, y, z, Blocks.HANGING_ROOTS.defaultBlockState());
    }

    /** Whether the cell is air or water we may dress (not rock, not a placed thing). */
    static boolean open(Sink s, int x, int y, int z) {
        if (!s.inside(x, y, z)) return false;
        BlockState b = s.get(x, y, z);
        return b.isAir();
    }

    static boolean solidAt(Sink s, int x, int y, int z) {
        return s.inside(x, y, z) && s.get(x, y, z).isSolidRender();
    }

    /** A block's state turned by {@code rot} quarter turns clockwise (stairs, ladders, rails...). */
    static BlockState turn(BlockState s, int rot) {
        return switch (rot & 3) {
            case 1 -> s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            case 2 -> s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
            case 3 -> s.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
            default -> s;
        };
    }

    static BlockState facing(Block b, Direction d) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, d) : s;
    }
}
