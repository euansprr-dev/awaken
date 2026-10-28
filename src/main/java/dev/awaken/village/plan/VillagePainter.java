package dev.awaken.village.plan;

import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint.Canvas;
import dev.awaken.city.VillageStyles;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.village.plan.VillageBlocks.*;

/**
 * A settlement's own painting (SPEC §28.6), around what the city's grammar paints: its ground (streets that follow
 * the land, the green's paving, yards, fields with their channels, pens, the pier), then after the houses its pieces
 * (lamp posts, the bell on its post or in its tower, the smithy's forge shed, the mill's wheel). Pure and clipped to
 * the canvas: a chunk painted alone equals the same chunk painted with its neighbours.
 */
final class VillagePainter {
    private VillagePainter() {}

    /** How far over the ground a settlement clears (trees and bumps) on the ground it uses. */
    static final int CLEAR = 12, CLEAR_PLOT = 26;

    static void paint(VillagePlan p, Canvas c, boolean before) {
        if (before) {
            for (int z = c.z0(); z <= c.z1(); z++) for (int x = c.x0(); x <= c.x1(); x++) ground(p, c, x, z);
        } else {
            for (VillagePlan.Lamp l : p.lamps) lamp(p, c, l);
            // (SPEC §28.9) a town's towers and gates, and its bell tower on the square
            for (int[] t : p.towers) TownPainter.tower(p, c, t);
            for (int[] gt : p.gates) TownPainter.gate(p, c, gt);
            if (p.mine != null) MinePainter.head(p, c, p.mine);
            if (p.bell != null && p.bell.x1() - p.bell.x0() >= 4) TownPainter.bellTower(p, c, p.bell);
            else if (p.bell != null) bell(p, c, p.bell);
            if (p.forge != null) forge(p, c, p.forge);
            if (p.wheel != null) wheel(p, c, p.wheel);
            for (VillagePlan.Pen pen : p.pens) penFence(p, c, pen);
            for (int[] t : p.trees) tree(p, c, t);
            if (p.dock != null) dockRail(p, c, p.dock);
        }
    }

    // ---- ground ---------------------------------------------------------------------------------------------------

    static void ground(VillagePlan p, Canvas c, int x, int z) {
        byte k = p.kind(x, z);
        if (k == CityPlan.VOID || k == CityPlan.FREE || k == VillagePlan.WATER) return;
        int g = p.lift(x, z);
        int fam = p.family;
        switch (k) {
            case CityPlan.STREET, CityPlan.LANE -> {
                bed(c, x, g, z, fam);
                BlockState pave = paving(p, c, x, z, k == CityPlan.STREET);
                c.set(x, g, z, pave);
                clear(c, x, g, z, CLEAR);
                halfStep(p, c, x, g, z, pave);
            }
            case CityPlan.SQUARE, CityPlan.LANDMARK -> {
                bed(c, x, g, z, fam);
                BlockState pave = green(p, c, x, z);
                c.set(x, g, z, pave);
                clear(c, x, g, z, CLEAR);
                halfStep(p, c, x, g, z, pave);
            }
            case CityPlan.YARD, CityPlan.GARDEN -> {
                bed(c, x, g, z, fam);
                c.set(x, g, z, yard(fam, c, x, z));
                clear(c, x, g, z, CLEAR);
                if (k == CityPlan.YARD && !nextToWall(p, x, z)) {
                    int roll = c.pct(x, z, 703);
                    if (roll < growth(fam)) c.set(x, g + 1, z, roll < 4 && flowers(fam) ? FLOWERS[c.pct(x, z, 704) % FLOWERS.length]
                            : fam == VillageStyles.FROST || fam == VillageStyles.ELDER ? FERN : SHORT_GRASS);
                }
            }
            case CityPlan.PLOT -> {
                // the foundation: rubble under the floor, down to the land (the writer fills a dip below it)
                for (int ry = g - 3; ry < g; ry++) c.set(x, ry, z, fam == VillageStyles.DESERT ? SANDSTONE : fam == VillageStyles.MIRE ? MUD_BRICKS : COBBLE);
                c.plate(x, z);
                clear(c, x, g, z, CLEAR_PLOT);
            }
            case VillagePlan.FIELD -> field(p, c, x, z, g);
            case VillagePlan.PEN -> {
                bed(c, x, g, z, fam);
                c.set(x, g, z, c.pct(x, z, 711) < 30 ? COARSE_DIRT : fam == VillageStyles.DESERT ? SAND : GRASS);
                clear(c, x, g, z, CLEAR);
            }
            case VillagePlan.DOCK -> dock(p, c, x, z, g);
            case CityPlan.WALL -> TownPainter.wall(p, c, x, z, g);
            case VillagePlan.KITCHEN -> kitchen(p, c, x, z, g);
            default -> {
            }
        }
    }

