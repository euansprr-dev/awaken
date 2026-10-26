package dev.awaken.city;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The city's painter for settlements other than Carillon (SPEC §28.6, PLAN 5.3): a village's planned raster and plots,
 * painted per chunk at world generation the way Carillon is (pure: the same layout, chunk and seed always give the same
 * blocks; written straight into the proto chunk's sections). The settlement paints its own ground and pieces (streets,
 * the green, fields, sheds) on a {@link Canvas}; its houses are painted by the city's grammar in the styles of
 * {@link VillageStyles} and furnished from who lives there ({@link CityInteriors}); its street things (wells, notice
 * boards, benches, carts, lines) are the city's props. Carillon's own painting never comes through here.
 */
public final class SettlementPaint {
    private SettlementPaint() {}

    /** A settlement's plan as the painter reads it. Plan coordinates are offsets from the origin; heights from its street level. */
    public interface Layout extends PlanView {
        int originX();

        int originZ();

        /** The street level heights are measured from (world y). */
        int baseY();

        /** The plot ids touching a chunk (world chunk coordinates), in a fixed order; plots reach 3 out from their walls. */
        int[] plots(int chunkX, int chunkZ);

        /** The props touching a chunk, in a fixed order. */
        List<CityPlan.Prop> props(int chunkX, int chunkZ);

        /** The settlement's own ground and pieces in the canvas's area: before the houses (ground) or after (pieces). */
        void paint(Canvas canvas, boolean beforeHouses);

        /** Who lives in a plot (its rooms are furnished for them), or null for an empty or working building's own fit. */
        @Nullable Interior interior(int plotId);
    }

    /**
     * Who a house is for: how many sleep there (a bed each; children share a room), the trade it keeps (its blocks:
     * "smith", "inn", "farmer", "scribe", "miller", "baker", "weaver", "fisher", "hunter", "woodcutter", "healer",
     * "elder", "" for none), and a seed so two houses of one family never match.
     */
    public record Interior(int adults, int children, String trade, int seed) {}

    /** What the settlement paints on: the chunk's buffer in plan coordinates, and the world seed's detail draw. */
    public static final class Canvas {
        final CityBuffer buf;
        final CityPainter.Ctx ctx;

        Canvas(CityBuffer buf, CityPainter.Ctx ctx) {
            this.buf = buf;
            this.ctx = ctx;
        }

        public static final int YMIN = CityBuffer.YMIN, YMAX = CityBuffer.YMAX;

        public void set(int x, int ry, int z, BlockState s) {
            buf.set(x, ry, z, s);
        }

        public @Nullable BlockState get(int x, int ry, int z) {
            return buf.get(x, ry, z);
        }

        public void column(int x, int ry0, int ry1, int z, BlockState s) {
            buf.column(x, ry0, ry1, z, s);
        }

        /** Sets a cell only where it is open air (another piece's work is never overwritten). */
        public void air(int x, int ry, int z, BlockState s) {
            CityProps.air(buf, x, ry, z, s);
        }

        /** The writer carries this column's ground down to solid rock (a fill over a dip or a pond). */
        public void plate(int x, int z) {
            buf.plate(x, z);
        }

        public boolean contains(int x, int z) {
            return buf.contains(x, z);
        }

        public int x0() {
            return buf.px0;
        }

        public int z0() {
            return buf.pz0;
        }

        public int x1() {
            return buf.x1();
        }

        public int z1() {
            return buf.z1();
        }

        /** 0..99 for a cell and a salt, from the world seed (details: flowers, moss, which crate). */
        public int pct(int x, int z, int salt) {
            return ctx.pct(x, z, salt);
        }
    }

    private static final ThreadLocal<CityBuffer> LOCAL = ThreadLocal.withInitial(() -> new CityBuffer(16, 16));

