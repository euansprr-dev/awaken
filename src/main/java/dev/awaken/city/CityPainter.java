package dev.awaken.city;

import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The per-chunk painter (the GatePainter + Sink pattern, SPEC §16.2). Pure: the same layout, area and seed always give
 * the same buffer, so it runs on worldgen threads and in commands alike, and a chunk painted alone equals the same
 * chunk painted beside its neighbours. The ground comes from the plan's raster column by column; then every piece
 * whose box touches the area paints its share (plots by the grammar, landmark parts, trees, lamps).
 *
 * <p>The world seed only varies details (weathering, flowers, shutters); shapes come from the plan alone.
 */
public final class CityPainter {
    private CityPainter() {}

    /** River floor and waterline, from the street: terrain carves the channel to y 57 and fills it to y 63. */
    static final int RIVER_FLOOR = -13, WATERLINE = -7, CANAL_FLOOR = -11;
    /** Everything the city clears above its ground reaches this high (trees, hills in a world without a basin). */
    static final int CLEAR_TOP = CityBuffer.YMAX;

    /** Paints the city's share of chunk (chunkX, chunkZ). */
    static void paintChunk(CityLayout layout, int chunkX, int chunkZ, long worldSeed, CityBuffer buf) {
        buf.reset((chunkX << 4) - layout.cx, (chunkZ << 4) - layout.cz);
        paint(layout, worldSeed, buf, layout.pieces(chunkX, chunkZ));
    }

    /** Paints any area (its plan-space corner is set on the buffer); the pieces are those touching it, in order. */
    static void paint(CityLayout layout, long worldSeed, CityBuffer buf, int[] pieces) {
        Ctx c = new Ctx(layout, worldSeed);
        for (int lz = 0; lz < buf.d; lz++) {
            for (int lx = 0; lx < buf.w; lx++) ground(c, buf, buf.px0 + lx, buf.pz0 + lz);
        }
        CityWall.approaches(c, buf); // the High Gate's yard, its pennant lines and the barbican (after the ground)
        CityFarmsteads.paint(c, buf); // the farms among the fields (the Commons step)
        CityPlan plan = layout.plan;
        for (int id : pieces) {
            if (id < layout.nPlots) {
                CityGrammar.plot(c, buf, plan.plots.get(id));
            } else if ((id -= layout.nPlots) < layout.nParts) {
                CityLandmarks.part(c, buf, plan.parts.get(id));
            } else if ((id -= layout.nParts) < layout.nTrees) {
                tree(c, buf, plan.trees.get(id));
            } else if ((id -= layout.nTrees) < layout.nLamps) {
                lamp(c, buf, plan.lamps.get(id));
            } else {
                CityProps.prop(c, buf, plan.props.get(id - layout.nLamps));
            }
        }
    }

    /** What every painter call shares: the layout and the seed's detail hashes. */
    static final class Ctx {
        final CityLayout layout;
        final CityPlan plan;
        final long seed;
        /** Scratch for the commons' painters (a road's frame, a field's cell), one set per paint call. */
        final double[] rd = new double[2], rd2 = new double[2], fc = new double[9], fc2 = new double[9];

        Ctx(CityLayout layout, long worldSeed) {
            this.layout = layout;
            this.plan = layout.plan;
            this.seed = worldSeed ^ 0x5EEDC17EL;
        }

        /** 0..99 for a cell and a salt (world-seeded detail). */
        int pct(int x, int z, int salt) {
            return (int) Math.floorMod(hash(seed, x * 31 + z * 1_000_003, salt), 100L);
        }

        /** 0..99 from the plan alone (shape decisions that must not vary by world). */
        static int shape(int a, int b, int salt) {
            return (int) Math.floorMod(hash(0xCA21110AL, a * 31 + b * 1_000_003, salt), 100L);
        }
    }

