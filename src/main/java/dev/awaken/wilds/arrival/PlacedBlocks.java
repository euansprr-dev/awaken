package dev.awaken.wilds.arrival;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/**
 * Where one player placed blocks lately: the last {@link #MAX} placements, each remembered for {@link #LIFE} ticks, so
 * "is that a block I put there?" is one hash read. A house built an hour ago isn't a tower to a site; a tower just
 * built is. Positions are {@code BlockPos.asLong}.
 */
final class PlacedBlocks {
    static final int MAX = 512;
    static final long LIFE = 20 * 60 * 10;
    private static final long NONE = Long.MIN_VALUE;

    private final Long2LongOpenHashMap at = new Long2LongOpenHashMap();
    private final long[] order = new long[MAX];
    private final long[] when = new long[MAX];
    private int head, size;

    PlacedBlocks() {
        at.defaultReturnValue(NONE);
    }

    void add(long pos, long now) {
        if (size == MAX) {
            long old = order[head];
            if (at.get(old) == when[head]) at.remove(old); // unless it was placed again since
        } else {
            size++;
        }
        order[head] = pos;
        when[head] = now;
        at.put(pos, now);
        head = (head + 1) % MAX;
    }

    boolean has(long pos, long now) {
        long t = at.get(pos);
        return t != NONE && now >= t && now - t <= LIFE;
    }

    void remove(long pos) {
        at.remove(pos);
    }

    boolean isEmpty() {
        return at.isEmpty();
    }
}
