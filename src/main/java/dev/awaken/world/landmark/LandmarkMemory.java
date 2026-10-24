package dev.awaken.world.landmark;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one player knows of the landmarks (attachment {@code awaken:landmark_memory}, kept on death, synced to its owner,
 * schema 2): the ones they reached in person ({@code "well"}, {@code "ruin:<chunk x>,<chunk z>"} of the ruin's start
 * chunk), the one a grove's spirit last whispered about, the rumours already told (for the town's API,
 * {@link Landmarks#rumour}) and, since schema 2, where each landmark they reached or heard of stands (the Gate Map).
 */
public final class LandmarkMemory {
    public static final int SCHEMA = 2;
    /** Plenty for a long game; the oldest reached ruins are forgotten first (they only mean "no Horizon line, no omen"). */
    static final int MAX_REACHED = 512;

    final Set<String> reached = new LinkedHashSet<>();
    final Set<String> told = new LinkedHashSet<>();
    final Map<String, Place> places = new LinkedHashMap<>();
    String whispered = "";
    long whisperedAt;

    public LandmarkMemory() {}

    public boolean reached(String id) {
        return reached.contains(id);
    }

    public Set<String> reached() {
        return reached;
    }

    /** The landmark a grove's spirit last whispered about ("" when none). */
    public String whispered() {
        return whispered;
    }

    /** Where the landmarks this player reached or heard of stand (oldest first). */
    public Collection<Place> places() {
        return places.values();
    }

    /** Records a landmark reached in person. Returns false if it was already known. */
    boolean reach(String id) {
        if (!reached.add(id)) return false;
        while (reached.size() > MAX_REACHED) reached.remove(reached.iterator().next());
        return true;
    }

    /** Remembers where a landmark stands (reached, told or whispered of). Returns false if it was already placed. */
    boolean place(String id, LandmarkKind kind, BlockPos pos) {
        if (places.containsKey(id)) return false;
        places.put(id, new Place(id, kind.ordinal(), pos.getX(), pos.getZ()));
        while (places.size() > MAX_REACHED) places.remove(places.keySet().iterator().next());
        return true;
    }

    /** One known landmark on the map: kind = {@link LandmarkKind} ordinal, x/z its centre. */
    public record Place(String id, int kind, int x, int z) {
        static final Codec<Place> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Place::id),
                Codec.INT.fieldOf("kind").forGetter(Place::kind),
                Codec.INT.fieldOf("x").forGetter(Place::x),
                Codec.INT.fieldOf("z").forGetter(Place::z)
        ).apply(i, Place::new));

        public LandmarkKind landmark() {
            return LandmarkKind.of(kind);
        }
    }

    private record M(Optional<Integer> schema, List<String> reached, List<String> told, String whispered, long whisperedAt,
                     List<Place> places) {
        static final MapCodec<M> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schema").forGetter(M::schema),
                Codec.STRING.listOf().optionalFieldOf("reached", List.of()).forGetter(M::reached),
                Codec.STRING.listOf().optionalFieldOf("told", List.of()).forGetter(M::told),
                Codec.STRING.optionalFieldOf("whispered", "").forGetter(M::whispered),
                Codec.LONG.optionalFieldOf("whispered_at", 0L).forGetter(M::whisperedAt),
                Place.CODEC.listOf().optionalFieldOf("places", List.of()).forGetter(M::places)
        ).apply(i, M::new));
    }

    public static final MapCodec<LandmarkMemory> MAP_CODEC = M.CODEC.xmap(m -> {
        LandmarkMemory l = new LandmarkMemory();
        l.reached.addAll(m.reached());
        l.told.addAll(m.told());
        l.whispered = m.whispered();
        l.whisperedAt = m.whisperedAt();
        for (Place p : m.places()) l.places.put(p.id(), p);
        return l;
    }, l -> new M(Optional.of(SCHEMA), List.copyOf(l.reached), List.copyOf(l.told), l.whispered, l.whisperedAt,
            List.copyOf(l.places.values())));

    /** Value view for tests. */
    public String describe() {
        return "reached " + reached + ", told " + told + ", whispered " + whispered + "@" + whisperedAt + ", places " + places.keySet();
    }
}
