package dev.awaken.delve.cave;

import dev.awaken.delve.Lode;
import dev.awaken.delve.LodePlans;
import dev.awaken.delve.Ore;
import dev.awaken.terrain.api.CityBasin;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a cave place goes (PLAN 8.2): the lair's cave placement (the right depth, rock overhead, a biome that fits)
 * plus what the lairs never checked, natural cave air nearby to join, read from the generator's own base columns (the
 * noise caves, aquifers and all, before any chunk exists). A sinkhole is laid beside a planned lode so its grotto holds
 * one (W2's {@code LodePlans}); a seep round a region's Deepglass lode (it's where the Deep presses in); a mine where a
 * hillside rises for its adit. Places keep out of water and lava and out of the city's reserve. Pure from the seed:
 * the same site on every start, a few dozen columns sampled per candidate (one candidate in hundreds of chunks).
 */
final class CaveSites {
    private CaveSites() {}

    /** The base columns sampled for one candidate, by column. */
    private static final class Columns {
        final Structure.GenerationContext ctx;
        final Long2ObjectOpenHashMap<NoiseColumn> cache = new Long2ObjectOpenHashMap<>();
        int sampled;

        Columns(Structure.GenerationContext ctx) {
            this.ctx = ctx;
        }

        NoiseColumn at(int x, int z) {
            long k = ((long) x << 32) | (z & 0xFFFFFFFFL);
            NoiseColumn c = cache.get(k);
            if (c == null) {
                c = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
                cache.put(k, c);
                sampled++;
            }
            return c;
        }

        BlockState block(int x, int y, int z) {
            return at(x, z).getBlock(y);
        }

        /** The feet's height on the column's top solid block (MIN_VALUE when its top is water or lava). */
        int surface(int x, int z) {
            NoiseColumn c = at(x, z);
            int top = ctx.heightAccessor().getMaxY();
            for (int y = top; y > ctx.heightAccessor().getMinY(); y--) {
                BlockState s = c.getBlock(y);
                if (s.isAir()) continue;
                return s.getFluidState().isEmpty() ? y + 1 : Integer.MIN_VALUE;
            }
            return Integer.MIN_VALUE;
        }

        /** Whether the column holds water or lava anywhere from y0 to y1. */
        boolean wet(int x, int z, int y0, int y1) {
            NoiseColumn c = at(x, z);
            for (int y = Math.max(y0, ctx.heightAccessor().getMinY()); y <= Math.min(y1, ctx.heightAccessor().getMaxY() - 1); y++) {
                if (!c.getBlock(y).getFluidState().isEmpty()) return true;
            }
            return false;
        }

        Holder<Biome> biome(int x, int y, int z) {
            return ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        }
    }

    /** As the search below, for any structure's site (the Crystal Nest's tunnel). */
    static int @Nullable [] air(Structure.GenerationContext ctx, int x, int y, int z, int reach) {
        return air(new Columns(ctx), x, y, z, reach, 10, 12, ctx.random());
    }

    /** A floor of natural cave air near (x, y, z): feet cell with three of air over solid rock; within reach; or null. */
    static int @Nullable [] air(Columns cols, int x, int y, int z, int reach, int below, int above, WorldgenRandom rnd) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        int[] rings = {8, 14, 20, 27};
        for (int ring : rings) {
            if (ring > reach) break;
            double spin = rnd.nextDouble() * Math.PI * 2;
            for (int k = 0; k < 6; k++) {
                double a = spin + k * Math.PI / 3;
                int cx = Mth.floor(x + Math.cos(a) * ring), cz = Mth.floor(z + Math.sin(a) * ring);
                NoiseColumn c = cols.at(cx, cz);
                int run = 0;
                for (int cy = y + above; cy >= y - below; cy--) {
                    BlockState s = c.getBlock(cy);
                    if (s.isAir()) {
                        run++;
                        continue;
                    }
                    if (run >= 3 && s.getFluidState().isEmpty()) {
                        int feet = cy + 1;
                        double d = Math.sqrt((cx - x) * (cx - x) + (feet - y) * (feet - y) * 2.0 + (cz - z) * (cz - z));
                        if (d < bestD) {
                            bestD = d;
                            best = new int[]{cx, feet, cz};
                        }
                    }
                    run = 0;
                }
            }
            if (best != null) return best;
        }
        return best;
    }

    /**
     * Whether the whole place lies within reach of its start chunk: the generator links a structure only to the chunks
     * within 8 of its start (ChunkGenerator.createReferences), so anything further would never be built.
     */
    static boolean fits(Spec s, ChunkPos start) {
        var b = s.box();
        int reach = 8 * 16;
        return b.minX() >= start.getMinBlockX() - reach && b.maxX() <= start.getMaxBlockX() + reach
                && b.minZ() >= start.getMinBlockZ() - reach && b.maxZ() <= start.getMaxBlockZ() + reach;
    }

    static boolean landBiome(Holder<Biome> b) {
        return !b.is(BiomeTags.IS_OCEAN) && !b.is(BiomeTags.IS_RIVER) && !b.is(BiomeTags.IS_BEACH);
    }

    static boolean cityNear(int x, int z, int reach) {
        return CityBasin.reservedWithin(x, z, reach);
    }

    // ---- the kinds ----------------------------------------------------------------------------------------------------

    static @Nullable Spec find(Structure.GenerationContext ctx, CaveKind kind, boolean first) {
        Columns cols = new Columns(ctx);
        WorldgenRandom rnd = ctx.random();
        long seed = rnd.nextLong();
        ChunkPos c = ctx.chunkPos();
        Spec s = switch (kind) {
            case SINKHOLE -> sinkhole(cols, c, rnd, seed, first);
            case MINE -> mine(cols, c, rnd, seed);
            case SEEP -> seep(cols, c, rnd, seed);
            case CHUTE -> chute(cols, c, rnd, seed);
            case GROTTO -> grotto(cols, c, rnd, seed);
        };
        lastSampled = cols.sampled;
        return s;
    }

    /** Base columns the last search sampled (the cost test reads it). */
    static volatile int lastSampled;

    private static List<Lode> lodesNear(long worldSeed, int x, int z, int reach) {
        List<Lode> out = new ArrayList<>();
        int r0x = Math.floorDiv(x - reach, LodePlans.REGION), r1x = Math.floorDiv(x + reach, LodePlans.REGION);
        int r0z = Math.floorDiv(z - reach, LodePlans.REGION), r1z = Math.floorDiv(z + reach, LodePlans.REGION);
        for (int rx = r0x; rx <= r1x; rx++) for (int rz = r0z; rz <= r1z; rz++) out.addAll(LodePlans.region(worldSeed, rx, rz));
        out.sort(Comparator.comparingDouble(l -> {
            double dx = l.heartPos().getX() - x, dz = l.heartPos().getZ() - z;
            return dx * dx + dz * dz;
        }));
        return out;
    }

    private static @Nullable Spec sinkhole(Columns cols, ChunkPos c, WorldgenRandom rnd, long seed, boolean first) {
        int x0 = c.getMiddleBlockX(), z0 = c.getMiddleBlockZ();
        int sea = cols.ctx.chunkGenerator().getSeaLevel();
        int tries = 0;
        for (Lode l : lodesNear(cols.ctx.seed(), x0, z0, 96)) {
            if (tries >= (first ? 10 : 6)) break;
            if (l.ore() == Ore.DEEPGLASS) continue;
            var h = l.heartPos();
            int rad = first ? 14 + rnd.nextInt(3) : 10 + rnd.nextInt(8);
            double a = rnd.nextDouble() * Math.PI * 2;
            double off = rad + 13 + rnd.nextInt(5);
            int cx = Mth.floor(h.getX() + 0.5 - Math.cos(a) * off), cz = Mth.floor(h.getZ() + 0.5 - Math.sin(a) * off);
            tries++;
            if (cityNear(cx, cz, rad + 40)) continue;
            int rim = cols.surface(cx, cz);
            if (rim == Integer.MIN_VALUE || rim <= sea + 1) continue;
            int floor = h.getY() - 1;
            int depth = rim - floor;
            if (depth < 40 || depth > 110) continue;
            if (!landBiome(cols.biome(cx, rim, cz)) || !cols.ctx.validBiome().test(cols.biome(cx, rim, cz))) continue;
            if (cols.wet(cx, cz, floor - 6, rim + 2)) continue;
            boolean flat = true;
            for (int k = 0; k < 4 && flat; k++) {
                double b = k * Math.PI / 2 + 0.4;
                int px = Mth.floor(cx + Math.cos(b) * (rad + 2)), pz = Mth.floor(cz + Math.sin(b) * (rad + 2));
                int top = cols.surface(px, pz);
                if (top == Integer.MIN_VALUE || Math.abs(top - rim) > 9 || cols.wet(px, pz, floor, top)) flat = false;
            }
            if (!flat) continue;
            int[] j = air(cols, cx, floor, cz, 27, 10, 24, rnd);
            // a sinkhole goes down into a cave system; the first one, placed on purpose, may end in its own grotto
            if (j == null && !first) continue;
            double heartA = Math.toDegrees(Math.atan2(h.getZ() + 0.5 - cz, h.getX() + 0.5 - cx));
            int water = Math.floorMod((int) Math.round(heartA + 120 + rnd.nextInt(120)), 360);
            int shelf = Math.floorMod(water + 150 + rnd.nextInt(60), 360);
            Spec spec = new Spec(CaveKind.SINKHOLE, cx, floor, cz, 0, seed, j != null, j == null ? 0 : j[0], j == null ? 0 : j[1], j == null ? 0 : j[2],
                    new int[]{rad, rim, 1, h.getX(), h.getY(), h.getZ(), water, shelf, first ? 1 : 0});
            if (fits(spec, c)) return spec;
        }
        return null;
    }

    private static @Nullable Spec mine(Columns cols, ChunkPos c, WorldgenRandom rnd, long seed) {
        int sea = cols.ctx.chunkGenerator().getSeaLevel();
        for (int t = 0; t < 6; t++) {
            int px = c.getMiddleBlockX() + (t == 0 ? 0 : rnd.nextInt(49) - 24), pz = c.getMiddleBlockZ() + (t == 0 ? 0 : rnd.nextInt(49) - 24);
            if (cityNear(px, pz, 130)) continue;
            int yp = cols.surface(px, pz);
            if (yp == Integer.MIN_VALUE || yp <= sea + 2) continue;
            if (!landBiome(cols.biome(px, yp, pz)) || !cols.ctx.validBiome().test(cols.biome(px, yp, pz))) continue;
            int start = rnd.nextInt(4);
            for (int r = 0; r < 4; r++) {
                int rot = (start + r) & 3;
                Frame f = new Frame(px, yp, pz, rot);
                int L = 20 + rnd.nextInt(8);
                int into = cols.surface(f.x(0, 18), f.z(0, 18));
                int head = cols.surface(f.x(0, L + 8), f.z(0, L + 8));
                int front = cols.surface(f.x(0, -6), f.z(0, -6));
                if (into == Integer.MIN_VALUE || head == Integer.MIN_VALUE || front == Integer.MIN_VALUE) continue;
                if (into < yp + 9 || head < yp + 12 || front > yp + 2) continue;
                if (yp + Mine.G2 - 28 < cols.ctx.heightAccessor().getMinY() + 6) continue;
                // the workings stay dry: the head, the first gallery, and the second (it runs back under the hill)
                int g1x = f.x(3, L + 40), g1z = f.z(3, L + 40);
                if (cols.wet(f.x(0, L + 6), f.z(0, L + 6), yp + Mine.G1 - 2, yp + 6) || cols.wet(g1x, g1z, yp + Mine.G1 - 6, yp + Mine.G1 + 6)) continue;
                Spec bare = new Spec(CaveKind.MINE, px, yp, pz, rot, seed, false, 0, 0, 0, new int[]{L});
                if (!fits(bare, c) || mineWet(cols, bare)) continue;
                int[] e = Mine.end(bare);
                int[] j = air(cols, e[0], e[1], e[2], 27, 12, 16, rnd);
                Spec spec = new Spec(CaveKind.MINE, px, yp, pz, rot, seed, j != null, j == null ? 0 : j[0], j == null ? 0 : j[1], j == null ? 0 : j[2],
                        new int[]{L});
                return fits(spec, c) ? spec : bare;
            }
        }
        return null;
    }

    /**
     * Whether the mine's second gallery would cut into water (an aquifer near sea level floods it and its chasm to the
     * roof): the gallery's length, the chasm's two ends and middle down to its floor, and the breakthrough.
     */
    private static boolean mineWet(Columns cols, Spec s) {
        Frame g = Mine.back(new Frame(s.x(), s.y(), s.z(), s.rot()), Mine.adit(s));
        int zr = Mine.adit(s) + 9 + Mine.G1_LEN - 2, gx = 6 + Mine.INCLINE, z1 = zr + Mine.G2_LEN, h0 = zr + 30, y = s.y();
        int[][] gallery = {{gx + 1, zr + 4}, {gx + 1, zr + 13}, {gx + 4, zr + 23}, {gx + 1, z1}, {gx + 1, z1 + 7}};
        for (int[] q : gallery) if (cols.wet(g.x(q[0], q[1]), g.z(q[0], q[1]), y + Mine.G2 - 3, y + Mine.G2 + 7)) return true;
        for (int lx : new int[]{gx - 8, gx + 1, gx + 10}) {
            if (cols.wet(g.x(lx, h0 + 2), g.z(lx, h0 + 2), y + Mine.G2 - 28, y + Mine.G2 + 7)) return true;
        }
        return false;
    }

    private static @Nullable Spec seep(Columns cols, ChunkPos c, WorldgenRandom rnd, long seed) {
        int x0 = c.getMiddleBlockX(), z0 = c.getMiddleBlockZ();
        int minY = cols.ctx.heightAccessor().getMinY();
        for (Lode l : lodesNear(cols.ctx.seed(), x0, z0, 96)) {
            if (l.ore() != Ore.DEEPGLASS) continue;
            var h = l.heartPos();
            double dx = h.getX() - x0, dz = h.getZ() - z0;
            if (dx * dx + dz * dz > 96 * 96) break;
            for (int t = 0; t < 3; t++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                int cx = Mth.floor(h.getX() + 0.5 - Math.cos(a) * 22), cz = Mth.floor(h.getZ() + 0.5 - Math.sin(a) * 22);
                int floor = Math.max(minY + 9, h.getY() - 2 + rnd.nextInt(5) - 2);
                if (cityNear(cx, cz, 60)) continue;
                if (cols.wet(cx, cz, floor - 4, floor + 16) || cols.wet(h.getX(), h.getZ(), h.getY() - 6, h.getY() + 8)) continue;
                if (!cols.ctx.validBiome().test(cols.biome(cx, floor + 3, cz))) continue;
                int top = cols.surface(cx, cz);
                if (top != Integer.MIN_VALUE && top - floor < 30) continue;
                int[] j = air(cols, cx, floor, cz, 27, 12, 14, rnd);
                if (j == null) continue;
                Spec spec = new Spec(CaveKind.SEEP, cx, floor, cz, 0, seed, true, j[0], j[1], j[2], new int[]{h.getX(), h.getY(), h.getZ()});
                if (fits(spec, c)) return spec;
            }
            return null;
        }
        return null;
    }

    private static @Nullable Spec chute(Columns cols, ChunkPos c, WorldgenRandom rnd, long seed) {
        int minY = cols.ctx.heightAccessor().getMinY();
        for (int t = 0; t < 4; t++) {
            int px = c.getMiddleBlockX() + rnd.nextInt(33) - 16, pz = c.getMiddleBlockZ() + rnd.nextInt(33) - 16;
            int y = 4 + rnd.nextInt(40);
            int rot = rnd.nextInt(4);
            if (y + Chute.LAKE - Chute.LAKE_DEPTH - 4 < minY + 6 || cityNear(px, pz, 110)) continue;
            Frame f = new Frame(px, y, pz, rot);
            int[][] probes = {{0, 0, 6}, {0, Chute.LENGTH / 2, -Chute.LENGTH / 4 + 6}, {0, Chute.LENGTH + 10, Chute.LAKE + 12}};
            boolean ok = true;
            for (int[] p : probes) {
                int wx = f.x(p[0], p[1]), wz = f.z(p[0], p[1]);
                int top = cols.surface(wx, wz);
                if (top != Integer.MIN_VALUE && top < y + p[2] + 8 || cols.wet(wx, wz, y + Chute.LAKE - Chute.LAKE_DEPTH - 2, y + 6)) {
                    ok = false;
                    break;
                }
            }
            if (!ok || !cols.ctx.validBiome().test(cols.biome(px, y, pz))) continue;
            int[] j = air(cols, f.x(0, -4), y, f.z(0, -4), 22, 10, 10, rnd);
            if (j == null) continue;
            Spec spec = new Spec(CaveKind.CHUTE, px, y, pz, rot, seed, true, j[0], j[1], j[2], new int[0]);
            if (fits(spec, c)) return spec;
        }
        return null;
    }

    private static @Nullable Spec grotto(Columns cols, ChunkPos c, WorldgenRandom rnd, long seed) {
        for (int t = 0; t < 4; t++) {
            int px = c.getMiddleBlockX() + rnd.nextInt(33) - 16, pz = c.getMiddleBlockZ() + rnd.nextInt(33) - 16;
            int y = 2 + rnd.nextInt(46);
            int rot = rnd.nextInt(4);
            if (cityNear(px, pz, 60)) continue;
            int top = cols.surface(px, pz);
            if (top != Integer.MIN_VALUE && top < y + 16) continue;
            if (!cols.ctx.validBiome().test(cols.biome(px, y + 2, pz))) continue;
            Frame f = new Frame(px, y, pz, rot);
            // dry across its span, not only at its middle (an aquifer beside the room floods it to the roof)
            boolean wet = false;
            for (int[] q : Grotto.SPAN) if (!wet) wet = cols.wet(f.x(q[0], q[1]), f.z(q[0], q[1]), y - 5, y + 10);
            if (wet) continue;
            int[] j = air(cols, f.x(0, -14), y, f.z(0, -14), 22, 10, 10, rnd);
            if (j == null) continue;
            Spec spec = new Spec(CaveKind.GROTTO, px, y, pz, rot, seed, true, j[0], j[1], j[2], new int[0]);
            if (fits(spec, c)) return spec;
        }
        return null;
    }
}
