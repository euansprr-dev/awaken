package dev.awaken.village.world;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.function.Predicate;

/**
 * One thread's noise columns for the planners (SPEC §28.9, Cost): the land's top and its floor at a column, worked out as
 * the generator's own {@code getBaseColumn} works them out, minus two costs it pays on every column. Each read made a new
 * noise chunk, whose sampler set looks every density function up by its value: a hash of the whole function tree, seven
 * times a column (the final density and the aquifer's six), and with Tectonic's trees in place that was most of a
 * column. Here the samplers are the same objects, found once per thread by identity. And it ran the aquifer down the
 * whole column, where the planners need it only down to the first solid block. Everything else is the generator's: the
 * same volume, sampler context, buffers, aquifer and fluid picker, read top down in the same order, so each block is the
 * one {@code getBaseColumn} gives ({@code /awaken village road columns} and village_columns_exact compare them).
 * <p>
 * Not shared between threads: a planner, a worldgen thread and the server thread each have their own (two generators
 * at most, the roads' and a level's).
 */
public final class NoiseColumns {
    /** Off with {@code -Dawaken.noiseColumns=false}: every column the generator's own way (a check, a way out). */
    static final boolean ON = !"false".equals(System.getProperty("awaken.noiseColumns"));
    /** Set at the first read that fails: from then on every column is read the generator's own way (one log line). */
    private static volatile boolean failed;
    /** Bumped when a server stops: a thread's columns over the world that's gone are dropped at its next read. */
    private static volatile int epoch;

    private static final ThreadLocal<NoiseColumns[]> MINE = ThreadLocal.withInitial(() -> new NoiseColumns[2]);
    private static final Identifier AQUIFER = Identifier.withDefaultNamespace("aquifer");
    /** The generator's own fields: no beardifier, no blending (what {@code getBaseColumn} passes). */
    private static final ContextMap FIELDS = ContextMap.builder().set(Beardifier.CONTEXT_KEY, null).build();

    private final int born = epoch;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState random;
    private final int minY, height;
    private final NoiseGeneratorSettings settings;
    private final NoiseSettings noise;
    private final Aquifer.FluidPicker fluids;
    private final BlockState defaultState;
    private final IdentityHashMap<DensityFunction, DensitySampler> samplers = new IdentityHashMap<>();
    private static final Predicate<BlockState> SURFACE = Heightmap.Types.WORLD_SURFACE_WG.isOpaque(), SOLID = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();

    private NoiseColumns(NoiseBasedChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
        this.generator = generator;
        this.random = random;
        this.minY = heights.getMinY();
        this.height = heights.getHeight();
        this.settings = generator.generatorSettings().value();
        this.noise = settings.noiseSettings().clampToHeightAccessor(heights);
        this.fluids = generator.globalFluidPicker.get();
        this.defaultState = settings.defaultBlock();
    }

    /** This thread's columns for a generator, or null when it isn't a noise generator (or they're switched off). */
    static @Nullable NoiseColumns of(net.minecraft.world.level.chunk.ChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
        if (!ON || failed || !(generator instanceof NoiseBasedChunkGenerator g)) return null;
        NoiseColumns[] mine = MINE.get();
        for (int i = 0; i < mine.length; i++) {
            NoiseColumns c = mine[i];
            if (c != null && c.born != epoch) mine[i] = c = null;
            if (c != null && c.generator == g && c.random == random && c.minY == heights.getMinY() && c.height == heights.getHeight()) {
                if (i > 0) {
                    mine[i] = mine[0];
                    mine[0] = c;
                }
                return c;
            }
        }
        NoiseColumns c = new NoiseColumns(g, heights, random);
        System.arraycopy(mine, 0, mine, 1, mine.length - 1);
        mine[0] = c;
        return c;
    }

    /** (Server stopped) the columns over that world are dropped, each thread's at its next read. */
    public static void forget() {
        epoch++;
    }

    /** A read of ours failed: logged once, and from now on the generator's own way. */
    static void failed(Throwable e) {
        if (failed) return;
        failed = true;
        dev.awaken.Awaken.LOG.error("[road] the planners' noise columns failed: reading the generator's own from now on", e);
    }

    private DensitySampler sampler(DensityFunction f) {
        DensitySampler s = samplers.get(f);
        if (s == null) {
            s = random.getSampler(f);
            samplers.put(f, s);
        }
        return s;
    }

