package dev.awaken.terrain.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * A landmark as a dense voxel grid round its centre (x and z from the centre, y from the first air above the ground
 * there), built once from the structure's seed and kept while the chunks it spans generate, so every chunk places the
 * same thing. Cell codes index the landmark's palette ({@link Landmark#state}); 0 is empty. A column may be marked
 * as standing on the ground ({@link #foot}): placement continues its lowest cell down to the real ground, so nothing
 * meant to stand ever floats over a dip.
 */
public final class Voxels {
    public static final byte EMPTY = 0;
    public static final int NO_FOOT = Short.MIN_VALUE;

    public final int extent, minY, maxY, sx, sy;
    final byte[] cells;
    final short[] foot;
    final short[] colLo, colHi;

    public Voxels(int extent, int minY, int maxY) {
        this.extent = extent;
        this.minY = minY;
        this.maxY = maxY;
        this.sx = 2 * extent + 1;
        this.sy = maxY - minY + 1;
        this.cells = new byte[sx * sx * sy];
        this.foot = new short[sx * sx];
        this.colLo = new short[sx * sx];
        this.colHi = new short[sx * sx];
        Arrays.fill(foot, (short) NO_FOOT);
    }

    public boolean inside(int x, int y, int z) {
        return x >= -extent && x <= extent && z >= -extent && z <= extent && y >= minY && y <= maxY;
    }

    int index(int x, int y, int z) {
        return ((y - minY) * sx + (x + extent)) * sx + (z + extent);
    }

    int column(int x, int z) {
        return (x + extent) * sx + (z + extent);
    }

    public byte get(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : EMPTY;
    }

    public void set(int x, int y, int z, byte code) {
        if (inside(x, y, z)) cells[index(x, y, z)] = code;
    }

    /** Sets a cell only where nothing is yet. */
    public void fill(int x, int y, int z, byte code) {
        if (inside(x, y, z)) {
            int i = index(x, y, z);
            if (cells[i] == EMPTY) cells[i] = code;
        }
    }

    /** Marks a column as standing: from its lowest cell (at or below y) it continues down to the ground. */
    public void stand(int x, int y, int z) {
        if (x < -extent || x > extent || z < -extent || z > extent) return;
        int c = column(x, z);
        if (foot[c] == NO_FOOT || y < foot[c]) foot[c] = (short) Math.max(minY, y);
    }

    /**
     * The surface heightmap a feature or piece should read: the worldgen one while chunks generate, the live one in a
     * loaded level (commands and GameTests build into it; a loaded chunk keeps no worldgen heightmaps).
     */
    public static net.minecraft.world.level.levelgen.Heightmap.Types surface(net.minecraft.world.level.WorldGenLevel level) {
        return level instanceof net.minecraft.server.level.ServerLevel ? net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE
                : net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG;
    }

    /** A stable 0..1 value per cell (no RandomSource: the same whatever is built first). */
    public static double hash(long seed, int x, int y, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x8CB92BA72F3D8DD7L + z * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /**
     * A crystal prism standing at (bx, by, bz): {@code height} tall, leaning ({@code lx}, {@code lz}) blocks per block
     * up, its section a slanted hexagon of radius {@code r0} tapering to a point (or broken off flat when
     * {@code broken}). The layers overlap face to face however it leans: a thin top is walked cell by cell.
     */
    public void prism(double bx, int by, double bz, int height, double r0, double lx, double lz, double turn, boolean broken, byte code) {
        double ca = Math.cos(turn), sa = Math.sin(turn);
        int px = Integer.MIN_VALUE, pz = 0;
        for (int k = 0; k < height; k++) {
            double t = k / (double) height;
            double r = r0 * Math.pow(1 - t, broken ? 0.35 : 0.85);
            double cx = bx + lx * k, cz = bz + lz * k;
            int y = by + k;
            int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
            if (r >= 0.55) {
                int span = (int) Math.ceil(r) + 1;
                for (int x = ix - span; x <= ix + span + 1; x++) {
                    for (int z = iz - span; z <= iz + span + 1; z++) {
                        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                        double u = dx * ca + dz * sa, v = -dx * sa + dz * ca;
                        // a hexagon: three pairs of flat faces
                        double hex = Math.max(Math.abs(v), Math.abs(u) * 0.866 + Math.abs(v) * 0.5);
                        if (hex <= r) {
                            fill(x, y, z, code);
                            if (k == 0) stand(x, y, z);
                        }
                    }
                }
            }
            fill(ix, y, iz, code);
            if (k == 0) stand(ix, y, iz);
            // every layer's centre joins the one below face to face, however the prism leans
            if (px != Integer.MIN_VALUE && (ix != px || iz != pz)) {
                fill(ix, y - 1, iz, code);
                if (ix != px && iz != pz) fill(ix, y - 1, pz, code);
            }
            px = ix;
            pz = iz;
        }
    }

    /**
     * A crystal needle from (bx, by, bz) along (dx, dy, dz), {@code length} long: a square section turned 45° (a diamond
     * in plan) {@code r0} across at the foot, tapering to a point. Walked in half-block steps, each step's centre joined
     * to the last face to face, so a steep or a slanting needle stays one piece. With {@code standing}, its foot's cells
     * are standing columns (they continue down to the ground at placement).
     */
    public void needle(double bx, double by, double bz, double dx, double dy, double dz, double length, double r0, boolean standing, byte code) {
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= n;
        dy /= n;
        dz /= n;
        int steps = Math.max(1, (int) Math.ceil(length * 2)), px = Integer.MIN_VALUE, py = 0, pz = 0;
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps, r = r0 * Math.pow(1 - t, 0.9);
            double cx = bx + dx * length * t, cy = by + dy * length * t, cz = bz + dz * length * t;
            int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy), iz = (int) Math.floor(cz);
            if (r >= 0.5) {
                int span = (int) Math.ceil(r) + 1;
                for (int x = ix - span; x <= ix + span; x++) {
                    for (int z = iz - span; z <= iz + span; z++) {
                        if (Math.abs(x + 0.5 - cx) + Math.abs(z + 0.5 - cz) > r * 1.25) continue;
                        fill(x, iy, z, code);
                        if (standing && s == 0) stand(x, iy, z);
                    }
                }
            }
            fill(ix, iy, iz, code);
            if (standing && s == 0) stand(ix, iy, iz);
            if (px != Integer.MIN_VALUE) join(px, py, pz, ix, iy, iz, code);
            px = ix;
            py = iy;
            pz = iz;
        }
    }

    /** Cells from a to b, one axis at a time (the longest first), so the run stays joined face to face. */
    private void join(int ax, int ay, int az, int bx, int by, int bz, byte code) {
        int x = ax, y = ay, z = az, guard = 0;
        while ((x != bx || y != by || z != bz) && guard++ < 64) {
            int ex = Math.abs(bx - x), ey = Math.abs(by - y), ez = Math.abs(bz - z);
            if (ey >= ex && ey >= ez) y += Integer.signum(by - y);
            else if (ex >= ez) x += Integer.signum(bx - x);
            else z += Integer.signum(bz - z);
            fill(x, y, z, code);
        }
    }

    /**
     * Places every cell of one code (after {@link #seal()}) at {@code origin} + cell as {@code state}, into blocks
     * {@code soft} accepts only; each standing column's foot then continues down, at most {@code reach} blocks, until it
     * meets something not soft. A feature's whole shape at once. Returns the blocks set.
     */
    public int place(WorldGenLevel level, BlockPos origin, byte code, BlockState state, int reach, Predicate<BlockState> soft) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ(), placed = 0;
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int col = column(x, z);
                for (int y = colLo[col]; y <= colHi[col]; y++) {
                    if (cells[index(x, y, z)] != code || !soft.test(level.getBlockState(m.set(ox + x, oy + y, oz + z)))) continue;
                    level.setBlock(m, state, Block.UPDATE_CLIENTS);
                    placed++;
                }
                int f = foot[col];
                if (f == NO_FOOT || cells[index(x, f, z)] != code) continue;
                for (int y = oy + f - 1; y >= oy + f - reach; y--) {
                    if (!soft.test(level.getBlockState(m.set(ox + x, y, oz + z)))) break;
                    level.setBlock(m, state, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Records each column's used range, so placement skips empty air quickly; returns the number of cells in use. */
    public int seal() {
        int used = 0, cols = sx * sx;
        Arrays.fill(colLo, (short) 1);
        Arrays.fill(colHi, (short) 0);
        for (int y = minY, i = 0; y <= maxY; y++) {
            for (int c = 0; c < cols; c++, i++) {
                if (cells[i] == EMPTY) continue;
                used++;
                if (colLo[c] > colHi[c]) colLo[c] = (short) y;
                colHi[c] = (short) y;
            }
        }
        return used;
    }

    /** How far from the centre the furthest cell lies, horizontally, after {@link #seal()} (the city reserve is kept this far off). */
    public double reach() {
        double m = 0;
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int c = column(x, z);
                if (colLo[c] <= colHi[c]) m = Math.max(m, Math.hypot(x, z));
            }
        }
        return m;
    }

    /** The cells' bounds after {@link #seal()}: minX, minY, minZ, maxX, maxY, maxZ (all 0 when empty). */
    public int[] bounds() {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int x = -extent; x <= extent; x++) {
            for (int z = -extent; z <= extent; z++) {
                int c = column(x, z);
                if (colLo[c] > colHi[c]) continue;
                b[0] = Math.min(b[0], x);
                b[2] = Math.min(b[2], z);
                b[3] = Math.max(b[3], x);
                b[5] = Math.max(b[5], z);
                b[1] = Math.min(b[1], colLo[c]);
                b[4] = Math.max(b[4], colHi[c]);
            }
        }
        if (b[0] == Integer.MAX_VALUE) return new int[6];
        return b;
    }

    /** Cells in use (after {@link #seal()}), counted. */
    public int count(byte code) {
        int n = 0;
        for (byte c : cells) if (c == code) n++;
        return n;
    }
}
