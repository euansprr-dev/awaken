package dev.awaken.delve.cave;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Delvers' mine (PLAN 8.2): the abandoned workings of the people who dug Carillon's bell metal, strung from rooms
 * like beads (G45): a timbered adit in a hillside with rails leading in, the mine head (the winch over the main shaft,
 * the foreman's office with a Delver's log, the lode map and an old pick), a ladder down the shaft to the first gallery,
 * a drowned stope across it (run its cut wall, or drop in and climb the ladder out), a rail incline to ride down in a
 * minecart beside the steps, the second gallery (a fall of roof to slide under, the camp, a rope bridge over the
 * chasm) and at its end the breakthrough the logs fear: they dug too deep and broke into a seep.
 *
 * <p>Every gallery wall is a dressed face, flat and on the grid two blocks and more high, its timber set flush, so the
 * Runner can run it (RunnerRules: an axis-aligned plane, feet 1.5 over the ground). Spec: (x, y, z) the portal's floor
 * (feet), {@code rot} forward into the hill, p = [adit length].
 */
final class Mine {
    private Mine() {}

    /** Local depths (feet) of the first and second galleries below the portal. */
    static final int G1 = -21, G2 = -35;
    static final int INCLINE = 14;
    static final int G1_LEN = 40, G2_LEN = 38;

    static int adit(Spec s) {
        return Math.max(18, s.p(0));
    }

