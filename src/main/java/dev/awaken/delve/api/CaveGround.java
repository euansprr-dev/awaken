package dev.awaken.delve.api;

import dev.awaken.delve.cave.CaveCuts;
import net.minecraft.server.level.ServerLevel;

/**
 * The land the caves cut (SPEC §12, §34.1) for other modules: whether a cave place that opens at the surface (a mouth, a
 * sinkhole, a mine's adit) reaches into a box. Cave places are cut at {@code underground_structures}, before anything at
 * the surface is written, so a thing set on the generator's own ground there hangs over the hole: ask this before choosing
 * the spot. Answered from the world seed (each nearby start's own site search, nothing generated), the same from any chunk
 * and any thread; the first ask round new land runs a few dozen noise columns per start. Overworld only.
 */
public final class CaveGround {
    private CaveGround() {}

    /** Whether a cave place open to the sky has its box over any of (x0..x1, y0..y1, z0..z1). */
    public static boolean cuts(ServerLevel level, int x0, int z0, int x1, int z1, int y0, int y1) {
        return CaveCuts.crosses(level, x0, z0, x1, z1, y0, y1);
    }
}
