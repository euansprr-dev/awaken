package dev.awaken.terrain.api;

import java.util.List;

/**
 * Where Carillon stands in this world (docs/SPEC.md §15 "City basin"). The ground inside {@code coreRadius} of the
 * centre is flat at {@code streetY} (the river channel aside) and eases back to the natural terrain by
 * {@code blendRadius}. {@code river} is the channel's centre line in world x, z (north is −z), from the north edge of
 * the blend through the core to the south-west edge. {@code basinCarved} is false when the world can't wrap its terrain
 * (no Lithostitched): then the site is still chosen, but the city flattens its own ground.
 */
public record CitySite(int x, int z, int coreRadius, int blendRadius, int streetY, List<int[]> river, int riverHalfWidth,
                       int riverFloorY, boolean basinCarved) {
    public CitySite {
        river = List.copyOf(river);
    }

    /** Whether (bx, bz) lies within {@code margin} blocks outside the core (structures and features keep out). */
    public boolean within(int bx, int bz, int margin) {
        long dx = bx - x, dz = bz - z;
        long r = coreRadius + margin;
        return dx * dx + dz * dz <= r * r;
    }
}
