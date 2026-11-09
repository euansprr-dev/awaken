package dev.awaken.gates.realms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Realm cells in use and each standing Realm's state (SPEC §50.7). */
public final class RealmData extends SavedData {
    private int nextCell;
    private final List<Integer> free = new ArrayList<>();
    private final Map<Integer, RealmState> states = new HashMap<>();

    public static final Codec<RealmData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(d -> 1),
            Codec.INT.fieldOf("next_cell").forGetter(d -> d.nextCell),
            Codec.INT.listOf().fieldOf("free").forGetter(d -> List.copyOf(d.free)),
            Codec.unboundedMap(Codec.STRING, RealmState.CODEC).fieldOf("states").forGetter(RealmData::saved)
    ).apply(i, (v, n, f, s) -> {
        RealmData d = new RealmData();
        d.nextCell = n;
        d.free.addAll(f);
        s.forEach((k, st) -> d.states.put(Integer.parseInt(k), st));
        return d;
    }));

    public static final SavedDataType<RealmData> TYPE = new SavedDataType<>(Awaken.id("realms"), RealmData::new, CODEC);

    private Map<String, RealmState> saved() {
        Map<String, RealmState> out = new HashMap<>();
        states.forEach((k, v) -> out.put(Integer.toString(k), v));
        return out;
    }

    public static RealmData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public int allocate() {
        setDirty();
        return free.isEmpty() ? nextCell++ : free.removeFirst();
    }

    /** The cell's chunks are gone (or nothing was ever drawn in it): it can be used again. */
    public void freeCell(int cell) {
        free.add(cell);
        setDirty();
    }

    public RealmState state(int gateId) {
        return states.computeIfAbsent(gateId, k -> {
            setDirty();
            return new RealmState();
        });
    }

    public void forget(int gateId) {
        if (states.remove(gateId) != null) setDirty();
    }
}
