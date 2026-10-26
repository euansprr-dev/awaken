package dev.awaken.village.trouble;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.village.api.Troubles;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the world remembers of its villages' troubles ({@code awaken:village_troubles}, schemaVersion 1; SPEC §28.7):
 * the troubles running now; for each village what its troubles left (when the last ended, its next festival, its bell,
 * the lairs and camps cleared for good, the family gone to Carillon, the Hollowed, grey fields, the tokens on doors and
 * the beams of your colour, how it has grown); and who each player brought home (the {@link Troubles} API).
 */
public final class TroubleState extends SavedData {
    static final int SCHEMA = 1;

    /** A village's lasting memory of its troubles (missing fields default: a village never troubled). */
    public static final class Memory {
        long lastEnd, festival;
        /** 0 the bell hangs, 1 stolen, 2 cracked; {@code bellBy} who hung the new one ("" the old one). */
        int bell;
        String bellBy;
        boolean grey;
        final List<String> sealed;
        final List<Integer> left, hollowed;
        final Map<Long, String> tokens;
        final List<Long> beams;
        /** Growth: houses raised since it was founded, and the plot rising now (BlockPos long, or 0) with its progress. */
        int grown;
        final Map<String, Long> data;
        /** After a Burning: the survivors (records) camped by the well, the camp's blocks, the charred houses' smoke (plot ids). */
        final List<Integer> homeless;
        final List<Long> camp;
        final List<Integer> charred;
        /** How it has grown, and the plot rising now (SPEC §28.7, PLAN 6.4). */
        final dev.awaken.village.growth.Growth.State growth;

        Memory(long lastEnd, long festival, int bell, String bellBy, boolean grey, List<String> sealed, List<Integer> left, List<Integer> hollowed,
               Map<Long, String> tokens, List<Long> beams, int grown, Map<String, Long> data, List<Integer> homeless, List<Long> camp,
               List<Integer> charred, dev.awaken.village.growth.Growth.State growth) {
            this.growth = growth;
            this.homeless = new ArrayList<>(homeless);
            this.camp = new ArrayList<>(camp);
            this.charred = new ArrayList<>(charred);
            this.lastEnd = lastEnd;
            this.festival = festival;
            this.bell = bell;
            this.bellBy = bellBy;
            this.grey = grey;
            this.sealed = new ArrayList<>(sealed);
            this.left = new ArrayList<>(left);
            this.hollowed = new ArrayList<>(hollowed);
            this.tokens = new HashMap<>(tokens);
            this.beams = new ArrayList<>(beams);
            this.grown = grown;
            this.data = new HashMap<>(data);
        }

        static Memory fresh() {
            return new Memory(-100, -1, 0, "", false, List.of(), List.of(), List.of(), Map.of(), List.of(), 0, Map.of(), List.of(), List.of(), List.of(),
                    dev.awaken.village.growth.Growth.State.fresh());
        }

        private static final Codec<Map<Long, String>> TOKENS = Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(
                m -> {
                    Map<Long, String> out = new HashMap<>();
                    m.forEach((k, v) -> {
                        try {
                            out.put(Long.parseLong(k), v);
                        } catch (NumberFormatException ignored) {
                            // a key this version can't read: dropped
                        }
                    });
                    return out;
                },
                m -> {
                    Map<String, String> out = new HashMap<>();
                    m.forEach((k, v) -> out.put(Long.toString(k), v));
                    return out;
                });

