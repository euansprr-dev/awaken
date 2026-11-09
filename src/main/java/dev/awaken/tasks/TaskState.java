package dev.awaken.tasks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's Tasks page (SPEC §27.13): the step they pinned, the steps they've already seen (a tab with an unseen step
 * shows a dot), and what they picked on a step with choices (the armour set: the card then lists that set's needs).
 * Since SPEC §53 also which line of work holds each place on a tab, what was set aside, and which tabs have opened. Saved
 * (schemaVersion 2; a schema 1 save loads with those empty and they fill on the first look), kept through death, per player.
 */
public final class TaskState {
    public static final int SCHEMA = 2;
    /** Seen keys kept: the oldest go first (a step seen long ago and back again is new again, which is fine). */
    static final int SEEN_MAX = 256;
    /** Steps kept set aside: the oldest come back first. */
    static final int ASIDE_MAX = 64;

    String pinned = "";
    final Set<String> seen = new LinkedHashSet<>();
    /** Step key → the choice picked on it. */
    final Map<String, String> chosen = new HashMap<>();
    /** Goal name → the threads in their places, so finishing one row leaves the others where they are. */
    final Map<String, List<String>> held = new HashMap<>();
    /** Step key → the game time it was set aside. */
    final Map<String, Long> aside = new LinkedHashMap<>();
    /** Where a row set aside this session was, so Undo puts it back there. Not saved: Put back later takes the last place. */
    final Map<String, Integer> asidePlace = new HashMap<>();
    /** The goals whose tab has opened: a tab comes when it first has something to do, and stays. */
    final Set<String> tabsSeen = new LinkedHashSet<>();

    static TaskState fresh() {
        return new TaskState();
    }

    static final MapCodec<TaskState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.STRING.optionalFieldOf("pinned", "").forGetter(s -> s.pinned),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(s -> new ArrayList<>(s.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("chosen", Map.of()).forGetter(s -> Map.copyOf(s.chosen)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("held", Map.of()).forGetter(s -> Map.copyOf(s.held)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("aside", Map.of()).forGetter(s -> Map.copyOf(s.aside)),
            Codec.STRING.listOf().optionalFieldOf("tabsSeen", List.of()).forGetter(s -> new ArrayList<>(s.tabsSeen))
    ).apply(i, (v, pin, seen, chosen, held, aside, tabs) -> {
        TaskState s = new TaskState();
        s.pinned = pin;
        s.seen.addAll(seen);
        s.chosen.putAll(chosen);
        s.held.putAll(held);
        aside.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e -> s.aside.put(e.getKey(), e.getValue()));
        s.tabsSeen.addAll(tabs);
        return s;
    }));

    public static TaskState of(ServerPlayer p) {
        return p.getData(TasksContent.STATE);
    }

    void save(ServerPlayer p) {
        p.setData(TasksContent.STATE, this);
    }

    public String pinned() {
        return pinned;
    }

    /** The choice picked on a step, or "". */
    public String chosen(String step) {
        return chosen.getOrDefault(step, "");
    }

    void setAside(String key, long now) {
        aside.remove(key);
        aside.put(key, now);
        while (aside.size() > ASIDE_MAX) aside.remove(aside.keySet().iterator().next());
    }

    void see(String key) {
        if (!seen.add(key)) return;
        while (seen.size() > SEEN_MAX) seen.remove(seen.iterator().next());
    }
}
