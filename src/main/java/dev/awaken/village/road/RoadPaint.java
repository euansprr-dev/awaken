package dev.awaken.village.road;

import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.city.VillageStyles;
import dev.awaken.village.plan.VillageBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One leg of a road as the settlement painter reads it (SPEC §28.9): a raster round the leg's line (the carriageway, its
 * verges, the band its trees are felled from) painted per chunk through the city's settlement writer, so a road clears
 * the trees it passes, its fill is carried down to the rock, and what hangs over it is swept like a village's. Its own
 * painting: the carriageway in the land's own stone and earth (dirt and gravel in the heartland with two wheel ruts and a
 * worn crown, cobbles and setts near a town, sand and sandstone in the desert, mud and packed earth in the mire), a slab's
 * half step wherever it climbs a block, a timber or stone bridge with rails and piers over water, boardwalks on posts
 * over the mire's shallows, a causeway over a brook; lantern posts by turns along the last stretch into a settlement;
 * the waymarkers. Pure: a chunk painted alone equals the same chunk painted with its neighbours.
 */
final class RoadPaint implements SettlementPaint.Layout {
    /** Extended point flags (beside the planner's water flags): lanterns here, a town near. */
    static final byte LIT = 8, TOWN = 16;

    final int ox, oz, baseY;
    /** The road's point index of the window's first point (markers name road points). */
    final int w0;
    final float[] xs, zs;
    final int[] ys;
    final byte[] fl, fam;
    /** The segments this leg paints: a .. b-1 (window indices); the others are its neighbours' (context only). */
    final int a, b;
    final float hw;
    final int cls;
    final List<Road.Marker> markers;
    // the raster
    final int rx0, rz0, rw, rd;
    final byte[] kinds;
    final short[] lifts;
    final short[] seg;
    final float[] dist, along;

