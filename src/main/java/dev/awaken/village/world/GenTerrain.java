package dev.awaken.village.world;

import dev.awaken.city.VillageStyles;
import dev.awaken.terrain.api.BiomeTable;
import dev.awaken.village.plan.Terrain;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Locale;

/**
 * The land as the generator estimates it (no chunk loads): the top of each column with and without its water, and the
 * biome's family. What the planner reads at world generation, in commands and when a settlement's people start.
 */
public record GenTerrain(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random, BiomeResolver biomes) implements Terrain {
    /** Noise columns the generator has worked out for the planners, on every thread (the roads' timer and bench). */
    public static final java.util.concurrent.atomic.LongAdder COLUMNS = new java.util.concurrent.atomic.LongAdder();

    /** For one thread's planning: its own biome resolver (the climate sampler caches). */
    public static GenTerrain of(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random) {
        var sampler = random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build());
        return new GenTerrain(generator, heights, random, generator.getBiomeSource().createResolver(sampler));
    }

    @Override
    public int top(int x, int z) {
        COLUMNS.increment();
        return generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
    }

    @Override
    public int water(int x, int z) {
        int top = top(x, z);
        COLUMNS.increment();
        int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
        return Math.max(0, top - floor);
    }

    /**
     * Both heights from one noise column: the generator samples the whole column either way, so asking twice (once per
     * heightmap) cost two full columns per read, and a hamlet's plan reads ~1,600 columns inside world generation.
     * Scanned top down with each heightmap's own test, it gives exactly what {@code getFirstOccupiedHeight} gives.
     */
    @Override
    public long both(int x, int z) {
        COLUMNS.increment();
        net.minecraft.world.level.NoiseColumn col = generator.getBaseColumn(x, z, heights, random);
        int min = heights.getMinY(), top = min - 1, floor = min - 1;
        if (col != null) {
            var surface = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
            var solid = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
            for (int y = heights.getMaxY(); y >= min; y--) {
                var state = col.getBlock(y);
                if (top < min && surface.test(state)) top = y;
                if (solid.test(state)) {
                    floor = y;
                    break;
                }
            }
        }
        return ((long) top << 32) | (Math.max(0, top - floor) & 0xffffffffL);
    }

    @Override
    public int family(int x, int z) {
        int y = top(x, z);
        Holder<Biome> b = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        return family(b);
    }

    @Override
    public int seaLevel() {
        return generator.getSeaLevel();
    }

    /** A biome's family: the Awakened biomes by name, the rest by their (archetype's) vanilla name. */
    public static int family(Holder<Biome> b) {
        String own = b.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        switch (own) {
            case "awaken:elderwood", "awaken:lumen_forest" -> {
                return VillageStyles.ELDER;
            }
            case "awaken:frost_spires" -> {
                return VillageStyles.FROST;
            }
            case "awaken:glass_steppe" -> {
                return VillageStyles.SAVANNA;
            }
            case "awaken:mirror_marsh" -> {
                return VillageStyles.MIRE;
            }
            case "awaken:shattered_highlands" -> {
                return VillageStyles.HIGHLAND;
            }
            default -> {
            }
        }
        ResourceKey<Biome> arch = BiomeTable.archetype(b);
        String p = arch == null ? "plains" : arch.identifier().getPath().toLowerCase(Locale.ROOT);
        if (p.contains("desert") || p.contains("badlands")) return VillageStyles.DESERT;
        if (p.contains("savanna")) return VillageStyles.SAVANNA;
        if (p.contains("swamp") || p.contains("mangrove")) return VillageStyles.MIRE;
        if (p.contains("jungle") || p.contains("bamboo")) return VillageStyles.JUNGLE;
        if (p.contains("cherry")) return VillageStyles.CHERRY;
        if (p.contains("dark_forest") || p.contains("pale_garden")) return VillageStyles.ELDER;
        if (p.contains("snowy") || p.contains("frozen") || p.contains("ice") || p.contains("grove") || p.contains("taiga")) return VillageStyles.FROST;
        if (p.contains("windswept") || p.contains("peaks") || p.contains("slopes") || p.contains("stony")) return VillageStyles.HIGHLAND;
        return VillageStyles.TEMPERATE;
    }
}
