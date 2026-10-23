package dev.awaken.world.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every lair the world has generated and loaded at least once (overworld saved data {@code awaken:fauna_lairs}): the
 * structure start's chunk (the key), whose lair it is, the den, the beast living there now and when the last one
 * fell. Lairs are learnt as their chunks load ({@code ChunkEvent.Load}), never searched for.
 */
public final class LairRegistry extends SavedData {
    public static final int SCHEMA = 1;

    public record Entry(long key, String beast, BlockPos den, Optional<UUID> resident, long felledAt) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("key").forGetter(Entry::key),
                Codec.STRING.fieldOf("beast").forGetter(Entry::beast),
                BlockPos.CODEC.fieldOf("den").forGetter(Entry::den),
                UUIDUtil.CODEC.optionalFieldOf("resident").forGetter(Entry::resident),
                Codec.LONG.optionalFieldOf("felled_at", 0L).forGetter(Entry::felledAt)
        ).apply(i, Entry::new));

        Entry withResident(@Nullable UUID id) {
            return new Entry(key, beast, den, Optional.ofNullable(id), felledAt);
        }

        Entry felled(long now) {
            return new Entry(key, beast, den, Optional.empty(), now);
        }
    }

    public static final Codec<LairRegistry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(r -> SCHEMA),
            Entry.CODEC.listOf().optionalFieldOf("lairs", List.of()).forGetter(r -> List.copyOf(r.byKey.values()))
    ).apply(i, LairRegistry::new));

    public static final SavedDataType<LairRegistry> TYPE = new SavedDataType<>(Awaken.id("fauna_lairs"), LairRegistry::new, CODEC);

    private final Map<Long, Entry> byKey = new LinkedHashMap<>();

    public LairRegistry() {}

    private LairRegistry(int schema, List<Entry> list) {
        for (Entry e : list) byKey.put(e.key(), e);
    }

    public static LairRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** A lair's chunk loaded: remember it (true the first time). */
    public boolean add(long key, String beast, BlockPos den) {
        if (byKey.containsKey(key)) return false;
        byKey.put(key, new Entry(key, beast, den.immutable(), Optional.empty(), 0L));
        setDirty();
        return true;
    }

    public @Nullable Entry get(long key) {
        return byKey.get(key);
    }

    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(byKey.values());
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    public void resident(long key, UUID id) {
        Entry e = byKey.get(key);
        if (e == null) return;
        byKey.put(key, e.withResident(id));
        setDirty();
    }

    /** The lair whose beast this is, if any. */
    public @Nullable Entry ofBeast(UUID id) {
        for (Entry e : byKey.values()) if (e.resident().isPresent() && e.resident().get().equals(id)) return e;
        return null;
    }

    /** Its beast fell (or was taken away): the lair is quiet until the respawn time has passed. */
    public @Nullable Entry felled(UUID id, long now) {
        Entry e = ofBeast(id);
        if (e == null) return null;
        Entry f = e.felled(now);
        byKey.put(e.key(), f);
        setDirty();
        return f;
    }

    /** The nearest lair of {@code beast} (any beast when null) within {@code radius} blocks (horizontal). */
    public @Nullable Entry nearest(@Nullable String beast, BlockPos pos, double radius) {
        Entry best = null;
        double bestD = radius * radius;
        for (Entry e : byKey.values()) {
            if (beast != null && !beast.equals(e.beast())) continue;
            double dx = e.den().getX() - pos.getX(), dz = e.den().getZ() - pos.getZ();
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Admin and tests: every lair waits for a new beast again. */
    public void resetFelled() {
        byKey.replaceAll((k, e) -> new Entry(k, e.beast(), e.den(), e.resident(), 0L));
        setDirty();
    }

    /** Tests: forget a lair. */
    public void remove(long key) {
        if (byKey.remove(key) != null) setDirty();
    }
}
