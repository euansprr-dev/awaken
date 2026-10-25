package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import dev.awaken.terrain.city.CityBasinFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Far Sight 2's height model (SPEC §20): predicted far terrain straight from the world generator, without building a
 * chunk, to a sixteenth of a block. Both vanilla's router and Tectonic's put the ground where their "sloped cheese"
 * terrain density crosses zero; this samples that density (with the generator's own caches on, so its 2D splines are
 * worked out once per column, not once per probe), starting from the last column's ground, steps 8 blocks to bracket
 * the crossing and places the ground on the bracket's straight line, as the real terrain's own 8-block interpolation
 * does. Caves, overhangs and structures are left to the real chunks. Awakened Alpine's erosion is in
 * the terrain's offset, so it is followed for free, and the city basin is applied by its own shape.
 *
 * <p>Samples sit on the ring's grid corners ({@code i · cell} blocks), so a coarse ring's corners are exactly every
 * other corner of the finer ring inside it and the rings meet without a crack. One instance per worker thread: it
 * holds its own sampler context (the caches) and biome resolver. Never used on the server tick.
 */
public final class FarColumns {
    /** Heights are stored 16-bit: (top + {@link #HEIGHT_BASE}) × {@link #HEIGHT_STEPS}, 0 meaning unknown. */
    public static final int HEIGHT_BASE = 1024, HEIGHT_STEPS = 16;
    /** The generated layer's whole-block heights are offset by this (FarChunk). */
    public static final int HEIGHT_OFFSET = 2048;
    /** Deepest water a texel records. */
    public static final int MAX_DEPTH = 255;
    /** Colour texel flags (its alpha): see {@code farsight.glsl}. */
    public static final int ALPINE = 1, COLD = 2, GENERATED = 128;

    /** The terrain densities whose zero is the ground, first found wins: Tectonic's, then vanilla's. */
    private static final String[] TERRAIN = {"tectonic:sloped_cheese", "minecraft:overworld/sloped_cheese"};
    private static final TagKey<Biome> ALPINE_BIOMES = TagKey.create(Registries.BIOME, Awaken.id("alpine"));

    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final @Nullable DensitySampler terrain, estimate;
    private final SamplerContext context = SamplerContext.builder().enableCaches().build();
    private final BiomeResolver biomes;
    private final int seaLevel, minY, maxY;
    private final boolean alpine;
    private final CityBasinFunction.@Nullable Shape city;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Density probes this instance has made (the cost per column, for the status line). */
    public long probes;

    private FarColumns(ServerLevel level) {
        this(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level.registryAccess(), level,
                FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_alpine")), id -> {}).isSubsetOf(level.enabledFeatures()),
                level.dimension() == Level.OVERWORLD ? CityBasin.current() : null);
    }

