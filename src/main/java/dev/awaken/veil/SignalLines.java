package dev.awaken.veil;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Data loaders the Veil reads: the Signal's lines and the spirits of the dead. */
public final class SignalLines {
    private SignalLines() {}

    private static final Map<String, String> LINES = new HashMap<>();
    /** Spirit definitions by id ("awaken:iri_the_cartographer"), in a stable order. */
    static final Map<String, SpiritDef> SPIRITS = new LinkedHashMap<>();

    public static String get(String key, String fallback) {
        return LINES.getOrDefault(key, fallback);
    }

    public static Map<String, SpiritDef> spirits() {
        return SPIRITS;
    }

    static void onReload(AddServerReloadListenersEvent event) {
        event.addListener(Awaken.id("veil_signal_lines"), new LinesLoader());
        event.addListener(Awaken.id("veil_spirits"), new SpiritLoader());
    }

    private static final class LinesLoader extends SimpleJsonResourceReloadListener<Map<String, String>> {
        LinesLoader() {
            super(Codec.unboundedMap(Codec.STRING, Codec.STRING), FileToIdConverter.json("awaken/signal_line"));
        }

        @Override
        protected void apply(Map<Identifier, Map<String, String>> loaded, ResourceManager manager, ProfilerFiller profiler) {
            LINES.clear();
            loaded.values().forEach(LINES::putAll);
        }
    }

    private static final class SpiritLoader extends SimpleJsonResourceReloadListener<SpiritDef> {
        SpiritLoader() {
            super(SpiritDef.CODEC, FileToIdConverter.json("awaken/spirit"));
        }

        @Override
        protected void apply(Map<Identifier, SpiritDef> loaded, ResourceManager manager, ProfilerFiller profiler) {
            SPIRITS.clear();
            loaded.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> SPIRITS.put(e.getKey().toString(), e.getValue()));
            Spirits.rebuildIndex();
            Awaken.LOG.info("AWAKEN loaded {} spirits", SPIRITS.size());
        }
    }
}
