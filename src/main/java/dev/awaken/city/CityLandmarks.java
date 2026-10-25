package dev.awaken.city;

import dev.awaken.city.CityBlocks.Roof;
import dev.awaken.city.CityGrammar.Box;
import dev.awaken.city.CityPainter.Ctx;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityPlan.*;

/**
 * The hand-made landmarks in blockout (SPEC §16.2): each part is at most 14x14 and paints its own columns, so a
 * landmark split across chunks agrees with itself. Silhouettes first: from 200 blocks up you should be able to name
 * the Choir's green needle, the Association's bronze dome, the Roost's copper cap on the north-west wall, the gate
 * towers, the district bell towers, the cranes on the Quay and the wheels of the Works.
 */
final class CityLandmarks {
    private CityLandmarks() {}

    static void part(Ctx c, CityBuffer b, Part p) {
        Box box = new Box(p.x0(), p.z0(), p.x1(), p.z1());
        int ax0 = Math.max(p.x0() - 2, b.px0), ax1 = Math.min(p.x1() + 2, b.x1());
        int az0 = Math.max(p.z0() - 2, b.pz0), az1 = Math.min(p.z1() + 2, b.z1());
        if (ax0 > ax1 || az0 > az1) return;
        int g = c.plan.lift((p.x0() + p.x1()) / 2, (p.z0() + p.z1()) / 2);
        for (int x = ax0; x <= ax1; x++) {
            for (int z = az0; z <= az1; z++) {
                switch (p.kind()) {
                    case "tower", "gate_tower" -> tower(c, b, p, box, x, z);
                    case "gate_passage" -> gatePassage(c, b, p, box, x, z);
                    case "spire" -> spire(c, b, p, box, x, z, g);
                    case "nave" -> nave(c, b, p, box, x, z, g);
                    case "apse" -> apse(c, b, p, box, x, z, g);
                    case "market_hall" -> marketHall(c, b, p, box, x, z, g);
                    case "stall" -> stall(c, b, p, box, x, z, g);
                    case "assoc_hall", "assoc_wing" -> assocHall(c, b, p, box, x, z, g);
                    case "assoc_centre" -> assocCentre(c, b, p, box, x, z, g);
                    case "archive" -> archive(c, b, p, box, x, z, g);
                    case "pavilion" -> pavilion(c, b, p, box, x, z, g);
                    case "roost_tower" -> roostTower(c, b, p, box, x, z);
                    case "skybridge" -> skybridge(c, b, p, box, x, z);
                    case "roost_hall" -> roostHall(c, b, p, box, x, z);
                    case "belltower" -> bellTower(c, b, p, box, x, z, g);
                    case "crane" -> crane(c, b, p, box, x, z);
                    case "boom" -> boom(c, b, p, box, x, z);
                    case "mill" -> mill(c, b, p, box, x, z, g);
                    case "wheel" -> wheel(c, b, p, box, x, z);
                    case "bridge" -> bridge(c, b, p, box, x, z);
                    case "cellar" -> cellar(c, b, p, box, x, z, g);
                    case "walkway" -> walkway(c, b, p, box, x, z);
                    default -> {
                    }
                }
            }
        }
    }

    // ---- shared shapes --------------------------------------------------------------------------------------

    static BlockState masonry(Ctx c, int x, int ry, int z) {
        int h = c.pct(x, z, 300 + ry);
        if (ry % 9 == 0) return POLISHED_ANDESITE;
        return h < 9 ? MOSSY_STONE_BRICKS : h < 17 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
    }

    static BlockState pale(Ctx c, int x, int ry, int z) {
        if (ry % 8 == 0) return POLISHED_DIORITE;
        return c.pct(x, z, 310 + ry) < 20 ? QUARTZ_BRICKS : CALCITE;
    }

    /** Distance in from the box's edge (0 on the edge). */
    static int inset(Box box, int x, int z) {
        return Math.min(Math.min(x - box.x0(), box.x1() - x), Math.min(z - box.z0(), box.z1() - z));
    }

    /** A pointed cap: a stepped pyramid over a box, two rows per step (steep), from row `from`. */
    static void pyramid(CityBuffer b, Box box, int x, int z, int from, Roof roof, int perStep) {
        if (!box.contains(x, z)) return;
        int dW = x - box.x0() + 1, dE = box.x1() - x + 1, dN = z - box.z0() + 1, dS = box.z1() - z + 1;
        int d = Math.min(Math.min(dW, dE), Math.min(dN, dS));
        int hs = (Math.min(box.w(), box.d()) + 1) / 2;
        int h = from + perStep * (Math.min(d, hs) - 1);
        for (int y = from; y < h; y++) b.set(x, y, z, roof.block());
        if (d >= hs) {
            b.set(x, h, z, roof.block());
            if (box.w() % 2 == 1 && box.d() % 2 == 1) b.set(x, h + 1, z, ROD);
        } else b.set(x, h, z, CityGrammar.facing(roof, dW, dE, dN, dS));
    }

    /** Octagon test: inside a square of half-size r with its corners cut by k. */
    static boolean octagon(double dx, double dz, double r, double k) {
        double ax = Math.abs(dx), az = Math.abs(dz);
        return ax <= r && az <= r && ax + az <= 2 * r - k;
    }

    // ---- the wall's towers and gates ------------------------------------------------------------------------

