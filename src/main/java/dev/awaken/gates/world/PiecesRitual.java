package dev.awaken.gates.world;

import dev.awaken.gates.GatesRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ritual of the Bell's hall (SetPiece.RITUAL_HALL, docs/proposals/the-ritual.md): the scripted Bell Crypt's one
 * room, a cathedral of black stone and aged gold. The doors are at the north wall (the antechamber), the Seated One's
 * throne is carved into the south wall. Everything is a pure function of the room and a column, like every piece, so
 * the painter, the server's geometry and the story's ritual director agree on where things stand.
 *
 * <p>The hall's frame: {@code u} runs across the hall (0 on the nave's axis; block centres are half-integers because the
 * room's centre lies on a block edge), {@code w} runs from the doors (0, the first row inside) to the throne wall (43).
 * Heights are rows above the hall's floor.
 */
public final class PiecesRitual {
    private PiecesRitual() {}

    // ---- the plan (shared with the ritual director) -----------------------------------------------------------------

    /** Ceiling over the nave and the throne; the vault drops to WALL_H at the side walls. */
    public static final int VAULT_H = 27, WALL_H = 18;
    /** The rune ring round the altar, and the altar's centre. */
    public static final double RING_IN = 7.0, RING_OUT = 9.0, ALTAR_W = 22.0;
    /** The three plates: radius from the altar's centre, and each plate's own radius. */
    public static final double PLATE_R = 5.0, PLATE_SIZE = 1.7;
    /** The dais rises in three steps from DAIS_W; the Seated One sits at THRONE_W on the dais top. */
    public static final int DAIS_W = 33, DAIS_H = 3;
    public static final double THRONE_W = 38.5;
    /** Kneeling statues: six a side, on plinths this far from the nave's axis. */
    public static final double STATUE_U = 11.5;
    public static final int[] STATUE_ROWS = {4, 9, 14, 19, 24, 29};
    /** Pillars of the colonnade (2x2) at these rows and |u|. */
    private static final int[] PILLAR_ROWS = {2, 8, 14, 20, 26};
    private static final double PILLAR_U = 16.5;
    /** The words appear here: over the dais, in front of the throne, under the bell's line. */
    public static final double WORDS_W = 31.0;
    public static final int WORDS_H = 9;
    /** The bell over the altar: its lip's height and radius, and how tall it is. */
    public static final int BELL_LIP = 13, BELL_TALL = 7;
    public static final double BELL_R = 3.6;

    /** A light the director can put out and light again: floor-local x/z, rows above the floor, its lit state, order 0..1. */
    public record Light(int x, int dy, int z, BlockState lit, double order, boolean fill) {}

    /** A statue's place: floor-local block centre (x + 0.5, z + 0.5), rows above the floor it kneels on, its yaw. */
    public record Spot(double x, double z, int dy, float yaw) {}

    public static double u(Room r, int x) {
        return x + 0.5 - Pieces.cx(r);
    }

    public static int w(Room r, int z) {
        return z - r.z0();
    }

    /** Floor-local x of a point at u (block-centre u values give block-centre x). */
    public static double xAt(Room r, double u) {
        return Pieces.cx(r) + u;
    }

    public static double zAt(Room r, double w) {
        return r.z0() + w;
    }

    public static Spot throne(Room r) {
        return new Spot(xAt(r, 0), zAt(r, THRONE_W), DAIS_H, 180F);
    }

    public static Spot altar(Room r) {
        return new Spot(xAt(r, 0), zAt(r, ALTAR_W), 0, 0F);
    }

    public static List<Spot> statues(Room r) {
        List<Spot> out = new ArrayList<>();
        for (int row : STATUE_ROWS) {
            // west side faces east, east side faces west: a guard of honour across the nave
            out.add(new Spot(xAt(r, -STATUE_U), zAt(r, row + 0.5), 1, -90F));
            out.add(new Spot(xAt(r, STATUE_U), zAt(r, row + 0.5), 1, 90F));
        }
        return out;
    }

    /** Plate k's centre (floor-local, block-edge coordinates): one toward the doors, two behind the altar. */
    public static double[] plate(Room r, int k) {
        double a = Math.toRadians(-90 + 120 * k);
        return new double[]{xAt(r, Math.cos(a) * PLATE_R), zAt(r, ALTAR_W + Math.sin(a) * PLATE_R)};
    }

    /** Which plate (0..2) a column is part of, or -1. */
    public static int plateAt(Room r, int x, int z) {
        for (int k = 0; k < 3; k++) {
            double[] p = plate(r, k);
            if (Math.hypot(x + 0.5 - p[0], z + 0.5 - p[1]) < PLATE_SIZE) return k;
        }
        return -1;
    }

