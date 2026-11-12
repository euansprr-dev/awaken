package dev.awaken.gates.realms;

import dev.awaken.Awaken;
import dev.awaken.gates.content.Theme;
import dev.awaken.progression.Rank;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Every Realm that can be rolled (SPEC §50.7): data/awaken/realms/*.json. One that doesn't parse is skipped and logged. */
public final class Blueprints {
    private Blueprints() {}

    private static volatile Map<String, Blueprint> defs = Map.of();

    public static Map<String, Blueprint> all() {
        return defs;
    }

    public static Optional<Blueprint> get(String id) {
        return Optional.ofNullable(defs.get(id));
    }

    public static void replace(Map<String, Blueprint> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
    }

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("realms"), new Loader());
    }

    /** The finished Realms of this theme open at this rank, in a fixed order. One that isn't {@code ready} is never rolled, only entered by command. */
    public static List<String> open(Theme theme, Rank rank) {
        return open(theme, rank, true);
    }

    /** As {@link #open(Theme, Rank)}; with {@code openLands} false, never a land under the open sky (SPEC §50.7, Euan's switch of 12 Nov). */
    public static List<String> open(Theme theme, Rank rank, boolean openLands) {
        return defs.entrySet().stream().filter(e -> e.getValue().ready() && e.getValue().theme().equals(theme.id) && e.getValue().ranks().contains(rank.label)
                        && (openLands || !openLand(e.getValue())))
                .map(Map.Entry::getKey).toList();
    }

    /** Whether this blueprint is a land under the open sky (its program says so; nothing is drawn to find out). */
    public static boolean openLand(Blueprint bp) {
        return LandPrograms.open(bp.land().program());
    }

    /** The bag (SPEC §50.2): one you haven't seen first, or the oldest of what's left. Empty means this gate rolls floors. */
    public static Optional<String> pick(Theme theme, Rank rank, Set<String> seen, long seed) {
        return pick(theme, rank, seen, seed, true);
    }

    /** As {@link #pick(Theme, Rank, Set, long)}; with {@code openLands} false the bag holds no land under the open sky. */
    public static Optional<String> pick(Theme theme, Rank rank, Set<String> seen, long seed, boolean openLands) {
        List<String> open = open(theme, rank, openLands);
        if (open.isEmpty()) return Optional.empty();
        List<String> fresh = open.stream().filter(k -> !seen.contains(k)).toList();
        List<String> from = fresh.isEmpty() ? open : fresh;
        return Optional.of(from.get((int) Math.floorMod(seed, (long) from.size())));
    }

    static final class Loader extends SimpleJsonResourceReloadListener<Blueprint> {
        Loader() {
            super(Blueprint.CODEC, FileToIdConverter.json("realms"));
        }

        @Override
        protected void apply(Map<Identifier, Blueprint> loaded, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Blueprint> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                String why = v.problem();
                if (!why.isEmpty()) Awaken.LOG.warn("[realms] {} skipped: {}", k, why);
                else ok.put(k.toString(), v);
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} Realms", ok.size());
        }
    }
}
