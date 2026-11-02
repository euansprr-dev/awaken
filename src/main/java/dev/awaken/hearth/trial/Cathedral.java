package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * The cathedral above the clouds (SPEC §46.8): the fourth Proving's hall. A white stone cathedral hanging in the night
 * over a sea of cloud, its broken foundation trailing under it: a nave between arcades of quartz columns, an aisle each
 * side behind them under a lean-to roof, flying buttresses out to piers, a vaulted roof broken open to the stars, an
 * apse at the north end, and the west front at the south with its rose window over the door and a porch outside, the
 * way out. A shaft runs up through the foundation to a font in the crossing: you're lifted up it into the nave. Built
 * by the trials' generator in every hall cell at {@link #AT_X}, {@link #AT_Z}. Pure: {@link #block} says what stands at
 * a spot. Local x runs east, z south, dy up from the trials floor (you stand on the nave at dy 1).
 * <p>
 * At two thirds the aisles break away and fall into the cloud (the arcades stay: the nave is the floor); at a third the
 * roof lifts away into the sky, everything over the arcades' arches.
 */
public final class Cathedral {
    private Cathedral() {}

    /** The crossing's centre (the font), this far east and south of its cell's north-west corner. */
    public static final int AT_X = 420, AT_Z = 256;
    /** The nave's floor to |x| 7; the arcades' columns at |x| 8-9; the aisles' floor 10-13, their outer wall at 14. */
    public static final int NAVE = 7, ARCADE = 8, AISLE = 13, OUTER = 14;
    /** The flying buttresses' piers, at |x| 18-19 by every column. */
    public static final int BUTTRESS = 18;
    /** The nave and aisles run from the apse's chord (north) to the west front (south); the apse is a half circle north. */
    public static final int APSE_Z = -42, WEST = 36;
    public static final double APSE_R = 9.5;
    /** The porch outside the west door runs to here; past {@link #OUT_Z} on it you've gone out. */
    public static final int PORCH = 45, OUT_Z = 41;
    /** Heights: the arcades' capitals and arches' crowns, the aisles' roof at the wall, the clerestory's top, the vault's crown. */
    static final int CAPITAL = 9, ARCH_TOP = 13, AISLE_TOP = 13, CLERESTORY = 24, CROWN = 30;
    /** The rose window over the door: its middle and radius. */
    static final int ROSE_DY = 18;
    static final double ROSE_R = 6.5;
    /** The font in the crossing, over the shaft you're lifted up. */
    public static final int WELL_R = 3;
    /** The cloud sea far under it (its top under the font is {@link #CLOUD_TOP}). */
    static final int CLOUD_LO = -30, CLOUD_TOP = -27;
    /** Where you first wake: on the cloud under the font. */
    public static final int WAKE_DY = CLOUD_TOP + 1;
    /** Where you wake after a fall: inside the west door, facing the altar. */
    public static final int DOOR_Z = 33;
    /** Where it comes down: the nave's north half. */
    public static final int LAND_Z = -14;
    /** The hall's box (the cloud sea reaches out to it). */
    public static final int WALL = 56, NORTH = -92, BACK = 88, LOW = CLOUD_LO, ROOF = 33;
    /** The band a fight changes: the foundation's root to the roof (the aisles fall, the roof lifts). */
    static final int BAND_LO = -20, BAND_HI = ROOF;
    /** What lifts away at a third: over the arches, inside this. */
    static final int LIFT_DY = 14, LIFT_X = 11;
    /** What falls at two thirds: the aisles and everything out from them, north of the porch and south of the apse. */
    static final int FALL_X = 10;

    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState QBRICK = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState QCARVED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState DARK = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    private static final BlockState BRICK = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState TUFF = Blocks.POLISHED_TUFF.defaultBlockState();
    private static final BlockState TUFF_BRICK = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState CORE = Blocks.STONE.defaultBlockState();
    private static final BlockState RUBBLE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState CLOUD = Blocks.WOOL.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState CLOUD_TOP_BLOCK = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState CLOUD_UNDER = Blocks.WOOL.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
    private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    private static final BlockState HUNG = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState[] GLASS = {
            Blocks.STAINED_GLASS.pick(DyeColor.BLUE).defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.PURPLE).defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA).defaultBlockState(),
            Blocks.STAINED_GLASS.pick(DyeColor.CYAN).defaultBlockState(), Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState()};

    /** The font's middle (dy 0 at the trials floor) in cell {@code cell}. */
    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT_X, 0, AT_Z);
    }

    public static boolean spans(int x, int z) {
        return Math.abs(x) <= WALL && z >= NORTH && z <= BACK;
    }

    private static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 83492791 ^ z * 19349663;
        return (h ^ (h >>> 13)) & 0x7fffffff;
    }

    /** Smooth value noise, 0..1, on a unit lattice. */
    static double noise(double x, double z, int salt) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(i, j, salt), b = lattice(i + 1, j, salt), c = lattice(i, j + 1, salt), d = lattice(i + 1, j + 1, salt);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static double lattice(int i, int j, int salt) {
        return (hash(i, salt, j) & 0xffff) / 65535.0;
    }

    /** The distance from the font's middle. */
    static double rWell(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /** The distance from the apse's middle (on its chord). */
    private static double rApse(int x, int z) {
        return Math.sqrt((double) x * x + (double) (z - APSE_Z) * (z - APSE_Z));
    }

    /** The block at a local spot, or null for air. */
    public static @Nullable BlockState block(int x, int dy, int z) {
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        if (dy <= CLOUD_TOP) return cloud(x, dy, z);
        if (dy < BAND_LO) return null;
        int ax = Math.abs(x);
        if (z > WEST + 1) return porch(x, dy, z);
        if (z < APSE_Z) return apse(x, dy, z);
        if (ax >= BUTTRESS - 1) return buttress(ax, dy, z);
        if (dy < 0) return root(x, dy, z);
        if (z >= WEST) return westFront(x, dy, z);
        if (dy == 0) return ax <= OUTER ? floor(x, z) : null;
        if (ax >= FALL_X && flyer(ax, dy, z) instanceof BlockState f) return f;
        if (ax > OUTER) return null;
        if (z == APSE_Z && ax >= FALL_X) return dy <= AISLE_TOP + 1 ? QUARTZ : null; // the aisles' north ends
        if (ax == OUTER) return outerWall(dy, z);
        if (ax >= FALL_X) return aisle(ax, dy, z);
        if (ax >= ARCADE) return arcade(dy, z);
        return nave(x, dy, z);
    }

    // ---- the cloud sea -----------------------------------------------------------------------------------------------

    /** How thick the cloud is at a spot (0 for a gap), fading out toward the box's edge; thick and whole under the font. */
    static int cloudThick(int x, int z) {
        if (rWell(x, z) < 7) return CLOUD_TOP - CLOUD_LO + 1;
        double n = noise(x / 11.0, z / 11.0, 7) * 0.7 + noise(x / 4.0, z / 4.0, 11) * 0.3;
        double edge = Math.min(WALL - Math.abs(x), Math.min(z - NORTH, BACK - z));
        n *= Math.min(1, edge / 16.0);
        if (n < 0.34) return 0;
        return Math.min(CLOUD_TOP - CLOUD_LO + 1, 1 + (int) ((n - 0.34) * 9));
    }

    private static @Nullable BlockState cloud(int x, int dy, int z) {
        int t = cloudThick(x, z);
        if (t == 0 || dy > CLOUD_LO + t - 1) return null;
        if (dy == CLOUD_LO + t - 1) return CLOUD_TOP_BLOCK;
        return dy == CLOUD_LO ? CLOUD_UNDER : CLOUD;
    }

    // ---- the foundation's root ---------------------------------------------------------------------------------------

    /** How far in from the cathedral's edge a spot is (negative outside it), for the root's depth. */
    private static double inside(int x, int z) {
        int ax = Math.abs(x);
        double main = Math.min(OUTER + 0.5 - ax, Math.min(z - APSE_Z + 1.5, WEST + 1.5 - z));
        double apse = z < APSE_Z + 1 ? APSE_R + 1 - rApse(x, z) : -99;
        return Math.max(main, apse);
    }

    private static @Nullable BlockState root(int x, int dy, int z) {
        if (rWell(x, z) <= WELL_R) return dy == -1 ? BRICK : shaftLight(x, dy, z); // the shaft up to the font
        double in = inside(x, z);
        if (in < 0) return null;
        if (dy == -1) return BRICK;
        double depth = 2 + Math.min(in, 11) * 1.25 + noise(x / 3.0, z / 3.0, 3) * 3;
        if (-dy > depth) return null;
        if (-dy > depth - 1) return RUBBLE;
        if (dy >= -3) return Math.floorMod(hash(x, dy, z), 7) == 0 ? CRACKED : BRICK;
        int h = Math.floorMod(hash(x, dy, z), 10);
        return in > 5 ? (h < 6 ? CORE : h < 8 ? TUFF_BRICK : ANDESITE) : (h < 5 ? BRICK : h < 8 ? CRACKED : TUFF_BRICK);
    }

    /** The shaft's own unseen light, so the climb isn't black. */
    private static @Nullable BlockState shaftLight(int x, int dy, int z) {
        return x == 0 && z == 0 && (dy == -6 || dy == -13) ? lightOf(11) : null;
    }

    // ---- the floor ---------------------------------------------------------------------------------------------------

    private static BlockState floor(int x, int z) {
        int ax = Math.abs(x);
        double rw = rWell(x, z);
        if (rw <= WELL_R) return rw < 1.5 ? QCARVED : QBRICK; // the font
        if (rw < WELL_R + 1) return DARK;
        if (ax >= FALL_X) return (x + z) % 2 == 0 ? TUFF : TUFF_BRICK;
        if (ax >= ARCADE) return ANDESITE;
        if (ax == NAVE) return DARK;
        boolean rosette = Math.floorMod(z, 8) == 4;
        if (ax <= 1) return rosette && ax == 0 ? QCARVED : QBRICK;
        return (x + z) % 2 == 0 ? CALCITE : QUARTZ;
    }

    // ---- the nave ----------------------------------------------------------------------------------------------------

    /** Whether a z is a column's (two thick, every 8, the first a respond against the apse's chord). */
    static boolean column(int z) {
        if (z <= APSE_Z + 1) return true;
        return Math.floorMod(z, 8) <= 1 && z >= -40 && z <= 33;
    }

    /** Where in its bay a z is: 0 to 5 between two columns, -1 on a column. */
    private static int inBay(int z) {
        if (column(z)) return -1;
        int i = Math.floorMod(z, 8) - 2;
        return z > 33 ? 0 : i;
    }

    /** The vault's underside over x (a pointed barrel: the crown over the middle). */
    static int vault(int ax) {
        return CLERESTORY + (int) Math.round(6 * (1 - Math.pow(ax / 8.0, 1.6)));
    }

    /** Whether the vault is broken open over a spot (a hole to the stars), never on a rib. */
    static boolean holed(int x, int z) {
        return !column(z) && noise(x / 2.5, z / 2.5, 5) > 0.68;
    }

    private static @Nullable BlockState nave(int x, int dy, int z) {
        int ax = Math.abs(x), v = vault(ax);
        if (dy == 3 && light(x, z)) return lightOf(12);
        if (dy >= v && dy <= v + 1) {
            if (holed(x, z)) return null;
            return column(z) ? (dy == v ? DARK : QUARTZ) : dy == v ? CALCITE : QUARTZ;
        }
        if (dy == v - 1 && column(z)) return TILES; // the ribs
        // the lanterns hung down the nave, a bay apart, where the vault over them stands
        if (x == 0 && Math.floorMod(z, 8) == 4 && z > -40 && z < 32 && !holed(0, z)) {
            if (dy == 20) return HUNG;
            if (dy > 20 && dy < vault(0)) return CHAIN;
        }
        return null;
    }

    private static boolean light(int x, int z) {
        return Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3;
    }

    /** The arcade (|x| 8-9): columns, pointed arches between them, the clerestory's wall and windows over them. */
    private static @Nullable BlockState arcade(int dy, int z) {
        int i = inBay(z);
        if (dy > vault(ARCADE) + 1) return null;
        if (dy > ARCH_TOP) {
            // the clerestory: a window in each bay's middle
            if (i >= 1 && i <= 4 && dy >= 16 && dy <= (i == 1 || i == 4 ? 20 : 21)) return glass(ARCADE, dy, z);
            return dy == CLERESTORY ? QBRICK : CALCITE;
        }
        if (i < 0) return dy == 1 ? DARK : dy == CAPITAL ? QCARVED : dy < CAPITAL ? PILLAR : QUARTZ;
        int open = CAPITAL + (i == 0 || i == 5 ? 0 : i == 1 || i == 4 ? 2 : 3);
        if (dy <= open) return null;
        return dy == open + 1 ? QBRICK : QUARTZ;
    }

    /** The aisles (|x| 10-13): open to the arcade, under a lean-to roof. */
    private static @Nullable BlockState aisle(int ax, int dy, int z) {
        if (dy == 3 && ax == 12 && Math.floorMod(z, 6) == 3) return lightOf(11);
        int roof = AISLE_TOP + (OUTER - ax) / 2 + 1;
        if (dy == roof) return TILES;
        return null;
    }

    /** The aisles' outer wall (|x| 14): a lancet in each bay, pinnacles over every column. */
    private static @Nullable BlockState outerWall(int dy, int z) {
        int i = inBay(z);
        if (dy > AISLE_TOP + 1) return null;
        if (i >= 1 && i <= 4 && dy >= 3 && dy <= (i == 1 || i == 4 ? 8 : 9)) return glass(OUTER, dy, z);
        if (dy == AISLE_TOP + 1) return i < 0 ? QCARVED : QBRICK;
        return i < 0 ? PILLAR : dy == 1 ? DARK : QUARTZ;
    }

    /** The flying buttresses: an arch from each pier over the aisle's roof up to the clerestory, by every column. */
    private static @Nullable BlockState flyer(int ax, int dy, int z) {
        if (!column(z) || z <= APSE_Z + 1) return null;
        int at = 14 + (int) Math.round((BUTTRESS - 1 - ax) * 0.8);
        return dy == at || dy == at - 1 ? (dy == at ? QBRICK : BRICK) : null;
    }

    /** A buttress's pier (|x| 17-19 by each column): from a ragged foot under the floor up to a pinnacle. */
    private static @Nullable BlockState buttress(int ax, int dy, int z) {
        if (!column(z) || z <= APSE_Z + 1 || ax > BUTTRESS + 1) return null;
        if (ax == BUTTRESS - 1) return dy == 14 || dy == 13 ? (dy == 14 ? QBRICK : BRICK) : null; // the flyer's foot
        int foot = -6 - Math.floorMod(hash(ax, 0, z), 3);
        if (dy < foot) return null;
        if (dy == foot) return RUBBLE;
        if (dy <= 15) return Math.floorMod(-dy, 5) == 0 ? QBRICK : dy < 0 ? BRICK : QUARTZ;
        if (ax == BUTTRESS && dy <= 19) return dy == 19 ? QCARVED : PILLAR; // the pinnacle
        return null;
    }

    private static BlockState glass(int x, int dy, int z) {
        return GLASS[Math.floorMod(hash(x / 3, dy / 3, z / 3), GLASS.length)];
    }

    // ---- the west front, the porch, the apse -------------------------------------------------------------------------

    private static @Nullable BlockState westFront(int x, int dy, int z) {
        int ax = Math.abs(x);
        if (dy < 0) return root(x, dy, z);
        if (ax > OUTER) return null;
        if (dy == 0) return ax <= 3 ? ANDESITE : QUARTZ;
        int top = ax >= FALL_X ? AISLE_TOP + 2 : CROWN + 2 - ax;
        if (dy > top) return null;
        if (ax <= 2 && dy <= (ax == 2 ? 4 : 5)) return null; // the door
        if (ax == 3 && dy <= 4 || ax == 2 && dy == 5 || ax <= 1 && dy == 6) return QCARVED; // its jambs and arch
        double rr = Math.sqrt((double) x * x + (double) (dy - ROSE_DY) * (dy - ROSE_DY));
        if (rr <= ROSE_R + 1) return rose(x, dy, rr, z);
        if (dy == top) return QCARVED;
        return z == WEST ? QUARTZ : CALCITE;
    }

    /** The rose window: a ring frame, twelve spokes, rings of coloured glass and a golden heart. */
    private static BlockState rose(int x, int dy, double rr, int z) {
        if (rr > ROSE_R) return QCARVED;
        if (rr < 1.2) return Blocks.STAINED_GLASS.pick(DyeColor.YELLOW).defaultBlockState();
        double a = Math.toDegrees(Math.atan2(dy - ROSE_DY, x));
        double spoke = Math.abs(((a % 30) + 30) % 30 - 15);
        if (spoke > 12.5 && rr > 2.2) return QBRICK;
        if (Math.abs(rr - 3.8) < 0.45) return QBRICK;
        if (rr < 3.4) return Blocks.STAINED_GLASS.pick(DyeColor.BLUE).defaultBlockState();
        int sector = Math.floorMod((int) Math.floor((a + 360) / 30), 12);
        return sector % 2 == 0 ? Blocks.STAINED_GLASS.pick(DyeColor.PURPLE).defaultBlockState() : Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
    }

    /** The porch outside the door: a short paved landing with low walls, its far end broken off. */
    private static @Nullable BlockState porch(int x, int dy, int z) {
        int ax = Math.abs(x);
        if (ax > 4 || z > PORCH) return null;
        if (z >= PORCH - 2 && Math.floorMod(hash(x, dy, z), 3) <= z - (PORCH - 2)) return null; // ragged
        if (dy == 0) return ax <= 3 ? (ax <= 1 ? QBRICK : ANDESITE) : QUARTZ;
        if (dy == 1) return ax == 4 ? QBRICK : null;
        if (dy == 3 && x == 0 && z == WEST + 3) return lightOf(13);
        if (dy < 0 && dy >= -3 + Math.floorMod(hash(x, 0, z), 2)) return dy == -1 ? BRICK : Math.floorMod(hash(x, dy, z), 3) == 0 ? CRACKED : BRICK;
        return null;
    }

    private static @Nullable BlockState apse(int x, int dy, int z) {
        double ra = rApse(x, z);
        if (dy < 0) return root(x, dy, z);
        if (ra >= APSE_R + 1.1) return null;
        boolean wall = ra >= APSE_R;
        if (dy == 0) return wall ? QUARTZ : Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z - APSE_Z, x))), 30) < 6 ? QBRICK : CALCITE;
        if (dy == 3 && !wall && light(x, z)) return lightOf(12);
        int dome = CLERESTORY + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - (ra / (APSE_R + 1)) * (ra / (APSE_R + 1)))));
        if (!wall) return dy >= dome && dy <= dome + 1 ? (holed(x, z) ? null : dy == dome ? CALCITE : QUARTZ) : null;
        if (dy > CLERESTORY + 1) return null;
        // tall lancets round it, every 30 degrees
        double a = Math.toDegrees(Math.atan2(z - APSE_Z, x));
        double off = Math.abs(((a % 30) + 30) % 30 - 15);
        if (off > 9 && dy >= 4 && dy <= 19) return glass(x, dy, z);
        return dy == 1 ? DARK : off < 2 ? PILLAR : QUARTZ;
    }

    // ---- light -------------------------------------------------------------------------------------------------------

    static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    // ---- the turns ---------------------------------------------------------------------------------------------------

    /** Whether a spot goes when the aisles fall (at two thirds): the aisles, their roofs, walls, flyers and piers. */
    static boolean falls(int x, int z) {
        return Math.abs(x) >= FALL_X && z >= APSE_Z && z <= WEST + 1;
    }

    /** Whether a spot lifts away when the roof goes (at a third): over the arches, the nave, the west gable, the apse. */
    static boolean lifts(int x, int dy, int z) {
        return dy >= LIFT_DY && Math.abs(x) <= LIFT_X && z <= WEST + 1;
    }

    /** Whether a block is the cathedral's light (unseen, or a lamp): kept when the roof lifts, so the nave isn't black. */
    static boolean keep(BlockState s) {
        return s.is(Blocks.LIGHT);
    }

    // ---- as a hall ---------------------------------------------------------------------------------------------------

    static final Hall HALL = new Hall() {
        @Override public int atX() { return AT_X; }
        @Override public int atZ() { return AT_Z; }
        @Override public int wall() { return WALL; }
        @Override public int north() { return NORTH; }
        @Override public int back() { return BACK; }
        @Override public int low() { return LOW; }
        @Override public int roof() { return ROOF; }
        @Override public int bandLo() { return BAND_LO; }
        @Override public int bandHi() { return BAND_HI; }
        @Override public int bandWall() { return BUTTRESS + 1; }
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Cathedral.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 12, 0, 0) && is(level, c, ARCADE, 4, 0) && is(level, c, 0, ROSE_DY, WEST)
                    && is(level, c, 0, CLOUD_TOP, 0);
        }
    };

    /** How a block is set as the hall turns (clients told, no neighbour updates). */
    static final int QUIET = Block.UPDATE_CLIENTS;
}
