package dev.awaken.tasks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's Tasks page (SPEC §27.13): the step they pinned, the steps they've already seen (a tab with an unseen step
 * shows a dot), and what they picked on a step with choices (the armour set: the card then lists that set's needs).
 * Saved (schemaVersion 1), kept through death, per player.
 */
public final class TaskState {
    public static final int SCHEMA = 1;
    /** Seen keys kept: the oldest go first (a step seen long ago and back again is new again, which is fine). */
    static final int SEEN_MAX = 256;

    String pinned = "";
    final Set<String> seen = new LinkedHashSet<>();
    /** Step key → the choice picked on it. */
    final Map<String, String> chosen = new HashMap<>();

    static TaskState fresh() {
        return new TaskState();
    }

    static final MapCodec<TaskState> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.STRING.optionalFieldOf("pinned", "").forGetter(s -> s.pinned),
            Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(s -> new ArrayList<>(s.seen)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("chosen", Map.of()).forGetter(s -> Map.copyOf(s.chosen))
    ).apply(i, (v, pin, seen, chosen) -> {
        TaskState s = new TaskState();
        s.pinned = pin;
        s.seen.addAll(seen);
        s.chosen.putAll(chosen);
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

    void see(String key) {
        if (!seen.add(key)) return;
        while (seen.size() > SEEN_MAX) seen.remove(seen.iterator().next());
    }
}