    /** Which of the rune ring's eight segments a column is in, or -1 (segment 0 points at the doors). */
    public static int ringSegment(Room r, int x, int z) {
        double du = u(r, x), dw = w(r, z) + 0.5 - ALTAR_W;
        double d = Math.hypot(du, dw);
        if (d < RING_IN || d > RING_OUT) return -1;
        double a = Math.atan2(dw, du) + Math.PI / 2; // 0 toward the doors (-w)
        return (int) Math.floorMod(Math.round(a / (Math.PI / 4)), 8L);
    }

    /** The doorway's centre on the hall's side (floor-local x, z of the first row inside). */
    public static int[] doorway(Room r) {
        Room.Door d = r.doors.isEmpty() ? null : r.doors.get(0);
        int x = d == null ? r.centerX() : d.centerX();
        return new int[]{x, r.z0()};
    }

    /** The blocks that seal the doorway: the inner wall layer of the door, 4 wide, 4 high (floor-local x, row, z). */
    public static List<int[]> doorBlocks(Room r) {
        List<int[]> out = new ArrayList<>();
        for (Room.Door d : r.doors) {
            for (int lat = RoomGeometry.DOOR_LO; lat <= RoomGeometry.DOOR_HI; lat++) {
                int x, z;
                switch (d.side) {
                    case 0 -> { x = d.gx * 16 + lat; z = d.gz * 16 + RoomGeometry.INSET - 1; }
                    case 2 -> { x = d.gx * 16 + lat; z = d.gz * 16 + 16 - RoomGeometry.INSET; }
                    case 1 -> { x = d.gx * 16 + 16 - RoomGeometry.INSET; z = d.gz * 16 + lat; }
                    default -> { x = d.gx * 16 + RoomGeometry.INSET - 1; z = d.gz * 16 + lat; }
                }
                for (int y = 0; y < RoomGeometry.DOOR_HEIGHT; y++) out.add(new int[]{x, d.y - r.dy + y, z, lat});
            }
        }
        return out;
    }

    // ---- geometry (RoomGeometry and Pieces dispatch here) -----------------------------------------------------------

    private static boolean pillar(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        if (au < PILLAR_U - 1 || au > PILLAR_U + 1) return false;
        int w = w(r, z);
        for (int row : PILLAR_ROWS) if (w == row || w == row + 1) return true;
        return false;
    }

    private static boolean plinth(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        if (Math.abs(au - STATUE_U) > 1.01) return false;
        int w = w(r, z);
        for (int row : STATUE_ROWS) if (Math.abs(w - row) <= 1) return true;
        return false;
    }

    private static boolean altarBlock(Room r, int x, int z) {
        return Math.abs(u(r, x)) < 2 && Math.abs(w(r, z) + 0.5 - ALTAR_W) < 2;
    }

    /** The throne's own stone: the backrest against the far wall and the two armrests. */
    private static int throneStone(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        int w = w(r, z);
        if (w >= 41 && au <= 9.5) return 14;                      // backrest, up to the figure's shoulders
        if (w >= 35 && w <= 40 && au >= 7.5 && au <= 9.5) return 7; // armrests
        return 0;
    }

    /** The figure's legs and seat: invisible, so nobody walks into the Seated One. */
    private static int barrierTop(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        int w = w(r, z);
        if (au < 7.5 && w >= 34 && w <= 40) return 10;
        return 0;
    }

    static boolean blocks(Room r, int x, int z) {
        return pillar(r, x, z) || plinth(r, x, z) || altarBlock(r, x, z) || throneStone(r, x, z) > 0 || barrierTop(r, x, z) > 0
                || brazier(r, x, z) || navePost(r, x, z) || sidePost(r, x, z);
    }

    static int floorDelta(Room r, int x, int z) {
        if (Math.abs(u(r, x)) > 13) return 0;
        int w = w(r, z);
        if (w < DAIS_W) return 0;
        return Math.min(DAIS_H, w - DAIS_W + 1);
    }

    static int ceilingDelta(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        if (w(r, z) >= DAIS_W - 2 && au <= 12) return 0;
        if (au <= 6) return 0;
        double t = Math.min(1, (au - 6) / (Pieces.hw(r) - 6));
        return -(int) Math.round(Math.pow(t, 1.6) * (VAULT_H - WALL_H));
    }

