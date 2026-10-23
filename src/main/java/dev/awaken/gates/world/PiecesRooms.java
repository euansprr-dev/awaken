package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.gates.world.Pieces.*;

/**
 * The rooms' set pieces (see {@link SetPiece}); called through {@link Pieces}, which keeps door axes and features clear.
 * Geometry (what blocks a column, what lowers or raises its floor) and painting are written side by side per piece so
 * they cannot drift apart. Coordinates: dx, dz from the room's interior centre (half-integers in even rooms).
 */
final class PiecesRooms {
    private PiecesRooms() {}

    // ---- geometry ---------------------------------------------------------------------------------------------------

    static boolean blocks(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z), d = Math.hypot(dx, dz);
        return switch (r.piece) {
            case BELL_LOFT -> candleRing(dx, dz);
            case OSSUARY_AISLES -> ossuaryWall(r, x, z);
            case SARCOPHAGI -> sarcophagus(r, x, z) != 0;
            case MUSHROOM_GROVE -> grove(r, dx, dz, true) != null;
            case SPORE_POOL -> d < 1.6;
            case MINE_GALLERY -> orePile(r, x, z);
            case LAVA_FORGE -> forge(r, x, z) == 2;
            case FLOODED_STACKS -> stack(r, x, z);
            case READING_ROOM -> readingTable(r, dx, dz) || wallShelf(r, x, z);
            case GEAR_WALL -> false;
            case CONVEYOR -> conveyorCargo(r, x, z);
            case BOILER -> d < 3.0;
            case GLASS_GARDEN -> crystal(r, dx, dz) > 0;
            case BURIED_COLONNADE -> column(r, x, z) > 0;
            case BROOD_COMB -> d < 2.0 || combPillar(r, dx, dz);
            case HONEY_POOLS -> false;
            case ROOT_TREE -> d < 3.0;
            case REFLECTING_POOL -> false;
            case MARKET_STALLS -> stall(r, x, z) == 1;
            case LANTERN_TREE -> d < 2.6;
            case CLOISTER -> cloisterPillar(r, x, z);
            case CHOIR -> choirStall(r, x, z) != 0;
            case MIRROR_HALL -> d < 1.6 || mirrorPanel(r, dx, dz);
            case MIRROR_MAZE -> mazeWall(r, x, z);
            case TANKS -> tank(r, dx, dz) > 0;
            case SERVER_HALL -> rack(r, x, z) != 0;
            case GIANT_TABLE -> tableLeg(r, dx, dz) || chairLeg(r, dx, dz);
            case WRONG_ROOM -> wrongDoor(r, dx, dz);
            case FLOATING_ISLES -> isleTree(r, dx, dz);
            case WIND_SHRINE -> d < 2.0 || windPillar(dx, dz, r);
            case VOID_RIFT -> false;
            case MONOLITHS -> monolith(r, dx, dz) > 0;
            default -> false;
        };
    }

    static int floorDelta(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z), d = Math.hypot(dx, dz);
        return switch (r.piece) {
            case SPORE_POOL -> d >= 2.5 && d < 4.6 ? -1 : KEEP;
            case LAVA_FORGE -> forge(r, x, z) == 1 ? -1 : KEEP;
            case FLOODED_STACKS -> stack(r, x, z) ? KEEP : -1;
            case HONEY_POOLS -> honeyPool(r, dx, dz) ? -1 : KEEP;
            case REFLECTING_POOL -> reflectingPool(r, dx, dz) ? -1 : KEEP;
            case VOID_RIFT -> d < 4.6 ? -3 : KEEP;
            case BURIED_COLONNADE -> dune(r, x, z) ? 1 : KEEP;
            case GLASS_GARDEN -> drift(r, dx, dz) ? 1 : KEEP;
            default -> KEEP;
        };
    }

    static int ceilingDelta(Room r, int x, int z) {
        double d = Math.hypot(dx(r, x), dz(r, z));
        return switch (r.piece) {
            case CLOISTER -> cloisterCourt(r, x, z) ? 2 : 0;
            case BELL_LOFT -> d < 5 ? 2 : 0;
            default -> 0;
        };
    }

    /** Floating Isles: a few round islands joined by the walkways along the door axes. */
    static boolean isle(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z);
        double R = radius(r);
        if (Math.hypot(dx, dz) < Math.max(3.0, R * 0.32)) return true;
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2 + (Hash.unit(r.seed, k, 31) - 0.5) * 0.5;
            double rr = R * (0.52 + Hash.unit(r.seed, k, 32) * 0.12);
            double ir = R * (0.2 + Hash.unit(r.seed, k, 33) * 0.08);
            double n = Hash.fbm(r.seed ^ 0x15E5L, x, z, 3) * 1.2;
            if (Math.hypot(dx - Math.cos(a) * rr, dz - Math.sin(a) * rr) < ir + n) return true;
        }
        return false;
    }

    static boolean wallClaim(Room r, int x, int z, int d) {
        return r.piece == SetPiece.GEAR_WALL && gearWallSide(r) == (d + 2) % 4 && gearWallColumn(r, x, z, d);
    }

    static boolean paintWall(GatePainter.Ctx c, int bx, int bz, int x, int z, int od) {
        if (c.r.piece != SetPiece.GEAR_WALL) return false;
        int d = Integer.numberOfTrailingZeros(od);
        if (Integer.bitCount(od) != 1 || !wallClaim(c.r, x, z, d)) return false;
        gearWall(c, bx, bz, x, z, d);
        return true;
    }

    // ---- painting ---------------------------------------------------------------------------------------------------

    static boolean paint(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        double dx = dx(r, x), dz = dz(r, z);
        return switch (r.piece) {
            case BELL_LOFT -> bellLoft(c, bx, bz, x, z, dx, dz, walk, ceil);
            case OSSUARY_AISLES -> ossuary(c, bx, bz, x, z, walk, ceil);
            case SARCOPHAGI -> sarcophagi(c, bx, bz, x, z, walk);
            case MUSHROOM_GROVE -> mushroomGrove(c, bx, bz, x, z, dx, dz, walk, ceil);
            case SPORE_POOL -> sporePool(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case MINE_GALLERY -> mineGallery(c, bx, bz, x, z, walk, ceil);
            case LAVA_FORGE -> lavaForge(c, bx, bz, x, z, walk, ceil, fo);
            case FLOODED_STACKS -> floodedStacks(c, bx, bz, x, z, walk, ceil, fo);
            case READING_ROOM -> readingRoom(c, bx, bz, x, z, dx, dz, walk, ceil);
            case CONVEYOR -> conveyor(c, bx, bz, x, z, walk, ceil);
            case BOILER -> boiler(c, bx, bz, x, z, dx, dz, walk, ceil);
            case GLASS_GARDEN -> glassGarden(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case BURIED_COLONNADE -> colonnade(c, bx, bz, x, z, walk, ceil, fo);
            case BROOD_COMB -> broodComb(c, bx, bz, x, z, dx, dz, walk, ceil);
            case HONEY_POOLS -> honeyPools(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case ROOT_TREE -> rootTree(c, bx, bz, x, z, dx, dz, walk, ceil);
            case REFLECTING_POOL -> pool(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case MARKET_STALLS -> marketStalls(c, bx, bz, x, z, walk, ceil);
            case LANTERN_TREE -> lanternTree(c, bx, bz, x, z, dx, dz, walk, ceil);
            case CLOISTER -> cloister(c, bx, bz, x, z, walk, ceil);
            case CHOIR -> choir(c, bx, bz, x, z, dx, dz, walk, ceil);
            case MIRROR_HALL -> mirrorHall(c, bx, bz, x, z, dx, dz, walk, ceil);
            case MIRROR_MAZE -> mirrorMaze(c, bx, bz, x, z, walk, ceil);
            case TANKS -> tanks(c, bx, bz, x, z, dx, dz, walk, ceil);
            case SERVER_HALL -> serverHall(c, bx, bz, x, z, walk, ceil);
            case GIANT_TABLE -> giantTable(c, bx, bz, x, z, dx, dz, walk, ceil);
            case WRONG_ROOM -> wrongRoom(c, bx, bz, x, z, dx, dz, walk, ceil);
            case FLOATING_ISLES -> isles(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case WIND_SHRINE -> windShrine(c, bx, bz, x, z, dx, dz, walk, ceil);
            case VOID_RIFT -> voidRift(c, bx, bz, x, z, dx, dz, walk, ceil, fo);
            case MONOLITHS -> monoliths(c, bx, bz, x, z, dx, dz, walk, ceil);
            default -> false;
        };
    }

    static boolean free(Room r, int x, int z) {
        return !Pieces.clear(r, x, z);
    }

    // =================================================================================================================
    // Bell Crypt

    /** A ring of grave candles round the great bell's shadow. */
    static boolean candleRing(double dx, double dz) {
        double d = Math.hypot(dx, dz);
        return Math.abs(d - 6.5) < 0.5 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx)) / 30.0), 2) == 0;
    }

    /**
     * The bell loft: a great bronze bell hangs over the centre of a round room, glowing from inside; smaller bells hang
     * on chains in a ring round it at different heights, soul lanterns between them; candles mark the bell's shadow.
     */
    static boolean bellLoft(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        int head = ceil - walk;
        boolean claimed = false;
        int tall = Math.max(5, Math.min(8, head - 6));
        double lip = Math.min(4.2, radius(r) * 0.3);
        if (d <= lip + 0.6) {
            Pieces.bell(c, bx, bz, dx, dz, ceil - tall - 1, tall, lip, ceil, bronze(),
                    s("minecraft:polished_deepslate"), c.lightless ? null : s("minecraft:pearlescent_froglight"));
            claimed = true;
        }
        // the floor under it: a ring of tiles where the toll falls, lights set in it looking up at the bell
        if (d < 5.5 && d > 4.5) c.set(bx, walk - 1, bz, !c.lightless && ringPoint(dx, dz, 5.0, 8, 0, 0.5) ? s("minecraft:pearlescent_froglight") : c.k.band());
        if (candleRing(dx, dz) && free(r, x, z)) {
            c.set(bx, walk, bz, c.k.trim());
            if (!c.lightless) c.set(bx, walk + 1, bz, s("minecraft:white_candle[candles=3,lit=true]"));
            return true;
        }
        // eight small bells round it on chains of different lengths, a soul lantern between each pair
        double ringR = Math.min(radius(r) - 3.5, 10.5);
        if (ringPoint(dx, dz, ringR, 16, 0, 0.5)) {
            int k = spoke(dx, dz, 16, 0);
            boolean bell = k % 2 == 0;
            int drop = bell ? 2 + Math.floorMod(k * 5 + 3, 4) : 4;
            int y = Math.max(walk + 4, ceil - drop - 1);
            for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, GatePainter.chain());
            c.set(bx, y, bz, bell ? GatesRegistry.BELL.get().defaultBlockState()
                    : c.lightless ? GatePainter.chain() : s("minecraft:soul_lantern[hanging=true]"));
            claimed = true;
        }
        return claimed;
    }

    /** Free-standing ossuary walls in rows, three high, gaps where you cross. Rows run along the room's long side. */
    static boolean ossuaryWall(Room r, int x, int z) {
        boolean alongX = (r.x1() - r.x0()) >= (r.z1() - r.z0());
        int across = alongX ? z - r.z0() : x - r.x0();
        int along = alongX ? x - r.x0() : z - r.z0();
        int acrossLen = alongX ? r.z1() - r.z0() : r.x1() - r.x0();
        int alongLen = alongX ? r.x1() - r.x0() : r.z1() - r.z0();
        if (across < 3 || across > acrossLen - 3 || Math.floorMod(across - 3, 4) != 0) return false;
        if (along < 2 || along > alongLen - 2) return false;
        return Math.floorMod(along - 2, 8) < 6;
    }

    static boolean ossuary(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        if (!ossuaryWall(r, x, z) || !free(r, x, z)) return false;
        c.set(bx, walk, bz, c.k.trim());
        c.set(bx, walk + 1, bz, GatesRegistry.OSSUARY.get().defaultBlockState());
        c.set(bx, walk + 2, bz, GatesRegistry.OSSUARY.get().defaultBlockState());
        c.set(bx, walk + 3, bz, s("minecraft:polished_deepslate_slab[type=bottom]"));
        boolean alongX = (r.x1() - r.x0()) >= (r.z1() - r.z0());
        int along = alongX ? x - r.x0() : z - r.z0();
        if (!c.lightless && Math.floorMod(along - 2, 8) == 2 && ceil - walk >= 6) c.set(bx, walk + 4, bz, s("minecraft:candle[candles=2,lit=true]"));
        return true;
    }

    /** Stone coffins in the room's quarters that no doorway crosses: 1 body, 2 head (a candle), 0 elsewhere. */
    static int sarcophagus(Room r, int x, int z) {
        double dx = dx(r, x), dz = dz(r, z);
        double qx = Math.signum(dx) * 4.0, qz = Math.signum(dz) * 4.5;
        double lx = dx - qx, lz = dz - qz;
        if (Math.abs(lx) <= 0.5 && Math.abs(lz) <= 1.0) return lz * Math.signum(qz) > 0.5 ? 2 : 1;
        return 0;
    }

    static boolean sarcophagi(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk) {
        Room r = c.r;
        int part = sarcophagus(r, x, z);
        if (part == 0 || !free(r, x, z)) return false;
        c.set(bx, walk, bz, s("minecraft:polished_deepslate"));
        c.set(bx, walk + 1, bz, s(part == 2 ? "minecraft:chiseled_deepslate" : "minecraft:deepslate_tile_slab[type=bottom]"));
        if (part == 2 && !c.lightless && Math.floorMod(x + z, 2) == 0) c.set(bx, walk + 2, bz, s("minecraft:candle[candles=1,lit=true]"));
        return true;
    }

    // =================================================================================================================
    // Fungal Hollow

    /** Giant mushrooms in the grove: {x, z, stem height, cap radius} of the one this column is under, or null. */
    static double[] grove(Room r, double dx, double dz, boolean stemOnly) {
        double R = radius(r);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2 + (Hash.unit(r.seed, k, 51) - 0.5) * 0.4;
            double rr = R * (0.42 + Hash.unit(r.seed, k, 52) * 0.12);
            double mx = Math.round(Math.cos(a) * rr), mz = Math.round(Math.sin(a) * rr);
            double stem = 5 + Math.floor(Hash.unit(r.seed, k, 53) * 3);
            double cap = 3.4 + Hash.unit(r.seed, k, 54) * 1.4;
            double ld = Math.hypot(dx - mx, dz - mz);
            if (stemOnly ? (Math.abs(dx - mx) < 1 && Math.abs(dz - mz) < 1) : ld <= cap + 0.5) return new double[]{mx, mz, stem, cap};
        }
        return null;
    }

    /** A grove of giant glowcaps: pale stems, violet caps lit with spots, gills glowing underneath, spores drifting down. */
    static boolean mushroomGrove(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        double[] m = grove(c.r, dx, dz, false);
        if (m == null) return false;
        return mushroom(c, bx, bz, dx - m[0], dz - m[1], walk, (int) m[2], m[3], ceil, free(c.r, x, z));
    }

    /** One giant mushroom in a column at (ldx, ldz) from its stem's axis. Returns true if it put anything here. */
    static boolean mushroom(GatePainter.Ctx c, int bx, int bz, double ldx, double ldz, int walk, int stemH, double capR, int ceil, boolean stemAllowed) {
        stemH = Math.min(stemH, ceil - walk - 3);
        if (stemH < 3) return false;
        double ld = Math.hypot(ldx, ldz);
        int capY = walk + stemH;
        boolean any = false;
        BlockState cap = GatesRegistry.GLOWCAP.get().defaultBlockState(), gills = GatesRegistry.GLOWCAP_GILLS.get().defaultBlockState();
        if (stemAllowed && Math.abs(ldx) < 1 && Math.abs(ldz) < 1) {
            for (int y = walk; y < capY; y++) c.set(bx, y, bz, s("minecraft:mushroom_stem"));
            c.set(bx, walk - 1, bz, s("minecraft:mycelium"));
            any = true;
        } else if (stemAllowed && ld < 2.2 && Math.abs(ldx) < 2 && Math.abs(ldz) < 2) {
            c.set(bx, walk, bz, s(ld < 1.8 ? "minecraft:moss_carpet" : "minecraft:brown_mushroom"));
        }
        if (ld <= capR + 0.35) {
            if (ld > capR - 0.9) {
                c.set(bx, capY, bz, cap);
                c.set(bx, capY - 1, bz, cap); // the rim droops
                if (!c.lightless && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(ldz, ldx))), 60) < 12)
                    c.set(bx, capY - 2, bz, s("minecraft:spore_blossom"));
            } else c.set(bx, capY, bz, ld < 0.9 ? s("minecraft:mushroom_stem") : gills);
            if (ld <= capR - 0.6) c.set(bx, capY + 1, bz, cap);
            if (ld <= capR - 1.9 && capY + 2 < ceil) c.set(bx, capY + 2, bz, cap);
            any = true;
        }
        return any;
    }

    /** The spore pool: a ring of glowing water round a mushroom on an island; the door axes cross it as causeways. */
    static boolean sporePool(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        double d = Math.hypot(dx, dz);
        if (fo == -1) {
            c.set(bx, walk - 1, bz, c.lightless || Math.floorMod(x * 3 + z, 5) != 0 ? s("minecraft:moss_block") : s("minecraft:shroomlight"));
            c.set(bx, walk, bz, s("minecraft:water"));
            return true;
        }
        if (d < 2.5) {
            mushroom(c, bx, bz, dx, dz, walk, Math.min(6, ceil - walk - 3), 3.2, ceil, true);
            if (d >= 1.6) c.set(bx, walk - 1, bz, s("minecraft:mycelium"));
            return true;
        }
        if (d < 5.4) c.set(bx, walk - 1, bz, s("minecraft:moss_block"));
        return false;
    }

    // =================================================================================================================
    // Ember Mines

    static boolean alongX(Room r) {
        return (r.x1() - r.x0()) >= (r.z1() - r.z0());
    }

    /** Ore heaps along the walls between the gallery's timber frames. */
    static boolean orePile(Room r, int x, int z) {
        boolean ax = alongX(r);
        int across = ax ? z : x, lo = ax ? r.z0() : r.x0(), hi = ax ? r.z1() : r.x1();
        int along = ax ? x - r.x0() : z - r.z0();
        boolean edge = across == lo || across == hi;
        return edge && Math.floorMod(along, 4) != 0 && Hash.unit(r.seed, along, across) < 0.45;
    }

    /**
     * The mine gallery: timber frames every four blocks (posts against the walls, a beam overhead, a lantern hung
     * from it), ore heaps between the posts, the runner's rails running down the middle.
     */
    static boolean mineGallery(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        boolean ax = alongX(r);
        int along = ax ? x - r.x0() : z - r.z0();
        int across = ax ? z : x, lo = ax ? r.z0() : r.x0(), hi = ax ? r.z1() : r.x1();
        int beamY = Math.min(ceil - 1, walk + 5);
        boolean claimed = false;
        if (Math.floorMod(along, 4) == 0) {
            // the frame: posts at the walls, a beam across, braces in the corners
            if (across == lo || across == hi) {
                for (int y = walk; y < beamY; y++) c.set(bx, y, bz, s("minecraft:stripped_dark_oak_log[axis=y]"));
                claimed = true;
            } else if (across == lo + 1 || across == hi - 1) {
                c.set(bx, beamY - 1, bz, GatePainter.facing(s("minecraft:dark_oak_stairs[half=top]"), across == lo + 1 ? (ax ? 0 : 3) : (ax ? 2 : 1)));
            }
            c.set(bx, beamY, bz, GatePainter.axis(s("minecraft:stripped_dark_oak_log"), ax ? "z" : "x"));
            int mid = (lo + hi) / 2;
            if (!c.lightless && (across == mid) && Math.floorMod(along, 8) == 4) c.set(bx, beamY - 1, bz, s("minecraft:lantern[hanging=true]"));
        }
        if (orePile(r, x, z) && free(r, x, z)) {
            int hgt = 1 + (Hash.unit(r.seed, x, z * 7) < 0.4 ? 1 : 0);
            for (int y = walk; y < walk + hgt; y++) c.set(bx, y, bz, s(Hash.unit(r.seed, x + y, z) < 0.3 ? "minecraft:gold_ore" : "minecraft:gilded_blackstone"));
            claimed = true;
        }
        return claimed;
    }

    /** The forge: 1 = the lava channel down the room, 2 = the anvils and crucible stand beside it, 0 = floor. */
    static int forge(Room r, int x, int z) {
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        double along = ax ? dx(r, x) : dz(r, z);
        if (Math.abs(across) < 1.0 && Math.abs(along) < (ax ? hw(r) : hd(r)) - 2.5) return 1;
        if (Math.abs(across - 3.0) < 0.6 && Math.abs(Math.abs(along) - 4.5) < 1.1) return 2;
        return 0;
    }

    /**
     * The forge hall: a channel of lava runs the length of the room (stone bridges where the doorways cross), crucibles of
     * molten metal hang over it on chains, anvils and quench troughs stand beside it.
     */
    static boolean lavaForge(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        int f = forge(r, x, z);
        boolean ax = alongX(r);
        double along = ax ? dx(r, x) : dz(r, z);
        if (f == 1 && fo == -1) {
            c.set(bx, walk - 1, bz, s("minecraft:magma_block"));
            c.set(bx, walk, bz, s("minecraft:lava"));
            if (Math.floorMod((int) Math.floor(along), 6) == 2 && ceil - walk > 8) {
                int y = ceil - 5;
                for (int yy = ceil - 1; yy > y; yy--) c.set(bx, yy, bz, GatePainter.chain());
                c.set(bx, y, bz, s("minecraft:lava_cauldron"));
            }
            return true;
        }
        if (f == 1) {
            c.set(bx, walk - 1, bz, s("minecraft:polished_blackstone_bricks")); // a bridge where a doorway crosses
            return false;
        }
        if (f == 2 && free(r, x, z)) {
            boolean anvil = Math.floorMod(x + z, 2) == 0;
            c.set(bx, walk, bz, anvil ? s("minecraft:polished_blackstone") : s("minecraft:water_cauldron[level=3]"));
            if (anvil) c.set(bx, walk + 1, bz, s("minecraft:anvil"));
            return true;
        }
        // the channel's lip
        double across = ax ? dz(r, z) : dx(r, x);
        if (Math.abs(Math.abs(across) - 1.5) < 0.5 && Math.abs(along) < (ax ? hw(r) : hd(r)) - 2) c.set(bx, walk - 1, bz, s("minecraft:polished_blackstone_bricks"));
        return false;
    }

    // =================================================================================================================
    // Drowned Archive

    /** Book stacks in rows two wide, the long way, gaps to cross between them. */
    static boolean stack(Room r, int x, int z) {
        boolean ax = alongX(r);
        int across = ax ? z - r.z0() : x - r.x0();
        int along = ax ? x - r.x0() : z - r.z0();
        int acrossLen = ax ? r.z1() - r.z0() : r.x1() - r.x0();
        int alongLen = ax ? r.x1() - r.x0() : r.z1() - r.z0();
        if (across < 2 || across > acrossLen - 2) return false;
        int k = Math.floorMod(across - 2, 5);
        if (k > 1) return false;
        if (along < 2 || along > alongLen - 2) return false;
        return Math.floorMod(along - 2, 9) < 7 && free(r, x, z);
    }

    /** The flooded stacks: shelves standing in shallow sea water, lamps glowing under it, lanterns hung over the aisles. */
    static boolean floodedStacks(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        if (fo == -1) {
            boolean lamp = !c.lightless && Math.floorMod(x * 7 + z * 3, 11) == 0;
            c.set(bx, walk - 1, bz, lamp ? s("minecraft:sea_lantern") : s("minecraft:dark_prismarine"));
            c.set(bx, walk, bz, s("minecraft:water"));
            if (Hash.unit(r.seed, x, z * 3) < 0.08) c.set(bx, walk, bz, s("minecraft:sea_pickle[pickles=2,waterlogged=true]"));
            return true;
        }
        if (stack(r, x, z)) {
            int top = Math.min(ceil - 2, walk + 5);
            for (int y = walk; y < top; y++) c.set(bx, y, bz, Hash.unit(r.seed, x * 3 + y, z) < 0.3 ? GatesRegistry.SODDEN_SHELF.get().defaultBlockState() : s("minecraft:bookshelf"));
            c.set(bx, top, bz, s("minecraft:dark_prismarine_slab[type=bottom]"));
            if (!c.lightless && Math.floorMod(x + z, 7) == 0) c.set(bx, top + 1, bz, s("minecraft:candle[candles=3,lit=true]"));
            return true;
        }
        return false;
    }

    static boolean readingTable(Room r, double dx, double dz) {
        return Math.abs(Math.abs(dx) - 4.0) < 0.6 && Math.abs(Math.abs(dz) - 4.0) < 1.6;
    }

    static boolean wallShelf(Room r, int x, int z) {
        return false;
    }

    /** The reading room: reading tables in the quarters with open books and lamps, a chandelier of lanterns overhead. */
    static boolean readingRoom(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        if (readingTable(r, dx, dz) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:dark_oak_planks"));
            boolean end = Math.abs(Math.abs(dz) - 4.0) > 1.0;
            c.set(bx, walk + 1, bz, end ? (c.lightless ? s("minecraft:white_carpet") : s("minecraft:candle[candles=2,lit=true]")) : s("minecraft:white_carpet"));
            return true;
        }
        // chairs at the tables' sides
        if (Math.abs(Math.abs(dx) - 2.5) < 0.6 && Math.abs(Math.abs(dz) - 4.0) < 0.6 && free(r, x, z)) {
            c.set(bx, walk, bz, GatePainter.facing(s("minecraft:dark_oak_stairs"), dx > 0 ? 1 : 3));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Clockwork Foundry

    /** The gear wall goes on the first wall (N, E, S, W from the room's seed) with no door in it; -1 if all have one. */
    static int gearWallSide(Room r) {
        boolean[] door = new boolean[4];
        for (Room.Door d : r.doors) door[d.side] = true;
        int start = (int) Math.floorMod(r.seed, 4L);
        for (int k = 0; k < 4; k++) if (!door[(start + k) % 4]) return (start + k) % 4;
        return -1;
    }

    /** A face column of the gear wall (d = the room's direction from it). */
    static boolean gearWallColumn(Room r, int x, int z, int d) {
        int along = (d == 0 || d == 2) ? x : z;
        int lo = (d == 0 || d == 2) ? r.x0() : r.z0(), hi = (d == 0 || d == 2) ? r.x1() : r.z1();
        return along > lo && along < hi;
    }

    /** Gears in the plane of a wall: (u along the wall from its middle, v up from the floor). 1 tooth/rim, 2 spoke, 3 hub. */
    static int gearAt(Room r, double u, double v, int h) {
        double span = ((r.gw + r.gh) * 16) / 2.0;
        double[][] gears = {{-span * 0.18, h * 0.48, Math.min(h * 0.42, 4.8)}, {span * 0.2, h * 0.36, Math.min(h * 0.3, 3.4)},
                {span * 0.05, h * 0.82, 2.2}};
        for (double[] g : gears) {
            double gu = u - g[0], gv = v - g[1];
            double rr = Math.hypot(gu, gv), R = g[2];
            if (rr > R + 0.9) continue;
            double a = Math.atan2(gv, gu);
            boolean tooth = Math.floorMod((int) Math.floor((a + Math.PI) / (2 * Math.PI) * Math.max(8, R * 4)), 2) == 0;
            if (rr <= 0.75) return 3;
            if (rr > R + 0.2) return tooth ? 1 : 0;
            if (rr > R - 0.9) return 1;
            if (Math.abs(gu) < 0.55 || Math.abs(gv) < 0.55) return 2;
            return 4; // open between the spokes: the dark works behind
        }
        return 0;
    }

    static void gearWall(GatePainter.Ctx c, int bx, int bz, int x, int z, int d) {
        Room r = c.r;
        int h = c.wallH(x, z, d);
        double u = ((d == 0 || d == 2) ? x : z) + 0.5 - ((d == 0 || d == 2) ? cx(r) : cz(r));
        for (int y = c.yLo; y <= c.yHi; y++) {
            int rel = y - c.floorY;
            if (rel < 0 || rel > h + 1) {
                c.set(bx, y, bz, c.p.fill());
                continue;
            }
            int g = rel >= h - 1 ? 0 : gearAt(r, u, rel + 0.5, h);
            BlockState s = switch (g) {
                case 1 -> s("minecraft:waxed_copper_block");
                case 2 -> s("minecraft:waxed_cut_copper");
                case 3 -> c.lightless ? GatesRegistry.GEAR.get().defaultBlockState() : s("minecraft:waxed_copper_bulb[lit=true]");
                case 4 -> s("minecraft:polished_blackstone");
                default -> rel == 0 ? c.k.trim() : rel == h - 1 ? s("minecraft:iron_block") : s("minecraft:waxed_oxidized_cut_copper");
            };
            c.set(bx, y, bz, s);
        }
    }

    /** Crates riding the belt every few blocks. */
    static boolean conveyorCargo(Room r, int x, int z) {
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        int along = ax ? x - r.x0() : z - r.z0();
        return Math.abs(across - 3.0) < 1.0 && Math.floorMod(along, 6) == 1 && free(r, x, z);
    }

    /**
     * The conveyor line: a belt two wide down the room carrying crates, a gantry rail under the ceiling with chains and
     * hooks, a press over the belt; doorways cross the belt on grates.
     */
    static boolean conveyor(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        int along = ax ? x - r.x0() : z - r.z0();
        int len = ax ? r.x1() - r.x0() : r.z1() - r.z0();
        boolean belt = Math.abs(across - 3.0) < 1.0 && along >= 1 && along <= len - 1;
        boolean claimed = false;
        if (belt) {
            c.set(bx, walk - 1, bz, free(r, x, z) ? GatePainter.axis(GatesRegistry.CONVEYOR.get().defaultBlockState(), ax ? "x" : "z")
                    : s("minecraft:waxed_copper_grate"));
            if (conveyorCargo(r, x, z)) {
                c.set(bx, walk, bz, Hash.unit(r.seed, along, 3) < 0.5 ? s("minecraft:waxed_copper_block") : s("minecraft:iron_block"));
                claimed = true;
            }
        }
        // the gantry: a rail under the ceiling over the belt, chains with hooks down to the crates
        int gy = ceil - 1;
        if (Math.abs(across - 3.0) < 0.6) {
            c.set(bx, gy, bz, s("minecraft:iron_block"));
            if (Math.floorMod(along, 6) == 4 && ceil - walk > 7) {
                for (int y = gy - 1; y > walk + 3; y--) c.set(bx, y, bz, s("minecraft:waxed_copper_chain[axis=y]"));
                c.set(bx, walk + 3, bz, s("minecraft:waxed_lightning_rod[facing=down]"));
            }
            if (along == len / 2 && ceil - walk > 6) {
                c.set(bx, walk + 3, bz, s("minecraft:piston[facing=down]"));
                c.set(bx, walk + 4, bz, s("minecraft:iron_block"));
            }
            claimed = true;
        }
        if (!c.lightless && Math.abs(across + 3.0) < 0.6 && Math.floorMod(along, 6) == 3) {
            c.set(bx, gy, bz, s("minecraft:iron_block"));
            c.set(bx, gy - 1, bz, s("minecraft:waxed_copper_lantern[hanging=true]"));
        }
        return claimed;
    }

    /** The boiler: a copper drum to the ceiling, banded, its gauges lit, pipes running off to the walls overhead. */
    static boolean boiler(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        double d = Math.hypot(dx, dz);
        if (d < 3.0) {
            for (int y = walk; y < ceil; y++) {
                int rel = y - walk;
                BlockState s = d < 2.0 ? s("minecraft:waxed_copper_block")
                        : rel % 4 == 0 ? s("minecraft:iron_block")
                        : (!c.lightless && rel % 4 == 2 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), 90) < 25)
                        ? s("minecraft:waxed_copper_bulb[lit=true]") : s("minecraft:waxed_cut_copper");
                c.set(bx, y, bz, s);
            }
            return true;
        }
        // pipes from the drum to the walls, two blocks under the ceiling, along the room's centre lines
        if ((Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6) && ceil - walk > 7) {
            c.set(bx, ceil - 2, bz, GatePainter.axis(s("minecraft:waxed_copper_chain"), Math.abs(dx) < 0.6 ? "z" : "x"));
            c.set(bx, ceil - 1, bz, s("minecraft:iron_block"));
        }
        if (d < 5.0) c.set(bx, walk - 1, bz, s("minecraft:waxed_copper_grate"));
        return false;
    }

    // =================================================================================================================
    // Glass Desert

    /** Glass crystals: {height} of the crystal growing in this column, 0 if none. */
    static int crystal(Room r, double dx, double dz) {
        double R = radius(r);
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6 + (Hash.unit(r.seed, k, 71) - 0.5) * 0.5;
            double rr = R * (0.45 + Hash.unit(r.seed, k, 72) * 0.25);
            double cxk = Math.round(Math.cos(a) * rr), czk = Math.round(Math.sin(a) * rr);
            if (Math.abs(dx - cxk) < 1 && Math.abs(dz - czk) < 1) {
                int sub = (int) Math.floorMod(Math.round(dx - cxk + 0.5) * 2 + Math.round(dz - czk + 0.5), 4L);
                return 2 + (int) (Hash.unit(r.seed, k, 73 + sub) * 4);
            }
        }
        return 0;
    }

    /** Sand drifted up against the walls. */
    static boolean drift(Room r, double dx, double dz) {
        double R = radius(r);
        double d = Math.max(Math.abs(dx) / hw(r), Math.abs(dz) / hd(r));
        return d > 0.72 + Hash.noise(r.seed, dx * 3, dz * 3, 4) * 0.2;
    }

    /** The glass garden: crystals of fused sand, glass and amethyst grow out of the drifts, glowing at their roots. */
    static boolean glassGarden(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        int h = crystal(r, dx, dz);
        if (fo == 1) c.set(bx, walk - 1, bz, s("minecraft:sand"));
        if (h > 0 && free(r, x, z)) {
            c.set(bx, walk - 1, bz, c.lightless ? s("minecraft:sandstone") : s("minecraft:glowstone"));
            for (int i = 0; i < h && walk + i < ceil - 1; i++) {
                double t = Hash.unit(r.seed, x * 31 + i, z);
                c.set(bx, walk + i, bz, i == h - 1 ? s("minecraft:amethyst_cluster[facing=up]")
                        : t < 0.4 ? GatesRegistry.SANDGLASS.get().defaultBlockState() : t < 0.75 ? s("minecraft:glass") : s("minecraft:tinted_glass"));
            }
            return true;
        }
        if (fo == 0 && Hash.unit(r.seed, x, z * 5) < 0.04 && free(r, x, z)) c.set(bx, walk, bz, s("minecraft:dead_bush"));
        return fo == 1;
    }

    /** The colonnade: 1 standing column, 2 broken column, 0 none. Two rows the long way, four blocks from the walls. */
    static int column(Room r, int x, int z) {
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        int along = ax ? x - r.x0() : z - r.z0();
        int len = ax ? r.x1() - r.x0() : r.z1() - r.z0();
        double halfAcross = ax ? hd(r) : hw(r);
        if (Math.abs(Math.abs(across) - (halfAcross - 3.5)) > 0.5) return 0;
        if (Math.floorMod(along - 2, 5) != 0 || along < 2 || along > len - 2) return 0;
        if (!free(r, x, z)) return 0;
        return Hash.unit(r.seed, along, across > 0 ? 1 : 0) < 0.3 ? 2 : 1;
    }

    static boolean dune(Room r, int x, int z) {
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        double halfAcross = ax ? hd(r) : hw(r);
        return Math.abs(across) > halfAcross - 2.2 + Hash.noise(r.seed, x, z, 4) * 1.5 && column(r, x, z) == 0;
    }

    /** The buried colonnade: sandstone columns, some broken, half drowned in drifts; an obelisk fallen across the floor. */
    static boolean colonnade(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil, int fo) {
        Room r = c.r;
        int col = column(r, x, z);
        if (fo == 1) c.set(bx, walk - 1, bz, s("minecraft:sand"));
        if (col > 0) {
            int top = col == 2 ? walk + 1 + (int) (Hash.unit(r.seed, x, z) * 3) : ceil - 1;
            // every other column stands on a sun tile that still holds the heat
            boolean sun = !c.lightless && Math.floorMod((alongX(r) ? x : z) - 2, 10) == 0;
            c.set(bx, walk, bz, sun ? GatesRegistry.SUN_TILE.get().defaultBlockState() : s("minecraft:cut_sandstone"));
            for (int y = walk + 1; y < top; y++) c.set(bx, y, bz, s("minecraft:smooth_sandstone"));
            c.set(bx, top, bz, col == 2 ? s("minecraft:sandstone_slab[type=bottom]") : s("minecraft:chiseled_sandstone"));
            return true;
        }
        // the fallen obelisk: one block high, lying on the floor across one quarter
        double dx = dx(r, x), dz = dz(r, z);
        if (Math.abs(dz - dx * 0.5 - 2.5) < 0.6 && dx > 1.5 && dx < hw(r) - 3 && free(r, x, z) && fo == 0) {
            c.set(bx, walk, bz, dx > hw(r) - 4 ? GatesRegistry.SUN_TILE.get().defaultBlockState() : s("minecraft:chiseled_sandstone"));
            return true;
        }
        return fo == 1;
    }

    // =================================================================================================================
    // Hive

    static boolean combPillar(Room r, double dx, double dz) {
        return ringPoint(dx, dz, radius(r) * 0.6, 6, 0.5, 1.6);
    }

    /** The brood comb: six hexagonal comb pillars round a royal cell glowing gold; honey drips from the vault. */
    static boolean broodComb(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        if (d < 2.0) {
            for (int y = walk; y < ceil; y++) {
                int rel = y - walk;
                c.set(bx, y, bz, rel >= 1 && rel <= 3 && d < 1.2 && !c.lightless ? GatesRegistry.ROYAL_JELLY.get().defaultBlockState()
                        : rel % 3 == 0 ? s("minecraft:chiseled_resin_bricks") : s("minecraft:honeycomb_block"));
            }
            return true;
        }
        if (combPillar(r, dx, dz) && free(r, x, z)) {
            for (int y = walk; y < ceil; y++) {
                int rel = y - walk;
                c.set(bx, y, bz, (rel + x + z) % 3 == 0 ? GatesRegistry.BROOD_CELL.get().defaultBlockState() : s("minecraft:honeycomb_block"));
            }
            return true;
        }
        if (ringPoint(dx, dz, radius(r) * 0.6, 6, 0.5, 2.6)) c.set(bx, walk, bz, s("minecraft:honey_block"));
        return false;
    }

    static boolean honeyPool(Room r, double dx, double dz) {
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            double px = Math.cos(a) * 4.2, pz = Math.sin(a) * 4.2;
            if (Math.hypot(dx - px, dz - pz) < 1.9) return true;
        }
        return false;
    }

    /** Honey pools: four sunken pools of honey in the quarters, wax candles round them, comb hanging from the ceiling. */
    static boolean honeyPools(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        if (fo == -1) {
            c.set(bx, walk - 1, bz, s("minecraft:ochre_froglight"));
            c.set(bx, walk, bz, s("minecraft:honey_block"));
            return true;
        }
        Room r = c.r;
        boolean rim = false;
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            double dd = Math.hypot(dx - Math.cos(a) * 4.2, dz - Math.sin(a) * 4.2);
            if (dd >= 1.9 && dd < 2.7) rim = true;
        }
        if (rim && free(r, x, z)) {
            c.set(bx, walk - 1, bz, s("minecraft:resin_bricks"));
            if (!c.lightless && Hash.unit(r.seed, x, z) < 0.25) c.set(bx, walk, bz, s("minecraft:yellow_candle[candles=2,lit=true]"));
        }
        return false;
    }

    // =================================================================================================================
    // Overgrown Temple

    /**
     * The tree that split the temple: a trunk rising through the floor into the vault, buttress roots running out
     * between the doorways, a canopy of leaves under the ceiling hung with glowing vines.
     */
    static boolean rootTree(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        double a = Math.atan2(dz, dx);
        boolean claimed = false;
        if (d < 3.0) {
            for (int y = walk - 1; y <= ceil; y++) c.set(bx, y, bz, d < 2.0 ? s("minecraft:jungle_wood[axis=y]") : s("minecraft:jungle_log[axis=y]"));
            return true;
        }
        // buttress roots: four of them on the diagonals, a block high, sinking toward their tips
        double diag = Math.abs(Math.sin(2 * a));
        if (diag > 0.93 && d < radius(r) * 0.75 && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:mangrove_roots"));
            if (d < 5.0) c.set(bx, walk + 1, bz, s("minecraft:jungle_log[axis=y]"));
            claimed = true;
        }
        // the canopy: leaves in a thick band under the vault, glow berries hanging out of it
        double canopy = radius(r) * 0.8;
        if (d < canopy && ceil - walk > 8) {
            int depth = d < canopy * 0.6 ? 2 : 1;
            for (int i = 1; i <= depth; i++) c.set(bx, ceil - i, bz, s("minecraft:jungle_leaves[persistent=true]"));
            if (!c.lightless && Hash.unit(r.seed, x, z * 11) < 0.12) {
                int len = 1 + (int) (Hash.unit(r.seed, z, x) * 3);
                for (int i = 1; i <= len; i++) c.set(bx, ceil - depth - i, bz, s(i == len ? "minecraft:cave_vines[berries=true]" : "minecraft:cave_vines_plant[berries=true]"));
            }
            claimed = true;
        }
        if (d < 5.5) c.set(bx, walk - 1, bz, s("minecraft:moss_block"));
        return claimed;
    }

    static boolean reflectingPool(Room r, double dx, double dz) {
        return Math.abs(dx) > 2.5 && Math.abs(dx) < hw(r) - 1.5 && Math.abs(dz) > 2.5 && Math.abs(dz) < hd(r) - 1.5;
    }

    /** The reflecting pools: four still pools in the quarters, lily pads, lights under the water, stone rims. */
    static boolean pool(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        if (fo == -1) {
            c.set(bx, walk - 1, bz, !c.lightless && Math.floorMod(x + z * 3, 7) == 0 ? s("minecraft:verdant_froglight") : s("minecraft:mossy_stone_bricks"));
            c.set(bx, walk, bz, s("minecraft:water"));
            if (Hash.unit(r.seed, x, z) < 0.15) c.set(bx, walk + 1, bz, s("minecraft:lily_pad"));
            return true;
        }
        if (Math.abs(dx) <= 2.5 && Math.abs(dz) <= 2.5 && Math.abs(dx) > 1.5 && Math.abs(dz) > 1.5 && free(r, x, z)) {
            c.set(bx, walk, bz, GatesRegistry.JADE.get().defaultBlockState());
            if (!c.lightless) c.set(bx, walk + 1, bz, s("minecraft:lantern[hanging=false]"));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Lantern Night Market

    /** Stalls along the walls: 1 counter, 2 under the awning (walkable), 0 elsewhere. */
    static int stall(Room r, int x, int z) {
        if (!RoomGeometry.inside(r, x, z)) return 0;
        // distance to the room's own walls (not RoomGeometry.edgeDistance, which asks the pieces in turn)
        int edge = 2;
        for (int d = 1; d <= 2 && edge == 2; d++) {
            if (!RoomGeometry.inside(r, x + d, z) || !RoomGeometry.inside(r, x - d, z) || !RoomGeometry.inside(r, x, z + d) || !RoomGeometry.inside(r, x, z - d)) edge = d - 1;
        }
        if (edge > 1) return 0;
        if (RoomGeometry.onDoorAxis(r, x, z, 3)) return 0;
        return edge == 0 ? 1 : 2;
    }

    /** The market street: stalls along the walls under striped awnings, goods on the counters, strings of paper lanterns overhead. */
    static boolean marketStalls(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        int st = stall(r, x, z);
        boolean claimed = false;
        if (st > 0) {
            int awn = Math.min(ceil - 2, walk + 3);
            boolean stripe = Math.floorMod(x + z, 2) == 0;
            c.set(bx, awn, bz, s(stripe ? "minecraft:red_wool" : "minecraft:white_wool"));
            if (st == 1) {
                c.set(bx, walk, bz, s("minecraft:spruce_planks"));
                double g = Hash.unit(r.seed, x, z);
                c.set(bx, walk + 1, bz, s(g < 0.2 ? "minecraft:melon" : g < 0.35 ? "minecraft:pumpkin" : g < 0.5 ? "minecraft:hay_block"
                        : g < 0.65 ? "minecraft:potted_red_tulip" : g < 0.8 ? "minecraft:flower_pot" : "minecraft:cake"));
            } else if (!c.lightless && Math.floorMod(x + z, 4) == 1) c.set(bx, awn - 1, bz, s("minecraft:lantern[hanging=true]"));
            claimed = true;
        }
        // strings of paper lanterns crossing the room, sagging in the middle
        double dz = dz(r, z), dx = dx(r, x);
        int ox = Math.floorMod(x, 16), oz = Math.floorMod(z, 16);
        if ((oz == 5 || oz == 10) && ceil - walk > 6) {
            double sag = 1 - (dx * dx) / (hw(r) * hw(r));
            int y = ceil - 1 - (int) Math.round(sag * 2);
            c.set(bx, y, bz, s("minecraft:iron_chain[axis=x]"));
            if (!c.lightless && Math.floorMod(x, 3) == 0) c.set(bx, y - 1, bz, GatesRegistry.PAPER_LANTERN.get().defaultBlockState());
            claimed = true;
        }
        return claimed;
    }

    /** The lantern tree: a dark oak in the middle of the square hung with paper lanterns, benches round it. */
    static boolean lanternTree(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        if (d < 2.6) {
            int top = ceil - 1;
            for (int y = walk; y <= top; y++) c.set(bx, y, bz, d < 1.5 ? s("minecraft:dark_oak_log[axis=y]") : y > walk + 4 ? s("minecraft:dark_oak_leaves[persistent=true]") : s("minecraft:dark_oak_wood[axis=y]"));
            return true;
        }
        boolean claimed = false;
        double crown = Math.min(radius(r) * 0.75, 9);
        int crownY = ceil - 3;
        if (d < crown && crownY > walk + 5) {
            double t = d / crown;
            int thick = t < 0.5 ? 3 : t < 0.8 ? 2 : 1;
            for (int i = 0; i < thick; i++) c.set(bx, crownY + i, bz, s("minecraft:dark_oak_leaves[persistent=true]"));
            if (Math.abs(dx) < 0.6 || Math.abs(dz) < 0.6 || Math.abs(Math.abs(dx) - Math.abs(dz)) < 0.6)
                c.set(bx, crownY + thick, bz, GatePainter.axis(s("minecraft:dark_oak_log"), Math.abs(dx) > Math.abs(dz) ? "x" : "z"));
            if (!c.lightless && ringPoint(dx, dz, crown * 0.7, 10, 0, 0.5)) {
                c.set(bx, crownY - 1, bz, s("minecraft:iron_chain[axis=y]"));
                c.set(bx, crownY - 2, bz, GatesRegistry.PAPER_LANTERN.get().defaultBlockState());
            }
            claimed = true;
        }
        // a ring of benches round the trunk
        if (Math.abs(d - 4.2) < 0.5 && free(r, x, z)) {
            c.set(bx, walk, bz, GatePainter.facing(s("minecraft:spruce_stairs"), Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0)));
            return true;
        }
        return claimed;
    }

    // =================================================================================================================
    // Frost Monastery

    static boolean cloisterCourt(Room r, int x, int z) {
        return Math.abs(dx(r, x)) < hw(r) - 4 && Math.abs(dz(r, z)) < hd(r) - 4;
    }

    static boolean cloisterPillar(Room r, int x, int z) {
        double ax = Math.abs(dx(r, x)), az = Math.abs(dz(r, z));
        double px = hw(r) - 4, pz = hd(r) - 4;
        boolean onX = Math.abs(ax - px) < 0.5 && az <= pz + 0.5, onZ = Math.abs(az - pz) < 0.5 && ax <= px + 0.5;
        if (!onX && !onZ) return false;
        int along = onX ? z : x;
        return Math.floorMod(along, 4) == 1;
    }

    /**
     * The cloister: an arcade of quartz pillars round a frozen garden open to a higher vault; snow drifts in the garden,
     * a pond of blue ice, a lantern post in the middle.
     */
    static boolean cloister(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        double ax = Math.abs(dx(r, x)), az = Math.abs(dz(r, z));
        double px = hw(r) - 4, pz = hd(r) - 4;
        boolean arcade = (Math.abs(ax - px) < 0.5 && az <= pz + 0.5) || (Math.abs(az - pz) < 0.5 && ax <= px + 0.5);
        int archY = Math.min(walk + 5, ceil - 2);
        if (cloisterPillar(r, x, z) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:quartz_bricks"));
            for (int y = walk + 1; y < archY; y++) c.set(bx, y, bz, s("minecraft:quartz_pillar[axis=y]"));
            c.set(bx, archY, bz, s("minecraft:chiseled_quartz_block"));
            for (int y = archY + 1; y < ceil; y++) c.set(bx, y, bz, s("minecraft:quartz_bricks"));
            return true;
        }
        if (arcade) {
            c.set(bx, archY, bz, s("minecraft:quartz_bricks"));
            c.set(bx, archY - 1, bz, GatePainter.facing(s("minecraft:quartz_stairs[half=top]"), ax > az ? (dx(r, x) > 0 ? 1 : 3) : (dz(r, z) > 0 ? 2 : 0)));
            for (int y = archY + 1; y < ceil; y++) c.set(bx, y, bz, s("minecraft:quartz_bricks"));
            return true;
        }
        if (cloisterCourt(r, x, z)) {
            double d = Math.hypot(dx(r, x), dz(r, z));
            if (d < 2.6 && d > 1.4) c.set(bx, walk - 1, bz, s("minecraft:blue_ice"));
            else if (d >= 2.6) {
                c.set(bx, walk - 1, bz, s("minecraft:snow_block"));
                if (free(r, x, z) && Hash.unit(r.seed, x, z) < 0.5) c.set(bx, walk, bz, s("minecraft:snow[layers=" + (1 + (int) (Hash.unit(r.seed, z, x) * 3)) + "]"));
            }
            if (d < 0.8 && Math.abs(dx(r, x)) < 0.6 && Math.abs(dz(r, z)) < 0.6) {
                // the lantern post (only on the centre column if no doorway axis runs through it)
                if (free(r, x, z)) {
                    for (int y = walk; y < walk + 3; y++) c.set(bx, y, bz, s("minecraft:quartz_pillar[axis=y]"));
                    if (!c.lightless) c.set(bx, walk + 3, bz, s("minecraft:lantern[hanging=false]"));
                }
            }
            return false;
        }
        return false;
    }

    /** Choir stalls: rows of seats along the two long walls, facing in. 1 seat, 2 back rail, 0 none. */
    static int choirStall(Room r, int x, int z) {
        boolean ax = alongX(r);
        double across = ax ? dz(r, z) : dx(r, x);
        double half = ax ? hd(r) : hw(r);
        double along = ax ? dx(r, x) : dz(r, z);
        double alongHalf = ax ? hw(r) : hd(r);
        if (Math.abs(along) > alongHalf - 1.5) return 0;
        if (!free(r, x, z)) return 0;
        if (Math.abs(Math.abs(across) - (half - 2.5)) < 0.5) return 1;
        if (Math.abs(Math.abs(across) - (half - 1.5)) < 0.5) return 2;
        return 0;
    }

    /** The choir: stalls of quartz stairs face each other across the room, candles on the rail, a lectern of ice between. */
    static boolean choir(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        int st = choirStall(r, x, z);
        boolean ax = alongX(r);
        double across = ax ? dz : dx;
        if (st == 1) {
            int face = ax ? (across > 0 ? 0 : 2) : (across > 0 ? 3 : 1);
            c.set(bx, walk, bz, GatePainter.facing(s("minecraft:quartz_stairs"), (face + 2) % 4));
            return true;
        }
        if (st == 2) {
            c.set(bx, walk, bz, s("minecraft:quartz_bricks"));
            c.set(bx, walk + 1, bz, s("minecraft:quartz_slab[type=bottom]"));
            if (!c.lightless && Math.floorMod(x + z, 3) == 0) c.set(bx, walk + 2, bz, s("minecraft:white_candle[candles=2,lit=true]"));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Mirror Palace

    static boolean mirrorPanel(Room r, double dx, double dz) {
        double d = Math.hypot(dx, dz);
        double R = Math.min(radius(r) - 1.5, 4.6);
        if (Math.abs(d - R) > 0.55) return false;
        double a = Math.atan2(dz, dx) / (Math.PI / 4);
        double frac = a - Math.floor(a);
        return frac > 0.25 && frac < 0.75;
    }

    /** The reflecting hall: eight mirrors framed in quartz stand in a ring facing a prism of light at the centre. */
    static boolean mirrorHall(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        if (d < 1.6) {
            int top = Math.min(ceil - 1, walk + 4);
            c.set(bx, walk, bz, s("minecraft:chiseled_quartz_block"));
            for (int y = walk + 1; y < top; y++) c.set(bx, y, bz, c.lightless ? s("minecraft:glass") : s("minecraft:sea_lantern"));
            c.set(bx, top, bz, s("minecraft:amethyst_cluster[facing=up]"));
            return true;
        }
        if (mirrorPanel(r, dx, dz) && free(r, x, z)) {
            int top = Math.min(ceil - 1, walk + 4);
            c.set(bx, walk, bz, s("minecraft:chiseled_quartz_block"));
            for (int y = walk + 1; y < top; y++) c.set(bx, y, bz, GatesRegistry.MIRROR.get().defaultBlockState());
            c.set(bx, top, bz, s("minecraft:smooth_quartz_slab[type=bottom]"));
            return true;
        }
        return false;
    }

    static boolean mazeWall(Room r, int x, int z) {
        int lx = x - r.x0(), lz = z - r.z0();
        if (lx < 2 || lz < 2 || lx > r.x1() - r.x0() - 2 || lz > r.z1() - r.z0() - 2) return false;
        boolean gx = Math.floorMod(lx - 2, 5) == 0, gz = Math.floorMod(lz - 2, 5) == 0;
        if (!gx && !gz) return false;
        if (gx && gz) return true;
        // break each wall segment somewhere so the maze has ways through
        int seg = gx ? Math.floorDiv(lz - 2, 5) : Math.floorDiv(lx - 2, 5);
        int line = gx ? lx : lz;
        int gap = 1 + (int) (Hash.unit(r.seed, line, seg) * 3);
        int pos = Math.floorMod(gx ? lz - 2 : lx - 2, 5);
        return pos != gap && Hash.unit(r.seed, line * 7, seg) < 0.75;
    }

    /** The maze of mirrors: mirror walls three high on a grid with gaps; end rods light it from above. */
    static boolean mirrorMaze(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        if (mazeWall(r, x, z) && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:quartz_bricks"));
            for (int y = walk + 1; y < walk + 3; y++) c.set(bx, y, bz, GatesRegistry.MIRROR.get().defaultBlockState());
            c.set(bx, walk + 3, bz, s("minecraft:chiseled_quartz_block"));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Precursor Lab

    /** Specimen tanks: 1 glass, 2 water core, 0 none. One 3x3 tank in each quarter no doorway crosses. */
    static int tank(Room r, double dx, double dz) {
        double qx = Math.signum(dx) * 4.5, qz = Math.signum(dz) * 4.5;
        double lx = Math.abs(dx - qx), lz = Math.abs(dz - qz);
        if (lx > 1.0 || lz > 1.0) return 0;
        return lx < 0.6 && lz < 0.6 ? 2 : 1;
    }

    /** The specimen tanks: tubes of lit water in plating sleeves, something curled up in each, cables to the ceiling. */
    static boolean tanks(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        int t = tank(r, dx, dz);
        if (t == 0 || !free(r, x, z)) return false;
        // a tank is whole or not at all: its core must be free too
        int top = Math.min(ceil - 1, walk + 4);
        c.set(bx, walk, bz, t == 2 && !c.lightless ? s("minecraft:sea_lantern") : s("awaken:precursor_plating"));
        for (int y = walk + 1; y < top; y++) {
            if (t == 2) c.set(bx, y, bz, y == walk + 2 ? s("minecraft:dried_ghast") : s("minecraft:water"));
            else c.set(bx, y, bz, s("minecraft:light_blue_stained_glass"));
        }
        c.set(bx, top, bz, s("awaken:precursor_plating"));
        if (t == 2) for (int y = top + 1; y < ceil; y++) c.set(bx, y, bz, s("minecraft:iron_chain[axis=y]"));
        return true;
    }

    /** Racks in rows the long way, two high, fronts to the aisles: 1 rack, 2 cable tray, 0 none. */
    static int rack(Room r, int x, int z) {
        boolean ax = alongX(r);
        int across = ax ? z - r.z0() : x - r.x0();
        int along = ax ? x - r.x0() : z - r.z0();
        int acrossLen = ax ? r.z1() - r.z0() : r.x1() - r.x0();
        int alongLen = ax ? r.x1() - r.x0() : r.z1() - r.z0();
        if (across < 2 || across > acrossLen - 2 || along < 2 || along > alongLen - 2) return 0;
        if (Math.floorMod(across - 2, 4) != 0) return 0;
        if (Math.floorMod(along - 2, 7) == 6) return 0;
        return free(r, x, z) ? 1 : 0;
    }

    /** The server hall: rows of blinking racks, cable trays over them, light strips in the ceiling. */
    static boolean serverHall(GatePainter.Ctx c, int bx, int bz, int x, int z, int walk, int ceil) {
        Room r = c.r;
        if (rack(r, x, z) == 1) {
            boolean ax = alongX(r);
            int face = ax ? (Math.floorMod(z, 2) == 0 ? 0 : 2) : (Math.floorMod(x, 2) == 0 ? 3 : 1);
            BlockState rk = GatePainter.facing(GatesRegistry.LAB_RACK.get().defaultBlockState(), face);
            c.set(bx, walk, bz, rk);
            c.set(bx, walk + 1, bz, rk);
            c.set(bx, walk + 2, bz, s("minecraft:smooth_stone_slab[type=bottom]"));
            for (int y = walk + 3; y < ceil - 1; y++) c.set(bx, y, bz, s("minecraft:iron_chain[axis=y]"));
            c.set(bx, ceil - 1, bz, s("minecraft:iron_bars"));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Nightmare

    static boolean tableLeg(Room r, double dx, double dz) {
        double lx = hw(r) * 0.45, lz = hd(r) * 0.3;
        return Math.abs(Math.abs(dx) - lx) < 1.0 && Math.abs(Math.abs(dz) - lz) < 1.0;
    }

    static boolean chairLeg(Room r, double dx, double dz) {
        double cxp = hw(r) * 0.45, czp = hd(r) * 0.3 + 4.5;
        return Math.abs(Math.abs(dx) - cxp) < 0.6 && Math.abs(Math.abs(dz) - czp) < 0.6;
    }

    /**
     * The table you are too small for: a dining table five blocks high on legs like pillars, a candle the height of a
     * man burning on it, a teacup you could bathe in, chairs you can't climb.
     */
    static boolean giantTable(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        int topY = Math.min(walk + 5, ceil - 4);
        double tx = hw(r) * 0.45 + 1.5, tz = hd(r) * 0.3 + 1.5;
        boolean claimed = false;
        if (tableLeg(r, dx, dz) && free(r, x, z)) {
            for (int y = walk; y < topY; y++) c.set(bx, y, bz, s("minecraft:stripped_oak_log[axis=y]"));
            claimed = true;
        }
        if (Math.abs(dx) < tx && Math.abs(dz) < tz) {
            c.set(bx, topY, bz, s("minecraft:oak_planks"));
            boolean cloth = Math.abs(dx) < tx - 1 && Math.abs(dz) < tz - 1;
            if (cloth) c.set(bx, topY + 1, bz, s("minecraft:white_carpet"));
            // the candle and the cup
            if (Math.abs(dx - 2.5) < 0.6 && Math.abs(dz) < 0.6) {
                for (int y = topY + 1; y < topY + 4 && y < ceil - 1; y++) c.set(bx, y, bz, s("minecraft:white_concrete"));
                if (!c.lightless) c.set(bx, Math.min(topY + 4, ceil - 1), bz, s("minecraft:shroomlight"));
            }
            if (Math.hypot(dx + 2.5, dz) < 1.6 && Math.hypot(dx + 2.5, dz) > 0.6) c.set(bx, topY + 1, bz, s("minecraft:white_terracotta"));
            claimed = true;
        }
        // the chairs: four legs, a seat at the table's height minus one, a tall back
        double cxp = hw(r) * 0.45, czp = hd(r) * 0.3 + 4.5;
        if (Math.abs(Math.abs(dx) - cxp) < 1.6 && Math.abs(Math.abs(dz) - czp) < 1.6) {
            if (chairLeg(r, dx, dz) && free(r, x, z)) for (int y = walk; y < topY - 1; y++) c.set(bx, y, bz, s("minecraft:oak_log[axis=y]"));
            c.set(bx, topY - 1, bz, s("minecraft:oak_planks"));
            if (Math.abs(Math.abs(dz) - czp - 1.0) < 0.6) for (int y = topY; y < Math.min(ceil - 1, topY + 4); y++) c.set(bx, y, bz, s("minecraft:oak_planks"));
            claimed = true;
        }
        return claimed;
    }

    static boolean wrongDoor(Room r, double dx, double dz) {
        return Math.abs(dz - 3.5) < 0.6 && Math.abs(Math.abs(dx) - 1.5) < 0.6;
    }

    /** The upside-down room: furniture on the ceiling, a door frame standing in the middle of nothing, books floating. */
    static boolean wrongRoom(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        boolean claimed = false;
        // a bed and a table on the ceiling
        if (dx > 1.5 && dx < 4.5 && dz > -4.5 && dz < -1.5) {
            c.set(bx, ceil - 1, bz, s(dz < -3.5 ? "minecraft:white_wool" : "minecraft:red_wool"));
            claimed = true;
        }
        if (dx < -1.5 && dx > -4.5 && dz > -4.5 && dz < -2.5) {
            c.set(bx, ceil - 1, bz, s("minecraft:oak_planks"));
            if (Math.abs(dx + 3.0) < 0.6 && Math.abs(dz + 3.5) < 0.6 && !c.lightless) c.set(bx, ceil - 2, bz, s("minecraft:lantern[hanging=false]"));
            claimed = true;
        }
        // the door that leads nowhere
        if (Math.abs(dz - 3.5) < 0.6 && Math.abs(dx) < 2.1 && free(r, x, z)) {
            if (wrongDoor(r, dx, dz)) for (int y = walk; y < walk + 3; y++) c.set(bx, y, bz, s("minecraft:oak_log[axis=y]"));
            c.set(bx, walk + 3, bz, s("minecraft:oak_planks"));
            claimed = true;
        }
        // books hanging in the air
        if (Hash.unit(r.seed, x, z * 13) < 0.04 && ceil - walk > 5 && free(r, x, z)) {
            c.set(bx, walk + 2 + (int) (Hash.unit(r.seed, z, x) * 3), bz, s("minecraft:bookshelf"));
            claimed = true;
        }
        return claimed;
    }

    // =================================================================================================================
    // Sky Archipelago

    static boolean isleTree(Room r, double dx, double dz) {
        double a = Math.PI / 4 + (Hash.unit(r.seed, 0, 31) - 0.5) * 0.5;
        double rr = radius(r) * (0.52 + Hash.unit(r.seed, 0, 32) * 0.12);
        return Math.abs(dx - Math.round(Math.cos(a) * rr)) < 0.6 && Math.abs(dz - Math.round(Math.sin(a) * rr)) < 0.6;
    }

    /** Floating isles: round islands over the cloud sea, a birch on one, rope bridges where the doorways run, lamp posts. */
    static boolean isles(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        Room r = c.r;
        if (fo == -3) {
            // the gap: clouds below
            if (Hash.unit(r.seed, x, z) < 0.25) c.set(bx, walk, bz, GatesRegistry.CLOUD.get().defaultBlockState());
            return true;
        }
        double a = Math.PI / 4 + (Hash.unit(r.seed, 0, 31) - 0.5) * 0.5;
        double rr = radius(r) * (0.52 + Hash.unit(r.seed, 0, 32) * 0.12);
        double tx = dx - Math.round(Math.cos(a) * rr), tz = dz - Math.round(Math.sin(a) * rr);
        if (isleTree(r, dx, dz)) {
            int top = Math.min(ceil - 3, walk + 5);
            for (int y = walk; y < top; y++) c.set(bx, y, bz, s("minecraft:birch_log[axis=y]"));
            for (int y = top; y < top + 2; y++) c.set(bx, y, bz, s("minecraft:birch_leaves[persistent=true]"));
            return true;
        }
        double td = Math.hypot(tx, tz);
        if (td < 2.6 && td > 0.5) {
            int top = Math.min(ceil - 3, walk + 5);
            for (int y = top - 1; y < top + 2 - (td > 1.8 ? 1 : 0); y++) c.set(bx, y, bz, s("minecraft:birch_leaves[persistent=true]"));
        }
        if (!c.lightless && Math.hypot(dx, dz) > 2 && Hash.unit(r.seed, x * 3, z) < 0.012 && free(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:birch_fence"));
            c.set(bx, walk + 1, bz, s("minecraft:birch_fence"));
            c.set(bx, walk + 2, bz, s("minecraft:lantern[hanging=false]"));
            return true;
        }
        return false;
    }

    static boolean windPillar(double dx, double dz, Room r) {
        return ringPoint(dx, dz, Math.min(radius(r) * 0.55, 7.5), 8, 0.5, 0.6);
    }

    /** The wind shrine: a ring of white pillars with streamers, a beam of light over a pedestal, clouds banked round it. */
    static boolean windShrine(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        double d = Math.hypot(dx, dz);
        double ringR = Math.min(radius(r) * 0.55, 7.5);
        if (d < 2.0) {
            c.set(bx, walk, bz, s("minecraft:white_concrete"));
            c.set(bx, walk + 1, bz, d < 1.0 ? (c.lightless ? s("minecraft:glass") : s("minecraft:glowstone")) : s("minecraft:smooth_quartz_slab[type=bottom]"));
            if (d < 1.0 && !c.lightless) for (int y = walk + 2; y < ceil; y += 2) c.set(bx, y, bz, s("minecraft:end_rod[facing=up]"));
            return true;
        }
        if (windPillar(dx, dz, r) && free(r, x, z)) {
            int top = ceil - 1;
            for (int y = walk; y < top; y++) c.set(bx, y, bz, s("minecraft:white_concrete"));
            c.set(bx, top, bz, s("minecraft:quartz_bricks"));
            return true;
        }
        // streamers hanging off the ring beam between the pillars
        if (Math.abs(d - ringR) < 0.55 && ceil - walk > 7) {
            c.set(bx, ceil - 1, bz, s("minecraft:quartz_bricks"));
            if (Math.floorMod(x + z, 2) == 0) c.set(bx, ceil - 2, bz, s("minecraft:white_wool"));
            if (Math.floorMod(x + z, 4) == 0) c.set(bx, ceil - 3, bz, s("minecraft:light_blue_wool"));
            return true;
        }
        return false;
    }

    // =================================================================================================================
    // Abyssal Shrine

    /** The rift: the floor falls away into stars at the centre; spikes of obsidian lean in over its rim. */
    static boolean voidRift(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil, int fo) {
        double d = Math.hypot(dx, dz);
        if (fo == -3) {
            c.set(bx, walk - 1, bz, GatesRegistry.VOID_GLASS.get().defaultBlockState());
            if (d > 3.6) c.set(bx, walk, bz, s("minecraft:crying_obsidian"));
            return true;
        }
        if (d >= 4.6 && d < 5.4) {
            c.set(bx, walk - 1, bz, s("minecraft:crying_obsidian"));
            if (ringPoint(dx, dz, 5.0, 10, 0.5, 0.5) && free(c.r, x, z)) {
                c.set(bx, walk, bz, s("minecraft:obsidian"));
                if (!c.lightless) c.set(bx, walk + 1, bz, s("minecraft:black_candle[candles=4,lit=true]"));
                return true;
            }
        }
        return false;
    }

    /** Monoliths: 1..h of the obsidian slab in this column, 0 none. Four of them in the quarters. */
    static int monolith(Room r, double dx, double dz) {
        double qx = Math.signum(dx) * 4.0, qz = Math.signum(dz) * 4.0;
        if (Math.abs(dx - qx) < 1.0 && Math.abs(dz - qz) < 0.6) return 5 + (int) (Hash.unit(r.seed, (int) qx, (int) qz) * 3);
        return 0;
    }

    /** The unlit monoliths: four slabs of obsidian veined with crying stone, black candles guttering at their feet. */
    static boolean monoliths(GatePainter.Ctx c, int bx, int bz, int x, int z, double dx, double dz, int walk, int ceil) {
        Room r = c.r;
        int h = monolith(r, dx, dz);
        if (h > 0 && free(r, x, z)) {
            for (int y = walk; y < Math.min(ceil - 1, walk + h); y++)
                c.set(bx, y, bz, Hash.unit(r.seed, x + y * 3, z) < 0.2 ? s("minecraft:crying_obsidian") : s("minecraft:obsidian"));
            return true;
        }
        double qx = Math.signum(dx) * 4.0, qz = Math.signum(dz) * 4.0;
        if (Math.abs(dx - qx) < 1.6 && Math.abs(dz - qz) < 1.6 && free(r, x, z)) {
            c.set(bx, walk - 1, bz, s("minecraft:sculk"));
            if (!c.lightless && Hash.unit(r.seed, x, z) < 0.35) c.set(bx, walk, bz, s("minecraft:black_candle[candles=2,lit=true]"));
            return true;
        }
        return false;
    }
}