    /**
     * Paints a settlement's share of chunk (chunkX, chunkZ) into a buffer: its ground, then its houses (grammar and
     * rooms), then its own pieces, then its props. For the writer, tests and commands.
     */
    static CityBuffer paint(Layout l, int chunkX, int chunkZ, long worldSeed, CityBuffer buf) {
        buf.reset((chunkX << 4) - l.originX(), (chunkZ << 4) - l.originZ());
        CityPainter.Ctx ctx = new CityPainter.Ctx(l, worldSeed);
        Canvas canvas = new Canvas(buf, ctx);
        l.paint(canvas, true);
        for (int id : l.plots(chunkX, chunkZ)) {
            CityPlan.Plot p = l.plot(id);
            CityGrammar.plot(ctx, buf, p);
            Interior who = l.interior(id);
            if (who != null && p.enter()) CityInteriors.furnish(ctx, buf, p, who);
        }
        l.paint(canvas, false);
        for (CityPlan.Prop pr : l.props(chunkX, chunkZ)) CityProps.prop(ctx, buf, pr);
        return buf;
    }

    /** World generation: paints the settlement's share of a proto chunk and writes it. Returns blocks written. */
    public static int paintChunk(Layout l, int chunkX, int chunkZ, long worldSeed, ChunkAccess chunk) {
        CityBuffer buf = paint(l, chunkX, chunkZ, worldSeed, LOCAL.get());
        return CityWriter.toProto(chunk, buf, l.baseY());
    }

    /** Counts the cells a chunk's paint would set (tests): {@code [cells, light sources]}. */
    public static int[] census(Layout l, int chunkX, int chunkZ, long worldSeed) {
        CityBuffer buf = paint(l, chunkX, chunkZ, worldSeed, new CityBuffer(16, 16));
        int cells = 0, lights = 0;
        for (BlockState s : buf.cells) {
            if (s == null) continue;
            cells++;
            if (s.getLightEmission() > 0) lights++;
        }
        return new int[]{cells, lights};
    }

    /** A fingerprint of a chunk's paint (tests: the same plan paints the same blocks). */
    public static long fingerprint(Layout l, int chunkX, int chunkZ, long worldSeed) {
        CityBuffer buf = paint(l, chunkX, chunkZ, worldSeed, new CityBuffer(16, 16));
        long h = 1469598103934665603L;
        for (BlockState s : buf.cells) h = (h ^ (s == null ? -1 : net.minecraft.world.level.block.Block.getId(s))) * 1099511628211L;
        for (boolean p : buf.plate) h = (h ^ (p ? 1 : 0)) * 1099511628211L;
        return h;
    }

    /**
     * The cells of a house's ground storey a body can stand in (floor below, two clear rows over it, never the door's own
     * cell), from painting the plot and its rooms alone: where an inn's guests stand at night (SPEC §28.6). Plan cells.
     */
    public static java.util.List<int[]> standingCells(Layout l, int plotId, long worldSeed) {
        CityPlan.Plot p = l.plot(plotId);
        CityBuffer buf = new CityBuffer(p.x1() - p.x0() + 7, p.z1() - p.z0() + 7);
        buf.reset(p.x0() - 3, p.z0() - 3);
        CityPainter.Ctx ctx = new CityPainter.Ctx(l, worldSeed);
        CityGrammar.plot(ctx, buf, p);
        Interior who = l.interior(plotId);
        if (who != null && p.enter()) CityInteriors.furnish(ctx, buf, p, who);
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int g = p.lift();
        for (int z = p.z0() + 1; z <= p.z1() - 1; z++) {
            for (int x = p.x0() + 1; x <= p.x1() - 1; x++) {
                BlockState f = buf.get(x, g, z), a = buf.get(x, g + 1, z), b = buf.get(x, g + 2, z);
                if (f == null || f.isAir()) continue;
                boolean low = a == null || a.isAir() || a.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock;
                if (low && (b == null || b.isAir())) out.add(new int[]{x, z});
            }
        }
        return out;
    }

