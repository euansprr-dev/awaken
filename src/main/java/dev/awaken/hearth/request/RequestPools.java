package dev.awaken.hearth.request;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The request pools, loaded from {@code data/<ns>/awaken/request/*.json} like progression's content and sent to every
 * client on login and /reload (the Status screen shows the hints). A def whose item or tag doesn't exist is dropped and
 * logged: the Anchor never asks for something that can't be had.
 */
public final class RequestPools {
    private RequestPools() {}

    private static volatile Map<String, RequestDef> defs = Map.of();

    public static Map<String, RequestDef> all() {
        return defs;
    }

    public static RequestDef get(String id) {
        return defs.get(id);
    }

    public static List<RequestRules.Entry> entries() {
        List<RequestRules.Entry> out = new ArrayList<>(defs.size());
        for (var e : defs.entrySet()) out.add(new RequestRules.Entry(e.getKey(), e.getValue()));
        return out;
    }

    /** Replaces the pools (the loader, the client's sync, tests). */
    public static void replace(Map<String, RequestDef> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
        RequestRules.forgetMatchers();
    }

    static void addReload(AddServerReloadListenersEvent event) {
        event.addListener(Awaken.id("hearth_requests"), new Loader());
    }

    static void onSync(OnDatapackSyncEvent event) {
        var msg = new RequestNet.Pools(Map.copyOf(defs));
        event.getRelevantPlayers().forEach(p -> AwakenNet.send(p, msg));
    }

    /**
     * Problems with a def, or empty when it's fine (the loader and the content test share this). Tags are bound after
     * reload listeners run, so a tag is only checked here when {@code checkTags}; offers check it again when made.
     */
    public static List<String> problems(String id, RequestDef d, boolean checkTags) {
        List<String> out = new ArrayList<>();
        if (d.item().isPresent() == d.tag().isPresent()) out.add(id + ": exactly one of item or tag");
        else if (d.tag().isPresent() && Identifier.tryParse(d.tag().get()) == null) out.add(id + ": bad tag id " + d.tag().get());
        else if ((d.item().isPresent() || checkTags) && !RequestRules.resolves(d.target())) out.add(id + ": " + d.target() + " doesn't exist");
        if (d.tag().isPresent() && d.name().isEmpty()) out.add(id + ": a tag request needs a name");
        if (d.count().isEmpty() || d.count().values().stream().anyMatch(n -> n <= 0)) out.add(id + ": counts by rank must be positive");
        if (d.value() <= 0) out.add(id + ": value must be positive");
        for (String t : d.requires()) if (!RequestRules.known(t)) out.add(id + ": unknown requirement " + t);
        for (String t : d.anyOf()) if (!RequestRules.known(t)) out.add(id + ": unknown requirement " + t);
        return out;
    }

    static final class Loader extends SimpleJsonResourceReloadListener<RequestDef> {
        Loader() {
            super(RequestDef.CODEC, FileToIdConverter.json("awaken/request"));
        }

        @Override
        protected void apply(Map<Identifier, RequestDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            TreeMap<String, RequestDef> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                List<String> bad = problems(k.toString(), v, false);
                if (bad.isEmpty()) ok.put(k.toString(), v);
                else bad.forEach(b -> Awaken.LOG.warn("[requests] skipped {}", b));
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} Anchor requests", ok.size());
        }
    }
}