    /**
     * Where the paving climbs a block to the next cell of street or green, the lower cell takes a slab of its own stone:
     * a half step up, never a cube of cobble with earth showing at its sides.
     */
    static void halfStep(VillagePlan p, Canvas c, int x, int g, int z, BlockState pave) {
        boolean up = false;
        for (int s = 0; s < 4 && !up; s++) {
            int nx = x + (s == 0 ? 1 : s == 1 ? -1 : 0), nz = z + (s == 2 ? 1 : s == 3 ? -1 : 0);
            byte k = p.kind(nx, nz);
            up = (k == CityPlan.STREET || k == CityPlan.LANE || k == CityPlan.SQUARE || k == CityPlan.LANDMARK) && p.lift(nx, nz) == g + 1;
        }
        if (up) c.set(x, g + 1, z, stepOf(pave, p.family));
    }

    static BlockState stepOf(BlockState pave, int fam) {
        if (pave == MOSSY_COBBLE) return MOSSY_COBBLE_SLAB;
        if (pave == ANDESITE) return ANDESITE_SLAB;
        if (pave == POLISHED_ANDESITE) return POLISHED_ANDESITE_SLAB;
        if (pave == STONE_BRICKS || pave == CRACKED_STONE_BRICKS) return STONE_SLAB;
        if (pave == SMOOTH_SANDSTONE) return SMOOTH_SANDSTONE_SLAB;
        if (pave == CUT_SANDSTONE) return CUT_SANDSTONE_SLAB;
        if (pave == MUD_BRICKS || pave == PACKED_MUD || fam == VillageStyles.MIRE || fam == VillageStyles.JUNGLE) return MUD_BRICK_SLAB;
        if (pave == SANDSTONE || pave == SAND || fam == VillageStyles.DESERT) return SANDSTONE_SLAB;
        return COBBLE_SLAB;
    }

    /** What lies under a surface: earth (or sand) three deep, then the writer's fill down to the land. */
    static void bed(Canvas c, int x, int g, int z, int fam) {
        BlockState under = fam == VillageStyles.DESERT ? SANDSTONE : DIRT;
        for (int ry = g - 3; ry < g; ry++) c.set(x, ry, z, under);
        c.plate(x, z);
    }

    static void clear(Canvas c, int x, int g, int z, int h) {
        c.column(x, g + 1, g + h, z, AIR);
    }

    static boolean nextToWall(VillagePlan p, int x, int z) {
        return p.kind(x + 1, z) == CityPlan.PLOT || p.kind(x - 1, z) == CityPlan.PLOT || p.kind(x, z + 1) == CityPlan.PLOT || p.kind(x, z - 1) == CityPlan.PLOT;
    }

    static int growth(int fam) {
        return switch (fam) {
            case VillageStyles.DESERT -> 2;
            case VillageStyles.FROST -> 6;
            case VillageStyles.JUNGLE, VillageStyles.MIRE -> 30;
            default -> 18;
        };
    }

    static boolean flowers(int fam) {
        return fam != VillageStyles.DESERT && fam != VillageStyles.FROST;
    }

