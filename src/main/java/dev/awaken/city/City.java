package dev.awaken.city;

import dev.awaken.Awaken;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
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

    /** The middle of Choir Square, one above the paving. */
    public static BlockPos square(CityLayout l) {
        return new BlockPos(l.cx + l.plan.squareX, l.streetY + 1, l.cz + l.plan.squareZ);
    }
}
