package dev.awaken.farsight.client;

import dev.awaken.Awaken;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.neoforged.fml.ModList;

/**
 * Sodium's own view of this frame (SPEC §20): whether it draws a box's sections, from the occlusion tree it renders with
 * ({@code SodiumWorldRenderer.isBoxVisible}, the public check its entity culling uses; an empty or absent section counts
 * as drawn). City Sight fills in where Sodium has built a section but culls it while it's on screen: its traversal
 * misses a view into an overhang's cave (the basin rim's old undercut showed the sky, with Far Sight off as well). The
 * only class that names Sodium; without Sodium, or on the first error, every box counts as drawn.
 */
final class SodiumSight {
    private SodiumSight() {}

    private static final boolean LOADED = ModList.get().isLoaded("sodium");
    private static boolean broken;

    /** Whether Sodium draws the box this frame (block coordinates; Sodium widens a box by half a block each way). */
    static boolean drawn(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (!LOADED || broken) return true;
        try {
            SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
            return renderer == null || renderer.isBoxVisible(x0, y0, z0, x1, y1, z1);
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            Awaken.LOG.warn("[farsight] Sodium's visibility can't be read ({}): City Sight fills in nowhere", e.toString());
            return true;
        }
    }
}
