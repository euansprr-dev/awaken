package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.api.Promises;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The locks a player has met and not opened yet (SPEC §27, {@link Promises}): saved with the player, kept through death,
 * synced to its owner (the band's "What waits" list and the Atlas's pins read it). At most {@link #MAX}; the oldest go.
 */
public final class PromiseBook {
    public static final int SCHEMA = 1;
    public static final int MAX = 64;

    /**
     * One lock: its key (one per place: "door:12,64,-30"), the dimension and place (none for a lock with no place), its
     * need, what it is ("A sealed vault"), and since when it answers the player (0: it doesn't yet).
     */
    public record Entry(String key, String dim, Optional<BlockPos> where, String kind, String value, String what, long answeredAt) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Entry::key),
                Codec.STRING.optionalFieldOf("dim", "minecraft:overworld").forGetter(Entry::dim),
                BlockPos.CODEC.optionalFieldOf("where").forGetter(Entry::where),
                Codec.STRING.fieldOf("kind").forGetter(Entry::kind),
                Codec.STRING.fieldOf("value").forGetter(Entry::value),
                Codec.STRING.fieldOf("what").forGetter(Entry::what),
                Codec.LONG.optionalFieldOf("answered", 0L).forGetter(Entry::answeredAt)
        ).apply(i, Entry::new));

        public Promises.Need need() {
            return new Promises.Need(kind, value);
        }

        public boolean answered() {
            return answeredAt != 0;
        }

        Entry answer(long at) {
            return new Entry(key, dim, where, kind, value, what, at);
        }
    }

    final List<Entry> entries = new ArrayList<>();

    public static final MapCodec<PromiseBook> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(b -> SCHEMA),
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(b -> b.entries)
    ).apply(i, (schema, entries) -> {
        PromiseBook b = new PromiseBook();
        b.entries.addAll(entries);
        return b;
    }));

    public static final StreamCodec<ByteBuf, PromiseBook> STREAM_CODEC = ByteBufCodecs.fromCodec(MAP_CODEC.codec());

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public @Nullable Entry get(String key) {
        for (Entry e : entries) if (e.key().equals(key)) return e;
        return null;
    }

    void add(Entry e) {
        entries.add(e);
        while (entries.size() > MAX) entries.removeFirst();
    }

    boolean remove(String key) {
        return entries.removeIf(e -> e.key().equals(key));
    }

    boolean removeAll(String prefix) {
        return entries.removeIf(e -> e.key().startsWith(prefix));
    }

    void set(int i, Entry e) {
        entries.set(i, e);
    }
}
