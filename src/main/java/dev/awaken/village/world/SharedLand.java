package dev.awaken.village.world;

import dev.awaken.village.plan.Terrain;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * The land as the generator estimates it, each column worked out once for every thread that plans (SPEC §28.9, Cost).
 * The towns' and wayhouses' site searches, the settlements' plans and the roads read the same ground again and again: a
 * site search, then the plan on the site it found; a village with no room, then a hamlet on its anchor; a town inside its
 * wall, then inside the wider one; a road's line, then the land's family point by point; two roads into one town. Here
 * they share one bounded cache of columns (top and water), so each column's noise is worked out once; the biome over a
 * column comes from each thread's own resolver (its climate sampler caches). Gives exactly what {@link GenTerrain} gives.
 */
public final class SharedLand implements Terrain {
    /** Stripes, and the columns a stripe's young half holds before it becomes the old half: 512k columns (16 MB) at most. */
    static final int STRIPES = 64, YOUNG = 4096;
    private static final long MISSING = Long.MIN_VALUE;

    private final ThreadLocal<GenTerrain> own;
    private final Stripe[] stripes = new Stripe[STRIPES];

    public SharedLand(ThreadLocal<GenTerrain> own) {
        this.own = own;
        for (int i = 0; i < STRIPES; i++) stripes[i] = new Stripe();
    }

    /** Forgets every column (the roads' bench, between its cold runs). */
    public void clear() {
        for (Stripe s : stripes) s.clear();
    }

    @Override
    public long both(int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xffffffffL);
        Stripe s = stripes[(int) (HashCommon.mix(k) >>> 58)];
        long v = s.get(k);
        if (v == MISSING) {
            v = own.get().both(x, z);
            s.put(k, v);
        }
        return v;
    }

    @Override
    public int top(int x, int z) {
        return (int) (both(x, z) >> 32);
    }

    @Override
    public int water(int x, int z) {
        return (int) both(x, z);
    }

    @Override
    public int family(int x, int z) {
        return own.get().family(x, top(x, z), z);
    }

    @Override
    public int seaLevel() {
        return own.get().seaLevel();
    }

    /** One stripe: a young and an old map (a full young half becomes the old one; a column found in the old is kept young). */
    private static final class Stripe {
        private Long2LongOpenHashMap young = map(), old = map();

        private static Long2LongOpenHashMap map() {
            Long2LongOpenHashMap m = new Long2LongOpenHashMap(YOUNG);
            m.defaultReturnValue(MISSING);
            return m;
        }

        synchronized long get(long k) {
            long v = young.get(k);
            if (v != MISSING) return v;
            v = old.get(k);
            if (v != MISSING) put(k, v);
            return v;
        }

        synchronized void clear() {
            young = map();
            old = map();
        }

        synchronized void put(long k, long v) {
            if (young.size() >= YOUNG) {
                old = young;
                young = map();
            }
            young.put(k, v);
        }
    }
}