    /**
     * The top (the first block from the sky that isn't air, water's surface included) and the floor (the first solid
     * one) at a column, packed as {@link GenTerrain#both} packs them; {@code split}, when given, adds the nanoseconds spent
     * making the aquifer, sampling the density and reading the blocks (the command's breakdown).
     */
    long both(int x, int z, long @Nullable [] split) {
        int min = minY, top = min - 1, floor = min - 1;
        if (noise.height() > 0) {
            long t0 = split != null ? System.nanoTime() : 0;
            DensityVolume volume = new DensityVolume(1, noise.height(), 1, x, noise.minY(), z);
            DensityBufferPool pool = random.acquireDensityBufferPool();
            try {
                SamplerContext ctx = SamplerContext.builder().setUserFields(FIELDS).useBufferArena(pool).enableCaches().build();
                DensitySamplerSet set = f -> sampler(f).bind(ctx);
                Aquifer aquifer = settings.aquifers().isPresent()
                        ? settings.aquifers().get().create(set, random.getOrCreateRandomFactory(AQUIFER), volume, fluids)
                        : Aquifer.createDisabled(fluids);
                DensitySampler.Bound finalDensity = set.get(settings.noiseRouter().finalDensity());
                long t1 = split != null ? System.nanoTime() : 0;
                try (ScopedDensityBuffer density = finalDensity.sampleVolume(volume)) {
                    long t2 = split != null ? System.nanoTime() : 0;
                    for (int y = volume.sizeY() - 1; y >= 0; y--) {
                        int blockY = volume.blockY(y);
                        BlockState s = aquifer.computeSubstance(x, blockY, z, density.get(volume.indexUnchecked(0, y, 0)));
                        BlockState state = s == null ? defaultState : s;
                        if (top < min && SURFACE.test(state)) top = blockY;
                        if (SOLID.test(state)) {
                            floor = blockY;
                            break;
                        }
                    }
                    if (split != null) {
                        long t3 = System.nanoTime();
                        split[0] += t1 - t0;
                        split[1] += t2 - t1;
                        split[2] += t3 - t2;
                    }
                }
            } finally {
                random.releaseDensityBufferPool(pool);
            }
        }
        return ((long) top << 32) | (Math.max(0, top - floor) & 0xffffffffL);
    }

    /**
     * The blocks this reads at a column, top down to the floor, into {@code out} (indexed from the noise's bottom, as the
     * generator's {@code NoiseColumn} is); how many it read. The check compares them with the generator's own column.
     */
    int blocks(int x, int z, BlockState[] out) {
        if (noise.height() <= 0) return 0;
        DensityVolume volume = new DensityVolume(1, noise.height(), 1, x, noise.minY(), z);
        DensityBufferPool pool = random.acquireDensityBufferPool();
        try {
            SamplerContext ctx = SamplerContext.builder().setUserFields(FIELDS).useBufferArena(pool).enableCaches().build();
            DensitySamplerSet set = f -> sampler(f).bind(ctx);
            Aquifer aquifer = settings.aquifers().isPresent()
                    ? settings.aquifers().get().create(set, random.getOrCreateRandomFactory(AQUIFER), volume, fluids)
                    : Aquifer.createDisabled(fluids);
            try (ScopedDensityBuffer density = set.get(settings.noiseRouter().finalDensity()).sampleVolume(volume)) {
                int n = 0;
                for (int y = volume.sizeY() - 1; y >= 0; y--) {
                    BlockState s = aquifer.computeSubstance(x, volume.blockY(y), z, density.get(volume.indexUnchecked(0, y, 0)));
                    BlockState state = s == null ? defaultState : s;
                    out[y] = state;
                    n++;
                    if (SOLID.test(state)) break;
                }
                return n;
            }
        } finally {
            random.releaseDensityBufferPool(pool);
        }
    }

    /** The noise's bottom and its height in blocks (the check's columns). */
    int noiseMinY() {
        return noise.minY();
    }

    int noiseHeight() {
        return noise.height();
    }

    /** The density functions a column looks up (the final density and the aquifer's six): the check times the lookups. */
    DensityFunction[] looked() {
        var a = settings.aquifers();
        DensityFunction fd = settings.noiseRouter().finalDensity();
        if (a.isEmpty()) return new DensityFunction[]{fd};
        var c = a.get();
        return new DensityFunction[]{fd, c.barrierNoise(), c.fluidLevelFloodednessNoise(), c.fluidLevelSpreadNoise(), c.lavaNoise(), c.exclusion(), c.surfaceLevel()};
    }

    RandomState random() {
        return random;
    }
}
