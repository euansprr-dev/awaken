package dev.awaken.arms;

/**
 * The client's record of the blows it played before the server's word (SPEC §47.1 "Hits land now"): each (move's
 * press, body) once, so the server's HIT for a blow already played adds only what the client couldn't know (a kill, a
 * cleave) and never plays the impact twice. A blow the server never confirms is counted as shown without damage.
 * Fixed arrays, no allocation per blow.
 */
public final class HitLedger {
    /** How long a played blow waits for the server's word before it counts as a miss (ticks: two seconds of ping). */
    public static final int LIFE = 40;
    static final int SIZE = 32;

    private final int[] seq = new int[SIZE], body = new int[SIZE], tick = new int[SIZE], ms = new int[SIZE];
    private final long[] nanos = new long[SIZE];
    // 0 empty, 1 played and waiting for the server, 2 the server's (confirmed, or struck first by the server)
    private final byte[] state = new byte[SIZE];
    private int next;
    public int predicted, confirmed, unconfirmed;

    /** The client played this blow now: its freeze began at {@code at} (nanos) for {@code freezeMs}. */
    public void played(int seq, int body, int tick, long at, int freezeMs) {
        put(seq, body, tick, at, freezeMs, (byte) 1);
        predicted++;
    }

    /**
     * The server's HIT for (seq, body): true when the blow was already played (the impact is not played again; the
     * server's word is counted). A blow the client didn't play is put down as the server's, so this move's prediction
     * leaves that body alone.
     */
    public boolean confirm(int seq, int body, int tick) {
        int i = find(seq, body);
        if (i >= 0) {
            if (state[i] == 1) confirmed++;
            state[i] = 2;
            return true;
        }
        put(seq, body, tick, 0L, 0, (byte) 2);
        return false;
    }

    /** Whether this move has already struck this body, by either side's word. */
    public boolean has(int seq, int body) {
        return find(seq, body) >= 0;
    }

    /** When the client's freeze on a played blow began (nanos), 0 if it didn't play it. */
    public long freezeAt(int seq, int body) {
        int i = find(seq, body);
        return i >= 0 ? nanos[i] : 0L;
    }

    /** The client's freeze length on a played blow (ms), 0 if it didn't play it. */
    public int freezeMs(int seq, int body) {
        int i = find(seq, body);
        return i >= 0 ? ms[i] : 0;
    }

    /** Blows played over {@link #LIFE} ago the server never confirmed: counted, and forgotten. Returns how many now. */
    public int expire(int now) {
        int n = 0;
        for (int i = 0; i < SIZE; i++) {
            if (state[i] == 0 || now - tick[i] <= LIFE) continue;
            if (state[i] == 1) n++;
            state[i] = 0;
        }
        unconfirmed += n;
        return n;
    }

    public void clear() {
        java.util.Arrays.fill(state, (byte) 0);
    }

    private int find(int s, int b) {
        for (int i = 0; i < SIZE; i++) if (state[i] != 0 && seq[i] == s && body[i] == b) return i;
        return -1;
    }

    private void put(int s, int b, int t, long at, int freezeMs, byte st) {
        int i = find(s, b);
        if (i < 0) {
            i = next;
            next = (next + 1) % SIZE;
            if (state[i] == 1) unconfirmed++; // a full ledger drops its oldest: never confirmed in time
        }
        seq[i] = s;
        body[i] = b;
        tick[i] = t;
        nanos[i] = at;
        ms[i] = freezeMs;
        state[i] = st;
    }
}
