package dev.awaken.city;

import dev.awaken.city.CityGrammar.Box;
import dev.awaken.city.CityPainter.Ctx;
import dev.awaken.city.CityPlan.Plot;
import dev.awaken.city.CityPlan.Prop;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * Carillon's farmsteads (M4, the Commons step; SPEC §16.3): six farms among the fields, three on the roads in (each by a
 * field gate, so its track crosses the ditch on the gate's culvert) and three out among the fields. Each is one plan, a
 * box 22 along its front by 26 deep, drawn in its own (u, v) frame (u along the front, v away from it) and turned to
 * face its road: a farmhouse from the city's own grammar, a timber barn with its doors to the yard and a hay loft, a
 * haystack, a well, a hay cart, a paddock with a hay feeder and a trough, a kitchen garden, a woodpile, crates and sacks
 * by the barn, and a lamp at the gate. Painted after the ground, over the fields there, clipped to the buffer.
 */
final class CityFarmsteads {
    private CityFarmsteads() {}

    static final int U = 22, V = 26;

    /** A farm: its box in plan coordinates and the side its front (v = 0) faces. */
    record Farm(int id, int x0, int z0, int front) {
        int x1() {
            return x0 + ((front == N || front == S) ? U : V) - 1;
        }

        int z1() {
            return z0 + ((front == N || front == S) ? V : U) - 1;
        }

        boolean contains(int x, int z) {
            return x >= x0 && x <= x1() && z >= z0 && z <= z1();
        }

        /** Plan cell to the farm's frame: [u, v]. */
        int u(int x, int z) {
            return front == W || front == E ? z - z0 : x - x0;
        }

        int v(int x, int z) {
            return switch (front) {
                case W -> x - x0;
                case E -> x1() - x;
                case N -> z - z0;
                default -> z1() - z;
            };
        }

        /** The farm's frame to plan: x, z of (u, v). */
        int x(int u, int v) {
            return switch (front) {
                case W -> x0 + v;
                case E -> x1() - v;
                default -> x0 + u;
            };
        }

        int z(int u, int v) {
            return switch (front) {
                case N -> z0 + v;
                case S -> z1() - v;
                default -> z0 + u;
            };
        }

        /** The plan box of a box in the farm's frame. */
        Box box(int u0, int v0, int u1, int v1) {
            int ax = x(u0, v0), az = z(u0, v0), bx = x(u1, v1), bz = z(u1, v1);
            return new Box(Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz));
        }

        /** Directions in plan of the farm's own: toward the front (-v), away (+v), along (+u). */
        int toFront() {
            return front;
        }

