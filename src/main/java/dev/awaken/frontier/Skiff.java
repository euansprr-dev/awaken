package dev.awaken.frontier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The skiff and its mooring as blocks (SPEC §67): an old Precursor skiff, a hull of plating with a gold keel and a
 * glowing core under it, a plank deck with dark rails, a mast with a canvas sail on a spar and a lantern at each end, an end
 * rod at the bow and a glowing helm at the stern; a plank jetty round it on log posts, open at the bow so it can lift away.
 * Both sides use it: the server builds it, the client draws the skiff in flight with the same blocks.
 * <p>
 * Drawn facing south (bow at +z) round the berth's middle at the deck's height (y 0 is the deck's planks); {@link #at}
 * turns an offset to any facing. The skiff's own blocks are {@link #skiff}; the jetty's are {@link #jetty}.
 */
public final class Skiff {
    private Skiff() {}

    /** One block of it: where (south-facing, from the berth's middle at deck height) and what. */
    public record Part(int x, int y, int z, BlockState state) {}

    /** The helm (stern, waist high) and where you stand at it; the hold and the board (the landing's jetty only). */
    public static final BlockPos HELM = new BlockPos(0, 1, -3), STAND = new BlockPos(0, 1, -4),
            HOLD = new BlockPos(4, 1, -6), BOARD = new BlockPos(-4, 1, -6);
    /** The deck you stand on to fly (inclusive, south-facing): the skiff's deck and the gangways. */
    public static final int DECK_X = 3, DECK_Z0 = -5, DECK_Z1 = 6;
    /** The whole mooring's footprint (inclusive, south-facing), and how far above the deck it keeps clear. */
    public static final int FOOT_X = 5, FOOT_Z0 = -9, FOOT_Z1 = 7, CLEAR_UP = 10;
    /** The lowest block of the hull (the keel), below the deck. */
    public static final int KEEL = -2;

    private static @Nullable List<Part> skiff, jetty, landingExtras;

    /** The skiff alone: what lifts away. */
    public static List<Part> skiff() {
        if (skiff == null) skiff = buildSkiff();
        return skiff;
    }

    /** The jetty round it (posts are added to the ground where it's built). */
    public static List<Part> jetty() {
        if (jetty == null) jetty = buildJetty();
        return jetty;
    }

    /** The landing's jetty also has the hold (a barrel) and the Association's board (a lectern). */
    public static List<Part> landingExtras() {
        if (landingExtras == null) {
            List<Part> out = new ArrayList<>();
            out.add(new Part(HOLD.getX(), HOLD.getY(), HOLD.getZ(), b("barrel").setValue(BlockStateProperties.FACING, Direction.UP)));
            out.add(new Part(BOARD.getX(), BOARD.getY(), BOARD.getZ(), b("lectern").setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)));
            landingExtras = out;
        }
        return landingExtras;
    }

    private static List<Part> buildSkiff() {
        List<Part> out = new ArrayList<>();
        BlockState plating = b("awaken:precursor_plating"), trim = b("awaken:precursor_trim"), glow = b("awaken:precursor_glow_panel");
        BlockState deck = b("dark_oak_planks"), rail = b("dark_oak_fence");
        BlockState mast = b("stripped_spruce_log"), sail = b("white_wool");
        // the deck: 5 wide, a narrowing bow and stern
        for (int x = -2; x <= 2; x++) for (int z = -4; z <= 4; z++) out.add(new Part(x, 0, z, deck));
        for (int x = -1; x <= 1; x++) {
            out.add(new Part(x, 0, 5, deck));
            out.add(new Part(x, 0, -5, deck));
        }
        out.add(new Part(0, 0, 6, trim));
        // the hull under it: plating, rounded at the ends, a gold keel and the glowing core
        for (int x = -1; x <= 1; x++) for (int z = -4; z <= 5; z++) out.add(new Part(x, -1, z, plating));
        for (int z = -2; z <= 2; z++) {
            out.add(new Part(-2, -1, z, stair("dark_oak_stairs", Direction.EAST, true)));
            out.add(new Part(2, -1, z, stair("dark_oak_stairs", Direction.WEST, true)));
        }
        for (int z = -3; z <= 4; z++) out.add(new Part(0, KEEL, z, z == 0 || z == 1 ? glow : trim));
        // the rails, open at the gangways (z 0, both sides) and at the helm's back
        for (int z = -4; z <= 4; z++) {
            if (z == 0) continue;
            out.add(new Part(-2, 1, z, rail));
            out.add(new Part(2, 1, z, rail));
        }
        out.add(new Part(-1, 1, 5, rail));
        out.add(new Part(1, 1, 5, rail));
        out.add(new Part(-1, 1, -5, rail));
        out.add(new Part(1, 1, -5, rail));
        // the bow light, pointing the way
        out.add(new Part(0, 1, 6, b("end_rod").setValue(BlockStateProperties.FACING, Direction.SOUTH)));
        // the helm: a glowing console at the stern, waist high, so the one at it looks out over the bow
        out.add(new Part(HELM.getX(), HELM.getY(), HELM.getZ(), glow));
        // the mast, the spar across its top with a lantern hung at each end, and the sail before it
        for (int y = 1; y <= 8; y++) out.add(new Part(0, y, 1, mast));
        for (int x = -3; x <= 3; x++) if (x != 0) out.add(new Part(x, 7, 1, mast.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)));
        out.add(new Part(-3, 6, 1, b("lantern").setValue(LanternBlock.HANGING, true)));
        out.add(new Part(3, 6, 1, b("lantern").setValue(LanternBlock.HANGING, true)));
        for (int x = -2; x <= 2; x++) for (int y = 3; y <= 6; y++) out.add(new Part(x, y, 2, sail));
        out.add(new Part(0, 9, 1, b("lantern")));
        return List.copyOf(out);
    }

    private static List<Part> buildJetty() {
        List<Part> out = new ArrayList<>();
        BlockState plank = b("spruce_planks"), rail = b("spruce_fence");
        // two arms either side of the berth and a cross-piece at the stern; open at the bow
        for (int z = -8; z <= 6; z++) {
            for (int x = 4; x <= 5; x++) {
                out.add(new Part(x, 0, z, plank));
                out.add(new Part(-x, 0, z, plank));
            }
        }
        for (int x = -3; x <= 3; x++) for (int z = -8; z <= -6; z++) out.add(new Part(x, 0, z, plank));
        // the gangways onto the deck
        out.add(new Part(3, 0, 0, plank));
        out.add(new Part(-3, 0, 0, plank));
        // a rail round the outside, with a gap at the stern for the steps
        for (int z = -8; z <= 6; z++) {
            out.add(new Part(5, 1, z, rail));
            out.add(new Part(-5, 1, z, rail));
        }
        for (int x = -4; x <= 4; x++) if (Math.abs(x) > 1) out.add(new Part(x, 1, -8, rail));
        // lanterns on the bow posts
        out.add(new Part(5, 2, 6, b("lantern")));
        out.add(new Part(-5, 2, 6, b("lantern")));
        return List.copyOf(out);
    }

    /** Where the jetty's posts go down to the ground (south-facing, below the deck). */
    public static final int[][] POSTS = {{5, -8}, {-5, -8}, {5, -1}, {-5, -1}, {5, 6}, {-5, 6}, {2, -8}, {-2, -8}};

    /** The steps go down from the stern's gap, toward -z. */
    public static final int STEPS_Z = -9;

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

    /** The turn that takes a south-facing block to {@code facing}. */
    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Whether a block (world position) is on the deck of the skiff moored at {@code origin} facing {@code facing}. */
    public static boolean onDeck(BlockPos origin, Direction facing, double px, double py, double pz) {
        double[] l = local(origin, facing, px, pz);
        double dy = py - (origin.getY() + 1);
        return Math.abs(l[0]) <= DECK_X + 0.5 && l[1] >= DECK_Z0 - 0.5 && l[1] <= DECK_Z1 + 0.5 && dy > -0.6 && dy < 2.5;
    }

    /** Whether a block is part of the mooring's footprint (from the keel to the clear air over it). */
    public static boolean inFootprint(BlockPos origin, Direction facing, BlockPos pos) {
        double[] l = local(origin, facing, pos.getX() + 0.5, pos.getZ() + 0.5);
        int dy = pos.getY() - origin.getY();
        return Math.abs(l[0]) <= FOOT_X + 0.5 && l[1] >= FOOT_Z0 - 0.5 && l[1] <= FOOT_Z1 + 0.5 && dy >= KEEL - 1 && dy <= CLEAR_UP;
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

    private static BlockState stair(String id, Direction facing, boolean top) {
        return b(id).setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState b(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        var block = BuiltInRegistries.BLOCK.getValue(key);
        return block == Blocks.AIR && !id.endsWith("air") ? Blocks.STONE.defaultBlockState() : block.defaultBlockState();
    }
}