    /** A street's paving: the main street cobbled near the green and worn to gravel and earth further out, lanes trodden earth. */
    static BlockState paving(VillagePlan p, Canvas c, int x, int z, boolean main) {
        int roll = c.pct(x, z, 701);
        double d = Math.hypot(x, z);
        boolean village = p.big();
        return switch (p.family) {
            case VillageStyles.DESERT -> main && d < 40 ? (roll < 55 ? SMOOTH_SANDSTONE : roll < 85 ? CUT_SANDSTONE : SANDSTONE) : roll < 60 ? SAND : roll < 85 ? SANDSTONE : COARSE_DIRT;
            case VillageStyles.MIRE, VillageStyles.JUNGLE -> main && village ? (roll < 50 ? MUD_BRICKS : roll < 80 ? PACKED_MUD : PATH) : roll < 70 ? PATH : PACKED_MUD;
            case VillageStyles.FROST -> main ? (roll < 45 ? GRAVEL : roll < 75 ? COBBLE : COARSE_DIRT) : roll < 55 ? PATH : roll < 80 ? GRAVEL : COARSE_DIRT;
            case VillageStyles.HIGHLAND -> main ? (roll < 50 ? COBBLE : roll < 75 ? GRAVEL : ANDESITE) : roll < 60 ? GRAVEL : PATH;
            default -> {
                if (main && village && d < 34 || main && d < 16) {
                    yield roll < 60 ? COBBLE : roll < 75 ? MOSSY_COBBLE : roll < 90 ? ANDESITE : GRAVEL;
                }
                if (main) yield roll < 55 ? PATH : roll < 80 ? GRAVEL : roll < 92 ? COARSE_DIRT : COBBLE;
                yield roll < 78 ? PATH : roll < 92 ? COARSE_DIRT : GRAVEL;
            }
        };
    }

    static BlockState green(VillagePlan p, Canvas c, int x, int z) {
        int roll = c.pct(x, z, 702);
        return switch (p.family) {
            case VillageStyles.DESERT -> roll < 50 ? SMOOTH_SANDSTONE : roll < 80 ? CUT_SANDSTONE : TERRACOTTA;
            case VillageStyles.MIRE, VillageStyles.JUNGLE -> roll < 60 ? MUD_BRICKS : roll < 85 ? PACKED_MUD : MOSSY_COBBLE;
            default -> roll < 45 ? COBBLE : roll < 65 ? STONE_BRICKS : roll < 78 ? MOSSY_COBBLE : roll < 90 ? POLISHED_ANDESITE : CRACKED_STONE_BRICKS;
        };
    }

    static BlockState yard(int fam, Canvas c, int x, int z) {
        int roll = c.pct(x, z, 705);
        return switch (fam) {
            case VillageStyles.DESERT -> roll < 80 ? SAND : COARSE_DIRT;
            case VillageStyles.FROST -> roll < 25 ? PODZOL : roll < 40 ? COARSE_DIRT : GRASS;
            case VillageStyles.SAVANNA -> roll < 30 ? COARSE_DIRT : GRASS;
            case VillageStyles.MIRE -> roll < 25 ? MUD : roll < 35 ? MOSS : GRASS;
            case VillageStyles.ELDER -> roll < 30 ? PODZOL : GRASS;
            default -> roll < 8 ? COARSE_DIRT : GRASS;
        };
    }

    // ---- fields -----------------------------------------------------------------------------------------------------

