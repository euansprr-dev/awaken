package dev.awaken.atlas.client;

import dev.awaken.atlas.Explored;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Height and top colour per column (SPEC §16.8), sampled from client chunks as they arrive, in tiles of 512×512
 * columns. Colour is the block's map colour, with the biome's grass, foliage and water tint where vanilla tints the
 * block. Main thread only; the mesh worker reads tiles without a lock (a torn read is one stale column for a frame).
 */
final class ColumnStore {
    static final int TILE_SHIFT = 9, TILE = 1 << TILE_SHIFT, MASK = TILE - 1;
    /** Material classes, for the colour under the surface on cliffs and cut edges. Three bits. */
    static final int K_NONE = 0, K_SOIL = 1, K_SAND = 2, K_STONE = 3, K_WATER = 4, K_SNOW = 5, K_LEAVES = 6, K_BUILT = 7;
    /** info: water depth 0..15 (bits 0–3), material class (bits 4–6), lit (bit 7: a light source on top, for night). */
    static final int LIT = 0x80;

    static final class Tile {
        final int tx, tz;
        final short[] height = new short[TILE * TILE];
        /** ARGB; alpha 0 = never sampled. */
        final int[] color = new int[TILE * TILE];
        final byte[] info = new byte[TILE * TILE];
        /** Unsaved samples since the last save; when it was last sampled (for eviction, oldest first). */
        boolean dirty;
        long touched;

        Tile(int tx, int tz) {
            this.tx = tx;
            this.tz = tz;
        }
    }

    final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private long cachedKey = Long.MIN_VALUE;
    private Tile cached;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    // stats: the last 4096 chunk samples
    final long[] sampleNanos = new long[4096];
    long samples;

    /** The tile holding block (bx, bz); null when absent and not {@code create}. Main thread (the worker uses {@link #peek}). */
    Tile tile(int bx, int bz, boolean create) {
        long k = Explored.key(bx >> TILE_SHIFT, bz >> TILE_SHIFT);
        if (k == cachedKey) return cached;
        Tile t = tiles.get(k);
        if (t == null) {
            if (!create) return null;
            t = new Tile(bx >> TILE_SHIFT, bz >> TILE_SHIFT);
            tiles.put(k, t);
        }
        cachedKey = k;
        cached = t;
        return t;
    }

    /** A tile by tile coordinates, no caching: safe to call from the mesh worker (the map is replaced, never resized there). */
    Tile peek(int tx, int tz) {
        return snapshot.get(Explored.key(tx, tz));
    }

    // the worker reads a copy of the map, swapped in by the main thread when tiles come or go
    private volatile Long2ObjectOpenHashMap<Tile> snapshot = new Long2ObjectOpenHashMap<>();

    private void published() {
        snapshot = new Long2ObjectOpenHashMap<>(tiles);
    }

    static int index(int bx, int bz) {
        return ((bz & MASK) << TILE_SHIFT) | (bx & MASK);
    }

