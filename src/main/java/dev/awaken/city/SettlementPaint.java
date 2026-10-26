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

    /** Dev and studio: repaints a settlement's chunk in a live world (setBlock, no updates). Returns blocks changed. */
    public static int repaint(Layout l, net.minecraft.server.level.ServerLevel level, int chunkX, int chunkZ) {
        CityBuffer buf = paint(l, chunkX, chunkZ, level.getSeed(), new CityBuffer(16, 16));
        return CityWriter.toLevel(level, level.getChunk(chunkX, chunkZ), buf, l.baseY());
    }
}