    static void field(VillagePlan p, Canvas c, int x, int z, int g) {
        VillagePlan.Field f = null;
        for (VillagePlan.Field ff : p.fields) if (x >= ff.x0() && x <= ff.x1() && z >= ff.z0() && z <= ff.z1()) f = ff;
        bed(c, x, g, z, p.family);
        clear(c, x, g, z, CLEAR);
        if (f == null) {
            c.set(x, g, z, GRASS);
            return;
        }
        // rows run along x or z; a channel down the middle row keeps every row watered (four either side)
        int row = f.rowsAlongX() ? z - f.z0() : x - f.x0();
        int rows = f.rowsAlongX() ? f.z1() - f.z0() + 1 : f.x1() - f.x0() + 1;
        int along = f.rowsAlongX() ? x - f.x0() : z - f.z0();
        int alongLen = f.rowsAlongX() ? f.x1() - f.x0() + 1 : f.z1() - f.z0() + 1;
        int mid = rows / 2;
        boolean channel = row == mid || rows > 9 && (row == mid - 5 || row == mid + 5);
        boolean edge = along == 0 || along == alongLen - 1;
        if (channel) {
            if (edge) {
                c.set(x, g, z, COBBLE);
                c.set(x, g + 1, z, COMPOSTER);
            } else {
                c.set(x, g - 1, z, DIRT);
                c.set(x, g, z, WATER);
            }
            return;
        }
        switch (f.crop()) {
            case "melons", "pumpkins" -> {
                // a row of fruit beside a row of vines reaching for it
                // pairs of rows away from the channel: the vine's row, then the fruit's row beside it
                boolean melon = f.crop().equals("melons");
                int off = row < mid ? mid - 1 - row : row - mid - 1;
                boolean fruit = (off & 1) == 1;
                if (fruit) {
                    c.set(x, g, z, DIRT);
                    c.set(x, g + 1, z, melon ? MELON : PUMPKIN);
                } else {
                    c.set(x, g, z, FARMLAND);
                    // away from the channel, toward the fruit row
                    int toward = f.rowsAlongX() ? (row < mid ? 0 : 2) : (row < mid ? 3 : 1);
                    c.set(x, g + 1, z, (melon ? MELON_STEM : PUMPKIN_STEM)[toward]);
                }
            }
            default -> {
                c.set(x, g, z, FARMLAND);
                int v = c.pct(x, z, 713);
                BlockState crop = switch (f.crop()) {
                    case "carrots" -> CARROTS[v < 75 ? 0 : 1];
                    case "potatoes" -> POTATOES[v < 75 ? 0 : 1];
                    case "beetroots" -> BEETROOTS[v < 75 ? 0 : 1];
                    default -> CROP[v < 60 ? 0 : v < 85 ? 1 : 2];
                };
                c.set(x, g + 1, z, crop);
            }
        }
    }

    /** A kitchen garden: a fence round it with its gate toward the house, vegetables in rows, a water hole in the middle. */
    static void kitchen(VillagePlan p, Canvas c, int x, int z, int g) {
        VillagePlan.Garden gd = null;
        for (VillagePlan.Garden k : p.gardens) if (x >= k.x0() && x <= k.x1() && z >= k.z0() && z <= k.z1()) gd = k;
        bed(c, x, g, z, p.family);
        clear(c, x, g, z, CLEAR);
        if (gd == null) {
            c.set(x, g, z, GRASS);
            return;
        }
        boolean edge = x == gd.x0() || x == gd.x1() || z == gd.z0() || z == gd.z1();
        VillageBlocks.Wood w = wood(p.family);
        if (edge) {
            c.set(x, g, z, c.pct(x, z, 740) < 30 ? COARSE_DIRT : GRASS);
            if (x == gd.gateX() && z == gd.gateZ()) {
                c.set(x, g + 1, z, w.gate()[gd.gateSide()]);
                return;
            }
            int m = 0;
            if (z > gd.z0() && (x == gd.x0() || x == gd.x1())) m |= 1;
            if (x < gd.x1() && (z == gd.z0() || z == gd.z1())) m |= 2;
            if (z < gd.z1() && (x == gd.x0() || x == gd.x1())) m |= 4;
            if (x > gd.x0() && (z == gd.z0() || z == gd.z1())) m |= 8;
            c.set(x, g + 1, z, w.fence()[m]);
            return;
        }
        int mx = (gd.x0() + gd.x1()) / 2, mz = (gd.z0() + gd.z1()) / 2;
        boolean bigEnough = gd.x1() - gd.x0() >= 4 && gd.z1() - gd.z0() >= 4;
        if (bigEnough && x == mx && z == mz) {
            c.set(x, g - 1, z, DIRT);
            c.set(x, g, z, WATER);
            return;
        }
        c.set(x, g, z, FARMLAND);
        int v = c.pct(x, z, 741);
        BlockState crop = switch (gd.crop()) {
            case "carrots" -> CARROTS[v < 70 ? 0 : 1];
            case "potatoes" -> POTATOES[v < 70 ? 0 : 1];
            case "beetroots" -> BEETROOTS[v < 70 ? 0 : 1];
            case "melons", "pumpkins" -> CARROTS[0];
            default -> CROP[v < 50 ? 0 : 1];
        };
        c.set(x, g + 1, z, crop);
    }