        static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("last_end", -100L).forGetter(m -> m.lastEnd),
                Codec.LONG.optionalFieldOf("festival", -1L).forGetter(m -> m.festival),
                Codec.INT.optionalFieldOf("bell", 0).forGetter(m -> m.bell),
                Codec.STRING.optionalFieldOf("bell_by", "").forGetter(m -> m.bellBy),
                Codec.BOOL.optionalFieldOf("grey", false).forGetter(m -> m.grey),
                Codec.STRING.listOf().optionalFieldOf("sealed", List.of()).forGetter(m -> m.sealed),
                Codec.INT.listOf().optionalFieldOf("left", List.of()).forGetter(m -> m.left),
                Codec.INT.listOf().optionalFieldOf("hollowed", List.of()).forGetter(m -> m.hollowed),
                TOKENS.optionalFieldOf("tokens", Map.of()).forGetter(m -> m.tokens),
                Codec.LONG.listOf().optionalFieldOf("beams", List.of()).forGetter(m -> m.beams),
                Codec.INT.optionalFieldOf("grown", 0).forGetter(m -> m.grown),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("data", Map.of()).forGetter(m -> m.data),
                Codec.INT.listOf().optionalFieldOf("homeless", List.of()).forGetter(m -> m.homeless),
                Codec.LONG.listOf().optionalFieldOf("camp", List.of()).forGetter(m -> m.camp),
                Codec.INT.listOf().optionalFieldOf("charred", List.of()).forGetter(m -> m.charred),
                dev.awaken.village.growth.Growth.State.CODEC.optionalFieldOf("growth", dev.awaken.village.growth.Growth.State.fresh()).forGetter(m -> m.growth)
        ).apply(i, Memory::new));

        public int bell() {
            return bell;
        }

        public dev.awaken.village.growth.Growth.State growth() {
            return growth;
        }

        public boolean grey() {
            return grey;
        }

        public List<Integer> left() {
            return List.copyOf(left);
        }

        public List<Integer> hollowed() {
            return List.copyOf(hollowed);
        }

        long get(String key, long def) {
            Long v = data.get(key);
            return v == null ? def : v;
        }
    }

    static final Codec<Troubles.Rescued> RESCUED = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("village").forGetter(Troubles.Rescued::village),
            Codec.STRING.optionalFieldOf("village_name", "").forGetter(Troubles.Rescued::villageName),
            Codec.INT.fieldOf("rec").forGetter(Troubles.Rescued::rec),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Troubles.Rescued::name),
            Codec.STRING.optionalFieldOf("trade", "resident").forGetter(Troubles.Rescued::trade),
            Codec.INT.optionalFieldOf("age", 2).forGetter(Troubles.Rescued::age),
            Codec.LONG.optionalFieldOf("day", 0L).forGetter(Troubles.Rescued::day),
            Codec.STRING.optionalFieldOf("trouble", "taken").forGetter(Troubles.Rescued::trouble),
            Codec.BOOL.optionalFieldOf("settled", false).forGetter(Troubles.Rescued::settled)
    ).apply(i, Troubles.Rescued::new));

    public static final Codec<TroubleState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Trouble.CODEC.listOf().optionalFieldOf("troubles", List.of()).forGetter(s -> List.copyOf(s.troubles)),
            Codec.unboundedMap(Codec.STRING, Memory.CODEC).optionalFieldOf("villages", Map.of()).forGetter(s -> s.memory),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, RESCUED.listOf()).optionalFieldOf("rescued", Map.of()).forGetter(s -> s.rescued),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(s -> s.nextId),
            Codec.LONG.listOf().optionalFieldOf("clear", List.of()).forGetter(s -> List.copyOf(s.clear))
    ).apply(i, (schema, ts, mem, res, next, clear) -> new TroubleState(ts, mem, res, next, clear)));

    public static final SavedDataType<TroubleState> TYPE = new SavedDataType<>(Awaken.id("village_troubles"), TroubleState::new, CODEC);

    final List<Trouble> troubles = new ArrayList<>();
    final Map<String, Memory> memory = new LinkedHashMap<>();
    final Map<UUID, List<Troubles.Rescued>> rescued = new HashMap<>();
    int nextId = 1;
    /** Marks a finished trouble laid in chunks that weren't loaded: taken away as they load. */
    final List<Long> clear = new ArrayList<>();

    public TroubleState() {}

    private TroubleState(List<Trouble> ts, Map<String, Memory> mem, Map<UUID, List<Troubles.Rescued>> res, int next, List<Long> clear) {
        troubles.addAll(ts);
        this.clear.addAll(clear);
        memory.putAll(mem);
        res.forEach((k, v) -> rescued.put(k, new ArrayList<>(v)));
        nextId = next;
    }

    public static TroubleState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Trouble> troubles() {
        return List.copyOf(troubles);
    }

    public Memory memory(String village) {
        return memory.computeIfAbsent(village, k -> {
            setDirty();
            return Memory.fresh();
        });
    }

    public @Nullable Memory peek(String village) {
        return memory.get(village);
    }

    /** The trouble a village is in now, if any (at most one: SPEC §28.0 decision 6; a festival counts apart). */
    @Nullable Trouble of(String village) {
        for (Trouble t : troubles) if (t.village.equals(village) && !t.kind.equals(TroubleKind.FESTIVAL.id)) return t;
        return null;
    }

    @Nullable Trouble festival(String village) {
        for (Trouble t : troubles) if (t.village.equals(village) && t.kind.equals(TroubleKind.FESTIVAL.id)) return t;
        return null;
    }

    @Nullable Trouble byId(int id) {
        for (Trouble t : troubles) if (t.id == id) return t;
        return null;
    }

    Trouble add(String village, TroubleKind kind, long day, long now, long due, String cause) {
        Trouble t = new Trouble(nextId++, village, kind.id, 0, day, now, due, cause, 0L, "", -1, -1, List.of(), Map.of(), List.of(), "");
        troubles.add(t);
        setDirty();
        return t;
    }

    void remove(Trouble t) {
        troubles.remove(t);
        setDirty();
    }

    void rescued(UUID player, Troubles.Rescued r) {
        List<Troubles.Rescued> l = rescued.computeIfAbsent(player, k -> new ArrayList<>());
        l.removeIf(x -> x.village().equals(r.village()) && x.rec() == r.rec());
        l.add(r);
        setDirty();
    }
}
