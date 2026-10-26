package dev.awaken.citizens;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every settlement's people as this world keeps them ({@code awaken:citizens}, world-global SavedData, schemaVersion 2,
 * SPEC §28.4): a list of settlements, each under its key and id with its own layout version, sizes (records) and
 * district count, holding its {@link CitizensData}. A schemaVersion 1 file (Carillon's alone, from before W0) reads as
 * settlement 0, "carillon", with nothing lost; it is written back as version 2 at the next save. Entries whose
 * settlement isn't running this session are kept as they are, so a village's state waits for its next registration.
 */
public final class CitizensSave extends SavedData {
    public static final int SCHEMA = 2;

    /** One settlement's saved state. {@code records} and {@code districts} are its sizes when written. */
    static final class Entry {
        static final int LAYOUT = 1;
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(e -> e.key),
                Codec.INT.fieldOf("id").forGetter(e -> e.id),
                Codec.INT.optionalFieldOf("schemaVersion", LAYOUT).forGetter(e -> LAYOUT),
                Codec.INT.optionalFieldOf("records", 0).forGetter(e -> e.data.records()),
                Codec.INT.optionalFieldOf("districts", 0).forGetter(e -> e.data.safety.length),
                CitizensData.CODEC.fieldOf("data").forGetter(e -> e.data)
        ).apply(i, Entry::new));

        final String key;
        final int id;
        final CitizensData data;

        Entry(String key, int id, CitizensData data) {
            this.key = key;
            this.id = id;
            this.data = data;
        }

        private Entry(String key, int id, int layout, int records, int districts, CitizensData data) {
            this(key, id, data);
            if (records != data.records() || districts != data.safety.length) {
                Awaken.LOG.warn("[citizens] settlement {} saved {} records and {} districts but holds {} and {}", key, records, districts,
                        data.records(), data.safety.length);
            }
        }
    }

    private static final Codec<CitizensSave> V2 = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(s -> SCHEMA),
            Entry.CODEC.listOf().fieldOf("settlements").forGetter(s -> s.entries)
    ).apply(i, (schema, entries) -> new CitizensSave(schema, entries)));

    /** Before W0 the file was Carillon's CitizensData itself (schemaVersion 1): it becomes settlement 0. */
    private static final Codec<CitizensSave> V1 = CitizensData.CODEC.xmap(
            d -> new CitizensSave(1, List.of(new Entry(Settlements.CARILLON_KEY, Settlements.CARILLON, d))),
            s -> {
                Entry e = s.find(Settlements.CARILLON_KEY);
                return e != null ? e.data : new CitizensData();
            });

    public static final Codec<CitizensSave> CODEC = Codec.withAlternative(V2, V1);

    public static final SavedDataType<CitizensSave> TYPE = new SavedDataType<>(Awaken.id("citizens"), CitizensSave::new, CODEC);

    final List<Entry> entries = new ArrayList<>();
    /** The schema the file was read from (1: Carillon's file from before W0; it is rewritten as 2). */
    final int readFrom;

    public CitizensSave() {
        readFrom = SCHEMA;
    }

    private CitizensSave(int schema, List<Entry> entries) {
        readFrom = schema;
        for (Entry e : entries) {
            this.entries.add(e);
            e.data.owner = this;
        }
        if (schema < SCHEMA) {
            setDirty(); // written back as version 2
            Awaken.LOG.info("[citizens] a schemaVersion {} save: Carillon's people read as settlement 0", schema);
        }
    }

    public static CitizensSave get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    @Nullable Entry find(String key) {
        for (Entry e : entries) if (e.key.equals(key)) return e;
        return null;
    }

    /** A settlement's saved state, made now (empty: the ledger seats everyone) the first time its key is seen. */
    CitizensData data(String key, int id) {
        Entry e = find(key);
        if (e == null) {
            e = new Entry(key, id, new CitizensData());
            e.data.owner = this;
            entries.add(e);
            setDirty();
        }
        return e.data;
    }

    /** The id a key has here, or the next free one (Carillon is always 0; others from 1, never reused). */
    int idFor(String key) {
        if (key.equals(Settlements.CARILLON_KEY)) return Settlements.CARILLON;
        Entry e = find(key);
        if (e != null) return e.id;
        int next = 1;
        for (Entry o : entries) next = Math.max(next, o.id + 1);
        return next;
    }
}