    static long hash(long seed, int a, int b) {
        long z = seed ^ (a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    // ---- ground ---------------------------------------------------------------------------------------------

    static void ground(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        byte k = p.kind(x, z);
        switch (k) {
            case VOID -> outside(c, b, x, z);
            case WALL -> CityWall.curtain(c, b, x, z);
            case WATERGATE -> {
                waterColumn(c, b, x, z, RIVER_FLOOR);
                CityWall.waterGate(c, b, x, z);
            }
            case RIVER -> waterColumn(c, b, x, z, RIVER_FLOOR);
            case CANAL, RACE -> waterColumn(c, b, x, z, CANAL_FLOOR);
            case LOWQUAY -> lowQuay(c, b, x, z);
            default -> land(c, b, x, z, k);
        }
    }

    static void waterColumn(Ctx c, CityBuffer b, int x, int z, int floor) {
        b.column(x, CityBuffer.YMIN, floor - 1, z, STONE);
        b.set(x, floor, z, c.pct(x, z, 3) < 30 ? GRAVEL : STONE);
        b.column(x, floor + 1, WATERLINE, z, WATER);
        b.column(x, WATERLINE + 1, CLEAR_TOP, z, AIR);
        // a ladder out of the river onto the lower quay, here and there
        int side = ladderSide(c, x, z, LOWQUAY, 7);
        if (side >= 0) {
            b.set(x, WATERLINE, z, LADDER_WET[side]);
            b.column(x, WATERLINE + 1, LOW_QUAY, z, LADDER[side]);
        }
    }

    static final BlockState[] MOORING = dirs("awaken:mooring_ring[facing=%s]");

    /** The lower quay along the river (a bas-port): three blocks above the water, four below the street. */
    static final int LOW_QUAY = -4;

    static void lowQuay(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        b.plate(x, z);
        boolean face = nextToWater(c.plan, x, z);
        for (int ry = RIVER_FLOOR; ry < LOW_QUAY; ry++) {
            b.set(x, ry, z, face ? (ry >= WATERLINE - 1 ? MOSSY_STONE_BRICKS : STONE_BRICKS) : STONE);
        }
        b.set(x, LOW_QUAY, z, face ? POLISHED_ANDESITE : (c.pct(x, z, 5) < 20 ? CRACKED_STONE_BRICKS : STONE_BRICKS));
        b.column(x, LOW_QUAY + 1, CLEAR_TOP, z, AIR);
        if (face && c.pct(x, z, 9) < 5) b.set(x, LOW_QUAY + 1, z, WALL_POST); // mooring bollards
        // ladders up the quay wall to the street, here and there
        int side = ladderSide(c, x, z, QUAY, 11);
        if (side >= 0) b.column(x, LOW_QUAY + 1, 0, z, LADDER[side]);
        else {
            // (M4 B3a) iron mooring rings on the quay wall every nine, waist-high over the lower quay (bible §5.2)
            for (int d = 0; d < 4; d++) {
                if (p.kind(x + NB[d][0], z + NB[d][1]) != QUAY) continue;
                if (Math.floorMod(NB[d][0] == 0 ? x : z, 9) == 4) b.set(x, LOW_QUAY + 2, z, MOORING[(d + 2) & 3]);
                break;
            }
        }
    }

    /**
     * Whether this cell carries a ladder up the wall of a neighbour of the given kind: about one cell in 25 along the
     * wall, never two side by side. Returns the side the ladder faces (away from that wall), or -1.
     */
    static int ladderSide(Ctx c, int x, int z, byte wallKind, int salt) {
        CityPlan p = c.plan;
        for (int d = 0; d < 4; d++) {
            int[] o = NB[d];
            if (p.kind(x + o[0], z + o[1]) != wallKind) continue;
            int along = (o[0] == 0 ? x : z);
            if (Math.floorMod(along * 7 + Ctx.shape(o[0] == 0 ? z : x, 0, salt), 29) != 0) return -1;
            return (d + 2) & 3;
        }
        return -1;
    }

    static boolean nextToWater(CityPlan p, int x, int z) {
        return water(p.kind(x + 1, z)) || water(p.kind(x - 1, z)) || water(p.kind(x, z + 1)) || water(p.kind(x, z - 1));
    }

    static void land(Ctx c, CityBuffer b, int x, int z, byte k) {
        CityPlan p = c.plan;
        int lift = p.lift(x, z);
        int dist = p.district(x, z);
        b.plate(x, z);
        boolean quay = k == QUAY;
        int bottom = quay ? RIVER_FLOOR : Math.min(-4, lift - 1);
        boolean face = quay && nextToWater(p, x, z);
        for (int ry = bottom; ry < 0; ry++) {
            BlockState s = STONE;
            if (face) s = ry >= WATERLINE - 1 && ry <= WATERLINE + 1 ? MOSSY_STONE_BRICKS : (c.pct(x, z, 50 + ry) < 12 ? CRACKED_STONE_BRICKS : STONE_BRICKS);
            else if (ry == -1 && (k == GARDEN || k == YARD && dist == GARDEN_D)) s = DIRT;
            b.set(x, ry, z, s);
        }
        // raised ground (look bible §6): a retaining wall where it stands two or more over a neighbour, a parapet where
        // the fall would hurt (6+), a coping where it is a step (2-4)
        if (lift > 0) {
            int low = lowest(p, x, z, lift);
            boolean edge = low <= lift - 2;
            for (int ry = 0; ry < lift; ry++) b.set(x, ry, z, edge ? retaining(c, x, ry, z, low, dist) : STONE);
        }
        BlockState top = switch (k) {
            case AVENUE -> avenue(c, x, z);
            case STREET, RING -> street(c, x, z);
            case LANE -> lane(c, x, z);
            case ALLEY -> alley(c, x, z);
            case SQUARE -> square(c, x, z);
            case PLAZA, LANDMARK -> plaza(c, x, z, dist);
            case QUAY -> face ? POLISHED_ANDESITE : (c.pct(x, z, 4) < 15 ? CRACKED_STONE_BRICKS : STONE_BRICKS);
            case STAIR -> stairStone(dist);
            case TRAINING -> training(c, x, z);
            case GARDEN -> GRASS;
            case YARD -> yard(c, x, z, dist);
            case PLOT -> FLOOR;
            default -> COBBLE;
        };
        if (k == STREET || k == RING || k == LANE || k == ALLEY) top = wear(c, x, z, k, top);
        b.set(x, lift, z, top);
        b.column(x, lift + 1, CLEAR_TOP, z, AIR);
        if (railAt(p, x, z)) b.set(x, lift + 1, z, (dist == TERRACE ? DIORITE_WALLS : STONE_WALLS)[railMask(p, x, z)]);
        else if (copingAt(p, x, z)) b.set(x, lift + 1, z, COPING);
        if (k == STAIR) stair(c, b, x, z, lift);
        else if (k == GARDEN) gardenCell(c, b, x, z, lift);
        else if (k == YARD) yardCell(c, b, x, z, lift, dist);
        else if (k == QUAY && face && c.pct(x, z, 9) < 6) b.set(x, 1, z, WALL_POST); // bollards
        if (open(k) && k != STAIR) wallFoot(c, b, x, z, lift);
        if (k == RING) CityWall.innerBand(c, b, x, z);
    }

    /** Open ground: what a retaining wall's foot can stand on (streets, squares, yards, gardens). */
    static boolean open(byte k) {
        return (walkable(k) && k != LOWQUAY) || k == YARD || k == GARDEN || k == FREE;
    }

    /** The lowest neighbour's ground (any kind but water and the wall), or this cell's own if none is lower. */
    static int lowest(CityPlan p, int x, int z, int lift) {
        int low = lift;
        for (int[] d : NB) {
            byte k = p.kind(x + d[0], z + d[1]);
            if (k == VOID || k == WALL || water(k) || k == LOWQUAY) continue;
            low = Math.min(low, p.lift(x + d[0], z + d[1]));
        }
        return low;
    }

    /** The fall from open ground here to open ground beside it (0 when nothing lower is open). */
    static int fall(CityPlan p, int x, int z) {
        int lift = p.lift(x, z), f = 0;
        for (int[] d : NB) {
            byte n = p.kind(x + d[0], z + d[1]);
            if (!open(n)) continue;
            f = Math.max(f, lift - p.lift(x + d[0], z + d[1]));
        }
        return f;
    }

    /** Whether a raised cell is open ground at an edge (not a step, a house or a landmark, and none of those below). */
    private static boolean edgeGround(CityPlan p, int x, int z) {
        byte k = p.kind(x, z);
        if (!open(k) || k == STAIR || p.lift(x, z) <= 0) return false;
        int lift = p.lift(x, z);
        for (int[] d : NB) {
            int nx = x + d[0], nz = z + d[1];
            if (p.lift(nx, nz) < lift - 1) {
                byte n = p.kind(nx, nz);
                if (n == STAIR || n == PLOT || n == LANDMARK) return false;
            }
        }
        return true;
    }

    static final int[][] NB = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** Whether a raised cell carries a parapet: an edge over a fall of 6+ (a "no"), open where a drainpipe arrives. */
    static boolean railAt(CityPlan p, int x, int z) {
        if (!edgeGround(p, x, z) || fall(p, x, z) < RAIL_FALL) return false;
        for (int[] d : NB) {
            int nx = x + d[0], nz = z + d[1];
            if (p.lift(nx, nz) > p.lift(x, z) - 2) continue;
            for (int s = -1; s <= 1; s++) {
                int ax = nx + (d[0] == 0 ? s : 0), az = nz + (d[1] == 0 ? s : 0);
                if (wallPipe(p, ax, az) >= 0) return false;
            }
        }
        return true;
    }

    /** A coping slab on an edge over a short fall (2-5): a step to catch, not a "no". */
    static boolean copingAt(CityPlan p, int x, int z) {
        byte k = p.kind(x, z);
        if (k != GARDEN && k != YARD && k != FREE || !edgeGround(p, x, z)) return false;
        int f = fall(p, x, z);
        return f >= 2 && f < RAIL_FALL;
    }

    static final int RAIL_FALL = 6;

    static int railMask(CityPlan p, int x, int z) {
        int m = 0;
        for (int d = 0; d < 4; d++) if (railAt(p, x + NB[d][0], z + NB[d][1])) m |= 1 << d;
        return m;
    }

    /** A retaining wall's face: ashlar courses with weathering; the Terrace's banded in andesite at the lip rows. */
    static BlockState retaining(Ctx c, int x, int ry, int z, int low, int dist) {
        int r = ry - low, h = c.pct(x, z, 70 + ry);
        return switch (dist) {
            case TERRACE -> r == 3 || r == 7 || r == 11 ? POLISHED_ANDESITE : h < 10 ? MOSSY_STONE_BRICKS : h < 18 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
            case BELLWATCH -> h < 30 ? COBBLE : h < 45 ? MOSSY_COBBLE : h < 55 ? TUFF_BRICKS : STONE_BRICKS;
            case GARDEN_D -> h < 35 ? MOSSY_STONE_BRICKS : h < 45 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
            default -> ry == 0 ? POLISHED_DIORITE : h < 12 ? MOSSY_STONE_BRICKS : h < 20 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
        };
    }

    /**
     * The wall a cell stands at the foot of: the side (0-3) whose ground stands 4+ higher (the first found), or -1.
     */
    static int wallSide(CityPlan p, int x, int z) {
        int lift = p.lift(x, z);
        for (int d = 0; d < 4; d++) {
            int nx = x + NB[d][0], nz = z + NB[d][1];
            byte n = p.kind(nx, nz);
            if (n == WALL || n == VOID || water(n) || n == PLOT || n == LANDMARK) continue;
            if (p.lift(nx, nz) >= lift + 4) return d;
        }
        return -1;
    }

    /**
     * Whether this cell at a tall retaining wall's foot (6+) carries a drainpipe up it: every 12 along the wall (a climb
     * line, bible §3.9), never at an inside corner. Returns the wall's side, or -1.
     */
    static int wallPipe(CityPlan p, int x, int z) {
        if (!open(p.kind(x, z)) || p.kind(x, z) == STAIR) return -1;
        int d = wallSide(p, x, z);
        if (d < 0) return -1;
        if (p.lift(x + NB[d][0], z + NB[d][1]) < p.lift(x, z) + RAIL_FALL) return -1;
        int along = NB[d][0] == 0 ? x : z;
        if (Math.floorMod(along, 12) != 6) return -1;
        int l = (d + 3) & 3, r = (d + 1) & 3;
        if (p.lift(x + NB[l][0], z + NB[l][1]) > p.lift(x, z) + 1 || p.lift(x + NB[r][0], z + NB[r][1]) > p.lift(x, z) + 1) return -1;
        return d;
    }

    /**
     * The foot of a retaining wall 4+ high: rustication lips in rows 3, 7 and 11 (shimmy rails, bible §5.4) and every 12
     * a drainpipe from the ground to the top (the climb line; the parapet opens for it).
     */
    static void wallFoot(Ctx c, CityBuffer b, int x, int z, int lift) {
        CityPlan p = c.plan;
        int d = wallSide(p, x, z);
        if (d < 0) return;
        int top = p.lift(x + NB[d][0], z + NB[d][1]);
        if (wallPipe(p, x, z) >= 0) {
            int out = (d + 2) & 3;
            for (int ry = lift + 1; ry <= top; ry++) b.set(x, ry, z, ry == lift + 1 ? PIPE_SHOE[out] : ry == top ? PIPE_HEAD[out] : PIPE[out]);
            return;
        }
        Course band = p.district(x + NB[d][0], z + NB[d][1]) == TERRACE ? C_ANDESITE : C_STONE;
        for (int r = 3; lift + r < top; r += 4) b.set(x, lift + r, z, band.straight()[d]);
    }

    static BlockState stairStone(int dist) {
        return dist == TERRACE ? POLISHED_DIORITE : STONE_BRICKS;
    }

    /**
     * A step. The Terrace's named flights: a stair facing up the flight, posts at the sides. Every other step (a lane's
     * flight, the bowl's rings, a stitched landing): a stair facing up the slope, turning its corners (inner where two
     * sides are higher, outer at a landing's corner), in its district's stone; the bowl's in polished andesite (its ring
     * lines are its steps).
     */
    static void stair(Ctx c, CityBuffer b, int x, int z, int lift) {
        CityPlan p = c.plan;
        for (CityPlan.Stair s : p.stairs) {
            if (x < s.x0() || x > s.x1() || z < s.z0() || z > s.z1()) continue;
            int up = (s.down() + 2) & 3;
            boolean side = (s.down() == N || s.down() == S) ? (x == s.x0() || x == s.x1()) : (z == s.z0() || z == s.z1());
            BlockState[] stairs = p.district(x, z) == TERRACE || lift > 0 ? DIORITE_STAIR : STONE_STAIR;
            b.set(x, lift, z, side ? POLISHED_DIORITE : stairs[up]);
            if (side) b.set(x, lift + 1, z, DIORITE_WALLS[s.down() == N || s.down() == S ? 5 : 10]);
            return;
        }
        int hi = 0, lo = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + NB[d][0], nz = z + NB[d][1];
            byte n = p.kind(nx, nz);
            if (!walkable(n) || n == LOWQUAY) continue;
            int nl = p.lift(nx, nz);
            if (nl > lift) hi |= 1 << d;
            else if (nl < lift) lo |= 1 << d;
        }
        Step st = stepOf(p, x, z);
        int ups = hi != 0 ? hi : ((lo << 2 | lo >> 2) & 15);
        b.set(x, lift, z, turn(st, ups, hi != 0));
    }

    /** The stair for a set of up-sides: straight toward one, or turning the corner toward two beside each other. */
    static BlockState turn(Step st, int ups, boolean inner) {
        int first = -1;
        for (int d = 0; d < 4; d++) {
            if ((ups & (1 << d)) == 0) continue;
            if (first < 0) first = d;
            int l = (d + 3) & 3, r = (d + 1) & 3;
            boolean hasL = (ups & (1 << l)) != 0, hasR = (ups & (1 << r)) != 0;
            if (Integer.bitCount(ups) == 2 && hasR) return inner ? st.innerRight()[d] : st.outerRight()[d];
            if (Integer.bitCount(ups) == 2 && hasL && d == 0) return inner ? st.innerLeft()[d] : st.outerLeft()[d];
        }
        return st.straight()[first < 0 ? N : first];
    }

    static Step stepOf(CityPlan p, int x, int z) {
        int dx = x - p.squareX, dz = z - p.squareZ;
        if (dx * dx + dz * dz < p.squareR * p.squareR) return STEP_ANDESITE;
        return switch (p.district(x, z)) {
            case TERRACE -> STEP_DIORITE;
            case GARDEN_D -> STEP_MOSSY;
            case WALLRING -> STEP_COBBLE;
            case WORKS -> STEP_BRICK;
            default -> STEP_STONE;
        };
    }

    // ---- paving ---------------------------------------------------------------------------------------------

    static boolean border(CityPlan p, int x, int z, byte k) {
        return p.kind(x + 1, z) != k || p.kind(x - 1, z) != k || p.kind(x, z + 1) != k || p.kind(x, z - 1) != k;
    }

    /**
     * Paving wear by street rank (look bible §7, M4 B3a): a gutter of dressed stone along the foot of the facades on the
     * streets, damp moss at the walls of the lanes and alleys, and a worn line down the middle where wheels and feet go.
     */
    static BlockState wear(Ctx c, int x, int z, byte k, BlockState base) {
        CityPlan p = c.plan;
        int h = c.pct(x, z, 540);
        for (int[] d : NB) {
            byte n = p.kind(x + d[0], z + d[1]);
            if (n != PLOT && n != LANDMARK) continue;
            return switch (k) {
                case STREET, RING -> h < 60 ? POLISHED_ANDESITE : h < 78 ? MOSSY_STONE_BRICKS : base;
                default -> h < 45 ? MOSSY_COBBLE : base;
            };
        }
        if (k != ALLEY && middle(p, x, z, k)) return h < 65 ? ANDESITE : base;
        return base;
    }

    /** Whether a street cell lies on its street's middle line (equally far from both sides, across its narrow way). */
    static boolean middle(CityPlan p, int x, int z, byte k) {
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1, a = 0, b = 0;
            while (a < 7 && p.kind(x - dx * (a + 1), z - dz * (a + 1)) == k) a++;
            while (b < 7 && p.kind(x + dx * (b + 1), z + dz * (b + 1)) == k) b++;
            if (a + b + 1 <= 9 && Math.abs(a - b) <= (a + b + 1 > 4 ? 1 : 0)) return true;
        }
        return false;
    }

