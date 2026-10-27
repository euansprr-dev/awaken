package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.living.region.RegionMap;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * What the roads read of this server's overworld from any thread (SPEC §28.9): its seed, the centre (Carillon's site or
 * the origin, as the settlement grid has it), the generator's estimate of the land (one {@link GenTerrain} per thread:
 * their climate samplers cache) and the region map's geometry (hearts, the Commons). Made once at the server's start,
 * after terrain has chosen the city's site and before any chunk generates, the way the first hamlet is found; gone when
 * it stops.
 */
public final class RoadWorld {
    public final long seed;
    public final int cx, cz;
    public final RegionMap regions;
    final NoiseBasedChunkGenerator generator;
    final RandomState random;
    final LevelHeightAccessor heights;
    /** Whether Carillon stands at the centre (its three roads out are the network's ports). */
    public final boolean city;
    /** The region cell of the first town a player meets ({@link FirstTown}), or null. */
    volatile int @Nullable [] firstTown;
    private final ThreadLocal<GenTerrain> terrain;

    private static volatile @Nullable RoadWorld current;

    private RoadWorld(long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, boolean city) {
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.generator = gen;
        this.random = rs;
        this.heights = heights;
        this.city = city;
        this.terrain = ThreadLocal.withInitial(() -> GenTerrain.of(gen, heights, rs));
        var source = gen.getBiomeSource();
        // the probe names a region once, from any thread: a sampler of its own each time (no caches to share)
        this.regions = new RegionMap(seed, cx, cz, (x, z) -> land(source.createResolver(
                rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().build()))
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(63), QuartPos.fromBlock(z))));
    }

    /** The land at a region's heart, named the way the region service names it (the archetype's vanilla name). */
    private static RegionMap.Land land(Holder<Biome> b) {
        var arch = dev.awaken.terrain.api.BiomeTable.archetype(b);
        String path = arch != null ? arch.identifier().getPath() : b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        return dev.awaken.living.region.RegionService.land(path);
    }

    /** This thread's reading of the land. */
    public GenTerrain terrain() {
        return terrain.get();
    }

    public static @Nullable RoadWorld get() {
        return current;
    }

    /** Server start (after terrain chose the city's site): the overworld's generator and noise, read from its stem. */
    public static void open(MinecraftServer server) {
        current = null;
        LevelStem stem = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return;
        long seed = stem.seedOverride().orElse(server.getWorldGenSettings().options().seed());
        RandomState rs = RandomState.create(server.registryAccess().lookupOrThrow(Registries.NOISE), seed, gen.generatorSettings().value());
        var type = stem.type().value();
        int[] c = SettlementGrid.centre();
        RoadWorld w = new RoadWorld(seed, c[0], c[1], gen, rs, LevelHeightAccessor.create(type.minY(), type.height()),
                dev.awaken.terrain.api.CityBasin.current() != null);
        RoadTimer.server = server.getRunningThread();
        w.prepare();
        current = w;
    }

    /** What's found once for the session: the first town. */
    void prepare() {
        long t0 = System.currentTimeMillis();
        firstTown = FirstTown.find(this);
        int[] f = firstTown;
        Awaken.LOG.info("[road] the roads' world: seed {}, centre {} {}{}; the first town's region {} ({} ms)", seed, cx, cz, city ? " (Carillon)" : "",
                f == null ? "none" : f[0] + "," + f[1], System.currentTimeMillis() - t0);
    }

    /** Tests and the studio: a world over a generator made for a seed (no server levels). */
    public static RoadWorld of(long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, boolean city) {
        RoadWorld w = new RoadWorld(seed, cx, cz, gen, rs, heights, city);
        w.prepare();
        return w;
    }

    /** Tests and the studio: make this the server's world for a while (null puts none). */
    public static void use(@Nullable RoadWorld w) {
        current = w;
    }

    public static void close() {
        current = null;
    }
}
