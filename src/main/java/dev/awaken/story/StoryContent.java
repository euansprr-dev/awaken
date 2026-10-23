package dev.awaken.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Story content from data/awaken/awaken/{revelation,rival,sovereign,ending,signal_line} (see tools/content/story_content.py). */
public final class StoryContent {
    private StoryContent() {}

    public record Revelation(String trigger, String title, String body) {
        public static final Codec<Revelation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("trigger").forGetter(Revelation::trigger),
                Codec.STRING.fieldOf("title").forGetter(Revelation::title),
                Codec.STRING.fieldOf("body").forGetter(Revelation::body)).apply(i, Revelation::new));
    }

    public record RivalBeat(String rank, String name, List<String> lines) {
        public static final Codec<RivalBeat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("rank").forGetter(RivalBeat::rank),
                Codec.STRING.fieldOf("name").forGetter(RivalBeat::name),
                Codec.STRING.listOf().fieldOf("lines").forGetter(RivalBeat::lines)).apply(i, RivalBeat::new));
    }

    public record Sovereign(String boss, String name, String title, String intro, String defeat) {
        public static final Codec<Sovereign> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("boss").forGetter(Sovereign::boss),
                Codec.STRING.fieldOf("name").forGetter(Sovereign::name),
                Codec.STRING.fieldOf("title").forGetter(Sovereign::title),
                Codec.STRING.fieldOf("intro").forGetter(Sovereign::intro),
                Codec.STRING.fieldOf("defeat").forGetter(Sovereign::defeat)).apply(i, Sovereign::new));
    }

    public record Ending(String title, List<String> lines, String effect) {
        public static final Codec<Ending> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("title").forGetter(Ending::title),
                Codec.STRING.listOf().fieldOf("lines").forGetter(Ending::lines),
                Codec.STRING.fieldOf("effect").forGetter(Ending::effect)).apply(i, Ending::new));
    }

    public static final Map<Identifier, Revelation> REVELATIONS = new HashMap<>();
    public static final Map<Identifier, RivalBeat> RIVAL = new HashMap<>();
    public static final Map<Identifier, Sovereign> SOVEREIGNS = new HashMap<>();
    public static final Map<Identifier, Ending> ENDINGS = new HashMap<>();
    public static final Map<String, String> SIGNAL_LINES = new HashMap<>();

    private static <T> SimpleJsonResourceReloadListener<T> loader(String folder, Codec<T> codec, Map<Identifier, T> into) {
        return new SimpleJsonResourceReloadListener<>(codec, FileToIdConverter.json("awaken/" + folder)) {
            @Override
            protected void apply(Map<Identifier, T> loaded, ResourceManager manager, ProfilerFiller profiler) {
                into.clear();
                into.putAll(loaded);
            }
        };
    }

    static void addListeners(AddServerReloadListenersEvent event) {
        event.addListener(Awaken.id("story_revelation"), loader("revelation", Revelation.CODEC, REVELATIONS));
        event.addListener(Awaken.id("story_rival"), loader("rival", RivalBeat.CODEC, RIVAL));
        event.addListener(Awaken.id("story_sovereign"), loader("sovereign", Sovereign.CODEC, SOVEREIGNS));
        event.addListener(Awaken.id("story_ending"), loader("ending", Ending.CODEC, ENDINGS));
        Map<Identifier, Map<String, String>> lines = new HashMap<>();
        event.addListener(Awaken.id("story_signal_lines"), new SimpleJsonResourceReloadListener<>(
                Codec.unboundedMap(Codec.STRING, Codec.STRING), FileToIdConverter.json("awaken/signal_line")) {
            @Override
            protected void apply(Map<Identifier, Map<String, String>> loaded, ResourceManager manager, ProfilerFiller profiler) {
                SIGNAL_LINES.clear();
                loaded.values().forEach(SIGNAL_LINES::putAll);
            }
        });
    }

    public static Revelation revelation(String key) {
        return REVELATIONS.get(Awaken.id(key));
    }

    public static String line(String key, String fallback) {
        return SIGNAL_LINES.getOrDefault(key, fallback);
    }
}
