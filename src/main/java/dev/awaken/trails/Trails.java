package dev.awaken.trails;

import dev.awaken.Awaken;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every trail in the loaded data packs, in catalogue order (weight, then id). Server side only: the client is sent what it shows. */
public final class Trails {
    private Trails() {}

    private static volatile Map<String, TrailDef> defs = Map.of();

    public static Map<String, TrailDef> all() {
        return defs;
    }

    public static TrailDef get(String id) {
        return defs.get(id);
    }

    /** Replaces the set (the reload, and tests); ordered by weight then id. */
    public static void replace(Map<String, TrailDef> next) {
        List<Map.Entry<String, TrailDef>> list = new ArrayList<>(next.entrySet());
        list.sort(Comparator.<Map.Entry<String, TrailDef>>comparingInt(e -> e.getValue().weight()).thenComparing(Map.Entry::getKey));
        Map<String, TrailDef> ordered = new LinkedHashMap<>();
        for (var e : list) ordered.put(e.getKey(), e.getValue());
        defs = Collections.unmodifiableMap(ordered);
    }

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("trails"), new Loader());
    }

    static final class Loader extends SimpleJsonResourceReloadListener<TrailDef> {
        Loader() {
            super(TrailDef.CODEC, FileToIdConverter.json("awaken/trail"));
        }

        @Override
        protected void apply(Map<Identifier, TrailDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, TrailDef> ok = new LinkedHashMap<>();
            loaded.forEach((k, v) -> {
                if (v.steps().isEmpty() || !TrailDef.AREAS.contains(v.area())) Awaken.LOG.warn("[trails] {} skipped: it needs steps and a known area", k);
                else ok.put(k.toString(), v);
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} trails", ok.size());
        }
    }
}
