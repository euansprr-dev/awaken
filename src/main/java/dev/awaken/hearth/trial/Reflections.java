package dev.awaken.hearth.trial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * The Hall of Reflections (SPEC §47.6): the second Proving's hall, a round examination hall of white stone ruled like a
 * measuring sheet, walled with dark mirrors. Built by the trials' generator in every hall cell at {@link #AT_X},
 * {@link #AT_Z} (clear of the Belfry and the box arena). Pure: {@link #block} says what stands at a spot. Local x runs
 * east, z south (the door is south), dy up from the trials floor (you stand at dy 1).
 * <p>
 * You never walk in: the room you stood in is laid over its middle and turns to glass round you. As the fight turns the
 * mirrors wake (they light up white), then a ring of mirrors rises from the floor and the hall closes in. Six standing
 * mirrors are cover. Out of the door a stair climbs to a landing: the way out.
 */
public final class Reflections {
    private Reflections() {}

    /** The hall's centre, this far east and south of its cell's north-west corner. */
    public static final int AT_X = 256, AT_Z = 120;
    /** The mirror wall's inner face (r 40) and its back (to r 42.5); the stair runs south to z 56. */
    public static final int WALL_R = 40, OUTER = 43, BACK = 56;
    public static final int LOW = -1, ROOF = 26;
    /** The wall's mirrors (dy 4 to 15 inside gold frames at 3 and 16), 24 bays, one centred on the door. */
    public static final int MIRROR_LO = 3, MIRROR_HI = 16, BAYS = 24;
    /** The ring that rises when the walls close in: its radius and its height. */
    public static final int RING_R = 26, RING_HI = 9;
    /** Where you wake after a fall: inside the door, facing the middle. */
    public static final int WAKE_Z = 35;
    /** Where it comes in after everyone fell: out of the north wall's mirror. */
    public static final int RETURN_Z = -36;
    /** The stair's top (you stand at dy 4): on it you've climbed out. */
    public static final int LANDING_Z = 48, LANDING_DY = 4;

    /** Standing mirrors: x, z, and whether they run along x (else along z). Five wide, six high, clear of the room's middle. */
    static final int[][] COVER = {{0, -18, 1}, {-17, -8, 0}, {17, -8, 0}, {-17, 9, 0}, {17, 9, 0}, {0, 19, 1}};

    private static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState QUARTZ_BLOCK = Blocks.QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState QUARTZ_BRICKS = Blocks.QUARTZ_BRICKS.defaultBlockState();
    private static final BlockState CHISELED = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
    private static final BlockState PILLAR = Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState INK = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState INK_BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState RULE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final BlockState DARK_GLASS = Blocks.TINTED_GLASS.defaultBlockState();
    private static final BlockState BLACK = Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    private static final BlockState WHITE_GLASS = Blocks.STAINED_GLASS.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState SEA = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    private static final BlockState WARM = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState STEP = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

    /** The hall's centre (dy 0 at the trials floor) in cell {@code cell}. */
    public static BlockPos centre(int cell) {
        return TrialChunkGenerator.origin(cell).offset(AT_X, 0, AT_Z);
    }

    public static boolean spans(int x, int z) {
        return Math.abs(x) <= OUTER && z >= -OUTER && z <= BACK;
    }

    static double r(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z);
    }

    /** The block at a local spot, or null for air. */
    public static @Nullable BlockState block(int x, int dy, int z) {
        if (dy < LOW || dy > ROOF || !spans(x, z)) return null;
        double r = r(x, z);
        if (r >= OUTER - 0.5) return z > 0 && Math.abs(x) <= 2 ? stair(Math.abs(x), dy, z) : null;
        if (dy == ROOF) return r <= 4 ? SEA : INK; // the oculus glows over the middle
        if (r >= WALL_R) return wall(x, dy, z, r);
        if (dy == LOW) return RULE;
        if (dy == 0) return floor(x, z, r);
        BlockState lamp = lamp(x, dy, z);
        if (lamp != null) return lamp;
        if (dy >= 24) return ceiling(x, dy, z, r);
        BlockState cover = cover(x, dy, z);
        if (cover != null) return cover;
        return light(x, dy, z, r);
    }

    // ---- the parts --------------------------------------------------------------------------------------------------

    private static int hash(int x, int z) {
        int h = x * 73856093 ^ z * 19349663;
        return (h ^ (h >>> 13)) & 0x7fffffff;
    }

    /** The floor, ruled like a measuring sheet: ink rings every 8, rule lines out from the 8, a gold seal in the middle. */
    private static BlockState floor(int x, int z, double r) {
        if (r < 1.5) return CHISELED;
        if (r >= 2.5 && r < 3.5) return GOLD;
        if (r < 3.5) return QUARTZ;
        if (r >= WALL_R - 1.5) return INK_BRICKS; // the border round the wall's foot
        for (int k = 8; k <= 32; k += 8) if (Math.abs(r - k) < 0.5) return INK;
        if (r > 8) {
            // sixteen rules out from the first ring, every 22.5 degrees
            double a = Math.atan2(z, x);
            double step = Math.PI / 8;
            double off = a - Math.round(a / step) * step;
            if (Math.abs(r * Math.sin(off)) < 0.5) return RULE;
        }
        int h = hash(x, z);
        return h % 9 == 0 ? CALCITE : h % 13 == 0 ? QUARTZ_BLOCK : QUARTZ;
    }

    /** Which of the 24 bays a spot's bearing falls in, and how far (in blocks at radius {@code r}) it is from the bay's edge. */
    static int bay(int x, int z) {
        double deg = Math.toDegrees(Math.atan2(z, x)) - 90 + 7.5; // bay 0 is centred on the door (south)
        return Math.floorMod((int) Math.floor(deg / 15.0), BAYS);
    }

    private static double fromEdge(int x, int z, double r) {
        double deg = Math.toDegrees(Math.atan2(z, x)) - 90 + 7.5;
        double in = Math.floorMod((int) Math.round(deg * 1000), 15000) / 1000.0; // 0..15 inside the bay
        return Math.toRadians(Math.min(in, 15 - in)) * r;
    }

    /** Whether a spot is the door's bay (south), which has the door and no mirror. */
    static boolean doorBay(int x, int z) {
        return bay(x, z) == 0;
    }

    /** The wall: a dark skirting, quartz pilasters between 24 bays, each bay a dark mirror in a gold frame, white above. */
    private static @Nullable BlockState wall(int x, int dy, int z, double r) {
        if (dy <= 0) return INK_BRICKS;
        boolean front = r < WALL_R + 1;
        if (doorBay(x, z)) {
            if (Math.abs(x) <= 1 && z > 0) {
                if (dy <= 3) return null; // the door: you fit, the Examiner doesn't
                if (dy == 4) return CHISELED;
            }
            if (dy <= 2) return INK_BRICKS;
            return dy == 20 ? CHISELED : QUARTZ_BRICKS;
        }
        double edge = fromEdge(x, z, r);
        if (edge < 1.2) return PILLAR;
        if (dy <= 2) return INK_BRICKS;
        if (dy >= MIRROR_LO && dy <= MIRROR_HI) {
            if (!front) return BLACK; // behind the glass: black, so the mirror reads deep
            if (dy == MIRROR_LO || dy == MIRROR_HI || edge < 2.2) return GOLD;
            return DARK_GLASS;
        }
        if (dy == 20) return CHISELED;
        if (dy == 23) return QUARTZ_BRICKS;
        return QUARTZ;
    }

    /** Whether a spot is the face of one of the wall's mirrors (what wakes). */
    public static boolean mirror(int x, int dy, int z) {
        return block(x, dy, z) == DARK_GLASS && r(x, z) >= WALL_R;
    }

    /** Whether a spot is behind a wall mirror (it lights when the mirror wakes). */
    static boolean behind(int x, int dy, int z) {
        return block(x, dy, z) == BLACK;
    }

    /** A wall mirror awake: white, lit from behind. */
    static BlockState woken(boolean face) {
        return face ? WHITE_GLASS : SEA;
    }

    /** The ceiling: dark beams on a grid and round the middle, white between, open over the oculus. */
    private static @Nullable BlockState ceiling(int x, int dy, int z, double r) {
        if (r <= 4) return null;
        if (dy == 25) return QUARTZ;
        boolean beam = Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0 || Math.abs(r - 20) < 0.6 || Math.abs(r - 5) < 0.7;
        return beam ? INK : null;
    }

    /** A standing mirror: a quartz post each end, dark glass between, on a black foot, a chiseled top. */
    private static @Nullable BlockState cover(int x, int dy, int z) {
        if (dy > 6) return null;
        for (int[] c : COVER) {
            int along = c[2] == 1 ? x - c[0] : z - c[1];
            int across = c[2] == 1 ? z - c[1] : x - c[0];
            if (across != 0 || Math.abs(along) > 2) continue;
            if (dy == 1) return INK_BRICKS;
            if (dy == 6) return CHISELED;
            return Math.abs(along) == 2 ? PILLAR : DARK_GLASS;
        }
        return null;
    }

    /** Twelve lamps hung from the ceiling on chains at radius 30 (a sea lantern at dy 12). */
    private static @Nullable BlockState lamp(int x, int dy, int z) {
        if (dy < 12 || dy > 24) return null;
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15 + 30 * k);
            if (x == (int) Math.round(30 * Math.cos(a)) && z == (int) Math.round(30 * Math.sin(a))) return dy == 12 ? SEA : CHAIN;
        }
        return null;
    }

    /** Light the eye can't see: 13 at head height on a 6-block grid over the floor (the copy of your room hides it until it breaks). */
    private static @Nullable BlockState light(int x, int dy, int z, double r) {
        if (dy == 4 && r <= WALL_R - 3 && Math.floorMod(x, 6) == 3 && Math.floorMod(z, 6) == 3) return lightOf(13);
        return null;
    }

    private static BlockState lightOf(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    /** The stair out (|x| up to 2, beyond the wall): half a block a step from the door to a landing at dy 4, lit warm. */
    private static @Nullable BlockState stair(int ax, int dy, int z) {
        if (dy < LOW || dy > 9) return null;
        if (dy == 9) return INK;
        if (z == BACK || ax == 2) return INK_BRICKS;
        // the standing height: 1 at the door, half a block a step, 4 on the landing
        double s = z >= LANDING_Z ? LANDING_DY : Math.min(LANDING_DY, 1 + (z - 42) * 0.5);
        int full = (int) Math.floor(s);
        boolean half = s - full > 0.25;
        if (dy < full) return INK_BRICKS;
        if (dy == full && half) return STEP;
        if (dy == 8 && ax == 0 && z == LANDING_Z + 3) return WARM; // the way out is lit warm, the hall cold
        return null;
    }

    // ---- the ring that rises -----------------------------------------------------------------------------------------

    /** Whether a column is the rising ring's (a door through it south: you fit, it doesn't). */
    static boolean ringAt(int x, int z) {
        double r = r(x, z);
        return r >= RING_R && r < RING_R + 1.3;
    }

    /** The ring's block at a height (1 to {@link #RING_HI}), or null for its door. */
    static @Nullable BlockState ring(int x, int dy, int z) {
        if (!ringAt(x, z) || dy < 1 || dy > RING_HI) return null;
        if (Math.abs(x) <= 1 && z > 0 && dy <= 3) return null;
        if (dy == 1) return INK_BRICKS;
        if (dy == RING_HI) return CHISELED;
        if (Math.abs(x) <= 1 && z > 0 && dy == 4) return CHISELED;
        return fromEdge(x, z, RING_R) < 0.9 ? PILLAR : DARK_GLASS;
    }

    // ---- as a hall --------------------------------------------------------------------------------------------------

    /** The band a fight changes: the room laid over the middle (dy -1 to 13), the mirrors (to 16) and the ring. */
    static final int BAND_LO = -1, BAND_HI = 16;

    static final Hall HALL = new Hall() {
        @Override public int atX() { return AT_X; }
        @Override public int atZ() { return AT_Z; }
        @Override public int wall() { return OUTER; }
        @Override public int north() { return -OUTER; }
        @Override public int back() { return BACK; }
        @Override public int low() { return LOW; }
        @Override public int roof() { return ROOF; }
        @Override public int bandLo() { return BAND_LO; }
        @Override public int bandHi() { return BAND_HI; }
        @Override public @Nullable BlockState block(int x, int dy, int z) { return Reflections.block(x, dy, z); }

        @Override
        public boolean standing(ServerLevel level, BlockPos c) {
            return is(level, c, 0, 0, 0) && is(level, c, 0, 0, -30) && is(level, c, WALL_R, 20, 0) && is(level, c, 20, 25, 0);
        }
    };
}
