package dev.awaken.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One player's Atlas on the server (SPEC §16.8): what they explored in each dimension, and the path of their opening run
 * (the Registry reveal draws it). Saved on the player, kept through death. Synced by {@link AtlasNet} in batches, never
 * whole on every change.
 */
public final class AtlasData {
    /** 1: dimensions' cell bits, the run's trail, whether the reveal was sent. */
    public static final int SCHEMA = 1;
    /** Points kept from the opening run (x, y, z each), about one every 10 ticks of running. */
    public static final int TRAIL_MAX = 400;

    int schema = SCHEMA;
    final Map<String, Explored> dims = new HashMap<>();
    final IntArrayList trail = new IntArrayList();
    boolean runShown;

    public static final Codec<AtlasData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 0).forGetter(d -> d.schema),
            Codec.unboundedMap(Codec.STRING, Explored.CODEC).optionalFieldOf("dims", Map.of()).forGetter(d -> d.dims),
            Codec.INT.listOf().optionalFieldOf("trail", List.of()).forGetter(d -> d.trail),
            Codec.BOOL.optionalFieldOf("runShown", false).forGetter(d -> d.runShown)
    ).apply(i, (schema, dims, trail, shown) -> {
        AtlasData d = new AtlasData();
        d.dims.putAll(dims);
        d.trail.addAll(trail);
        d.runShown = shown;
        return d;
    }));

    /** This dimension's record, created on first use. */
    public Explored dim(String dimension) {
        return dims.computeIfAbsent(dimension, k -> new Explored());
    }

    public Explored peek(String dimension) {
        return dims.get(dimension);
    }

    public int[] trail() {
        return trail.toIntArray();
    }

    public int cells() {
        int n = 0;
        for (Explored e : dims.values()) n += e.count();
        return n;
    }
}
