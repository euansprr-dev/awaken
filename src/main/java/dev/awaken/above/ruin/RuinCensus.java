package dev.awaken.above.ruin;

import dev.awaken.Awaken;
import dev.awaken.above.AboveBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The blue rope sites in a square round a point, counted both ways (SPEC §17.11 "Room between them"): by today's rule
 * alone and with the spacing rule, from the seed and the land's own biomes (no chunk made), and the starts that really
 * stand in the chunks loaded now: each on its grid, whole, and how near the next. {@code /awaken above ruin spacing census}.
 */
public final class RuinCensus {
    private RuinCensus() {}

    /** One kind's sites by one rule. */
    public record Spread(int count, double perKm2, int apartMin, int apartP10, int apartMedian, int pointMedian, int pointP95, int pointWorst,
                         double within1500) {
        String line() {
            return String.format(Locale.ROOT, "%d (%.2f per km²), apart: least %d, p10 %d, median %d; from anywhere: median %d, p95 %d, worst %d; within 1,500: %.1f%%",
                    count, perKm2, apartMin, apartP10, apartMedian, pointMedian, pointP95, pointWorst, within1500 * 100);
        }
    }

    /** The real starts in loaded chunks: how many, how many whole, off the grid, and the nearest pairs. */
    public record Real(int spires, int skies, int whole, int checked, int offGrid, int nearestSpires, int nearestSkies, int nearestMixed, List<String> notes) {}

    public record Report(int radius, int spireSpots, int skySpots, Spread spiresBefore, Spread spiresAfter, Spread skiesBefore, Spread skiesAfter,
                         Spread blueBefore, Spread blueAfter, Real real) {
        public List<String> lines() {
            List<String> out = new ArrayList<>();
            out.add(String.format(Locale.ROOT, "square of %,d blocks round you: %d spire spots, %d sky platform spots on the grids", radius * 2, spireSpots, skySpots));
            out.add("Tether Spires before: " + spiresBefore.line());
            out.add("Tether Spires after:  " + spiresAfter.line());
            out.add("sky platforms before: " + skiesBefore.line());
            out.add("sky platforms after:  " + skiesAfter.line());
            out.add("either before: " + blueBefore.line());
            out.add("either after:  " + blueAfter.line());
            out.add(String.format(Locale.ROOT, "standing in loaded chunks: %d spires, %d sky platforms; %d of %d checked whole; %d off the grid; nearest two spires %d, two platforms %d, a spire and a platform %d",
                    real.spires, real.skies, real.whole, real.checked, real.offGrid, real.nearestSpires, real.nearestSkies, real.nearestMixed));
            out.addAll(real.notes);
            return out;
        }
    }

    public static Report count(ServerLevel level, BlockPos origin, int radius) {
        RuinSpacing.Grids grids = RuinSpacing.Grids.of(level.registryAccess(), level.getSeed());
        if (grids == null) throw new IllegalStateException("no spire or sky platform set");
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Long, Boolean> seen = new HashMap<>();
        RuinSpacing.Ground ground = (k, c) -> seen.computeIfAbsent(c.pack() * 31 + k.ordinal(), key -> {
            Structure s = structures.getValue(k.structure);
            return s != null && RuinSpacing.validAt(k, c, s, level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level,
                    level.uncachedBiomeResolver());
        });
        List<ChunkPos> spireSpots = spots(grids, RuinKind.SPIRE, origin, radius), skySpots = spots(grids, RuinKind.SKY, origin, radius);
        List<ChunkPos> spB = new ArrayList<>(), spA = new ArrayList<>(), skB = new ArrayList<>(), skA = new ArrayList<>();
        for (ChunkPos c : skySpots) {
            if (!ground.valid(RuinKind.SKY, c)) continue;
            skB.add(c);
            if (Boolean.TRUE.equals(RuinSpacing.rule(RuinKind.SKY, c, grids, ground))) skA.add(c);
        }
        for (ChunkPos c : spireSpots) {
            if (!ground.valid(RuinKind.SPIRE, c)) continue;
            spB.add(c);
            if (Boolean.TRUE.equals(RuinSpacing.rule(RuinKind.SPIRE, c, grids, ground))) spA.add(c);
        }
        int inner = radius - 1600;
        Real real = real(level, grids, origin, radius);
        Report r = new Report(radius, spireSpots.size(), skySpots.size(), spread(spB, origin, inner), spread(spA, origin, inner), spread(skB, origin, inner),
                spread(skA, origin, inner), spread(both(spB, skB), origin, inner), spread(both(spA, skA), origin, inner), real);
        for (String l : r.lines()) Awaken.LOG.info("[above] spacing census: {}", l);
        return r;
    }

