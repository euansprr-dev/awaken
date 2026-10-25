package dev.awaken.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

/**
 * What one player has explored in one dimension (SPEC §16.8): 1 bit per 16×16 cell, a chunk's footprint, so a cell's
 * coordinates are its chunk's. Tiles of 32×32 cells (512×512 blocks) are 16 longs each. The same class backs the
 * server's record and the client's mirror. Not thread-safe; the server thread and the client's main thread own theirs.
 */
public final class Explored {
    public static final int TILE_SHIFT = 5, TILE = 1 << TILE_SHIFT, WORDS = TILE * TILE / 64;

    /** One tile on the wire and on disk. */
    public record TileBits(int tx, int tz, long[] bits) {
        static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);
        public static final Codec<TileBits> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(TileBits::tx),
                Codec.INT.fieldOf("z").forGetter(TileBits::tz),
                LONGS.fieldOf("bits").forGetter(TileBits::bits)).apply(i, TileBits::new));
    }

    public static final Codec<Explored> CODEC = TileBits.CODEC.listOf().xmap(Explored::of, Explored::tiles);

    private final Long2ObjectOpenHashMap<long[]> tiles = new Long2ObjectOpenHashMap<>();
    private int count;
    /** Bumps on every change, so caches (the client's clouds and mesh) know to look again. */
    public int version;

    public static long key(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    public static int keyX(long k) {
        return (int) (k >> 32);
    }

    public static int keyZ(long k) {
        return (int) k;
    }

    static Explored of(List<TileBits> list) {
        Explored e = new Explored();
        for (TileBits t : list) if (t.bits().length == WORDS) e.putTile(t.tx(), t.tz(), t.bits().clone());
        return e;
    }

    public List<TileBits> tiles() {
        List<TileBits> out = new ArrayList<>(tiles.size());
        for (Long2ObjectMap.Entry<long[]> en : tiles.long2ObjectEntrySet())
            out.add(new TileBits(keyX(en.getLongKey()), keyZ(en.getLongKey()), en.getValue().clone()));
        return out;
    }

    public boolean explored(int cellX, int cellZ) {
        long[] t = tiles.get(key(cellX >> TILE_SHIFT, cellZ >> TILE_SHIFT));
        if (t == null) return false;
        int i = ((cellZ & (TILE - 1)) << TILE_SHIFT) | (cellX & (TILE - 1));
        return (t[i >> 6] & (1L << (i & 63))) != 0;
    }

    /**
     * How many cells are explored in the square of {@code size} cells from (cellX0, cellZ0); {@code size} a power of two
     * up to 32, the corner aligned to it, so each row is one run of bits in one word (rows are 32 bits, two to a long).
     */
    public int countIn(int cellX0, int cellZ0, int size) {
        long mask = size >= 64 ? -1L : (1L << size) - 1;
        int n = 0;
        long[] t = null;
        int lastTz = Integer.MIN_VALUE;
        for (int z = cellZ0; z < cellZ0 + size; z++) {
            if ((z >> TILE_SHIFT) != lastTz) {
                lastTz = z >> TILE_SHIFT;
                t = tiles.get(key(cellX0 >> TILE_SHIFT, lastTz));
            }
            if (t == null) continue;
            int bit = ((z & (TILE - 1)) << TILE_SHIFT) | (cellX0 & (TILE - 1));
            n += Long.bitCount((t[bit >> 6] >>> (bit & 63)) & mask);
        }
        return n;
    }

    /** Marks one cell; true when it was new. */
    public boolean mark(int cellX, int cellZ) {
        long[] t = tiles.computeIfAbsent(key(cellX >> TILE_SHIFT, cellZ >> TILE_SHIFT), k -> new long[WORDS]);
        int i = ((cellZ & (TILE - 1)) << TILE_SHIFT) | (cellX & (TILE - 1));
        long bit = 1L << (i & 63);
        if ((t[i >> 6] & bit) != 0) return false;
        t[i >> 6] |= bit;
        count++;
        version++;
        return true;
    }

    /** The tile's bits, or null when nothing in it is explored. Read only. */
    public long[] tile(int tx, int tz) {
        return tiles.get(key(tx, tz));
    }

    /** Replaces one tile (the client, from the server). */
    public void putTile(int tx, int tz, long[] bits) {
        long[] old = tiles.put(key(tx, tz), bits);
        if (old != null) count -= bits(old);
        count += bits(bits);
        version++;
    }

    /** Adds everything {@code other} knows; returns how many cells were new. */
    public int merge(Explored other) {
        int before = count;
        for (Long2ObjectMap.Entry<long[]> en : other.tiles.long2ObjectEntrySet()) {
            long[] mine = tiles.computeIfAbsent(en.getLongKey(), k -> new long[WORDS]);
            long[] theirs = en.getValue();
            for (int w = 0; w < WORDS; w++) {
                long add = theirs[w] & ~mine[w];
                if (add != 0) {
                    mine[w] |= add;
                    count += Long.bitCount(add);
                }
            }
        }
        if (count != before) version++;
        return count - before;
    }

    /** A deep copy (the client's mesh worker reads one while the main thread keeps marking). */
    public Explored copy() {
        Explored e = new Explored();
        for (Long2ObjectMap.Entry<long[]> en : tiles.long2ObjectEntrySet()) e.tiles.put(en.getLongKey(), en.getValue().clone());
        e.count = count;
        e.version = version;
        return e;
    }

    public int count() {
        return count;
    }

    public int tileCount() {
        return tiles.size();
    }

    public void clear() {
        tiles.clear();
        count = 0;
        version++;
    }

    private static int bits(long[] t) {
        int n = 0;
        for (long w : t) n += Long.bitCount(w);
        return n;
    }

    /** Whether the circle of {@code radius} blocks round (bx, bz) touches the cell (its nearest point is inside). */
    public static boolean touches(int cellX, int cellZ, int bx, int bz, int radius) {
        int nx = Math.clamp(bx, cellX << 4, (cellX << 4) + 15), nz = Math.clamp(bz, cellZ << 4, (cellZ << 4) + 15);
        long dx = nx - bx, dz = nz - bz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
