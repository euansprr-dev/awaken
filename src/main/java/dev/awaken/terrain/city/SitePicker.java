package dev.awaken.terrain.city;

import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Chooses where Carillon stands, from the generator alone (no chunks), before the world's levels exist: land near the
 * origin whose ground already sits near street level, gentle across the core and low along the city's fixed river, with
 * land around it. Deterministic: the same generator and seed always give the same site.
 */
public final class SitePicker {
    private SitePicker() {}

    /** Candidate centres: the origin, then rings every 160 blocks out to 1,440. */
    static List<int[]> candidates() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{0, 0});
        for (int r = 160; r <= 1440; r += 160) {
            int n = Math.max(8, (int) Math.round(2 * Math.PI * r / 220));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                out.add(new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)});
            }
        }
        return out;
    }

    /** The site for this generator; never null (the best candidate wins even if none is ideal). */
    public static CitySite pick(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, boolean carved) {
        int sea = gen.getSeaLevel();
        List<int[]> cands = candidates();
        ForkJoinPool pool = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 2));
        try {
            // coarse pass: nine columns each, land and closeness to street level
            double[] coarse = pool.submit(() -> cands.parallelStream().mapToDouble(c -> {
                double s = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int f = floor(gen, rs, level, c[0] + dx * 200, c[1] + dz * 200);
                        s += f < sea + 2 ? 60 : Math.abs(f - CityBasin.STREET_Y);
                    }
                }
                return s / 9 + Math.hypot(c[0], c[1]) / 100.0;
            }).toArray()).join();
            Integer[] order = new Integer[cands.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingDouble(i -> coarse[i]));
            int keep = Math.min(10, order.length);
            // fine pass on the best ten
            double[] fine = pool.submit(() -> Arrays.stream(order, 0, keep).parallel()
                    .mapToDouble(i -> score(gen, rs, level, cands.get(i)[0], cands.get(i)[1], sea)).toArray()).join();
            int best = 0;
            for (int k = 1; k < keep; k++) if (fine[k] < fine[best]) best = k;
            int[] c = cands.get(order[best]);
            return site(c[0], c[1], carved);
        } finally {
            pool.shutdown();
        }
    }

    /** Lower is better. */
    static double score(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, int cx, int cz, int sea) {
        int street = CityBasin.STREET_Y, core = CityBasin.CORE_RADIUS;
        List<Integer> floors = new ArrayList<>();
        int land = 0, n = 0;
        double off = 0;
        for (int dx = -core; dx <= core; dx += 33) {
            for (int dz = -core; dz <= core; dz += 33) {
                if (dx * dx + dz * dz > core * core) continue;
                int f = floor(gen, rs, level, cx + dx, cz + dz);
                floors.add(f);
                n++;
                if (f >= sea + 2) land++;
                off += Math.abs(f - street);
            }
        }
        floors.sort(null);
        double relief = floors.get((int) (floors.size() * 0.9)) - floors.get((int) (floors.size() * 0.1));
        double landShare = land / (double) n;
        // along the city's river: low ground is a gift, a ridge across it is work
        double river = 0;
        for (int[] p : CityBasin.RIVER) river += Math.max(0, floor(gen, rs, level, cx + p[0], cz + p[1]) - (street - 4));
        river /= CityBasin.RIVER.size();
        // the ring the basin blends into: near street level means a gentle blend
        double ring = 0;
        for (int i = 0; i < 16; i++) {
            double a = 2 * Math.PI * i / 16;
            int f = floor(gen, rs, level, cx + (int) (Math.cos(a) * 520), cz + (int) (Math.sin(a) * 520));
            ring += Math.min(60, Math.abs(f - street));
        }
        ring /= 16;
        double s = off / n + 0.6 * relief + 0.5 * river + 0.4 * ring + Math.hypot(cx, cz) / 120.0;
        if (landShare < 0.85) s += 400 * (0.85 - landShare);
        return s;
    }

    static int floor(ChunkGenerator gen, RandomState rs, LevelHeightAccessor level, int x, int z) {
        return gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
    }

    /** The site at (cx, cz): the city's river in world coordinates, carried on past the core to the blend edge. */
    public static CitySite site(int cx, int cz, boolean carved) {
        List<int[]> core = CityBasin.RIVER;
        List<int[]> river = new ArrayList<>();
        int[] first = core.get(0), second = core.get(1);
        for (int[] p : extend(first, second)) river.add(0, new int[]{cx + p[0], cz + p[1]});
        for (int[] p : core) river.add(new int[]{cx + p[0], cz + p[1]});
        int[] last = core.get(core.size() - 1), before = core.get(core.size() - 2);
        for (int[] p : extend(last, before)) river.add(new int[]{cx + p[0], cz + p[1]});
        return new CitySite(cx, cz, CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, CityBasin.STREET_Y, river,
                CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, carved);
    }

    /** Points continuing the line from {@code inner} through {@code end} out to the blend radius, nearest first. */
    static List<int[]> extend(int[] end, int[] inner) {
        double dx = end[0] - inner[0], dz = end[1] - inner[1];
        double len = Math.hypot(dx, dz);
        dx /= len;
        dz /= len;
        List<int[]> out = new ArrayList<>();
        for (int step = 120; ; step += 120) {
            int x = (int) Math.round(end[0] + dx * step), z = (int) Math.round(end[1] + dz * step);
            out.add(new int[]{x, z});
            if (Math.hypot(x, z) >= CityBasin.BLEND_RADIUS) break;
        }
        return out;
    }
}
