package dev.awaken.ground.wild.gen;

import dev.awaken.living.api.Wildness;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Wildness for worldgen (SPEC §41.3): the field worked out at a chunk's 5 by 5 corners four blocks apart (the chunk's edges
 * included, so neighbouring chunks share their edge values and nothing shows a line) and read between them bilinearly.
 * Cached per thread for the last few chunks, so every feature of a chunk shares one working-out (25 calls of
 * {@link Wildness#land}, about 0.3 ms). Thread-safe; nothing is allocated on a hit.
 */
public final class WildLattice {
    private WildLattice() {}

    /** Wildness at a column: what the wild features read. */
    @FunctionalInterface
    public interface Field {
        double at(int x, int z);
    }

    private static final int SLOTS = 4;

    private static final class Cache {
        final ServerLevel[] level = new ServerLevel[SLOTS];
        final long[] key = new long[SLOTS];
        final float[][] corners = new float[SLOTS][25];
        int next;
    }

    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    public static double at(WorldGenLevel level, int x, int z) {
        return at(level.getLevel(), x, z);
    }

    public static double at(ServerLevel level, int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        float[] c = corners(level, cx, cz);
        int lx = x & 15, lz = z & 15;
        int i = lx >> 2, j = lz >> 2;
        float fx = (lx & 3) / 4F, fz = (lz & 3) / 4F;
        float a = c[j * 5 + i], b = c[j * 5 + i + 1], d = c[(j + 1) * 5 + i], e = c[(j + 1) * 5 + i + 1];
        float top = a + (b - a) * fx, bottom = d + (e - d) * fx;
        return top + (bottom - top) * fz;
    }

    private static float[] corners(ServerLevel level, int cx, int cz) {
        Cache cache = CACHE.get();
        long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
        for (int s = 0; s < SLOTS; s++) {
            if (cache.level[s] == level && cache.key[s] == key) return cache.corners[s];
        }
        int s = cache.next;
        cache.next = (s + 1) % SLOTS;
        float[] c = cache.corners[s];
        int x0 = cx << 4, z0 = cz << 4;
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < 5; i++) c[j * 5 + i] = (float) Wildness.land(level, x0 + i * 4, z0 + j * 4);
        }
        cache.level[s] = level;
        cache.key[s] = key;
        return c;
    }
}
