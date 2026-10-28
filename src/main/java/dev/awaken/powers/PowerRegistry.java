package dev.awaken.powers;

import dev.awaken.Awaken;
import dev.awaken.powers.catalog.PowerCatalog;
import dev.awaken.progression.Rank;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The static registry of power types ({@code awaken:power_type}). Powers are code, so they are registered once at
 * class load from {@link PowerCatalog} and never change at runtime; ids are stable and saved in loadouts by string.
 */
public final class PowerRegistry {
    private PowerRegistry() {}

    private static final Map<Identifier, PowerType> BY_ID = new LinkedHashMap<>();
    private static final List<PowerType> ORDER = new ArrayList<>();
    private static final Map<PowerType, Integer> INDEX = new java.util.IdentityHashMap<>();

    static void register(PowerType type) {
        if (BY_ID.containsKey(type.id)) throw new IllegalStateException("Duplicate power " + type.id);
        BY_ID.put(type.id, type);
        INDEX.put(type, ORDER.size());
        ORDER.add(type);
    }

    /** A power's place in registration order: both sides build the registry from one jar, so FX events carry it (SPEC 36.4). */
    public static int index(PowerType type) {
        PowerCatalog.bootstrap();
        Integer i = INDEX.get(type);
        return i == null ? -1 : i;
    }

    public static @Nullable PowerType byIndex(int index) {
        PowerCatalog.bootstrap();
        return index >= 0 && index < ORDER.size() ? ORDER.get(index) : null;
    }

    public static @Nullable PowerType get(Identifier id) {
        PowerCatalog.bootstrap();
        return BY_ID.get(id);
    }

    public static @Nullable PowerType get(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier parsed = Identifier.tryParse(id.contains(":") ? id : Awaken.MODID + ":" + id);
        return parsed == null ? null : get(parsed);
    }

    public static Collection<PowerType> all() {
        PowerCatalog.bootstrap();
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Powers a Power Core may roll for this rank: never hidden ones, never passives above the rank. */
    public static List<PowerType> rollable(Rank rank) {
        List<PowerType> out = new ArrayList<>();
        for (PowerType t : all()) {
            if (!t.hidden && t.unlock.ordinal() <= rank.ordinal()) out.add(t);
        }
        return out;
    }

    /**
     * Rolls a power for a core. Powers unlocked at the player's own rank are weighted 3x so cores feel fresh as you
     * climb; older ones still appear (and level up powers you already have).
     */
    public static @Nullable PowerType roll(Rank rank, RandomSource random) {
        List<PowerType> pool = rollable(rank);
        if (pool.isEmpty()) return null;
        int total = 0;
        for (PowerType t : pool) total += weight(t, rank);
        int r = random.nextInt(total);
        for (PowerType t : pool) {
            r -= weight(t, rank);
            if (r < 0) return t;
        }
        return pool.get(pool.size() - 1);
    }

    static int weight(PowerType t, Rank rank) {
        return t.unlock == rank ? 3 : 1;
    }

    /** Variant roll: Standard half the time, the special forms share the rest. */
    public static int rollVariant(PowerType t, RandomSource random) {
        if (t.variants.size() <= 1 || random.nextBoolean()) return 0;
        return 1 + random.nextInt(t.variants.size() - 1);
    }
}
