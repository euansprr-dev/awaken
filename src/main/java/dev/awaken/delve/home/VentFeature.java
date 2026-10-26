package dev.awaken.delve.home;

import com.mojang.serialization.MapCodec;
import dev.awaken.body.ascent.AscentContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code awaken:delve_vents} (SPEC §28.5; PLAN 8.2 "Vents"): hot air over magma in the lava tunnels and the sulfur
 * caves, the C-rank way up a great cavern. One chunk in {@link #CHANCE} probes {@link #PROBES} columns from the bottom
 * up for a floor with at least {@link #MIN_AIR} blocks of open air over it, either in the sulfur caves or deep
 * ({@link #LAVA_MAX_Y} and under) beside lava; the first found gets a crust of magma with the Ascent's updraft vent set
 * flush in its heart. The vent's block entity lists it with {@code Updrafts} as it loads (the lift, Aether's refill
 * and the column's light, the same as the sandbox's), and {@code delve.client.VentFx} adds the embers. Pure and per
 * chunk: the probes read a block or two past the chunk's edge, the crust is written inside it. New chunks only.
 */
public record VentFeature() implements Feature {
    public static final MapCodec<VentFeature> CODEC = MapCodec.unit(VentFeature::new);

    /** A chunk tries for a vent one time in this many. */
    public static final int CHANCE = 8;
    public static final int PROBES = 3;
    /** Open air a vent's floor needs over it (the column rises 34, lifts fully to 24). */
    public static final int MIN_AIR = 20;
    /** Lava tunnels and the lava under the deep caves: floors this deep or deeper, with lava within 4. */
    public static final int LAVA_MAX_Y = -28;
    /** Floors are looked for up to here (the sulfur caves sit well under it). */
    static final int SCAN_TOP = 48;

    public enum Kind { LAVA, SULFUR }

    /** A floor that takes a vent: the floor block (the vent replaces it) and its kind. */
    public record Site(BlockPos floor, Kind kind) {}

    @Override
    public MapCodec<VentFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        if (random.nextInt(CHANCE) != 0) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        int from = level.getMinY() + 5, to = Math.min(SCAN_TOP, level.getMaxY() - MIN_AIR - 2);
        for (int probe = 0; probe < PROBES; probe++) {
            // inside the chunk by 3, so the crust round the vent stays in it
            Site s = site(level, x0 + 3 + random.nextInt(10), z0 + 3 + random.nextInt(10), from, to, LAVA_MAX_Y);
            if (s == null) continue;
            dress(level, s.floor(), s.kind(), random);
            return true;
        }
        return false;
    }

    /**
     * The lowest floor in the column (x, z) between {@code from} and {@code to} with {@link #MIN_AIR} of air over it
     * that is in the sulfur caves, or at {@code lavaMaxY} or under with lava beside it; null if none. One pass up the
     * column (about a hundred block reads), and the lava check only for floors with the air.
     */
    public static @Nullable Site site(WorldGenLevel level, int x, int z, int from, int to, int lavaMaxY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), n = new BlockPos.MutableBlockPos();
        int floor = Integer.MIN_VALUE, run = 0;
        for (int y = from; y <= to + MIN_AIR; y++) {
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (s.isAir()) {
                if (floor == Integer.MIN_VALUE || ++run < MIN_AIR) continue;
                Kind k = kindAt(level, x, floor, z, lavaMaxY, n);
                if (k != null) return new Site(new BlockPos(x, floor, z), k);
                floor = Integer.MIN_VALUE;
            } else {
                floor = y <= to && s.getFluidState().isEmpty() && s.isFaceSturdy(level, m, Direction.UP) ? y : Integer.MIN_VALUE;
                run = 0;
            }
        }
        return null;
    }

    private static @Nullable Kind kindAt(WorldGenLevel level, int x, int floor, int z, int lavaMaxY, BlockPos.MutableBlockPos m) {
        if (level.getBiome(m.set(x, floor + 1, z)).is(Biomes.SULFUR_CAVES)) return Kind.SULFUR;
        if (floor > lavaMaxY) return null;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (level.getFluidState(m.set(x + dx, floor + dy, z + dz)).is(FluidTags.LAVA)) return Kind.LAVA;
                }
            }
        }
        return null;
    }

    /**
     * The vent on its floor: a crust of magma round it (radius about 2.3, ragged; lava at the floor's level crusts over
     * too), cinnabar round that in the sulfur caves, magma under the grate, and the vent set flush where the floor was.
     * Used by worldgen, the tour's stage and the test.
     */
    public static void dress(WorldGenLevel level, BlockPos floor, Kind kind, RandomSource r) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState(), rim = Blocks.CINNABAR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) + (r.nextDouble() - 0.5) * 0.8;
                if (d > 3.4 || (dx == 0 && dz == 0)) continue;
                boolean core = d <= 2.3;
                if (!core && kind != Kind.SULFUR) continue;
                int y = top(level, floor.getX() + dx, floor.getY(), floor.getZ() + dz, m);
                if (y == Integer.MIN_VALUE) continue;
                level.setBlock(m.set(floor.getX() + dx, y, floor.getZ() + dz), core ? magma : rim, Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(m.set(floor.getX(), floor.getY() - 1, floor.getZ()), magma, Block.UPDATE_CLIENTS);
        level.setBlock(floor, AscentContent.UPDRAFT_VENT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The column's floor block within a block of {@code y} (rock or lava with air over it), or MIN_VALUE. */
    private static int top(WorldGenLevel level, int x, int y, int z, BlockPos.MutableBlockPos m) {
        for (int yy = y + 1; yy >= y - 1; yy--) {
            BlockState s = level.getBlockState(m.set(x, yy, z));
            if (s.isAir() || !level.getBlockState(m.set(x, yy + 1, z)).isAir()) continue;
            boolean lava = s.getFluidState().is(FluidTags.LAVA);
            if (lava || (s.getFluidState().isEmpty() && s.isSolidRender())) return yy;
        }
        return Integer.MIN_VALUE;
    }
}