    /**
     * The nearest Tether Spire spot whose land takes one, that the spacing rule keeps or passes over (the seam check's
     * goto), or null within the radius.
     */
    public static @org.jspecify.annotations.Nullable ChunkPos nearestSpire(ServerLevel level, BlockPos origin, int radius, boolean kept) {
        RuinSpacing.Grids grids = RuinSpacing.Grids.of(level.registryAccess(), level.getSeed());
        if (grids == null) return null;
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Long, Boolean> seen = new HashMap<>();
        RuinSpacing.Ground ground = (k, c) -> seen.computeIfAbsent(c.pack() * 31 + k.ordinal(), key -> {
            Structure s = structures.getValue(k.structure);
            return s != null && RuinSpacing.validAt(k, c, s, level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level,
                    level.uncachedBiomeResolver());
        });
        List<ChunkPos> spots = spots(grids, RuinKind.SPIRE, origin, radius);
        ChunkPos me = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        spots.sort(java.util.Comparator.comparingDouble(c -> RuinSpacing.distance(c, me)));
        for (ChunkPos c : spots) {
            if (!ground.valid(RuinKind.SPIRE, c)) continue;
            if (Boolean.TRUE.equals(RuinSpacing.rule(RuinKind.SPIRE, c, grids, ground)) == kept) return c;
        }
        return null;
    }

