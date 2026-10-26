package dev.awaken.village.plan;

import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One settlement as planned (SPEC §28.6, PLAN 5.3): where it stands, its raster of ground kinds and heights round the
 * origin, its plots (the city's {@code Plot} records, painted by the city's grammar), its streets, the green and what
 * stands on it, fields, pens, a mill and a dock, its lamps, and who lives where. Made once per settlement by
 * {@link VillagePlanner} (pure, from the seed and the generator's height estimate), cached, immutable after; the painter
 * reads it on any worldgen thread, the people engine's roster is built from it on the server thread.
 *
 * <p>Plan coordinates are blocks from the origin, x east, z south; {@code lift} is over {@link #baseY}.
 */
public final class VillagePlan implements SettlementPaint.Layout {
    /** The planner's version: a plan made by another version is another plan (the piece remembers it). */
    public static final int VERSION = 1;

    public enum Kind { HAMLET, VILLAGE }

    // ground kinds beyond the city's (CityPlan.VOID .. LOWQUAY)
    public static final byte FIELD = 32, PEN = 33, DOCK = 34, KITCHEN = 35, WATER = CityPlan.RIVER;

    public final String key, name;
    public final Kind kind;
    public final int family, wealth;
    public final long seed;
    public final int ox, oz, baseY;
    /** Half the raster's side: plan x and z run -r .. r. */
    public final int r;
    final int n;
    final byte[] kinds, lifts;
    final short[] plotIds;
    /** Each cell's distance to the ground the settlement uses, in half blocks (capped): its clearing (see {@link #clears}). */
    private byte[] near;

    /** How far round its ground a settlement fells the land's trees (blocks). */
    public static final int CLEARING = 6;

    public final List<CityPlan.Plot> plots = new ArrayList<>();
    /** One per plot, same index. */
    public final List<Building> buildings = new ArrayList<>();
    public final List<Street> streets = new ArrayList<>();
    public final List<CityPlan.Prop> props = new ArrayList<>();
    public final List<Lamp> lamps = new ArrayList<>();
    public final List<Field> fields = new ArrayList<>();
    public final List<Pen> pens = new ArrayList<>();
    public final List<Garden> gardens = new ArrayList<>();
    /** Trees: x, z, lift, a seed. */
    public final List<int[]> trees = new ArrayList<>();
    final it.unimi.dsi.fastutil.longs.LongOpenHashSet treeCells = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    public final List<Household> households = new ArrayList<>();
    public final List<Person> people = new ArrayList<>();
    public final List<int[]> posts = new ArrayList<>(); // the Watch's posts at the street ends: x, z, lift
    public @Nullable Dock dock;
    public @Nullable Bell bell;
    public @Nullable Wheel wheel;
    public @Nullable Forge forge;
    /** The green: centre and radius; the well's middle cell; the notice board's cell. */
    public int greenR;
    public int[] well = new int[2], notice = new int[3];
    public int[] benches = new int[0];

    private final Long2ObjectOpenHashMap<int[]> plotsByChunk = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<CityPlan.Prop>> propsByChunk = new Long2ObjectOpenHashMap<>();

    VillagePlan(String key, String name, Kind kind, int family, int wealth, long seed, int ox, int oz, int baseY, int r) {
        this.key = key;
        this.name = name;
        this.kind = kind;
        this.family = family;
        this.wealth = wealth;
        this.seed = seed;
        this.ox = ox;
        this.oz = oz;
        this.baseY = baseY;
        this.r = r;
        this.n = 2 * r + 1;
        kinds = new byte[n * n];
        lifts = new byte[n * n];
        plotIds = new short[n * n];
        java.util.Arrays.fill(plotIds, (short) -1);
    }

    // ---- what it holds --------------------------------------------------------------------------------------------

    /** A street's centre line: points every few blocks with the paving's lift; rank 0 the main street, 1 a lane, 2 a path. */
    public record Street(int rank, float width, float[] x, float[] z, int[] lift) {
        public int points() {
            return x.length;
        }
    }

    /**
     * A building on a plot: its use ("house", "inn", "smithy", "mill", "barn", "granary"), the door's cell in its wall and
     * the cell outside it (where people stand to go in), and the street point its path joins (street, point).
     */
    public record Building(int plot, String use, int doorX, int doorZ, int outX, int outZ, int street, int point) {}

    /** A street lamp's post (its head stands four over the ground); the street point beside it. */
    public record Lamp(int x, int z, int lift, int street, int point) {}

    /** A strip field: its box, height, crop, which way its rows run, where the farmer stands. */
    public record Field(int x0, int z0, int x1, int z1, int lift, String crop, boolean rowsAlongX, int workX, int workZ) {}

    /** A pen: its box, height, beast, its gate (cell and the side it opens to). */
    public record Pen(int x0, int z0, int x1, int z1, int lift, String beast, int gateX, int gateZ, int gateSide) {}

    /** A kitchen garden behind a house: its box, height, crop, its gate (cell and the side it opens to). */
    public record Garden(int x0, int z0, int x1, int z1, int lift, String crop, int gateX, int gateZ, int gateSide) {}

    /** A pier: its deck's box over the water, the deck's lift, the end the fisher stands at. */
    public record Dock(int x0, int z0, int x1, int z1, int lift, int endX, int endZ, int side) {}

    /** The bell: a post (hamlets) or a tower (villages); its box, ground lift, and the bell's cell. */
    public record Bell(int x0, int z0, int x1, int z1, int lift, boolean tower, int bx, int bry, int bz, int ringerX, int ringerZ) {}

    /** The mill's wheel in the water beside its wall: centre, the wall side it turns on, axle height. */
    public record Wheel(int plot, int cx, int cz, int side, int axle) {}

    /** The forge's shed beside the smithy: its box, lift, the anvil's cell, where the smith stands. */
    public record Forge(int plot, int x0, int z0, int x1, int z1, int lift, int anvilX, int anvilZ, int smithX, int smithZ) {}

    /** A household: the building it lives in and its family name. */
    public record Household(int id, int building, String family) {}

    /**
     * A person: names, job (Roster.JOBS), age class (0 child .. 3 old), their household, build (0 wide, 1 slim) and where
     * they work: a kind ("field", "pen", "anvil", "inn", "mill", "dock", "bell", "well", "lamps", "post", "woods", "green", "")
     * and its index.
     */
    public record Person(String given, String family, String job, int age, int household, int build, String work, int workIndex) {}

    // ---- the raster ------------------------------------------------------------------------------------------------

    public boolean inRaster(int x, int z) {
        return x >= -r && x <= r && z >= -r && z <= r;
    }

    int idx(int x, int z) {
        return (z + r) * n + (x + r);
    }

    @Override
    public byte kind(int x, int z) {
        return inRaster(x, z) ? kinds[idx(x, z)] : CityPlan.VOID;
    }

    @Override
    public int lift(int x, int z) {
        return inRaster(x, z) ? lifts[idx(x, z)] : 0;
    }

    @Override
    public int plotAt(int x, int z) {
        return inRaster(x, z) ? plotIds[idx(x, z)] : -1;
    }

    @Override
    public CityPlan.Plot plot(int id) {
        return plots.get(id);
    }

    @Override
    public boolean treeAt(int x, int z) {
        return treeCells.contains(((long) x << 32) ^ (z & 0xffffffffL));
    }

    @Override
    public boolean lampNear(int x, int z) {
        for (Lamp l : lamps) if (Math.abs(l.x() - x) <= 1 && Math.abs(l.z() - z) <= 1) return true;
        return false;
    }

    @Override
    public boolean clears(int x, int z) {
        if (!inRaster(x, z) || near == null) return false;
        int i = idx(x, z);
        return kinds[i] == CityPlan.FREE && near[i] <= 2 * CLEARING;
    }

    /** The clearing's distances: two passes of a chamfer (2 straight, 3 diagonal) from every used cell. */
    private void clearing() {
        near = new byte[n * n];
        for (int i = 0; i < near.length; i++) {
            byte k = kinds[i];
            near[i] = (byte) (k != CityPlan.VOID && k != CityPlan.FREE && k != WATER || plotIds[i] >= 0 ? 0 : 100);
        }
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) {
                int d = near[z * n + x];
                if (x > 0) d = Math.min(d, near[z * n + x - 1] + 2);
                if (z > 0) {
                    d = Math.min(d, near[(z - 1) * n + x] + 2);
                    if (x > 0) d = Math.min(d, near[(z - 1) * n + x - 1] + 3);
                    if (x + 1 < n) d = Math.min(d, near[(z - 1) * n + x + 1] + 3);
                }
                near[z * n + x] = (byte) Math.min(100, d);
            }
        for (int z = n - 1; z >= 0; z--)
            for (int x = n - 1; x >= 0; x--) {
                int d = near[z * n + x];
                if (x + 1 < n) d = Math.min(d, near[z * n + x + 1] + 2);
                if (z + 1 < n) {
                    d = Math.min(d, near[(z + 1) * n + x] + 2);
                    if (x + 1 < n) d = Math.min(d, near[(z + 1) * n + x + 1] + 3);
                    if (x > 0) d = Math.min(d, near[(z + 1) * n + x - 1] + 3);
                }
                near[z * n + x] = (byte) Math.min(100, d);
            }
    }

    void set(int x, int z, byte k, int lift) {
        if (!inRaster(x, z)) return;
        int i = idx(x, z);
        kinds[i] = k;
        lifts[i] = (byte) Math.clamp(lift, -100, 100);
    }

    void setKind(int x, int z, byte k) {
        if (inRaster(x, z)) kinds[idx(x, z)] = k;
    }

    void setPlot(int x, int z, int id) {
        if (inRaster(x, z)) plotIds[idx(x, z)] = (short) id;
    }

    /** Whether a world column lies in the settlement's planned ground (its streets, plots, yards, fields: not the land round it). */
    public boolean owns(int worldX, int worldZ) {
        byte k = kind(worldX - ox, worldZ - oz);
        return k != CityPlan.VOID && k != CityPlan.FREE && k != WATER;
    }

    /** The radius people count as inside (its furthest planned ground, rounded up). */
    public int radius() {
        int far = 0;
        for (int z = -r; z <= r; z++)
            for (int x = -r; x <= r; x++) {
                byte k = kinds[idx(x, z)];
                if (k != CityPlan.VOID && k != CityPlan.FREE) far = Math.max(far, (int) Math.ceil(Math.sqrt(x * x + z * z)));
            }
        return far + 4;
    }

    // ---- the painter's view (SettlementPaint.Layout) ----------------------------------------------------------------

    @Override
    public int originX() {
        return ox;
    }

    @Override
    public int originZ() {
        return oz;
    }

    @Override
    public int baseY() {
        return baseY;
    }

    @Override
    public int[] plots(int chunkX, int chunkZ) {
        int[] a = plotsByChunk.get(ChunkPos.pack(chunkX, chunkZ));
        return a == null ? NONE : a;
    }

    @Override
    public List<CityPlan.Prop> props(int chunkX, int chunkZ) {
        List<CityPlan.Prop> l = propsByChunk.get(ChunkPos.pack(chunkX, chunkZ));
        return l == null ? List.of() : l;
    }

    @Override
    public void paint(SettlementPaint.Canvas canvas, boolean beforeHouses) {
        VillagePainter.paint(this, canvas, beforeHouses);
    }

    @Override
    public SettlementPaint.@Nullable Interior interior(int plotId) {
        Building b = buildings.get(plotId);
        int adults = 0, children = 0;
        String trade = "";
        for (Household h : households) {
            if (h.building() != plotId) continue;
            for (Person p : people) {
                if (p.household() != h.id()) continue;
                if (p.age() == 0) children++;
                else adults++;
                if (trade.isEmpty() && p.age() > 0 && !p.job().equals("resident")) trade = p.job();
            }
        }
        String use = b.use();
        switch (use) {
            case "inn" -> trade = "inn";
            case "smithy" -> trade = "smith";
            case "mill" -> trade = "miller";
            case "barn", "granary" -> trade = use;
            default -> {
            }
        }
        return new SettlementPaint.Interior(adults, children, trade, (int) (seed ^ plotId * 0x9E3779B1L));
    }

    private static final int[] NONE = new int[0];

    /** After planning: which plots and props each chunk paints (plots reach three out from their walls). */
    void index() {
        clearing();
        Long2ObjectOpenHashMap<List<Integer>> lists = new Long2ObjectOpenHashMap<>();
        for (CityPlan.Plot p : plots) {
            for (int cx = (ox + p.x0() - 3) >> 4; cx <= (ox + p.x1() + 3) >> 4; cx++)
                for (int cz = (oz + p.z0() - 3) >> 4; cz <= (oz + p.z1() + 3) >> 4; cz++)
                    lists.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(p.id());
        }
        for (var e : lists.long2ObjectEntrySet()) plotsByChunk.put(e.getLongKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        for (CityPlan.Prop p : props) {
            for (int cx = (ox + p.x0() - 1) >> 4; cx <= (ox + p.x1() + 1) >> 4; cx++)
                for (int cz = (oz + p.z0() - 1) >> 4; cz <= (oz + p.z1() + 1) >> 4; cz++)
                    propsByChunk.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(p);
        }
    }

    /** Every chunk the settlement paints into (world chunk coordinates, packed). */
    public long[] chunks() {
        List<Long> out = new ArrayList<>();
        for (int cx = (ox - r) >> 4; cx <= (ox + r) >> 4; cx++)
            for (int cz = (oz - r) >> 4; cz <= (oz + r) >> 4; cz++) {
                boolean any = false;
                for (int x = Math.max(-r, (cx << 4) - ox); x <= Math.min(r, (cx << 4) + 15 - ox) && !any; x++)
                    for (int z = Math.max(-r, (cz << 4) - oz); z <= Math.min(r, (cz << 4) + 15 - oz) && !any; z++) {
                        byte k = kinds[idx(x, z)];
                        any = k != CityPlan.VOID && k != CityPlan.FREE && k != WATER || plotIds[idx(x, z)] >= 0;
                    }
                if (any || plotsByChunk.containsKey(ChunkPos.pack(cx, cz)) || propsByChunk.containsKey(ChunkPos.pack(cx, cz)))
                    out.add(ChunkPos.pack(cx, cz));
            }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** The world position of a plan cell's ground (its top block). */
    public net.minecraft.core.BlockPos ground(int x, int z) {
        return new net.minecraft.core.BlockPos(ox + x, baseY + lift(x, z), oz + z);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s %s (%s, %s, wealth %d) at %d %d %d: %d plots, %d streets, %d people",
                kind, name, key, dev.awaken.city.VillageStyles.FAMILIES[family], wealth, ox, baseY, oz, plots.size(), streets.size(), people.size());
    }
}
