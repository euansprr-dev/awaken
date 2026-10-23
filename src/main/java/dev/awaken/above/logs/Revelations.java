package dev.awaken.above.logs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads the Revelation texts the Above fires itself (the Halo). The story module owns Revelations in general; the
 * Above guards each one it fires with the counter {@code awaken:revelation/<name>} so it shows once per player.
 */
public final class Revelations {
    private Revelations() {}

    public record Def(String trigger, String title, String body) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("trigger").forGetter(Def::trigger),
                Codec.STRING.fieldOf("title").forGetter(Def::title),
                Codec.STRING.fieldOf("body").forGetter(Def::body)
        ).apply(i, Def::new));
    }

    private static final Map<String, Def> DEFS = new HashMap<>();

    /** Fires {@code revelation/<name>.json} as a Signal REVELATION once per player. Returns true if shown. */
    public static boolean fire(ServerPlayer player, String name, String fallbackTitle, String fallbackBody) {
        String guard = "awaken:revelation/" + name;
        if (Progression.profile(player).counter(guard) > 0) return false;
        if (dev.awaken.story.Story.revelation(player, name)) {
            // the story owns Revelations: it shows the window and records it in the Codex
            Progression.setCounter(player, guard, 1);
            return true;
        }
        Def d = DEFS.get("awaken:" + name);
        String title = d != null ? d.title() : fallbackTitle;
        String body = d != null ? d.body() : fallbackBody;
        Progression.setCounter(player, guard, 1);
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(title))
                .body(Component.literal(body)).priority(9).ttl(20 * 14).build());
        return true;
    }

    static final class Loader extends SimpleJsonResourceReloadListener<Def> {
        Loader() {
            super(Def.CODEC, FileToIdConverter.json("awaken/revelation"));
        }

        @Override
        protected void apply(Map<Identifier, Def> loaded, ResourceManager manager, ProfilerFiller profiler) {
            DEFS.clear();
            loaded.forEach((k, v) -> DEFS.put(k.toString(), v));
        }
    }
}