    /**
     * Dev, for the seam check: the {@code n} nearest spots of each kind are made as far as their structure starts (a cheap
     * step: no blocks), and what the game decided is set beside what the census expects there (its land takes one, and
     * the rule, while it's on, keeps it). A spot made before keeps what it was made with, so run it in fresh land.
     */
    public static List<String> verify(ServerLevel level, BlockPos origin, int n) {
        RuinSpacing.Grids grids = RuinSpacing.Grids.of(level.registryAccess(), level.getSeed());
        if (grids == null) return List.of("no spire or sky platform set");
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var gen = level.getChunkSource().getGenerator();
        var rs = level.getChunkSource().randomState();
        var biomes = level.uncachedBiomeResolver();
        Map<Long, Boolean> seen = new HashMap<>();
        RuinSpacing.Ground ground = (k, c) -> seen.computeIfAbsent(c.pack() * 31 + k.ordinal(), key -> {
            Structure s = structures.getValue(k.structure);
            return s != null && RuinSpacing.validAt(k, c, s, gen, rs, level, biomes);
        });
        List<String> out = new ArrayList<>();
        int agree = 0, total = 0, standing = 0, now = 0, earlier = 0, unseen = 0;
        ChunkPos me = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        for (RuinKind kind : List.of(RuinKind.SPIRE, RuinKind.SKY)) {
            Structure s = structures.getValue(kind.structure);
            if (s == null) continue;
            List<ChunkPos> spots = spots(grids, kind, origin, 2400);
            spots.sort(java.util.Comparator.comparingDouble(c -> RuinSpacing.distance(c, me)));
            for (ChunkPos c : spots.subList(0, Math.min(n, spots.size()))) {
                boolean valid = ground.valid(kind, c);
                // judged by the rule its start was decided under: made land keeps its own (asking may decide it now)
                Boolean before = RuinSpacing.decidedUnder(kind, c);
                var chunk = level.getChunk(c.x(), c.z(), net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, true);
                Boolean under = before != null ? before : RuinSpacing.decidedUnder(kind, c);
                boolean rule = under != null ? under : RuinSpacing.on();
                boolean expected = valid && (!rule || Boolean.TRUE.equals(RuinSpacing.rule(kind, c, grids, ground)));
                String when = before != null ? "decided earlier with spacing " + (before ? "on" : "OFF")
                        : under != null ? "decided by this check" : "decided before this server started";
                if (before != null) earlier++;
                else if (under != null) now++;
                else unseen++;
                StructureStart start = chunk == null ? null : chunk.getStartForStructure(s);
                boolean stands = start != null && start.isValid();
                total++;
                if (stands) standing++;
                if (stands == expected) {
                    agree++;
                    continue;
                }
                BlockPos stub = RuinSpacing.stub(kind, c, gen, rs, level);
                String biome = biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(stub.getX()), net.minecraft.core.QuartPos.fromBlock(stub.getY()),
                        net.minecraft.core.QuartPos.fromBlock(stub.getZ())).getRegisteredName();
                out.add(String.format(Locale.ROOT, "MISMATCH: %s at chunk %s (%.0f away, %s): expected %s (land %s), the game made %s; stub %s in %s%s",
                        kind.displayName, c, RuinSpacing.distance(c, me), when, expected ? "one" : "none", valid ? "takes one" : "refuses",
                        stands ? "one" : "none", stub.toShortString(), biome,
                        dev.awaken.terrain.api.CityBasin.reserved(c.getMinBlockX() + 8, c.getMinBlockZ() + 8) ? ", in the city's reserve" : ""));
            }
        }
        out.add(0, String.format(Locale.ROOT, "verify (spacing %s): %d of %d spots agree with the rule they were decided under (%d decided by this check, %d earlier, %d before this server started), %d standing",
                RuinSpacing.on() ? "on" : "OFF", agree, total, now, earlier, unseen, standing));
        for (String l : out) Awaken.LOG.info("[above] spacing {}", l);
        return out;
    }

    private static List<ChunkPos> both(List<ChunkPos> a, List<ChunkPos> b) {
        List<ChunkPos> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    /** Every spot of the kind's grid whose cell meets the square. */
    static List<ChunkPos> spots(RuinSpacing.Grids grids, RuinKind kind, BlockPos origin, int radius) {
        int sp = grids.of(kind).spacing();
        int cx0 = Math.floorDiv(origin.getX() - radius, 16), cx1 = Math.floorDiv(origin.getX() + radius, 16);
        int cz0 = Math.floorDiv(origin.getZ() - radius, 16), cz1 = Math.floorDiv(origin.getZ() + radius, 16);
        List<ChunkPos> out = new ArrayList<>();
        for (int gx = Math.floorDiv(cx0, sp); gx <= Math.floorDiv(cx1, sp); gx++)
            for (int gz = Math.floorDiv(cz0, sp); gz <= Math.floorDiv(cz1, sp); gz++) {
                ChunkPos c = grids.spot(kind, gx, gz);
                if (c.x() >= cx0 && c.x() <= cx1 && c.z() >= cz0 && c.z() <= cz1) out.add(c);
            }
        return out;
    }

    /** Distances among sites (those inside the inner square) and from points of the inner square to the nearest. */
    static Spread spread(List<ChunkPos> sites, BlockPos origin, int inner) {
        int n = 0;
        List<Double> apart = new ArrayList<>();
        for (ChunkPos a : sites) {
            if (!inside(a, origin, inner)) continue;
            n++;
            double best = Double.MAX_VALUE;
            for (ChunkPos b : sites) if (b != a) best = Math.min(best, RuinSpacing.distance(a, b));
            if (best < Double.MAX_VALUE) apart.add(best);
        }
        List<Double> from = new ArrayList<>();
        int step = Math.max(64, inner / 40);
        for (int x = -inner; x <= inner; x += step)
            for (int z = -inner; z <= inner; z += step) {
                double px = origin.getX() + x, pz = origin.getZ() + z, best = Double.MAX_VALUE;
                for (ChunkPos b : sites) {
                    double dx = b.getMiddleBlockX() - px, dz = b.getMiddleBlockZ() - pz;
                    best = Math.min(best, dx * dx + dz * dz);
                }
                from.add(Math.sqrt(best));
            }
        double[] a = apart.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double[] f = from.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double area = Math.pow(inner * 2 / 1000.0, 2);
        long near = Arrays.stream(f).filter(d -> d <= 1500).count();
        return new Spread(n, n / area, q(a, 0), q(a, 0.1), q(a, 0.5), q(f, 0.5), q(f, 0.95), q(f, 1), f.length == 0 ? 0 : near / (double) f.length);
    }

    private static boolean inside(ChunkPos c, BlockPos origin, int inner) {
        return Math.abs(c.getMiddleBlockX() - origin.getX()) <= inner && Math.abs(c.getMiddleBlockZ() - origin.getZ()) <= inner;
    }

    private static int q(double[] sorted, double f) {
        if (sorted.length == 0) return -1;
        return (int) Math.round(sorted[Math.min(sorted.length - 1, (int) (f * sorted.length))]);
    }

    // ---- what really stands --------------------------------------------------------------------------------------

    /** The starts in the square's loaded chunks: on the grid, whole where all their chunks are loaded, and how near. */
    static Real real(ServerLevel level, RuinSpacing.Grids grids, BlockPos origin, int radius) {
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure spire = structures.getValue(RuinKind.SPIRE.structure), sky = structures.getValue(RuinKind.SKY.structure);
        List<ChunkPos> spires = new ArrayList<>(), skies = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int whole = 0, checked = 0, offGrid = 0;
        int cx0 = Math.floorDiv(origin.getX() - radius, 16), cx1 = Math.floorDiv(origin.getX() + radius, 16);
        int cz0 = Math.floorDiv(origin.getZ() - radius, 16), cz1 = Math.floorDiv(origin.getZ() + radius, 16);
        for (int x = cx0; x <= cx1; x++)
            for (int z = cz0; z <= cz1; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (RuinKind kind : List.of(RuinKind.SPIRE, RuinKind.SKY)) {
                    StructureStart start = chunk.getStartForStructure(kind == RuinKind.SPIRE ? spire : sky);
                    if (start == null || !start.isValid()) continue;
                    ChunkPos at = new ChunkPos(x, z);
                    (kind == RuinKind.SPIRE ? spires : skies).add(at);
                    int sp = grids.of(kind).spacing();
                    if (!grids.spot(kind, Math.floorDiv(x, sp), Math.floorDiv(z, sp)).equals(at)) {
                        offGrid++;
                        notes.add("OFF THE GRID: a " + kind.displayName + " starts at chunk " + at);
                    }
                    var box = start.getBoundingBox();
                    if (!loaded(level, box)) continue;
                    checked++;
                    String missing = kind == RuinKind.SPIRE ? spireMissing(level, start) : skyMissing(level, start);
                    if (missing.isEmpty()) whole++;
                    else notes.add("NOT WHOLE: the " + kind.displayName + " at " + box.getCenter().toShortString() + " lacks " + missing);
                }
            }
        return new Real(spires.size(), skies.size(), whole, checked, offGrid, nearest(spires, spires), nearest(skies, skies), nearest(spires, skies), notes);
    }

    private static int nearest(List<ChunkPos> a, List<ChunkPos> b) {
        double best = Double.MAX_VALUE;
        for (ChunkPos p : a) for (ChunkPos q : b) if (p != q) best = Math.min(best, RuinSpacing.distance(p, q));
        return best == Double.MAX_VALUE ? -1 : (int) Math.round(best);
    }

    private static boolean loaded(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++)
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    /** A spire is whole with its crown and the five platforms' lit hearts, each on its own side of the column. */
    private static String spireMissing(ServerLevel level, StructureStart start) {
        var box = start.getPieces().getFirst().getBoundingBox();
        int cx = box.minX() + RuinBuilder.SPIRE_C, cz = box.minZ() + RuinBuilder.SPIRE_C;
        StringBuilder out = new StringBuilder();
        boolean crown = false;
        for (int y = box.minY() + RuinBuilder.SPIRE_CROWN - 1; y <= box.minY() + RuinBuilder.SPIRE_CROWN + 3; y++)
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(cx, y, cz)).getBlock()).getPath().equals("spire_crown")) crown = true;
        if (!crown) out.append("its crown ");
        for (int k = 0; k < RuinBuilder.SPIRE_PETALS; k++) {
            int py = box.minY() + RuinBuilder.spirePetal(k)[1];
            boolean heart = false;
            for (int dx = -6; dx <= 6 && !heart; dx++)
                for (int dz = -6; dz <= 6 && !heart; dz++)
                    if (Math.abs(dx) + Math.abs(dz) >= 4 && level.getBlockState(new BlockPos(cx + dx, py, cz + dz)).is(AboveBlocks.GLOW_PANEL.get())) heart = true;
            if (!heart) out.append("platform ").append(k + 1).append(' ');
        }
        return out.toString().trim();
    }

    /** A sky platform is whole with its deck out to the rim on all four sides (the disc reads the same in any turn). */
    private static String skyMissing(ServerLevel level, StructureStart start) {
        var box = start.getPieces().getFirst().getBoundingBox();
        int cx = box.minX() + SkyRig.C, cz = box.minZ() + SkyRig.C, deck = box.minY() + SkyRig.DECK;
        StringBuilder out = new StringBuilder();
        int[][] marks = {{10, 0}, {-10, 0}, {0, 10}, {0, -10}, {4, 0}, {0, 4}};
        for (int[] m : marks)
            if (level.getBlockState(new BlockPos(cx + m[0], deck, cz + m[1])).isAir()) out.append("the deck at ").append(m[0]).append(',').append(m[1]).append(' ');
        return out.toString().trim();
    }
}
