package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.world.Pieces.*;

/**
 * The second small set piece of every theme, for single rooms (12 x 12): the doorway axes cross the middle, so these
 * dress the four corners (|dx|, |dz| from 3.5 to 5.5) and the space overhead. Called through {@link PiecesRooms}.
 */
final class PiecesSmall {
    private PiecesSmall() {}

    /** In a corner quarter (outside the doorway axes): which corner footprint cell (0 = none, 1 inner, 2 outer, 3 edge). */
    static boolean corner2x2(double dx, double dz) {
        return Math.abs(Math.abs(dx) - 4.0) < 1.0 && Math.abs(Math.abs(dz) - 4.0) < 1.0;
    }

    /** One column at (±4.5, ±4.5): the corner's middle. */
    static boolean cornerMid(double dx, double dz) {
        return Math.abs(Math.abs(dx) - 4.5) < 0.5 && Math.abs(Math.abs(dz) - 4.5) < 0.5;
    }

    /** Distance outward from the room's centre lines, in the larger of the two (corner pieces taper toward the walls). */
    static double out(double dx, double dz) {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    static boolean blocks(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z);
        return switch (r.piece) {
            case CANDLE_VIGIL, WAX_PILLARS, IDOLS, OBELISKS -> corner2x2(dx, dz);
            case TOADSTOOL_RING, PRESS_LINE, CRYSTAL_CHANDELIER, CONTROL_ROOM -> cornerMid(dx, dz);
            case CAVE_IN -> rubble(r, dx, dz) >= 2;
            case SUNKEN_DESKS -> cornerMid(dx, dz);
            case TEA_HOUSE -> cornerMid(dx, dz);
            case ICE_GARDEN -> iceSpire(r, dx, dz) > 1;
            case STAIRS_NOWHERE -> stairStep(r, dx, dz) > 0;
            case CLOUD_BANK -> false;
            case SCULK_BLOOM -> cornerMid(dx, dz);
            default -> false;
        };
    }

