package dev.awaken.gates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared, saved state of one floor of one gate: which rooms woke up, which are cleared, arena waves, puzzles, gate
 * pressure, flood levels. Keyed in {@link GateRecord} by "area:floor".
 */
public final class FloorState {
    /** Room triggers: room id, or (room id << 8 | zone) for cavern zones. */
    public final Set<Integer> triggered = new LinkedHashSet<>();
    /** Encounters already populated, independently of whether anyone has engaged them. */
    public final Set<Integer> prepared = new LinkedHashSet<>();
    /** Live encounter members, including entities in unloaded chunks. UUID string -> room/zone key. */
    public final Map<String, Integer> living = new HashMap<>();
    public final Set<Integer> clearedRooms = new LinkedHashSet<>();
    /** Arena wave reached per room (1 or 2). */
    public final Map<Integer, Integer> waves = new HashMap<>();
    /** Current rune mask per puzzle room. */
    public final Map<Integer, Integer> runes = new HashMap<>();
    public final Set<Integer> puzzlesSolved = new LinkedHashSet<>();
    public final Set<Integer> lanternsTaken = new LinkedHashSet<>();
    public final Map<Integer, Integer> water = new HashMap<>();
    /** Game time anyone first set foot here, or {@link #NEVER}. */
    public long enteredAt = NEVER;
    public static final long NEVER = Long.MIN_VALUE;

    public boolean entered() {
        return enteredAt != NEVER;
    }

    public int pressureWaves;
    public boolean warden;
    public boolean alarm;
    public boolean shrineOpen;
    public boolean spiritDone;
    public boolean collapsed;
    /** Rooms whose doors are barred right now (arenas in a fight). */
    public final Set<Integer> locked = new LinkedHashSet<>();
    /** The Gate Rig's rooms on this floor: tracks, swapped groups, latches (SPEC §17). */
    public RigState rig = new RigState();

    private record Sets(List<Integer> triggered, List<Integer> cleared, List<Integer> solved, List<Integer> lanterns, List<Integer> locked) {
        static final Codec<Sets> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().fieldOf("triggered").forGetter(Sets::triggered),
                Codec.INT.listOf().fieldOf("cleared").forGetter(Sets::cleared),
                Codec.INT.listOf().fieldOf("solved").forGetter(Sets::solved),
                Codec.INT.listOf().fieldOf("lanterns").forGetter(Sets::lanterns),
                Codec.INT.listOf().optionalFieldOf("locked", List.of()).forGetter(Sets::locked)
        ).apply(i, Sets::new));
    }

    private record Maps(Map<String, Integer> waves, Map<String, Integer> runes, Map<String, Integer> water) {
        static final Codec<Map<String, Integer>> M = Codec.unboundedMap(Codec.STRING, Codec.INT);
        static final Codec<Maps> CODEC = RecordCodecBuilder.create(i -> i.group(
                M.fieldOf("waves").forGetter(Maps::waves),
                M.fieldOf("runes").forGetter(Maps::runes),
                M.fieldOf("water").forGetter(Maps::water)
        ).apply(i, Maps::new));
    }

    private record Flags(long enteredAt, int pressureWaves, boolean warden, boolean alarm, boolean shrineOpen, boolean spiritDone, boolean collapsed) {
        static final Codec<Flags> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("entered").forGetter(Flags::enteredAt),
                Codec.INT.fieldOf("waves").forGetter(Flags::pressureWaves),
                Codec.BOOL.fieldOf("warden").forGetter(Flags::warden),
                Codec.BOOL.fieldOf("alarm").forGetter(Flags::alarm),
                Codec.BOOL.fieldOf("shrine_open").forGetter(Flags::shrineOpen),
                Codec.BOOL.fieldOf("spirit_done").forGetter(Flags::spiritDone),
                Codec.BOOL.optionalFieldOf("collapsed", false).forGetter(Flags::collapsed)
        ).apply(i, Flags::new));
    }

    public static final Codec<FloorState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Sets.CODEC.fieldOf("sets").forGetter(f -> new Sets(List.copyOf(f.triggered), List.copyOf(f.clearedRooms),
                    List.copyOf(f.puzzlesSolved), List.copyOf(f.lanternsTaken), List.copyOf(f.locked))),
            Maps.CODEC.fieldOf("maps").forGetter(f -> new Maps(str(f.waves), str(f.runes), str(f.water))),
            Flags.CODEC.fieldOf("flags").forGetter(f -> new Flags(f.enteredAt, f.pressureWaves, f.warden, f.alarm, f.shrineOpen, f.spiritDone, f.collapsed)),
            RigState.CODEC.optionalFieldOf("rig").forGetter(f -> f.rig.empty() ? java.util.Optional.empty() : java.util.Optional.of(f.rig)),
            Codec.INT.listOf().optionalFieldOf("prepared").forGetter(f -> java.util.Optional.of(List.copyOf(f.prepared))),
            Maps.M.optionalFieldOf("living", Map.of()).forGetter(f -> Map.copyOf(f.living))
    ).apply(i, (s, m, fl, rig, prepared, living) -> {
        FloorState f = new FloorState();
        f.triggered.addAll(s.triggered);
        // Old saves already populated every triggered encounter; never spawn those a second time.
        f.prepared.addAll(prepared.orElse(s.triggered));
        f.living.putAll(living);
        f.clearedRooms.addAll(s.cleared);
        f.puzzlesSolved.addAll(s.solved);
        f.lanternsTaken.addAll(s.lanterns);
        f.locked.addAll(s.locked);
        m.waves.forEach((k, v) -> f.waves.put(Integer.parseInt(k), v));
        m.runes.forEach((k, v) -> f.runes.put(Integer.parseInt(k), v));
        m.water.forEach((k, v) -> f.water.put(Integer.parseInt(k), v));
        f.enteredAt = fl.enteredAt;
        f.pressureWaves = fl.pressureWaves;
        f.warden = fl.warden;
        f.alarm = fl.alarm;
        f.shrineOpen = fl.shrineOpen;
        f.spiritDone = fl.spiritDone;
        f.collapsed = fl.collapsed;
        rig.ifPresent(r -> f.rig = r);
        return f;
    }));

    private static Map<String, Integer> str(Map<Integer, Integer> m) {
        Map<String, Integer> out = new HashMap<>();
        m.forEach((k, v) -> out.put(Integer.toString(k), v));
        return out;
    }
}
