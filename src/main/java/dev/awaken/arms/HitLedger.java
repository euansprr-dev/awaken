package dev.awaken.arms;

import org.jspecify.annotations.Nullable;

/**
 * The client's record of the blows it played before the server's word (SPEC §47.1 "Hits land now"): each (move's
 * press, body) once, so the server's HIT for a blow already played adds only what the client couldn't know (a kill, a
 * cleave) and never plays the impact twice. A blow the server never confirms is counted as shown without damage.
 * <p>
 * The two sides can file one blow under different presses (SPEC §25 "Weight in the Hand", stage 1): when a queued strike
 * is pressed again, each side moves it to the newer press on its own clock, and a HIT sent in between carries the older
 * number. So the server's word is matched by the press first, and failing that by the body and the move: the same strike
 * on the same body within {@link #LIFE} is the same blow, never a second impact and never a ghost.
 * Fixed arrays, no allocation per blow.
 */
public final class HitLedger {
    /** How long a played blow waits for the server's word before it counts as a miss (ticks: two seconds of ping). */
    public static final int LIFE = 40;
    /** How long the server's own blow on a body stands for the hands' strike of the same move, filed under another press (ticks). */
    public static final int SAME_BLOW = 12;
    static final int SIZE = 32;

    private final int[] seq = new int[SIZE], body = new int[SIZE], move = new int[SIZE], tick = new int[SIZE], ms = new int[SIZE];
    private final long[] nanos = new long[SIZE];
    // 0 empty, 1 played and waiting for the server, 2 the server's (confirmed, or struck first by the server)
    private final byte[] state = new byte[SIZE];
    private int next, last = -1;
    /** Blows played, confirmed, never confirmed; and of the confirmed, those matched by body and move under another press. */
    public int predicted, confirmed, unconfirmed, reconciled;

    /** Told each blow that was played and never confirmed, as it is counted (development: {@link HitWhy} says why). */
    public interface Ghosts {
        void ghost(int seq, int body);
    }

    public @Nullable Ghosts ghosts;

    /** The client played this blow of {@code move} now: its freeze began at {@code at} (nanos) for {@code freezeMs}. */
    public void played(int seq, int body, int move, int tick, long at, int freezeMs) {
        put(seq, body, move, tick, at, freezeMs, (byte) 1);
        predicted++;
    }

    /**
     * The server's HIT for (seq, body) of {@code move}: true when the blow was already played (the impact is not played
     * again; the server's word is counted), by its press or, under another press, by its body and move. A blow the client
     * didn't play is put down as the server's, so this move's prediction leaves that body alone.
     */
    public boolean confirm(int seq, int body, int move, int tick) {
        int i = find(seq, body);
        if (i < 0) {
            i = waiting(body, move, tick);
            if (i >= 0) reconciled++;
        }
        last = i;
        if (i >= 0) {
            if (state[i] == 1) confirmed++;
            state[i] = 2;
            return true;
        }
        put(seq, body, move, tick, 0L, 0, (byte) 2);
        return false;
    }

    /** When the freeze of the blow the last {@link #confirm} matched began (nanos), 0 if it matched none. */
    public long matchedFreezeAt() {
        return last >= 0 ? nanos[last] : 0L;
    }

    /** That blow's freeze length (ms). */
    public int matchedFreezeMs() {
        return last >= 0 ? ms[last] : 0;
    }

    /** Whether this move has already struck this body, by either side's word under this press. */
    public boolean has(int seq, int body) {
        return find(seq, body) >= 0;
    }

    /**
     * Whether the hands should leave this body alone for the strike of {@code move} under {@code seq}: it is already in
     * the ledger under that press, or the server struck it with the same move under another press within
     * {@link #SAME_BLOW} ticks (the server ran ahead: its blow is this blow, and plays once).
     */
    public boolean struck(int seq, int body, int move, int now) {
        if (find(seq, body) >= 0) return true;
        for (int i = 0; i < SIZE; i++) {
            if (state[i] == 2 && this.body[i] == body && this.move[i] == move && this.seq[i] != seq && now - tick[i] <= SAME_BLOW && now >= tick[i]) return true;
        }
        return false;
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
            if (state[i] == 1) {
                n++;
                if (ghosts != null) ghosts.ghost(seq[i], body[i]);
            }
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

    /** The oldest blow of this move on this body still waiting for the server's word, played no longer than {@link #LIFE} ago. */
    private int waiting(int b, int m, int now) {
        int best = -1;
        for (int i = 0; i < SIZE; i++) {
            if (state[i] != 1 || body[i] != b || move[i] != m || now - tick[i] > LIFE) continue;
            if (best < 0 || tick[i] < tick[best]) best = i;
        }
        return best;
    }

    private void put(int s, int b, int m, int t, long at, int freezeMs, byte st) {
        int i = find(s, b);
        if (i < 0) {
            i = next;
            next = (next + 1) % SIZE;
            if (state[i] == 1) { // a full ledger drops its oldest: never confirmed in time
                unconfirmed++;
                if (ghosts != null) ghosts.ghost(seq[i], body[i]);
            }
        }
        seq[i] = s;
        body[i] = b;
        move[i] = m;
        tick[i] = t;
        nanos[i] = at;
        ms[i] = freezeMs;
        state[i] = st;
    }
}
