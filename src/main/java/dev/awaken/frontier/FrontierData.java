package dev.awaken.frontier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Far Landing's record (SPEC §67), world-global: where the two moorings stand (the ridge's by Aldo, the landing's
 * far out) and whether each is built; each player's once-marks ("flown", an invitation sent, the homecoming), a flight
 * caught mid-air by a crash (where to put them on their next login) and the last letters, which wait in the hold.
 */
public final class FrontierData extends SavedData {
    static final int SCHEMA = 1;
    /** Letters kept a player (the hold shows the newest). */
    static final int LETTERS_KEPT = 16;

    /** A mooring: the berth's middle at deck height, the way its bow points, and whether its blocks are up. */
    public record Mooring(BlockPos origin, Direction facing, boolean built) {
        static final Codec<Mooring> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("origin").forGetter(Mooring::origin),
                Direction.CODEC.fieldOf("facing").forGetter(Mooring::facing),
                Codec.BOOL.fieldOf("built").forGetter(Mooring::built)).apply(i, Mooring::new));
    }

    /** Where a player caught mid-flight is put: the deck they were flying to. */
    public record Dest(double x, double y, double z, float yaw) {
        static final Codec<Dest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Dest::x), Codec.DOUBLE.fieldOf("y").forGetter(Dest::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Dest::z), Codec.FLOAT.fieldOf("yaw").forGetter(Dest::yaw)).apply(i, Dest::new));
    }

    /** A letter: who wrote, what they said, on which play day. */
    public record Letter(String from, String text, long day) {
        static final Codec<Letter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("from").forGetter(Letter::from), Codec.STRING.fieldOf("text").forGetter(Letter::text),
                Codec.LONG.fieldOf("day").forGetter(Letter::day)).apply(i, Letter::new));
    }

    public static final Codec<FrontierData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(d -> d.schemaVersion),
            Mooring.CODEC.optionalFieldOf("ridge").forGetter(d -> Optional.ofNullable(d.ridge)),
            Mooring.CODEC.optionalFieldOf("landing").forGetter(d -> Optional.ofNullable(d.landing)),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("marks", Map.of()).forGetter(d -> {
                Map<UUID, List<String>> out = new HashMap<>();
                d.marks.forEach((k, v) -> out.put(k, List.copyOf(v)));
                return out;
            }),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Dest.CODEC).optionalFieldOf("pending", Map.of()).forGetter(d -> d.pending),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Letter.CODEC.listOf()).optionalFieldOf("letters", Map.of()).forGetter(d -> d.letters)
    ).apply(i, FrontierData::new));

    public static final SavedDataType<FrontierData> TYPE = new SavedDataType<>(Awaken.id("frontier"), FrontierData::new, CODEC);

    public int schemaVersion = SCHEMA;
    @Nullable Mooring ridge, landing;
    final Map<UUID, Set<String>> marks = new HashMap<>();
    final Map<UUID, Dest> pending = new HashMap<>();
    final Map<UUID, List<Letter>> letters = new HashMap<>();

    public FrontierData() {}

    private FrontierData(int schema, Optional<Mooring> ridge, Optional<Mooring> landing, Map<UUID, List<String>> marks,
                         Map<UUID, Dest> pending, Map<UUID, List<Letter>> letters) {
        this.schemaVersion = schema;
        this.ridge = ridge.orElse(null);
        this.landing = landing.orElse(null);
        marks.forEach((k, v) -> this.marks.put(k, new HashSet<>(v)));
        this.pending.putAll(pending);
        letters.forEach((k, v) -> this.letters.put(k, new ArrayList<>(v)));
    }

    public static FrontierData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Mooring ridge() {
        return ridge;
    }

    public @Nullable Mooring landing() {
        return landing;
    }

    void ridge(Mooring m) {
        ridge = m;
        setDirty();
    }

    void landing(Mooring m) {
        landing = m;
        setDirty();
    }

    public boolean marked(UUID who, String mark) {
        Set<String> s = marks.get(who);
        return s != null && s.contains(mark);
    }

    /** Sets a once-mark: true the first time. */
    public boolean mark(UUID who, String mark) {
        boolean first = marks.computeIfAbsent(who, k -> new HashSet<>()).add(mark);
        if (first) setDirty();
        return first;
    }

    /** The first of this player's marks that starts with {@code prefix} ("peal@12"), or null. */
    @Nullable String markStarting(UUID who, String prefix) {
        Set<String> s = marks.get(who);
        if (s != null) for (String m : s) if (m.startsWith(prefix)) return m;
        return null;
    }

    void unmark(UUID who, String mark) {
        Set<String> s = marks.get(who);
        if (s != null && s.remove(mark)) setDirty();
    }

    void pending(UUID who, @Nullable Dest d) {
        if (d == null) {
            if (pending.remove(who) != null) setDirty();
        } else {
            pending.put(who, d);
            setDirty();
        }
    }

    @Nullable Dest pending(UUID who) {
        return pending.get(who);
    }

    void letter(UUID who, Letter l) {
        List<Letter> list = letters.computeIfAbsent(who, k -> new ArrayList<>());
        list.add(l);
        while (list.size() > LETTERS_KEPT) list.removeFirst();
        setDirty();
    }

    List<Letter> letters(UUID who) {
        return letters.getOrDefault(who, List.of());
    }
}
