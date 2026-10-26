package dev.awaken.city;

import dev.awaken.city.CityPainter.Ctx;
import dev.awaken.city.CityPlan.Prop;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * Paints the street-life layer (Milestone 4 step B3a; look bible §7): each prop of plan.json's "props" (written by
 * tools/city/props.py) in its own cells, after the plots, landmarks, trees and lamps, and only into open air above the
 * ground (a drainpipe's shoe or a neighbour's hood is never overwritten), so a chunk painted alone still matches its
 * neighbours. Fountains and wells sink into the paving inside their own boxes.
 *
 * <p>Stalls are painted shut; the citizens open them (counter and wares) when their keepers arrive.
 */
final class CityProps {
    private CityProps() {}

    static final String[] SIDE = {"north", "east", "south", "west"};
    static final String[] GOODS = {"bread", "fish", "produce", "cloth", "pottery", "candles", "flowers", "spice", "tools", "cheese"};
    static final String[] AWNING_COLOURS = {"red", "blue", "yellow", "green", "orange", "plum"};
    static final String[] BLOOM = {"red", "yellow", "blue", "pink", "white", "herbs", "shrub"};
    static final String[] CRATE = {"lid", "apples", "cabbages", "fish", "straw", "bottles"};
    static final String[] PRODUCE = {"apples", "cabbages", "carrots", "pumpkins", "potatoes", "flowers"};
    static final String[] SHELF = {"jars", "cloth", "bread", "pots", "books", "tools"};
    static final String[] FILL = {"grain", "flour", "wool"};

    // ---- states, resolved once (the first paint, when the registries are ready) ------------------------------------

    /** awning[facing][colour][shape: slope upper lower flat] */
    static final BlockState[][][] AWNING_KIT = new BlockState[4][6][4];
    static final BlockState[][] COUNTER = new BlockState[4][2];
    static final BlockState[] WARES = new BlockState[GOODS.length];
    static final BlockState[][] CART_BODY = new BlockState[4][5], CART_SHAFTS = new BlockState[4][5];
    static final BlockState[] HAY_BODY = new BlockState[4], HAY_SHAFTS = new BlockState[4];
    static final BlockState[] BENCH = new BlockState[4], NOTICE = new BlockState[4];
    static final BlockState[] TABLE = new BlockState[2];
    static final BlockState[] CRATES = new BlockState[CRATE.length];
    static final BlockState[][] SACKS = new BlockState[3][3];
    static final BlockState[] CASK = new BlockState[3];
    static final BlockState[] PRODUCE_KIT = new BlockState[PRODUCE.length];
    static final BlockState[][] SHELF_KIT = new BlockState[4][SHELF.length];
    static final BlockState[][] PLANTER = new BlockState[2][BLOOM.length];
    static final BlockState[] NET = new BlockState[2], WINDLASS = new BlockState[2];
    static final BlockState POLE = of("awaken:line_pole[top=false]"), POLE_TOP = of("awaken:line_pole[top=true]");
    /** hang_line[axis][hang: rope laundry bunting lanterns][set][sag] */
    static final BlockState[][][][] LINE = new BlockState[2][4][4][3];
    static final BlockState COAL = of("minecraft:coal_block"), RAW_IRON = of("minecraft:raw_iron_block");
    static final BlockState[] LOGS = {of("minecraft:stripped_oak_log[axis=x]"), of("minecraft:stripped_oak_log[axis=z]"),
            of("minecraft:stripped_spruce_log[axis=x]"), of("minecraft:stripped_spruce_log[axis=z]")};
    static final BlockState GRINDSTONE = of("minecraft:grindstone[face=floor,facing=north]");
    static final BlockState CAULDRON = of("minecraft:water_cauldron[level=3]");
    static final BlockState STONE_BRICK_SLAB = of("minecraft:stone_brick_slab[type=bottom]");
    static final BlockState SPRUCE_SLAB = of("minecraft:spruce_slab[type=bottom]");
    static final BlockState[] SPRUCE_STAIR = dirs("minecraft:spruce_stairs[facing=%s,half=bottom,shape=straight]");

