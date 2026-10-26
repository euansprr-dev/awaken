package dev.awaken.wildlife;

import java.util.Arrays;

/**
 * The director's rule for one kind on one chunk (SPEC "Wild life"), pure so the GameTests can drive it: how many the
 * chunk should hold now ({@link Density}), never any inside Carillon's walls (its animals are B3b's), and never more of a
 * kind near the player than its cap, nor more bodies or insects in all than the layer's budget. The client walks the
 * chunks nearest first, so the caps keep what is close.
 */
public final class Census {
    /** Of each kind, how many are near and staying (not leaving); bodies and insects in all. */
    public final int[] count = new int[WildKinds.COUNT];
    public int bodies, insects;

    public void reset() {
        Arrays.fill(count, 0);
        bodies = insects = 0;
    }

    public void add(int kind) {
        count[kind]++;
        if (WildKinds.body(kind)) bodies++;
        else insects++;
    }

    public void remove(int kind) {
        count[kind]--;
        if (WildKinds.body(kind)) bodies--;
        else insects--;
    }

    /** How many a chunk wants of a kind now: none inside the city or out of reach. */
    public static int want(int kind, double dist, boolean city, Density.Habitat h, long chunk, int tod, float rain, float thunder, float dial) {
        if (city || dist > WildKinds.REACH[kind]) return 0;
        return Density.count(Density.lambda(kind, h, tod, rain, thunder) * dial, chunk, kind);
    }

    /**
     * One step toward what the chunk wants: +1 to make one (within the caps), -1 to send one away, 0 to leave it be. A
     * chunk out of reach is left alone (its animals are dropped by distance as the player moves off).
     */
    public int decide(int kind, double dist, boolean city, Density.Habitat h, long chunk, int have, int tod, float rain, float thunder, float dial) {
        if (dist > WildKinds.REACH[kind] && !city) return 0;
        int want = want(kind, dist, city, h, chunk, tod, rain, thunder, dial);
        if (have > want) return -1;
        if (have == want || count[kind] >= WildKinds.CAP[kind]) return 0;
        if (WildKinds.body(kind) ? bodies >= WildKinds.MAX_BODIES : insects >= WildKinds.MAX_INSECTS) return 0;
        return 1;
    }
}