    RoadPaint(int w0, float[] xs, float[] zs, int[] ys, byte[] fl, byte[] fam, int a, int b, int cls, List<Road.Marker> markers) {
        this.w0 = w0;
        this.xs = xs;
        this.zs = zs;
        this.ys = ys;
        this.fl = fl;
        this.fam = fam;
        this.a = a;
        this.b = b;
        this.cls = cls;
        this.hw = cls == Road.HIGHROAD ? 2.5f : 1.5f;
        this.markers = markers;
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        int lo = Integer.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
            lo = Math.min(lo, ys[i]);
        }
        this.ox = (int) Math.floor(xs[a]);
        this.oz = (int) Math.floor(zs[a]);
        this.baseY = lo - 2;
        int reach = (int) Math.ceil(hw + REACH) + 1;
        rx0 = (int) Math.floor(minX) - reach - ox;
        rz0 = (int) Math.floor(minZ) - reach - oz;
        rw = (int) Math.ceil(maxX) + reach - ox - rx0 + 1;
        rd = (int) Math.ceil(maxZ) + reach - oz - rz0 + 1;
        kinds = new byte[rw * rd];
        lifts = new short[rw * rd];
        seg = new short[rw * rd];
        dist = new float[rw * rd];
        along = new float[rw * rd];
        raster();
    }

    /** How far past the carriageway's edge the land's trees are felled. */
    static final float REACH = 4.0f;
    static final float VERGE = 1.6f;

    /** Each cell's nearest segment of the whole window; cells owned by this leg's segments get their kind and height. */
    private void raster() {
        for (int j = 0; j < rd; j++) {
            for (int i = 0; i < rw; i++) {
                double px = ox + rx0 + i + 0.5, pz = oz + rz0 + j + 0.5;
                double best = Double.MAX_VALUE, bt = 0;
                int bs = -1;
                for (int s = 0; s + 1 < xs.length; s++) {
                    double ax = xs[s], az = zs[s], dx = xs[s + 1] - ax, dz = zs[s + 1] - az, l2 = dx * dx + dz * dz;
                    double t = l2 == 0 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / l2, 0, 1);
                    double d = Math.hypot(ax + dx * t - px, az + dz * t - pz);
                    if (d < best) {
                        best = d;
                        bs = s;
                        bt = t;
                    }
                }
                int k = j * rw + i;
                seg[k] = (short) bs;
                dist[k] = (float) best;
                along[k] = (float) bt;
                // the road's ends: a cell past the first or last point belongs to the settlement or road it meets
                boolean off = bs == 0 && bt <= 0 && a == 0 && endsHere(0, px, pz) || bs == xs.length - 2 && bt >= 1 && b == xs.length - 1 && endsHere(xs.length - 1, px, pz);
                if (bs < a || bs >= b || off || best > hw + REACH) {
                    kinds[k] = CityPlan.VOID;
                    continue;
                }
                double y = ys[bs] + (ys[bs + 1] - ys[bs]) * bt;
                lifts[k] = (short) (Math.round(y) - baseY);
                byte f = flagsAt(bs, bt);
                kinds[k] = best <= hw ? CityPlan.STREET : best <= hw + VERGE && (f & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK)) == 0 ? CityPlan.YARD : CityPlan.FREE;
            }
        }
    }

    /** Past an end of the line, away from the road (the settlement's street or the crossing road takes over there). */
    private boolean endsHere(int i, double px, double pz) {
        int j = i == 0 ? 1 : i - 1;
        double dx = xs[i] - xs[j], dz = zs[i] - zs[j];
        return (px - xs[i]) * dx + (pz - zs[i]) * dz > 0;
    }

    /** A water crossing's flag holds over its whole segment; lanterns and towns by the nearer point. */
    byte flagsAt(int s, double t) {
        byte water = (byte) ((fl[s] & fl[s + 1]) & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.CAUSEWAY));
        if (water == 0 && (fl[s] | fl[s + 1]) != 0 && ((fl[s] ^ fl[s + 1]) & RoadPlanner.BRIDGE) != 0) {
            // a bridge's last segment onto the bank: the deck runs to its end
            water = (byte) (t < 0.5 ? fl[s] & RoadPlanner.BRIDGE : fl[s + 1] & RoadPlanner.BRIDGE);
        }
        byte near = t < 0.5 ? fl[s] : fl[s + 1];
        return (byte) (water | (near & (LIT | TOWN)));
    }

    int cell(int x, int z) {
        int i = x - rx0, j = z - rz0;
        return i < 0 || j < 0 || i >= rw || j >= rd ? -1 : j * rw + i;
    }

    // ---- the painter's view -----------------------------------------------------------------------------------------

    @Override
    public byte kind(int x, int z) {
        int k = cell(x, z);
        return k < 0 ? CityPlan.VOID : kinds[k];
    }

    @Override
    public int lift(int x, int z) {
        int k = cell(x, z);
        return k < 0 ? 0 : lifts[k];
    }

    @Override
    public int plotAt(int x, int z) {
        return -1;
    }

    @Override
    public CityPlan.Plot plot(int id) {
        throw new IllegalArgumentException("a road has no plots");
    }

    @Override
    public boolean treeAt(int x, int z) {
        return false;
    }

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
        return new int[0];
    }

    @Override
    public List<CityPlan.Prop> props(int chunkX, int chunkZ) {
        return List.of();
    }

    @Override
    public SettlementPaint.@Nullable Interior interior(int plotId) {
        return null;
    }

    @Override
    public boolean clears(int x, int z) {
        int k = cell(x, z);
        if (k < 0 || kinds[k] == CityPlan.VOID) return false;
        return (flagsAt(seg[k], along[k]) & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK)) == 0 || dist[k] > hw + 1;
    }

    @Override
    public void paint(SettlementPaint.Canvas c, boolean before) {
        if (before) {
            for (int z = c.z0(); z <= c.z1(); z++) for (int x = c.x0(); x <= c.x1(); x++) ground(c, x, z);
        } else {
            for (int s = a; s < b; s++) if ((fl[s] & LIT) != 0 && (fl[s] & RoadPlanner.BRIDGE) == 0 && Math.floorMod(s, 4) == 0) lamp(c, s);
            for (Road.Marker m : markers) RoadMarks.post(this, c, m);
        }
    }

    // ---- the ground ---------------------------------------------------------------------------------------------------

    /** 0..99 from a world column and a salt (the same in every leg, so overlapping legs agree). */
    static int pct(int wx, int wz, int salt) {
        long h = wx * 0x9E3779B97F4A7C15L + wz * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) Math.floorMod(h ^ (h >>> 33), 100L);
    }

    private void ground(SettlementPaint.Canvas c, int x, int z) {
        int k = cell(x, z);
        if (k < 0) return;
        byte kind = kinds[k];
        if (kind != CityPlan.STREET && kind != CityPlan.YARD) return;
        int s = seg[k];
        float t = along[k], d = dist[k];
        int g = lifts[k];
        byte f = flagsAt(s, t);
        int family = fam[t < 0.5f ? s : s + 1];
        int wx = ox + x, wz = oz + z;
        if ((f & RoadPlanner.BRIDGE) != 0) {
            bridge(c, x, z, g, d, s, t, family, (f & TOWN) != 0);
            return;
        }
        if ((f & RoadPlanner.BOARDWALK) != 0) {
            boardwalk(c, x, z, g, d, s, family);
            return;
        }
        if (kind == CityPlan.YARD) {
            // the verge: the land's own ground, trodden at the edge, a few flowers
            bed(c, x, g, z, family);
            int roll = pct(wx, wz, 51);
            c.set(x, g, z, verge(family, roll));
            c.column(x, g + 1, g + 4, z, VillageBlocks.of("minecraft:air"));
            if (roll >= 80 && family != VillageStyles.DESERT) c.set(x, g + 1, z, roll >= 94 ? FLOWER[pct(wx, wz, 52) % FLOWER.length] : SHORT_GRASS);
            c.plate(x, z);
            return;
        }
        BlockState pave = (f & RoadPlanner.CAUSEWAY) != 0 ? (pct(wx, wz, 53) < 30 ? MOSSY_COBBLE : COBBLE) : paving(family, d, (f & TOWN) != 0, wx, wz);
        bed(c, x, g, z, family);
        c.set(x, g, z, pave);
        c.column(x, g + 1, g + 5, z, AIR);
        c.plate(x, z);
        // a half step where the next cell along climbs a block
        for (int q = 0; q < 4; q++) {
            int nx = x + (q == 0 ? 1 : q == 1 ? -1 : 0), nz = z + (q == 2 ? 1 : q == 3 ? -1 : 0);
            int nk = cell(nx, nz);
            if (nk < 0 || kinds[nk] != CityPlan.STREET || lifts[nk] != g + 1) continue;
            if ((flagsAt(seg[nk], along[nk]) & RoadPlanner.BRIDGE) != 0) continue;
            c.set(x, g + 1, z, slabOf(pave, family));
            break;
        }
    }

    static void bed(SettlementPaint.Canvas c, int x, int g, int z, int family) {
        BlockState under = family == VillageStyles.DESERT ? SANDSTONE : DIRT;
        for (int ry = g - 3; ry < g; ry++) c.set(x, ry, z, under);
    }

    /**
     * The carriageway's surface by the land: a two-track country road (the crown between the wheels grown over, two worn
     * tracks, rougher shoulders on a highroad), each band mostly one ground so it reads along the road; near a town,
     * cobbles and setts.
     */
    static BlockState paving(int family, float d, boolean town, int wx, int wz) {
        int roll = pct(wx, wz, 50);
        boolean crown = d < 0.6f, rut = d >= 0.6f && d < 1.6f;
        if (town) return roll < 45 ? COBBLE : roll < 65 ? STONE_BRICKS : roll < 78 ? ANDESITE : roll < 88 ? MOSSY_COBBLE : roll < 95 ? GRAVEL : CRACKED_STONE_BRICKS;
        return switch (family) {
            case VillageStyles.DESERT -> crown ? (roll < 50 ? SMOOTH_SANDSTONE : roll < 80 ? SANDSTONE : SAND)
                    : rut ? (roll < 60 ? SAND : roll < 85 ? SANDSTONE : COARSE_DIRT) : (roll < 55 ? SAND : roll < 80 ? SMOOTH_SANDSTONE : SANDSTONE);
            case VillageStyles.SAVANNA -> crown ? (roll < 50 ? COARSE_DIRT : roll < 80 ? GRASS : PATH)
                    : rut ? (roll < 60 ? PATH : roll < 85 ? PACKED_MUD : COARSE_DIRT) : (roll < 55 ? COARSE_DIRT : roll < 80 ? PATH : GRAVEL);
            case VillageStyles.MIRE, VillageStyles.JUNGLE -> crown ? (roll < 40 ? GRASS : roll < 70 ? MUD : PACKED_MUD)
                    : rut ? (roll < 55 ? PATH : roll < 85 ? PACKED_MUD : MUD) : (roll < 40 ? PACKED_MUD : roll < 70 ? MUD : roll < 90 ? PATH : MOSSY_COBBLE);
            case VillageStyles.FROST -> crown ? (roll < 45 ? COARSE_DIRT : roll < 80 ? GRAVEL : PODZOL)
                    : rut ? (roll < 70 ? PATH : GRAVEL) : (roll < 50 ? GRAVEL : roll < 80 ? COARSE_DIRT : PATH);
            case VillageStyles.HIGHLAND -> crown ? (roll < 40 ? GRASS : roll < 75 ? GRAVEL : COARSE_DIRT)
                    : rut ? (roll < 55 ? GRAVEL : roll < 85 ? PATH : ANDESITE) : (roll < 35 ? COBBLE : roll < 70 ? GRAVEL : roll < 85 ? ANDESITE : STONE);
            case VillageStyles.ELDER -> crown ? (roll < 45 ? PODZOL : roll < 80 ? GRASS : COARSE_DIRT)
                    : rut ? (roll < 75 ? PATH : COARSE_DIRT) : (roll < 40 ? PODZOL : roll < 75 ? COARSE_DIRT : PATH);
            case VillageStyles.COAST -> crown ? (roll < 45 ? GRASS : roll < 75 ? SAND : PATH)
                    : rut ? (roll < 65 ? PATH : roll < 90 ? GRAVEL : SAND) : (roll < 45 ? GRAVEL : roll < 80 ? SAND : PATH);
            default -> crown ? (roll < 65 ? GRASS : roll < 90 ? PATH : COARSE_DIRT)
                    : rut ? (roll < 82 ? PATH : roll < 94 ? COARSE_DIRT : GRAVEL) : (roll < 40 ? COARSE_DIRT : roll < 70 ? PATH : roll < 85 ? GRAVEL : GRASS);
        };
    }

    static BlockState verge(int family, int roll) {
        return switch (family) {
            case VillageStyles.DESERT -> roll < 70 ? SAND : COARSE_DIRT;
            case VillageStyles.MIRE -> roll < 30 ? MUD : roll < 45 ? COARSE_DIRT : GRASS;
            case VillageStyles.FROST -> roll < 30 ? COARSE_DIRT : roll < 45 ? PODZOL : GRASS;
            case VillageStyles.ELDER -> roll < 40 ? PODZOL : GRASS;
            case VillageStyles.HIGHLAND -> roll < 25 ? GRAVEL : roll < 40 ? COARSE_DIRT : GRASS;
            case VillageStyles.SAVANNA -> roll < 35 ? COARSE_DIRT : GRASS;
            default -> roll < 22 ? COARSE_DIRT : roll < 30 ? PATH : GRASS;
        };
    }

    /** The half step where the road climbs a block: the paving's own stone, or an earthen one on an earth road, one per land. */
    static BlockState slabOf(BlockState pave, int family) {
        if (pave == PATH || pave == COARSE_DIRT || pave == GRASS || pave == PODZOL || pave == GRAVEL)
            return switch (family) {
                case VillageStyles.DESERT -> SANDSTONE_SLAB;
                case VillageStyles.FROST, VillageStyles.HIGHLAND -> COBBLE_SLAB;
                default -> MUD_BRICK_SLAB;
            };
        if (pave == COBBLE) return COBBLE_SLAB;
        if (pave == MOSSY_COBBLE) return MOSSY_COBBLE_SLAB;
        if (pave == STONE_BRICKS || pave == CRACKED_STONE_BRICKS) return STONE_BRICK_SLAB;
        if (pave == ANDESITE) return ANDESITE_SLAB;
        if (pave == STONE) return STONE_SLAB;
        if (pave == SAND || pave == SANDSTONE || pave == SMOOTH_SANDSTONE) return SANDSTONE_SLAB;
        if (pave == PACKED_MUD || pave == MUD) return MUD_BRICK_SLAB;
        return family == VillageStyles.DESERT ? SANDSTONE_SLAB : COBBLE_SLAB;
    }

    // ---- water ----------------------------------------------------------------------------------------------------------

    /** The land's timber (bridges, boardwalks, posts and the waymarkers' wood). */
    static String wood(int family) {
        return switch (family) {
            case VillageStyles.FROST, VillageStyles.HIGHLAND, VillageStyles.COAST -> "spruce";
            case VillageStyles.DESERT, VillageStyles.SAVANNA -> "acacia";
            case VillageStyles.MIRE -> "mangrove";
            case VillageStyles.ELDER -> "dark_oak";
            case VillageStyles.JUNGLE -> "jungle";
            case VillageStyles.CHERRY -> "cherry";
            default -> "oak";
        };
    }

    /**
     * A bridge: its deck level over the span; timber (planks on log piers, fence rails) on a lane or in the wilds, stone
     * near a town or on a highroad over a wide river (stone brick deck, walls for parapets, stone piers).
     */
    private void bridge(SettlementPaint.Canvas c, int x, int z, int g, float d, int s, float t, int family, boolean town) {
        boolean stone = town || cls == Road.HIGHROAD && span(s) >= 6;
        String w = wood(family);
        BlockState deck = stone ? (pct(ox + x, oz + z, 54) < 20 ? MOSSY_STONE_BRICKS : STONE_BRICKS) : VillageBlocks.of("minecraft:" + w + "_planks");
        c.set(x, g, z, deck);
        c.column(x, g + 1, g + 4, z, AIR);
        boolean edge = d > hw - 1.0f;
        // rails along both edges: a parapet of wall on stone, a fence on timber
        if (edge && d <= hw) {
            boolean ax = alongX(s);
            c.set(x, g + 1, z, stone ? (ax ? WALL_X : WALL_Z) : VillageBlocks.connected("minecraft:" + w + "_fence", false)[ax ? 10 : 5]);
        }
        // piers every few blocks under the edges, down to the bed (the writer stops at YMIN; the river's bed is near)
        boolean pier = edge && (Math.floorMod(s, 3) == 0 && t < 0.3f);
        if (pier) c.column(x, g - 16, g - 1, z, stone ? STONE_BRICKS : VillageBlocks.of("minecraft:stripped_" + log(w) + "[axis=y]"));
        else if (!stone) c.set(x, g - 1, z, VillageBlocks.of("minecraft:stripped_" + log(w) + "[axis=" + (alongX(s) ? "x" : "z") + "]"));
        else c.set(x, g - 1, z, STONE_BRICK_SLAB_TOP);
    }

    private int span(int s) {
        int n = 0;
        for (int i = s; i >= 0 && (fl[i] & RoadPlanner.BRIDGE) != 0; i--) n++;
        for (int i = s + 1; i < fl.length && (fl[i] & RoadPlanner.BRIDGE) != 0; i++) n++;
        return n;
    }

    private boolean alongX(int s) {
        return Math.abs(xs[s + 1] - xs[s]) >= Math.abs(zs[s + 1] - zs[s]);
    }

    static String log(String wood) {
        return wood.equals("crimson") || wood.equals("warped") ? wood + "_stem" : wood + "_log";
    }

    /** A boardwalk over the mire's shallows: planks on posts every few blocks at its edges, no rails. */
    private void boardwalk(SettlementPaint.Canvas c, int x, int z, int g, float d, int s, int family) {
        String w = wood(family == VillageStyles.MIRE ? family : VillageStyles.MIRE);
        c.set(x, g, z, VillageBlocks.of("minecraft:" + w + "_planks"));
        c.column(x, g + 1, g + 4, z, AIR);
        if (d > hw - 1.0f && Math.floorMod(s, 2) == 0) c.column(x, g - 6, g - 1, z, VillageBlocks.of("minecraft:" + log(w) + "[axis=y]"));
    }

    // ---- lanterns -------------------------------------------------------------------------------------------------------

    /** A lantern post beside point s, by turns either side: a stone foot, a timber post, the lantern on top. */
    private void lamp(SettlementPaint.Canvas c, int s) {
        int side = Math.floorMod(s / 4, 2) == 0 ? 1 : -1;
        int px = (int) Math.floor(xs[s] - ox + -dz(s) * side * (hw + 1.6)), pz = (int) Math.floor(zs[s] - oz + dx(s) * side * (hw + 1.6));
        if (!c.contains(px, pz)) return;
        int k = cell(px, pz);
        int g = k >= 0 && kinds[k] != CityPlan.VOID ? lifts[k] : ys[s] - baseY;
        String w = wood(fam[s]);
        c.set(px, g + 1, pz, MOSSY_COBBLE_WALL);
        c.column(px, g + 2, g + 3, pz, VillageBlocks.of("minecraft:" + w + "_fence"));
        c.set(px, g + 4, pz, LANTERN);
    }

    float dx(int s) {
        int j = Math.min(xs.length - 1, s + 1), i = Math.max(0, j - 1);
        float ddx = xs[j] - xs[i], ddz = zs[j] - zs[i];
        return ddx / (float) Math.max(1e-3, Math.hypot(ddx, ddz));
    }

    float dz(int s) {
        int j = Math.min(xs.length - 1, s + 1), i = Math.max(0, j - 1);
        float ddx = xs[j] - xs[i], ddz = zs[j] - zs[i];
        return ddz / (float) Math.max(1e-3, Math.hypot(ddx, ddz));
    }

    // ---- blocks -------------------------------------------------------------------------------------------------------

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState DIRT = VillageBlocks.of("minecraft:dirt"), GRASS = VillageBlocks.of("minecraft:grass_block"),
            COARSE_DIRT = VillageBlocks.of("minecraft:coarse_dirt"), PODZOL = VillageBlocks.of("minecraft:podzol"), PATH = VillageBlocks.of("minecraft:dirt_path"),
            GRAVEL = VillageBlocks.of("minecraft:gravel"), SAND = VillageBlocks.of("minecraft:sand"), SANDSTONE = VillageBlocks.of("minecraft:sandstone"),
            SMOOTH_SANDSTONE = VillageBlocks.of("minecraft:smooth_sandstone"), COBBLE = VillageBlocks.of("minecraft:cobblestone"),
            MOSSY_COBBLE = VillageBlocks.of("minecraft:mossy_cobblestone"), STONE = VillageBlocks.of("minecraft:stone"),
            STONE_BRICKS = VillageBlocks.of("minecraft:stone_bricks"), MOSSY_STONE_BRICKS = VillageBlocks.of("minecraft:mossy_stone_bricks"),
            CRACKED_STONE_BRICKS = VillageBlocks.of("minecraft:cracked_stone_bricks"), ANDESITE = VillageBlocks.of("minecraft:andesite"),
            MUD = VillageBlocks.of("minecraft:mud"), PACKED_MUD = VillageBlocks.of("minecraft:packed_mud"),
            SHORT_GRASS = VillageBlocks.of("minecraft:short_grass"),
            COBBLE_SLAB = VillageBlocks.of("minecraft:cobblestone_slab[type=bottom]"), MOSSY_COBBLE_SLAB = VillageBlocks.of("minecraft:mossy_cobblestone_slab[type=bottom]"),
            STONE_BRICK_SLAB = VillageBlocks.of("minecraft:stone_brick_slab[type=bottom]"), STONE_BRICK_SLAB_TOP = VillageBlocks.of("minecraft:stone_brick_slab[type=top]"),
            ANDESITE_SLAB = VillageBlocks.of("minecraft:andesite_slab[type=bottom]"), STONE_SLAB = VillageBlocks.of("minecraft:stone_slab[type=bottom]"),
            SANDSTONE_SLAB = VillageBlocks.of("minecraft:sandstone_slab[type=bottom]"), MUD_BRICK_SLAB = VillageBlocks.of("minecraft:mud_brick_slab[type=bottom]"),
            WALL_X = VillageBlocks.connected("minecraft:stone_brick_wall", true)[10], WALL_Z = VillageBlocks.connected("minecraft:stone_brick_wall", true)[5],
            MOSSY_COBBLE_WALL = VillageBlocks.of("minecraft:mossy_cobblestone_wall[up=true]"),
            LANTERN = VillageBlocks.of("minecraft:lantern[hanging=false]");
    static final BlockState[] FLOWER = {VillageBlocks.of("minecraft:dandelion"), VillageBlocks.of("minecraft:poppy"), VillageBlocks.of("minecraft:oxeye_daisy"),
            VillageBlocks.of("minecraft:cornflower"), VillageBlocks.of("minecraft:azure_bluet")};
}
