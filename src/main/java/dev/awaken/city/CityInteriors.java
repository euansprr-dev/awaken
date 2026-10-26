package dev.awaken.city;

import dev.awaken.city.CityPlan.Plot;
import dev.awaken.city.SettlementPaint.Interior;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;

/**
 * The room filler (SPEC §28.6, PLAN 5.3): furnishes an enterable house from who lives there, inside the shell the
 * grammar built (ground storey rows 1-4 under the floor at row 5; each upper storey rows 1-3 of its own over its floor).
 * Generic over plots, so Carillon's B4 can adopt it for its own enterable houses; settlements call it today. Pure and
 * clipped like every painter: the same plot and seed always give the same rooms.
 */
final class CityInteriors {
    private CityInteriors() {}

    static void furnish(CityPainter.Ctx c, CityBuffer b, Plot p, Interior who) {
        int g = p.lift();
        int x0 = p.x0() + 1, x1 = p.x1() - 1, z0 = p.z0() + 1, z1 = p.z1() - 1;
        if (x1 - x0 < 1 || z1 - z0 < 1) return;
        VillageStyles.Palette pal = VillageStyles.isVillage(p) ? VillageStyles.palette(p) : null;
        BlockState floor = pal != null ? pal.floor() : FLOOR;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) b.set(x, g, z, floor);
        // a lantern from the ceiling's middle: the room's one light (decision 26's budget: one a house)
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        b.set(mx, g + 4, mz, LANTERN_HANGING);
    }
}