    static {
        String[] shapes = {"slope", "upper", "lower", "flat"};
        String[] hangs = {"rope", "laundry", "bunting", "lanterns"};
        String[] loads = {"empty", "crates", "sacks", "casks", "produce"};
        for (int d = 0; d < 4; d++) {
            for (int c = 0; c < 6; c++)
                for (int s = 0; s < 4; s++)
                    AWNING_KIT[d][c][s] = of("awaken:awning[facing=" + SIDE[d] + ",colour=" + AWNING_COLOURS[c] + ",shape=" + shapes[s] + "]");
            COUNTER[d][0] = of("awaken:stall_counter[facing=" + SIDE[d] + ",open=false]");
            COUNTER[d][1] = of("awaken:stall_counter[facing=" + SIDE[d] + ",open=true]");
            for (int l = 0; l < loads.length; l++) {
                CART_BODY[d][l] = of("awaken:cart[facing=" + SIDE[d] + ",part=body,load=" + loads[l] + "]");
                CART_SHAFTS[d][l] = of("awaken:cart[facing=" + SIDE[d] + ",part=shafts,load=" + loads[l] + "]");
            }
            HAY_BODY[d] = of("awaken:hay_cart[facing=" + SIDE[d] + ",part=body]");
            HAY_SHAFTS[d] = of("awaken:hay_cart[facing=" + SIDE[d] + ",part=shafts]");
            BENCH[d] = of("awaken:bench[facing=" + SIDE[d] + "]");
            NOTICE[d] = of("awaken:notice_board[facing=" + SIDE[d] + "]");
            for (int g = 0; g < SHELF.length; g++) SHELF_KIT[d][g] = of("awaken:goods_shelf[facing=" + SIDE[d] + ",goods=" + SHELF[g] + "]");
        }
        for (int g = 0; g < GOODS.length; g++) WARES[g] = of("awaken:wares[good=" + GOODS[g] + "]");
        TABLE[0] = of("awaken:market_table[axis=x]");
        TABLE[1] = of("awaken:market_table[axis=z]");
        for (int k = 0; k < CRATE.length; k++) CRATES[k] = of("awaken:crate[content=" + CRATE[k] + "]");
        for (int n = 0; n < 3; n++)
            for (int f = 0; f < 3; f++) SACKS[n][f] = of("awaken:sack[count=" + (n + 1) + ",fill=" + FILL[f] + "]");
        CASK[0] = of("awaken:cask[orient=upright]");
        CASK[1] = of("awaken:cask[orient=x]");
        CASK[2] = of("awaken:cask[orient=z]");
        for (int k = 0; k < PRODUCE.length; k++) PRODUCE_KIT[k] = of("awaken:produce[kind=" + PRODUCE[k] + "]");
        for (int b = 0; b < BLOOM.length; b++) {
            PLANTER[0][b] = of("awaken:planter[style=wood,bloom=" + BLOOM[b] + "]");
            PLANTER[1][b] = of("awaken:planter[style=stone,bloom=" + BLOOM[b] + "]");
        }
        NET[0] = of("awaken:fishing_net[axis=x]");
        NET[1] = of("awaken:fishing_net[axis=z]");
        WINDLASS[0] = of("awaken:well_windlass[axis=x]");
        WINDLASS[1] = of("awaken:well_windlass[axis=z]");
        for (int a = 0; a < 2; a++)
            for (int h = 0; h < 4; h++)
                for (int s = 0; s < 4; s++)
                    for (int g = 0; g < 3; g++)
                        LINE[a][h][s][g] = of("awaken:hang_line[axis=" + (a == 0 ? "x" : "z") + ",hang=" + hangs[h] + ",set=" + s + ",sag=" + g + "]");
    }

