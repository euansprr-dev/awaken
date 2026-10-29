package dev.awaken.living.hunt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one hunter has learned (SPEC §41.3), saved, synced to them, kept through death: research per kind (signs read,
 * hunts later) and the places they found for the Atlas (beds, dens, kill sites; the newest 24). Immutable: a change is a
 * new state, set on the player so it syncs.
 */
public record HuntState(Map<String, Integer> research, List<Found> found) {
    public static final int SCHEMA = 1, FOUND_MAX = 24;
    /** Research points for each level: kinds are studied at 5, 15 and 30 (a sign read is one, a hunt ten). */
    public static final int[] LEVELS = {5, 15, 30};

    /** A place found: a herd's bed, a pack's den or a kill site; where, what left it, and on which play-day. */
    public record Found(int kind, int x, int z, String species, long day) {
        public static final int BED = 0, DEN = 1, KILL = 2;
        static final Codec<Found> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("kind").forGetter(Found::kind),
                Codec.INT.fieldOf("x").forGetter(Found::x),
                Codec.INT.fieldOf("z").forGetter(Found::z),
                Codec.STRING.fieldOf("species").forGetter(Found::species),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Found::day)
        ).apply(i, Found::new));
    }

    public static final Codec<HuntState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("research", Map.of()).forGetter(HuntState::research),
            Found.CODEC.listOf().optionalFieldOf("found", List.of()).forGetter(HuntState::found)
    ).apply(i, (schema, research, found) -> new HuntState(research, found)));

    public static final HuntState EMPTY = new HuntState(Map.of(), List.of());

    public HuntState {
        research = Map.copyOf(research);
        found = List.copyOf(found);
    }

    public int points(String species) {
        return research.getOrDefault(species, 0);
    }

    /** 0 (never studied) to 3. */
    public int level(String species) {
        int p = points(species), lv = 0;
        while (lv < LEVELS.length && p >= LEVELS[lv]) lv++;
        return lv;
    }

    public HuntState study(String species, int points) {
        Map<String, Integer> r = new HashMap<>(research);
        r.merge(species, points, Integer::sum);
        return new HuntState(r, found);
    }

    /** One more place; the same kind of place within 16 blocks replaces the old one. */
    public HuntState find(Found f) {
        List<Found> list = new ArrayList<>(found.size() + 1);
        for (Found o : found) if (o.kind != f.kind || Math.abs(o.x - f.x) > 16 || Math.abs(o.z - f.z) > 16) list.add(o);
        list.add(f);
        while (list.size() > FOUND_MAX) list.removeFirst();
        return new HuntState(research, list);
    }
}
