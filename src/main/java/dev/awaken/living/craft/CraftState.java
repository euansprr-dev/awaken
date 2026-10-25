package dev.awaken.living.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's crafts (attachment {@code awaken:crafts}, schema 1, kept through death, synced to its owner): craft XP per
 * craft (levels follow from it) and the kinds hunted (for Weak Spot).
 */
public final class CraftState {
    public static final int SCHEMA = 1;

    public static final Codec<CraftState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("xp", Map.of()).forGetter(s -> Map.copyOf(s.xp)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("hunted", Map.of()).forGetter(s -> Map.copyOf(s.hunted))
    ).apply(i, (schema, xp, hunted) -> new CraftState(xp, hunted)));

    final Map<String, Double> xp;
    final Map<String, Integer> hunted;

    public CraftState() {
        this(Map.of(), Map.of());
    }

    CraftState(Map<String, Double> xp, Map<String, Integer> hunted) {
        this.xp = new HashMap<>(xp);
        this.hunted = new HashMap<>(hunted);
    }

    public double xp(Craft c) {
        return xp.getOrDefault(c.id, 0.0);
    }

    public int level(Craft c) {
        return Craft.levelFor(xp(c));
    }

    public boolean has(Craft c, String perk) {
        int lv = level(c);
        for (Craft.Perk p : c.perks) if (p.id().equals(perk)) return lv >= p.level();
        return false;
    }

    public int hunted(String type) {
        return hunted.getOrDefault(type, 0);
    }
}
