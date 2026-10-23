package dev.awaken.hearth.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.progression.Rank;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Town NPC definitions from {@code data/awaken/awaken/townsfolk/*.json}: who arrives at which rank and what they say
 * at each rank. The id is the file name (e.g. {@code oren_blacksmith}); the skin is its first word.
 */
public final class Townsfolk {
    private Townsfolk() {}

    public record Def(String name, String role, Rank arrivesAt, Optional<Rank> leavesAt, Map<Rank, String> lines, List<String> chatter) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Def::name),
                Codec.STRING.fieldOf("role").forGetter(Def::role),
                Rank.CODEC.fieldOf("arrives_at").forGetter(Def::arrivesAt),
                Rank.CODEC.optionalFieldOf("leaves_at").forGetter(Def::leavesAt),
                Codec.unboundedMap(Rank.CODEC, Codec.STRING).fieldOf("lines").forGetter(Def::lines),
                Codec.STRING.listOf().optionalFieldOf("chatter", List.of()).forGetter(Def::chatter)
        ).apply(i, Def::new));

        /** Whether this NPC lives at a Hearth whose owner holds {@code rank}. */
        public boolean presentAt(Rank rank) {
            return rank.ordinal() >= arrivesAt.ordinal() && leavesAt.map(l -> rank.ordinal() < l.ordinal()).orElse(true);
        }

        /** The line for the highest rank at or below {@code rank} (falls back to the earliest line). */
        public String line(Rank rank) {
            String best = null;
            int bestRank = -1;
            String earliest = null;
            int earliestRank = Integer.MAX_VALUE;
            for (var e : lines.entrySet()) {
                int r = e.getKey().ordinal();
                if (r <= rank.ordinal() && r > bestRank) {
                    bestRank = r;
                    best = e.getValue();
                }
                if (r < earliestRank) {
                    earliestRank = r;
                    earliest = e.getValue();
                }
            }
            return best != null ? best : earliest != null ? earliest : "...";
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    public static Map<String, Def> all() {
        return DEFS;
    }

    public static Optional<Def> get(String id) {
        return Optional.ofNullable(DEFS.get(id));
    }

    /** Sorted by arrival rank, then name: the order the town fills in. */
    public static List<Map.Entry<String, Def>> inArrivalOrder() {
        List<Map.Entry<String, Def>> l = new ArrayList<>(DEFS.entrySet());
        l.sort(Comparator.<Map.Entry<String, Def>>comparingInt(e -> e.getValue().arrivesAt().ordinal()).thenComparing(Map.Entry::getKey));
        return l;
    }

    /** "Oren the Blacksmith". */
    public static String displayName(String id) {
        return get(id).map(d -> d.name() + " the " + d.role()).orElse(id);
    }

    public static String shortName(String id) {
        return get(id).map(Def::name).orElse(id);
    }

    /** Texture key for the client: the first word of the id ("oren_blacksmith" → "oren"). */
    public static String skin(String id) {
        int u = id.indexOf('_');
        return u < 0 ? id : id.substring(0, u);
    }

    public static String roleOf(String id) {
        return get(id).map(d -> d.role().toLowerCase(java.util.Locale.ROOT)).orElse("");
    }

    /** For tests and commands when no datapack is loaded. */
    public static void put(String id, Def d) {
        DEFS.put(id, d);
    }

    public static void onReloadListeners(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("hearth_townsfolk"), new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener<Def> {
        Loader() {
            super(Def.CODEC, FileToIdConverter.json("awaken/townsfolk"));
        }

        @Override
        protected void apply(Map<Identifier, Def> loaded, ResourceManager manager, ProfilerFiller profiler) {
            DEFS.clear();
            loaded.forEach((id, d) -> DEFS.put(id.getPath(), d));
            Awaken.LOG.info("AWAKEN loaded {} townsfolk", DEFS.size());
        }
    }
}
