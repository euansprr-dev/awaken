package dev.awaken.farsight;

import dev.awaken.Awaken;
import dev.awaken.city.City;
import dev.awaken.city.CityLayout;
import dev.awaken.terrain.alpine.AlpineSurface;
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
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
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
 * the terrain's offset, so it is followed for free. Round the city, inside the basin's blend, the ground is found in the
 * router's own final density (which the basin wraps), down from above the natural ground: the basin flattens by
 * blending densities, and the terrain's density alone can't say where that leaves the street flat up to a cliff.
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
    /** Grass and forest biomes the terrain lifts into the mountains: alpine ground too, above {@link #UPLAND_FROM}. */
    private static final TagKey<Biome> UPLAND_BIOMES = TagKey.create(Registries.BIOME, Awaken.id("upland"));
    static final float UPLAND_FROM = 150f;

    private final ChunkGenerator generator;
    private final RandomState random;
    private final LevelHeightAccessor heights;
    private final @Nullable DensitySampler terrain, estimate;
    /** The final density as the chunks are built from it, the city's basin included (null without a carved basin). */
    private final @Nullable DensitySampler finalDensity;
    private final SamplerContext context = SamplerContext.builder().enableCaches().build();
    private final BiomeResolver biomes;
    private final int seaLevel, minY, maxY;
    private final boolean alpine;
    private final CityBasinFunction.@Nullable Shape city;
    private final int streetY;
    /** The world's seed (the city plan paints with it); 0 for the tests' generators. */
    private final long seed;
    private final int[] cityY = new int[1];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Density probes this instance has made (the cost per column, for the status line). */
    public long probes;

    private FarColumns(ServerLevel level) {
        this(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level.registryAccess(), level,
                FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_alpine")), id -> {}).isSubsetOf(level.enabledFeatures()),
                level.dimension() == Level.OVERWORLD ? CityBasin.current() : null, level.getSeed());
    }

    /** For any generator (tests use vanilla's own overworld at a fixed seed): its random state, registries and height. */
    FarColumns(ChunkGenerator generator, RandomState random, RegistryAccess registries, LevelHeightAccessor heights, boolean alpine,
            @Nullable CitySite site) {
        this(generator, random, registries, heights, alpine, site, 0L);
    }

    private FarColumns(ChunkGenerator generator, RandomState random, RegistryAccess registries, LevelHeightAccessor heights, boolean alpine,
            @Nullable CitySite site, long seed) {
        this.seed = seed;
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
        DensitySampler b = null;
        if (t != null && site != null && site.basinCarved() && generator instanceof NoiseBasedChunkGenerator noise) {
            // the lands pack's Lithostitched wrap puts the basin round the settings' final density; a router without it
            // (the tests' vanilla one) gets the same wrap here
            DensityFunction fin = noise.generatorSettings().value().noiseRouter().finalDensity();
            b = random.getSampler(fin instanceof CityBasinFunction ? fin
                    : new CityBasinFunction(fin, CityBasinFunction.Mode.DENSITY, java.util.Optional.of(noise.generatorSettings().value().noiseRouter().depth())));
        }
        finalDensity = b;
        streetY = site != null ? site.streetY() : 0;
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
        if (terrain == null) {
            float h = fallback(x, z);
            return city == null ? h : city.ground(h, x, z);
        }
        float natural = solve(x, z, fine);
        if (city == null || city.weight(x, z) <= 0f) return natural;
        // inside the basin's blend: blending heights drew a long rise where the real street runs flat out to a cliff
        // (Euan, 2026-10-26: "showing stuff that isn't really there")
        return finalDensity != null ? builtGround(x, z, natural) : city.ground(natural, x, z);
    }

    /**
     * The ground as the chunks are built: down through the final density in 8-block steps from above anything that can
     * stand there (the natural ground, or the basin's fill just over the street), to the first solid, then the bracket's
     * straight line. Where a ledge overhangs, its top (what shows from afar); a solid run thinner than 8 blocks can be
     * stepped over (the ground then shows lower, never higher).
     */
    private float builtGround(int x, int z, float natural) {
        int air = Math.min(Math.floorDiv((int) Math.ceil(Math.max(natural, streetY + 11)), 8) * 8 + 8, maxY - 8);
        float da = finalAt(x, air, z);
        // not above everything after all (an overhang the natural solve stepped under): climb to the air first. Never
        // the natural ground as it is: inside the blend that stood a hill where the basin had flattened it
        for (int up = 0; da > 0f && up < 24 && air + 8 <= maxY - 8; up++) {
            air += 8;
            da = finalAt(x, air, z);
        }
        if (da > 0f) return city.ground(natural, x, z);
        while (true) {
            int solid = air - 8;
            if (solid < minY) return minY;
            float ds = finalAt(x, solid, z);
            if (ds > 0f) return solid + 8 * ds / Math.max(1e-6f, ds - da) + 0.5f;
            air = solid;
            da = ds;
        }
    }

    private float finalAt(int x, int y, int z) {
        probes++;
        return finalDensity.sampleValue(context, x, y, z);
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

    // Biomes change over tens of blocks, so a patch samples them on a sparser grid (at least 8 blocks and two corners
    // apart) and each corner takes the nearest sample: a biome edge moves by under two pixels at any ring's distance.
    // (2026-10-26 profile in the pack's world: the biome source, Tectonic's climate behind Lithostitched's injector, was
    // 86% of the workers' time at one lookup a corner, 178 us a column, and rings 1-5 took minutes to fill.)
    private static final int BIOME_SIDE = Rings.PATCH / 2 + 1;
    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] patchBiomes = new Holder[BIOME_SIDE * BIOME_SIDE];
    private int biomeX, biomeZ, biomeStep, biomeCell;

    /** Starts a patch's biome samples afresh: patch (px, pz) of ring {@code ring}. */
    public void biomesFor(int ring, int px, int pz) {
        java.util.Arrays.fill(patchBiomes, null);
        biomeCell = Rings.cellSize(ring);
        biomeStep = Math.max(2, 8 / biomeCell);
        biomeX = px * Rings.PATCH;
        biomeZ = pz * Rings.PATCH;
    }

    /** The biome at corner (i, j) of the patch {@link #biomesFor} named: its nearest sample's, sampled at height y. */
    public Holder<Biome> patchBiome(int i, int j, int y) {
        int bi = Math.round((float) i / biomeStep), bj = Math.round((float) j / biomeStep), b = bj * BIOME_SIDE + bi;
        Holder<Biome> biome = patchBiomes[b];
        if (biome == null) {
            biome = biome((biomeX + bi * biomeStep) * biomeCell, y, (biomeZ + bj * biomeStep) * biomeCell);
            patchBiomes[b] = biome;
        }
        return biome;
    }

    /**
     * Fills one patch of ring {@code ring}: its 16×16 grid corners, row by row, as the texels the client uploads:
     * <ul>
     *   <li>{@code height}: 16 bits, (top + {@link #HEIGHT_BASE}) × {@link #HEIGHT_STEPS}; water stands at sea level;</li>
     *   <li>{@code colour} (ABGR): the ground's colour, flags in alpha ({@link #ALPINE}, {@link #COLD}, the palette's);</li>
     *   <li>{@code info} (ABGR): r the water's depth, g the canopy's height × 4, b how densely trees stand, a how far
     *       above the snow-cold line the ground stands ({@link #coldness});</li>
     *   <li>{@code crown} (ABGR): the trees' crown colour, their shape in alpha.</li>
     * </ul>
     * Returns the lowest and highest top as {@code min << 16 | max} (whole blocks + {@link #HEIGHT_BASE}), for culling.
     */
    public int patch(int ring, int px, int pz, int[] height, int[] colour, int[] info, int[] crown) {
        int s = Rings.cellSize(ring);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        float rowStart = Float.NaN;
        biomesFor(ring, px, pz);
        guess = Float.NaN;
        for (int j = 0; j < Rings.PATCH; j++) {
            // each row starts from the corner above its first, the nearest solved neighbour
            guess = rowStart;
            for (int i = 0; i < Rings.PATCH; i++) {
                int x = (px * Rings.PATCH + i) * s, z = (pz * Rings.PATCH + j) * s;
                // the city, as its plan lays it out (whether or not terrain carved its basin, the city levels its own
                // ground): its walls, roofs and squares at their true height and colour, never the land it was built
                // over; its columns skip the terrain solve
                CityLayout l = this.seed != 0 ? City.layout() : null;
                BlockState built = l == null ? null : FarCity.plannedAt(l, seed, x, z, cityY);
                // under City Sight the city's real blocks are drawn: here only its ground, a block under the street
                if (built != null && FarCity.underCitySight) cityY[0] = l.groundY(x, z) - 1;
                float ground = built != null ? cityY[0] + 1 : ground(x, z);
                if (i == 0) rowStart = guess;
                // the sea fills to just under sea level: its surface is the top face of the block below it
                boolean water = ground < seaLevel;
                float top = water ? seaLevel : ground;
                Holder<Biome> biome = patchBiome(i, j, (int) top);
                FarPalette.Look look = FarPalette.look(biome);
                int k = j * Rings.PATCH + i;
                int flags = look.flags();
                if (alpine && (biome.is(ALPINE_BIOMES) || top >= UPLAND_FROM && biome.is(UPLAND_BIOMES))) flags |= ALPINE;
                if (biome.value().coldEnoughToSnow(pos.set(x, (int) top, z), seaLevel)) flags |= COLD;
                int rgb = water ? FarPalette.water(biome) : look.ground();
                int depth = water ? Math.clamp(Math.round(seaLevel - ground), 1, MAX_DEPTH) : 0;
                // Awakened Alpine plants spruce in the mountain biomes: the far forests stand there too
                int cold = coldness(biome, x, (int) Math.floor(top), z);
                int density = water ? 0 : Math.min(255, look.density() + (alpine ? AlpineSurface.mountainForest(look.archetype()) : 0));
                height[k] = packHeight(top);
                colour[k] = abgr(rgb, flags);
                info[k] = abgr(depth, Math.min(255, look.canopy() * 4), density, cold);
                crown[k] = abgr(look.crown(), look.shape());
                if (built != null) {
                    int brgb = FarPalette.block(built, EmptyBlockGetter.INSTANCE, pos.set(x, cityY[0], z), biome);
                    if (brgb >= 0) {
                        boolean wet = !built.getFluidState().isEmpty();
                        top = cityY[0] + 1;
                        // a corner spanning 8 blocks or more stands at the city's mean height round it, as the real
                        // chunks' corners do (FarStore.patch): one spire's tip at a corner would be a pyramid that wide
                        if (s >= 8 && !FarCity.underCitySight) top = cityMean(l, x, z, s, top);
                        height[k] = packHeight(top);
                        colour[k] = abgr(brgb, GENERATED);
                        info[k] = abgr(wet ? 4 : 0, 0, 0, cold);
                        crown[k] = 0;
                    }
                }
                int whole = (int) Math.floor(top) + HEIGHT_BASE;
                min = Math.min(min, whole);
                max = Math.max(max, whole + 1);
            }
        }
        return Math.clamp(min, 0, 65535) << 16 | Math.clamp(max, 0, 65535);
    }

    /** The plan's mean top (top faces) over a 3 by 3 spread of an s-block cell round (x, z); {@code at} where none is city. */
    private float cityMean(CityLayout l, int x, int z, int s, float at) {
        float sum = 0f;
        int n = 0, d = Math.max(1, s / 3);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (FarCity.plannedAt(l, seed, x + dx * d, z + dz * d, cityY) != null) {
                    sum += cityY[0] + 1;
                    n++;
                }
            }
        }
        return n == 0 ? at : sum / n;
    }

    /**
     * How far above (or below) the height where the biome turns cold enough to snow this ground stands, as a byte: 128
     * at the line, 4 a block, clamped to ±31 blocks. The game cools with height in a straight line, so a few probes of
     * its own snow test find the line; the shaders then lay snow smoothly by it instead of by a flag per corner.
     */
    private int coldness(Holder<Biome> biome, int x, int y, int z) {
        Biome b = biome.value();
        boolean coldHere = b.coldEnoughToSnow(pos.set(x, y, z), seaLevel);
        int lo = y - 32, hi = y + 32;
        if (coldHere && b.coldEnoughToSnow(pos.set(x, lo, z), seaLevel)) return 255;
        if (!coldHere && !b.coldEnoughToSnow(pos.set(x, hi, z), seaLevel)) return 0;
        // bisect to the line: below it warm, above it cold
        int warm = coldHere ? lo : y, cool = coldHere ? y : hi;
        while (cool - warm > 1) {
            int mid = (warm + cool) >>> 1;
            if (b.coldEnoughToSnow(pos.set(x, mid, z), seaLevel)) cool = mid;
            else warm = mid;
        }
        return Math.clamp(128 + (y - cool) * 4, 0, 255);
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
