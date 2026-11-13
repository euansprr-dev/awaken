package dev.awaken.village.world;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The planners' noise columns against the generator's own (SPEC §28.9, Cost): for columns spread over the land round a
 * point (far apart, so every kind of land is among them, and a close patch, so every place within a noise cell is), each
 * block {@link NoiseColumns} reads, top down to the floor, must be the block {@code getBaseColumn} gives there, and the
 * top and water must be the same. Then both are timed, and where a column's time goes: the lookups by value the
 * generator makes, and ours (the aquifer, the density, the blocks). The command {@code /awaken village road columns}
 * runs it over the server's noise (Tectonic's when it is installed), the GameTest village_columns_exact over vanilla's.
 */
public final class NoiseColumnsCheck {
    private NoiseColumnsCheck() {}

    /** What a check found: the columns whose blocks differ (none, or ours aren't the generator's), and its line. */
    public record Result(int columns, int differ, String line) {}

    /** {@code far} columns over about ±{@code reach} round x, z, and a close patch of 256 there. */
    public static Result run(ChunkGenerator gen, LevelHeightAccessor heights, RandomState random, int x0, int z0, int far, int reach) {
        NoiseColumns ours = NoiseColumns.of(gen, heights, random);
        if (ours == null) return new Result(0, 0, "not a noise generator (or the planners' columns are switched off): nothing to check");
        GenTerrain t = GenTerrain.of(gen, heights, random);
        List<int[]> at = new ArrayList<>();
        int side = Math.max(1, (int) Math.ceil(Math.sqrt(far))), step = Math.max(1, 2 * reach / side);
        for (int i = 0; i < side; i++)
            for (int j = 0; j < side && at.size() < far; j++) {
                // off the grid by a hash, so the columns don't all fall at one place in their noise cells
                int h = (i * 73_856_093) ^ (j * 19_349_663);
                at.add(new int[]{x0 - reach + i * step + Math.floorMod(h, 61), z0 - reach + j * step + Math.floorMod(h >> 8, 61)});
            }
        for (int i = 0; i < 16; i++) for (int j = 0; j < 16; j++) at.add(new int[]{x0 + i * 3, z0 + j * 5});

        // warm both ways first (the JIT), on columns of their own
        for (int i = 0; i < 48; i++) {
            t.vanilla(x0 + 7919 + i * 37, z0 - 104_729 + i * 41);
            ours.both(x0 + 7919 + i * 41, z0 - 104_729 + i * 37, null);
        }

        // block for block
        int differ = 0;
        List<String> first = new ArrayList<>();
        Set<String> biomes = new HashSet<>();
        int[] families = new int[16];
        BlockState[] mine = new BlockState[Math.max(1, ours.noiseHeight())];
        for (int[] p : at) {
            int x = p[0], z = p[1];
            NoiseColumn col = gen.getBaseColumn(x, z, heights, random);
            int n = ours.blocks(x, z, mine);
            String why = null;
            for (int k = 0; k < n && why == null; k++) {
                int y = ours.noiseHeight() - 1 - k, blockY = ours.noiseMinY() + y;
                BlockState theirs = col.getBlock(blockY);
                if (theirs != mine[y]) why = "y " + blockY + " " + mine[y] + " for " + theirs;
            }
            long a = t.vanilla(x, z), b = ours.both(x, z, null);
            if (why == null && a != b) why = "top/water " + (b >> 32) + "/" + (int) b + " for " + (a >> 32) + "/" + (int) a;
            if (why != null) {
                differ++;
                if (first.size() < 4) first.add(x + " " + z + ": " + why);
            }
            int top = (int) (a >> 32);
            var biome = t.biomes().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(top), QuartPos.fromBlock(z));
            biomes.add(biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
            families[Math.floorMod(GenTerrain.family(biome), families.length)]++;
        }

        // timed twice, alternating, the second round counted
        long vanilla = 0, mineNs = 0;
        for (int round = 0; round < 2; round++) {
            long t0 = System.nanoTime();
            for (int[] p : at) t.vanilla(p[0], p[1]);
            long t1 = System.nanoTime();
            for (int[] p : at) ours.both(p[0], p[1], null);
            long t2 = System.nanoTime();
            vanilla = t1 - t0;
            mineNs = t2 - t1;
        }
        // the generator's lookups by value alone (what a new noise chunk pays for each function it binds), and ours split
        DensityFunction[] looked = ours.looked();
        long t0 = System.nanoTime();
        for (int i = 0; i < at.size(); i++) for (DensityFunction f : looked) random.getSampler(f);
        long lookups = System.nanoTime() - t0;
        long[] split = new long[3];
        for (int[] p : at) ours.both(p[0], p[1], split);

        int cols = at.size();
        int fams = 0;
        for (int f : families) if (f > 0) fams++;
        String line = String.format(Locale.ROOT,
                "%d columns (%d kinds of land, %d biomes): %d differ%s; a column %.3f ms the generator's way, %.3f ms ours (%.1fx); "
                        + "the generator's %d lookups by value %.3f ms a column; ours: aquifer %.3f, density %.3f, blocks %.3f ms",
                cols, fams, biomes.size(), differ, first.isEmpty() ? "" : " (" + String.join("; ", first) + ")", vanilla / 1e6 / cols,
                mineNs / 1e6 / cols, mineNs > 0 ? (double) vanilla / mineNs : 0, looked.length, lookups / 1e6 / cols,
                split[0] / 1e6 / cols, split[1] / 1e6 / cols, split[2] / 1e6 / cols);
        return new Result(cols, differ, line);
    }
}
