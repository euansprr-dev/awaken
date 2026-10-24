package dev.awaken.gates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Gate Rig's saved state for one floor (SPEC §17 "Gate Rig"), kept in {@link FloorState}: for each rig room, the
 * tracks running (when they started, the last tick played), the groups in their active state, the one-time triggers
 * already fired and when the others may fire again; and the Collapse Timer's cave-in waves. Everything else (where
 * blocks are, what they become) follows from the gate's plan, so a relog or a restart picks up exactly here.
 * Schema 1.
 */
public final class RigState {
    public static final int SCHEMA = 1;

    public static final class Room {
        public final Map<Integer, Long> started = new HashMap<>();
        public final Map<Integer, Integer> age = new HashMap<>();
        public final Set<Integer> applied = new LinkedHashSet<>();
        public final Set<Integer> latched = new LinkedHashSet<>();
        public final Map<Integer, Long> ready = new HashMap<>();

        public boolean idle() {
            return started.isEmpty() && applied.isEmpty();
        }

        private record Data(Map<String, Long> started, Map<String, Integer> age, List<Integer> applied, List<Integer> latched,
                            Map<String, Long> ready) {
            static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("started").forGetter(Data::started),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("age").forGetter(Data::age),
                    Codec.INT.listOf().fieldOf("applied").forGetter(Data::applied),
                    Codec.INT.listOf().fieldOf("latched").forGetter(Data::latched),
                    Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ready", Map.of()).forGetter(Data::ready)
            ).apply(i, Data::new));
        }

        static final Codec<Room> CODEC = Data.CODEC.xmap(d -> {
            Room r = new Room();
            d.started.forEach((k, v) -> r.started.put(Integer.parseInt(k), v));
            d.age.forEach((k, v) -> r.age.put(Integer.parseInt(k), v));
            r.applied.addAll(d.applied);
            r.latched.addAll(d.latched);
            d.ready.forEach((k, v) -> r.ready.put(Integer.parseInt(k), v));
            return r;
        }, r -> new Data(keys(r.started), keys(r.age), List.copyOf(r.applied), List.copyOf(r.latched), keys(r.ready)));
    }

    /** Rig rooms by room id; a room is only here once something happened in it. */
    public final Map<Integer, Room> rooms = new HashMap<>();
    /** Collapse Timer: how many waves of the ceiling have come down on this floor. */
    public int caveWaves;

    public Room room(int id) {
        return rooms.computeIfAbsent(id, k -> new Room());
    }

    public boolean empty() {
        return rooms.isEmpty() && caveWaves == 0;
    }

    private record Data(int schema, Map<String, Room> rooms, int cave) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(Data::schema),
                Codec.unboundedMap(Codec.STRING, Room.CODEC).fieldOf("rooms").forGetter(Data::rooms),
                Codec.INT.optionalFieldOf("cave_waves", 0).forGetter(Data::cave)
        ).apply(i, Data::new));
    }

    public static final Codec<RigState> CODEC = Data.CODEC.xmap(d -> {
        RigState s = new RigState();
        d.rooms.forEach((k, v) -> s.rooms.put(Integer.parseInt(k), v));
        s.caveWaves = d.cave;
        return s;
    }, s -> new Data(SCHEMA, keys(s.rooms), s.caveWaves));

    private static <V> Map<String, V> keys(Map<Integer, V> m) {
        Map<String, V> out = new HashMap<>();
        m.forEach((k, v) -> out.put(Integer.toString(k), v));
        return out;
    }
}
