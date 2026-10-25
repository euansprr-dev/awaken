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
        CityPlan plan = layout.plan;
        for (int id : pieces) {
            if (id < layout.nPlots) {
                CityGrammar.plot(c, buf, plan.plots.get(id));
            } else if ((id -= layout.nPlots) < layout.nParts) {
                CityLandmarks.part(c, buf, plan.parts.get(id));
            } else if ((id -= layout.nParts) < layout.nTrees) {
                tree(c, buf, plan.trees.get(id));
            } else {
                lamp(c, buf, plan.lamps.get(id - layout.nTrees));
            }
        }
    }

    /** What every painter call shares: the layout and the seed's detail hashes. */
    static final class Ctx {
        final CityLayout layout;
        final CityPlan plan;
        final long seed;

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
            case WALL -> wall(c, b, x, z);
            case WATERGATE -> {
                waterColumn(c, b, x, z, RIVER_FLOOR);
                // the wall arches over the water: open to ry 9, masonry above, a portcullis hanging on the outer line
                int top = p.wallHeight - 1;
                for (int ry = 10; ry <= top; ry++) b.set(x, ry, z, ry == 10 ? STONE_SLAB_TOP : wallStone(c, x, ry, z));
                b.set(x, top + 1, z, POLISHED_ANDESITE);
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

    /** The lower quay along the river (a bas-port): three blocks above the water, four below the street. */
    static final int LOW_QUAY = -4;

    static void lowQuay(Ctx c, CityBuffer b, int x, int z) {
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
        int bottom = quay ? RIVER_FLOOR : -4;
        boolean face = quay && nextToWater(p, x, z);
        for (int ry = bottom; ry < 0; ry++) {
            BlockState s = STONE;
            if (face) s = ry >= WATERLINE - 1 && ry <= WATERLINE + 1 ? MOSSY_STONE_BRICKS : (c.pct(x, z, 50 + ry) < 12 ? CRACKED_STONE_BRICKS : STONE_BRICKS);
            else if (ry == -1 && (k == GARDEN || k == YARD && dist == GARDEN_D)) s = DIRT;
            b.set(x, ry, z, s);
        }
        // the terrace: retaining walls where lifted ground meets lower ground, a parapet along open edges
        boolean rail = false;
        if (lift > 0) {
            boolean edge = lowerNeighbour(p, x, z, lift);
            for (int ry = 0; ry < lift; ry++) b.set(x, ry, z, edge ? terraceWall(c, x, ry, z) : STONE);
            rail = edge && k != STAIR && k != PLOT && k != LANDMARK && parapet(p, x, z, lift);
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
        b.set(x, lift, z, top);
        b.column(x, lift + 1, CLEAR_TOP, z, AIR);
        if (rail) b.set(x, lift + 1, z, DIORITE_WALLS[railMask(p, x, z)]);
        if (k == STAIR) stair(c, b, x, z, lift);
        else if (k == GARDEN) gardenCell(c, b, x, z, lift);
        else if (k == YARD) yardCell(c, b, x, z, lift, dist);
        else if (k == QUAY && face && c.pct(x, z, 9) < 6) b.set(x, 1, z, WALL_POST); // bollards
    }

    static boolean lowerNeighbour(CityPlan p, int x, int z, int lift) {
        return lowerAt(p, x + 1, z, lift) || lowerAt(p, x - 1, z, lift) || lowerAt(p, x, z + 1, lift) || lowerAt(p, x, z - 1, lift);
    }

    private static boolean lowerAt(CityPlan p, int x, int z, int lift) {
        return p.lift(x, z) < lift - 1 && p.kind(x, z) != STAIR;
    }

    /** A parapet on the terrace's edge where the ground below is open (not where a stair or a house meets it). */
    static boolean parapet(CityPlan p, int x, int z, int lift) {
        for (int[] d : NB) {
            int nx = x + d[0], nz = z + d[1];
            if (p.lift(nx, nz) < lift - 1) {
                byte k = p.kind(nx, nz);
                if (k == STAIR || k == PLOT || k == LANDMARK) return false;
            }
        }
        return true;
    }

    static final int[][] NB = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** Whether a terrace cell carries a parapet (the same rule its neighbours use, so the runs join up). */
    static boolean railAt(CityPlan p, int x, int z) {
        int lift = p.lift(x, z);
        byte k = p.kind(x, z);
        return lift > 0 && k != STAIR && k != PLOT && k != LANDMARK && lowerNeighbour(p, x, z, lift) && parapet(p, x, z, lift);
    }

    static int railMask(CityPlan p, int x, int z) {
        int m = 0;
        for (int d = 0; d < 4; d++) if (railAt(p, x + NB[d][0], z + NB[d][1])) m |= 1 << d;
        return m;
    }

    static BlockState terraceWall(Ctx c, int x, int ry, int z) {
        int h = c.pct(x, z, 70 + ry);
        if (ry == 0) return POLISHED_DIORITE;
        return h < 12 ? MOSSY_STONE_BRICKS : h < 20 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
    }

    static BlockState stairStone(int dist) {
        return dist == TERRACE ? POLISHED_DIORITE : STONE_BRICKS;
    }

    /** A flight cut into the terrace edge: each step a stair block facing up the flight, solid below. */
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
    }

    // ---- paving ---------------------------------------------------------------------------------------------

    static boolean border(CityPlan p, int x, int z, byte k) {
        return p.kind(x + 1, z) != k || p.kind(x - 1, z) != k || p.kind(x, z + 1) != k || p.kind(x, z - 1) != k;
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

    /** The curtain wall: solid 40 high, a walk on top, merlons on the outer edge and a low parapet on the inner. */
    static void wall(Ctx c, CityBuffer b, int x, int z) {
        CityPlan p = c.plan;
        int top = p.wallHeight - 1;
        b.plate(x, z);
        b.column(x, -4, -1, z, STONE);
        for (int ry = 0; ry < top; ry++) b.set(x, ry, z, ry < 3 ? DEEPSLATE_BRICKS : wallStone(c, x, ry, z));
        b.set(x, top, z, c.pct(x, z, 30) < 25 ? STONE_BRICKS : POLISHED_ANDESITE);
        b.column(x, top + 1, CLEAR_TOP, z, AIR);
        boolean outer = false, inner = false;
        for (int[] d : NB) {
            byte n = p.kind(x + d[0], z + d[1]);
            if (n == VOID) outer = true;
            else if (n != WALL && n != WATERGATE && n != LANDMARK) inner = true;
        }
        if (outer) {
            b.set(x, top + 1, z, STONE_BRICKS);
            if (((x + z) & 1) == 0) b.set(x, top + 2, z, STONE_BRICKS);
        } else if (inner) {
            int m = 0;
            for (int d = 0; d < 4; d++) if (innerParapet(p, x + NB[d][0], z + NB[d][1])) m |= 1 << d;
            b.set(x, top + 1, z, STONE_WALLS[m]);
        }
    }

    /** A wall cell on the curtain's inner face (no outside neighbour, an inside one). */
    static boolean innerParapet(CityPlan p, int x, int z) {
        if (p.kind(x, z) != WALL) return false;
        boolean outer = false, inner = false;
        for (int[] d : NB) {
            byte n = p.kind(x + d[0], z + d[1]);
            if (n == VOID) outer = true;
            else if (n != WALL && n != WATERGATE && n != LANDMARK) inner = true;
        }
        return inner && !outer;
    }

    // ---- outside the wall -----------------------------------------------------------------------------------

    static void outside(Ctx c, CityBuffer b, int x, int z) {
        CityLayout l = c.layout;
        CityPlan p = c.plan;
        double r = Math.sqrt((double) x * x + (double) z * z);
        if (r > l.reach) return;
        double rd = riverDistance(p, x, z);
        if (rd < 13) return; // terrain's river
        // the wall's outer foot: a corbelled machicolation line high up, read from the sky as the wall's shadow line
        for (int[] d : NB) {
            if (p.kind(x + d[0], z + d[1]) == WALL) {
                int side = d[0] == 0 ? (d[1] < 0 ? S : N) : (d[0] < 0 ? E : W);
                b.set(x, p.wallHeight - 4, z, STONE_CORBEL[side]);
                break;
            }
        }
        boolean apron = r < p.fieldsFrom;
        if (!l.carved && !apron) return;
        b.plate(x, z);
        b.column(x, -4, -1, z, DIRT);
        // all the way up: a tree's crown above 40 would hang over the cleared trunk
        int clear = CLEAR_TOP;
        if (onRoad(p, x, z)) {
            int h = c.pct(x, z, 31);
            b.set(x, 0, z, h < 70 ? PATH : h < 85 ? GRAVEL : COARSE_DIRT);
            b.column(x, 1, clear, z, AIR);
            return;
        }
        if (apron || r > p.fieldsTo) {
            b.set(x, 0, z, GRASS);
            b.column(x, 1, clear, z, AIR);
            if (c.pct(x, z, 32) < 10) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        // the fields: irregular patches round the city (each column belongs to its nearest seed on a jittered polar
        // lattice), each one crop or fallow, with grass paths and hedges along the boundaries
        b.column(x, 1, clear, z, AIR);
        long cell = fieldCell(p, x, z, r);
        int band = (int) (cell >> 32), sector = (int) cell;
        if (band < 0) {
            b.set(x, 0, z, GRASS);
            if (Ctx.shape(sector, -band, 33) < 35) b.set(x, 1, z, OAK_LEAVES);
            else if (c.pct(x, z, 36) < 25) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        int crop = Ctx.shape(sector, band, 34);
        if (crop < 14) {
            b.set(x, 0, z, GRASS);
            if (c.pct(x, z, 35) < 18) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        b.set(x, 0, z, FARMLAND);
        BlockState s = crop < 42 ? CROPS[0] : crop < 56 ? CROPS[4] : crop < 72 ? CROPS[1] : crop < 87 ? CROPS[2] : CROPS[3];
        b.set(x, 1, z, s);
    }

    static final double FIELD_BAND = 21.0, FIELD_W = 26.0;

    /**
     * The field a column belongs to: (band << 32 | sector) of its nearest seed, or a negative band (-1 - band) when
     * the column lies on the boundary between two fields (a path or a hedge).
     */
    static long fieldCell(CityPlan p, int x, int z, double r) {
        double a = Math.atan2(z, x);
        int b0 = (int) Math.floor((r - p.fieldsFrom) / FIELD_BAND);
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bb = 0, bk = 0;
        for (int band = b0 - 1; band <= b0 + 1; band++) {
            if (band < 0) continue;
            double rb = p.fieldsFrom + (band + 0.5) * FIELD_BAND;
            int n = Math.max(16, (int) (2 * Math.PI * rb / FIELD_W));
            double step = 2 * Math.PI / n;
            int k0 = (int) Math.floor((a + Math.PI) / step);
            for (int k = k0 - 1; k <= k0 + 1; k++) {
                int kk = Math.floorMod(k, n);
                double ja = (Ctx.shape(band, kk, 37) / 100.0 - 0.5) * 0.7, jr = (Ctx.shape(band, kk, 38) / 100.0 - 0.5) * 0.6;
                double sa = -Math.PI + (k + 0.5 + ja) * step, sr = rb + jr * FIELD_BAND;
                double dx = x - Math.cos(sa) * sr, dz = z - Math.sin(sa) * sr;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < best) {
                    second = best;
                    best = d;
                    bb = band;
                    bk = kk;
                } else if (d < second) second = d;
            }
        }
        if (second - best < 1.2) return ((long) (-1 - bb) << 32) | (bk & 0xffffffffL);
        return ((long) bb << 32) | (bk & 0xffffffffL);
    }

    static boolean onRoad(CityPlan p, int x, int z) {
        double half = p.roadWidth / 2.0;
        for (int[] r : p.roads) {
            if (segDist(x + 0.5, z + 0.5, r[0], r[1], r[2], r[3]) <= half) return true;
        }
        return false;
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
                        b.set(x + dx, g + dy, z + dz, leaves);
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
        int g = c.plan.lift(x, z);
        b.set(x, g + 1, z, WALL_POST);
        b.column(x, g + 2, g + 3, z, FENCE);
        b.set(x, g + 4, z, LANTERN);
    }
}