    static int floorDelta(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z);
        return switch (r.piece) {
            case SUNKEN_DESKS -> Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5 && !cornerMid(dx, dz) ? -1 : KEEP;
            default -> KEEP;
        };
    }

    static boolean paint(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        double dx = dx(r, x), dz = dz(r, z);
        boolean free = !Pieces.clear(r, x, z);
        return switch (r.piece) {
            case CANDLE_VIGIL -> candleVigil(c, bx, bz, dx, dz, walk, ceil, free);
            case TOADSTOOL_RING -> toadstools(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case CAVE_IN -> caveIn(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case SUNKEN_DESKS -> sunkenDesks(c, bx, bz, x, z, dx, dz, walk, ceil, fo, free);
            case PRESS_LINE -> pressLine(c, bx, bz, dx, dz, walk, ceil, free);
            case OBELISKS -> obelisks(c, bx, bz, dx, dz, walk, ceil, free);
            case WAX_PILLARS -> waxPillars(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case IDOLS -> idols(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case TEA_HOUSE -> teaHouse(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case ICE_GARDEN -> iceGarden(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            case CRYSTAL_CHANDELIER -> crystalChandelier(c, bx, bz, dx, dz, walk, ceil, free);
            case CONTROL_ROOM -> controlRoom(c, bx, bz, dx, dz, walk, ceil, free);
            case STAIRS_NOWHERE -> stairsNowhere(c, bx, bz, dx, dz, walk, ceil, free);
            case CLOUD_BANK -> cloudBank(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case SCULK_BLOOM -> sculkBloom(c, bx, bz, x, z, dx, dz, walk, ceil, free);
            default -> false;
        };
    }

    // ---- Bell Crypt: a vigil of candles on stepped altars in the corners, a wheel of candles hung over the middle -------

    static boolean candleVigil(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        boolean claimed = false;
        if (corner2x2(dx, dz) && free) {
            boolean outer = Math.abs(dx) > 4 && Math.abs(dz) > 4;
            c.set(bx, walk, bz, s("minecraft:polished_deepslate"));
            if (outer) c.set(bx, walk + 1, bz, s("minecraft:chiseled_deepslate"));
            if (!c.lightless) c.set(bx, walk + (outer ? 2 : 1), bz, s(outer ? "minecraft:white_candle[candles=4,lit=true]" : "minecraft:candle[candles=3,lit=true]"));
            return true;
        }
        // the candle wheel: a ring of top slabs three under the ceiling, candles on it, four chains up
        int y = ceil - 3;
        if (ring(dx, dz, 2.5, 0.55) && y > walk + 3) {
            c.set(bx, y, bz, s("minecraft:polished_deepslate_slab[type=top]"));
            if (!c.lightless && Math.floorMod((int) Math.round((dx + dz) * 2), 2) == 0) c.set(bx, y + 1, bz, s("minecraft:candle[candles=2,lit=true]"));
            if (ringPoint(dx, dz, 2.5, 4, 0.5, 0.5)) for (int yy = y + 1; yy < ceil; yy++) c.set(bx, yy, bz, GatePainter.chain());
            claimed = true;
        }
        return claimed;
    }

    // ---- Fungal Hollow: a fairy ring of small mushrooms round toadstools in the corners -----------------------------

    static boolean toadstools(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        int k = (dx > 0 ? 1 : 0) + (dz > 0 ? 2 : 0);
        double mx = Math.signum(dx) * 4.5, mz = Math.signum(dz) * 4.5;
        double ld = Math.hypot(dx - mx, dz - mz);
        int stem = 2 + k % 2;
        if (ld < 1.8 && Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5) {
            boolean any = false;
            if (ld < 0.5 && free) {
                for (int y = walk; y < walk + stem; y++) c.set(bx, y, bz, s("minecraft:mushroom_stem"));
                any = true;
            }
            BlockState cap = k % 2 == 0 ? GatesRegistry.GLOWCAP.get().defaultBlockState() : s("minecraft:red_mushroom_block");
            c.set(bx, walk + stem, bz, cap);
            if (ld < 0.9) c.set(bx, walk + stem + 1, bz, cap);
            else if (!c.lightless && Hash.unit(r.seed, x, z) < 0.3) c.set(bx, walk + stem - 1, bz, s("minecraft:spore_blossom"));
            return true;
        }
        if (ring(dx, dz, 4.0, 0.5) && free && Hash.unit(r.seed, x, z * 3) < 0.7) {
            c.set(bx, walk - 1, bz, s("minecraft:mycelium"));
            c.set(bx, walk, bz, s(Hash.unit(r.seed, z, x) < 0.5 ? "minecraft:red_mushroom" : "minecraft:brown_mushroom"));
            return true;
        }
        return false;
    }

    // ---- Ember Mines: the ceiling came down in two corners; props hold up the rest ---------------------------------

    /** Height of the rubble in this column (0 none): heaped against two opposite corners. */
    static int rubble(Room r, double dx, double dz) {
        boolean flip = Hash.unit(r.seed, 3, 9) < 0.5;
        if (Math.abs(dx) <= 2.5 || Math.abs(dz) <= 2.5) return 0;
        if ((dx > 0) == (dz > 0) == flip) return 0;
        double t = (Math.abs(dx) - 2.5) + (Math.abs(dz) - 2.5);
        return (int) Math.max(0, Math.min(4, Math.floor(t * 0.75)));
    }

    static boolean caveIn(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        int h = rubble(r, dx, dz);
        if (h > 0 && free) {
            for (int i = 0; i < h; i++) {
                double t = Hash.unit(r.seed, x * 7 + i, z);
                c.set(bx, walk + i, bz, t < 0.12 ? GatesRegistry.EMBER_ORE.get().defaultBlockState() : t < 0.2 ? s("minecraft:gilded_blackstone")
                        : t < 0.6 ? s("minecraft:blackstone") : s("minecraft:cobbled_deepslate"));
            }
            return true;
        }
        // props in the other corners: a post and a cracked beam overhead
        if (cornerMid(dx, dz) && free && h == 0) {
            for (int y = walk; y < ceil; y++) c.set(bx, y, bz, s("minecraft:stripped_dark_oak_log[axis=y]"));
            if (!c.lightless) c.set(bx, walk + 3, bz, s("minecraft:ochre_froglight"));
            return true;
        }
        return false;
    }

    // ---- Drowned Archive: the corners have sunk; desks stand in the water with their papers ------------------------

    static boolean sunkenDesks(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo, boolean free) {
        Room r = c.r;
        if (fo == -1) {
            c.set(bx, walk - 1, bz, !c.lightless && Hash.unit(r.seed, x, z) < 0.15 ? s("minecraft:sea_lantern") : s("minecraft:dark_prismarine"));
            c.set(bx, walk, bz, s("minecraft:water"));
            if (Hash.unit(r.seed, z, x) < 0.12) c.set(bx, walk, bz, s("minecraft:sea_pickle[pickles=2,waterlogged=true]"));
            return true;
        }
        if (cornerMid(dx, dz) && free) {
            c.set(bx, walk - 1, bz, s("minecraft:dark_prismarine"));
            c.set(bx, walk, bz, s("minecraft:dark_oak_planks"));
            c.set(bx, walk + 1, bz, c.lightless ? s("minecraft:white_carpet") : s("minecraft:candle[candles=1,lit=true]"));
            return true;
        }
        return false;
    }

    // ---- Clockwork Foundry: stamping presses in the corners, pistons hung over iron anvils --------------------------

    static boolean pressLine(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        if (cornerMid(dx, dz) && free) {
            c.set(bx, walk, bz, s("minecraft:iron_block"));
            c.set(bx, walk + 1, bz, s("minecraft:heavy_weighted_pressure_plate"));
            int py = Math.min(walk + 4, ceil - 2);
            c.set(bx, py, bz, s("minecraft:piston[facing=down]"));
            for (int y = py + 1; y < ceil; y++) c.set(bx, y, bz, s("minecraft:iron_block"));
            return true;
        }
        // grates round each press, a bulb lighting its plate
        if (Math.abs(Math.abs(dx) - 4.5) < 1.6 && Math.abs(Math.abs(dz) - 4.5) < 1.6 && Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5) {
            c.set(bx, walk - 1, bz, s("minecraft:waxed_copper_grate"));
            if (!c.lightless && Math.abs(Math.abs(dx) - 4.5) < 0.6 && Math.abs(Math.abs(dz) - 3.5) < 0.6 && free) {
                c.set(bx, walk - 1, bz, s("minecraft:waxed_copper_bulb[lit=true]"));
            }
        }
        return false;
    }

    // ---- Glass Desert: four obelisks with suns at their tips --------------------------------------------------------

    static boolean obelisks(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        if (!corner2x2(dx, dz) || !free) return false;
        boolean core = Math.abs(dx) > 4 && Math.abs(dz) > 4;
        c.set(bx, walk, bz, s("minecraft:cut_sandstone"));
        if (core) {
            int top = Math.min(ceil - 2, walk + 6);
            for (int y = walk + 1; y < top; y++) c.set(bx, y, bz, y == walk + 3 ? s("minecraft:chiseled_sandstone") : s("minecraft:smooth_sandstone"));
            c.set(bx, top, bz, GatesRegistry.SUN_TILE.get().defaultBlockState());
        } else c.set(bx, walk + 1, bz, s("minecraft:smooth_sandstone_slab[type=bottom]"));
        return true;
    }

    // ---- Hive: giant wax candles in the corners, burning -----------------------------------------------------------

    static boolean waxPillars(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        if (!corner2x2(dx, dz) || !free) return false;
        int k = (dx > 0 ? 1 : 0) + (dz > 0 ? 2 : 0);
        int h = 3 + k % 3;
        for (int y = walk; y < walk + h; y++) c.set(bx, y, bz, Hash.unit(r.seed, x, y * 5 + z) < 0.2 ? s("minecraft:honey_block") : s("minecraft:yellow_terracotta"));
        boolean wick = Math.abs(dx) > 4 && Math.abs(dz) > 4;
        if (wick && !c.lightless) c.set(bx, walk + h, bz, s("minecraft:shroomlight"));
        return true;
    }

    // ---- Overgrown Temple: idols in the corners, their jade-eyed faces turned to the room ---------------------------

    static boolean idols(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        if (!corner2x2(dx, dz) || !free) return false;
        c.set(bx, walk, bz, s("minecraft:mossy_stone_bricks"));
        c.set(bx, walk + 1, bz, s("minecraft:chiseled_stone_bricks"));
        // the head faces the nearer centre line
        int face = Math.abs(dx) < Math.abs(dz) ? (dx > 0 ? 3 : 1) : (dz > 0 ? 0 : 2);
        BlockState head = GatePainter.facing(GatesRegistry.TEMPLE_RELIEF.get().defaultBlockState(), face);
        c.set(bx, walk + 2, bz, head);
        c.set(bx, walk + 3, bz, s("minecraft:mossy_stone_brick_slab[type=bottom]"));
        if (Hash.unit(r.seed, x, z) < 0.4) c.set(bx, walk + 4, bz, s("minecraft:moss_carpet"));
        return true;
    }

    // ---- Lantern Night Market: a tea house: low tables, cushions, lanterns strung corner to corner --------------------

    static boolean teaHouse(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        boolean claimed = false;
        if (cornerMid(dx, dz) && free) {
            c.set(bx, walk, bz, s("minecraft:dark_oak_planks"));
            c.set(bx, walk + 1, bz, s(Hash.unit(r.seed, x, z) < 0.5 ? "minecraft:flower_pot" : "minecraft:candle[candles=3,lit=true]"));
            return true;
        }
        if (Math.abs(Math.abs(dx) - 4.5) < 1.6 && Math.abs(Math.abs(dz) - 4.5) < 1.6 && Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5 && free) {
            c.set(bx, walk, bz, s(Math.floorMod(x + z, 2) == 0 ? "minecraft:red_carpet" : "minecraft:yellow_carpet"));
            claimed = true;
        }
        // two strings of paper lanterns along the diagonals
        if (Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6 && ceil - walk > 6) {
            int y = ceil - 1 - (Math.hypot(dx, dz) < 3 ? 1 : 0);
            c.set(bx, y, bz, GatePainter.chain());
            if (!c.lightless && Math.floorMod(x, 2) == 0) c.set(bx, y - 1, bz, GatesRegistry.PAPER_LANTERN.get().defaultBlockState());
            claimed = true;
        }
        return claimed;
    }

    // ---- Frost Monastery: an ice garden: spires of ice in the corners, snow drifted round them ----------------------

    /** Height of the ice spire in this column (0 none). */
    static int iceSpire(Room r, double dx, double dz) {
        if (Math.abs(dx) <= 2.5 || Math.abs(dz) <= 2.5) return 0;
        double ld = Math.hypot(Math.abs(dx) - 4.5, Math.abs(dz) - 4.5);
        if (ld > 1.6) return 0;
        return (int) Math.round(4.5 - ld * 2.2);
    }

    static boolean iceGarden(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        int h = iceSpire(r, dx, dz);
        if (h > 0 && free) {
            for (int y = walk; y < Math.min(ceil - 1, walk + h); y++) c.set(bx, y, bz, y == walk + h - 1 && h > 2 ? s("minecraft:blue_ice") : s("minecraft:packed_ice"));
            if (h == 1) c.set(bx, walk, bz, s("minecraft:snow[layers=3]"));
            return true;
        }
        if (Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5 && free) {
            c.set(bx, walk - 1, bz, s("minecraft:snow_block"));
            if (Hash.unit(r.seed, x, z) < 0.6) c.set(bx, walk, bz, s("minecraft:snow[layers=1]"));
        }
        return false;
    }

    // ---- Mirror Palace: a grand chandelier over the floor, mirrors turned to it from the corners --------------------

    static boolean crystalChandelier(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        boolean claimed = false;
        if (cornerMid(dx, dz) && free) {
            c.set(bx, walk, bz, s("minecraft:chiseled_quartz_block"));
            for (int y = walk + 1; y < Math.min(ceil - 1, walk + 4); y++) c.set(bx, y, bz, GatesRegistry.MIRROR.get().defaultBlockState());
            c.set(bx, Math.min(ceil - 1, walk + 4), bz, s("minecraft:chiseled_quartz_block"));
            return true;
        }
        int y = ceil - 3;
        double d = Math.hypot(dx, dz);
        if (d < 4.0 && y > walk + 4) {
            if (ring(dx, dz, 3.5, 0.5)) c.set(bx, y, bz, s("minecraft:glass"));
            if (ring(dx, dz, 2.0, 0.5)) c.set(bx, y + 1, bz, s("minecraft:glass"));
            if (!c.lightless && ringPoint(dx, dz, 3.5, 8, 0.5, 0.5)) c.set(bx, y - 1, bz, s("minecraft:end_rod[facing=down]"));
            if (d < 1.0) {
                for (int yy = y + 1; yy < ceil; yy++) c.set(bx, yy, bz, GatePainter.chain());
                c.set(bx, y, bz, c.lightless ? s("minecraft:glass") : s("minecraft:sea_lantern"));
                c.set(bx, y - 1, bz, s("minecraft:amethyst_cluster[facing=down]"));
            }
            claimed = true;
        }
        return claimed;
    }

    // ---- Precursor Lab: the control room: consoles in the corners facing a light table hung from the ceiling ----------

    static boolean controlRoom(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        if (cornerMid(dx, dz) && free) {
            int face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 3 : 1) : (dz > 0 ? 0 : 2);
            BlockState rack = GatePainter.facing(GatesRegistry.LAB_RACK.get().defaultBlockState(), face);
            c.set(bx, walk, bz, rack);
            c.set(bx, walk + 1, bz, rack);
            c.set(bx, walk + 2, bz, s("awaken:precursor_plating"));
            return true;
        }
        // consoles' desks either side of each rack
        boolean desk = Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5 && (Math.abs(Math.abs(dx) - 4.5) < 0.6) != (Math.abs(Math.abs(dz) - 4.5) < 0.6)
                && Math.abs(Math.abs(dx) - 4.5) < 1.6 && Math.abs(Math.abs(dz) - 4.5) < 1.6;
        if (desk && free) {
            c.set(bx, walk, bz, s("minecraft:smooth_stone_slab[type=top]"));
            return false;
        }
        if (Math.hypot(dx, dz) < 2.0 && !c.lightless) c.set(bx, ceil, bz, GatesRegistry.LAB_PANEL.get().defaultBlockState());
        return false;
    }

    // ---- Nightmare: stairs that climb out of the corners into the ceiling ------------------------------------------

    /** Step height (1..) of the stair in this column, 0 if none: two flights against opposite walls, rising toward the corners. */
    static int stairStep(Room r, double dx, double dz) {
        boolean flip = Hash.unit(r.seed, 5, 1) < 0.5;
        double along = flip ? dx : dz, across = flip ? dz : dx;
        if (Math.abs(across) < 4.4 || Math.abs(across) > 5.6) return 0;
        if (Math.abs(along) < 2.6) return 0;
        return (int) Math.round(Math.abs(along) - 2.5);
    }

    static boolean stairsNowhere(GatePainter.Ctx c, int bx, int bz, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        int st = stairStep(r, dx, dz);
        if (st > 0 && free) {
            boolean flip = Hash.unit(r.seed, 5, 1) < 0.5;
            double along = flip ? dx : dz;
            int face = flip ? (along > 0 ? 1 : 3) : (along > 0 ? 2 : 0);
            for (int y = walk; y < walk + st - 1; y++) c.set(bx, y, bz, s("minecraft:oak_planks"));
            c.set(bx, walk + st - 1, bz, GatePainter.facing(s("minecraft:oak_stairs"), face));
            // the top step ends in a door frame against the ceiling
            if (st == 3) for (int y = walk + st; y < ceil; y++) c.set(bx, y, bz, s("minecraft:oak_log[axis=y]"));
            return true;
        }
        return false;
    }

    // ---- Sky Archipelago: clouds come in over the islands, a lamp post on each --------------------------------------

    static boolean cloudBank(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        double n = Hash.fbm(r.seed ^ 0xC10BL, x, z, 3);
        int mid = Math.min(ceil - 2, c.floorY + 6); // clouds drifting through overhead, not at eye height
        if (n > 0.62 && Math.hypot(dx, dz) > 2.5 && mid + 1 < ceil) {
            c.set(bx, mid, bz, GatesRegistry.CLOUD.get().defaultBlockState());
            if (n > 0.72) c.set(bx, mid + 1, bz, GatesRegistry.CLOUD.get().defaultBlockState());
            return fo != 0;
        }
        if (fo == 0 && cornerMid(dx, dz) && !Pieces.clear(r, x, z) && !c.lightless) {
            c.set(bx, walk, bz, s("minecraft:birch_fence"));
            c.set(bx, walk + 1, bz, s("minecraft:lantern[hanging=false]"));
            return true;
        }
        return fo != 0;
    }

    // ---- Abyssal Shrine: the sculk is blooming: patches of it, black spires, a candle at each ------------------------

    static boolean sculkBloom(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, boolean free) {
        Room r = c.r;
        if (cornerMid(dx, dz) && free) {
            int h = 2 + (int) (Hash.unit(r.seed, x, z) * 3);
            for (int y = walk; y < walk + h; y++) c.set(bx, y, bz, y == walk + h - 1 ? s("minecraft:crying_obsidian") : s("minecraft:obsidian"));
            return true;
        }
        if (Hash.noise(r.seed ^ 0x5C1CL, x, z, 3) < 0.45) c.set(bx, walk - 1, bz, s("minecraft:sculk"));
        if (free && Math.abs(Math.abs(dx) - 4.5) < 1.6 && Math.abs(Math.abs(dz) - 4.5) < 1.6 && Math.abs(dx) > 2.5 && Math.abs(dz) > 2.5
                && Hash.unit(r.seed, z, x) < 0.25 && !c.lightless) {
            c.set(bx, walk, bz, s("minecraft:black_candle[candles=2,lit=true]"));
            return true;
        }
        return false;
    }
}