    /** For any generator (tests use vanilla's own overworld at a fixed seed): its random state, registries and height. */
    FarColumns(ChunkGenerator generator, RandomState random, RegistryAccess registries, LevelHeightAccessor heights, boolean alpine,
            @Nullable CitySite site) {
        this.generator = generator;
        this.random = random;
        this.heights = heights;
        DensitySampler t = null, e = null;
        if (generator instanceof NoiseBasedChunkGenerator noise) {
            e = random.getSampler(noise.generatorSettings().value().noiseRouter().chunkSurfaceLevel());
            var functions = registries.lookupOrThrow(Registries.DENSITY_FUNCTION);
            for (String id : TERRAIN) {
                var holder = functions.get(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse(id)));
                if (holder.isPresent()) {
                    t = random.getSampler(holder.get().value());
                    break;
                }
            }
        }
        terrain = t;
        estimate = e;
        biomes = generator.getBiomeSource().createUncachedResolver(random);
        seaLevel = generator.getSeaLevel();
        minY = heights.getMinY();
        maxY = heights.getMaxY();
        this.alpine = alpine;
        city = site != null && site.basinCarved() ? new CityBasinFunction.Shape(site) : null;
    }

    /** Whether this generator has a terrain density to solve (else whole-block base heights, much slower). */
    public boolean modelled() {
        return terrain != null;
    }

    /** A sampler for this level's generator. Call on the thread that will use it. */
    public static FarColumns of(ServerLevel level) {
        return new FarColumns(level);
    }

    public int seaLevel() {
        return seaLevel;
    }

    /**
     * The ground's height at block (x, z): the top face of its highest solid block, to a fraction of a block. The real
     * terrain interpolates its density over 8-block cells in y, so the 8-block bracket's own straight line matches it
     * better than halving to one block does (median 0.3 blocks off against 0.6, test farsight_columns_predict).
     */
    public float ground(int x, int z) {
        return ground(x, z, false);
    }

    /** The same, halving the bracket to one block first when {@code fine} (the test compares the two). */
    public float ground(int x, int z, boolean fine) {
        float h = terrain == null ? fallback(x, z) : solve(x, z, fine);
        return city == null ? h : city.ground(h, x, z);
    }

    // a generator without a terrain density (flat, debug, another mod's own): its base height, whole blocks
    private float fallback(int x, int z) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
    }

    /** The density's zero crossing in column (x, z), plus half a block (the top face of the block it falls in). */
    private float solve(int x, int z, boolean fine) {
        // start from the last column's ground (neighbours are close: a probe or two brackets it); the router's own
        // estimate (four 8-block searches of its preliminary surface) only for a column with no neighbour before it
        float start = Float.isNaN(guess) ? estimate == null ? seaLevel : estimate.sampleValue(context, x, 0, z) : guess;
        int y = Math.clamp((int) Math.floor(start / 8f) * 8, minY, maxY - 8);
        float d = density(x, y, z);
        int solid, air;
        float ds, da;
        if (d > 0f) {
            // ground at y: climb until air
            solid = y;
            ds = d;
            while (true) {
                int up = solid + 8;
                if (up >= maxY) return maxY;
                float du = density(x, up, z);
                if (du <= 0f) {
                    air = up;
                    da = du;
                    break;
                }
                solid = up;
                ds = du;
            }
        } else {
            air = y;
            da = d;
            while (true) {
                int down = air - 8;
                if (down < minY) return minY;
                float dd = density(x, down, z);
                if (dd > 0f) {
                    solid = down;
                    ds = dd;
                    break;
                }
                air = down;
                da = dd;
            }
        }
        // halve the bracket to one block
        while (fine && air - solid > 1) {
            int mid = (solid + air) >>> 1;
            float dm = density(x, mid, z);
            if (dm > 0f) {
                solid = mid;
                ds = dm;
            } else {
                air = mid;
                da = dm;
            }
        }
        // where between the two blocks the density crosses zero, then the top face of the block that holds it
        float t = (air - solid) * ds / Math.max(1e-6f, ds - da);
        guess = solid + t;
        return solid + t + 0.5f;
    }

    /** The last column's crossing, a first guess for the next (NaN: none, ask the router's estimate). */
    private float guess = Float.NaN;

    private float density(int x, int y, int z) {
        probes++;
        return terrain.sampleValue(context, x, y, z);
    }

    public Holder<Biome> biome(int x, int y, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }

    /**
     * Fills one patch of ring {@code ring}: its 16×16 grid corners, row by row, as the texels the client uploads:
     * <ul>
     *   <li>{@code height}: 16 bits, (top + {@link #HEIGHT_BASE}) × {@link #HEIGHT_STEPS}; water stands at sea level;</li>
     *   <li>{@code colour} (ABGR): the ground's colour, flags in alpha ({@link #ALPINE}, {@link #COLD}, the palette's);</li>
     *   <li>{@code info} (ABGR): r the water's depth, g the canopy's height × 4, b how densely trees stand, a 255;</li>
     *   <li>{@code crown} (ABGR): the trees' crown colour, their shape in alpha.</li>
     * </ul>
     * Returns the lowest and highest top as {@code min << 16 | max} (whole blocks + {@link #HEIGHT_BASE}), for culling.
     */
    public int patch(int ring, int px, int pz, int[] height, int[] colour, int[] info, int[] crown) {
        int s = Rings.cellSize(ring);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        float rowStart = Float.NaN;
        guess = Float.NaN;
        for (int j = 0; j < Rings.PATCH; j++) {
            // each row starts from the corner above its first, the nearest solved neighbour
            guess = rowStart;
            for (int i = 0; i < Rings.PATCH; i++) {
                int x = (px * Rings.PATCH + i) * s, z = (pz * Rings.PATCH + j) * s;
                float ground = ground(x, z);
                if (i == 0) rowStart = guess;
                // the sea fills to just under sea level: its surface is the top face of the block below it
                boolean water = ground < seaLevel;
                float top = water ? seaLevel : ground;
                Holder<Biome> biome = biome(x, (int) top, z);
                FarPalette.Look look = FarPalette.look(biome);
                int k = j * Rings.PATCH + i;
                int flags = look.flags();
                if (alpine && biome.is(ALPINE_BIOMES)) flags |= ALPINE;
                if (biome.value().coldEnoughToSnow(pos.set(x, (int) top, z), seaLevel)) flags |= COLD;
                int rgb = water ? FarPalette.water(biome) : look.ground();
                int depth = water ? Math.clamp(Math.round(seaLevel - ground), 1, MAX_DEPTH) : 0;
                int density = water ? 0 : look.density();
                height[k] = packHeight(top);
                colour[k] = abgr(rgb, flags);
                info[k] = abgr(depth, Math.min(255, look.canopy() * 4), density, 255);
                crown[k] = abgr(look.crown(), look.shape());
                int whole = (int) Math.floor(top) + HEIGHT_BASE;
                min = Math.min(min, whole);
                max = Math.max(max, whole + 1);
            }
        }
        return Math.clamp(min, 0, 65535) << 16 | Math.clamp(max, 0, 65535);
    }

    /** A height as its 16-bit texel (never 0, which means unknown). */
    public static int packHeight(float top) {
        return Math.clamp(Math.round((top + HEIGHT_BASE) * HEIGHT_STEPS), 1, 65535);
    }

    public static float unpackHeight(int texel) {
        return texel == 0 ? Float.NaN : texel / (float) HEIGHT_STEPS - HEIGHT_BASE;
    }

    /** An RGB colour and an alpha byte as an ABGR texel (RGBA8 in little-endian memory). */
    static int abgr(int rgb, int alpha) {
        return alpha << 24 | (rgb & 255) << 16 | (rgb >> 8 & 255) << 8 | rgb >> 16 & 255;
    }

    static int abgr(int r, int g, int b, int a) {
        return a << 24 | b << 16 | g << 8 | r;
    }
}
