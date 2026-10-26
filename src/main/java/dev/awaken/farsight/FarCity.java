package dev.awaken.farsight;

import dev.awaken.city.City;
import dev.awaken.city.CityLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * The city on the far horizon (SPEC §20): walls, towers, roofs and spires are a few blocks across, so far corners take
 * the highest built cell under them rather than the average, or a ring wall would melt into the ground a few hundred
 * blocks out. Cells the city builds on carry {@link #BUILT} in their colour's top byte: real city chunks above the
 * street, and chunks laid out from the city plan ({@link City#skyline}) where far terrain has never seen the real one;
 * a real chunk replaces its planned one when it arrives.
 */
public final class FarCity {
    private FarCity() {}

    /** The colour's top byte: this cell is built (its corner keeps the highest built cell). */
    public static final int BUILT = 1 << 24;
    /** Cells this far above the street count as built (the paving and the gardens don't). */
    static final int ABOVE_STREET = 3;

    public static boolean built(int colour) {
        return (colour & BUILT) != 0;
    }

    /** Marks a real chunk's built cells, if the city stands there. */
    static FarChunk mark(FarChunk c) {
        CityLayout l = City.layout();
        if (l == null || !l.touches(c.x(), c.z())) return c;
        for (int i = 0; i < FarChunk.CELLS; i++) {
            int cell = c.cell()[i];
            if (cell != 0 && FarChunk.top(cell) >= l.streetY + ABOVE_STREET) c.colour()[i] |= BUILT;
        }
        return c;
    }

    /**
     * The chunk as the city plan lays it out, for a city chunk never seen: the city's own columns at their true height
     * and colour; cells the city leaves to the land stay unknown (the prediction shows there). Null outside the city.
     */
    static @Nullable FarChunk planned(CityLayout l, int chunkX, int chunkZ, long seed, Holder<Biome> biome) {
        int[] topY = new int[256];
        BlockState[] top = new BlockState[256];
        if (!City.skyline(l, chunkX, chunkZ, seed, topY, top)) return null;
        int[] cell = new int[FarChunk.CELLS], colour = new int[FarChunk.CELLS], crown = new int[FarChunk.CELLS];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int sz = 0; sz < FarChunk.SIDE; sz++) {
            for (int sx = 0; sx < FarChunk.SIDE; sx++) {
                int high = Integer.MIN_VALUE, n = 0, wet = 0, r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int lx = sx * 2 + (k & 1), lz = sz * 2 + (k >> 1), j = lz * 16 + lx;
                    if (top[j] == null) continue;
                    pos.set((chunkX << 4) + lx, topY[j], (chunkZ << 4) + lz);
                    int rgb = FarPalette.block(top[j], EmptyBlockGetter.INSTANCE, pos, biome);
                    if (rgb < 0) continue;
                    FluidState fluid = top[j].getFluidState();
                    if (!fluid.isEmpty()) wet++;
                    high = Math.max(high, topY[j] + 1);
                    r += rgb >> 16 & 255;
                    g += rgb >> 8 & 255;
                    b += rgb & 255;
                    n++;
                }
                if (n == 0) continue;
                int i = sz * FarChunk.SIDE + sx;
                // the city's canals and basins are a few blocks deep
                int d = wet * 2 >= n ? 4 : 0;
                cell[i] = d << 16 | Math.clamp(high + FarColumns.HEIGHT_OFFSET, 1, 65535);
                colour[i] = (r / n) << 16 | (g / n) << 8 | b / n | (high >= l.streetY + ABOVE_STREET ? BUILT : 0);
                any = true;
            }
        }
        return any ? new FarChunk(chunkX, chunkZ, cell, colour, crown) : null;
    }
}