    /** A tree of the land's wood: a bark trunk and a rounded crown of kept leaves (a spruce's a cone). */
    static void tree(VillagePlan p, Canvas c, int[] t) {
        int x = t[0], z = t[1], g = t[2], seed = t[3];
        if (Math.abs(x - c.x0() - 8) > 12 || Math.abs(z - c.z0() - 8) > 12) return;
        String wood = switch (p.family) {
            case dev.awaken.city.VillageStyles.FROST, dev.awaken.city.VillageStyles.HIGHLAND -> "spruce";
            case dev.awaken.city.VillageStyles.SAVANNA, dev.awaken.city.VillageStyles.DESERT -> "acacia";
            case dev.awaken.city.VillageStyles.MIRE -> "mangrove";
            case dev.awaken.city.VillageStyles.JUNGLE -> "jungle";
            case dev.awaken.city.VillageStyles.CHERRY -> "cherry";
            case dev.awaken.city.VillageStyles.ELDER -> "dark_oak";
            case dev.awaken.city.VillageStyles.COAST -> seed % 2 == 0 ? "birch" : "oak";
            default -> seed % 4 == 0 ? "birch" : "oak";
        };
        BlockState trunk = VillageBlocks.of("minecraft:" + wood + "_wood[axis=y]");
        BlockState leaf = VillageBlocks.of("minecraft:" + (wood.equals("mangrove") ? "mangrove" : wood) + "_leaves[persistent=true]");
        int h = 4 + seed % 3;
        c.column(x, g + 1, g + h, z, trunk);
        if (wood.equals("spruce")) {
            for (int dy = 2; dy <= h + 2; dy++) {
                int rad = Math.max(0, (h + 2 - dy) / 2 + (dy < h ? 1 : 0));
                for (int dz = -rad; dz <= rad; dz++)
                    for (int dx = -rad; dx <= rad; dx++) {
                        if (dx == 0 && dz == 0 && dy <= h) continue;
                        if (Math.abs(dx) + Math.abs(dz) > rad + (rad > 1 ? 1 : 0)) continue;
                        c.air(x + dx, g + dy, z + dz, leaf);
                    }
            }
            c.air(x, g + h + 3, z, leaf);
            return;
        }
        int top = g + h;
        for (int dy = -2; dy <= 2; dy++) {
            int rad = dy <= -1 ? 3 : dy == 2 ? 1 : 2;
            for (int dz = -rad; dz <= rad; dz++)
                for (int dx = -rad; dx <= rad; dx++) {
                    double d = Math.hypot(dx, dz);
                    if (d > rad + 0.4) continue;
                    if (d > rad - 0.6 && c.pct(x + dx, z + dz, 742 + dy) < 35) continue; // a ragged edge
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    c.air(x + dx, top + dy, z + dz, leaf);
                }
        }
    }

    // ---- pens and the dock ------------------------------------------------------------------------------------------

    static VillageBlocks.Wood wood(int fam) {
        return switch (fam) {
            case VillageStyles.FROST, VillageStyles.HIGHLAND -> SPRUCE;
            case VillageStyles.DESERT, VillageStyles.SAVANNA -> ACACIA;
            case VillageStyles.MIRE -> MANGROVE;
            case VillageStyles.ELDER -> DARK_OAK;
            case VillageStyles.JUNGLE -> JUNGLE;
            case VillageStyles.CHERRY -> CHERRY;
            case VillageStyles.COAST -> SPRUCE;
            default -> OAK;
        };
    }

