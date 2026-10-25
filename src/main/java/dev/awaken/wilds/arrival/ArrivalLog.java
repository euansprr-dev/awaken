package dev.awaken.wilds.arrival;

import dev.awaken.core.api.ArrivalHooks.Way;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's last {@link #SIZE} movement samples (a quarter second each: 20 s), oldest overwritten first. A tally per
 * way keeps "anything built in the window?" a single read. No allocation after construction, except {@link #toList}.
 */
final class ArrivalLog {
    static final int SIZE = 80;
    static final Way[] WAYS = Way.values();

    private final byte[] ring = new byte[SIZE];
    private final int[] tally = new int[WAYS.length];
    private int head, size;

    void push(Way way) {
        if (size == SIZE) tally[ring[head]]--;
        else size++;
        ring[head] = (byte) way.ordinal();
        tally[way.ordinal()]++;
        head = (head + 1) % SIZE;
    }

    boolean contains(Way way) {
        return tally[way.ordinal()] > 0;
    }

    int count(Way way) {
        return tally[way.ordinal()];
    }

    int size() {
        return size;
    }

    /** Oldest first (commands and tests). */
    List<Way> toList() {
        List<Way> out = new ArrayList<>(size);
        int start = Math.floorMod(head - size, SIZE);
        for (int i = 0; i < size; i++) out.add(WAYS[ring[(start + i) % SIZE]]);
        return out;
    }
}
