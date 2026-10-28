package dev.awaken.core.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Objects;


/** Meaningful events, authored by their owning module. Signal saves and presents them in the existing Journal. */
public final class NoticeEvents {
    private NoticeEvents() {}
    public enum Kind {
        PERMANENT("Permanent change"), COMBAT("Temporary combat effect"), DISCOVERY("Discovery"), REWARD("Reward"), TASK("Task");
        public final String label;
        Kind(String label) { this.label = label; }
    }

    /** A stable event id deduplicates repeat delivery. A new reward occurrence must have a new id. */
    public record Event(String id, String category, String name, String summary, List<String> details, Kind kind, int colour) {
        public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Event::id), Codec.STRING.fieldOf("category").forGetter(Event::category),
                Codec.STRING.fieldOf("name").forGetter(Event::name), Codec.STRING.fieldOf("summary").forGetter(Event::summary),
                Codec.STRING.listOf().fieldOf("details").forGetter(Event::details),
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Event::kind),
                Codec.INT.fieldOf("colour").forGetter(Event::colour)
        ).apply(i, Event::new));
        public Event {
            Objects.requireNonNull(kind);
            if (id.isBlank() || id.length() > 160 || category.length() > 48 || name.length() > 160 || summary.length() > 320
                    || details.size() > 16 || details.stream().anyMatch(s -> s.length() > 1200)) throw new IllegalArgumentException("Unbounded notice event");
            int bytes = id.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + category.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + summary.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            for (String detail : details) bytes += detail.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > 4096) throw new IllegalArgumentException("Notice event exceeds byte budget");
            details = List.copyOf(details);
        }
        public String title() { return name + " · " + category; }
    }

    public interface Sink { void record(ServerPlayer player, Event event, boolean announce); }
    private static Sink sink = (p, e, announce) -> {};
    public static void install(Sink server) { sink = Objects.requireNonNull(server); }
    public static void tell(ServerPlayer player, Event event) { sink.record(player, event, true); }
    /** For a reward already explained by its owning scene or a related event. Still visible and unread in Journal. */
    public static void remember(ServerPlayer player, Event event) { sink.record(player, event, false); }
}
