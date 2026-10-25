package dev.awaken.living.pulse;

import dev.awaken.Awaken;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** The kinds of happening, from {@code data/<ns>/awaken/happening/*.json} (SPEC §21.6). One with no known behaviour or tell is skipped. */
public final class HappeningBook {
    private HappeningBook() {}

    private static volatile Map<String, HappeningDef> defs = Map.of();

    public static Map<String, HappeningDef> all() {
        return defs;
    }

    public static @Nullable HappeningDef get(String id) {
        HappeningDef d = defs.get(id);
        return d != null ? d : defs.get(Awaken.MODID + ":" + id);
    }

    public static void replace(Map<String, HappeningDef> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
    }

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("living_happenings"), new Loader());
    }

    static final class Loader extends SimpleJsonResourceReloadListener<HappeningDef> {
        Loader() {
            super(HappeningDef.CODEC, FileToIdConverter.json("awaken/happening"));
        }

        @Override
        protected void apply(Map<Identifier, HappeningDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            TreeMap<String, HappeningDef> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                if (Behaviours.get(v.behaviour()) == null) Awaken.LOG.warn("[living] happening {} skipped: no behaviour {}", k, v.behaviour());
                else if (!HappeningDef.TELLS.contains(v.tell())) Awaken.LOG.warn("[living] happening {} skipped: no tell {}", k, v.tell());
                else if (v.title().contains("—") || v.rumours().stream().anyMatch(l -> l.contains("—"))) Awaken.LOG.warn("[living] happening {} skipped: an em dash", k);
                else ok.put(k.toString(), v);
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} kinds of happening", ok.size());
        }
    }
}
