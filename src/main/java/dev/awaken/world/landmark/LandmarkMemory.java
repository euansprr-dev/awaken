package dev.awaken.world.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What one player knows of the landmarks (attachment {@code awaken:landmark_memory}, kept on death, schema 1): the ones
 * they reached in person ({@code "well"}, {@code "ruin:<chunk x>,<chunk z>"} of the ruin's start chunk), the one a
 * grove's spirit last whispered about, and the rumours already told (for the town's API, {@link Landmarks#rumour}).
 */
public final class LandmarkMemory {
    public static final int SCHEMA = 1;
    /** Plenty for a long game; the oldest reached ruins are forgotten first (they only mean "no Now line, no omen"). */
    static final int MAX_REACHED = 512;

    final Set<String> reached = new LinkedHashSet<>();
    final Set<String> told = new LinkedHashSet<>();
    String whispered = "";
    long whisperedAt;

    public LandmarkMemory() {}

    public boolean reached(String id) {
        return reached.contains(id);
    }

    public Set<String> reached() {
        return reached;
    }

    /** Records a landmark reached in person. Returns false if it was already known. */
    boolean reach(String id) {
        if (!reached.add(id)) return false;
        while (reached.size() > MAX_REACHED) reached.remove(reached.iterator().next());
        return true;
    }

    private record M(Optional<Integer> schema, List<String> reached, List<String> told, String whispered, long whisperedAt) {
        static final MapCodec<M> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schema").forGetter(M::schema),
                Codec.STRING.listOf().optionalFieldOf("reached", List.of()).forGetter(M::reached),
                Codec.STRING.listOf().optionalFieldOf("told", List.of()).forGetter(M::told),
                Codec.STRING.optionalFieldOf("whispered", "").forGetter(M::whispered),
                Codec.LONG.optionalFieldOf("whispered_at", 0L).forGetter(M::whisperedAt)
        ).apply(i, M::new));
    }

    public static final MapCodec<LandmarkMemory> MAP_CODEC = M.CODEC.xmap(m -> {
        LandmarkMemory l = new LandmarkMemory();
        l.reached.addAll(m.reached());
        l.told.addAll(m.told());
        l.whispered = m.whispered();
        l.whisperedAt = m.whisperedAt();
        return l;
    }, l -> new M(Optional.of(SCHEMA), List.copyOf(l.reached), List.copyOf(l.told), l.whispered, l.whisperedAt));

    /** Value view for tests. */
    public String describe() {
        return "reached " + reached + ", told " + told + ", whispered " + whispered + "@" + whisperedAt;
    }
}
