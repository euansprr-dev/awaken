package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A condition over a player's profile. Used by Truths, Titles, Latents, Directives and the Regimen.
 * Counter conditions measure progress since a baseline when one is given (Directives and Regimen drills).
 *
 * <pre>
 * {"type": "counter", "counter": "awaken:dashes_airborne_chain", "value": 10}
 * {"type": "all", "of": [ ... ]}
 * </pre>
 */
public sealed interface Condition {
    String type();

    boolean test(Profile p, Map<String, Long> baseline);

    /** Counter ids this condition reads, for indexing. */
    void counters(Set<String> out);

    /** Progress in [0,1] for display. */
    double progress(Profile p, Map<String, Long> baseline);

    Codec<Condition> CODEC = Codec.recursive("awaken_condition", self -> Codec.STRING.dispatch("type", Condition::type, t -> switch (t) {
        case "counter" -> CounterAtLeast.MAP_CODEC;
        case "level" -> LevelAtLeast.MAP_CODEC;
        case "rank" -> RankAtLeast.MAP_CODEC;
        case "stat" -> StatAtLeast.MAP_CODEC;
        case "flag" -> Flag.MAP_CODEC;
        case "all" -> RecordCodecBuilder.<All>mapCodec(i -> i.group(self.listOf().fieldOf("of").forGetter(All::of)).apply(i, All::new));
        case "any" -> RecordCodecBuilder.<Any>mapCodec(i -> i.group(self.listOf().fieldOf("of").forGetter(Any::of)).apply(i, Any::new));
        default -> throw new IllegalArgumentException("Unknown condition type " + t);
    }));

    static long counterValue(Profile p, Map<String, Long> baseline, String counter) {
        long v = p.counter(counter);
        if (baseline != null) v -= baseline.getOrDefault(counter, 0L);
        return v;
    }

    record CounterAtLeast(String counter, long value) implements Condition {
        static final MapCodec<CounterAtLeast> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("counter").forGetter(CounterAtLeast::counter),
                Codec.LONG.fieldOf("value").forGetter(CounterAtLeast::value)
        ).apply(i, CounterAtLeast::new));
        public String type() { return "counter"; }
        public boolean test(Profile p, Map<String, Long> b) { return counterValue(p, b, counter) >= value; }
        public void counters(Set<String> out) { out.add(counter); }
        public double progress(Profile p, Map<String, Long> b) { return Math.min(1.0, (double) counterValue(p, b, counter) / Math.max(1, value)); }
    }

    record Flag(String flag) implements Condition {
        static final MapCodec<Flag> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("flag").forGetter(Flag::flag)).apply(i, Flag::new));
        public String type() { return "flag"; }
        public boolean test(Profile p, Map<String, Long> b) { return counterValue(p, b, flag) >= 1; }
        public void counters(Set<String> out) { out.add(flag); }
        public double progress(Profile p, Map<String, Long> b) { return test(p, b) ? 1 : 0; }
    }

    record LevelAtLeast(int level) implements Condition {
        static final MapCodec<LevelAtLeast> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("level").forGetter(LevelAtLeast::level)).apply(i, LevelAtLeast::new));
        public String type() { return "level"; }
        public boolean test(Profile p, Map<String, Long> b) { return p.level() >= level; }
        public void counters(Set<String> out) { out.add(Counters.LEVEL); }
        public double progress(Profile p, Map<String, Long> b) { return Math.min(1.0, (double) p.level() / level); }
    }

    record RankAtLeast(Rank rank) implements Condition {
        static final MapCodec<RankAtLeast> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Rank.CODEC.fieldOf("rank").forGetter(RankAtLeast::rank)).apply(i, RankAtLeast::new));
        public String type() { return "rank"; }
        public boolean test(Profile p, Map<String, Long> b) { return p.rank().ordinal() >= rank.ordinal(); }
        public void counters(Set<String> out) { out.add(Counters.RANK); }
        public double progress(Profile p, Map<String, Long> b) { return test(p, b) ? 1 : (double) p.rank().ordinal() / rank.ordinal(); }
    }

    record StatAtLeast(Stat stat, int value) implements Condition {
        static final MapCodec<StatAtLeast> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Stat.CODEC.fieldOf("stat").forGetter(StatAtLeast::stat),
                Codec.INT.fieldOf("value").forGetter(StatAtLeast::value)).apply(i, StatAtLeast::new));
        public String type() { return "stat"; }
        public boolean test(Profile p, Map<String, Long> b) { return p.stat(stat) >= value; }
        public void counters(Set<String> out) { out.add(Counters.STATS); }
        public double progress(Profile p, Map<String, Long> b) { return Math.min(1.0, (double) p.stat(stat) / value); }
    }

    record All(List<Condition> of) implements Condition {
        public String type() { return "all"; }
        public boolean test(Profile p, Map<String, Long> b) { return of.stream().allMatch(c -> c.test(p, b)); }
        public void counters(Set<String> out) { of.forEach(c -> c.counters(out)); }
        public double progress(Profile p, Map<String, Long> b) { return of.stream().mapToDouble(c -> c.progress(p, b)).average().orElse(1); }
    }

    record Any(List<Condition> of) implements Condition {
        public String type() { return "any"; }
        public boolean test(Profile p, Map<String, Long> b) { return of.stream().anyMatch(c -> c.test(p, b)); }
        public void counters(Set<String> out) { of.forEach(c -> c.counters(out)); }
        public double progress(Profile p, Map<String, Long> b) { return of.stream().mapToDouble(c -> c.progress(p, b)).max().orElse(0); }
    }
}