    /** Samples all 256 columns of a chunk the client just received. Main thread. */
    void sample(LevelChunk chunk, boolean ceiling) {
        long t0 = System.nanoTime();
        int bx0 = chunk.getPos().getMinBlockX(), bz0 = chunk.getPos().getMinBlockZ(), minY = chunk.getMinY();
        int before = tiles.size();
        Tile t = tile(bx0, bz0, true);
        if (tiles.size() != before) published();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int bx = bx0 + lx, bz = bz0 + lz, i = index(bx, bz);
                int y = ceiling ? underRoof(chunk, bx, bz, minY) : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
                if (y < minY) { // void column (End islands' gaps), or no floor under a roof
                    t.color[i] = 0;
                    continue;
                }
                pos.set(bx, y, bz);
                BlockState s = chunk.getBlockState(pos);
                int argb, kind, depth = 0;
                if (s.getFluidState().is(FluidTags.WATER)) {
                    while (depth < 15) { // depth darkens the water, as on vanilla maps
                        pos.setY(y - depth - 1);
                        if (!chunk.getBlockState(pos).getFluidState().is(FluidTags.WATER)) break;
                        depth++;
                    }
                    pos.setY(y);
                    argb = 0xFF000000 | biome(chunk, bx, y, bz).getWaterColor();
                    kind = K_WATER;
                } else {
                    MapColor mc = s.getMapColor(chunk, pos);
                    if (mc == MapColor.NONE) {
                        argb = 0xFF707070;
                        kind = K_STONE;
                    } else if (mc == MapColor.GRASS) {
                        argb = 0xFF000000 | biome(chunk, bx, y, bz).getGrassColor(bx, bz);
                        kind = K_SOIL;
                    } else if (mc == MapColor.PLANT) {
                        argb = 0xFF000000 | biome(chunk, bx, y, bz).getFoliageColor();
                        kind = K_LEAVES;
                    } else {
                        argb = 0xFF000000 | mc.col;
                        kind = mc == MapColor.SAND ? K_SAND : mc == MapColor.SNOW ? K_SNOW
                                : mc == MapColor.DIRT || mc == MapColor.PODZOL ? K_SOIL
                                : mc == MapColor.WOOD || mc == MapColor.TERRACOTTA_WHITE || mc == MapColor.COLOR_RED ? K_BUILT : K_STONE;
                    }
                }
                // lit: a lamp on top, or a light a few blocks down the column (a lantern under an eave), or one of
                // Carillon's lit windows (the city's kit, found by id) in the wall under a roof's edge, so the city
                // glows at night
                boolean lit = false;
                for (int dy = 0; dy <= 12 && !lit; dy++) {
                    pos.setY(y - dy);
                    BlockState below = chunk.getBlockState(pos);
                    lit = dy <= 5 && below.getLightEmission(chunk, pos) >= 10 || window() != net.minecraft.world.level.block.Blocks.AIR && below.getBlock() == window();
                }
                t.height[i] = (short) y;
                t.color[i] = argb;
                t.info[i] = (byte) (depth | (kind << 4) | (lit ? LIT : 0));
            }
        }
        t.dirty = true;
        t.touched = System.currentTimeMillis();
        long dt = System.nanoTime() - t0;
        sampleNanos[(int) (samples & 4095)] = dt;
        samples++;
    }

    /** A roofed dimension (the Nether) starts sampling this far under its roof. */
    static final int ROOF_START = 118, ROOF_SCAN = 110;

    /**
     * Under a roof the heightmap only finds the roof, so read down from just under it: through the rock to the first
     * open air, then down through the air to the floor. The table then shows the highest floor of each column, the
     * ledges and shores you can walk. Up to {@link #ROOF_SCAN} blocks a column, so roofed chunks are sampled a few a
     * tick (AtlasState), never all on arrival.
     */
    private int underRoof(LevelChunk chunk, int bx, int bz, int minY) {
        int y = Math.min(ROOF_START, chunk.getMaxY() - 2), end = Math.max(minY, y - ROOF_SCAN);
        pos.set(bx, y, bz);
        while (y > end && !chunk.getBlockState(pos).isAir()) pos.setY(--y);
        while (y > end && chunk.getBlockState(pos).isAir()) pos.setY(--y);
        return y > end ? y : minY - 1;
    }

    private static net.minecraft.world.level.block.Block window;

    /** The city's lit window block, or air when the city isn't here. */
    private static net.minecraft.world.level.block.Block window() {
        if (window == null) window = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("lit_window"));
        return window;
    }

    private static Biome biome(LevelChunk chunk, int bx, int y, int bz) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(y), QuartPos.fromBlock(bz)).value();
    }

    /** A tile read from the disk cache: columns sampled this session win, the rest come from disk. Main thread. */
    void adopt(Tile disk) {
        long k = Explored.key(disk.tx, disk.tz);
        Tile t = tiles.get(k);
        if (t == null) {
            tiles.put(k, disk);
            cachedKey = Long.MIN_VALUE;
            published();
            return;
        }
        for (int i = 0; i < TILE * TILE; i++) {
            if ((t.color[i] >>> 24) == 0 && (disk.color[i] >>> 24) != 0) {
                t.height[i] = disk.height[i];
                t.color[i] = disk.color[i];
                t.info[i] = disk.info[i];
            }
        }
        t.touched = Math.max(t.touched, disk.touched);
    }

    /** The sampled ground height under a point, or the fallback where nothing is sampled. */
    double groundY(double x, double z, double fallback) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        Tile t = tile(bx, bz, false);
        int i = index(bx, bz);
        return t != null && (t.color[i] >>> 24) != 0 ? t.height[i] + 1 : fallback;
    }

    long bytes() {
        return tiles.size() * (long) TILE * TILE * (2 + 4 + 1);
    }

    void clear() {
        tiles.clear();
        cachedKey = Long.MIN_VALUE;
        cached = null;
        published();
        samples = 0;
    }
}
