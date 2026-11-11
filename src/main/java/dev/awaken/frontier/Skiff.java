package dev.awaken.frontier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The skiff and its mooring as blocks (SPEC §67.4): an old Precursor wind-skiff that people have kept flying. A slim
 * hull of navy plating with a gold keel and the lift core in its fin; a planked deck with low gold-capped gunwales and a
 * quarterdeck at the stern, where the helm stands a step above everything so the one at it sees over the bow; a long
 * beak with the bow light; two canvas wings on booms; and at the stern a great canvas wind-wheel. Round it, the mooring:
 * the old landing ring set in a stone pad, a timber cradle, a pier along one side with a roofed bay, and a mast with
 * three lights. Both sides use it: the server builds it, the client draws the skiff in flight with the same blocks, the
 * wings and the wheel moving.
 * <p>
 * The shape itself is {@link SkiffPlan}, written by {@code tools/art/skiff_plan.py} (which also draws it, so it can be
 * looked at without the game). Drawn facing south (bow at +z) round the berth's middle at the main deck's height (y 0 is
 * its planks); {@link #at} turns an offset to any facing.
 */
public final class Skiff {
    private Skiff() {}

    /** What a block moves with in flight: the hull, a wing (about its root), the wind-wheel (about its hub). */
    public static final int HULL = 0, WING_A = 1, WING_B = 2, WHEEL = 3;

    /** One block of it: where (south-facing, from the berth's middle at deck height), what, and what it moves with. */
    public record Part(int x, int y, int z, BlockState state, int rig) {}

    /** The helm (on the quarterdeck) and where you stand at it; the hold and the board (in the landing's bay). */
    public static final BlockPos HELM = pos(SkiffPlan.HELM), STAND = pos(SkiffPlan.STAND), HOLD = pos(SkiffPlan.HOLD), BOARD = pos(SkiffPlan.BOARD);
    /** The wind-wheel's hub; the wings' height and how far out their roots are. */
    public static final BlockPos HUB = pos(SkiffPlan.HUB);
    public static final int WING_Y = SkiffPlan.WING_Y;
    public static final float WING_ROOT = SkiffPlan.WING_ROOT;
    /** The deck you stand on to fly (inclusive, south-facing): the main deck and the quarterdeck. */
    public static final int DECK_X = 2, DECK_Z0 = -6, DECK_Z1 = 7;
    /** The whole mooring's footprint (inclusive, south-facing), and how far above the deck it keeps clear. */
    public static final int FOOT_X = SkiffPlan.FOOT_X, FOOT_Z0 = SkiffPlan.FOOT_Z0, FOOT_Z1 = SkiffPlan.FOOT_Z1, CLEAR_UP = SkiffPlan.CLEAR_UP;
    /** The lowest block of the skiff (the lift core in its fin), and the pad's top under it. */
    public static final int KEEL = SkiffPlan.KEEL, PAD = SkiffPlan.PAD;
    /** The pad is a disc this wide round the berth's middle (its radius, squared). */
    public static final int PAD_R2 = SkiffPlan.PAD_R2;
    /** Where the pier's posts and the mast go down to the ground (south-facing, below the deck). */
    public static final int[][] POSTS = SkiffPlan.POSTS;
    /** The steps go down from the pier's stern end, toward -z, these columns wide. */
    public static final int[] STEPS_X = SkiffPlan.STEPS_X;
    public static final int STEPS_Z = SkiffPlan.STEPS_Z;

    private static @Nullable List<Part> skiff, jetty, landingExtras, ridgeExtras;

    /** The skiff alone: what lifts away. */
    public static List<Part> skiff() {
        if (skiff == null) skiff = parts(SkiffPlan.SKIFF);
        return skiff;
    }

    /** The mooring round it: the pier, the bay, the mast, the cradle and the landing ring (the pad and the posts are fitted to the ground where it's built). */
    public static List<Part> jetty() {
        if (jetty == null) jetty = parts(SkiffPlan.JETTY);
        return jetty;
    }

    /** The landing's bay holds the hold (a barrel) and the Association's board. */
    public static List<Part> landingExtras() {
        if (landingExtras == null) landingExtras = parts(SkiffPlan.LANDING_EXTRAS);
        return landingExtras;
    }

    /** The ridge's bay just has goods waiting. */
    public static List<Part> ridgeExtras() {
        if (ridgeExtras == null) ridgeExtras = parts(SkiffPlan.RIDGE_EXTRAS);
        return ridgeExtras;
    }

    private static List<Part> parts(int[] rows) {
        BlockState[] states = new BlockState[SkiffPlan.STATES.length];
        for (int i = 0; i < states.length; i++) states[i] = state(SkiffPlan.STATES[i]);
        List<Part> out = new ArrayList<>(rows.length / 5);
        for (int i = 0; i + 4 < rows.length; i += 5) out.add(new Part(rows[i], rows[i + 1], rows[i + 2], states[rows[i + 3]], rows[i + 4]));
        return List.copyOf(out);
    }

    private static BlockPos pos(int[] xyz) {
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    /** One offset turned to face {@code facing}, from {@code origin} (the berth's middle at deck height). */
    public static BlockPos at(BlockPos origin, Direction facing, int x, int y, int z) {
        return switch (facing) {
            case WEST -> origin.offset(-z, y, x);
            case NORTH -> origin.offset(-x, y, -z);
            case EAST -> origin.offset(z, y, -x);
            default -> origin.offset(x, y, z);
        };
    }

    public static BlockPos at(BlockPos origin, Direction facing, BlockPos local) {
        return at(origin, facing, local.getX(), local.getY(), local.getZ());
    }

    /** The same turn for a point that isn't on the grid: {x, z} in the world's frame. */
    public static double[] turn(Direction facing, double x, double z) {
        return switch (facing) {
            case WEST -> new double[] {-z, x};
            case NORTH -> new double[] {-x, -z};
            case EAST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
    }

    /** The turn that takes a south-facing block to {@code facing}. */
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Whether a point (world position) is on the deck of the skiff moored at {@code origin} facing {@code facing}. */
    public static boolean onDeck(BlockPos origin, Direction facing, double px, double py, double pz) {
        double[] l = local(origin, facing, px, pz);
        double dy = py - (origin.getY() + 1);
        return Math.abs(l[0]) <= DECK_X + 0.5 && l[1] >= DECK_Z0 - 0.5 && l[1] <= DECK_Z1 + 0.5 && dy > -0.6 && dy < 2.5;
    }

    /** Whether a block is part of the mooring's footprint (from the pad to the clear air over it). */
    public static boolean inFootprint(BlockPos origin, Direction facing, BlockPos pos) {
        double[] l = local(origin, facing, pos.getX() + 0.5, pos.getZ() + 0.5);
        int dy = pos.getY() - origin.getY();
        return Math.abs(l[0]) <= FOOT_X + 0.5 && l[1] >= FOOT_Z0 - 0.5 && l[1] <= FOOT_Z1 + 0.5 && dy >= PAD && dy <= CLEAR_UP;
    }

    /** A world point back in the south-facing frame: {x, z} from the berth's middle. */
    static double[] local(BlockPos origin, Direction facing, double px, double pz) {
        double dx = px - (origin.getX() + 0.5), dz = pz - (origin.getZ() + 0.5);
        return switch (facing) {
            case WEST -> new double[] {dz, -dx};
            case NORTH -> new double[] {-dx, -dz};
            case EAST -> new double[] {-dz, dx};
            default -> new double[] {dx, dz};
        };
    }

    /** The yaw a player at the helm faces, toward the bow (Minecraft's: south 0, west 90, north 180, east -90). */
    public static float yaw(Direction facing) {
        return facing.toYRot();
    }

    /** A block by id (the game's own unless it names a namespace). */
    static BlockState b(String id) {
        return state(id.contains(":") ? id : "minecraft:" + id);
    }

    /** A state from the plan's words: {@code namespace:block[property=value,...]}. A block that isn't there is stone. */
    static BlockState state(String spec) {
        int open = spec.indexOf('[');
        String id = open < 0 ? spec : spec.substring(0, open);
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == Blocks.AIR && !id.endsWith("air")) return Blocks.STONE.defaultBlockState();
        BlockState s = block.defaultBlockState();
        if (open < 0) return s;
        for (String pair : spec.substring(open + 1, spec.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            Property<?> property = block.getStateDefinition().getProperty(pair.substring(0, eq));
            if (property != null) s = with(s, property, pair.substring(eq + 1));
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> property, String value) {
        return property.getValue(value).map(v -> s.setValue(property, v)).orElse(s);
    }
}
