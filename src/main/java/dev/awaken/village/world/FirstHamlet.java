package dev.awaken.village.world;

import dev.awaken.Awaken;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The first hamlet, placed on purpose (SPEC §28.6, PLAN 5.2): up the road north of Carillon (or of a classic world's
 * spawn), 760-1,060 blocks out past the Wayfarer camp (450). At the server's start, after terrain has chosen the city's
 * site and before any chunk generates, the spots along that road are tried nearest the nominal first (straight up the
 * road, then a little to either side) until the land takes a hamlet; the settlement grid then puts its start there.
 * The same seed and land always find the same spot.
 */
public final class FirstHamlet {
    private FirstHamlet() {}

    static final int[] OUT = {820, 760, 880, 940, 1000, 1060};
    static final int[] SIDE = {0, -96, 96, -192, 192};

    public static void find(MinecraftServer server) {
        SettlementGrid.firstFound = null;
        LevelStem stem = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getValue(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator gen)) return;
        long seed = stem.seedOverride().orElse(server.getWorldGenSettings().options().seed());
        long t0 = System.currentTimeMillis();
        RandomState rs = RandomState.create(server.registryAccess().lookupOrThrow(Registries.NOISE), seed, gen.generatorSettings().value());
        var type = stem.type().value();
        LevelHeightAccessor heights = LevelHeightAccessor.create(type.minY(), type.height());
        GenTerrain t = GenTerrain.of(gen, heights, rs);
        int[] centre = SettlementGrid.centre();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("awaken").resolve(FILE);
        Saved saved = read(file, seed, centre);
        if (saved != null) {
            // found when this world was first opened (SPEC §28.6): the search read up to 480 plans' land on the server
            // thread at every start (16 s on Euan's world of 12 Nov, six minutes on a seed whose roads take none)
            SettlementGrid.Anchor a = saved.found() ? new SettlementGrid.Anchor("first", saved.x(), saved.z(), VillagePlan.Kind.HAMLET, true,
                    SettlementGrid.nominalFirst(seed, centre).seed()) : null;
            SettlementGrid.firstFound = a;
            // its plan made off the server thread, last, for chunks of it not made yet (world generation makes it itself
            // if it gets there first: whichever asks first makes it, the other waits); never into the next world's plans
            if (a != null) dev.awaken.core.api.LandFirst.later().execute(() -> {
                if (server.isRunning() && SettlementGrid.firstFound == a) Plans.of(a, t);
            });
            Awaken.LOG.info("[village] the first hamlet's spot: {} (found when this world was first opened)",
                    a != null ? a.x() + " " + a.z() : "the nominal one");
            return;
        }
        VillagePlan[] found = new VillagePlan[1];
        // before any chunk is made nothing else needs the cores: all but two plan the spots at once
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
        SettlementGrid.Anchor a = firstTaken(spots(seed, centre), () -> GenTerrain.of(gen, heights, rs), threads, found);
        SettlementGrid.firstFound = a;
        write(file, seed, centre, a);
        // the plan the search just made is the one world generation would make (a pure function of anchor and land):
        // kept, so the structure start doesn't plan it again inside generation (5.5 s on the worldgen queue, 26 Oct)
        if (found[0] != null) Plans.put(found[0]);
        if (a != null) Awaken.LOG.info("[village] the first hamlet's spot: {} {} ({} blocks up the road, {} to the side) found in {} ms", a.x(), a.z(),
                centre[1] - a.z(), a.x() - centre[0], System.currentTimeMillis() - t0);
        else Awaken.LOG.info("[village] no spot up the road takes the first hamlet ({} ms): the nominal one stands", System.currentTimeMillis() - t0);
    }

    /** The world's answer: {@code <world>/awaken/first_hamlet}, one line "1 seed centreX centreZ x z" (or "none"). */
    static final String FILE = "first_hamlet";
    static final int SCHEMA = 1;

    record Saved(boolean found, int x, int z) {}

    /** The saved answer for this seed and centre, or null (none saved, another centre, or unreadable: searched again). */
    static @Nullable Saved read(Path file, long seed, int[] centre) {
        try {
            if (!Files.exists(file)) return null;
            String[] w = Files.readString(file).trim().split("\\s+");
            if (w.length < 5 || Integer.parseInt(w[0]) != SCHEMA || Long.parseLong(w[1]) != seed
                    || Integer.parseInt(w[2]) != centre[0] || Integer.parseInt(w[3]) != centre[1]) return null;
            if (w[4].equals("none")) return new Saved(false, 0, 0);
            return w.length >= 6 ? new Saved(true, Integer.parseInt(w[4]), Integer.parseInt(w[5])) : null;
        } catch (java.io.IOException | RuntimeException e) {
            Awaken.LOG.warn("[village] can't read {}: {}", file, e.toString());
            return null;
        }
    }

    static void write(Path file, long seed, int[] centre, SettlementGrid.@Nullable Anchor a) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, SCHEMA + " " + seed + " " + centre[0] + " " + centre[1] + " " + (a != null ? a.x() + " " + a.z() : "none") + "\n");
        } catch (java.io.IOException e) {
            Awaken.LOG.warn("[village] can't write {}: {}", file, e.toString());
        }
    }

    /** The studio: where this server found the first hamlet (or null), and to put another there for a while. */
    public static SettlementGrid.@org.jspecify.annotations.Nullable Anchor found() {
        return SettlementGrid.firstFound;
    }

    public static void use(SettlementGrid.@org.jspecify.annotations.Nullable Anchor a) {
        SettlementGrid.firstFound = a;
    }

    /**
     * The first spot up the road (nearest the nominal one first) whose land takes a hamlet, or null. North is the camp's
     * road; where the land north is sea or mountain the other roads out are tried (east, west, south), then a little
     * further out.
     */
    public static SettlementGrid.@org.jspecify.annotations.Nullable Anchor search(long seed, int[] centre, dev.awaken.village.plan.Terrain t) {
        return search(seed, centre, t, new VillagePlan[1]);
    }

    /** The most a creature's land may be at the first hamlet (SPEC §21.10): the first place a level-3 player goes. */
    public static final int MAX_LEVEL = 12;

    /**
     * As {@link #search}, handing back the winning spot's plan in {@code found[0]}. The land's level is checked too
     * (SPEC §21.10: nothing the first thread sends you to is a trap): the spots at level {@link #MAX_LEVEL} or less are
     * tried first, then, only if none takes a hamlet, any. The level reads the region map with no biome tilt, which moves
     * a land by a couple of levels at most.
     */
    static SettlementGrid.@org.jspecify.annotations.Nullable Anchor search(long seed, int[] centre, dev.awaken.village.plan.Terrain t, VillagePlan[] found) {
        return firstTaken(spots(seed, centre), () -> t, 1, found);
    }

    /**
     * The spots in the order they're tried: up the roads, nearest the nominal one first, those at {@link #MAX_LEVEL} or
     * less, then the rest in the same order, each spot once. (The first that takes a hamlet is the one the old two passes
     * found: a plan is a pure function of anchor and land, so a spot tried once and refused is refused again.)
     */
    static List<SettlementGrid.Anchor> spots(long seed, int[] centre) {
        var map = new dev.awaken.living.region.RegionMap(seed, centre[0], centre[1], (x, z) -> dev.awaken.living.region.RegionMap.Land.PLAIN);
        SettlementGrid.Anchor nominal = SettlementGrid.nominalFirst(seed, centre);
        int[][] roads = {{0, -1}, {1, 0}, {-1, 0}, {0, 1}};
        LinkedHashSet<SettlementGrid.Anchor> low = new LinkedHashSet<>(), high = new LinkedHashSet<>();
        for (int pass = 0; pass < 2; pass++) {
            for (int[] road : roads) {
                for (int out : OUT) {
                    int far = out + pass * 240;
                    for (int side : SIDE) {
                        int x = centre[0] + road[0] * far + (road[0] == 0 ? 6 + side : 0), z = centre[1] + road[1] * far + (road[1] == 0 ? side : 0);
                        boolean nominalSpot = pass == 0 && road[1] == -1 && out == OUT[0] && side == 0;
                        SettlementGrid.Anchor a = nominalSpot ? nominal : new SettlementGrid.Anchor("first", x, z, VillagePlan.Kind.HAMLET, true, nominal.seed());
                        (map.levelAt(a.x(), a.z()) > MAX_LEVEL ? high : low).add(a);
                    }
                }
            }
        }
        high.removeAll(low);
        List<SettlementGrid.Anchor> out = new ArrayList<>(low);
        out.addAll(high);
        return out;
    }

    /** A site, and room for a proper hamlet there: four homes at least (a plan is null without a site). */
    static boolean takes(@Nullable VillagePlan p) {
        return p != null && p.buildings.stream().filter(b -> b.use().equals("house")).count() >= 4;
    }

    /**
     * The first of {@code spots} (in order) whose land takes a hamlet, its plan in {@code found[0]}; null if none. With
     * more than one thread the spots are planned that many at a time, each thread reading the land its own way
     * ({@code land} gives each its terrain), and the earliest that takes it wins: the same spot one thread finds, in a
     * fraction of the time (each plan reads a few thousand noise columns: 315 s for the 480 tries of a seed whose roads
     * take none, on GitHub's runner, 12 Nov).
     */
    static SettlementGrid.@Nullable Anchor firstTaken(List<SettlementGrid.Anchor> spots, Supplier<dev.awaken.village.plan.Terrain> land, int threads,
                                                     VillagePlan[] found) {
        if (threads <= 1) {
            dev.awaken.village.plan.Terrain t = land.get();
            for (SettlementGrid.Anchor a : spots) {
                VillagePlan p = VillagePlanner.plan(Plans.request(a), t);
                if (takes(p)) {
                    found[0] = p;
                    return a;
                }
            }
            return null;
        }
        ThreadLocal<dev.awaken.village.plan.Terrain> mine = ThreadLocal.withInitial(land);
        AtomicInteger k = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread th = new Thread(r, "Awaken first hamlet " + k.incrementAndGet());
            th.setDaemon(true);
            return th;
        });
        try {
            for (int from = 0; from < spots.size(); from += threads) {
                List<Future<VillagePlan>> plans = new ArrayList<>();
                for (int i = from; i < Math.min(spots.size(), from + threads); i++) {
                    SettlementGrid.Anchor a = spots.get(i);
                    plans.add(pool.submit(() -> VillagePlanner.plan(Plans.request(a), mine.get())));
                }
                for (int j = 0; j < plans.size(); j++) {
                    VillagePlan p = plans.get(j).get();
                    if (takes(p)) {
                        found[0] = p;
                        return spots.get(from + j);
                    }
                }
            }
            return null;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }
}
