package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.living.region.RegionMap;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.SettlementGrid;
import dev.awaken.village.world.SharedLand;
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
 * the origin, as the settlement grid has it), the generator's estimate of the land (one {@link SharedLand}: each column
 * worked out once for every thread) and the region map's geometry (hearts, the Commons). Made once at the server's start,
 * after terrain has chosen the city's site and before any chunk generates, the way the first hamlet is found; gone when
 * it stops.
 */
public final class RoadWorld {
    public final long seed;
    public final int cx, cz;
    /**
     * Where the land's danger is measured from: a Far Landing world's landing (SPEC §67), else the centre. The first town
     * and the holdfasts are found round it; the roads themselves still run from the city.
     */
    public final int lx, lz;
    public final RegionMap regions;
    final NoiseBasedChunkGenerator generator;
    final RandomState random;
    final LevelHeightAccessor heights;
    /** Whether Carillon stands at the centre (its three roads out are the network's ports). */
    public final boolean city;
    /** The region cell of the first town a player meets ({@link FirstTown}), or null. */
    volatile int @Nullable [] firstTown;
    /** The region cell of the first holdfast ({@link Holdfasts}), or null. */
    volatile int @Nullable [] firstHoldfast;
    /** The generator's estimate of the land, each column worked out once for every thread (one {@link GenTerrain} per thread). */
    private final SharedLand land;

    private static volatile @Nullable RoadWorld current;

    private RoadWorld(long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, boolean city, int rules) {
        this(seed, cx, cz, gen, rs, heights, city, rules, null);
    }

    private RoadWorld(long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, boolean city, int rules,
                      int @Nullable [] landing) {
        this.seed = seed;
        this.cx = cx;
        this.cz = cz;
        this.lx = landing != null ? landing[0] : cx;
        this.lz = landing != null ? landing[1] : cz;
        this.generator = gen;
        this.random = rs;
        this.heights = heights;
        this.city = city;
        this.land = new SharedLand(ThreadLocal.withInitial(() -> GenTerrain.of(gen, heights, rs)));
        var source = gen.getBiomeSource();
        // the probe names a region once, from any thread: a sampler of its own each time (no caches to share)
        this.regions = new RegionMap(seed, cx, cz, (x, z) -> land(source.createResolver(
                rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().build()))
                .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(63), QuartPos.fromBlock(z))), rules, lx, lz);
    }

    /** Whether this world's real game starts at a far landing (SPEC §67): its first town and holdfasts are round it. */
    public boolean farLanding() {
        return regions.farLanding();
    }

    /**
     * A Far Landing world's landing (SPEC §67), found from the seed before any chunk is made: the saved one, else found now
     * on this generator (the same answer the region map gets: the finder is pure and keeps it for the session).
     */
    private static int[] landing(MinecraftServer server, long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights) {
        int[] saved = dev.awaken.living.region.RegionService.landingOnDisk(server);
        if (saved != null) return saved;
        var source = gen.getBiomeSource();
        var resolver = source.createResolver(rs.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().build()));
        GenTerrain t = GenTerrain.of(gen, heights, rs);
        return dev.awaken.core.api.FarLanding.of(seed, cx, cz, new dev.awaken.core.api.FarLanding.Sampler() {
            @Override
            public String biome(int x, int z) {
                return dev.awaken.living.region.RegionService.path(resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(63), QuartPos.fromBlock(z)));
            }

            @Override
            public int top(int x, int z) {
                return t.top(x, z) - t.water(x, z);
            }

            @Override
            public int seaLevel() {
                return t.seaLevel();
            }
        });
    }

    /** The land at a region's heart, named the way the region service names it (the archetype's vanilla name). */
    private static RegionMap.Land land(Holder<Biome> b) {
        var arch = dev.awaken.terrain.api.BiomeTable.archetype(b);
        String path = arch != null ? arch.identifier().getPath() : b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        return dev.awaken.living.region.RegionService.land(path);
    }

    /** The land as every planner reads it. */
    public Terrain terrain() {
        return land;
    }

    /**
     * The shared reading of the land in place of a reading of the same generator's (a settlement's plan made at world
     * generation, by its people or by a command reads the columns the roads and the other plans already read).
     */
    public static Terrain shared(Terrain t) {
        RoadWorld w = current;
        return w != null && t instanceof GenTerrain g && g.generator() == w.generator ? w.land : t;
    }

    /** Forgets the land read so far (the bench's cold runs). */
    void forget() {
        land.clear();
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
        var heights = LevelHeightAccessor.create(type.minY(), type.height());
        boolean city = dev.awaken.terrain.api.CityBasin.current() != null;
        int rules = dev.awaken.living.region.RegionService.rulesOnDisk(server);
        int[] landing = city && rules >= 4 ? landing(server, seed, c[0], c[1], gen, rs, heights) : null;
        RoadWorld w = new RoadWorld(seed, c[0], c[1], gen, rs, heights, city, rules, landing);
        RoadTimer.server = server.getRunningThread();
        RoadTimer.watch(server);
        w.prepare();
        current = w;
    }

    /** What's found once for the session: the first town, then the first holdfast (SPEC §43.2). */
    void prepare() {
        long t0 = System.currentTimeMillis();
        firstTown = FirstTown.find(this);
        Holdfasts.clear();
        firstHoldfast = Holdfasts.findFirst(this);
        int[] f = firstTown, h = firstHoldfast;
        Awaken.LOG.info("[road] the roads' world: seed {}, centre {} {}{}{}; the first town's region {}, the first holdfast's {} ({} ms)", seed, cx, cz,
                city ? " (Carillon)" : "", farLanding() ? ", the far landing " + lx + " " + lz : "",
                f == null ? "none" : f[0] + "," + f[1], h == null ? "none" : h[0] + "," + h[1], System.currentTimeMillis() - t0);
    }

    /** Tests and the studio: a world over a generator made for a seed (no server levels). */
    public static RoadWorld of(long seed, int cx, int cz, NoiseBasedChunkGenerator gen, RandomState rs, LevelHeightAccessor heights, boolean city) {
        RoadWorld w = new RoadWorld(seed, cx, cz, gen, rs, heights, city, RegionMap.RULES);
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
