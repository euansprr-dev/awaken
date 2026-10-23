package dev.awaken.descent.well;

import dev.awaken.Awaken;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.DescentModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Well: a Precursor ring around a deep shaft, 400-700 blocks from world spawn on solid ground, built once per world
 * at runtime (SavedData flag) because landmark structures can't be placed relative to spawn. Looking down you see the
 * lining's glyphs and the lights at the bottom; jumping in (or the lift on the rim) takes you to stratum 1.
 */
public final class Well {
    private Well() {}

    public static final int MIN_DISTANCE = 400, MAX_DISTANCE = 700;
    public static final int RING_INNER = 5, RING_OUTER = 13;
    public static final int DEPTH = 110;
    private static final int SALT = 77031;

    /** Candidate sites around spawn, all within [MIN_DISTANCE + margin, MAX_DISTANCE - margin]. Pure. */
    public static List<BlockPos> candidates(long seed, int spawnX, int spawnZ, int count) {
        XoroshiroRandomSource r = new XoroshiroRandomSource(seed ^ SALT);
        double base = r.nextDouble() * Math.PI * 2;
        List<BlockPos> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = base + i * 2.399963229728653; // golden angle: spread the tries around the ring
            double dist = MIN_DISTANCE + RING_OUTER + 16 + r.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE - 2 * (RING_OUTER + 16));
            out.add(new BlockPos(spawnX + (int) Math.round(Math.cos(angle) * dist), 0, spawnZ + (int) Math.round(Math.sin(angle) * dist)));
        }
        return out;
    }

    private static boolean building;

    /** Called at server start (and when the feature flag is on): chooses and builds the Well if it doesn't exist. */
    public static void ensure(MinecraftServer server) {
        WellData data = WellData.get(server);
        if (data.built() || building || !DescentModule.enabled()) return;
        ServerLevel overworld = server.overworld();
        if (data.state == WellData.CHOSEN) {
            build(server, data);
            return;
        }
        BlockPos spawn = overworld.getRespawnData().pos();
        List<BlockPos> sites = candidates(overworld.getSeed(), spawn.getX(), spawn.getZ(), 32);
        ChunkGenerator gen = overworld.getChunkSource().getGenerator();
        RandomState rs = overworld.getChunkSource().randomState();
        building = true;
        CompletableFuture.supplyAsync(() -> pick(sites, gen, rs, overworld), Util.backgroundExecutor())
                .thenAcceptAsync(site -> {
                    data.state = WellData.CHOSEN;
                    data.x = site.getX();
                    data.z = site.getZ();
                    data.y = site.getY();
                    data.setDirty();
                    Awaken.LOG.info("AWAKEN: the Well will stand at {} {}", site.getX(), site.getZ());
                    build(server, data);
                }, server)
                .exceptionally(t -> {
                    Awaken.LOG.error("AWAKEN: could not place the Well", t);
                    building = false;
                    return null;
                });
    }

    /** First candidate on dry, fairly flat land (estimated from the generator, no chunk loads). */
    static BlockPos pick(List<BlockPos> sites, ChunkGenerator gen, RandomState rs, ServerLevel level) {
        int sea = gen.getSeaLevel();
        BlockPos fallback = null;
        for (BlockPos s : sites) {
            int[] h = new int[9];
            boolean dry = true;
            int k = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = s.getX() + dx * 12, z = s.getZ() + dz * 12;
                    int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
                    int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
                    if (surface != floor || surface <= sea + 1) dry = false;
                    h[k++] = surface;
                }
            }
            Arrays.sort(h);
            BlockPos site = new BlockPos(s.getX(), h[4] - 1, s.getZ());
            if (fallback == null) fallback = site;
            if (dry && h[8] - h[0] <= 7) return site;
        }
        return fallback;
    }

    private static void build(MinecraftServer server, WellData data) {
        building = true;
        ServerLevel level = server.overworld();
        ChunkPos center = ChunkPos.containing(new BlockPos(data.x, 0, data.z));
        level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, center, 2)
                .thenRunAsync(() -> startBuild(level, data), server);
    }

    private static void startBuild(ServerLevel level, WellData data) {
        // Ground level from the real terrain: median of the ring footprint.
        int[] h = new int[16];
        for (int i = 0; i < h.length; i++) {
            double a = i * Math.PI * 2 / h.length;
            h[i] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, data.x + (int) Math.round(Math.cos(a) * 10),
                    data.z + (int) Math.round(Math.sin(a) * 10)) - 1;
        }
        Arrays.sort(h);
        int y0 = Math.max(level.getSeaLevel() + 1, h[h.length / 2]);
        data.y = y0;
        data.bottom = Math.max(level.getMinY() + 6, y0 - DEPTH);
        data.setDirty();
        JOB = new Job(level, data);
    }

    private static Job JOB;

    /** Server tick: advances the build job, a bounded number of columns per tick. */
    public static void tick(MinecraftServer server) {
        Job j = JOB;
        if (j == null) return;
        if (j.step(24)) {
            JOB = null;
            building = false;
            j.data.state = WellData.BUILT;
            j.data.setDirty();
            DescentModule.onWellBuilt(server);
            Awaken.LOG.info("AWAKEN: the Well is built at {} {} {}", j.data.x, j.data.y, j.data.z);
        }
    }

    public static void reset() {
        JOB = null;
        building = false;
    }

    private static final class Job {
        final ServerLevel level;
        final WellData data;
        int column;
        static final int SPAN = RING_OUTER + 1;
        static final int SIDE = SPAN * 2 + 1;

        Job(ServerLevel level, WellData data) {
            this.level = level;
            this.data = data;
        }

        /** Returns true when finished. */
        boolean step(int columns) {
            for (int n = 0; n < columns; n++) {
                if (column >= SIDE * SIDE) return true;
                int dx = column % SIDE - SPAN, dz = column / SIDE - SPAN;
                column++;
                buildColumn(dx, dz);
            }
            return column >= SIDE * SIDE;
        }

        private void set(BlockPos.MutableBlockPos p, BlockState s) {
            level.setBlock(p, s, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }

        private void buildColumn(int dx, int dz) {
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > RING_OUTER + 0.5) return;
            int x = data.x + dx, z = data.z + dz, y0 = data.y, bottom = data.bottom;
            BlockState stone = DescentContent.WELL_STONE.get().defaultBlockState();
            BlockState glyph = DescentContent.WELL_GLYPH.get().defaultBlockState();
            BlockState light = DescentContent.WELL_LIGHT.get().defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            // clear above the ring
            for (int y = y0 + 1; y <= y0 + 16; y++) set(p.set(x, y, z), air);
            double a = Math.atan2(dz, dx);
            if (r <= RING_INNER) {
                for (int y = bottom + 2; y <= y0; y++) set(p.set(x, y, z), air);
                set(p.set(x, bottom + 1, z), Blocks.WATER.defaultBlockState());
                set(p.set(x, bottom, z), Blocks.WATER.defaultBlockState());
                set(p.set(x, bottom - 1, z), ((dx + dz) & 1) == 0 ? light : glyph);
                set(p.set(x, bottom - 2, z), stone);
                return;
            }
            if (r <= RING_INNER + 1.6) {
                // the lining: watertight, a glyph light every 12 blocks so you see the depth when you look down
                for (int y = bottom - 2; y <= y0 - 1; y++) {
                    boolean mark = (y0 - y) % 12 == 6 && Math.floorMod((int) Math.round(a * 4 / Math.PI), 2) == 0;
                    set(p.set(x, y, z), mark ? glyph : stone);
                }
                set(p.set(x, y0, z), stone);
                // a knee-high rim you can step over to jump in
                set(p.set(x, y0 + 1, z), Math.floorMod((int) Math.round(a * 8 / Math.PI), 4) == 0 ? air : stone);
                return;
            }
            // the ring: floor, foundation down to the ground, a glyph circle, four pillars
            boolean circle = Math.abs(r - 9.5) < 0.6 && Math.floorMod((int) Math.round(a * 6 / Math.PI), 2) == 0;
            set(p.set(x, y0, z), circle ? glyph : stone);
            for (int y = y0 - 1; y >= y0 - 12; y--) {
                p.set(x, y, z);
                BlockState here = level.getBlockState(p);
                if (y < y0 - 3 && !here.canBeReplaced() && here.getFluidState().isEmpty()) break;
                set(p, stone);
            }
            boolean pillar = Math.abs(r - 11.5) < 1.1 && Math.abs(Math.sin(2 * a)) > 0.985;
            if (pillar) {
                for (int y = y0 + 1; y <= y0 + 7; y++) set(p.set(x, y, z), y == y0 + 7 ? glyph : stone);
            }
            if (dx == RING_INNER + 3 && dz == 0) set(p.set(x, y0 + 1, z), DescentContent.WELL_LIFT.get().defaultBlockState());
        }
    }

    /** True when a player at this position has jumped into the shaft. */
    public static boolean inShaft(WellData data, double x, double y, double z) {
        if (!data.built()) return false;
        double dx = x - (data.x + 0.5), dz = z - (data.z + 0.5);
        return dx * dx + dz * dz < (RING_INNER + 0.4) * (RING_INNER + 0.4) && y < data.y - 6 && y > data.bottom - 4;
    }
}