    /**
     * Where a settlement's chimneys stand (plan x, the row the smoke rises from, z, and the plot), from the grammar's own
     * chimney rule: the client puffs smoke there (SPEC §28.6).
     */
    public static java.util.List<int[]> chimneys(Layout l, int plots) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        CityPainter.Ctx ctx = new CityPainter.Ctx(l, 0L);
        for (int id = 0; id < plots; id++) {
            CityPlan.Plot p = l.plot(id);
            CityGrammar.Facade f = CityGrammar.facadeOf(ctx, p);
            int[] cs = CityGrammar.chimneySpot(f);
            if (cs == null) continue;
            int span = p.ridgeX() ? f.up.d() : f.up.w();
            int top = p.lift() + p.eave() + CityGrammar.rise(p.steep(), (span + 1) / 2) + 2;
            out.add(new int[]{cs[0], top + 2, cs[1], id});
        }
        return out;
    }

    /** How high over its ground a settlement clears trees off the ground it uses. */
    public static final int CLEAR = 12;

    /**
     * After a settlement chunk is written: trees a neighbouring chunk grew may hang leaves and limbs into the ground the
     * settlement uses (its streets, yards and fields), and the trees it cut may leave pieces hanging. Like the city's sweep
     * (CityWriter): vegetation in the neighbours' used columns up to {@link #CLEAR} over their ground goes, then any
     * group of logs and leaves round this chunk that stands on nothing. Returns blocks removed.
     */
    public static int sweep(net.minecraft.world.level.WorldGenLevel level, Layout l, int chunkX, int chunkZ) {
        int removed = 0;
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                int nx = chunkX + dx, nz = chunkZ + dz;
                ChunkAccess n = level.getChunk(nx, nz, net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN, false);
                if (n == null) continue;
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int wx = (nx << 4) + lx, wz = (nz << 4) + lz;
                        int px = wx - l.originX(), pz = wz - l.originZ();
                        byte k = l.kind(px, pz);
                        if (k == CityPlan.VOID || k == CityPlan.FREE || k == CityPlan.RIVER) continue;
                        int g = l.baseY() + l.lift(px, pz);
                        for (int y = g + 1; y <= g + CLEAR; y++) {
                            BlockState s = n.getBlockState(pos.set(wx, y, wz));
                            if (!CityWriter.vegetation(s)) continue;
                            level.setBlock(pos, air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            removed++;
                        }
                    }
                }
            }
        }
        // what the cut left hanging round this chunk
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap blocks = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet loaded = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = chunkX + dx, nz = chunkZ + dz;
                ChunkAccess c = dx == 0 && dz == 0 ? level.getChunk(nx, nz) : level.getChunk(nx, nz, net.minecraft.world.level.chunk.status.ChunkStatus.TERRAIN, false);
                if (c == null) continue;
                loaded.add(net.minecraft.world.level.ChunkPos.pack(nx, nz));
                CityTrees.read(c, CityWriter::vegetation, blocks, null);
            }
        }
        if (blocks.isEmpty()) return removed;
        it.unimi.dsi.fastutil.longs.LongArrayList doomed = new it.unimi.dsi.fastutil.longs.LongArrayList();
        CityTrees.groups(blocks, loaded::contains, (cells, grounded, clipped) -> {
            if (grounded || clipped) return;
            boolean tree = false;
            for (int i = 0; i < cells.size() && !tree; i++) tree = (blocks.get(cells.getLong(i)) & (CityTrees.WOOD | CityTrees.LEAF)) != 0;
            if (tree) doomed.addAll(cells);
        });
        for (int i = 0; i < doomed.size(); i++) {
            long p = doomed.getLong(i);
            level.setBlock(pos.set(net.minecraft.core.BlockPos.getX(p), net.minecraft.core.BlockPos.getY(p), net.minecraft.core.BlockPos.getZ(p)), air,
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        return removed + doomed.size();
    }

    /**
     * Dev studio: paints a whole settlement into memory and writes it in the city dump's format for
     * {@code tools/city/render.py}; where the settlement leaves a column to the land, the land is drawn from
     * {@code land} (its top's lift over the street, grass on dirt; below the sea level, water), so the render reads as a
     * place. Returns the chunks written.
     */
    public static int dump(java.nio.file.Path out, Layout l, long seed, long[] chunks, java.util.function.IntBinaryOperator land) throws java.io.IOException {
        return dump(out, l, seed, chunks, land, 0);
    }

    /** As {@link #dump}, with every house cut off {@code cut} rows over its floor (0: whole), so its rooms show from above. */
    public static int dump(java.nio.file.Path out, Layout l, long seed, long[] chunks, java.util.function.IntBinaryOperator land, int cut) throws java.io.IOException {
        it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<BlockState> ids = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<>();
        java.util.List<String> palette = new java.util.ArrayList<>();
        palette.add("none");
        CityBuffer buf = new CityBuffer(16, 16);
        int ccx = l.originX() >> 4, ccz = l.originZ() >> 4;
        java.nio.file.Files.createDirectories(out.toAbsolutePath().getParent());
        java.nio.file.Path tmp = out.resolveSibling(out.getFileName() + ".chunks");
        BlockState grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),
                water = net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
        int n = 0;
        try (java.io.DataOutputStream d = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(tmp)), 1 << 16))) {
            short[] cells = new short[16 * 16 * CityBuffer.H];
            for (long key : chunks) {
                int x = net.minecraft.world.level.ChunkPos.getX(key), z = net.minecraft.world.level.ChunkPos.getZ(key);
                paint(l, x, z, seed, buf);
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int px = buf.px0 + lx, pz = buf.pz0 + lz;
                        int pid = l.plotAt(px, pz);
                        if (cut > 0 && pid >= 0) {
                            int from = l.plot(pid).lift() + cut;
                            for (int ry = from + 1; ry <= CityBuffer.YMAX; ry++) buf.cells[buf.index(lx, ry, lz)] = null;
                        }
                        int top = land.applyAsInt(px, pz);
                        boolean wet = top <= -1000;
                        if (wet) top += 2000;
                        // the land under and round what the settlement painted
                        int low = CityBuffer.YMIN;
                        while (low <= CityBuffer.YMAX && buf.cells[buf.index(lx, low, lz)] == null) low++;
                        for (int ry = CityBuffer.YMIN; ry <= Math.min(top, CityBuffer.YMAX); ry++) {
                            if (ry >= low) break;
                            buf.cells[buf.index(lx, ry, lz)] = ry == top ? (wet ? water : grass) : dirt;
                        }
                    }
                }
                for (int i = 0; i < cells.length; i++) {
                    BlockState s = buf.cells[i];
                    if (s == null) {
                        cells[i] = 0;
                        continue;
                    }
                    int id = ids.getOrDefault(s, -1);
                    if (id < 0) {
                        id = palette.size();
                        palette.add(s.toString());
                        ids.put(s, id);
                    }
                    cells[i] = (short) id;
                }
                d.writeInt(x - ccx);
                d.writeInt(z - ccz);
                for (short v : cells) d.writeShort(v);
                n++;
            }
        }
        try (java.io.DataOutputStream d = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(out)), 1 << 16))) {
            d.writeBytes("CITYDUMP");
            d.writeInt(2);
            d.writeInt(CityBuffer.YMIN);
            d.writeInt(CityBuffer.H);
            d.writeInt(palette.size());
            for (String s : palette) d.writeUTF(s);
            d.writeInt(n);
            d.writeInt(l.originX() - (ccx << 4));
            d.writeInt(l.originZ() - (ccz << 4));
        }
        return n;
    }

    /** Dev and studio: repaints a settlement's chunk in a live world (setBlock, no updates). Returns blocks changed. */
    public static int repaint(Layout l, net.minecraft.server.level.ServerLevel level, int chunkX, int chunkZ) {
        CityBuffer buf = paint(l, chunkX, chunkZ, level.getSeed(), new CityBuffer(16, 16));
        return CityWriter.toLevel(level, level.getChunk(chunkX, chunkZ), buf, l.baseY());
    }
}
