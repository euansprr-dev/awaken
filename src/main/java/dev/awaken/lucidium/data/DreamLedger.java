package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which dreams are waiting for one player and what they've done in them (SPEC §9.2). A dream comes only when one waits:
 * at most one a night, not two nights running unless asked for, and new ones join the list at most one a play-day.
 * Saved inside {@link Dreamer}.
 */
public final class DreamLedger {
    /** A dream waiting to be dreamed: the trial, since when, why (for the log), and what it carries (a bell, a place). */
    public record Waiting(String trial, long since, String why, String ref) {
        public static final Codec<Waiting> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("trial").forGetter(Waiting::trial),
                Codec.LONG.fieldOf("since").forGetter(Waiting::since),
                Codec.STRING.optionalFieldOf("why", "").forGetter(Waiting::why),
                Codec.STRING.optionalFieldOf("ref", "").forGetter(Waiting::ref)
        ).apply(i, Waiting::new));
    }

    /** A trial finished at least once: best time in ticks, best medal (0 none, 1 bronze, 2 silver, 3 gold), when, how often. */
    public record Finished(String trial, int best, int medal, long day, int times) {
        public static final Codec<Finished> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("trial").forGetter(Finished::trial),
                Codec.INT.fieldOf("best").forGetter(Finished::best),
                Codec.INT.optionalFieldOf("medal", 0).forGetter(Finished::medal),
                Codec.LONG.fieldOf("day").forGetter(Finished::day),
                Codec.INT.optionalFieldOf("times", 1).forGetter(Finished::times)
        ).apply(i, Finished::new));
    }

    public static final int BRONZE = 1, SILVER = 2, GOLD = 3;
    /** Old worlds: at most this many dreams wait after the first login since the update. */
    public static final int SEED_MAX = 3;

    public final List<Waiting> waiting = new ArrayList<>();
    public final Map<String, Finished> finished = new LinkedHashMap<>();
    /** Run ids whose move your body remembers (the morning gave it once). */
    public final Set<String> remembered = new LinkedHashSet<>();
    /** Waiting refs already given out (a bell taken, a place shown), so the same prize never waits twice. */
    public final Set<String> given = new LinkedHashSet<>();
    public long lastDreamDay = Long.MIN_VALUE / 2;
    public long lastNewDay = Long.MIN_VALUE / 2;
    /** The Dream Journal's choice for tonight ("" the oldest waiting): a trial kind or "bell". */
    public String choice = "";
    public long choiceDay = -1;
    public boolean seeded;

    public boolean isWaiting(String trial) {
        for (Waiting w : waiting) if (w.trial.equals(trial)) return true;
        return false;
    }

    public boolean finished(String trial) {
        return finished.containsKey(trial);
    }

    public @Nullable Waiting waitingFor(String trial) {
        for (Waiting w : waiting) if (w.trial.equals(trial)) return w;
        return null;
    }

    /**
     * Puts a dream on the list: not if it already waits, and only one new dream a play-day ({@code anyDay} lifts that
     * for the very first dream and old worlds' seeding). Returns whether it was added.
     */
    public boolean offer(String trial, long day, String why, String ref, boolean anyDay) {
        if (isWaiting(trial)) return false;
        if (!anyDay && lastNewDay == day) return false;
        waiting.add(new Waiting(trial, day, why, ref));
        if (!anyDay) lastNewDay = day;
        return true;
    }

    /**
     * Tonight's dream, or null: none waits, you dreamed tonight already, or you dreamed last night and didn't ask for one
     * ({@link #asked}: a choice in the Dream Journal for tonight).
     */
    public @Nullable Waiting tonight(long day) {
        if (waiting.isEmpty() || lastDreamDay == day) return null;
        if (lastDreamDay == day - 1 && !asked(day)) return null;
        return next(day);
    }

    /** The dream at the head of the list for tonight, whatever the nights before: the choice's kind first, else the oldest. */
    public @Nullable Waiting next(long day) {
        if (waiting.isEmpty()) return null;
        if (asked(day)) {
            for (Waiting w : waiting) if (choice.equals(kindOf(w))) return w;
        }
        return waiting.getFirst();
    }

    /** Whether the Dream Journal asked for a dream tonight. */
    public boolean asked(long day) {
        return !choice.isEmpty() && choiceDay == day;
    }

    /** What the Dream Journal's choice calls a waiting dream: "bell" for one carrying a bell, else its trial's kind. */
    public static String kindOf(Waiting w) {
        if (w.ref.startsWith("bell:")) return "bell";
        var t = dev.awaken.lucidium.trial.Trial.byId(w.trial);
        return t == null ? "" : t.kind.name().toLowerCase();
    }

    /** Finished: off the list (its prize given), best time and medal kept. Returns the record before (null the first time). */
    public @Nullable Finished finish(String trial, int ticks, int medal, long day) {
        Waiting w = waitingFor(trial);
        if (w != null) {
            waiting.remove(w);
            if (!w.ref.isEmpty()) given.add(w.ref);
        }
        Finished before = finished.get(trial);
        if (before == null) finished.put(trial, new Finished(trial, ticks, medal, day, 1));
        else finished.put(trial, new Finished(trial, Math.min(before.best, ticks), Math.max(before.medal, medal), day, before.times + 1));
        return before;
    }

    // ---- save ------------------------------------------------------------------------------------------------------

    public static final Codec<List<Waiting>> WAITING = Waiting.CODEC.listOf();
    public static final Codec<List<Finished>> FINISHED = Finished.CODEC.listOf();

    void read(List<Waiting> w, List<Finished> f, List<String> rem, List<String> giv) {
        waiting.clear();
        waiting.addAll(w);
        finished.clear();
        for (Finished x : f) finished.put(x.trial, x);
        remembered.clear();
        remembered.addAll(rem);
        given.clear();
        given.addAll(giv);
    }
}
