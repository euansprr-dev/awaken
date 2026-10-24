package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where a player's XP came from (SPEC §17 "telemetry"): every grant, after the story-length scale and XP perks, added up
 * by {@link Progression.XpSource}, and world XP split again by its own source key ({@code kill}, {@code ore},
 * {@code hearth_request}...). Pacing is measured from this rather than guessed. XP banked before awakening is paid at
 * Accept and shows as STORY. Saved with the player, kept through death, never synced (read by {@code /awaken xp}).
 */
public final class XpLedger {
    public static final int SCHEMA = 1;

    final EnumMap<Progression.XpSource, Double> bySource = new EnumMap<>(Progression.XpSource.class);
    final TreeMap<String, Double> world = new TreeMap<>();
    long grants;

    public static final MapCodec<XpLedger> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(l -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("sources", Map.of()).forGetter(l -> {
                Map<String, Double> out = new TreeMap<>();
                l.bySource.forEach((k, v) -> out.put(k.name(), v));
                return out;
            }),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("world", Map.of()).forGetter(l -> l.world),
            Codec.LONG.optionalFieldOf("grants", 0L).forGetter(l -> l.grants)
    ).apply(i, (schema, sources, world, grants) -> {
        XpLedger l = new XpLedger();
        sources.forEach((k, v) -> {
            for (Progression.XpSource s : Progression.XpSource.values()) if (s.name().equals(k)) l.bySource.put(s, v);
        });
        l.world.putAll(world);
        l.grants = grants;
        return l;
    }));

    void add(Progression.XpSource source, @Nullable String worldKey, double xp) {
        bySource.merge(source, xp, Double::sum);
        if (worldKey != null) world.merge(worldKey, xp, Double::sum);
        grants++;
    }

    public double total() {
        double t = 0;
        for (double v : bySource.values()) t += v;
        return t;
    }

    public double of(Progression.XpSource source) {
        return bySource.getOrDefault(source, 0.0);
    }

    public double ofWorld(String key) {
        return world.getOrDefault(key, 0.0);
    }

    public Map<Progression.XpSource, Double> bySource() {
        return java.util.Collections.unmodifiableMap(bySource);
    }

    public Map<String, Double> world() {
        return java.util.Collections.unmodifiableMap(world);
    }

    public boolean isEmpty() {
        return grants == 0;
    }
}
