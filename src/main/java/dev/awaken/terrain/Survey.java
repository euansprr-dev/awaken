package dev.awaken.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * The terrain studio's eyes: surface height and surface biome straight from the generator's noise, without generating
 * a chunk. Safe off the main thread (the generator's samplers are; each worker gets its own caching resolver).
 */
public final class Survey {
    private Survey() {}

    /** A square grid of samples, row-major from its north-west corner. Water where {@code floor < seaLevel}. */
    public record Grid(String name, long seed, int originX, int originZ, int step, int size, int seaLevel, int minY, int maxY,
                       int[] floor, int[] biome, List<String> palette, long millis) {
        public int at(int col, int row) {
            return row * size + col;
        }
    }

    /** Where one surface sample stands: the solid ground height and the biome at that height (sea level over water). */
    public record Spot(int x, int z, int floor, Holder<Biome> biome) {}

    public static Spot spot(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, BiomeResolver resolver, int x, int z) {
        int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        int y = Math.max(floor, gen.getSeaLevel());
        return new Spot(x, z, floor, resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
    }

    /** Samples {@code (2 * radius / step + 1)²} columns centred on (cx, cz), spread over worker threads. */
    public static Grid sample(String name, long seed, ChunkGenerator gen, RandomState rs, LevelHeightAccessor level,
                              int cx, int cz, int radius, int step) {
        long t0 = System.currentTimeMillis();
        int size = 2 * (radius / step) + 1;
        int ox = cx - (size / 2) * step, oz = cz - (size / 2) * step;
        int[] floor = new int[size * size];
        @SuppressWarnings("unchecked") Holder<Biome>[] biomes = new Holder[size * size];
        ForkJoinPool pool = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
        try {
            pool.submit(() -> IntStream.range(0, size).parallel().forEach(row -> {
                BiomeResolver resolver = gen.getBiomeSource().createCachingResolver(rs);
                for (int col = 0; col < size; col++) {
                    Spot s = spot(gen, rs, level, resolver, ox + col * step, oz + row * step);
                    floor[row * size + col] = s.floor();
                    biomes[row * size + col] = s.biome();
                }
            })).join();
        } finally {
            pool.shutdown();
        }
        Map<Holder<Biome>, Integer> index = new HashMap<>();
        List<String> palette = new ArrayList<>();
        int[] biome = new int[size * size];
        for (int i = 0; i < biome.length; i++) {
            biome[i] = index.computeIfAbsent(biomes[i], h -> {
                palette.add(id(h));
                return palette.size() - 1;
            });
        }
        return new Grid(name, seed, ox, oz, step, size, gen.getSeaLevel(), level.getMinY(), level.getMaxY(), floor, biome,
                palette, System.currentTimeMillis() - t0);
    }

    /** A patch smaller than this many dry samples of the 13 x 13 round a hit (24 blocks apart) is passed over. */
    static final int MIN_PATCH = 45;

    /**
     * The middle of the nearest sizeable surface patch of a biome on dry land: rings of samples outward from (x, z)
     * every 64 blocks (a cheap climate check at three heights first, the real surface only for candidates); each hit is
     * measured on a 13 x 13 grid round it and slivers and lake beds are passed over (the biggest is kept if nothing
     * else turns up); then twice toward the centroid of the matching samples. Null if none within {@code radius}.
     */
    public static Spot findHeart(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, Predicate<Holder<Biome>> want,
                                 int x, int z, int radius) {
        BiomeResolver resolver = gen.getBiomeSource().createCachingResolver(rs);
        int sea = gen.getSeaLevel();
        int[] probeYs = {sea, sea + 40, sea + 100};
        Spot hit = null, best = null;
        int bestCount = 0, measured = 0;
        java.util.List<Spot> passed = new java.util.ArrayList<>();
        search:
        for (int r = 0; r <= radius; r += 64) {
            int n = r == 0 ? 1 : Math.max(8, (int) Math.round(2 * Math.PI * r / 64));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int sx = x + (int) Math.round(Math.cos(a) * r), sz = z + (int) Math.round(Math.sin(a) * r);
                boolean maybe = false;
                for (int y : probeYs) {
                    if (want.test(resolver.getNoiseBiome(QuartPos.fromBlock(sx), QuartPos.fromBlock(y), QuartPos.fromBlock(sz)))) {
                        maybe = true;
                        break;
                    }
                }
                if (!maybe) continue;
                Spot s = spot(gen, rs, level, resolver, sx, sz);
                // dry land: a forest biome also runs under Tectonic's lakes
                if (!want.test(s.biome()) || s.floor() < sea) continue;
                boolean near = false;
                for (Spot q : passed) near |= Math.abs(q.x() - sx) < 240 && Math.abs(q.z() - sz) < 240;
                if (near) continue;
                int count = patch(gen, rs, level, resolver, want, sea, sx, sz, null);
                if (count > bestCount) {
                    best = s;
                    bestCount = count;
                }
                if (count >= MIN_PATCH || ++measured >= 16) {
                    hit = count >= MIN_PATCH ? s : best;
                    break search;
                }
                passed.add(s);
            }
        }
        if (hit == null) hit = best;
        if (hit == null) return null;
        for (int pass = 0; pass < 2; pass++) {
            long[] sum = new long[2];
            int count = patch(gen, rs, level, resolver, want, sea, hit.x(), hit.z(), sum);
            Spot centre = spot(gen, rs, level, resolver, (int) (sum[0] / count), (int) (sum[1] / count));
            if (!want.test(centre.biome()) || centre.floor() < sea) break; // a ring-shaped patch, or a lake in the middle
            hit = centre;
        }
        return hit;
    }

    /** Dry samples of the biome on a 13 x 13 grid 24 blocks apart round (x, z); their x and z sums go into {@code sum}. */
    private static int patch(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, BiomeResolver resolver, Predicate<Holder<Biome>> want,
                             int sea, int x, int z, long[] sum) {
        int count = 0;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int sx = x + dx * 24, sz = z + dz * 24;
                Spot p = spot(gen, rs, level, resolver, sx, sz);
                if (want.test(p.biome()) && p.floor() >= sea) {
                    count++;
                    if (sum != null) {
                        sum[0] += sx;
                        sum[1] += sz;
                    }
                }
            }
        }
        return count;
    }

    /** The highest dry ground within {@code radius} of (x, z), sampled every 16 blocks. */
    public static Spot lookout(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, int x, int z, int radius) {
        BiomeResolver resolver = gen.getBiomeSource().createCachingResolver(rs);
        Spot best = null;
        for (int dx = -radius; dx <= radius; dx += 16) {
            for (int dz = -radius; dz <= radius; dz += 16) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Spot s = spot(gen, rs, level, resolver, x + dx, z + dz);
                if (s.floor() >= gen.getSeaLevel() && (best == null || s.floor() > best.floor())) best = s;
            }
        }
        return best;
    }

    public static String id(Holder<Biome> h) {
        return h.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    /** Writes the grid as compact JSON (schemaVersion 1) for tools/terrain/survey.py. */
    public static Path write(Grid g, Path dir) throws IOException {
        Files.createDirectories(dir);
        StringBuilder b = new StringBuilder(g.floor().length * 8);
        b.append("{\"schemaVersion\":1,\"name\":\"").append(g.name()).append("\",\"seed\":").append(g.seed())
                .append(",\"originX\":").append(g.originX()).append(",\"originZ\":").append(g.originZ())
                .append(",\"step\":").append(g.step()).append(",\"size\":").append(g.size())
                .append(",\"seaLevel\":").append(g.seaLevel()).append(",\"minY\":").append(g.minY()).append(",\"maxY\":").append(g.maxY())
                .append(",\"millis\":").append(g.millis()).append(",\"palette\":[");
        for (int i = 0; i < g.palette().size(); i++) b.append(i == 0 ? "" : ",").append('"').append(g.palette().get(i)).append('"');
        b.append("],\"floor\":[");
        for (int i = 0; i < g.floor().length; i++) b.append(i == 0 ? "" : ",").append(g.floor()[i]);
        b.append("],\"biome\":[");
        for (int i = 0; i < g.biome().length; i++) b.append(i == 0 ? "" : ",").append(g.biome()[i]);
        b.append("]}");
        Path file = dir.resolve(g.name() + ".json");
        Files.writeString(file, b, StandardCharsets.UTF_8);
        return file;
    }
}
