package dev.awaken.city;

import dev.awaken.Awaken;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * Whether this server's world has Carillon, and where. A world has it when it carries the feature flag
 * {@code awaken:carillon} (the built-in Carillon data pack asks for it; new worlds get it unless the player switches the
 * pack off on the Data Packs screen, "Classic start"; worlds made before it never have it) and terrain found a site.
 * Decided at {@link ServerAboutToStartEvent}, after terrain picks the site and before any chunk generates.
 */
public final class City {
    private City() {}

    public static final Identifier FLAG = Awaken.id("carillon");
    static final CityStats STATS = new CityStats();
    private static volatile @Nullable CityLayout layout;

    /** The city in this world, or null (classic worlds, old worlds, other dimensions' servers). */
    public static @Nullable CityLayout layout() {
        return layout;
    }

    static void init() {
        CityBasin.request(City::flagged);
        // after terrain (which picks the site at the default priority)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, City::aboutToStart);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> layout = null);
    }

    /** The world asked for Carillon (its feature flags include awaken:carillon). */
    public static boolean flagged(MinecraftServer server) {
        FeatureFlagSet set = FeatureFlags.REGISTRY.fromNames(Set.of(FLAG), id -> {});
        return !set.isEmpty() && set.isSubsetOf(server.getWorldData().enabledFeatures());
    }

    private static void aboutToStart(ServerAboutToStartEvent e) {
        layout = null;
        STATS.clear();
        MinecraftServer server = e.getServer();
        if (!flagged(server)) return;
        Optional<CitySite> site = CityBasin.site(server);
        if (site.isEmpty()) {
            Awaken.LOG.info("[city] this world asks for Carillon but terrain found no site (no noise overworld): no city");
            return;
        }
        CityPlan plan = CityPlan.get();
        CityLayout l = new CityLayout(plan, site.get());
        layout = l;
        Awaken.LOG.info("[city] Carillon stands at {} {} (street y {}, basin {}), {} chunks within {} blocks", l.cx, l.cz,
                l.streetY, l.carved ? "carved by terrain" : "flattened by the city", l.chunks.length, l.reach);
    }

    /** Tests: put a city somewhere (or null), returning what was there. */
    static @Nullable CityLayout install(@Nullable CityLayout l) {
        CityLayout before = layout;
        layout = l;
        return before;
    }

    /** Where a new player starts: on the Runners' Roost, facing the spire. */
    public static BlockPos start(CityLayout l) {
        CityPlan p = l.plan;
        return new BlockPos(l.cx + p.startX, l.streetY + p.startRy, l.cz + p.startZ);
    }

    /** The middle of Choir Square, one above the paving (the floor of its bowl, by the Bell Stone). */
    public static BlockPos square(CityLayout l) {
        CityPlan p = l.plan;
        return new BlockPos(l.cx + p.squareX, l.streetY + p.lift(p.squareX, p.squareZ) + 1, l.cz + p.squareZ);
    }

    private static final ThreadLocal<CityBuffer> SKYLINE = ThreadLocal.withInitial(() -> new CityBuffer(16, 16));

    /**
     * The city as planned over chunk (chunkX, chunkZ), from the plan alone (no chunk needed, any thread): for each column
     * (index {@code z * 16 + x}) the world y of the highest block the city places there in {@code topY}, and that block in
     * {@code top}; {@link Integer#MIN_VALUE} and null where the city leaves the column to the land. False where the city
     * doesn't reach. Far Sight's far skyline (SPEC §20) reads it for city chunks it has never seen.
     */
    public static boolean skyline(CityLayout l, int chunkX, int chunkZ, long worldSeed, int[] topY, BlockState[] top) {
        if (!l.touches(chunkX, chunkZ)) return false;
        CityBuffer buf = SKYLINE.get();
        CityPainter.paintChunk(l, chunkX, chunkZ, worldSeed, buf);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                topY[i] = Integer.MIN_VALUE;
                top[i] = null;
                for (int ry = CityBuffer.YMAX; ry >= CityBuffer.YMIN; ry--) {
                    BlockState s = buf.cells[buf.index(lx, ry, lz)];
                    if (s == null || s.isAir()) continue;
                    topY[i] = l.streetY + ry;
                    top[i] = s;
                    break;
                }
            }
        }
        return true;
    }

    /**
     * Spots named after cities were already built (plan x, ry, z, as in the plan's own). They live here, not in plan.json:
     * the plan's hash covers its spots, and a world whose city was built from another hash gets no citizens (SPEC §43.1).
     */
    private static final java.util.Map<String, int[]> LATER_SPOTS = java.util.Map.of(
            "hettie", new int[]{-18, -1, -4});   // the market square (its floor is 2 below the street) by the cloth and produce stalls

    /** A named spot the plan lays out for other modules (SPEC §16.9, decision 29), or null if the plan has none. */
    public static @Nullable BlockPos spot(CityLayout l, String id) {
        int[] s = l.plan.spots.get(id);
        if (s == null) s = LATER_SPOTS.get(id);
        return s == null ? null : new BlockPos(l.cx + s[0], l.streetY + s[1], l.cz + s[2]);
    }
}
