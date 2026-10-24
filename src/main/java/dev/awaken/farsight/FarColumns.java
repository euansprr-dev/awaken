package dev.awaken.farsight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * Predicted far terrain, straight from the world generator and without building a chunk (SPEC §16). Far cells take
 * one sample of the noise router's {@code chunk_surface_level} (the generator's own surface estimate); cells within
 * about a kilometre take the exact base height ({@code getBaseHeight}, sea floor included). The biome comes from the
 * biome source and the colour from {@link FarPalette}; below sea level a cell is water at sea level. Terrain mods that
 * reshape the noise router (Tectonic, Awakened Lands) are followed for free.
 *
 * <p>One instance per worker thread: it holds its own sampler context and biome resolver. Never used on the server
 * tick.
 */
public final class FarColumns {
    /** Heights are stored 16-bit, offset so the deepest world still fits. */
    public static final int HEIGHT_OFFSET = 2048;
    public static final int WATER = 1;

    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final @Nullable DensitySampler surface;
    private final SamplerContext context = SamplerContext.builder().build();
    private final BiomeResolver biomes;
    private final int seaLevel;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private FarColumns(ServerLevel level) {
        var source = level.getChunkSource();
        generator = source.getGenerator();
        random = source.randomState();
        heights = level;
        surface = generator instanceof NoiseBasedChunkGenerator noise
                ? random.getSampler(noise.generatorSettings().value().noiseRouter().chunkSurfaceLevel()) : null;
        biomes = generator.getBiomeSource().createUncachedResolver(random);
        seaLevel = generator.getSeaLevel();
    }

    /** A sampler for this level's generator. Call on the thread that will use it. */
    public static FarColumns of(ServerLevel level) {
        return new FarColumns(level);
    }

    public int seaLevel() {
        return seaLevel;
    }

    /** The top of the ground at block (x, z): exact base height, or the generator's surface estimate. */
    public int ground(int x, int z, boolean exact) {
        if (exact || surface == null) return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
        return Math.round(surface.sampleValue(context, x, 0, z));
    }

    public Holder<Biome> biome(int x, int y, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }

    /**
     * Fills one patch of ring {@code ring}: {@code height[i]} and {@code colour[i]} for its 16×16 cells, row by row,
     * as the RGBA8 texels the client uploads (ABGR ints). Height texel: r, g the drawn top (+{@link #HEIGHT_OFFSET}),
     * b flags ({@link #WATER}), a 255 (known). Colour texel: r, g, b the colour, a 255. Returns min and max top as
     * {@code min << 16 | max} (offset), for culling.
     */
    public int patch(int ring, int px, int pz, int[] height, int[] colour) {
        int s = Rings.cellSize(ring);
        boolean exact = ring < Rings.EXACT_RINGS;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int j = 0; j < Rings.PATCH; j++) {
            for (int i = 0; i < Rings.PATCH; i++) {
                int cx = (px * Rings.PATCH + i) * s, cz = (pz * Rings.PATCH + j) * s;
                // exact cells sample their centre; estimate cells their corner, a 16-block multiple, where the
                // interpolated surface takes a single sample
                int x = exact ? cx + s / 2 : cx, z = exact ? cz + s / 2 : cz;
                int ground = ground(x, z, exact);
                boolean water = ground < seaLevel;
                int top = water ? seaLevel : ground;
                Holder<Biome> biome = biome(x, top, z);
                int rgb;
                if (water) {
                    rgb = FarPalette.water(biome);
                } else {
                    rgb = FarPalette.ground(biome);
                    if (top > seaLevel + 2 && biome.value().coldEnoughToSnow(pos.set(x, top, z), seaLevel)) rgb = FarPalette.SNOW;
                    // treetops, not the forest floor: they meet the real canopy where the chunks end
                    top += FarPalette.canopy(biome);
                }
                int h = Math.clamp(top + HEIGHT_OFFSET, 0, 65535);
                int k = j * Rings.PATCH + i;
                height[k] = 255 << 24 | (water ? WATER : 0) << 16 | (h & 255) << 8 | h >> 8;
                colour[k] = 255 << 24 | (rgb & 255) << 16 | (rgb >> 8 & 255) << 8 | rgb >> 16 & 255;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return min << 16 | max;
    }

    /** The drawn top of a height texel (blocks), or {@code Integer.MIN_VALUE} for an unknown one. */
    public static int top(int heightTexel) {
        if (heightTexel >>> 24 == 0) return Integer.MIN_VALUE;
        return ((heightTexel & 255) << 8 | heightTexel >> 8 & 255) - HEIGHT_OFFSET;
    }
}
