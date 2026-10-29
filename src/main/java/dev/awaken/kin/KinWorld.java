package dev.awaken.kin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The kin's world state (schema 1): which wild spots have had their beasts and when (a spot whose beasts are gone fills
 * again after three days), the spots a rumour promised a kind, who lives at each Hearth (so nooks are counted and the
 * Hearth page lists them while their chunks sleep), each Hearth's Ore Shed heart, the lode hearts players set down, and
 * the bells each home's Foundry has cast (an optional field: schema 1 still reads).
 */
public final class KinWorld extends SavedData {
    public static final int SCHEMA = 1;

    /** One beast living at a Hearth. */
    public record Homed(UUID id, KinKind kind, BlockPos nook, long since) {
        static final Codec<Homed> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Homed::id),
                Codec.STRING.xmap(KinKind::of, KinKind::id).fieldOf("kind").forGetter(Homed::kind),
                BlockPos.CODEC.fieldOf("nook").forGetter(Homed::nook),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Homed::since)
        ).apply(i, Homed::new));
    }

    final Map<Long, Long> filled = new HashMap<>();
    final Map<Long, KinKind> promised = new HashMap<>();
    final Map<Long, List<Homed>> homes = new HashMap<>();
    final Map<Long, Long> oreHearts = new HashMap<>();
    /** Lode hearts a player set down ("dimension|pos"): breaking one again gives the heart back, never its ore again. */
    final java.util.Set<String> placedHearts = new java.util.HashSet<>();
    /** The bells each home's Foundry has cast (SPEC §43.12): every member gets them, the ones away when they next come by. */
    final Map<Long, List<String>> cast = new HashMap<>();

    private static final Codec<Map<String, Long>> LONGS = Codec.unboundedMap(Codec.STRING, Codec.LONG);

    public static final Codec<KinWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(w -> SCHEMA),
            LONGS.optionalFieldOf("filled", Map.of()).forGetter(w -> keyed(w.filled)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("promised", Map.of()).forGetter(w -> {
                Map<String, String> m = new HashMap<>();
                w.promised.forEach((k, v) -> m.put(Long.toString(k), v.id()));
                return m;
            }),
            Codec.unboundedMap(Codec.STRING, Homed.CODEC.listOf()).optionalFieldOf("homes", Map.of()).forGetter(w -> {
                Map<String, List<Homed>> m = new HashMap<>();
                w.homes.forEach((k, v) -> m.put(Long.toString(k), List.copyOf(v)));
                return m;
            }),
            LONGS.optionalFieldOf("ore_hearts", Map.of()).forGetter(w -> keyed(w.oreHearts)),
            Codec.STRING.listOf().optionalFieldOf("placed_hearts", List.of()).forGetter(w -> List.copyOf(w.placedHearts)),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("cast", Map.of()).forGetter(w -> {
                Map<String, List<String>> m = new HashMap<>();
                w.cast.forEach((k, v) -> m.put(Long.toString(k), List.copyOf(v)));
                return m;
            })
    ).apply(i, (schema, filled, promised, homes, hearts, placed, cast) -> {
        KinWorld w = new KinWorld();
        filled.forEach((k, v) -> w.filled.put(Long.parseLong(k), v));
        promised.forEach((k, v) -> w.promised.put(Long.parseLong(k), KinKind.of(v)));
        homes.forEach((k, v) -> w.homes.put(Long.parseLong(k), new ArrayList<>(v)));
        hearts.forEach((k, v) -> w.oreHearts.put(Long.parseLong(k), v));
        w.placedHearts.addAll(placed);
        cast.forEach((k, v) -> w.cast.put(Long.parseLong(k), new ArrayList<>(v)));
        return w;
    }));

    private static Map<String, Long> keyed(Map<Long, Long> m) {
        Map<String, Long> out = new HashMap<>();
        m.forEach((k, v) -> out.put(Long.toString(k), v));
        return out;
    }

    public static final SavedDataType<KinWorld> TYPE = new SavedDataType<>(Awaken.id("kin"), KinWorld::new, CODEC);

    public static KinWorld get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Homed> at(BlockPos anchor) {
        return homes.getOrDefault(anchor.asLong(), List.of());
    }

    void home(BlockPos anchor, Homed h) {
        List<Homed> l = homes.computeIfAbsent(anchor.asLong(), k -> new ArrayList<>());
        l.removeIf(x -> x.id().equals(h.id()));
        l.add(h);
        setDirty();
    }

    void leave(BlockPos anchor, UUID id) {
        List<Homed> l = homes.get(anchor.asLong());
        if (l != null && l.removeIf(x -> x.id().equals(id))) setDirty();
    }

    public @org.jspecify.annotations.Nullable BlockPos oreHeart(BlockPos anchor) {
        Long h = oreHearts.get(anchor.asLong());
        return h == null ? null : BlockPos.of(h);
    }

    void oreHeart(BlockPos anchor, @org.jspecify.annotations.Nullable BlockPos heart) {
        if (heart == null) oreHearts.remove(anchor.asLong());
        else oreHearts.put(anchor.asLong(), heart.asLong());
        setDirty();
    }
}