    static void penFence(VillagePlan p, Canvas c, VillagePlan.Pen pen) {
        VillageBlocks.Wood w = wood(p.family);
        int g = pen.lift();
        for (int z = pen.z0(); z <= pen.z1(); z++) {
            for (int x = pen.x0(); x <= pen.x1(); x++) {
                if (!c.contains(x, z)) continue;
                boolean edge = x == pen.x0() || x == pen.x1() || z == pen.z0() || z == pen.z1();
                if (!edge) continue;
                if (x == pen.gateX() && z == pen.gateZ()) {
                    c.set(x, g + 1, z, w.gate()[pen.gateSide()]);
                    continue;
                }
                int m = 0;
                if (z > pen.z0() && (x == pen.x0() || x == pen.x1())) m |= 1;
                if (x < pen.x1() && (z == pen.z0() || z == pen.z1())) m |= 2;
                if (z < pen.z1() && (x == pen.x0() || x == pen.x1())) m |= 4;
                if (x > pen.x0() && (z == pen.z0() || z == pen.z1())) m |= 8;
                c.set(x, g + 1, z, w.fence()[m]);
            }
        }
        // a trough and a bale in the corner away from the gate
        int tx = pen.gateX() <= (pen.x0() + pen.x1()) / 2 ? pen.x1() - 1 : pen.x0() + 1, tz = pen.gateZ() <= (pen.z0() + pen.z1()) / 2 ? pen.z1() - 1 : pen.z0() + 1;
        c.set(tx, g + 1, tz, CAULDRON);
        int hx = tx == pen.x1() - 1 ? tx - 1 : tx + 1;
        c.set(hx, g + 1, tz, HAY);
    }

    static void dock(VillagePlan p, Canvas c, int x, int z, int g) {
        VillagePlan.Dock d = p.dock;
        if (d == null) return;
        VillageBlocks.Wood w = wood(p.family);
        c.set(x, g, z, w.planks());
        clear(c, x, g, z, 6);
        // posts under the deck's edges every other block, down into the water
        boolean edgeX = x == d.x0() || x == d.x1(), edgeZ = z == d.z0() || z == d.z1();
        boolean alongX = d.side() == 1 || d.side() == 3;
        boolean edge = alongX ? edgeZ : edgeX;
        int along = alongX ? x : z;
        if (edge && (along & 1) == 0) {
            c.column(x, g - 10, g - 1, z, w.post());
        }
    }

    static void dockRail(VillagePlan p, Canvas c, VillagePlan.Dock d) {
        VillageBlocks.Wood w = wood(p.family);
        boolean alongX = d.side() == 1 || d.side() == 3;
        // a rail on both long sides, open at the far end and the bank; a ladder down at the end
        for (int z = d.z0(); z <= d.z1(); z++) {
            for (int x = d.x0(); x <= d.x1(); x++) {
                boolean side = alongX ? z == d.z0() || z == d.z1() : x == d.x0() || x == d.x1();
                int along = alongX ? x : z;
                int endA = alongX ? (d.side() == 1 ? d.x1() : d.x0()) : (d.side() == 2 ? d.z1() : d.z0());
                if (!side || Math.abs(along - endA) <= 1) continue;
                int m = alongX ? 2 | 8 : 1 | 4;
                if ((along & 3) == 0) c.set(x, d.lift() + 1, z, w.fence()[m]);
            }
        }
    }

    // ---- pieces -----------------------------------------------------------------------------------------------------

    /** A lamp post: a stone foot, a timber post and the street lamp's head four over the ground (lit by the lamplighter). */
    static void lamp(VillagePlan p, Canvas c, VillagePlan.Lamp l) {
        if (!c.contains(l.x(), l.z())) return;
        int g = l.lift();
        VillageBlocks.Wood w = wood(p.family);
        c.set(l.x(), g + 1, l.z(), STONE_WALL_POST);
        c.column(l.x(), g + 2, g + 3, l.z(), w.fence()[0]);
        c.set(l.x(), g + 4, l.z(), STREET_LAMP);
    }

