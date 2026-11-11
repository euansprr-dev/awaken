package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The task card's rows this player has seen ticked (SPEC §27.14; Euan 2026-11-11: "tasks are never removed, and a task can
 * never not be completed if it actually is completed"). A row once sent done under a task stays done, whatever a later
 * check finds; {@link Checklist} applies it to every card, the Tab glance's with it. Keyed by the task's id and the row's
 * words. Saved (schema 1), kept through death, never synced. At most {@link #MAX}; the oldest go first.
 */
public final class Ticked {
    public static final int SCHEMA = 1;
    static final int MAX = 1024;

    final Set<String> rows = new LinkedHashSet<>();

    public static final MapCodec<Ticked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(t -> SCHEMA),
            Codec.STRING.listOf().optionalFieldOf("rows", List.of()).forGetter(t -> new ArrayList<>(t.rows))
    ).apply(i, (schema, rows) -> {
        Ticked t = new Ticked();
        t.rows.addAll(rows);
        return t;
    }));

    static String key(String task, String row) {
        return task + "|" + row;
    }

    boolean has(String key) {
        return rows.contains(key);
    }

    /** Remembers a ticked row; true when it is new (the attachment wants saving). */
    boolean add(String key) {
        if (!rows.add(key)) return false;
        while (rows.size() > MAX) rows.remove(rows.iterator().next());
        return true;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }
}