    /** A candle post by the side walls, in each bay between two pillars. */
    private static boolean sidePost(Room r, int x, int z) {
        if (Math.abs(Math.abs(u(r, x)) - (PILLAR_U + 4)) > 0.01) return false;
        int w = w(r, z);
        for (int row : PILLAR_ROWS) if (w == row + 4) return true;
        return false;
    }

    /** The nave's candle posts, before the ring. */
    private static boolean navePost(Room r, int x, int z) {
        int w = w(r, z);
        return Math.abs(Math.abs(u(r, x)) - 5.5) < 0.01 && (w == 2 || w == 6 || w == 10);
    }

    private static boolean brazier(Room r, int x, int z) {
        double au = Math.abs(u(r, x));
        return Math.abs(au - 10.5) < 0.6 && w(r, z) == DAIS_W - 1;
    }

    // ---- lights -----------------------------------------------------------------------------------------------------

    private static volatile BlockState CANDLE4, CANDLE3, CANDLE2, SOUL_FIRE, FILL, FILL_PLATE;

    private static void states() {
        if (FILL != null) return;
        CANDLE4 = GatePainter.state("minecraft:white_candle[candles=4,lit=true]");
        CANDLE3 = GatePainter.state("minecraft:candle[candles=3,lit=true]");
        CANDLE2 = GatePainter.state("minecraft:white_candle[candles=2,lit=true]");
        SOUL_FIRE = GatePainter.state("minecraft:soul_fire");
        FILL_PLATE = GatePainter.state("minecraft:light[level=11]");
        FILL = GatePainter.state("minecraft:light[level=10]");
    }

    /** The candle, fire or lamp in this column, or null. Order is 0 at the doors and 1 at the throne. */
    static Light lightAt(Room r, int x, int z) {
        states();
        double au = Math.abs(u(r, x));
        int w = w(r, z);
        double order = Math.min(1, w / 41.0);
        // the statues' plinths: a candle on each aisle-side corner
        if (plinth(r, x, z) && Math.abs(au - (STATUE_U - 1)) < 0.01) {
            for (int row : STATUE_ROWS) if (w == row - 1 || w == row + 1) return new Light(x, 1, z, CANDLE3, order, false);
        }
        // candle stands down the nave before the ring
        if (navePost(r, x, z)) return new Light(x, 1, z, CANDLE4, order, false);
        // the altar: four candles on its corners
        if (altarBlock(r, x, z) && au > 1 && Math.abs(w + 0.5 - ALTAR_W) > 1) return new Light(x, 1, z, CANDLE4, order, false);
        // the dais's first step: a line of candles across it
        if (w == DAIS_W && au <= 12.5 && au >= 1.5 && Math.floorMod((int) Math.floor(au), 2) == 1) return new Light(x, 1, z, CANDLE4, order, false);
        // the second step, offset, closer together toward the throne
        if (w == DAIS_W + 1 && au <= 6.5 && au >= 2.5 && Math.floorMod((int) Math.floor(au), 2) == 0) return new Light(x, 2, z, CANDLE2, order, false);
        // braziers before the dais
        if (brazier(r, x, z)) return new Light(x, 2, z, SOUL_FIRE, order, false);
        // the armrests' tops
        if (throneStone(r, x, z) == 7 && (w == 35 || w == 40) && Math.abs(au - 8.5) < 0.01) return new Light(x, DAIS_H + 8, z, CANDLE4, 1.0, false);
        // candle posts along the side walls, in the bays between the pillars
        if (sidePost(r, x, z)) return new Light(x, 1, z, CANDLE3, order, false);
        // fill: an invisible lamp on a coarse grid at head height, so the hall reads at default brightness
        if (!blocks(r, x, z) && plateAt(r, x, z) < 0 && Math.floorMod((int) Math.floor(au), 6) == 2 && Math.floorMod(w, 6) == 3)
            return new Light(x, floorDelta(r, x, z) + 3, z, FILL, order, true);
        // and one over each plate, so the three read from anywhere in the nave
        for (int k = 0; k < 3; k++) {
            double[] p = plate(r, k);
            if ((int) Math.floor(p[0]) == x && (int) Math.floor(p[1]) == z) return new Light(x, 2, z, FILL_PLATE, order, true);
        }
        return null;
    }

