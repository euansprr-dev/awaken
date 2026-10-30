package dev.awaken.bells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * How a home's song is played (SPEC §43.16), worked out the same on both sides from which bells are in it. Missing bells
 * are silence, never a knock. One bell tolls three times, slowly, each stroke left to ring out: a village's one bell
 * calling people home. Two to nine ring rounds the way ringers do, each bell once from the highest to the lowest, three
 * times over, then the deepest once more on its own. Ten or more play the tune: each bell at its step, a run of missing
 * steps shortened to one beat so the tune never stalls, and every note there is struck together at the end.
 */
public final class SongPlan {
    private SongPlan() {}

    /** One bell struck {@code tick} ticks into the song. */
    public record Strike(int tick, int no) {}

    /** The strikes in order, and how long the song lasts with its last stroke rung out. */
    public record Plan(List<Strike> strikes, int length) {}

    static final int TOLL_GAP = 60, ROUND_GAP = 12, ROUND_REST = 12, LAST_REST = 24, RING_OUT = 60, TUNE_BEAT = 4, SLOW_BEAT = 6, SLOW_BEATS = 50;

    /** Where bell #n's note sits above D, in semitones, so rounds can go from the highest bell to the lowest. */
    static int height(int no) {
        return SEMIS[BellCatalog.MELODY[no]];
    }

    private static final int[] SEMIS = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24, 26, 28, 31, 33};

    public static Plan of(IntPredicate has) {
        List<Integer> bells = new ArrayList<>();
        for (int no = 0; no < 100; no++) if (has.test(no)) bells.add(no);
        List<Strike> out = new ArrayList<>();
        int n = bells.size();
        if (n == 0) return new Plan(out, 0);
        if (n == 1) {
            for (int k = 0; k < 3; k++) out.add(new Strike(k * TOLL_GAP, bells.getFirst()));
            return new Plan(out, 2 * TOLL_GAP + RING_OUT);
        }
        if (n < 10) {
            bells.sort(Comparator.comparingInt(SongPlan::height).reversed().thenComparingInt(no -> no));
            int t = 0;
            for (int round = 0; round < 3; round++) {
                if (round > 0) t += ROUND_REST;
                for (int no : bells) {
                    out.add(new Strike(t, no));
                    t += ROUND_GAP;
                }
            }
            t += LAST_REST - ROUND_GAP;
            out.add(new Strike(t, bells.getLast()));
            return new Plan(out, t + RING_OUT);
        }
        // the tune: count its beats first (rests shortened), and play it slower when it's short
        int beats = 0;
        boolean rest = false;
        for (int no = 0; no < 100; no++) {
            if (has.test(no)) {
                beats++;
                rest = false;
            } else if (!rest) {
                beats++;
                rest = true;
            }
        }
        int beat = beats <= SLOW_BEATS ? SLOW_BEAT : TUNE_BEAT;
        int t = 0;
        rest = false;
        for (int no = 0; no < 100; no++) {
            if (has.test(no)) {
                out.add(new Strike(t, no));
                t += beat;
                rest = false;
            } else if (!rest) {
                t += beat;
                rest = true;
            }
        }
        // the end: each note the home has, struck together once (one bell a note)
        boolean[] note = new boolean[15];
        t += beat;
        for (int no : bells)
            if (!note[BellCatalog.MELODY[no]]) {
                note[BellCatalog.MELODY[no]] = true;
                out.add(new Strike(t, no));
            }
        return new Plan(out, t + RING_OUT);
    }
}
