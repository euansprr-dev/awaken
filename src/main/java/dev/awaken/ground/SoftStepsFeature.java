package dev.awaken.ground;

import com.mojang.serialization.MapCodec;
import dev.awaken.terrain.landmark.LandsClock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The soft steps (SPEC §31, P1): once per chunk, at {@code local_modifications} (after the surface and carvers, before
 * structures, vegetation and the painters, which overwrite what they own), every foot column of the chunk
 * ({@link SoftRule#foot}) takes a half-block slab of its own ground on top. Decisions come from the natural ground heights of
 * the chunk and a one-column ring round it (the generator's own heightmap, never a noise column), and only this chunk's
 * columns are written, so a chunk comes out the same with or without its neighbours made. Never into water or snow
 * country, never over a block or a plant, never on a ground it has no slab for.
 */
public final class SoftStepsFeature implements Feature {
    public static final SoftStepsFeature INSTANCE = new SoftStepsFeature();
    public static final MapCodec<SoftStepsFeature> CODEC = MapCodec.unit(INSTANCE);

    private SoftStepsFeature() {}

    @Override
    public MapCodec<SoftStepsFeature> codec() {
        return CODEC;
    }

    /** Natural ground heights and dryness over a chunk and its ring: what the rule reads. */
    public interface Ground {
        /** The y of the top ground block at (x, z). */
        int top(int x, int z);

        /** Whether the column is dry land (no water over the ground). */
        boolean dry(int x, int z);
    }

    private static volatile @Nullable Map<Block, BlockState> slabs;

    /** Ground block to the slab that softens it (vanilla's where vanilla has one). */
    static Map<Block, BlockState> slabs() {
        Map<Block, BlockState> m = slabs;
        if (m == null) {
            m = new IdentityHashMap<>();
            m.put(Blocks.GRASS_BLOCK, SoftSlabs.TURF_SLAB.get().defaultBlockState());
            m.put(Blocks.PODZOL, SoftSlabs.PODZOL_SLAB.get().defaultBlockState());
            m.put(Blocks.DIRT, SoftSlabs.DIRT_SLAB.get().defaultBlockState());
            m.put(Blocks.COARSE_DIRT, SoftSlabs.COARSE_DIRT_SLAB.get().defaultBlockState());
            m.put(Blocks.MUD, SoftSlabs.MUD_SLAB.get().defaultBlockState());
            m.put(Blocks.GRAVEL, SoftSlabs.GRAVEL_SLAB.get().defaultBlockState());
            m.put(Blocks.SAND, SoftSlabs.SAND_SLAB.get().defaultBlockState());
            m.put(Blocks.RED_SAND, SoftSlabs.RED_SAND_SLAB.get().defaultBlockState());
            m.put(Blocks.STONE, Blocks.STONE_SLAB.defaultBlockState());
            m.put(Blocks.ANDESITE, Blocks.ANDESITE_SLAB.defaultBlockState());
            m.put(Blocks.GRANITE, Blocks.GRANITE_SLAB.defaultBlockState());
            m.put(Blocks.DIORITE, Blocks.DIORITE_SLAB.defaultBlockState());
            m.put(Blocks.TUFF, Blocks.TUFF_SLAB.defaultBlockState());
            m.put(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB.defaultBlockState());
            m.put(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_SLAB.defaultBlockState());
            slabs = m;
        }
        return m;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        int cx = origin.getX() & ~15, cz = origin.getZ() & ~15;
        ChunkAccess[] chunks = new ChunkAccess[9];
        for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) chunks[a * 3 + b] = level.getChunk((cx >> 4) + a - 1, (cz >> 4) + b - 1);
        Ground ground = new Ground() {
            @Override
            public int top(int x, int z) {
                // ChunkAccess.getHeight already answers with the top block's y (the first free block, less one)
                return chunk(x, z).getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }

            @Override
            public boolean dry(int x, int z) {
                ChunkAccess c = chunk(x, z);
                return c.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15) == c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            }

            private ChunkAccess chunk(int x, int z) {
                return chunks[((x >> 4) - (cx >> 4) + 1) * 3 + (z >> 4) - (cz >> 4) + 1];
            }
        };
        int n = paint(level, cx, cz, generator.getSeaLevel(), level.getSeed(), ground);
        LandsClock.add("soft_steps", System.nanoTime() - t0);
        return n > 0;
    }

    /** Lays the soft steps of the chunk whose corner is (cx, cz); returns the slabs placed. Public for the tests. */
    public static int paint(WorldGenLevel level, int cx, int cz, int sea, long seed, Ground ground) {
        int w = 18;
        int[] h = new int[w * w];
        for (int j = 0; j < w; j++) for (int i = 0; i < w; i++) h[j * w + i] = ground.top(cx - 1 + i, cz - 1 + j);
        Map<Block, BlockState> map = slabs();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int j = 1; j <= 16; j++) {
            for (int i = 1; i <= 16; i++) {
                if (!SoftRule.foot(h, w, i, j)) continue;
                int x = cx - 1 + i, z = cz - 1 + j, y = h[j * w + i];
                if (!ground.dry(x, z)) continue;
                BlockState surface = level.getBlockState(m.set(x, y, z));
                BlockState slab = map.get(surface.getBlock());
                if (slab == null) continue;                                          // no slab for this ground
                if (!level.getBlockState(m.set(x, y + 1, z)).isAir()) continue;      // a block or a plant over it: leave it
                Biome biome = level.getBiome(m).value();
                if (biome.coldEnoughToSnow(m, sea)) continue;                        // snow country: its layers can't lie on a slab
                if (slab.getBlock() instanceof TurfSlabBlock) slab = slab.setValue(TurfSlabBlock.TUFT, SoftRule.tuft(seed, x, z));
                // the grass under a slab would die: it is dirt from the start
                if (surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.PODZOL)) level.setBlock(m.set(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(m.set(x, y + 1, z), slab, Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        return placed;
    }
}
