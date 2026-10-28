package dev.awaken.gates.worlds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's Atlas of Gates and the world director's memory (attachment {@code awaken:gate_atlas}, kept on death; SPEC
 * §38): every world found with the moods seen and secrets found, the creatures met, floors cleared per theme (mastery)
 * and the last worlds arrived in. The Codex's Atlas page shows it; the director reads it to lean toward what you
 * haven't seen. Each player's own, so friends in one gate each fill theirs.
 */
public final class GateAtlas {
    public static final int SCHEMA = 1;
    static final int RECENT = 12;

    /** What you know of one world. */
    public static final class Entry {
        public long firstDay;
        public int visits;
        /** mood id → arrivals in that mood */
        public final Map<String, Integer> moods = new LinkedHashMap<>();
        public final Set<String> secrets = new LinkedHashSet<>();
        public boolean keepsake;

        public int moodVisits(String mood) {
            return moods.getOrDefault(mood, 0);
        }

        private record S(long firstDay, int visits, Map<String, Integer> moods, List<String> secrets, boolean keepsake) {
            static final Codec<S> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.LONG.fieldOf("first_day").forGetter(S::firstDay),
                    Codec.INT.fieldOf("visits").forGetter(S::visits),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("moods").forGetter(S::moods),
                    Codec.STRING.listOf().fieldOf("secrets").forGetter(S::secrets),
                    Codec.BOOL.optionalFieldOf("keepsake", false).forGetter(S::keepsake)
            ).apply(i, S::new));
        }

        static final Codec<Entry> CODEC = S.CODEC.xmap(s -> {
            Entry e = new Entry();
            e.firstDay = s.firstDay;
            e.visits = s.visits;
            e.moods.putAll(s.moods);
            e.secrets.addAll(s.secrets);
            e.keepsake = s.keepsake;
            return e;
        }, e -> new S(e.firstDay, e.visits, Map.copyOf(e.moods), List.copyOf(e.secrets), e.keepsake));
    }

    public final Map<String, Entry> worlds = new LinkedHashMap<>();
    /** theme id → floors of it cleared (opens its deep world and wonder early) */
    public final Map<String, Integer> themeFloors = new LinkedHashMap<>();
    /** "world#mood" of the last arrivals, newest first */
    public final List<String> recent = new ArrayList<>();
    public final Set<String> creatures = new LinkedHashSet<>();
    public int arrivals;

    public Entry entry(String world) {
        return worlds.get(world);
    }

    public int visits(String world) {
        Entry e = worlds.get(world);
        return e == null ? 0 : e.visits;
    }

    public int moodVisits(String world, String mood) {
        Entry e = worlds.get(world);
        return e == null ? 0 : e.moodVisits(mood);
    }

    public int themeFloors(String theme) {
        return themeFloors.getOrDefault(theme, 0);
    }

    /** The last world arrived in, or "". */
    public String lastWorld() {
        if (recent.isEmpty()) return "";
        String r = recent.get(0);
        int h = r.indexOf('#');
        return h < 0 ? r : r.substring(0, h);
    }

    /** Records an arrival. True when the world is a first-ever find (the title card makes more of it). */
    public boolean arrive(String world, String mood, long day) {
        Entry e = worlds.get(world);
        boolean first = e == null;
        if (first) {
            e = new Entry();
            e.firstDay = day;
            worlds.put(world, e);
        }
        e.visits++;
        e.moods.merge(mood, 1, Integer::sum);
        recent.add(0, world + "#" + mood);
        while (recent.size() > RECENT) recent.remove(recent.size() - 1);
        arrivals++;
        return first;
    }

    public void floorCleared(String theme) {
        themeFloors.merge(theme, 1, Integer::sum);
    }

    /** True when the secret is new to you. */
    public boolean secret(String world, String secret) {
        Entry e = worlds.get(world);
        if (e == null) return false;
        return e.secrets.add(secret);
    }

    public boolean met(String creature) {
        return creatures.add(creature);
    }

    private record A(int schema, Map<String, Entry> worlds, Map<String, Integer> themes, List<String> recent, List<String> creatures,
                     int arrivals) {
        static final Codec<A> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(A::schema),
                Codec.unboundedMap(Codec.STRING, Entry.CODEC).fieldOf("worlds").forGetter(A::worlds),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("themes").forGetter(A::themes),
                Codec.STRING.listOf().fieldOf("recent").forGetter(A::recent),
                Codec.STRING.listOf().fieldOf("creatures").forGetter(A::creatures),
                Codec.INT.fieldOf("arrivals").forGetter(A::arrivals)
        ).apply(i, A::new));
    }

    public static final MapCodec<GateAtlas> MAP_CODEC = A.CODEC.fieldOf("atlas").xmap(a -> {
        GateAtlas g = new GateAtlas();
        g.worlds.putAll(a.worlds);
        g.themeFloors.putAll(a.themes);
        g.recent.addAll(a.recent);
        g.creatures.addAll(a.creatures);
        g.arrivals = a.arrivals;
        return g;
    }, g -> new A(SCHEMA, new LinkedHashMap<>(g.worlds), Map.copyOf(g.themeFloors), List.copyOf(g.recent), List.copyOf(g.creatures),
            g.arrivals));

    public boolean isEmpty() {
        return worlds.isEmpty() && themeFloors.isEmpty() && creatures.isEmpty();
    }
}
