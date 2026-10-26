package dev.awaken.village.world;

import dev.awaken.Awaken;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The settlements' plans on this server, made once each and kept (SPEC §28.6): by the structure start at world
 * generation, again after a restart by whichever chunk paints first, and by the people engine when someone comes near.
 * A plan is a pure function of its anchor and the land, so every one of those makes the same plan. Safe from any
 * thread; cleared when the server stops.
 */
public final class Plans {
    private Plans() {}

    private static final ConcurrentHashMap<String, Optional<VillagePlan>> CACHE = new ConcurrentHashMap<>();
    static long planNanos, plans;

    /** The structure's reach round its start chunk (references are made eight chunks out), less a margin for what hangs off walls. */
    public static VillagePlanner.Request request(SettlementGrid.Anchor a) {
        int cx = a.x() >> 4, cz = a.z() >> 4;
        return new VillagePlanner.Request(a.key(), a.seed(), a.x(), a.z(), a.kind(), a.first(), ((cx - 8) << 4) + 4, ((cz - 8) << 4) + 4,
                ((cx + 8) << 4) + 11, ((cz + 8) << 4) + 11, (x, z) -> CityBasin.reservedWithin(x, z, 8));
    }

    /** The plan for an anchor (made now if no one has yet), or null where no site suits. */
    public static @Nullable VillagePlan of(SettlementGrid.Anchor a, Terrain t) {
        return CACHE.computeIfAbsent(a.key(), k -> {
            long t0 = System.nanoTime();
            VillagePlan p = VillagePlanner.plan(request(a), t);
            long ns = System.nanoTime() - t0;
            planNanos += ns;
            plans++;
            if (p != null) Awaken.LOG.info("[village] planned {} in {} ms", p, String.format(java.util.Locale.ROOT, "%.1f", ns / 1e6));
            else Awaken.LOG.info("[village] no site near {} {} for {} ({} ms)", a.x(), a.z(), a.key(), String.format(java.util.Locale.ROOT, "%.1f", ns / 1e6));
            return Optional.ofNullable(p);
        }).orElse(null);
    }

    /** A plan already made (or null), without making one. */
    public static @Nullable VillagePlan cached(String key) {
        Optional<VillagePlan> p = CACHE.get(key);
        return p == null ? null : p.orElse(null);
    }

    public static boolean known(String key) {
        return CACHE.containsKey(key);
    }

    public static void clear() {
        CACHE.clear();
        planNanos = 0;
        plans = 0;
    }

    /** Tests and commands: a plan made elsewhere under its key. */
    public static void put(VillagePlan p) {
        CACHE.put(p.key, Optional.of(p));
    }

    public static String stats() {
        return String.format(java.util.Locale.ROOT, "%d plans made, %.1f ms each on average", plans, plans == 0 ? 0 : planNanos / 1e6 / plans);
    }
}
