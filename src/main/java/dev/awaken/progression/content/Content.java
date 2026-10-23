package dev.awaken.progression.content;

import com.mojang.serialization.Codec;
import dev.awaken.progression.Condition;
import dev.awaken.progression.content.Defs.*;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loaded progression content. Server-side the maps come from datapacks; client-side they are filled from the
 * ContentSync payload so the Status screen can show names and text.
 */
public final class Content {
    private Content() {}

    public static final Store<TruthDef> TRUTHS = new Store<>("truth", TruthDef.CODEC);
    public static final Store<TitleDef> TITLES = new Store<>("title", TitleDef.CODEC);
    public static final Store<LatentDef> LATENTS = new Store<>("latent", LatentDef.CODEC);
    public static final Store<PathDef> PATHS = new Store<>("path", PathDef.CODEC);
    public static final Store<DirectiveDef> DIRECTIVES = new Store<>("directive", DirectiveDef.CODEC);
    public static final Store<DrillDef> DRILLS = new Store<>("drill", DrillDef.CODEC);

    public static final List<Store<?>> ALL = List.of(TRUTHS, TITLES, LATENTS, PATHS, DIRECTIVES, DRILLS);

    /** Counter id -> content ids whose condition reads it. Rebuilt on reload. */
    public static final Map<String, Set<Identifier>> INDEX = new HashMap<>();

    static void rebuildIndex() {
        INDEX.clear();
        TRUTHS.map.forEach((id, d) -> index(id, d.condition()));
        TRUTHS.map.forEach((id, d) -> d.emergent().ifPresent(c -> index(id, c)));
        TITLES.map.forEach((id, d) -> index(id, d.condition()));
        LATENTS.map.forEach((id, d) -> index(id, d.condition()));
    }

    private static void index(Identifier id, Condition c) {
        Set<String> counters = new HashSet<>();
        c.counters(counters);
        for (String k : counters) INDEX.computeIfAbsent(k, x -> new HashSet<>()).add(id);
    }

    public static final class Store<T> {
        public final String folder;
        public final Codec<T> codec;
        public final Map<Identifier, T> map = new HashMap<>();
        private final List<Consumer<Store<T>>> listeners = new java.util.ArrayList<>();

        Store(String folder, Codec<T> codec) {
            this.folder = folder;
            this.codec = codec;
        }

        public T get(Identifier id) {
            return map.get(id);
        }

        public T get(String id) {
            Identifier parsed = Identifier.tryParse(id);
            return parsed == null ? null : map.get(parsed);
        }

        public void onReload(Consumer<Store<T>> l) {
            listeners.add(l);
        }

        public Codec<Map<Identifier, T>> mapCodec() {
            return Codec.unboundedMap(Identifier.CODEC, codec);
        }

        public void replace(Map<Identifier, T> values) {
            map.clear();
            map.putAll(values);
            for (var l : listeners) l.accept(this);
        }

        public Loader loader() {
            return new Loader();
        }

        public final class Loader extends SimpleJsonResourceReloadListener<T> {
            Loader() {
                super(codec, FileToIdConverter.json("awaken/" + folder));
            }

            @Override
            protected void apply(Map<Identifier, T> loaded, ResourceManager manager, ProfilerFiller profiler) {
                replace(loaded);
                rebuildIndex();
                dev.awaken.Awaken.LOG.info("AWAKEN loaded {} {}", loaded.size(), folder);
            }
        }
    }
}
