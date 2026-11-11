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
        return defs.entrySet().stream().filter(e -> e.getValue().ready() && e.getValue().theme().equals(theme.id) && e.getValue().ranks().contains(rank.label))
                .map(Map.Entry::getKey).toList();
    }

    /** The bag (SPEC §50.2): one you haven't seen first, or the oldest of what's left. Empty means this gate rolls floors. */
    public static Optional<String> pick(Theme theme, Rank rank, Set<String> seen, long seed) {
        List<String> open = open(theme, rank);
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
