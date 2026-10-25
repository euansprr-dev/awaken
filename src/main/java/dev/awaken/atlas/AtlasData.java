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
    /** 1: dimensions' cell bits, the run's trail, whether the reveal was sent. 2: regions you've been in, your pins. */
    public static final int SCHEMA = 2;
    /** Pins a player may keep (all dimensions together). */
    public static final int PINS_MAX = 12;

    /** A named region of the Living Wilds you've stood in (SPEC §21.2): lettered across the land on the Atlas. */
    public record Region(long id, String name, int heartX, int heartZ, int low, int high) {
        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Region::id),
                Codec.STRING.fieldOf("name").forGetter(Region::name),
                Codec.INT.fieldOf("x").forGetter(Region::heartX),
                Codec.INT.fieldOf("z").forGetter(Region::heartZ),
                Codec.INT.fieldOf("low").forGetter(Region::low),
                Codec.INT.fieldOf("high").forGetter(Region::high)).apply(i, Region::new));
    }

    /** A pin you dropped on the table: your party sees it too. */
    public record Pin(String dim, int x, int y, int z) {
        public static final Codec<Pin> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Pin::dim),
                Codec.INT.fieldOf("x").forGetter(Pin::x),
                Codec.INT.fieldOf("y").forGetter(Pin::y),
                Codec.INT.fieldOf("z").forGetter(Pin::z)).apply(i, Pin::new));
    }
    /** Points kept from the opening run (x, y, z each), about one every 10 ticks of running. */
    public static final int TRAIL_MAX = 400;

    int schema = SCHEMA;
    final Map<String, Explored> dims = new HashMap<>();
    final IntArrayList trail = new IntArrayList();
    boolean runShown;
    /** Overworld regions, in the order found. */
    final java.util.List<Region> regions = new java.util.ArrayList<>();
    final java.util.List<Pin> pins = new java.util.ArrayList<>();

    public static final Codec<AtlasData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 0).forGetter(d -> d.schema),
            Codec.unboundedMap(Codec.STRING, Explored.CODEC).optionalFieldOf("dims", Map.of()).forGetter(d -> d.dims),
            Codec.INT.listOf().optionalFieldOf("trail", List.of()).forGetter(d -> d.trail),
            Codec.BOOL.optionalFieldOf("runShown", false).forGetter(d -> d.runShown),
            Region.CODEC.listOf().optionalFieldOf("regions", List.of()).forGetter(d -> d.regions),
            Pin.CODEC.listOf().optionalFieldOf("pins", List.of()).forGetter(d -> d.pins)
    ).apply(i, (schema, dims, trail, shown, regions, pins) -> {
        AtlasData d = new AtlasData();
        d.dims.putAll(dims);
        d.trail.addAll(trail);
        d.runShown = shown;
        d.regions.addAll(regions);
        d.pins.addAll(pins);
        return d;
    }));

    /** This dimension's record, created on first use. */
    public Explored dim(String dimension) {
        return dims.computeIfAbsent(dimension, k -> new Explored());
    }

    public Explored peek(String dimension) {
        return dims.get(dimension);
    }

    public List<Region> regions() {
        return java.util.Collections.unmodifiableList(regions);
    }

    public List<Pin> pins() {
        return java.util.Collections.unmodifiableList(pins);
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
