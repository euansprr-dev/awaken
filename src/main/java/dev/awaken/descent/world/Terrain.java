package dev.awaken.descent.world;

import dev.awaken.descent.DescentContent;
import dev.awaken.descent.Strata;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure, deterministic terrain of one stratum form for one seed. Every stratum is a vertical abyss around a local
 * origin: an arrival platform at the top, a spiral ramp (the "helix") down the shaft wall, a floor with the bottom camp
 * (Waystone + straw bed) and the pit to the next stratum. Forms add their own shapes: hanging islands, flooded halls,
 * giant ribs, silent corridors, machine halls, an open sea cavern, shifting dream rooms, a black court and the Seed.
 *
 * <p>Thread-safe: instances are immutable after construction; scratch buffers are per thread. Cost is a few ms per
 * chunk (coarse 4x8x4 noise lattice, trilinear interpolation, no per-block allocation).
 */
public final class Terrain {
    public final int form;
    public final long seed;
    final Palette pal;

    private final SimplexNoise n1, n2;
    // Shape
    final boolean square;
    final int rTop, rBottom, helixR, pitch, helixW, maxR;
    final boolean ledges, caves;
    // Features (local coordinates)
    private final double[][] islands;  // cx, cy, cz, rx, ryTop, ryBottom
    private final double[][] stalactites; // cx, cz, baseR, length (forms 7)
    private final double[][] ribs;     // phi(cos, sin), y0, span, arch
    private final double[][] trunks;   // x, z, height, radius
    private final double[][] veins;    // x0,y0,z0,x1,y1,z1
    private final List<BlockPos> caches = new ArrayList<>();

