package dev.awaken.village;

import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.road.RoadNet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Settled land and the roads between are calmer (SPEC §21.10, decision 5): within 24 blocks of a laid road creatures are
 * 6 levels lower (easing to nothing by 60), and round a settlement (its ground and 96 more) they are capped at 10 for a
 * hamlet or a wayhouse, 14 for a village or a mining town, 18 for a town. It is the Hearth's calm given to everyone's
 * home, and it is what makes a road the safe way in.
 */
public final class SettledCalm {
    private SettledCalm() {}

    public static final int ROAD_DROP = 6, ROAD_NEAR = 24, ROAD_FAR = 60, SETTLED_REACH = 96;

    /** Levels off for a spot's nearness to a laid road (0 or negative). */
    public static int roadExtra(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return 0;
        double d = RoadNet.distanceWithin(pos.getX() + 0.5, pos.getZ() + 0.5, ROAD_FAR);
        if (d < 0) return 0;
        return -(int) Math.round(ROAD_DROP * dropShare(d));
    }

    /** The share of the drop at a distance from a road: all of it within {@link #ROAD_NEAR}, none from {@link #ROAD_FAR}. */
    public static double dropShare(double d) {
        return Math.max(0, Math.min(1, (ROAD_FAR - d) / (ROAD_FAR - ROAD_NEAR)));
    }

    /** The highest a creature's land may be round a settlement, or {@link Integer#MAX_VALUE} away from any. */
    public static int cap(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return Integer.MAX_VALUE;
        int cap = Integer.MAX_VALUE;
        for (VillageService.Live l : VillageService.all()) {
            VillagePlan p = l.plan();
            double reach = p.radius() + SETTLED_REACH;
            double dx = pos.getX() - p.ox, dz = pos.getZ() - p.oz;
            if (dx * dx + dz * dz > reach * reach) continue;
            cap = Math.min(cap, capOf(p.kind));
        }
        return cap;
    }

    public static int capOf(VillagePlan.Kind kind) {
        return switch (kind) {
            case HAMLET, WAYHOUSE -> 10;
            case VILLAGE, MINING -> 14;
            case TOWN -> 18;
        };
    }
}