    static void tower(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) {
            // machicolation: a corbel ring under the parapet
            int os = box.outside(x, z);
            if (os >= 0) b.set(x, p.h() - 5, z, STONE_CORBEL[CityGrammar.inward(os)]);
            return;
        }
        int h = p.h();
        int in = inset(box, x, z);
        b.plate(x, z);
        b.column(x, -4, -1, z, STONE);
        for (int ry = 0; ry < h - 4; ry++) {
            BlockState s = ry < 3 ? DEEPSLATE_BRICKS : masonry(c, x, ry, z);
            if (in == 0 && ry > 6 && ry % 10 == 4 && ((box.along(box.side(x, z), x, z) + 2) % 7 == 3)) s = TINTED;
            b.set(x, ry, z, s);
        }
        // the wall walk runs through: a hall at walk level with openings where the curtain meets the tower
        int walk = c.plan.wallHeight;
        if (in >= 1 && in <= 5) b.column(x, walk, walk + 2, z, AIR);
        if (in == 0) {
            int sd = box.side(x, z);
            int nx = x + (sd == E ? 1 : sd == W ? -1 : 0), nz = z + (sd == S ? 1 : sd == N ? -1 : 0);
            if (c.plan.kind(nx, nz) == WALL || c.plan.kind(nx, nz) == WATERGATE) b.column(x, walk, walk + 2, z, AIR);
        }
        // the top: a parapet platform, merlons, then the cap
        int deck = h - 4;
        b.set(x, deck, z, POLISHED_ANDESITE);
        if (in == 0) {
            b.set(x, deck + 1, z, STONE_BRICKS);
            if (((x + z) & 1) == 0) b.set(x, deck + 2, z, STONE_BRICKS);
        }
        String style = p.style();
        Roof roof = switch (style) {
            case "1" -> GREEN;
            case "3" -> COPPER;
            default -> SLATE;
        };
        if (style.equals("2")) return; // an open fighting top
        Box cap = new Box(box.x0() + 1, box.z0() + 1, box.x1() - 1, box.z1() - 1);
        pyramid(b, cap, x, z, deck + 1, roof, 2);
    }

    static void gatePassage(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        int h = p.h();
        boolean alongZ = p.axis().equals("z");
        int across = alongZ ? x - box.x0() : z - box.z0();
        int width = alongZ ? box.w() : box.d();
        int along = alongZ ? z - box.z0() : x - box.x0();
        int length = alongZ ? box.d() : box.w();
        // a pointed arch over the road: open to 9 in the middle, 7 at the sides
        int edge = Math.min(across, width - 1 - across);
        int open = edge == 0 ? 0 : edge == 1 ? 7 : edge == 2 ? 8 : 9;
        for (int ry = open + 1; ry < h; ry++) b.set(x, ry, z, masonry(c, x, ry, z));
        if (open > 0) {
            b.column(x, 1, open, z, AIR);
            b.set(x, open + 1, z, edge == 1 ? STONE_SLAB_TOP : CHISELED_STONE_BRICKS);
            // the portcullis, half raised
            if (along == length / 2) b.column(x, open - 2, open, z, alongZ ? BARS[0] : BARS[1]);
        } else {
            b.column(x, 1, open, z, AIR);
            b.column(x, 0, 9, z, masonry(c, x, 5, z));
        }
        b.set(x, h, z, POLISHED_ANDESITE);
        if (inset(box, x, z) == 0 && ((x + z) & 1) == 0) b.set(x, h + 1, z, STONE_BRICKS);
        // a bronze bell-gable over the gate's middle, the city's sign above every gate
        if (edge >= 3 && along >= length / 2 - 1 && along <= length / 2 + 1) {
            b.column(x, h + 1, h + 4, z, (along == length / 2 && edge == width / 2 - 1) ? BRONZE : AIR);
            if (along == length / 2) {
                b.set(x, h + 1, z, POLISHED_ANDESITE);
                b.set(x, h + 5, z, edge == width / 2 - 1 ? GILDED : BRONZE);
            }
        }
    }

    // ---- the Choir --------------------------------------------------------------------------------------------

    /**
     * The great spire (~140): a pale stone tower with a portal and a rose window toward the square, a lancet stage,
     * an open bronze-trimmed belfry with the great bell, a crown of pinnacles, then a smooth verdigris needle.
     */
    static void spire(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        double dx = x - cx, dz = z - cz;
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        boolean south = z == box.z1();
        int t = sd >= 0 ? box.along(sd, x, z) : 0;
        b.plate(x, z);
        // stage 1: the base, 0-30
        for (int ry = 1; ry <= 30; ry++) {
            BlockState s = pale(c, x, ry, z);
            boolean corner = box.corner(x, z);
            if (corner) s = POLISHED_DIORITE;
            if (south && in == 0 && Math.abs(dx) <= 2.5 && ry <= 10) s = ry == 10 || (Math.abs(dx) > 1.5 && ry == 9) ? CHISELED_STONE_BRICKS : AIR;
            if (south && in == 0 && Math.abs(dx) <= 3 && ry >= 15 && ry <= 21) {
                double rr = Math.sqrt(dx * dx + (ry - 18) * (ry - 18));
                if (rr <= 3.2) s = rr < 1 ? GILDED : PANE[0];
            }
            if (!south && in == 0 && !corner && (t == 3 || t == 10) && ry >= 14 && ry <= 22) s = TINTED;
            if (in > 0) s = CORE;
            b.set(x, g + ry, z, s);
        }
        // stage 2: the lancet stage, inset 1, 31-60
        if (in >= 1) {
            for (int ry = 31; ry <= 60; ry++) {
                BlockState s = in == 1 ? pale(c, x, ry, z) : CORE;
                int t2 = Math.abs(in == 1 ? (sd == N || sd == S || z == box.z0() + 1 || z == box.z1() - 1 ? (int) dx : (int) dz) : 99);
                if (in == 1 && t2 <= 1 && ry >= 36 && ry <= 54 && ry % 18 != 0) s = TINTED;
                if (in == 1 && (x == box.x0() + 1 || x == box.x1() - 1) && (z == box.z0() + 1 || z == box.z1() - 1)) s = POLISHED_DIORITE;
                b.set(x, g + ry, z, s);
            }
        }
        // corner pinnacles on stage 1's top
        if (box.corner(x, z)) {
            b.column(x, g + 31, g + 36, z, POLISHED_DIORITE);
            b.set(x, g + 37, z, COPPER_ROD);
        }
        // stage 3: the belfry (octagon), 61-78, open arches on the eight faces, the bell at its heart
        if (octagon(dx, dz, 5.5, 3)) {
            boolean shell = !octagon(dx, dz, 4.5, 3);
            for (int ry = 61; ry <= 78; ry++) {
                BlockState s;
                if (ry == 61 || ry == 70 || ry == 78) s = BRONZE;
                else if (shell) {
                    boolean pier = Math.abs(Math.abs(dx) - Math.abs(dz)) < 1.1 || (Math.abs(dx) > 4.9 && Math.abs(dz) > 4.9);
                    s = pier ? POLISHED_DIORITE : (ry >= 63 && ry <= 68 || ry >= 72 && ry <= 76 ? AIR : CALCITE);
                } else s = AIR;
                b.set(x, g + ry, z, s);
            }
            if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) {
                b.set(x, g + 69, z, CHAIN_Y);
                b.set(x, g + 68, z, BELL);
            }
        }
        // stage 4: the crown 79-84, then the needle 85-136, an orb and the rod to 141
        if (octagon(dx, dz, 5.5, 3)) {
            for (int ry = 79; ry <= 84; ry++) {
                boolean shell = !octagon(dx, dz, 4.5, 3);
                b.set(x, g + ry, z, shell ? (ry == 84 ? BRONZE : CALCITE) : CORE);
            }
        }
        if ((Math.abs(dx) > 5 && Math.abs(dz) > 5) || (Math.abs(Math.abs(dx) - 4.5) < 0.6 && Math.abs(Math.abs(dz) - 4.5) < 0.6)) {
            b.column(x, g + 85, g + 92, z, VERDIGRIS);
            b.set(x, g + 93, z, COPPER_ROD);
        }
        for (int ry = 85; ry <= 136; ry++) {
            double f = (ry - 85) / 51.0;
            double r = 4.6 * Math.pow(1 - f, 0.85) + 0.35;
            if (octagon(dx, dz, r, r * 0.6)) b.set(x, g + ry, z, ry % 12 == 0 ? BRONZE : VERDIGRIS);
        }
        if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) {
            b.set(x, g + 137, z, GILDED);
            b.column(x, g + 138, g + 139, z, COPPER_ROD);
        }
    }

    static void nave(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) {
            // buttresses on the long sides
            int os = box.outside(x, z);
            if ((os == N || os == S) && Math.floorMod(x - box.x0(), 4) == 1) b.column(x, g + 1, g + 12, z, POLISHED_DIORITE);
            return;
        }
        int wallTop = p.h() - 9;
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        b.plate(x, z);
        for (int ry = 1; ry <= wallTop; ry++) {
            BlockState s = in == 0 ? pale(c, x, ry, z) : CORE;
            if (in == 0 && (sd == N || sd == S) && Math.floorMod(x - box.x0(), 4) == 3 && ry >= 5 && ry <= wallTop - 3) s = TINTED;
            b.set(x, g + ry, z, s);
        }
        Roof roof = GREEN;
        int d = Math.min(z - box.z0() + 1, box.z1() - z + 1);
        int hs = (box.d() + 1) / 2;
        int h = g + wallTop + Math.min(2 * d, 2 * hs);
        for (int y = g + wallTop + 1; y < h; y++) b.set(x, y, z, (x == box.x0() || x == box.x1()) ? CALCITE : roof.block());
        if (d >= hs) {
            b.set(x, h, z, roof.block());
            b.set(x, h + 1, z, BRONZE);
        } else b.set(x, h, z, roof.stair()[z - box.z0() < box.z1() - z ? S : N]);
    }

    static void apse(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        double dx = x - cx, dz = z - cz;
        if (!octagon(dx, dz, 6.5, 4)) return;
        b.plate(x, z);
        boolean shell = !octagon(dx, dz, 5.5, 4);
        for (int ry = 1; ry <= 18; ry++) b.set(x, g + ry, z, shell ? (ry >= 6 && ry <= 14 && ((x + z) & 3) == 0 ? TINTED : pale(c, x, ry, z)) : CORE);
        // a verdigris cone
        for (int ry = 19; ry <= p.h(); ry++) {
            double r = 6.5 * (1 - (ry - 19) / (double) (p.h() - 18));
            if (octagon(dx, dz, r, r * 0.6)) b.set(x, g + ry, z, VERDIGRIS);
        }
    }

    // ---- the market -------------------------------------------------------------------------------------------

    static void marketHall(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        boolean eastFace = p.face().equals("E");
        if (!box.contains(x, z)) {
            // the balcony on the square side (act 3 of the run climbs it)
            if (eastFace && x == box.x1() + 1 && z >= box.z0() && z <= box.z1()) {
                b.set(x, g + 10, z, STONE_SLAB_TOP);
                b.set(x, g + 11, z, DIORITE_WALL_RUN[1]);
                if ((z - box.z0()) % 4 == 0) b.set(x, g + 9, z, STONE_CORBEL[W]);
            }
            return;
        }
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        b.plate(x, z);
        // an open arcade on the ground floor: piers every three blocks
        boolean pier = box.corner(x, z) || (in == 0 && box.along(sd, x, z) % 3 == 0);
        for (int ry = 1; ry <= 5; ry++) b.set(x, g + ry, z, pier ? STONE_BRICKS : (in == 0 && ry == 5 ? CHISELED_STONE_BRICKS : AIR));
        b.set(x, g + 6, z, BRONZE);
        for (int ry = 7; ry <= 14; ry++) {
            BlockState s = in == 0 ? (ry == 14 ? BRONZE : WHITE_TERRACOTTA) : CORE;
            if (in == 0 && !box.corner(x, z) && box.along(sd, x, z) % 3 == 1 && ry >= 8 && ry <= 12) s = PANE[sd == N || sd == S ? 0 : 1];
            if (box.corner(x, z)) s = STONE_BRICKS;
            b.set(x, g + ry, z, s);
        }
        Box roofBox = new Box(box.x0(), box.z0(), box.x1(), box.z1());
        pyramidHip(b, roofBox, x, z, g + 15, COPPER);
    }

    /** A hipped roof one row per step, ridge along the long side, from row `from`. */
    static void pyramidHip(CityBuffer b, Box box, int x, int z, int from, Roof roof) {
        int dW = x - box.x0() + 1, dE = box.x1() - x + 1, dN = z - box.z0() + 1, dS = box.z1() - z + 1;
        int d = Math.min(Math.min(dW, dE), Math.min(dN, dS));
        int hs = (Math.min(box.w(), box.d()) + 1) / 2;
        int h = from + Math.min(d, hs) - 1;
        for (int y = from; y < h; y++) b.set(x, y, z, roof.block());
        if (d >= hs) {
            b.set(x, h, z, roof.block());
            b.set(x, h + 1, z, roof.slab());
        } else b.set(x, h, z, CityGrammar.facing(roof, dW, dE, dN, dS));
    }

    static void stall(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        int colour = Math.floorMod(p.colour(), AWNING.length);
        boolean corner = box.corner(x, z);
        if (corner) b.column(x, g + 1, g + 3, z, SPRUCE_FENCE);
        if (!corner && z == box.z1()) b.set(x, g + 1, z, STONE_SLAB);
        // a striped awning: alternate stripes of the stall's colour and white
        int stripe = (x - box.x0()) % 2 == 0 ? colour : 3;
        int d = Math.min(z - box.z0(), box.z1() - z);
        if (d == 2) b.set(x, g + 5, z, AWNING_SLAB[stripe]);
        else b.set(x, g + 4, z, AWNING_STAIR[stripe][z - box.z0() < box.z1() - z ? S : N]);
    }

    // ---- the Hunter Association on the High Terrace -----------------------------------------------------------

    static void assocHall(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) {
            int os = box.outside(x, z);
            if (os >= 0) b.set(x, g + p.h() - 8, z, STONE_CORBEL[CityGrammar.inward(os)]);
            return;
        }
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        int top = p.h() - 8;
        b.plate(x, z);
        for (int ry = 1; ry <= top; ry++) {
            BlockState s = in == 0 ? pale(c, x, ry, z) : CORE;
            if (in == 0) {
                int t = box.along(sd, x, z);
                if (t % 3 == 0) s = POLISHED_DIORITE;                       // pilasters
                else if (ry >= 3 && ry <= top - 3 && t % 3 == 2 && ry % 7 != 0) s = PANE[sd == N || sd == S ? 0 : 1];
                if (ry == top - 1) s = BRONZE;                              // the glyph line
            }
            b.set(x, g + ry, z, s);
        }
        pyramid(b, box, x, z, g + top + 1, SLATE, 1);
    }

    static void assocCentre(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        double dx = x - cx, dz = z - cz;
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        b.plate(x, z);
        for (int ry = 1; ry <= 20; ry++) {
            BlockState s = in == 0 ? pale(c, x, ry, z) : CORE;
            // the great door on the south front, facing the forecourt
            if (z == box.z1() && Math.abs(dx) <= 1.6 && ry <= 7) s = ry == 7 ? CHISELED_STONE_BRICKS : AIR;
            if (in == 0 && box.corner(x, z)) s = POLISHED_DIORITE;
            if (ry == 19) s = in == 0 ? BRONZE : s;
            b.set(x, g + ry, z, s);
        }
        // the drum and a bronze dome with a lantern
        double r2 = dx * dx + dz * dz;
        if (r2 <= 6.2 * 6.2) {
            for (int ry = 21; ry <= 25; ry++) b.set(x, g + ry, z, r2 > 5.2 * 5.2 ? (ry == 25 ? BRONZE : CALCITE) : CORE);
            for (int ry = 26; ry <= 33; ry++) {
                double rr = 6.2 * Math.sqrt(Math.max(0, 1 - Math.pow((ry - 25) / 8.5, 2)));
                // a copper dome with bronze ribs on the eight winds: it catches the dawn from the whole city
                boolean rib = Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6;
                if (r2 <= rr * rr) b.set(x, g + ry, z, rib ? BRONZE : GILDED);
            }
        }
        if (Math.abs(dx) <= 1.1 && Math.abs(dz) <= 1.1) {
            b.column(x, g + 34, g + 36, z, (Math.abs(dx) > 0.6 && Math.abs(dz) > 0.6) ? POLISHED_DIORITE : AIR);
            b.set(x, g + 37, z, BRONZE);
            if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) {
                b.set(x, g + 35, z, LANTERN);
                b.set(x, g + 38, z, GILDED);
                b.set(x, g + 39, z, COPPER_ROD);
            }
        }
    }

    static void archive(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        int in = inset(box, x, z);
        b.plate(x, z);
        int top = p.h() - 12;
        for (int ry = 1; ry <= top; ry++) {
            BlockState s = in == 0 ? (ry % 8 == 0 ? BRONZE : pale(c, x, ry, z)) : CORE;
            if (in == 0 && !box.corner(x, z) && box.along(box.side(x, z), x, z) % 4 == 2 && ry % 8 >= 3 && ry % 8 <= 5) s = TINTED;
            b.set(x, g + ry, z, s);
        }
        pyramid(b, box, x, z, g + top + 1, SLATE, 2);
    }

    static void pavilion(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        if (box.corner(x, z) || (inset(box, x, z) == 0 && (x - box.x0()) % 3 == 0 && (z == box.z0() || z == box.z1())))
            b.column(x, g + 1, g + 5, z, POLISHED_DIORITE);
        b.set(x, g + 6, z, POLISHED_DIORITE);
        pyramidHip(b, box, x, z, g + 7, SLATE);
    }

    // ---- the Runners' Roost ----------------------------------------------------------------------------------

    static void roostTower(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        int walk = c.plan.wallHeight;
        Box up = new Box(box.x0() - 1, box.z0() - 1, box.x1() + 1, box.z1() + 1);
        if (box.contains(x, z)) {
            b.plate(x, z);
            b.column(x, -4, -1, z, STONE);
            for (int ry = 0; ry < walk; ry++) b.set(x, ry, z, ry < 3 ? DEEPSLATE_BRICKS : masonry(c, x, ry, z));
            if (inset(box, x, z) >= 1) b.column(x, walk, walk + 2, z, AIR);
            int sd = box.side(x, z);
            if (sd >= 0) {
                int nx = x + (sd == E ? 1 : sd == W ? -1 : 0), nz = z + (sd == S ? 1 : sd == N ? -1 : 0);
                if (c.plan.kind(nx, nz) == WALL) b.column(x, walk, walk + 2, z, AIR);
            }
            b.set(x, walk - 1, z, SPRUCE_PLANKS);
        }
        if (!up.contains(x, z)) return;
        // three timber storeys above the wall, jettied out by one all round, orange copper cap, a lookout lantern
        boolean edge = up.side(x, z) >= 0;
        int sd = up.side(x, z);
        for (int s = 0; s < 3; s++) {
            int base = walk + 3 + 4 * s;
            for (int r = 0; r < 4; r++) {
                BlockState bs;
                if (!edge) bs = CORE;
                else if (up.corner(x, z)) bs = POST[1];
                else if (r == 0) bs = BEAM_X[1];
                else if (up.along(sd, x, z) % 3 == 0) bs = POST[1];
                else if (r >= 1 && r <= 2 && up.along(sd, x, z) % 3 == 2) bs = PANE[sd == N || sd == S ? 0 : 1];
                else bs = WHITE_TERRACOTTA;
                b.set(x, base + r, z, bs);
            }
        }
        int top = walk + 15;
        b.set(x, top, z, SPRUCE_PLANKS);
        if (edge) b.set(x, top + 1, z, SPRUCE_FENCES[ringMask(up, x, z)]);
        Box cap = new Box(up.x0() + 2, up.z0() + 2, up.x1() - 2, up.z1() - 2);
        pyramid(b, cap, x, z, top + 3, COPPER, 2);
        // the lookout posts carrying the cap
        if (cap.corner(x, z)) b.column(x, top + 1, top + 2, z, SPRUCE_FENCE);
        // the guild's pennants on the cap's tip
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        if (Math.abs(x - cx) < 0.6 && Math.abs(z - cz) < 0.6) {
            b.column(x, top + 18, top + 24, z, SPRUCE_FENCE);
            b.set(x, top + 24, z, LANTERN);
        }
        if (Math.abs(x - cx - 1) < 0.6 && Math.abs(z - cz) < 0.6) {
            b.set(x, top + 22, z, AWNING_WOOL[4]);
            b.set(x, top + 21, z, AWNING_WOOL[0]);
        }
        if (Math.abs(x - cx - 2) < 0.6 && Math.abs(z - cz) < 0.6) b.set(x, top + 22, z, AWNING_WOOL[0]);
    }

    static void skybridge(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        int walk = c.plan.wallHeight;
        int in = inset(box, x, z);
        b.set(x, walk - 1, z, SPRUCE_PLANKS);
        b.set(x, walk - 2, z, (x & 1) == 0 ? CORBEL[N] : AIR);
        if (in == 0 && (z == box.z0() || z == box.z1())) {
            b.set(x, walk, z, SPRUCE_FENCES[(x > box.x0() ? 8 : 0) | (x < box.x1() ? 2 : 0)]);
            if ((x & 1) == 0) b.column(x, walk + 1, walk + 2, z, SPRUCE_FENCE);
        }
        b.set(x, walk + 3, z, SPRUCE.slab());
    }

    /** The guild hall inside the wall: stone to the wall walk, a roof terrace (the start) and a timber loft. */
    static void roostHall(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        int walk = c.plan.wallHeight;
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        b.plate(x, z);
        double cx = (box.x0() + box.x1()) / 2.0;
        for (int ry = 1; ry < walk; ry++) {
            BlockState s = in == 0 ? masonry(c, x, ry, z) : CORE;
            if (in == 0 && !box.corner(x, z) && box.along(sd, x, z) % 4 == 2 && ry % 6 >= 2 && ry % 6 <= 3) s = PANE[sd == N || sd == S ? 0 : 1];
            // the door to the street on the south side
            if (z == box.z1() && Math.abs(x - cx) < 1 && ry <= 2) s = (ry == 1 ? DOOR_LOWER : DOOR_UPPER)[1][N];
            b.set(x, ry, z, s);
        }
        b.set(x, walk - 1, z, SPRUCE_PLANKS);
        // the loft on the north-west half, the terrace on the south-east
        boolean loft = roostLoft(box, x, z);
        if (loft) {
            Box lb = new Box(box.x0(), box.z0(), box.x0() + box.w() / 2, box.z0() + box.d() / 2);
            if (lb.contains(x, z)) {
                boolean e = lb.side(x, z) >= 0;
                for (int ry = walk; ry <= walk + 7; ry++) {
                    BlockState s = !e ? CORE : (lb.corner(x, z) || ry == walk + 4 ? POST[1] : (ry % 4 == 2 && lb.along(lb.side(x, z), x, z) % 3 == 1 ? PANE[0] : WHITE_TERRACOTTA));
                    b.set(x, ry, z, s);
                }
                pyramid(b, lb, x, z, walk + 8, COPPER, 2);
            }
        }
        if (in == 0 && !loft) {
            int m = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + CityPainter.NB[d][0], nz = z + CityPainter.NB[d][1];
                if (box.side(nx, nz) >= 0 && !roostLoft(box, nx, nz)) m |= 1 << d;
            }
            b.set(x, walk, z, SPRUCE_FENCES[m]);
        }
        // lanterns at the terrace's corners
        if (box.corner(x, z) && !loft) b.set(x, walk + 1, z, LANTERN);
    }

    static boolean roostLoft(Box box, int x, int z) {
        return (x - box.x0()) + (z - box.z0()) < (box.w() + box.d()) / 2 - 1;
    }

    /** Neighbours of a box's perimeter cell that are on the perimeter too (a railing that runs round it). */
    static int ringMask(Box box, int x, int z) {
        int m = 0;
        for (int d = 0; d < 4; d++) if (box.side(x + CityPainter.NB[d][0], z + CityPainter.NB[d][1]) >= 0) m |= 1 << d;
        return m;
    }

    /** A slender octagonal needle from row `from`, `height` tall, starting `r0` wide (Bellwatch's slate spires). */
    static void needle(CityBuffer b, Box box, int x, int z, int from, int height, double r0, BlockState mat, BlockState band) {
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        double dx = x - cx, dz = z - cz;
        for (int k = 0; k < height; k++) {
            double r = r0 * Math.pow(1 - k / (double) height, 0.9) + 0.3;
            if (octagon(dx, dz, r, r * 0.6)) b.set(x, from + k, z, k % 9 == 0 ? band : mat);
        }
        if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6) b.column(x, from + height, from + height + 1, z, COPPER_ROD);
    }

    // ---- the district bell towers -----------------------------------------------------------------------------

    static void bellTower(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        String style = p.style();
        int h = p.h();
        int in = inset(box, x, z);
        int sd = box.side(x, z);
        int belfry = h - 18;
        b.plate(x, z);
        BlockState body = switch (style) {
            case "bellwatch" -> DEEPSLATE_BRICKS;
            case "quay" -> STONE_BRICKS;
            case "terrace" -> CALCITE;
            case "garden" -> CALCITE;
            case "works" -> BRICKS;
            default -> STONE_BRICKS;
        };
        BlockState trim = switch (style) {
            case "bellwatch" -> POLISHED_DEEPSLATE;
            case "terrace", "garden" -> POLISHED_DIORITE;
            case "works" -> STONE_BRICKS;
            default -> POLISHED_ANDESITE;
        };
        for (int ry = 1; ry < belfry; ry++) {
            BlockState s = in == 0 ? (box.corner(x, z) || ry % 10 == 0 ? trim : body) : CORE;
            if (in == 0 && !box.corner(x, z) && box.along(sd, x, z) == box.length(sd) / 2 && ry % 10 >= 4 && ry % 10 <= 6) s = TINTED;
            if (style.equals("quay") && ry > belfry - 10 && in == 0 && !box.corner(x, z)) s = ry % 4 == 0 ? BEAM_X[1] : SPRUCE_PLANKS;
            b.set(x, g + ry, z, s);
        }
        // the open belfry
        for (int ry = belfry; ry < belfry + 7; ry++) {
            BlockState s = AIR;
            if (box.corner(x, z) || (in == 0 && (box.along(sd, x, z) == 1 || box.along(sd, x, z) == box.length(sd) - 2))) s = trim;
            if (ry == belfry || ry == belfry + 6) s = in == 0 ? trim : (ry == belfry ? body : trim);
            b.set(x, g + ry, z, s);
        }
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        if (Math.abs(x - cx) < 0.6 && Math.abs(z - cz) < 0.6 || (box.w() % 2 == 0 && x == (int) Math.floor(cx) && z == (int) Math.floor(cz))) {
            b.set(x, g + belfry + 5, z, CHAIN_Y);
            b.set(x, g + belfry + 4, z, BELL);
        }
        int crown = g + belfry + 7;
        double dx = x - cx, dz = z - cz;
        switch (style) {
            case "bellwatch" -> needle(b, box, x, z, crown, 22, box.w() / 2.0, DEEPSLATE_TILES, POLISHED_DEEPSLATE);
            case "works" -> pyramid(b, box, x, z, crown, CINNABAR, 1);
            case "quay" -> {
                Box lb = new Box(box.x0() + 2, box.z0() + 2, box.x1() - 2, box.z1() - 2);
                if (lb.contains(x, z)) {
                    b.column(x, crown, crown + 3, z, lb.corner(x, z) ? SPRUCE_FENCE : (lb.side(x, z) >= 0 ? AIR : CORE));
                    if (!lb.corner(x, z) && lb.side(x, z) < 0 && Math.abs(dx) < 1 && Math.abs(dz) < 1) b.set(x, crown + 2, z, LANTERN);
                }
                pyramid(b, lb, x, z, crown + 4, COPPER, 2);
            }
            case "terrace", "garden" -> {
                BlockState dome = style.equals("terrace") ? BRONZE : VERDIGRIS;
                double r2 = dx * dx + dz * dz;
                for (int ry = 0; ry <= 9; ry++) {
                    double rr = (box.w() / 2.0) * Math.sqrt(Math.max(0, 1 - Math.pow(ry / 7.5, 2)));
                    if (style.equals("garden") && ry > 3) rr = (box.w() / 2.0) * Math.max(0, 1.15 - ry / 7.0) * (ry < 6 ? 1.05 : 0.8);
                    if (r2 <= rr * rr) b.set(x, crown + ry, z, dome);
                }
                if (Math.abs(dx) < 1 && Math.abs(dz) < 1) b.column(x, crown + 9, crown + 11, z, COPPER_ROD);
            }
            default -> pyramid(b, box, x, z, crown, SLATE, 2);
        }
    }

    // ---- the Quay and the Works -------------------------------------------------------------------------------

    static void crane(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        int h = p.h();
        boolean corner = box.corner(x, z);
        int in = inset(box, x, z);
        if (corner) b.column(x, 1, h, z, SPRUCE_WOOD);
        else if (in == 0) for (int ry = 4; ry < h - 6; ry += 5) b.set(x, ry, z, (z == box.z0() || z == box.z1()) ? SPRUCE_BEAM[0] : SPRUCE_BEAM[1]);
        // the cab at the top
        for (int ry = h - 6; ry <= h - 1; ry++) {
            BlockState s = in == 0 ? ((ry == h - 6 || ry == h - 1) ? SPRUCE_PLANKS : (ry == h - 3 && !corner ? PANE[0] : DARK_PLANKS)) : CORE;
            if (corner) s = SPRUCE_WOOD;
            b.set(x, ry, z, s);
        }
        pyramidHip(b, box, x, z, h, DARK_OAK);
    }

    static void boom(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        int h = p.h();
        double cz = (box.z0() + box.z1()) / 2.0;
        if (Math.abs(z - cz) < 0.6) {
            b.set(x, h, z, SPRUCE_BEAM[0]);
            if (x == box.x0() + 1) {
                b.column(x, 6 - 8, h - 1, z, CHAIN_Y);
                b.set(x, -3, z, HAY);
            }
            if (x == box.x1() - 3) b.set(x, h + 1, z, SPRUCE_FENCE);
        } else if (x > box.x0() && x < box.x1() && (x & 1) == 0) b.set(x, h + 1, z, SPRUCE_FENCE);
    }

    static void mill(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        Plot fake = new Plot(-1, QUAY_D, box.x0(), box.z0(), box.x1(), box.z1(), S, 3, CityPlan.Roof.GABLE, true, true,
                13, 22, g, Integer.MIN_VALUE, Integer.MIN_VALUE, "mill", false, 7);
        CityGrammar.building(c, b, fake, CityGrammar.millStyle(), box, x, z, g);
    }

    /** A water wheel standing in the mill race: rim, spokes and paddles of dark oak, its top just over the quay. */
    static final BlockState[] WHEEL_FACE = new BlockState[13 * 15];

    static {
        for (int i = 0; i < WHEEL_FACE.length; i++) WHEEL_FACE[i] = of("awaken:wheel_face[cell=" + i + "]");
    }

    static void wheel(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        double cx = (box.x0() + box.x1()) / 2.0, cz = (box.z0() + box.z1()) / 2.0;
        if (Math.abs(z - cz) > 1.1) return;
        boolean mid = Math.abs(z - cz) < 0.6;
        int axleY = -4;
        double dx = x - cx;
        for (int ry = axleY - 7; ry <= axleY + 7; ry++) {
            double dy = ry - axleY;
            double r = Math.sqrt(dx * dx + dy * dy);
            BlockState s = null;
            if (mid) {
                // (M4 B3a) the middle plane turns: animated cells of the whole wheel (tools/art/specs/city_wheel.py)
                int ix = (int) Math.round(dx) + 6, iy = (int) Math.round(dy) + 7;
                if (r <= 6.9 && ix >= 0 && ix < 13 && iy >= 0 && iy < 15) s = WHEEL_FACE[ix + iy * 13];
            } else if (r > 5.4 && r <= 6.6) s = DARK_WOOD;
            if (s != null) b.set(x, ry, z, s);
        }
    }

    // ---- under the old bell (M4 B2; SPEC decision 29) -----------------------------------------------------------

    /**
     * The cellar under Bellwatch's bell tower, in the Knoll's fill: a vaulted room 9 x 9 x 5 (floor at the street's
     * level, the bell square 8 over it), a passage east, and steps down to it from the bell square in an open stairwell
     * with a parapet round it, entered from the north. The box: the room's walls at its west, north and south edges, the
     * passage and the stairwell east of the room. B4 dresses it; the Living Wilds' quest finds its melting room here.
     */
    static void cellar(Ctx c, CityBuffer b, Part p, Box box, int x, int z, int g) {
        if (!box.contains(x, z)) return;
        int u = x - box.x0(), v = z - box.z0();
        boolean room = u >= 1 && u <= 9 && v >= 1 && v <= 9;
        boolean roomWall = u >= 0 && u <= 10 && v >= 0 && v <= 10 && !room;
        if (room) {
            b.set(x, 0, z, c.pct(x, z, 400) < 25 ? COBBLE : STONE_BRICKS);
            b.column(x, 1, 5, z, AIR);
            b.set(x, 6, z, u % 3 == 2 ? BEAM_Z[0] : DARK_PLANKS);
        } else if (roomWall) {
            for (int ry = 0; ry <= 6; ry++) b.set(x, ry, z, weatheredStone(c, x, ry, z));
        }
        // the stairwell: two wide, steps down from the north to a landing by the passage; its walls and parapet
        boolean well = (u == 13 || u == 14) && v >= 0 && v <= 8;
        boolean wellWall = ((u == 12 || u == 15) && v >= 0 && v <= 9) || ((u == 13 || u == 14) && v == 9);
        if (well) {
            if (v <= 6) {
                int step = 7 - v;
                b.set(x, step, z, STONE_STAIR[N]);
                b.column(x, step + 1, g, z, AIR);
            } else {
                b.set(x, 0, z, STONE_BRICKS);
                b.column(x, 1, g, z, AIR);
            }
        } else if (wellWall) {
            for (int ry = 0; ry <= g; ry++) b.set(x, ry, z, weatheredStone(c, x, ry, z));
            b.set(x, g + 1, z, STONE_WALLS[wellMask(u, v)]);
        }
        // the passage east (two wide, three high) through the room's wall and the stairwell's
        if (u >= 10 && u <= 12 && (v == 7 || v == 8)) {
            b.set(x, 0, z, STONE_BRICKS);
            b.column(x, 1, 3, z, AIR);
            b.set(x, 4, z, STONE_SLAB_TOP);
        }
        // a lantern under the vault (the room is found by its light)
        if (u == 5 && v == 5) {
            b.set(x, 5, z, CHAIN_Y);
            b.set(x, 4, z, LANTERN_HANGING);
        }
    }

    private static BlockState weatheredStone(Ctx c, int x, int ry, int z) {
        int h = c.pct(x, z, 410 + ry);
        return h < 15 ? MOSSY_STONE_BRICKS : h < 25 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
    }

    /** The stairwell's parapet: joined along its two sides and round its south end. */
    private static int wellMask(int u, int v) {
        int m = 0;
        if ((u == 12 || u == 15) && v > 0) m |= 1;
        if ((u == 12 || u == 15) && v < 9) m |= 4;
        if (v == 9 && u <= 14) m |= 2;
        if (v == 9 && u >= 13) m |= 8;
        return m;
    }

    // ---- walkways over the avenues (M4 B2; look bible §3.5) ---------------------------------------------------

    /**
     * An enclosed walkway between two facing upper floors across an avenue: its floor a storey under the lower house's
     * eave, walls of the district's frame with small windows (blind running faces along it), its roof a road level with
     * that eave, carried on brackets against both fronts. The box runs across the avenue ({@code axis}), three wide.
     */
    static void walkway(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        boolean alongX = p.axis().equals("x");
        int along = alongX ? x - box.x0() : z - box.z0();
        int len = alongX ? box.w() : box.d();
        int across = alongX ? z - box.z0() : x - box.x0();
        boolean side = across == 0 || across == 2;
        int deck = p.h(), floor = deck - 4;
        String st = p.style();
        boolean timber = st.equals("bellwatch") || st.equals("quay") || st.equals("wallring");
        BlockState wall = switch (st) {
            case "bellwatch", "wallring", "garden" -> PLASTER[P_WHITE];
            case "quay" -> SPRUCE_PLANKS;
            case "market" -> PLASTER[P_CREAM];
            case "terrace" -> CALCITE;
            default -> CITY_BRICKS;
        };
        BlockState frame = timber ? T_POST[0] : st.equals("terrace") ? POLISHED_DIORITE : st.equals("market") ? CUT_SANDSTONE : ASHLAR;
        BlockState beam = alongX ? T_BEAM_X[0] : T_BEAM_Z[0];
        // (at its ends it meets the houses: never inside a jettied upper storey, and a drainpipe in front of a house runs
        // on up past it)
        int toward = along <= 1 ? -1 : along >= len - 2 ? 1 : 0;
        if (toward != 0 && houseEnd(c, x, z, alongX ? toward : 0, alongX ? 0 : toward)) return;
        BlockState roof = switch (st) {
            case "bellwatch", "wallring" -> SLATE_KIT.block();
            case "quay" -> SPRUCE.block();
            case "market" -> CLAY.block();
            case "terrace" -> PALE.block();
            case "garden" -> GREEN.block();
            default -> BRICK.block();
        };
        b.set(x, floor, z, timber ? beam : STONE_SLAB_TOP);
        for (int ry = floor + 1; ry < deck; ry++) {
            BlockState s;
            if (!side) s = AIR;
            else if (along == 0 || along == len - 1 || along % 3 == 0) s = frame;
            else if (ry == floor + 2) s = WINDOW[timber ? W_LEADED : W_SASH][2];
            else s = wall;
            b.set(x, ry, z, s);
        }
        b.set(x, deck, z, side ? (timber ? beam : frame) : roof);
        // brackets under its ends, against the fronts (where the walkway starts: its end cell, or the next when a jetty took it)
        int ax = alongX ? toward : 0, az = alongX ? 0 : toward;
        boolean end = along == 0 || along == len - 1 || (toward != 0 && houseEnd(c, x + ax, z + az, ax, az));
        if (side && end && toward != 0) {
            int facing = alongX ? (toward < 0 ? W : E) : (toward < 0 ? N : S);
            b.set(x, floor - 1, z, (timber ? CORBEL : STONE_CORBEL)[facing]);
        }
    }

    /**
     * Whether (x, z) belongs to the house a step or two beyond it in direction (dx, dz): inside its upper storeys (a
     * jetty over the street), or the cell of a climb line in front of them.
     */
    static boolean houseEnd(Ctx c, int x, int z, int dx, int dz) {
        for (int k = 1; k <= 2; k++) {
            int id = c.plan.plotAt(x + dx * k, z + dz * k);
            if (id < 0) continue;
            CityGrammar.Facade f = CityGrammar.facadeOf(c, c.plan.plots.get(id));
            if (f.up.contains(x, z)) return true;
            int sd = f.up.outside(x, z);
            if (sd < 0) return false;
            int t = f.up.along(sd, x, z);
            return t >= 0 && t < f.climb[sd].length && f.climb[sd][t];
        }
        return false;
    }

    // ---- bridges ----------------------------------------------------------------------------------------------

    /**
     * A stone bridge part: a deck at street level over the water, an arch under it (low at the banks, highest over
     * the middle), parapets on both edges and lamps at the ends. The Bell Bridge is one the opening breaks (its parts
     * are marked breakable in the plan).
     */
    static void bridge(Ctx c, CityBuffer b, Part p, Box box, int x, int z) {
        if (!box.contains(x, z)) return;
        boolean alongX = p.axis().equals("x");
        CityPlan plan = c.plan;
        Bridge whole = null;
        for (Bridge br : plan.bridges) {
            if (x >= br.x0() && x <= br.x1() && z >= br.z0() && z <= br.z1()) {
                whole = br;
                break;
            }
        }
        if (whole == null) return;
        int along = alongX ? x - whole.x0() : z - whole.z0();
        int length = alongX ? whole.x1() - whole.x0() + 1 : whole.z1() - whole.z0() + 1;
        int across = alongX ? z - whole.z0() : x - whole.x0();
        int width = alongX ? whole.z1() - whole.z0() + 1 : whole.x1() - whole.x0() + 1;
        boolean edge = across == 0 || across == width - 1;
        byte under = plan.kind(x, z);
        boolean bell = whole.breakable();
        // the arch's underside: a half ellipse from the waterline at the ends to near the deck in the middle
        double u = (along + 0.5) / length * 2 - 1;
        int underside = CityPainter.WATERLINE + (int) Math.round(Math.sqrt(Math.max(0, 1 - u * u)) * (-1 - CityPainter.WATERLINE));
        if (water(under)) {
            for (int ry = underside; ry < 0; ry++) b.set(x, ry, z, ry == underside ? STONE_SLAB_TOP : (bell ? masonry(c, x, ry, z) : STONE_BRICKS));
        }
        b.set(x, 0, z, edge ? POLISHED_ANDESITE : (bell ? (across == width / 2 ? BRONZE : STONE_BRICKS) : STONE_BRICKS));
        b.column(x, 1, 6, z, AIR);
        if (edge) {
            b.set(x, 1, z, alongX ? WALL_RUN[0] : WALL_RUN[1]);
            boolean end = along == 0 || along == length - 1;
            if (end || (bell && along == length / 2)) {
                b.set(x, 1, z, WALL_POST);
                b.set(x, 2, z, WALL_POST);
                b.set(x, 3, z, bell ? COPPER_LANTERN : LANTERN);
            }
        }
        // the Bell Bridge carries a small bell arch over its middle
        if (bell && along == length / 2) {
            if (edge) b.column(x, 1, 6, z, POLISHED_ANDESITE);
            b.set(x, 7, z, BRONZE);
            if (across == width / 2) {
                b.set(x, 6, z, CHAIN_Y);
                b.set(x, 5, z, BELL);
            }
        }
    }
}