        int along() {
            return front == W || front == E ? S : E;
        }
    }

    /**
     * The farms. A, B and C stand by the first field gate on the north, east and south roads (the track at u 12..14 meets
     * the gate); D, E and F out among the fields.
     */
    static final List<Farm> FARMS = List.of(
            new Farm(0, 8, -283, W),      // Gatefield, by the High Gate road
            new Farm(1, 255, -34, S),     // by the east road
            new Farm(2, -34, 255, E),     // by the south road
            new Farm(3, 176, -214, S),    // north-east, among the fields
            new Farm(4, -302, 18, E),     // west
            new Farm(5, 178, 186, N));    // south-east

    /** Whether a cell is some farm's ground (the fields leave it to the farm). */
    static Farm at(int x, int z) {
        for (Farm f : FARMS) if (f.contains(x, z)) return f;
        return null;
    }

    static final BlockState PACKED_MUD = of("minecraft:packed_mud"), TROUGH = of("minecraft:water_cauldron[level=3]"),
            DARK_LOG = of("minecraft:dark_oak_log[axis=y]"), STRIPPED_SPRUCE = of("minecraft:stripped_spruce_log[axis=y]");
    static final BlockState[] SPRUCE_LOG_LYING = {of("minecraft:spruce_log[axis=x]"), of("minecraft:spruce_log[axis=z]")};
    static final BlockState[] FENCE_RUN = CityCommons.FENCE_RUN;

    /** Paints every farm that touches the buffer (after the ground: the farm's own ground over the fields). */
    static void paint(Ctx c, CityBuffer b) {
        for (Farm f : FARMS) {
            if (f.x1() < b.px0 || f.x0() > b.x1() || f.z1() < b.pz0 || f.z0() > b.z1()) continue;
            for (int x = Math.max(f.x0(), b.px0); x <= Math.min(f.x1(), b.x1()); x++)
                for (int z = Math.max(f.z0(), b.pz0); z <= Math.min(f.z1(), b.z1()); z++) cell(c, b, f, x, z);
            props(c, b, f);
            house(c, b, f);
        }
    }

    /**
     * One cell of a farm in its own frame: the track (u 12..14 to v 8), the barn (u 0..10, v 10..24 with its eaves),
     * the yard (u 11..21, v 9..16, and a lane by the barn to the haystack), the paddock (u 0..9, v 1..8), the kitchen
     * garden (u 16..21, v 1..7), grass round the farmhouse (u 14..21, v 17..24).
     */
    static void cell(Ctx c, CityBuffer b, Farm f, int x, int z) {
        int u = f.u(x, z), v = f.v(x, z);
        int h = c.pct(x, z, 700);
        b.column(x, 1, 14, z, AIR);
        if (u >= 12 && u <= 14 && v <= 8) {
            b.set(x, 0, z, h < 60 ? PATH : h < 82 ? COARSE_DIRT : GRAVEL);
            return;
        }
        if (barn(u, v)) {
            barnCell(c, b, f, x, z, u, v);
            return;
        }
        if (u >= 11 && u <= 21 && v >= 9 && v <= 16 || u >= 11 && u <= 12 && v >= 17) {
            b.set(x, 0, z, h < 38 ? COARSE_DIRT : h < 62 ? PACKED_MUD : h < 82 ? GRAVEL : PATH);
            return;
        }
        // the paddock: grass inside a fence, a hay feeder and a trough
        if (u <= 9 && v >= 1 && v <= 8) {
            b.set(x, 0, z, GRASS);
            boolean rim = u == 0 || u == 9 || v == 1 || v == 8;
            if (rim) b.set(x, 1, z, u == 9 && v == 5 ? CityCommons.FIELD_GATE[f.along()] : fence(f, u, v, 0, 9, 1, 8));
            else if (u == 4 && v == 4) b.set(x, 1, z, CityCommons.HAY_Y);
            else if (u == 6 && v == 3) b.set(x, 1, z, TROUGH);
            else if (h < 30) b.set(x, 1, z, SHORT_GRASS);
            return;
        }
        // the kitchen garden: a low fence round rows of greens either side of a path
        if (u >= 16 && u <= 21 && v >= 1 && v <= 7) {
            boolean rim = u == 16 || u == 21 || v == 1 || v == 7;
            if (rim) {
                b.set(x, 0, z, GRASS);
                b.set(x, 1, z, u == 16 && v == 4 ? CityCommons.FIELD_GATE[f.along()] : fence(f, u, v, 16, 21, 1, 7));
            } else if (v == 4) b.set(x, 0, z, PATH);
            else {
                b.set(x, 0, z, FARMLAND);
                int row = u % 3;
                b.set(x, 1, z, row == 0 ? CityCommons.CARROTS[5 + h % 3] : row == 1 ? CityCommons.BEETS[2 + (h & 1)] : CityCommons.POTATOES[4 + h % 4]);
            }
            return;
        }
        b.set(x, 0, z, GRASS);
        if (u == 11 && v == 1) lamp(b, x, z); // a lamp where the track leaves the road
        else if (h < 20) b.set(x, 1, z, SHORT_GRASS);
        else if (h < 23) b.set(x, 1, z, CityCommons.WILDFLOWERS[h % CityCommons.WILDFLOWERS.length]);
    }

    static void lamp(CityBuffer b, int x, int z) {
        b.set(x, 1, z, WALL_POST);
        b.column(x, 2, 3, z, FENCE);
        b.set(x, 4, z, LANTERN);
    }

    /** A fence round a rectangle in the farm's frame: runs along its sides, posts at its corners. */
    static BlockState fence(Farm f, int u, int v, int u0, int u1, int v0, int v1) {
        boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
        if (corner) return CityWall.SPRUCE_FENCE_;
        boolean alongU = v == v0 || v == v1;
        int planAxis = (f.along() == E) == alongU ? 0 : 1; // 0: the run is along plan x
        return FENCE_RUN[planAxis];
    }

    // ---- the barn ----------------------------------------------------------------------------------------------------

    static final int BU0 = 1, BU1 = 9, BV0 = 11, BV1 = 23, BWALL = 5, RIDGE = 5;

    static boolean barn(int u, int v) {
        return u >= BU0 - 1 && u <= BU1 + 1 && v >= BV0 - 1 && v <= BV1 + 1;
    }

    /**
     * The barn, nine wide and thirteen deep, its ridge along v (u 5): a cobble plinth, walls of spruce boards between dark
     * oak posts to row 5, big doors open on its yard side (u 9) and a hay loft door in the gable to the road, a roof of
     * dark oak falling to eaves one out each side and over each gable; hay inside.
     */
    static void barnCell(Ctx c, CityBuffer b, Farm f, int x, int z, int u, int v) {
        int h = c.pct(x, z, 710);
        boolean inside = u > BU0 && u < BU1 && v > BV0 && v < BV1;
        boolean wall = u >= BU0 && u <= BU1 && v >= BV0 && v <= BV1 && !inside;
        b.set(x, 0, z, inside || wall ? COARSE_DIRT : GRASS);
        // the roof: d cells from the ridge's line; eaves at d 5 (row 5), the ridge at row 10
        int d = Math.abs(u - RIDGE);
        int roofRow = BWALL + 5 - d;
        int up = u < RIDGE ? f.along() : (f.along() + 2) & 3; // toward the ridge, in plan
        if (v >= BV0 - 1 && v <= BV1 + 1) {
            if (d == 0) b.set(x, roofRow, z, DARK_OAK.block());
            else b.set(x, roofRow, z, DARK_OAK.stair()[up]);
        }
        if (!inside && !wall) return;
        boolean gableEnd = v == BV0 || v == BV1;
        boolean post = (u == BU0 || u == BU1) && (v == BV0 || v == BV1 || v == 17);
        for (int ry = 1; ry <= (gableEnd ? roofRow - 1 : BWALL); ry++) {
            if (inside) {
                b.set(x, ry, z, AIR);
                continue;
            }
            BlockState s = ry == 1 ? COBBLE : post ? DARK_LOG : SPRUCE_PLANKS;
            // the big doors on the yard side, open (the leaves folded back inside), the loft door to the road
            if (u == BU1 && v >= 15 && v <= 18 && ry <= 4) s = AIR;
            if (v == BV0 && u >= 4 && u <= 6 && ry >= 6 && ry <= 7) s = u == RIDGE ? AIR : STRIPPED_SPRUCE;
            b.set(x, ry, z, s);
        }
        // hay inside, stacked against the back wall and in the loft
        if (inside && v >= 19 && h < 75) b.column(x, 1, v >= 21 ? 3 : 1 + (h & 1), z, CityCommons.HAY_Y);
        if (inside && v <= 13 && u <= 4) b.set(x, 1, z, CityCommons.HAY_Y);
    }

    // ---- props: haystack, well, cart, woodpile, crates ---------------------------------------------------------------

    static void props(Ctx c, CityBuffer b, Farm f) {
        // the haystack (three by three, two high, a cap of one) behind the yard
        for (int u = 11; u <= 13; u++)
            for (int v = 20; v <= 22; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                if (!b.contains(x, z)) continue;
                b.column(x, 1, 2, z, CityCommons.HAY_Y);
                if (u == 12 && v == 21) b.set(x, 3, z, CityCommons.HAY_Y);
            }
        Box well = f.box(17, 10, 19, 12);
        CityProps.well(c, b, new Prop("well", well.x0(), well.z0(), well.x1(), well.z1(), 0, "", f.id(), ""));
        Box cart = f.box(15, 14, 16, 14);
        CityProps.cart(b, new Prop("hay_cart", cart.x0(), cart.z0(), cart.x1(), cart.z1(), 0, DIR[f.along()], 0, ""), true);
        // the woodpile by the house, logs lying along u
        int axis = f.along() == E ? 0 : 1;
        for (int v = 17; v <= 20; v++) {
            int x = f.x(13, v), z = f.z(13, v);
            if (b.contains(x, z)) b.column(x, 1, v == 17 || v == 20 ? 1 : 2, z, SPRUCE_LOG_LYING[1 - axis]);
        }
        // crates, sacks and a cask by the barn doors
        int[][] by = {{10, 14}, {11, 19}, {10, 19}};
        for (int i = 0; i < by.length; i++) {
            int x = f.x(by[i][0], by[i][1]), z = f.z(by[i][0], by[i][1]);
            if (b.contains(x, z)) b.set(x, 1, z, i == 0 ? CityProps.CRATES[0] : CityProps.SACKS[1][i]);
        }
    }

    // ---- the farmhouse ------------------------------------------------------------------------------------------------

    /**
     * The farmhouse: two storeys of the Wallring's timber and plaster under a steep gable, eight by eight, its door to
     * the yard (the city's own grammar, as any house inside the wall; ids from 9000).
     */
    static void house(Ctx c, CityBuffer b, Farm f) {
        Box box = f.box(14, 17, 21, 24);
        if (box.x1() < b.px0 - 2 || box.x0() > b.x1() + 2 || box.z1() < b.pz0 - 2 || box.z0() > b.z1() + 2) return;
        int front = f.toFront();
        boolean ridgeX = front == N || front == S;
        int eave = 9;
        int top = eave + 7 + 1; // the plan's steep rise over a half-span of 4, plus one
        int doorX, doorZ;
        switch (front) {
            case N -> { doorX = box.x0() + 3; doorZ = box.z0(); }
            case S -> { doorX = box.x0() + 3; doorZ = box.z1(); }
            case W -> { doorX = box.x0(); doorZ = box.z0() + 3; }
            default -> { doorX = box.x1(); doorZ = box.z0() + 3; }
        }
        Plot p = new Plot(9000 + f.id(), WALLRING, box.x0(), box.z0(), box.x1(), box.z1(), front, 2, CityPlan.Roof.GABLE, ridgeX, true,
                eave, top, 0, doorX, doorZ, "farm", false, 7 + f.id() * 13);
        CityGrammar.plot(c, b, p);
    }
}