    /** The bell: on a hamlet's green a frame of two posts and a beam; a village's a stone tower with an open belfry. */
    static void bell(VillagePlan p, Canvas c, VillagePlan.Bell b) {
        VillageBlocks.Wood w = wood(p.family);
        int g = b.lift();
        if (!b.tower()) {
            boolean alongX = b.x1() > b.x0();
            for (int z = b.z0(); z <= b.z1(); z++) {
                for (int x = b.x0(); x <= b.x1(); x++) {
                    boolean end = alongX ? x == b.x0() || x == b.x1() : z == b.z0() || z == b.z1();
                    if (end) {
                        c.set(x, g + 1, z, COBBLE);
                        c.column(x, g + 2, g + 4, z, w.post());
                    }
                    c.set(x, g + 5, z, alongX ? w.beamX() : w.beamZ());
                    c.set(x, g + 6, z, w.slab());
                }
            }
            c.set(b.bx(), g + 4, b.bz(), CHAIN);
            c.set(b.bx(), g + b.bry(), b.bz(), alongX ? BELL_CEILING_X : BELL_CEILING_Z);
            return;
        }
        // the tower: rubble walls to the belfry, corner posts round the bell, a pyramid roof and a rod
        BlockState wall = p.family == VillageStyles.DESERT ? SANDSTONE : STONE_BRICKS;
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int x = b.x0(); x <= b.x1(); x++) {
                boolean corner = (x == b.x0() || x == b.x1()) && (z == b.z0() || z == b.z1());
                boolean mid = x == b.bx() && z == b.bz();
                for (int ry = 1; ry <= 6; ry++) {
                    BlockState s = corner ? (ry % 2 == 1 ? POLISHED_ANDESITE : wall) : mid ? AIR : c.pct(x, z, 720 + ry) < 15 ? MOSSY_STONE_BRICKS : wall;
                    c.set(x, g + ry, z, s);
                }
                c.set(x, g + 7, z, mid ? AIR : STONE_SLAB);
                if (corner) c.column(x, g + 7, g + 9, z, w.post());
                c.set(x, g + 10, z, w.planks());
            }
        }
        // a ladder up the shaft inside (the ringer's way up; the bell rope is the story)
        int lx = b.bx(), lz = b.bz();
        c.column(lx, g + 1, g + 6, lz, LADDER[2]);
        c.set(lx, g + 7, lz, AIR);
        c.set(lx, g + 9, lz, CHAIN);
        c.set(b.bx(), g + b.bry(), b.bz(), BELL_CEILING_X);
        // the roof: a pyramid of stairs and a lightning rod
        for (int z = b.z0() - 1; z <= b.z1() + 1; z++) {
            for (int x = b.x0() - 1; x <= b.x1() + 1; x++) {
                boolean ring = x < b.x0() || x > b.x1() || z < b.z0() || z > b.z1();
                if (!ring) continue;
                int face = z < b.z0() ? 2 : z > b.z1() ? 0 : x < b.x0() ? 1 : 3;
                c.set(x, g + 10, z, w.stair()[face]);
            }
        }
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int x = b.x0(); x <= b.x1(); x++) {
                boolean mid = x == b.bx() && z == b.bz();
                int face = z == b.z0() ? 2 : z == b.z1() ? 0 : x == b.x0() ? 1 : 3;
                c.set(x, g + 11, z, mid ? w.planks() : w.stair()[face]);
            }
        }
        c.set(b.bx(), g + 12, b.bz(), LIGHTNING_ROD);
    }

    /** The forge: four posts and a slab roof against the smithy, a cobbled floor, the anvil, a lit blast furnace by the wall. */
    static void forge(VillagePlan p, Canvas c, VillagePlan.Forge f) {
        VillageBlocks.Wood w = wood(p.family);
        int g = f.lift();
        CityPlan.Plot smithy = p.plot(f.plot());
        for (int z = f.z0(); z <= f.z1(); z++) {
            for (int x = f.x0(); x <= f.x1(); x++) {
                c.set(x, g, z, c.pct(x, z, 730) < 30 ? COBBLE : STONE_BRICKS);
                boolean corner = (x == f.x0() || x == f.x1()) && (z == f.z0() || z == f.z1());
                boolean wallSide = touches(smithy, x, z);
                if (corner && !wallSide) c.column(x, g + 1, g + 3, z, w.post());
                c.set(x, g + 4, z, w.slab());
            }
        }
        int face = towardSmithy(smithy, f.anvilX(), f.anvilZ());
        c.set(f.anvilX(), g + 1, f.anvilZ(), ANVIL_FACING[(face + 1) & 3]);
        // the furnace against the smithy's wall, at the shed's far end from the anvil
        int[] fu = furnace(smithy, f.x0(), f.z0(), f.x1(), f.z1(), f.anvilX(), f.anvilZ());
        c.set(fu[0], g + 1, fu[1], BLAST_FURNACE[(face + 2) & 3]);
        c.set(fu[0], g + 2, fu[1], COBBLE_SLAB);
        // the Hearth's forge beside the anvil: an Armoury door where a hunter draws patterns, raises weapons and tempers gear
        int[] fo = forgeBlock(f.x0(), f.z0(), f.x1(), f.z1(), f.anvilX(), f.anvilZ(), fu);
        if (fo != null) c.set(fo[0], g + 1, fo[1], FORGE[(face + 2) & 3]);
    }

    /** The forge shed's furnace cell: against the smithy's wall, at the shed's far end from the anvil. */
    static int[] furnace(CityPlan.Plot smithy, int x0, int z0, int x1, int z1, int ax, int az) {
        int bx = x0, bz = z0;
        for (int z = z0; z <= z1; z++)
            for (int x = x0; x <= x1; x++)
                if (touches(smithy, x, z) && Math.abs(x - ax) + Math.abs(z - az) >= 2) {
                    bx = x;
                    bz = z;
                }
        return new int[]{bx, bz};
    }

    /** The Hearth forge's cell: the first side of the anvil inside the shed that is no corner and not the furnace. */
    static int @org.jspecify.annotations.Nullable [] forgeBlock(int x0, int z0, int x1, int z1, int ax, int az, int[] furnace) {
        int[][] beside = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : beside) {
            int fx = ax + d[0], fz = az + d[1];
            boolean corner = (fx == x0 || fx == x1) && (fz == z0 || fz == z1);
            if (fx < x0 || fx > x1 || fz < z0 || fz > z1 || corner || (fx == furnace[0] && fz == furnace[1])) continue;
            return new int[]{fx, fz};
        }
        return null;
    }

    static boolean touches(CityPlan.Plot q, int x, int z) {
        return (x == q.x0() - 1 || x == q.x1() + 1) && z >= q.z0() && z <= q.z1() || (z == q.z0() - 1 || z == q.z1() + 1) && x >= q.x0() && x <= q.x1();
    }

    static int towardSmithy(CityPlan.Plot q, int x, int z) {
        if (x < q.x0()) return 1;
        if (x > q.x1()) return 3;
        if (z < q.z0()) return 2;
        return 0;
    }

    /** The mill's wheel: a ring of planks round a hub on an axle from the wall, its lower third in the water. */
    static void wheel(VillagePlan p, Canvas c, VillagePlan.Wheel wh) {
        VillageBlocks.Wood w = wood(p.family);
        boolean planeX = wh.side() == 0 || wh.side() == 2; // the wheel's plane runs along x on a north or south wall
        int g = wh.axle();
        for (int a = -3; a <= 3; a++) {
            for (int dy = -3; dy <= 3; dy++) {
                double d = Math.hypot(a, dy);
                int x = planeX ? wh.cx() + a : wh.cx(), z = planeX ? wh.cz() : wh.cz() + a;
                if (d >= 2.5 && d <= 3.6) c.set(x, g + dy, z, w.planks());
                else if (a == 0 || dy == 0) c.set(x, g + dy, z, d < 0.5 ? w.post() : planeX ? (a == 0 ? w.post() : w.beamX()) : (a == 0 ? w.post() : w.beamZ()));
                else if (d < 2.5) c.set(x, g + dy, z, AIR);
            }
        }
        // the axle back to the wall
        int sx = wh.side() == 1 ? -1 : wh.side() == 3 ? 1 : 0, sz = wh.side() == 2 ? -1 : wh.side() == 0 ? 1 : 0;
        c.set(wh.cx() + sx, g, wh.cz() + sz, sx != 0 ? w.beamX() : w.beamZ());
    }
}
