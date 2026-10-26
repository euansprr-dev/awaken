package dev.awaken.core.audio.world;

import net.minecraft.util.RandomSource;

/**
 * When a soundscape's one-shots happen (docs/sound/PLAN.md §6.1, events). Each event is a Poisson process at its
 * full rate ({@code per_minute}); each candidate is kept with the probability the caller gives it this moment (its day
 * curve, the rain, its family's weight, a hush: thinning, so a rate change takes effect at once and never bunches).
 * A kept event may be answered from elsewhere a moment later (a wren, then another wren in the next tree). Lulls:
 * every so often the whole soundscape goes almost still for half a minute, so silence is part of the texture.
 *
 * <p>Pure and allocation-free after construction (one per playing soundscape, client thread); deterministic for a
 * seed, which is what the GameTest {@code audio_world_clock} checks.
 */
public final class EventClock {
    /** Receives the events that happen: the event's index in the soundscape, and whether it answers an earlier one. */
    public interface Sink {
        void fire(int event, boolean answer);
    }

    private static final int MAX_ANSWERS = 8;

    private final RandomSource random;
    private final double[] next = new double[SoundscapeDef.MAX_EVENTS];
    private final double[] answerAt = new double[MAX_ANSWERS];
    private final int[] answerOf = new int[MAX_ANSWERS];
    private int answers;
    private SoundscapeDef def;
    private double now, lullNext, lullLeft;

    public EventClock(long seed) {
        this.random = RandomSource.create(seed);
    }

    /** Starts the clock on a soundscape: every event waits its own first interval (nothing fires together at once). */
    public void start(SoundscapeDef def) {
        this.def = def;
        answers = 0;
        now = 0;
        for (int i = 0; i < def.events().size(); i++) next[i] = interval(def.events().get(i));
        lullLeft = 0;
        lullNext = def.lull().none() ? Double.MAX_VALUE : uniform(def.lull().everyMin(), def.lull().everyMax());
    }

    public SoundscapeDef def() {
        return def;
    }

    /** True while the soundscape is in a lull. */
    public boolean lull() {
        return lullLeft > 0;
    }

    /**
     * Moves the clock on by {@code seconds}. {@code keep[i]}: the chance (0..1) that event i, if it comes up now, is
     * heard (the caller's curve × rain × weight × hush). An answer follows its heard call unless, by the time it comes,
     * its event's chance has fallen to almost nothing (a hush, a lull, the place left behind).
     */
    public void advance(double seconds, float[] keep, Sink sink) {
        if (def == null) return;
        now += seconds;
        float lullDepth = 1F;
        if (lullLeft > 0) {
            lullLeft -= seconds;
            lullDepth = def.lull().depth();
            if (lullLeft <= 0) lullNext = uniform(def.lull().everyMin(), def.lull().everyMax());
        } else if ((lullNext -= seconds) <= 0) {
            lullLeft = uniform(def.lull().lengthMin(), def.lull().lengthMax());
            lullNext = Double.MAX_VALUE;
        }
        for (int i = 0; i < def.events().size(); i++) {
            SoundscapeDef.Event e = def.events().get(i);
            next[i] -= seconds;
            while (next[i] <= 0) {
                next[i] += interval(e);
                if (random.nextFloat() < keep[i] * lullDepth) {
                    sink.fire(i, false);
                    if (e.answer() > 0 && answers < MAX_ANSWERS && random.nextFloat() < e.answer()) {
                        answerAt[answers] = now + uniform(e.answerMin(), e.answerMax());
                        answerOf[answers++] = i;
                    }
                }
            }
        }
        for (int k = 0; k < answers; ) {
            if (answerAt[k] > now) {
                k++;
                continue;
            }
            int i = answerOf[k];
            answers--;
            answerAt[k] = answerAt[answers];
            answerOf[k] = answerOf[answers];
            if (keep[i] * lullDepth > 0.05F) sink.fire(i, true); // a heard call is answered unless its place has gone quiet
        }
    }

    /** A uniform value in [lo, hi] from the clock's own randomness (the Conductor's placement uses it too). */
    public float uniform(float lo, float hi) {
        return lo + (hi - lo) * random.nextFloat();
    }

    private double interval(SoundscapeDef.Event e) {
        double mean = 60.0 / e.perMinute();
        return -mean * Math.log(1.0 - random.nextDouble());
    }
}
