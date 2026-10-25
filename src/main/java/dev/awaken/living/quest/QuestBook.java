package dev.awaken.living.quest;

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

/** The Wayfarer chains, loaded from {@code data/<ns>/awaken/quest/*.json} (SPEC §21). A chain with problems is skipped. */
public final class QuestBook {
    private QuestBook() {}

    private static volatile Map<String, QuestDef> defs = Map.of();

    public static Map<String, QuestDef> all() {
        return defs;
    }

    /** A chain by id ({@code awaken:wrens_rope}) or bare path ({@code wrens_rope}). */
    public static @Nullable QuestDef get(String id) {
        QuestDef d = defs.get(id);
        return d != null ? d : defs.get(Awaken.MODID + ":" + id);
    }

    public static void replace(Map<String, QuestDef> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
    }

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("living_quests"), new Loader());
    }

    static final class Loader extends SimpleJsonResourceReloadListener<QuestDef> {
        Loader() {
            super(QuestDef.CODEC, FileToIdConverter.json("awaken/quest"));
        }

        @Override
        protected void apply(Map<Identifier, QuestDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            TreeMap<String, QuestDef> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                var bad = QuestDef.problems(k.toString(), v);
                if (bad.isEmpty()) ok.put(k.toString(), v);
                else bad.forEach((where, why) -> Awaken.LOG.warn("[living] quest {} skipped: {}", where, why));
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} Wayfarer chains", ok.size());
        }
    }
}