    // States
    private final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final BlockState WATER = Blocks.WATER.defaultBlockState();
    private final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private final BlockState HANGING_ROOTS = Blocks.HANGING_ROOTS.defaultBlockState();
    private final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    private final BlockState LEAVES = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private final BlockState VINE_PLANT = Blocks.CAVE_VINES_PLANT.defaultBlockState();
    private final BlockState VINE_PLANT_LIT = Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVinesBlock.BERRIES, true);
    private final BlockState VINE_TIP = Blocks.CAVE_VINES.defaultBlockState();
    private final BlockState VINE_TIP_LIT = Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVinesBlock.BERRIES, true);
    private final BlockState BONE_Y = Blocks.BONE_BLOCK.defaultBlockState();
    private final BlockState BONE_X = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    private final BlockState BONE_Z = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);

    // Gallery levels
    static final int[] HALLS_3 = {44, 94, 144};
    static final int[] CORRIDORS_5 = {44, 84, 124, 164};
    static final int[] HALLS_6 = {48, 98, 148};
    public static final int[] ROOMS_8 = {38, 74, 110, 146, 182};
    public static final int ROOM_SECTORS = 12;
    public static final int ROOM_VARIANTS = 6;
    static final int[] COURT_9 = {50, 110, 170};

    private static final ConcurrentHashMap<Long, Terrain> CACHE = new ConcurrentHashMap<>();

    /** Terrain for a depth index (stratum or Endless cell). Cached. */
    public static Terrain forDepth(int depth, long worldSeed) {
        int form = Strata.formOf(depth);
        long s = worldSeed * 0x5DEECE66DL + depth * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL;
        long key = Strata.mix(s ^ form);
        Terrain t = CACHE.get(key);
        if (t == null) {
            if (CACHE.size() > 48) CACHE.clear();
            t = new Terrain(form, s);
            CACHE.put(key, t);
        }
        return t;
    }

    public Terrain(int form, long seed) {
        this.form = form;
        this.seed = seed;
        this.pal = Palette.of(form);
        this.n1 = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0xA5A5A5A5L));
        this.n2 = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x5A5A5A5AL));
        switch (form) {
            case 1 -> { square = false; rTop = 36; rBottom = 48; helixR = 0; pitch = 30; helixW = 7; ledges = true; caves = true; }
            case 2 -> { square = false; rTop = 54; rBottom = 54; helixR = 0; pitch = 36; helixW = 5; ledges = false; caves = true; }
            case 3 -> { square = false; rTop = 30; rBottom = 30; helixR = 0; pitch = 40; helixW = 6; ledges = false; caves = true; }
            case 4 -> { square = false; rTop = 44; rBottom = 54; helixR = 0; pitch = 30; helixW = 7; ledges = true; caves = true; }
            case 5 -> { square = false; rTop = 26; rBottom = 26; helixR = 0; pitch = 36; helixW = 6; ledges = true; caves = false; }
            case 6 -> { square = true; rTop = 38; rBottom = 38; helixR = 32; pitch = 34; helixW = 6; ledges = false; caves = false; }
            case 7 -> { square = false; rTop = 112; rBottom = 112; helixR = 0; pitch = 1; helixW = 0; ledges = false; caves = false; }
            case 8 -> { square = false; rTop = 34; rBottom = 34; helixR = 0; pitch = 32; helixW = 6; ledges = true; caves = true; }
            case 9 -> { square = true; rTop = 30; rBottom = 30; helixR = 24; pitch = 34; helixW = 5; ledges = true; caves = false; }
            default -> { square = false; rTop = 40; rBottom = 40; helixR = 0; pitch = 32; helixW = 7; ledges = false; caves = true; }
        }
        this.maxR = form == 7 ? 124 : 150;

        XoroshiroRandomSource r = new XoroshiroRandomSource(seed ^ 0x1234ABCDL);
        // Hanging islands (2) and floating islands (7)
        if (form == 2) {
            islands = new double[26][];
            for (int i = 0; i < islands.length; i++) {
                double rx = 5 + r.nextDouble() * 6;
                double y = 45 + (i + r.nextDouble()) * (140.0 / islands.length);
                double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * (rTop - helixW - rx - 4);
                islands[i] = new double[]{Math.cos(a) * d, y, Math.sin(a) * d, rx, 2.5 + r.nextDouble() * 1.5, rx * 0.9};
            }
        } else if (form == 7) {
            islands = new double[30][];
            for (int i = 0; i < islands.length; i++) {
                double rx = 7 + r.nextDouble() * 9;
                double y = 70 + r.nextDouble() * 115;
                double a = r.nextDouble() * Math.PI * 2, d = 22 + r.nextDouble() * (92 - rx);
                islands[i] = new double[]{Math.cos(a) * d, y, Math.sin(a) * d, rx, 2 + r.nextDouble() * 2, rx * 1.1};
            }
        } else {
            islands = new double[0][];
        }
        if (form == 7) {
            stalactites = new double[12][];
            for (int i = 0; i < stalactites.length; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 20 + r.nextDouble() * 80;
                stalactites[i] = new double[]{Math.cos(a) * d, Math.sin(a) * d, 5 + r.nextDouble() * 7, 12 + r.nextDouble() * 22};
            }
        } else {
            stalactites = new double[0][];
        }
        // Ribs and bone trees (4)
        if (form == 4) {
            ribs = new double[9][];
            for (int i = 0; i < ribs.length; i++) {
                double phi = r.nextDouble() * Math.PI;
                double y0 = 50 + (i + r.nextDouble() * 0.8) * (125.0 / ribs.length);
                ribs[i] = new double[]{Math.cos(phi), Math.sin(phi), y0, shaftR(y0) + 3, 10 + r.nextDouble() * 12};
            }
            trunks = new double[14][];
            for (int i = 0; i < trunks.length; i++) {
                double a = (i + r.nextDouble() * 0.6) * (Math.PI * 2 / trunks.length), d = 18 + r.nextDouble() * (shaftR(20) - 28);
                trunks[i] = new double[]{Math.cos(a) * d, Math.sin(a) * d, 14 + r.nextDouble() * 16, 1.6 + r.nextDouble() * 0.9};
            }
        } else {
            ribs = new double[0][];
            trunks = new double[0][];
        }
        // Veins of the Seed (10)
        if (form == 10) {
            veins = new double[8][];
            for (int i = 0; i < 8; i++) {
                double a = (i + 0.5) * Math.PI / 4;
                double y1 = (i % 2 == 0) ? 62 : 108;
                veins[i] = new double[]{0, SEED_Y, 0, Math.cos(a) * 60, y1, Math.sin(a) * 60};
            }
        } else {
            veins = new double[0][];
        }
        // Loot caches
        if (form == 7) {
            for (int i = 0; i < 4 && i < islands.length; i++) {
                double[] is = islands[i * 7 % islands.length];
                caches.add(new BlockPos((int) Math.floor(is[0]), (int) Math.floor(is[1]) + 3, (int) Math.floor(is[2])));
            }
        } else {
            int turns = (Layout.ENTRY_Y - 1 - Layout.floorY(form)) / pitch;
            for (int k = 1; k <= Math.min(4, turns - 1); k++) {
                double a = (0.45 + 0.17 * k) % 1.0;
                double th = Layout.THETA0 + a * Math.PI * 2;
                double yS = Layout.ENTRY_Y - 1 - pitch * (k + a);
                double rr = helixRadius(yS) - helixW / 2.0;
                caches.add(new BlockPos((int) Math.floor(Math.cos(th) * rr), (int) Math.floor(yS) + 1, (int) Math.floor(Math.sin(th) * rr)));
            }
        }
    }

    // ---- shape helpers ---------------------------------------------------------------------------------------------

    static final int SEED_Y = 84;

    public double shaftR(double y) {
        double f = Mth.clamp((Layout.ENTRY_Y - y) / (Layout.ENTRY_Y - 20.0), 0, 1);
        return rTop + (rBottom - rTop) * f;
    }

    double helixRadius(double y) {
        return helixR > 0 ? helixR : shaftR(y);
    }

    /** Arrival platform distance from the axis (it sits on the start of the helix). */
    public static int entryDistance(int form) {
        return switch (form) {
            case 1 -> 33;
            case 2 -> 51;
            case 3 -> 27;
            case 4 -> 40;
            case 5 -> 23;
            case 6 -> 29;
            case 8 -> 31;
            case 9 -> 21;
            case 10 -> 36;
            default -> 40;
        };
    }

    public int helixPitch() {
        return pitch;
    }

    public int helixWidth() {
        return helixW;
    }

    public double helixR(double y) {
        return helixRadius(y);
    }

    public List<BlockPos> caches() {
        return caches;
    }

    /** Max local distance from the axis that holds anything but solid rock. */
    public int maxRadius() {
        return maxR;
    }

    // ---- noise lattice ---------------------------------------------------------------------------------------------

    static final int NODES_Y = Layout.HEIGHT / 8 + 1;

    void nodeColumn(int nx, int nz, float[] out) {
        for (int k = 0; k < NODES_Y; k++) {
            int y = k * 8;
            out[k] = n1.get(nx / 36.0, y / 24.0, nz / 36.0);
            out[NODES_Y + k] = n2.get(nx / 36.0 + 91.7, y / 24.0, nz / 36.0 - 37.3);
        }
    }

    static final class Scratch {
        final float[][] nodes = new float[25][NODES_Y * 2];
        final float[] lerped = new float[NODES_Y * 2];
        final float[] cA = new float[Layout.HEIGHT], cB = new float[Layout.HEIGHT];
        final BlockState[] col = new BlockState[Layout.HEIGHT];
        final float[][] single = new float[4][NODES_Y * 2];
        final int[] cand = new int[64];
        int nCand;
        double seaWall;
    }

    static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /** Fills the lattice of a 16x16 area whose local min corner is (lx0, lz0). */
    void chunkNodes(int lx0, int lz0, Scratch s) {
        if (!caves) return;
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) nodeColumn(lx0 + i * 4, lz0 + j * 4, s.nodes[i * 5 + j]);
    }

    private void interpolate(float[] c00, float[] c10, float[] c01, float[] c11, float fx, float fz, Scratch s) {
        float[] l = s.lerped;
        for (int k = 0; k < NODES_Y * 2; k++) {
            float a = c00[k] + (c10[k] - c00[k]) * fx;
            float b = c01[k] + (c11[k] - c01[k]) * fx;
            l[k] = a + (b - a) * fz;
        }
        for (int y = 0; y < Layout.HEIGHT; y++) {
            int k = y >> 3;
            float fy = (y & 7) / 8.0F;
            s.cA[y] = l[k] + (l[k + 1] - l[k]) * fy;
            s.cB[y] = l[NODES_Y + k] + (l[NODES_Y + k + 1] - l[NODES_Y + k]) * fy;
        }
    }

    /** Column inside a chunk whose lattice was filled by {@link #chunkNodes}. lx/lz in 0..15. */
    BlockState[] chunkColumn(int lx0, int lz0, int lx, int lz, Scratch s) {
        if (caves) {
            int i = lx >> 2, j = lz >> 2;
            interpolate(s.nodes[i * 5 + j], s.nodes[(i + 1) * 5 + j], s.nodes[i * 5 + j + 1], s.nodes[(i + 1) * 5 + j + 1],
                    (lx & 3) / 4.0F, (lz & 3) / 4.0F, s);
        }
        column(lx0 + lx, lz0 + lz, s);
        return s.col;
    }

    /** One column on its own (getBaseColumn, tests). Same lattice as chunk generation, so identical results. */
    public BlockState[] singleColumn(int x, int z) {
        Scratch s = SCRATCH.get();
        if (caves) {
            int nx = Math.floorDiv(x, 4) * 4, nz = Math.floorDiv(z, 4) * 4;
            nodeColumn(nx, nz, s.single[0]);
            nodeColumn(nx + 4, nz, s.single[1]);
            nodeColumn(nx, nz + 4, s.single[2]);
            nodeColumn(nx + 4, nz + 4, s.single[3]);
            interpolate(s.single[0], s.single[1], s.single[2], s.single[3], (x - nx) / 4.0F, (z - nz) / 4.0F, s);
        }
        column(x, z, s);
        return s.col;
    }

    /** Whether the whole 16x16 area is beyond the stratum (solid rock). */
    public boolean solidArea(int lx0, int lz0) {
        double nx = Mth.clamp(0, lx0, lx0 + 15), nz = Mth.clamp(0, lz0, lz0 + 15);
        return Math.sqrt(nx * nx + nz * nz) > maxR + 2;
    }

    public BlockState solidFill(int y) {
        return y == 0 ? BEDROCK : pal.rock;
    }

    // ---- the column ------------------------------------------------------------------------------------------------

    private boolean isRock(BlockState s) {
        return s == pal.rock || s == pal.rockAlt;
    }

    void column(int x, int z, Scratch s) {
        BlockState[] col = s.col;
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r > maxR) {
            for (int y = 0; y < Layout.HEIGHT; y++) col[y] = y == 0 ? BEDROCK : pal.rock;
            return;
        }
        double cheb = Math.max(Math.abs(x), Math.abs(z));
        double dist = square ? cheb : r;
        double theta = Math.atan2(z, x);
        double a = (theta - Layout.THETA0) / (Math.PI * 2);
        a -= Math.floor(a);
        collectCandidates(x, z, s);
        if (form == 7) s.seaWall = rTop + 5 * n1.get(x / 34.0, z / 34.0);
        for (int y = 0; y < Layout.HEIGHT; y++) col[y] = base(x, y, z, r, dist, a, s.cA[y], s.cB[y], s);
        decorate(x, z, r, dist, col);
        landmarks(x, z, col);
    }

    private void collectCandidates(int x, int z, Scratch s) {
        s.nCand = 0;
        if (form == 2 || form == 7) {
            for (int i = 0; i < islands.length && s.nCand < s.cand.length; i++) {
                double[] is = islands[i];
                double dx = x - is[0], dz = z - is[2];
                if (dx * dx + dz * dz < (is[3] + 1) * (is[3] + 1)) s.cand[s.nCand++] = i;
            }
            for (int i = 0; i < stalactites.length && s.nCand < s.cand.length; i++) {
                double[] st = stalactites[i];
                double dx = x - st[0], dz = z - st[1];
                if (dx * dx + dz * dz < (st[2] + 1) * (st[2] + 1)) s.cand[s.nCand++] = 1000 + i;
            }
        } else if (form == 4) {
            for (int i = 0; i < ribs.length && s.nCand < s.cand.length; i++) {
                double[] rb = ribs[i];
                double perp = Math.abs(x * rb[1] - z * rb[0]);
                double along = x * rb[0] + z * rb[1];
                if (perp < 2.2 && Math.abs(along) < rb[3]) s.cand[s.nCand++] = i;
            }
            for (int i = 0; i < trunks.length && s.nCand < s.cand.length; i++) {
                double dx = x - trunks[i][0], dz = z - trunks[i][1];
                if (dx * dx + dz * dz < 64) s.cand[s.nCand++] = 1000 + i;
            }
        } else if (form == 10) {
            for (int i = 0; i < veins.length; i++) {
                if (segDist2Xz(veins[i], x, z) < 4) s.cand[s.nCand++] = i;
            }
        }
    }

    private BlockState rock(int x, int y, int z, double dist, double shaftEdge) {
        long h = Strata.hash(seed, x >> 1, y >> 1, z >> 1);
        double u = (h >>> 11) * 0x1.0p-53;
        if (u < pal.oreChance) return pal.ores[(int) ((h >>> 3) & 0x7FFFFFFF) % pal.ores.length];
        if (dist < shaftEdge + 1.6 && Strata.hash01(seed + 7, x, y, z) < 0.035) return pal.wallGlow;
        return ((y / 9 + (int) (h & 3)) % 5 == 0) ? pal.rockAlt : pal.rock;
    }

    private BlockState base(int x, int y, int z, double r, double dist, double a, float cA, float cB, Scratch s) {
        if (y == 0) return BEDROCK;
        if (form == 7) return seaBase(x, y, z, r, s);
        int floor = Layout.floorY(form);
        if (y < floor - 1) return rock(x, y, z, 999, 0);
        double R = shaftR(y);
        double wob = caves ? Math.max(0, cA * 3.5) : 0;
        double edge = R + wob;
        boolean inShaft;
        if (y > 212) {
            double k = (y - 212) / 24.0;
            inShaft = k < 1 && dist < edge * Math.sqrt(1 - k * k);
        } else {
            inShaft = dist < edge;
        }
        if (form == 10 && !inShaft) inShaft = inSeedChamber(r, y);
        if (y == floor - 1) {
            // the floor layer of the shaft
            return inShaft ? pal.rock : rock(x, y, z, dist, edge);
        }
        if (inShaft) {
            if (helix(r, a, y)) return pal.rock;
            BlockState f = shaftFeature(x, y, z, r, dist, a, s);
            if (f != null) return f;
            return AIR;
        }
        BlockState g = gallery(x, y, z, r, dist, a, R);
        if (g != null) return g;
        if (caves && r < maxR - 8 && y > floor + 1 && y < 226 && Math.abs(cA) < 0.085 && Math.abs(cB) < 0.085) return AIR;
        return rock(x, y, z, dist, edge);
    }

    private boolean helix(double r, double a, int y) {
        if (helixW <= 0) return false;
        double rh = helixRadius(y);
        if (r < rh - helixW || r > rh + 1.5) return false;
        double t = (Layout.ENTRY_Y - 1 - y) / (double) pitch - a;
        if (t < 0) return false;
        int k = (int) Math.floor(t);
        double d = (t - k) * pitch;
        double surface = Layout.ENTRY_Y - 1 - pitch * (k + a);
        if (surface < Layout.floorY(form) - 1) return false;
        if (d < 2.6) return true;
        if (ledges) {
            boolean window = (a > 0.20 && a < 0.235) || (a > 0.53 && a < 0.565) || (a > 0.86 && a < 0.895);
            if (window && (Math.abs(d - pitch / 3.0) < 0.5 || Math.abs(d - 2 * pitch / 3.0) < 0.5)) return true;
        }
        return false;
    }

    private BlockState shaftFeature(int x, int y, int z, double r, double dist, double a, Scratch s) {
        // keep the helix and its headroom clear of every feature
        if (helixW > 0) {
            double rh = helixRadius(y);
            if (r >= rh - helixW - 0.5 && r <= rh + 1.5) return null;
        }
        switch (form) {
            case 2 -> {
                for (int c = 0; c < s.nCand; c++) {
                    double[] is = islands[s.cand[c]];
                    if (island(is, x, y, z)) return pal.rock;
                }
            }
            case 4 -> {
                for (int c = 0; c < s.nCand; c++) {
                    int id = s.cand[c];
                    if (id >= 1000) {
                        BlockState t = trunk(trunks[id - 1000], x, y, z);
                        if (t != null) return t;
                    } else {
                        double[] rb = ribs[id];
                        double along = x * rb[0] + z * rb[1];
                        double perp = Math.abs(x * rb[1] - z * rb[0]);
                        double h = rb[2] + rb[4] * (1 - (along / rb[3]) * (along / rb[3]));
                        if (perp < 1.6 && Math.abs(y - h) < 1.6) {
                            double slope = Math.abs(2 * rb[4] * along / (rb[3] * rb[3]));
                            if (slope > 1) return BONE_Y;
                            return Math.abs(rb[0]) > Math.abs(rb[1]) ? BONE_X : BONE_Z;
                        }
                    }
                }
            }
            case 9 -> {
                // colonnade along the court walls
                double cheb = Math.max(Math.abs(x), Math.abs(z));
                if (cheb >= rTop - 2.5) {
                    int along = Math.abs(x) >= Math.abs(z) ? z : x;
                    if (Math.floorMod(along, 7) <= 1) return y % 24 == 23 ? pal.light : pal.rockAlt;
                }
            }
            case 10 -> {
                double dy = y - SEED_Y;
                double d = Math.sqrt(r * r + dy * dy);
                if (d < 10) {
                    if (d < 8.3) return DescentContent.SEED_CORE.get().defaultBlockState();
                    return Strata.hash01(seed, x, y, z) < 0.18 ? DescentContent.SEED_CORE.get().defaultBlockState() : pal.floor;
                }
                for (int c = 0; c < s.nCand; c++) {
                    if (segDist2(veins[s.cand[c]], x, y, z) < 1.7) return pal.wallGlow;
                }
            }
            default -> {}
        }
        return null;
    }

    private static boolean island(double[] is, int x, int y, int z) {
        double dx = (x - is[0]) / is[3], dz = (z - is[2]) / is[3];
        double dy = y - is[1];
        dy = dy >= 0 ? dy / is[4] : dy / is[5];
        return dx * dx + dz * dz + dy * dy < 1;
    }

    private BlockState trunk(double[] t, int x, int y, int z) {
        int floor = Layout.floorY(form);
        double dx = x - t[0], dz = z - t[1];
        double top = floor + t[2];
        if (y >= floor && y <= top && dx * dx + dz * dz < t[3] * t[3]) return BONE_Y;
        // three branches rising from near the top
        for (int b = 0; b < 3; b++) {
            double ang = b * 2.094 + t[0] * 0.13;
            double bx = Math.cos(ang), bz = Math.sin(ang);
            double along = dx * bx + dz * bz;
            if (along < 0 || along > 7) continue;
            double perp = Math.abs(dx * bz - dz * bx);
            double by = top - 4 + along;
            if (perp < 1.0 && Math.abs(y - by) < 1.0) return along > 6 ? pal.wallGlow : (Math.abs(bx) > Math.abs(bz) ? BONE_X : BONE_Z);
        }
        return null;
    }

    private boolean inSeedChamber(double r, int y) {
        int floor = Layout.floorY(10);
        if (y < floor) return false;
        if (y <= 120) return r < 60;
        double k = (y - 120) / 50.0;
        return k < 1 && r < 60 * Math.sqrt(1 - k * k);
    }

    private static double segDist2(double[] v, double x, double y, double z) {
        double sx = v[3] - v[0], sy = v[4] - v[1], sz = v[5] - v[2];
        double px = x - v[0], py = y - v[1], pz = z - v[2];
        double t = Mth.clamp((px * sx + py * sy + pz * sz) / (sx * sx + sy * sy + sz * sz), 0, 1);
        double ex = px - sx * t, ey = py - sy * t, ez = pz - sz * t;
        return ex * ex + ey * ey + ez * ez;
    }

    private static double segDist2Xz(double[] v, double x, double z) {
        double sx = v[3] - v[0], sz = v[5] - v[2];
        double px = x - v[0], pz = z - v[2];
        double t = Mth.clamp((px * sx + pz * sz) / (sx * sx + sz * sz), 0, 1);
        double ex = px - sx * t, ez = pz - sz * t;
        return ex * ex + ez * ez;
    }

    // ---- galleries (carved into the rock around the shaft) -----------------------------------------------------------

    private BlockState gallery(int x, int y, int z, double r, double dist, double a, double R) {
        switch (form) {
            case 3 -> {
                for (int gy : HALLS_3) {
                    if (y < gy || y > gy + 13 || dist >= R + 30) continue;
                    double arc = Math.PI * 2 * dist / 8;
                    double fs = a * 8 - Math.floor(a * 8);
                    double toWall = Math.min(fs, 1 - fs) * arc;
                    if (toWall < 1.2 && !(y < gy + 6 && Math.abs(dist - (R + 15)) < 2.5)) return pal.rockAlt;
                    double ring = Math.abs(dist - (R + 10)) < 1.6 ? R + 10 : Math.abs(dist - (R + 21)) < 1.6 ? R + 21 : 0;
                    if (ring > 0) {
                        int n = Math.max(6, (int) Math.round(Math.PI * 2 * ring / 12));
                        double slot = a * n - Math.floor(a * n);
                        if (Math.abs(slot - 0.5) * (Math.PI * 2 * ring / n) < 1.6) {
                            return y == gy + 12 ? pal.light : pal.floor;
                        }
                    }
                    return y <= gy + 2 ? WATER : AIR;
                }
            }
            case 5 -> {
                for (int gy : CORRIDORS_5) {
                    if (y < gy - 1 || y > gy + 6 || dist >= R + 44) continue;
                    int mx = Math.floorMod(x + 1, 16), mz = Math.floorMod(z + 1, 16);
                    boolean roomX = mx <= 4 || mx >= 14, roomZ = mz <= 4 || mz >= 14;
                    boolean corridor = (mx <= 3 && mx >= 1) || (mz <= 3 && mz >= 1);
                    boolean room = roomX && roomZ;
                    if (y == gy + 6) {
                        if (room && mx == 2 && mz == 2) return pal.light;
                        continue;
                    }
                    if (y == gy - 1) continue;
                    if (room || (corridor && y <= gy + 4)) return AIR;
                }
            }
            case 6 -> {
                for (int gy : HALLS_6) {
                    if (y < gy || y > gy + 9 || dist >= R + 52) continue;
                    int mx = Math.floorMod(x + 13, 26) - 13, mz = Math.floorMod(z + 13, 26) - 13;
                    int ax = Math.abs(mx), az = Math.abs(mz);
                    boolean hall = ax <= 9 && az <= 9;
                    boolean door = (ax <= 2 || az <= 2) && y <= gy + 3;
                    if (hall) {
                        if (ax == 7 && az == 7) return y == gy + 7 ? pal.light : pal.rockAlt;
                        if (ax <= 1 && az <= 1 && y <= gy + 2) return (ax == 0 && az == 0 && y == gy + 2) ? pal.light : pal.floor;
                        return AIR;
                    }
                    if (door) return AIR;
                }
            }
            case 8 -> {
                for (int g = 0; g < ROOMS_8.length; g++) {
                    int gy = ROOMS_8[g];
                    if (y < gy || y > gy + 7 || dist >= R + 25.5) continue;
                    double arc = Math.PI * 2 * dist / ROOM_SECTORS;
                    double fs = a * ROOM_SECTORS - Math.floor(a * ROOM_SECTORS);
                    double toWall = Math.min(fs, 1 - fs) * arc;
                    double u = (fs - 0.5) * arc;
                    double v = dist - (R + 13);
                    int h = y - gy;
                    if (dist < R + 1.5) {
                        // inner wall with a doorway to the shaft
                        if (Math.abs(u) < 1.5 && h <= 3) return AIR;
                        return pal.rockAlt;
                    }
                    if (dist > R + 24.5) return pal.rockAlt;
                    if (toWall < 1.0) {
                        if (h <= 2 && Math.abs(v) < 1.5) return AIR;
                        return pal.rockAlt;
                    }
                    int sector = (int) Math.floor(a * ROOM_SECTORS);
                    return roomBlock(defaultVariant(g, sector), u, v, h);
                }
            }
            case 9 -> {
                for (int gy : COURT_9) {
                    if (y < gy || y > gy + 12 || dist >= rTop + 24) continue;
                    int along = Math.abs(x) >= Math.abs(z) ? z : x;
                    if (Math.abs(dist - (rTop + 12)) < 1.0 && Math.floorMod(along, 8) <= 1) return y >= gy + 11 ? pal.light : pal.rockAlt;
                    return AIR;
                }
            }
            case 10 -> {
                return null;
            }
            default -> {}
        }
        return null;
    }

    // ---- dream rooms (form 8) ----------------------------------------------------------------------------------------

    public int defaultVariant(int gallery, int sector) {
        return (int) Math.floorMod(Strata.hash(seed, gallery, 88, sector), (long) ROOM_VARIANTS);
    }

    /** What a dream room variant holds at room coordinates (u along the arc, v radial, h above the floor). */
    public BlockState roomBlock(int variant, double u, double v, int h) {
        BlockState cloud = pal.rock, tile = pal.floor, glow = pal.light;
        switch (variant) {
            case 0 -> { if (Math.abs(u) < 1.5 && Math.abs(v) < 1.5) return glow; }
            case 1 -> {
                double au = Math.abs(u), av = Math.abs(v);
                if ((au < 1 || (au > 5 && au < 6.5)) && av > 5.5 && av < 7) return cloud;
            }
            case 2 -> { if (h <= (u + 9) / 3 && Math.abs(v) < 4) return tile; }
            case 3 -> {
                if (h >= 5 && Math.abs(v) < 3 && Math.abs(u) < 3) return cloud;
                if (h == 4 && Math.abs(v) < 1 && Math.abs(u) < 1) return glow;
            }
            case 4 -> {
                if (h <= 4 && Math.floorMod((int) Math.round(u), 4) == 0 && Math.floorMod((int) Math.round(v), 6) != 3) return tile;
            }
            case 5 -> {
                if (h == 0 && Math.abs(u) < 7 && Math.abs(v) < 6) return WATER;
                if (h == 5 && Math.abs(u) < 2 && Math.abs(v) < 2) return glow;
            }
            default -> {}
        }
        return AIR;
    }

    /** Room id (gallery * ROOM_SECTORS + sector) of an interior position of a dream room, or -1. Local coordinates. */
    public int roomAt(int x, int y, int z) {
        if (form != 8) return -1;
        double r = Math.sqrt((double) x * x + (double) z * z);
        double R = shaftR(y);
        if (r <= R + 1.5 || r >= R + 24.5) return -1;
        for (int g = 0; g < ROOMS_8.length; g++) {
            int gy = ROOMS_8[g];
            if (y < gy || y > gy + 7) continue;
            double a = (Math.atan2(z, x) - Layout.THETA0) / (Math.PI * 2);
            a -= Math.floor(a);
            double arc = Math.PI * 2 * r / ROOM_SECTORS;
            double fs = a * ROOM_SECTORS - Math.floor(a * ROOM_SECTORS);
            if (Math.min(fs, 1 - fs) * arc < 1.0) return -1;
            return g * ROOM_SECTORS + (int) Math.floor(a * ROOM_SECTORS);
        }
        return -1;
    }

    /** Interior block of a room at a local position for a given variant (null if the position is not interior). */
    public BlockState roomBlockAt(int x, int y, int z, int variant) {
        double r = Math.sqrt((double) x * x + (double) z * z);
        double R = shaftR(y);
        int room = roomAt(x, y, z);
        if (room < 0) return null;
        int gy = ROOMS_8[room / ROOM_SECTORS];
        double a = (Math.atan2(z, x) - Layout.THETA0) / (Math.PI * 2);
        a -= Math.floor(a);
        double arc = Math.PI * 2 * r / ROOM_SECTORS;
        double fs = a * ROOM_SECTORS - Math.floor(a * ROOM_SECTORS);
        return roomBlock(variant, (fs - 0.5) * arc, r - (R + 13), y - gy);
    }

    /** Local centre of a dream room (floor level + 3). */
    public BlockPos roomCenter(int room) {
        int g = room / ROOM_SECTORS, sector = room % ROOM_SECTORS;
        int gy = ROOMS_8[g];
        double th = Layout.THETA0 + (sector + 0.5) * Math.PI * 2 / ROOM_SECTORS;
        double rr = shaftR(gy) + 13;
        return new BlockPos((int) Math.floor(Math.cos(th) * rr), gy + 3, (int) Math.floor(Math.sin(th) * rr));
    }

    public static int roomCount() {
        return ROOMS_8.length * ROOM_SECTORS;
    }

    // ---- the Inverted Sea (form 7) -----------------------------------------------------------------------------------

    private BlockState seaBase(int x, int y, int z, double r, Scratch s) {
        int entryZ = -entryDistance(7);
        double px = x, pz = z - entryZ;
        double rp = Math.sqrt(px * px + pz * pz);
        if (y >= Layout.ENTRY_Y - 1 && rp < 7.5) {
            if (y >= Layout.ENTRY_Y && y <= Layout.ENTRY_Y + 5 && rp < 5) return AIR;
            if (y >= Layout.ENTRY_Y && y <= Layout.ENTRY_Y + 3 && pz > 0 && Math.abs(px) < 3 && rp < 8) return AIR;
            return pal.rock;
        }
        if (y >= 185 && y < Layout.ENTRY_Y - 1 && rp < 7 * (y - 185) / 14.0) return pal.rock;
        double wall = s.seaWall;
        if (r >= wall || y >= 221) return rock(x, y, z, r, wall);
        for (int c = 0; c < s.nCand; c++) {
            int id = s.cand[c];
            if (id >= 1000) {
                double[] st = stalactites[id - 1000];
                double dx = x - st[0], dz = z - st[1];
                double top = 204, len = st[3];
                if (y <= top && y > top - len) {
                    double rr = st[2] * (y - (top - len)) / len;
                    if (dx * dx + dz * dz < rr * rr) return y > top - 2 ? pal.rock : pal.rockAlt;
                }
            } else if (island(islands[id], x, y, z)) {
                return pal.rock;
            }
        }
        if (y >= 204) return r < wall - 2 ? WATER : pal.rock;
        if (y <= 46 && r < 16 + (46 - y) * 1.5) return pal.rock;
        if (y < 20) return pal.rock;
        if (y <= 44) return WATER;
        return AIR;
    }

    // ---- decoration ------------------------------------------------------------------------------------------------

    private void decorate(int x, int z, double r, double dist, BlockState[] col) {
        int vineLeft = 0;
        boolean vineLit = false;
        for (int y = Layout.HEIGHT - 2; y >= 1; y--) {
            BlockState here = col[y];
            if (vineLeft > 0) {
                if (here == AIR) {
                    vineLeft--;
                    col[y] = vineLeft == 0 ? (vineLit ? VINE_TIP_LIT : VINE_TIP) : (vineLit && (y & 1) == 0 ? VINE_PLANT_LIT : VINE_PLANT);
                    continue;
                }
                if (y + 1 < Layout.HEIGHT && (col[y + 1] == VINE_PLANT || col[y + 1] == VINE_PLANT_LIT)) {
                    col[y + 1] = vineLit ? VINE_TIP_LIT : VINE_TIP;
                }
                vineLeft = 0;
            }
            BlockState above = col[y + 1];
            if (form == 7 && isRock(here) && above == WATER && y < 46) {
                col[y] = pal.floor;
                continue;
            }
            if (isRock(here) && above == AIR) {
                col[y] = topBlock(x, y, z);
                if (form == 2 && y + 1 < Layout.HEIGHT - 1) {
                    double h = Strata.hash01(seed + 11, x, y, z);
                    if (h < 0.05) col[y + 1] = LEAVES;
                    else if (h < 0.065) col[y + 1] = pal.light;
                }
                continue;
            }
            if (here == AIR && isSolidCeiling(above)) {
                double h = Strata.hash01(seed + 13, x, y, z);
                switch (form) {
                    case 1 -> { if (h < 0.22) col[y] = HANGING_ROOTS; else if (h < 0.24) col[y + 1] = pal.light; }
                    case 2 -> {
                        if (h < 0.2) {
                            vineLeft = 3 + (int) (Strata.hash01(seed + 17, x, y, z) * 16);
                            vineLit = Strata.hash01(seed + 19, x, y, z) < 0.45;
                            vineLeft--;
                            col[y] = vineLeft == 0 ? (vineLit ? VINE_TIP_LIT : VINE_TIP) : VINE_PLANT;
                        }
                    }
                    case 4 -> { if (h < 0.025) col[y + 1] = pal.light; }
                    case 7 -> { if (h < 0.03) col[y + 1] = pal.wallGlow; }
                    default -> { if (h < 0.018) col[y + 1] = pal.light; }
                }
            }
        }
    }

    private boolean isSolidCeiling(BlockState s) {
        return s != AIR && s != WATER && s != VINE_PLANT && s != VINE_PLANT_LIT && s != VINE_TIP && s != VINE_TIP_LIT
                && s != LEAVES && s != HANGING_ROOTS;
    }

    private BlockState topBlock(int x, int y, int z) {
        return switch (form) {
            case 1 -> Strata.hash01(seed + 3, x, y, z) < 0.15 ? MOSS : pal.floor;
            case 7 -> y > 46 ? (Strata.hash01(seed + 3, x, y, z) < 0.06 ? pal.wallGlow : pal.rockAlt) : pal.floor;
            case 9 -> ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1) == 0 ? pal.floor : pal.rock;
            case 10 -> {
                double rr = Math.sqrt((double) x * x + (double) z * z);
                yield ((int) (rr / 4) & 1) == 0 ? pal.floor : pal.rock;
            }
            default -> pal.floor;
        };
    }

    // ---- landmarks (arrival platform, camp, pit, foundry puzzle) -----------------------------------------------------

    private void landmarks(int x, int z, BlockState[] col) {
        int entryZ = -entryDistance(form);
        int floor = Layout.floorY(form);
        // Arrival platform
        int ex = x, ez = z - entryZ;
        int e2 = ex * ex + ez * ez;
        if (e2 <= Layout.PLATFORM_R * Layout.PLATFORM_R + 1) {
            boolean rim = e2 >= (Layout.PLATFORM_R - 1) * (Layout.PLATFORM_R - 1) + 1 && (ex == 0 || ez == 0);
            col[Layout.ENTRY_Y - 1] = rim ? pal.platformGlow : pal.platform;
            for (int y = Layout.ENTRY_Y; y <= Layout.ENTRY_Y + 5; y++) col[y] = AIR;
            if (ex == 0 && ez == -4) col[Layout.ENTRY_Y] = DescentContent.ASCENT_STONE.get().defaultBlockState();
        }
        int ax = Math.abs(x), az = Math.abs(z);
        // Pit and its rim
        if (ax <= Layout.PIT_HALF + 1 && az <= Layout.PIT_HALF + 1) {
            for (int y = floor; y <= floor + 4; y++) col[y] = AIR;
            if (ax <= Layout.PIT_HALF && az <= Layout.PIT_HALF) {
                col[floor - 1] = DescentContent.DESCENT_PIT.get().defaultBlockState();
                col[floor - 2] = BEDROCK;
                if (form == 6) col[floor] = DescentContent.FOUNDRY_SEAL.get().defaultBlockState();
            } else {
                col[floor - 1] = pal.platformGlow;
            }
        }
        // Camp
        if (x >= 6 && x <= 12 && az <= 4) {
            col[floor - 1] = pal.platform;
            for (int y = floor; y <= floor + 3; y++) col[y] = AIR;
            BlockPos w = Layout.waystone(form), bed = Layout.bedFoot(form), lantern = Layout.lantern(form);
            if (x == w.getX() && z == w.getZ()) col[floor] = DescentContent.WAYSTONE.get().defaultBlockState();
            if (x == bed.getX() && z == bed.getZ()) col[floor] = bed(BedPart.FOOT);
            if (x == bed.getX() + 1 && z == bed.getZ()) col[floor] = bed(BedPart.HEAD);
            if (x == lantern.getX() && z == lantern.getZ()) col[floor] = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        }
        // Foundry conduits
        if (form == 6) {
            for (int i = 0; i < Layout.CONDUITS; i++) {
                BlockPos c = Layout.conduit(form, i);
                if (x == c.getX() && z == c.getZ()) {
                    col[floor - 1] = pal.platform;
                    col[floor] = DescentContent.CONDUIT.get().defaultBlockState();
                    col[floor + 1] = AIR;
                    col[floor + 2] = AIR;
                }
            }
        }
    }

    private static BlockState bed(BedPart part) {
        return Blocks.STRAW_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, part);
    }

    // ---- digest for tests ------------------------------------------------------------------------------------------

    /** A stable digest of a 16x16 area (used by the determinism GameTest). */
    public long digest(int lx0, int lz0) {
        Scratch s = SCRATCH.get();
        chunkNodes(lx0, lz0, s);
        long h = 1469598103934665603L;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                BlockState[] col = chunkColumn(lx0, lz0, lx, lz, s);
                for (int y = 0; y < Layout.HEIGHT; y++) {
                    h = (h ^ System.identityHashCode(col[y].getBlock()) ^ (col[y].hashCode() * 31L)) * 1099511628211L;
                }
            }
        }
        return h;
    }
}