    static BoundingBox box(Spec s) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int L = adit(s);
        int ax = f.x(-14, -9), az = f.z(-14, -9), bx = f.x(33, L + 9 + G1_LEN + 3), bz = f.z(33, L + 9 + G1_LEN + 3);
        return new BoundingBox(Math.min(ax, bx), s.y() + G2 - 28, Math.min(az, bz), Math.max(ax, bx), s.y() + 9, Math.max(az, bz));
    }

    static void build(Spec s, Sink k) {
        Frame f = new Frame(s.x(), s.y(), s.z(), s.rot());
        int L = adit(s);
        long seed = s.seed();
        adit(f, k, L, seed);
        head(f, k, L, seed, s);
        gallery1(f, k, L, seed);
        incline(f, k, L, seed);
        gallery2(back(f, L), k, L, seed, s);
    }

    /**
     * The second gallery's frame: it runs back under the hill from the incline's foot (the workings fold into a U, so the
     * whole mine stays within a structure's reach of its start), its lanes on the same blocks as the first frame's.
     */
    static Frame back(Frame f, int L) {
        int zr = L + 9 + G1_LEN - 2, gx = 6 + INCLINE;
        return new Frame(f.x(2 * gx + 2, 2 * zr + 2), f.oy, f.z(2 * gx + 2, 2 * zr + 2), f.rot + 2);
    }

    /** Where the breakthrough's chamber is (world x, y, z), for the site's search for the caves beyond it. */
    static int[] end(Spec s) {
        Frame g = back(new Frame(s.x(), s.y(), s.z(), s.rot()), adit(s));
        int zr = adit(s) + 9 + G1_LEN - 2, gx = 6 + INCLINE, z1 = zr + G2_LEN;
        return new int[]{g.x(gx + 1, z1 + 7), s.y() + G2, g.z(gx + 1, z1 + 7)};
    }

    // ---- the adit and its portal -------------------------------------------------------------------------------------

    private static void adit(Frame f, Sink k, int L, long seed) {
        if (!f.touches(k, -8, -9, 8, L + 1)) return;
        // the cutting in the hillside in front of the portal, widening out
        for (int lz = -8; lz < 0; lz++) {
            int w = 2 + (-lz) / 3;
            f.carve(k, -w, 0, lz, w, 6, lz);
            for (int lx = -w; lx <= w; lx++) {
                if (!f.inside(k, lx, -1, lz)) continue;
                BlockState under = f.get(k, lx, -1, lz);
                if (!under.isSolidRender()) f.put(k, lx, -1, lz, Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
        // spoil heaps beside the cutting
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) f.put(k, side * (4 + i), 0, -3 - i, i == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
        }
        f.carve(k, -1, 0, 0, 1, 3, L - 1);
        for (int lz = -4; lz < L; lz++) {
            for (int lx = -1; lx <= 1; lx++) f.put(k, lx, -1, lz, lx == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
            if (lz < L - 1 && (lz < -1 || lz % 7 != 3)) f.put(k, 0, 0, lz, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        }
        // the portal: heavy posts and a lintel, a lantern hook
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState beam = Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int ly = -1; ly <= 4; ly++) {
            f.put(k, -2, ly, 0, post);
            f.put(k, 2, ly, 0, post);
        }
        for (int lx = -3; lx <= 3; lx++) f.put(k, lx, 4, 0, beam);
        f.put(k, -3, 5, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        f.put(k, 3, 5, 0, Blocks.DARK_OAK_SLAB.defaultBlockState());
        f.put(k, 1, 3, 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // timber sets every four blocks in
        for (int lz = 4; lz < L; lz += 4) set(f, k, -2, 2, 0, 3, lz);
        f.mark(k, "start", 0, 0, -7);
    }

    /** A timber set across a passage: posts flush in the walls at lx0 and lx1, a cap in the roof at ly1 + 1. */
    private static void set(Frame f, Sink k, int lx0, int lx1, int ly0, int ly1, int lz) {
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState cap = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int ly = ly0; ly <= ly1; ly++) {
            f.put(k, lx0, ly, lz, post);
            f.put(k, lx1, ly, lz, post);
        }
        for (int lx = lx0; lx <= lx1; lx++) f.put(k, lx, ly1 + 1, lz, cap);
    }

    // ---- the mine head: the winch, the office ------------------------------------------------------------------------

    private static void head(Frame f, Sink k, int L, long seed, Spec s) {
        if (!f.touches(k, -13, L - 1, 7, L + 13)) return;
        f.carve(k, -5, 0, L, 5, 5, L + 12);
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int lx = -5; lx <= 5; lx++) for (int lz = L; lz <= L + 12; lz++) {
            if (lx >= 2 && lx <= 4 && lz >= L + 9 && lz <= L + 11) continue; // the shaft
            f.put(k, lx, -1, lz, Carve.rand(seed + 41, lx, 0, lz) < 0.7 ? floor : Blocks.GRAVEL.defaultBlockState());
        }
        // pillars and the roof's beams
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int lz : new int[]{L + 4, L + 8}) for (int lx : new int[]{-3, 0}) for (int ly = 0; ly <= 5; ly++) f.put(k, lx, ly, lz, post);
        BlockState beam = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int lz : new int[]{L + 4, L + 8, L + 12}) for (int lx = -5; lx <= 5; lx++) f.put(k, lx, 6, lz, beam);
        f.put(k, -1, 5, L + 6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        f.put(k, 3, 5, L + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // the main shaft (3 x 3) down to the first gallery, its ladder on the far wall, a rail round its mouth
        f.carve(k, 2, G1, L + 9, 4, 5, L + 11);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        for (int ly = G1; ly <= -1; ly++) {
            f.put(k, 5, ly, L + 10, Carve.dressed(f.y(ly)));
            f.put(k, 4, ly, L + 10, ladder);
        }
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        for (int lz = L + 8; lz <= L + 12; lz++) f.put(k, 1, 0, lz, fence);
        for (int lx = 2; lx <= 4; lx++) {
            f.put(k, lx, 0, L + 8, fence);
            f.put(k, lx, 0, L + 12, fence);
        }
        // cribbing down the shaft every five blocks
        for (int ly = -3; ly > G1 + 2; ly -= 5) {
            for (int lz = L + 8; lz <= L + 12; lz++) {
                f.put(k, 1, ly, lz, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            for (int lx = 1; lx <= 5; lx++) {
                f.put(k, lx, ly, L + 8, Blocks.SPRUCE_PLANKS.defaultBlockState());
                f.put(k, lx, ly, L + 12, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        }
        // the winch: a drum over the shaft on two trestles, its chain hanging into the dark
        BlockState drum = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        f.carve(k, 3, 1, L + 13, 3, 5, L + 13);
        for (int lz = L + 7; lz <= L + 13; lz++) f.put(k, 3, 5, lz, drum);
        for (int ly = 1; ly <= 4; ly++) {
            f.put(k, 3, ly, L + 7, Blocks.SPRUCE_FENCE.defaultBlockState());
            f.put(k, 3, ly, L + 13, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int ly = 4; ly >= -9; ly--) f.put(k, 3, ly, L + 10, Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        // crates, a barrel of picks, spoil
        f.put(k, -5, 0, L + 1, Blocks.BARREL.defaultBlockState());
        f.put(k, -4, 0, L + 1, Blocks.BARREL.defaultBlockState());
        f.put(k, -5, 1, L + 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        f.put(k, 5, 0, L + 1, Blocks.GRAVEL.defaultBlockState());
        f.put(k, 5, 0, L + 2, Blocks.GRAVEL.defaultBlockState());
        f.put(k, 4, 0, L + 1, Blocks.GRAVEL.defaultBlockState());
        // the foreman's office, through a door in the west wall
        f.carve(k, -6, 0, L + 4, -6, 1, L + 4);
        f.carve(k, -11, 0, L + 2, -7, 2, L + 7);
        for (int lx = -11; lx <= -7; lx++) for (int lz = L + 2; lz <= L + 7; lz++) f.put(k, lx, -1, lz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        f.put(k, -11, 0, L + 3, Blocks.SPRUCE_PLANKS.defaultBlockState());
        f.put(k, -11, 0, L + 4, Blocks.SPRUCE_PLANKS.defaultBlockState());
        f.put(k, -11, 1, L + 3, Blocks.LANTERN.defaultBlockState());
        f.put(k, -10, 0, L + 3, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        f.put(k, -7, 0, L + 7, Blocks.BOOKSHELF.defaultBlockState());
        f.put(k, -8, 0, L + 7, Blocks.BOOKSHELF.defaultBlockState());
        f.put(k, -7, 1, L + 7, Blocks.FLOWER_POT.defaultBlockState());
        f.put(k, -11, 0, L + 7, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        f.put(k, -10, 0, L + 7, Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        List<ItemStack> office = new ArrayList<>();
        office.add(CaveItems.log(0));
        office.add(CaveItems.map());
        office.add(CaveItems.pick());
        office.add(new ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 2));
        office.add(new ItemStack(net.minecraft.world.item.Items.TORCH, 8));
        int cx = f.x(-11, L + 6), cz = f.z(-11, L + 6);
        k.container(cx, f.y(0), cz, Carve.turn(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), f.rot), office);
        f.mark(k, "office", -9, 0, L + 5);
    }

    // ---- the first gallery: the drowned stope ------------------------------------------------------------------------

    private static void gallery1(Frame f, Sink k, int L, long seed) {
        int z0 = L + 9, z1 = L + 9 + G1_LEN;
        if (!f.touches(k, 0, z0 - 1, 7, z1 + 1)) return;
        f.carve(k, 2, G1, z0, 4, G1 + 4, z1);
        dress(f, k, 1, 5, G1, z0 + 3, z1, G1 + 4);
        // the shaft's ladder runs on down to the gallery floor (the gallery's bore took its foot)
        for (int ly = G1; ly <= G1 + 4; ly++) {
            f.put(k, 5, ly, L + 10, Carve.dressed(f.y(ly)));
            f.put(k, 4, ly, L + 10, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        // floor, rails down its middle
        for (int lz = z0; lz <= z1; lz++) {
            for (int lx = 2; lx <= 4; lx++) f.put(k, lx, G1 - 1, lz, lx == 3 ? Blocks.GRAVEL.defaultBlockState() : Carve.rock(f.y(G1 - 1)));
            if (lz > z0 + 3 && lz < z1 - 3) f.put(k, 3, G1, lz, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        }
        for (int lz = z0 + 6; lz < z1; lz += 6) {
            set(f, k, 1, 5, G1, G1 + 4, lz);
            if ((lz / 6) % 2 == 0) f.put(k, 3, G1 + 4, lz + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        // the drowned stope: the floor fell into old workings; water two deep, ladders out at both ends
        int p0 = z0 + 15, p1 = z0 + 23;
        f.carve(k, 2, G1 - 4, p0, 4, G1 - 1, p1);
        for (int lz = p0; lz <= p1; lz++) for (int lx = 2; lx <= 4; lx++) {
            f.put(k, lx, G1 - 5, lz, Blocks.GRAVEL.defaultBlockState());
            f.put(k, lx, G1 - 4, lz, Carve.WATER);
            f.put(k, lx, G1 - 3, lz, Carve.WATER);
        }
        dress(f, k, 1, 5, G1 - 4, p0, p1, G1 - 1);
        for (int ly = G1 - 3; ly <= G1 - 1; ly++) {
            f.put(k, 3, ly, p0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            f.put(k, 3, ly, p1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        // what's left of the plank bridge that crossed it
        f.put(k, 2, G1 - 1, p0 + 1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        f.put(k, 4, G1 - 1, p1 - 1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        f.mark(k, "stope", 3, G1, p0 - 1);
    }

    /** Dressed walls (the Runner's planes) at lx0 and lx1 from ly0 to ly1, along lz0..lz1. */
    private static void dress(Frame f, Sink k, int lx0, int lx1, int ly0, int lz0, int lz1, int ly1) {
        for (int lz = lz0; lz <= lz1; lz++) for (int ly = ly0; ly <= ly1; ly++) {
            BlockState d = Carve.dressed(f.y(ly));
            f.put(k, lx0, ly, lz, d);
            f.put(k, lx1, ly, lz, d);
        }
    }

    // ---- the incline: rails and steps down to the second gallery -----------------------------------------------------

    private static void incline(Frame f, Sink k, int L, long seed) {
        int zr = L + 9 + G1_LEN - 2; // the rail lane; the steps are the two lanes after it
        if (!f.touches(k, 4, zr - 1, 7 + INCLINE, zr + 3)) return;
        // through the gallery's east wall at its end
        f.carve(k, 5, G1, zr, 5, G1 + 3, zr + 2);
        f.put(k, 5, G1 - 1, zr, Carve.rock(f.y(G1 - 1)));
        f.put(k, 4, G1, zr, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        f.put(k, 5, G1, zr, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        f.put(k, 3, G1, zr, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        for (int lx = 3; lx <= 5; lx++) for (int lz = zr; lz <= zr + 2; lz++) f.put(k, lx, G1 - 1, lz, Carve.rock(f.y(G1 - 1)));
        k.minecart(f.x(4, zr), f.y(G1), f.z(4, zr));
        for (int i = 0; i < INCLINE; i++) {
            int lx = 6 + i, top = G1 - 1 - i; // the rail's cell; the block under it at top - 1
            f.carve(k, lx, top, zr, lx, top + 4, zr + 2);
            f.put(k, lx, top - 1, zr, Carve.bricks(f.y(top - 1)));
            f.put(k, lx, top, zr, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.ASCENDING_WEST));
            // the steps beside the rails: each stair's high half level with the step above it
            BlockState step = Carve.stairs(f.y(top)).setValue(StairBlock.FACING, Direction.WEST);
            for (int lz = zr + 1; lz <= zr + 2; lz++) {
                f.put(k, lx, top - 1, lz, Carve.bricks(f.y(top - 1)));
                f.put(k, lx, top, lz, step);
            }
            // the roof's timber every four steps
            if (i % 4 == 1) for (int lz = zr; lz <= zr + 2; lz++) f.put(k, lx, top + 4, lz, Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        f.put(k, 6 + INCLINE / 2, G1 - INCLINE / 2 + 3, zr + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        f.mark(k, "incline", 4, G1, zr + 1);
    }

    // ---- the second gallery: the fall of roof, the camp, the chasm, the breakthrough ----------------------------------

    private static void gallery2(Frame f, Sink k, int L, long seed, Spec s) {
        int zr = L + 9 + G1_LEN - 2;
        int gx = 6 + INCLINE; // the gallery's west lane
        int z0 = zr, z1 = zr + G2_LEN;
        if (!f.touches(k, gx - 12, z0 - 1, gx + 14, z1 + 14)) return;
        f.carve(k, gx, G2, z0, gx + 2, G2 + 4, z1);
        dress(f, k, gx - 1, gx + 3, G2, z0 + 3, z1, G2 + 4);
        for (int lz = z0; lz <= z1; lz++) for (int lx = gx; lx <= gx + 2; lx++) f.put(k, lx, G2 - 1, lz, Carve.rock(f.y(G2 - 1)));
        // the incline's foot (this frame is turned: the rail lane is its last): flat rails and a stop
        f.put(k, gx + 2, G2, zr + 2, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        f.put(k, gx + 1, G2, zr + 2, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, RailShape.EAST_WEST));
        f.put(k, gx, G2, zr + 2, Blocks.HAY_BLOCK.defaultBlockState());
        for (int lz = z0 + 6; lz < z1; lz += 6) set(f, k, gx - 1, gx + 3, G2, G2 + 4, lz);
        // a fall of roof: rubble to the ceiling but a crawl along the floor (slide under it)
        int c0 = z0 + 11, c1 = z0 + 15;
        for (int lz = c0; lz <= c1; lz++) for (int lx = gx; lx <= gx + 2; lx++) {
            for (int ly = G2 + 1; ly <= G2 + 4; ly++) {
                double n = Carve.rand(seed + 51, lx, ly, lz);
                f.put(k, lx, ly, lz, n < 0.45 ? Carve.rubble(f.y(ly)) : n < 0.7 ? Blocks.GRAVEL.defaultBlockState() : Carve.rock(f.y(ly)));
            }
        }
        for (int lz = c0 - 1; lz <= c1 + 1; lz += c1 - c0 + 2) {
            f.put(k, gx, G2, lz, Carve.rubble(f.y(G2)));
            f.put(k, gx + 2, G2 + 1, lz, Blocks.GRAVEL.defaultBlockState());
        }
        f.mark(k, "crawl", gx + 1, G2, c0 - 2);
        // the camp: an alcove with a cold fire, a bedroll and a barrel
        int cz = z0 + 22;
        f.carve(k, gx + 3, G2, cz, gx + 6, G2 + 2, cz + 3);
        f.put(k, gx + 5, G2, cz + 1, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        f.put(k, gx + 6, G2, cz, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState());
        f.put(k, gx + 6, G2, cz + 1, Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState());
        f.put(k, gx + 4, G2 + 2, cz + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        List<ItemStack> camp = new ArrayList<>();
        camp.add(CaveItems.log(1));
        camp.add(new ItemStack(net.minecraft.world.item.Items.BREAD, 3));
        camp.add(new ItemStack(net.minecraft.world.item.Items.RAW_COPPER, 6));
        k.container(f.x(gx + 6, cz + 3), f.y(G2), f.z(gx + 6, cz + 3), Blocks.BARREL.defaultBlockState(), camp);
        // the chasm across the gallery and the rope bridge over it
        int h0 = z0 + 30, h1 = h0 + 5;
        chasm(f, k, gx, h0, h1, seed);
        bridge(f, k, gx, h0, h1);
        // the end: the drift they broke through, and what they found
        breakthrough(f, k, gx, z1, seed, s);
    }

    private static void chasm(Frame f, Sink k, int gx, int h0, int h1, long seed) {
        int bottom = G2 - 26;
        for (int lx = gx - 10; lx <= gx + 12; lx++) {
            double edge = Math.abs(lx - (gx + 1)) / 11.0;
            for (int lz = h0 - 1; lz <= h1 + 1; lz++) {
                for (int ly = bottom; ly <= G2 + 6; ly++) {
                    double n = Carve.noise(seed + 61, lx, ly, lz, 4);
                    boolean in = lz >= h0 && lz <= h1 || n > 0.62;
                    // narrower toward its ends and its top, as a crack is
                    if (!in || edge > 0.95 - (ly - bottom) * 0.006 || (ly > G2 + 4 && Math.abs(lx - (gx + 1)) > 3)) continue;
                    f.air(k, lx, ly, lz);
                }
                for (int ly = bottom; ly <= bottom + 2; ly++) if (lz >= h0 && lz <= h1 && edge < 0.9) f.put(k, lx, ly, lz, Carve.WATER);
            }
        }
    }

    /** A rope bridge: planks two wide that sag a block in the middle, posts and fence rails, chains up to the roof. */
    private static void bridge(Frame f, Sink k, int gx, int h0, int h1) {
        BlockState plank = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int lz = h0 - 1; lz <= h1 + 1; lz++) {
            boolean sag = lz > h0 + 1 && lz < h1 - 1;
            int deck = G2 - 1 - (sag ? 1 : 0);
            for (int lx = gx + 1; lx <= gx + 2; lx++) f.put(k, lx, deck, lz, plank);
            f.put(k, gx, deck + 1, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
            f.put(k, gx + 3, deck + 1, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
            f.put(k, gx, deck, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
            f.put(k, gx + 3, deck, lz, Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        for (int lz : new int[]{h0 - 1, h1 + 1}) for (int lx : new int[]{gx, gx + 3}) {
            for (int ly = G2; ly <= G2 + 4; ly++) f.put(k, lx, ly, lz, Blocks.SPRUCE_LOG.defaultBlockState());
        }
        for (int ly = G2; ly <= G2 + 4; ly++) {
            f.put(k, gx, ly, (h0 + h1) / 2, Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            f.put(k, gx + 3, ly, (h0 + h1) / 2, Blocks.IRON_CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        }
        f.mark(k, "bridge", gx + 1, G2, h0 - 2);
    }

    /** The breakthrough: the drift opens into stained rock, a half-built seal, the last log, a taste of Deepglass. */
    private static void breakthrough(Frame f, Sink k, int gx, int z1, long seed, Spec s) {
        double cx = f.x(gx + 1.5, z1 + 7.5), cz = f.z(gx + 1.5, z1 + 7.5);
        Carve.blob(k, cx, f.y(G2) + 2.5, cz, 6.5, 4.2, 6.5, seed + 71, 0.28, 3.5, f.y(G2));
        // the stain round it: obsidian black and violet, Deepglass breaking through here and there
        int r = 9;
        int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz), by = f.y(G2);
        if (k.touches(bx - r, bz - r, bx + r, bz + r)) {
            for (int x = bx - r; x <= bx + r; x++) for (int z = bz - r; z <= bz + r; z++) for (int y = by - 2; y <= by + 8; y++) {
                if (!k.inside(x, y, z)) continue;
                BlockState here = k.get(x, y, z);
                if (!here.isSolidRender() || !touchesAir(k, x, y, z)) continue;
                double n = Carve.rand(seed + 72, x, y, z);
                if (n < 0.18) k.set(x, y, z, Blocks.OBSIDIAN.defaultBlockState());
                else if (n < 0.22) k.set(x, y, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                else if (n < 0.30) k.set(x, y, z, Blocks.BLACKSTONE.defaultBlockState());
                else if (n < 0.325) k.set(x, y, z, dev.awaken.delve.DelveContent.DEEPGLASS.get().defaultBlockState());
                else if (n < 0.36) k.set(x, y, z, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        }
        // the last of the drift, cut through into the chamber
        f.carve(k, gx, G2, z1 + 1, gx + 2, G2 + 3, z1 + 5);
        for (int lz = z1 + 1; lz <= z1 + 5; lz++) for (int lx = gx; lx <= gx + 2; lx++) {
            if (f.inside(k, lx, G2 - 1, lz) && !f.get(k, lx, G2 - 1, lz).isSolidRender()) f.put(k, lx, G2 - 1, lz, Carve.rubble(f.y(G2 - 1)));
        }
        // the seal they began across the drift's mouth, a gap left where they ran out of stone
        for (int lx = gx - 1; lx <= gx + 3; lx++) for (int ly = G2; ly <= G2 + 4; ly++) {
            if (lx >= gx + 1 && lx <= gx + 2 && ly <= G2 + 1) continue;
            if (ly >= G2 + 3 && lx == gx + 2) continue;
            f.put(k, lx, ly, z1 + 1, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        }
        f.carve(k, gx + 1, G2, z1 + 1, gx + 2, G2 + 1, z1 + 1);
        List<ItemStack> last = new ArrayList<>();
        last.add(CaveItems.log(2));
        last.add(new ItemStack(net.minecraft.world.item.Items.CANDLE, 2));
        k.container(f.x(gx, z1), f.y(G2), f.z(gx, z1), Blocks.BARREL.defaultBlockState(), last);
        f.mark(k, "goal", gx + 1, G2, z1 + 6);
        // the way on: through the breakthrough into the natural caves
        if (s.joined()) Carve.tunnel(k, cx, f.y(G2), cz, s.jx() + 0.5, s.jy(), s.jz() + 0.5, 1.5, 2.0, seed + 73);
    }

    static boolean touchesAir(Sink k, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
            if (k.inside(nx, ny, nz) && k.get(nx, ny, nz).isAir()) return true;
        }
        return false;
    }
}