    static int index(String[] names, String v, int fallback) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(v)) return i;
        return fallback;
    }

    // ---- painting -----------------------------------------------------------------------------------------------------

    /** Sets a cell only where it is open air (another piece's work is never overwritten). */
    static void air(CityBuffer b, int x, int ry, int z, BlockState s) {
        BlockState was = b.get(x, ry, z);
        if (was != null && was.isAir()) b.set(x, ry, z, s);
    }

    static void prop(Ctx c, CityBuffer b, Prop p) {
        if (p.x1() < b.px0 || p.x0() > b.x1() || p.z1() < b.pz0 || p.z0() > b.z1()) return;
        switch (p.kind()) {
            case "stall" -> stall(b, p);
            case "line" -> line(c, b, p);
            case "cart" -> cart(b, p, false);
            case "hay_cart" -> cart(b, p, true);
            case "bench" -> each(b, p, BENCH[Math.max(0, p.side())]);
            case "tavern_table" -> tavernTable(b, p);
            case "stack" -> stack(b, p);
            case "display" -> display(b, p);
            case "planter" -> each(b, p, PLANTER[p.s().equals("stone") ? 1 : 0][Math.floorMod(p.v(), BLOOM.length)]);
            case "notice" -> each(b, p, NOTICE[Math.max(0, p.side())]);
            case "well" -> well(c, b, p);
            case "fountain" -> fountain(c, b, p);
            case "heap" -> heap(c, b, p);
            case "net" -> net(b, p);
            default -> {
            }
        }
    }

    static void each(CityBuffer b, Prop p, BlockState s) {
        for (int x = p.x0(); x <= p.x1(); x++)
            for (int z = p.z0(); z <= p.z1(); z++) air(b, x, p.ry() + 1, z, s);
    }

    /** The stall's front side, its along axis and how far a cell is along its counter (0..3) and back from it (0, 1). */
    static int along(Prop p, int x, int z) {
        int f = p.side();
        return switch (f) {
            case N -> x - p.x0();
            case S -> p.x1() - x;
            case E -> z - p.z0();
            default -> p.z1() - z;
        };
    }

    static int depth(Prop p, int x, int z) {
        return switch (p.side()) {
            case N -> z - p.z0();
            case S -> p.z1() - z;
            case E -> p.x1() - x;
            default -> x - p.x0();
        };
    }

    /**
     * A market stall, 4 along its counter and 2 deep: posts at the corners, the counter in the middle of the front row
     * (painted shut), the keeper's two cells behind it, a canopy over all of it at row 3 that slopes down to the front
     * and to the back (a striped tent).
     */
    static void stall(CityBuffer b, Prop p) {
        int f = p.side(), back = (f + 2) & 3;
        int colour = Math.floorMod(p.v(), 6);
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                int a = along(p, x, z), d = depth(p, x, z);
                boolean post = a == 0 || a == 3;
                if (post) {
                    air(b, x, p.ry() + 1, z, SPRUCE_FENCE);
                    air(b, x, p.ry() + 2, z, SPRUCE_FENCE);
                } else if (d == 0) {
                    air(b, x, p.ry() + 1, z, COUNTER[f][0]);
                }
                air(b, x, p.ry() + 3, z, AWNING_KIT[d == 0 ? f : back][colour][0]);
            }
        }
    }

    /**
     * A hang line: rope (bare, laundry, bunting or lanterns) across the cells at its row, drooping toward the middle;
     * a pole from the ground up to the rope at each end that has one. The world seed picks which laundry hangs.
     */
    static void line(Ctx c, CityBuffer b, Prop p) {
        int axis = p.v() >= 4 ? 1 : 0;
        int set = p.v() & 3;
        int hang = switch (p.s()) {
            case "laundry" -> 1;
            case "bunting" -> 2;
            case "lanterns" -> 3;
            default -> 0;
        };
        int n = axis == 0 ? p.x1() - p.x0() + 1 : p.z1() - p.z0() + 1;
        for (int k = 0; k < n; k++) {
            int x = axis == 0 ? p.x0() + k : p.x0(), z = axis == 0 ? p.z0() : p.z0() + k;
            boolean pole = k == 0 && p.facing().contains("a") || k == n - 1 && p.facing().contains("b");
            if (pole) {
                int g = c.view.lift(x, z);
                for (int ry = g + 1; ry < p.ry(); ry++) air(b, x, ry, z, POLE);
                air(b, x, p.ry(), z, POLE_TOP);
                continue;
            }
            double t = Math.sin(Math.PI * (k + 0.5) / n);
            int sag = n >= 6 ? (t > 0.85 ? 2 : t > 0.45 ? 1 : 0) : n >= 3 && t > 0.8 ? 1 : 0;
            // laundry changes from world to world (decision 3): some days a line is only rope and pegs
            int s = hang == 1 ? Math.floorMod(set + c.pct(x, z, 520), 4) : set;
            air(b, x, p.ry(), z, LINE[axis][hang][s][sag]);
        }
    }

    /** A cart and its shafts: the body at the end away from the way the shafts point. */
    static void cart(CityBuffer b, Prop p, boolean hay) {
        int f = Math.max(0, p.side());
        int load = index(new String[]{"empty", "crates", "sacks", "casks", "produce"}, p.s(), 0);
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                boolean shafts = switch (f) {
                    case N -> z == p.z0();
                    case S -> z == p.z1();
                    case E -> x == p.x1();
                    default -> x == p.x0();
                };
                BlockState s = hay ? (shafts ? HAY_SHAFTS[f] : HAY_BODY[f]) : (shafts ? CART_SHAFTS[f][load] : CART_BODY[f][load]);
                air(b, x, p.ry() + 1, z, s);
            }
        }
    }

    /** A tavern's table outside: two table cells, a bench either side (one row by the wall, one out in the lane). */
    static void tavernTable(CityBuffer b, Prop p) {
        int f = Math.max(0, p.side());
        boolean ns = f == N || f == S;
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                // distance out from the wall along the front's normal: 0 the near bench, 1 the table, 2 the far bench
                int out = switch (f) {
                    case N -> p.z1() - z;
                    case S -> z - p.z0();
                    case E -> x - p.x0();
                    default -> p.x1() - x;
                };
                BlockState s = out == 1 ? TABLE[ns ? 0 : 1] : BENCH[out == 0 ? f : (f + 2) & 3];
                air(b, x, p.ry() + 1, z, s);
            }
        }
    }

    /**
     * Goods stacked by a door: crates, sacks, casks, produce. They stand in the facade's outside plane, so whatever is
     * stacked on top is never a full block (a hold in an even row would break the climbing grid, bible §3.1).
     */
    static void stack(CityBuffer b, Prop p) {
        int x = p.x0(), z = p.z0(), g = p.ry(), v = Math.floorMod(p.v(), 4);
        switch (p.s()) {
            case "crates2" -> {
                air(b, x, g + 1, z, CRATES[v == 0 ? 0 : 4]);
                if (v != 3) air(b, x, g + 2, z, v == 1 ? PRODUCE_KIT[0] : SACKS[v % 2][0]);
            }
            case "sacks" -> air(b, x, g + 1, z, SACKS[v % 3][v == 3 ? 1 : 0]);
            case "crate_sack" -> {
                air(b, x, g + 1, z, CRATES[v % 2 == 0 ? 0 : 4]);
                air(b, x, g + 2, z, SACKS[0][v % 3]);
            }
            case "casks" -> {
                air(b, x, g + 1, z, CASK[0]);
                if (v >= 2) air(b, x, g + 2, z, SACKS[0][1]);
            }
            case "produce" -> air(b, x, g + 1, z, PRODUCE_KIT[v]);
            default -> air(b, x, g + 1, z, CRATES[v == 1 ? 1 : v == 2 ? 2 : 0]);
        }
    }

    /** A Market shop's display under its awning: produce, a crate of goods, a table of wares or a shelf. */
    static void display(CityBuffer b, Prop p) {
        int x = p.x0(), z = p.z0(), g = p.ry(), f = Math.max(0, p.side());
        String[] ks = p.s().split(":");
        String kind = ks[0], good = ks.length > 1 ? ks[1] : "bread";
        int gi = index(GOODS, good, 0), v = Math.floorMod(p.v(), 6);
        switch (kind) {
            case "produce" -> air(b, x, g + 1, z, PRODUCE_KIT[good.equals("flowers") ? 5 : v % 5]);
            case "crate" -> air(b, x, g + 1, z, CRATES[1 + v % 5]);
            case "table" -> {
                air(b, x, g + 1, z, TABLE[f == N || f == S ? 0 : 1]);
                air(b, x, g + 2, z, WARES[gi]);
            }
            default -> air(b, x, g + 1, z, SHELF_KIT[f][index(SHELF, switch (good) {
                case "cloth" -> "cloth";
                case "bread", "cheese" -> "bread";
                case "pottery", "candles" -> "pots";
                case "tools" -> "tools";
                case "spice", "flowers" -> "jars";
                default -> "books";
            }, 0)]);
        }
    }

    /**
     * A well, 3 x 3: a ring of stone a block high round a shaft of water, two posts on the ring carrying the windlass
     * over the water, and a little gable roof of spruce.
     */
    static void well(Ctx c, CityBuffer b, Prop p) {
        int g = p.ry(), cx = p.x0() + 1, cz = p.z0() + 1;
        boolean alongX = (p.v() & 1) == 0;
        BlockState ring = switch (p.v() & 3) {
            case 0, 1 -> STONE_BRICKS;
            case 2 -> COBBLE;
            default -> MOSSY_STONE_BRICKS;
        };
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                if (!b.contains(x, z)) continue;
                if (x == cx && z == cz) {
                    for (int ry = g - 3; ry <= g; ry++) b.set(x, ry, z, WATER);
                    b.set(x, g - 4, z, STONE);
                    air(b, x, g + 3, z, WINDLASS[alongX ? 0 : 1]);
                    air(b, x, g + 4, z, SPRUCE_PLANKS);
                    continue;
                }
                b.set(x, g, z, POLISHED_ANDESITE);
                air(b, x, g + 1, z, c.pct(x, z, 530) < 25 ? MOSSY_STONE_BRICKS : ring);
                boolean post = alongX ? z == cz && x != cx : x == cx && z != cz;
                if (post) {
                    air(b, x, g + 2, z, SPRUCE_FENCE);
                    air(b, x, g + 3, z, SPRUCE_FENCE);
                }
                // the roof: a ridge along the windlass, stairs falling away either side
                boolean ridge = alongX ? z == cz : x == cx;
                if (ridge) air(b, x, g + 4, z, SPRUCE_PLANKS);
                else {
                    int down = alongX ? (z < cz ? N : S) : (x < cx ? W : E);
                    air(b, x, g + 4, z, SPRUCE_STAIR[(down + 2) & 3]);
                }
            }
        }
    }

    /**
     * A fountain, 5 x 5: a sunken basin of water inside a low rim, and a column in its middle carrying an upper bowl
     * (the spray is the client's, CitizenFx).
     */
    static void fountain(Ctx c, CityBuffer b, Prop p) {
        int g = p.ry(), cx = p.x0() + 2, cz = p.z0() + 2;
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                if (!b.contains(x, z)) continue;
                boolean rim = x == p.x0() || x == p.x1() || z == p.z0() || z == p.z1();
                if (rim) {
                    b.set(x, g, z, POLISHED_ANDESITE);
                    air(b, x, g + 1, z, STONE_BRICK_SLAB);
                } else if (x == cx && z == cz) {
                    b.set(x, g - 1, z, STONE);
                    b.set(x, g, z, CHISELED_STONE_BRICKS);
                    air(b, x, g + 1, z, CHISELED_STONE_BRICKS);
                    air(b, x, g + 2, z, CAULDRON);
                } else {
                    b.set(x, g - 1, z, STONE_BRICKS);
                    b.set(x, g, z, WATER);
                }
            }
        }
    }

    /** The Works' yards: a heap of coal or raw iron, or a stack of timber, two by two. */
    static void heap(Ctx c, CityBuffer b, Prop p) {
        int g = p.ry(), v = p.v();
        for (int x = p.x0(); x <= p.x1(); x++) {
            for (int z = p.z0(); z <= p.z1(); z++) {
                int k = (x - p.x0()) + 2 * (z - p.z0());
                switch (p.s()) {
                    case "coal" -> {
                        air(b, x, g + 1, z, COAL);
                        if (k == (v & 3)) air(b, x, g + 2, z, COAL);
                    }
                    case "iron" -> {
                        air(b, x, g + 1, z, RAW_IRON);
                        if (k == (v & 3)) air(b, x, g + 2, z, RAW_IRON);
                    }
                    default -> {
                        // a grindstone in the far corner of some timber yards (inside the heap's own box, so a chunk
                        // painted alone matches its neighbours)
                        if (k == 3 && (v & 3) == 3) {
                            air(b, x, g + 1, z, GRINDSTONE);
                            continue;
                        }
                        BlockState log = LOGS[(v & 2) + ((v & 1) == 0 ? 0 : 1)];
                        air(b, x, g + 1, z, log);
                        if (k < 2 || (v & 1) == 1 && (k & 1) == 0) air(b, x, g + 2, z, log);
                    }
                }
            }
        }
    }

    /** A net drying on the quay between two poles, along the water's edge. */
    static void net(CityBuffer b, Prop p) {
        boolean alongX = p.x1() > p.x0();
        int n = alongX ? p.x1() - p.x0() + 1 : p.z1() - p.z0() + 1;
        for (int k = 0; k < n; k++) {
            int x = alongX ? p.x0() + k : p.x0(), z = alongX ? p.z0() : p.z0() + k;
            if (k == 0 || k == n - 1) {
                air(b, x, p.ry() + 1, z, POLE);
                air(b, x, p.ry() + 2, z, POLE_TOP);
            } else {
                air(b, x, p.ry() + 1, z, NET[alongX ? 0 : 1]);
                air(b, x, p.ry() + 2, z, NET[alongX ? 0 : 1]);
            }
        }
    }
}