    static BlockState avenue(Ctx c, int x, int z) {
        if (border(c.plan, x, z, AVENUE)) return POLISHED_ANDESITE;
        int h = c.pct(x, z, 11);
        return h < 8 ? CRACKED_STONE_BRICKS : h < 14 ? ANDESITE : STONE_BRICKS;
    }

    static BlockState street(Ctx c, int x, int z) {
        int h = c.pct(x, z, 12);
        return h < 50 ? STONE_BRICKS : h < 72 ? ANDESITE : h < 92 ? COBBLE : CRACKED_STONE_BRICKS;
    }

    static BlockState lane(Ctx c, int x, int z) {
        int h = c.pct(x, z, 13);
        return h < 58 ? COBBLE : h < 74 ? MOSSY_COBBLE : h < 88 ? ANDESITE : GRAVEL;
    }

    static BlockState alley(Ctx c, int x, int z) {
        int h = c.pct(x, z, 14);
        return h < 40 ? GRAVEL : h < 60 ? COARSE_DIRT : COBBLE;
    }

    /** Choir Square: rings of pale stone round a bronze seal at the centre, spokes on the eight winds. */
    static BlockState square(Ctx c, int x, int z) {
        int dx = x - c.plan.squareX, dz = z - c.plan.squareZ;
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r < 2.5) return BRONZE;
        if (r < 4) return CHISELED_STONE_BRICKS;
        if (r < 5.5) return POLISHED_ANDESITE;
        double a = Math.toDegrees(Math.atan2(dz, dx));
        double spoke = Math.abs(((a % 45) + 45) % 45 - 22.5);
        if (spoke > 21.0 && r < 30) return POLISHED_ANDESITE;
        int ring = (int) (r / 3.0);
        if (r > 33.5) return POLISHED_ANDESITE;
        return switch (ring % 3) {
            case 0 -> POLISHED_DIORITE;
            case 1 -> CALCITE;
            default -> c.pct(x, z, 15) < 20 ? DIORITE : POLISHED_DIORITE;
        };
    }

    static BlockState plaza(Ctx c, int x, int z, int dist) {
        if (dist == TERRACE) return ((x & 3) == 0 || (z & 3) == 0) ? POLISHED_ANDESITE : POLISHED_DIORITE;
        if (((x & 3) == 0) && ((z & 3) == 0)) return POLISHED_ANDESITE;
        return c.pct(x, z, 16) < 12 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
    }

    static BlockState training(Ctx c, int x, int z) {
        int h = c.pct(x, z, 17);
        return h < 50 ? COARSE_DIRT : h < 80 ? PACKED_MUD : PATH;
    }

    static BlockState yard(Ctx c, int x, int z, int dist) {
        int h = c.pct(x, z, 18);
        return switch (dist) {
            case BELLWATCH -> h < 45 ? COBBLE : h < 65 ? MOSSY_COBBLE : h < 85 ? COARSE_DIRT : GRASS;
            case QUAY_D -> h < 50 ? GRAVEL : h < 80 ? COBBLE : COARSE_DIRT;
            case MARKET -> h < 70 ? STONE_BRICKS : h < 90 ? POLISHED_ANDESITE : GRASS;
            case TERRACE -> h < 80 ? GRASS : PATH;
            case WORKS -> h < 40 ? GRAVEL : h < 70 ? COARSE_DIRT : h < 90 ? PACKED_MUD : COBBLE;
            case GARDEN_D -> GRASS;
            default -> h < 60 ? GRASS : COARSE_DIRT;
        };
    }

    static void gardenCell(Ctx c, CityBuffer b, int x, int z, int lift) {
        CityPlan p = c.plan;
        // hedges where a garden meets a street: dark green outlines from the sky
        for (int[] d : NB) {
            byte n = p.kind(x + d[0], z + d[1]);
            if (n == AVENUE || n == STREET || n == LANE || n == RING || n == PLAZA) {
                b.set(x, lift + 1, z, AZALEA_LEAVES);
                return;
            }
        }
        int h = c.pct(x, z, 19);
        if (h < 6) b.set(x, lift + 1, z, BLOOMS[c.pct(x, z, 20) % BLOOMS.length]);
        else if (h < 18) b.set(x, lift + 1, z, SHORT_GRASS);
    }

    static void yardCell(Ctx c, CityBuffer b, int x, int z, int lift, int dist) {
        if (dist == TERRACE && c.pct(x, z, 21) < 5) b.set(x, lift + 1, z, BLOOMS[c.pct(x, z, 22) % BLOOMS.length]);
        else if (dist == QUAY_D && c.pct(x, z, 23) < 4) b.set(x, lift + 1, z, HAY);
    }

    // ---- the wall -------------------------------------------------------------------------------------------

    static BlockState wallStone(Ctx c, int x, int ry, int z) {
        int h = c.pct(x, z, 100 + ry);
        if (ry % 8 == 0) return POLISHED_ANDESITE;   // string courses every 8
        return h < 10 ? MOSSY_STONE_BRICKS : h < 18 ? CRACKED_STONE_BRICKS : h < 24 ? ANDESITE : STONE_BRICKS;
    }

    // ---- outside the wall -----------------------------------------------------------------------------------

    static void outside(Ctx c, CityBuffer b, int x, int z) {
        CityLayout l = c.layout;
        CityPlan p = c.plan;
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r > l.reach) return;
        double rd = riverDistance(p, x, z);
        if (rd < 13) return; // terrain's river
        boolean apron = r < p.fieldsFrom;
        if (!l.carved && !apron) return;
        b.plate(x, z);
        b.column(x, -4, -1, z, DIRT);
        // all the way up: a tree's crown above 40 would hang over the cleared trunk
        b.column(x, 1, CLEAR_TOP, z, AIR);
        CityCommons.ground(c, b, x, z, r, apron); // the roads, the fields, the farmsteads' ground (the Commons step)
    }

    static double riverDistance(CityPlan p, int x, int z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < p.river.size(); i++) {
            float[] a = p.river.get(i), q = p.river.get(i + 1);
            best = Math.min(best, segDist(x + 0.5, z + 0.5, a[0], a[1], q[0], q[1]));
        }
        // the river continues straight past both ends of the core polyline
        float[] a0 = p.river.get(0), a1 = p.river.get(1);
        best = Math.min(best, segDist(x + 0.5, z + 0.5, a0[0], a0[1], a0[0] + (a0[0] - a1[0]) * 8, a0[1] + (a0[1] - a1[1]) * 8));
        int n = p.river.size();
        float[] b0 = p.river.get(n - 1), b1 = p.river.get(n - 2);
        best = Math.min(best, segDist(x + 0.5, z + 0.5, b0[0], b0[1], b0[0] + (b0[0] - b1[0]) * 8, b0[1] + (b0[1] - b1[1]) * 8));
        return best;
    }

    static double segDist(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, ll = dx * dx + dz * dz;
        double t = ll == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / ll));
        double qx = ax + dx * t - px, qz = az + dz * t - pz;
        return Math.sqrt(qx * qx + qz * qz);
    }

    // ---- trees and lamps ------------------------------------------------------------------------------------

    /** A rounded crown of persistent leaves on a wood trunk (no logs: the vegetation sweep owns those). */
    static void tree(Ctx c, CityBuffer b, int[] t) {
        int x = t[0], z = t[1], kind = t[2], size = t[3];
        int g = c.plan.lift(x, z);
        BlockState trunk = kind == 1 ? BIRCH_TRUNK : kind == 2 ? CHERRY_TRUNK : OAK_TRUNK;
        BlockState leaves = kind == 1 ? BIRCH_LEAVES : kind == 2 ? CHERRY_LEAVES : (Ctx.shape(x, z, 40) < 25 ? AZALEA_LEAVES : OAK_LEAVES);
        if (kind == 3) {
            // avenue trees: clipped, a trunk to 4 and a tight crown
            b.column(x, g + 1, g + 4, z, trunk);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 4; dy <= 6; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + (dy == 6 ? 1 : 0) > 2) continue;
                        if (dx == 0 && dz == 0 && dy < 6) continue;
                        // (only into air: a crown never cuts a facade's courses or a drainpipe)
                        BlockState was = b.get(x + dx, g + dy, z + dz);
                        if (was == null || was.isAir()) b.set(x + dx, g + dy, z + dz, leaves);
                    }
            b.set(x, g + 6, z, leaves);
            return;
        }
        int trunkH = 3 + size + (Ctx.shape(x, z, 41) < 40 ? 1 : 0);
        b.column(x, g + 1, g + trunkH, z, trunk);
        int r = size;
        int cy = g + trunkH;
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++)
                for (int dy = -2; dy <= r; dy++) {
                    double dd = dx * dx + dz * dz + (dy * 1.4) * (dy * 1.4);
                    if (dd > (r + 0.6) * (r + 0.6)) continue;
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    if (dd > r * r - 0.5 && c.pct(x + dx, z + dz, 42 + dy) < 35) continue;
                    if (b.get(x + dx, cy + dy, z + dz) == null || b.get(x + dx, cy + dy, z + dz).isAir())
                        b.set(x + dx, cy + dy, z + dz, leaves);
                }
    }

    static void lamp(Ctx c, CityBuffer b, int[] l) {
        int x = l[0], z = l[1];
        // (never in a gate's passage: the gatehouse hangs its own lantern there; the Walls step)
        for (CityPlan.Part p : c.plan.parts)
            if (p.kind().equals("gate_passage") && x >= p.x0() && x <= p.x1() && z >= p.z0() && z <= p.z1()) return;
        if (CityWall.pipeAt(c.plan, x, z)) return; // (nor in a drainpipe's way up the wall)
        int g = c.plan.lift(x, z);
        b.set(x, g + 1, z, WALL_POST);
        b.column(x, g + 2, g + 3, z, FENCE);
        b.set(x, g + 4, z, LANTERN);
    }
}