    /** Every light in the hall, doors first. */
    public static List<Light> lights(Room r) {
        List<Light> out = new ArrayList<>();
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                Light l = lightAt(r, x, z);
                if (l != null) out.add(l);
            }
        }
        out.sort((a, b) -> Double.compare(a.order(), b.order()));
        return out;
    }

    // ---- painting ---------------------------------------------------------------------------------------------------

    private static BlockState s(String spec) {
        return GatePainter.state(spec);
    }

    /**
     * Paints one column of the hall's slots. Returns false for the doorway's tunnel through the wall, which the
     * painter's own door code draws.
     */
    static boolean column(GatePainter.Ctx c, int bx, int bz, int x, int z) {
        Room r = c.r;
        boolean in = x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1();
        if (!in) {
            Room.Door door = RoomGeometry.tunnel(r, x, z);
            if (door != null) return false;
            wall(c, bx, bz, x, z);
            return true;
        }
        interior(c, bx, bz, x, z);
        return true;
    }

    private static void wall(GatePainter.Ctx c, int bx, int bz, int x, int z) {
        Room r = c.r;
        int f = c.floorY;
        // the face toward the hall, if this column touches it
        int dx = x < r.x0() ? 1 : x > r.x1() ? -1 : 0, dz = z < r.z0() ? 1 : z > r.z1() ? -1 : 0;
        int nx = x + dx, nz = z + dz;
        boolean face = nx >= r.x0() && nx <= r.x1() && nz >= r.z0() && nz <= r.z1() && (dx == 0 || dz == 0);
        if (!face) {
            c.solidColumn(bx, bz, c.yLo, c.yHi);
            return;
        }
        int h = RoomGeometry.ceiling(r, nx, nz);
        int fd = floorDelta(r, nx, nz);
        boolean south = dz == -1, north = dz == 1;
        double au = Math.abs(u(r, x));
        for (int y = c.yLo; y <= c.yHi; y++) {
            int rel = y - f;
            BlockState b;
            if (rel < fd || rel > h) b = c.p.fill();
            else if (rel == fd) b = s("minecraft:polished_blackstone");
            else if (south) b = rel == fd + 1 ? s("minecraft:gilded_blackstone") : sunburst(r, au, rel, fd);
            else if (rel == fd + 1 || rel == WALL_H - 3) b = s("minecraft:gilded_blackstone");
            else if (north && au <= 6 && rel <= 10) b = rel == 10 || au > 5 ? s("minecraft:raw_gold_block") : s("minecraft:polished_blackstone_bricks");
            else {
                // side walls: tall bays between the pillars, a pilaster at each pillar row
                int along = dz == 0 ? w(r, z) : (int) Math.floor(au);
                boolean pilaster = false;
                if (dz == 0) for (int row : PILLAR_ROWS) if (along == row || along == row + 1) pilaster = true;
                if (pilaster) b = rel % 6 == 0 ? s("minecraft:chiseled_polished_blackstone") : s("minecraft:polished_basalt[axis=y]");
                else b = Hash.unit(r.seed, x * 31 + z, rel) < 0.12 ? s("minecraft:cracked_polished_blackstone_bricks") : s("minecraft:polished_blackstone_bricks");
            }
            c.set(bx, y, bz, b);
        }
    }

    /** The wall behind the throne: rays of aged gold fanning out from behind the Seated One's head. */
    private static BlockState sunburst(Room r, double au, int rel, int fd) {
        double cy = fd + 19.5;
        double d = Math.hypot(au, rel - cy);
        if (d < 12) {
            double a = Math.atan2(rel - cy, au);
            int ray = (int) Math.floorMod(Math.round(a / (Math.PI / 12)), 24L);
            if (d > 10.6) return s("minecraft:raw_gold_block");
            if (d > 3.5 && ray % 2 == 0) return d > 8 ? s("awaken:gate_bell_bronze") : s("minecraft:gilded_blackstone");
            return s("minecraft:polished_blackstone");
        }
        return s("minecraft:polished_blackstone_bricks");
    }

    private static void interior(GatePainter.Ctx c, int bx, int bz, int x, int z) {
        Room r = c.r;
        int f = c.floorY;
        int fd = floorDelta(r, x, z);
        int walk = f + fd;
        int ceil = f + RoomGeometry.ceiling(r, x, z);
        double au = Math.abs(u(r, x));
        int w = w(r, z);
        c.solidColumn(bx, bz, c.yLo, walk - 2);
        c.set(bx, walk - 1, bz, floor(r, x, z, fd));
        c.set(bx, ceil, bz, ceiling(r, x, z));
        c.solidColumn(bx, bz, ceil + 1, c.yHi);
        if (pillar(r, x, z)) {
            for (int y = walk; y < ceil; y++) {
                int rel = y - f;
                c.set(bx, y, bz, rel == 0 || rel == WALL_H - 4 ? s("minecraft:chiseled_polished_blackstone")
                        : rel > WALL_H - 4 ? s("minecraft:polished_blackstone_bricks") : s("minecraft:polished_basalt[axis=y]"));
            }
            return;
        }
        int stone = throneStone(r, x, z);
        if (stone > 0) {
            for (int y = walk; y <= walk + stone && y < ceil; y++) {
                int rel = y - walk;
                BlockState b = rel == stone ? s("minecraft:raw_gold_block")
                        : stone == 7 ? (rel == 0 ? s("minecraft:chiseled_polished_blackstone") : s("minecraft:polished_blackstone"))
                        : rel % 5 == 4 ? s("minecraft:gilded_blackstone") : s("minecraft:polished_blackstone_bricks");
                c.set(bx, y, bz, b);
            }
        }
        int bar = barrierTop(r, x, z);
        if (bar > 0) for (int y = walk; y <= walk + bar; y++) c.set(bx, y, bz, Blocks.BARRIER.defaultBlockState());
        if (plinth(r, x, z)) {
            c.set(bx, walk, bz, Math.abs(au - STATUE_U) < 0.01 && Math.abs(w - nearestStatueRow(w)) == 0
                    ? s("minecraft:chiseled_polished_blackstone") : s("minecraft:polished_blackstone_bricks"));
        }
        if (altarBlock(r, x, z)) {
            boolean corner = au > 1 && Math.abs(w + 0.5 - ALTAR_W) > 1;
            c.set(bx, walk, bz, corner ? s("minecraft:raw_gold_block") : s("minecraft:chiseled_polished_blackstone"));
        }
        if (brazier(r, x, z)) {
            c.set(bx, walk, bz, s("minecraft:chiseled_polished_blackstone"));
            c.set(bx, walk + 1, bz, s("minecraft:soul_soil"));
        }
        if (navePost(r, x, z) || sidePost(r, x, z)) c.set(bx, walk, bz, s("minecraft:polished_blackstone_wall"));
        Light l = lightAt(r, x, z);
        if (l != null) c.set(bx, f + l.dy(), bz, l.lit());
        bell(c, bx, bz, x, z, ceil);
    }

    private static int nearestStatueRow(int w) {
        int best = STATUE_ROWS[0];
        for (int row : STATUE_ROWS) if (Math.abs(row - w) < Math.abs(best - w)) best = row;
        return best;
    }

    /** The floor block painted in this column (the director restores the rune ring's pits with it). */
    public static BlockState floorState(Room r, int x, int z) {
        return floor(r, x, z, floorDelta(r, x, z));
    }

    private static BlockState floor(Room r, int x, int z, int fd) {
        double au = Math.abs(u(r, x));
        int w = w(r, z);
        if (plateAt(r, x, z) >= 0) return s("minecraft:chiseled_polished_blackstone");
        int seg = ringSegment(r, x, z);
        if (seg >= 0) {
            double d = Math.hypot(u(r, x), w + 0.5 - ALTAR_W);
            return Math.abs(d - (RING_IN + RING_OUT) / 2) < 0.5 ? s("awaken:gate_bell_bronze") : s("minecraft:gilded_blackstone");
        }
        if (fd > 0) return s("minecraft:polished_blackstone_bricks");
        if (au < 2.5 && w < RING_IN + ALTAR_W - 14) return s("minecraft:polished_blackstone_bricks"); // the aisle
        if (Math.abs(au - 2.5) < 0.6 && w < ALTAR_W - RING_OUT) return s("minecraft:raw_gold_block");  // its gold edging
        if (Math.floorMod(w, 6) == 0 && au < 15) return s("minecraft:polished_deepslate");
        return ((int) Math.floor(au) + w) % 2 == 0 ? s("minecraft:polished_blackstone") : s("minecraft:deepslate_tiles");
    }

    private static BlockState ceiling(Room r, int x, int z) {
        int w = w(r, z);
        double au = Math.abs(u(r, x));
        for (int row : PILLAR_ROWS) if (w == row || w == row + 1) return s("awaken:gate_bell_bronze");
        if (au < 0.6) return s("minecraft:chiseled_polished_blackstone");
        return s("minecraft:polished_blackstone_bricks");
    }

    /** The great bell over the altar, hung from the vault. */
    private static void bell(GatePainter.Ctx c, int bx, int bz, int x, int z, int ceil) {
        Room r = c.r;
        double du = u(r, x), dw = w(r, z) + 0.5 - ALTAR_W;
        if (Math.hypot(du, dw) > BELL_R + 1) return;
        Pieces.bell(c, bx, bz, du, dw, c.floorY + BELL_LIP, BELL_TALL, BELL_R, ceil,
                Pieces.bronze(), s("minecraft:polished_blackstone"), null);
    }
}
