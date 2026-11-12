package dev.awaken.shaping;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for the Mallet (SPEC §52): one per mechanism. CI runs them. */
final class ShapingTests {
    private ShapingTests() {}

    static void register() {
        AwakenTests.register("shaping_mesh_is_solid", 20, ShapingTests::meshIsSolid);
        AwakenTests.register("shaping_knock_steps", 20, ShapingTests::knockSteps);
        AwakenTests.register("shaping_custom_block", 20, ShapingTests::customBlock);
        AwakenTests.register("shaping_no_gain", 20, ShapingTests::noGain);
        AwakenTests.register("shaping_looks_real_or_worn", 20, ShapingTests::looksRealOrWorn);
        AwakenTests.register("shaping_run_same_kind", 20, ShapingTests::runSameKind);
        AwakenTests.register("shaping_kept_blocks", 20, ShapingTests::keptBlocks);
        AwakenTests.register("shaping_undo", 20, ShapingTests::undo);
        AwakenTests.register("shaping_looks_list", 20, ShapingTests::looksList);
        AwakenTests.register("shaping_corners_join", 20, ShapingTests::cornersJoin);
        AwakenTests.register("shaping_corners_stairs", 40, ShapingTests::cornersStairs);
    }

    /**
     * Every cut at every facing and, for the edge cuts, every corner, is a closed solid facing outward: each face has the
     * solid just behind it and nothing just in front, the faces close up, and the volume is the outline's (pushed one way
     * straight, kept where both ways keep at an outer corner, where either keeps at an inner). Faces that lie on a side of
     * the block say so, collision is near the mesh, and a notch or an under has a real stair's own shape at every corner.
     */
    static void meshIsSolid(GameTestHelper h) {
        for (Shape s : Shape.values()) {
            float[] outline = s.outline();
            for (Direction f : Direction.Plane.HORIZONTAL) {
                for (StairsShape c : StairsShape.values()) {
                    if (!s.edge() && c != StairsShape.STRAIGHT) continue;
                    String what = s + " " + f + " " + c;
                    double sx = 0, sy = 0, sz = 0, volume = 0;
                    for (Shape.Face q : s.faces(f, c)) {
                        double ax = 0, ay = 0, az = 0, cx = 0, cy = 0, cz = 0;
                        for (int i = 1; i <= 2; i++) {
                            double ux = q.x(i) - q.x(0), uy = q.y(i) - q.y(0), uz = q.z(i) - q.z(0);
                            double vx = q.x(i + 1) - q.x(0), vy = q.y(i + 1) - q.y(0), vz = q.z(i + 1) - q.z(0);
                            ax += (uy * vz - uz * vy) / 2;
                            ay += (uz * vx - ux * vz) / 2;
                            az += (ux * vy - uy * vx) / 2;
                        }
                        h.assertTrue(ax * q.nx() + ay * q.ny() + az * q.nz() > 1.0E-6, what + ": a face points inward or has no area");
                        for (int i = 0; i < 4; i++) {
                            h.assertTrue(within(q.x(i)) && within(q.y(i)) && within(q.z(i)), what + ": a face leaves the block");
                            h.assertTrue(q.cull() == null || onSide(q.cull(), q.x(i), q.y(i), q.z(i)), what + ": a face culled by " + q.cull() + " isn't on that side");
                            cx += q.x(i) / 4;
                            cy += q.y(i) / 4;
                            cz += q.z(i) / 4;
                        }
                        double e = 1.0E-3;
                        h.assertTrue(inside(outline, f, c, cx - q.nx() * e, cy - q.ny() * e, cz - q.nz() * e)
                                && !inside(outline, f, c, cx + q.nx() * e, cy + q.ny() * e, cz + q.nz() * e),
                                what + ": a face at " + (float) cx + " " + (float) cy + " " + (float) cz + " isn't on the outside of the solid");
                        sx += ax;
                        sy += ay;
                        sz += az;
                        volume += (q.x(0) * ax + q.y(0) * ay + q.z(0) * az) / 3;
                    }
                    double expected = volume(outline, c);
                    h.assertTrue(Math.abs(sx) + Math.abs(sy) + Math.abs(sz) < 1.0E-3, what + " is not closed");
                    h.assertTrue(Math.abs(volume - expected) < 2.0E-3, what + " has volume " + volume + ", its outline makes " + expected);
                    VoxelShape box = s.voxel(f, c);
                    double boxes = 0;
                    for (AABB b : box.toAabbs()) boxes += b.getXsize() * b.getYsize() * b.getZsize();
                    h.assertTrue(!box.isEmpty() && Math.abs(boxes - volume) < 0.06, what + " has collision of " + boxes + " for a mesh of " + volume);
                    if (s == Shape.NOTCH || s == Shape.UNDER) {
                        BlockState stair = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, f.getOpposite())
                                .setValue(StairBlock.HALF, s == Shape.NOTCH ? Half.BOTTOM : Half.TOP).setValue(StairBlock.SHAPE, c);
                        VoxelShape real = stair.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                        h.assertTrue(!Shapes.joinIsNotEmpty(box, real, BooleanOp.NOT_SAME), what + " is not the real stair's shape");
                    }
                }
            }
        }
        h.succeed();
    }

    private static boolean within(float v) {
        return v > -1.0E-4F && v < 1.0001F;
    }

    private static boolean onSide(Direction d, float x, float y, float z) {
        float v = switch (d.getAxis()) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
        return Math.abs(v - (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1F : 0F)) < 1.0E-4F;
    }

    /** How deep the outline is solid at height y, from the back (every cut keeps everything behind it), or -1 if not at all. */
    private static double width(float[] p, double y) {
        int n = p.length / 2;
        double w = -1;
        for (int i = 0; i < n; i++) {
            double d0 = p[i * 2], y0 = p[i * 2 + 1], d1 = p[(i + 1) % n * 2], y1 = p[(i + 1) % n * 2 + 1];
            if (y0 == y1 || y < Math.min(y0, y1) || y > Math.max(y0, y1)) continue;
            w = Math.max(w, d0 + (d1 - d0) * (y - y0) / (y1 - y0));
        }
        return w;
    }

    /** How far in from side d a point is: 0 on the far side, 1 on d itself. */
    private static double depth(Direction d, double x, double z) {
        return switch (d) {
            case EAST -> x;
            case WEST -> 1 - x;
            case SOUTH -> z;
            default -> 1 - z;
        };
    }

    /** A corner's second front, by a stair's left and right; null when straight. */
    private static @Nullable Direction second(Direction f, StairsShape c) {
        return switch (c) {
            case OUTER_LEFT, INNER_LEFT -> f.getCounterClockWise();
            case OUTER_RIGHT, INNER_RIGHT -> f.getClockWise();
            case STRAIGHT -> null;
        };
    }

    private static boolean outer(StairsShape c) {
        return c == StairsShape.OUTER_LEFT || c == StairsShape.OUTER_RIGHT;
    }

    /** Whether a point is in the solid this cut, facing and corner should make, worked out from the outline alone. */
    private static boolean inside(float[] outline, Direction f, StairsShape c, double x, double y, double z) {
        if (x < 0 || x > 1 || y < 0 || y > 1 || z < 0 || z > 1) return false;
        double w = width(outline, y);
        boolean a = depth(f, x, z) <= w;
        Direction g = second(f, c);
        if (g == null) return a;
        boolean b = depth(g, x, z) <= w;
        return outer(c) ? a && b : a || b;
    }

    private static double volume(float[] outline, StairsShape c) {
        int n = 4000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0, width(outline, (i + 0.5) / n));
            sum += c == StairsShape.STRAIGHT ? w : outer(c) ? w * w : 2 * w - w * w;
        }
        return sum / n;
    }

    private static boolean knock(ServerPlayer p, ServerLevel level, BlockPos pos, Direction face, double x, double y, double z) {
        return Shaping.knock(p, level, pos, face, new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z));
    }

    private static Shape shape(ServerLevel level, BlockPos pos) {
        RealShapes.Parts p = RealShapes.read(level, pos, level.getBlockState(pos));
        return p == null ? null : p.shape();
    }

    /** Knocking the same place steps through the cuts and back, a whole block's first cut being a real stair where there is one. */
    static void knockSteps(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(knock(p, level, pos, Direction.SOUTH, 0.5, 0.9, 1.0), "the first knock lands");
        BlockState first = level.getBlockState(pos);
        h.assertTrue(first.is(Blocks.STONE_STAIRS) && first.getValue(StairBlock.FACING) == Direction.NORTH, "a knock at the top of a whole stone makes a real stair");
        knock(p, level, pos, Direction.SOUTH, 0.5, 0.75, 0.5);
        h.assertTrue(shape(level, pos) == Shape.BEVEL, "a second knock on the cut bevels it, got " + shape(level, pos));
        knock(p, level, pos, Direction.SOUTH, 0.5, 0.75, 0.75);
        h.assertTrue(shape(level, pos) == Shape.ROUND, "a third rounds it, got " + shape(level, pos));
        knock(p, level, pos, Direction.SOUTH, 0.5, 0.75, 0.85);
        h.assertTrue(shape(level, pos) == Shape.FULL, "a fourth makes it whole, got " + shape(level, pos));

        BlockPos top = pos.above(2);
        level.setBlock(top, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        knock(p, level, top, Direction.UP, 0.5, 1.0, 0.5);
        BlockState slab = level.getBlockState(top);
        h.assertTrue(slab.is(Blocks.STONE_SLAB) && slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM, "the middle of the top makes a real slab");
        h.succeed();
    }

    /** A block with no slab or stair of its own takes every cut, and keeps its own texture and drop. */
    static void customBlock(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        Block ashlar = BuiltInRegistries.BLOCK.getValue(Awaken.id("ashlar"));
        h.assertTrue(ashlar != Blocks.AIR, "the test block exists");
        level.setBlock(pos, ashlar.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(knock(p, level, pos, Direction.SOUTH, 0.5, 0.9, 1.0), "the knock lands");
        h.assertTrue(level.getBlockState(pos).is(ShapingContent.SHAPED.get()) && shape(level, pos) == Shape.NOTCH, "it's a notch of itself");
        RealShapes.Parts parts = RealShapes.read(level, pos, level.getBlockState(pos));
        h.assertTrue(parts.base().is(ashlar) && parts.look().is(ashlar), "it's still ashlar underneath and on top");
        h.succeed();
    }

    /** Cutting and reshaping change nothing about what a block is worth, and ore never takes a knock. */
    static void noGain(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        knock(p, level, pos, Direction.UP, 0.5, 0.5, 0.5);
        h.assertTrue(level.getBlockState(pos).is(ShapingContent.SHAPED.get()) && shape(level, pos) == Shape.FULL, "a slab knocked whole is a whole-looking cut block");
        List<ItemStack> drops = Block.getDrops(level.getBlockState(pos), level, pos, level.getBlockEntity(pos));
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.STONE_SLAB) && drops.getFirst().getCount() == 1, "and it still drops one slab: " + drops);

        BlockPos ore = pos.above(2);
        level.setBlock(ore, Blocks.DIAMOND_ORE.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(!knock(p, level, ore, Direction.SOUTH, 0.5, 0.9, 1.0) && level.getBlockState(ore).is(Blocks.DIAMOND_ORE), "ore won't take a knock");
        h.succeed();
    }

    /** Within its own family a whole block becomes the real thing; across families the look is only worn. Back to its own look, it's real again. */
    static void looksRealOrWorn(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(ShapingContent.MALLET.get()));
        BlockPos a = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(a, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
        p.teleportTo(a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5);
        Shaping.onSetLook(p, new ShapingPayloads.SetLook(a, BuiltInRegistries.BLOCK.getKey(Blocks.STONE_BRICKS), false));
        h.assertTrue(level.getBlockState(a).is(Blocks.STONE_BRICKS), "cobble to stone bricks is the real block");
        Shaping.onSetLook(p, new ShapingPayloads.SetLook(a, BuiltInRegistries.BLOCK.getKey(Blocks.OAK_PLANKS), false));
        RealShapes.Parts worn = RealShapes.read(level, a, level.getBlockState(a));
        h.assertTrue(worn.base().is(Blocks.STONE_BRICKS) && worn.look().is(Blocks.OAK_PLANKS), "stone bricks wearing oak are still stone bricks");
        Shaping.onSetLook(p, new ShapingPayloads.SetLook(a, BuiltInRegistries.BLOCK.getKey(Blocks.STONE_BRICKS), false));
        h.assertTrue(level.getBlockState(a).is(Blocks.STONE_BRICKS), "taking its own look back makes it the real block again");
        h.succeed();
    }

    /** A run goes through touching blocks of the same look, stops at anything else, and stops at the cap. */
    static void runSameKind(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 4, 1));
        for (int i = 0; i < 5; i++) level.setBlock(o.east(i), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.east(5), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.east(6), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        int all = Looks.run(level, o, 64).stream().mapToInt(List::size).sum();
        h.assertTrue(all == 5, "five touching stones and no further, got " + all);
        int capped = Looks.run(level, o, 3).stream().mapToInt(List::size).sum();
        h.assertTrue(capped == 3, "the cap holds, got " + capped);
        h.succeed();
    }

    /** Someone who may not build can't cut. */
    static void keptBlocks(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(ShapingRules.mayChange(p, level, pos), "survival can build here");
        p.setGameMode(GameType.ADVENTURE);
        h.assertTrue(!ShapingRules.mayChange(p, level, pos), "adventure can't");
        h.succeed();
    }

    /** Undo puts back what you did, and leaves alone anything changed since. */
    static void undo(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, Blocks.DIORITE.defaultBlockState(), Block.UPDATE_ALL);
        knock(p, level, pos, Direction.SOUTH, 0.5, 0.9, 1.0);
        h.assertTrue(!level.getBlockState(pos).is(Blocks.DIORITE), "knocked");
        h.assertTrue(Shaping.undo(p) == 1 && level.getBlockState(pos).is(Blocks.DIORITE), "undo puts it back");
        knock(p, level, pos, Direction.SOUTH, 0.5, 0.9, 1.0);
        level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        h.assertTrue(Shaping.undo(p) == 0 && level.getBlockState(pos).is(Blocks.GOLD_BLOCK), "a block changed since is left alone");
        h.succeed();
    }

    /** Every look on offer can be worn, and each is listed once. */
    static void looksList(GameTestHelper h) {
        Set<Block> seen = new HashSet<>();
        for (Looks.Family f : Looks.families()) {
            for (Block b : f.members()) {
                h.assertTrue(seen.add(b), BuiltInRegistries.BLOCK.getKey(b) + " is listed twice");
                h.assertTrue(Looks.offerable(b.defaultBlockState()), BuiltInRegistries.BLOCK.getKey(b) + " can't be worn");
            }
        }
        h.succeed();
    }

    // ---- corners ----------------------------------------------------------------------------------------------------------

    /** An L-shaped deck, 5 by 5 with its south-east quarter gone: one inner corner at (1,1) and outer corners at (4,1) and (1,4). */
    private static final int[][] SOUTH_EDGE = {{2, 1}, {3, 1}, {4, 1}, {1, 1}, {0, 4}}, EAST_EDGE = {{1, 2}, {1, 3}, {1, 4}, {4, 0}};

    private static void deck(ServerLevel level, BlockPos o, Block corners, Block rest) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x >= 2 && z >= 2) continue;
                boolean corner = (x == 4 && z == 1) || (x == 1 && z == 1) || (x == 1 && z == 4);
                level.setBlock(o.offset(x, 0, z), (corner ? corners : rest).defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Knocks the deck's south edge, then its east edge: the second run has to turn the first run's ends into corners. */
    private static void knockDeck(GameTestHelper h, ServerPlayer p, ServerLevel level, BlockPos o) {
        for (int[] c : SOUTH_EDGE) knock(p, level, o.offset(c[0], 0, c[1]), Direction.SOUTH, 0.5, 0.9, 1.0);
        h.assertTrue(Corners.of(level.getBlockState(o.offset(4, 0, 1))) == StairsShape.STRAIGHT, "one run alone is straight to its end");
        for (int[] c : EAST_EDGE) knock(p, level, o.offset(c[0], 0, c[1]), Direction.EAST, 1.0, 0.9, 0.5);
    }

    /** The fronts a block's cut opens, and whether it's an inner corner: a corner by what it is, whatever it calls left or right. */
    private static void assertCorner(GameTestHelper h, ServerLevel level, BlockPos pos, StairsShape kind, Direction... fronts) {
        BlockState state = level.getBlockState(pos);
        RealShapes.Parts p = RealShapes.read(level, pos, state);
        h.assertTrue(p != null && p.shape() == Shape.NOTCH, "a notch at " + pos + ", got " + state);
        StairsShape c = Corners.of(state);
        Set<Direction> got = EnumSet.of(p.facing());
        Direction g = second(p.facing(), c);
        if (g != null) got.add(g);
        boolean sameKind = kind == StairsShape.STRAIGHT ? c == StairsShape.STRAIGHT : outer(kind) == outer(c) && c != StairsShape.STRAIGHT;
        h.assertTrue(sameKind && got.equals(EnumSet.of(fronts[0], fronts)), pos + " should be " + kind + " opening " + List.of(fronts) + ", got " + state);
    }

    /**
     * An L of knocked edges forms an outer corner at each outside turn and an inner corner inside the L, as stairs do, and
     * taking a neighbour away turns the corner back straight. Ashlar has no stairs of its own, so these are all cut blocks.
     */
    static void cornersJoin(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        Block ashlar = BuiltInRegistries.BLOCK.getValue(Awaken.id("ashlar"));
        BlockPos o = h.absolutePos(new BlockPos(0, 2, 0));
        deck(level, o, ashlar, ashlar);
        knockDeck(h, p, level, o);
        h.assertTrue(level.getBlockState(o.offset(4, 0, 1)).is(ShapingContent.SHAPED.get()), "ashlar takes cut blocks");
        assertCorner(h, level, o.offset(4, 0, 1), StairsShape.OUTER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, o.offset(1, 0, 4), StairsShape.OUTER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, o.offset(1, 0, 1), StairsShape.INNER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, o.offset(3, 0, 1), StairsShape.STRAIGHT, Direction.SOUTH);
        assertCorner(h, level, o.offset(1, 0, 3), StairsShape.STRAIGHT, Direction.EAST);
        // the corner is the block's own shape: walking into the outer corner from the east, the cut quarter is open
        h.assertTrue(!Shapes.joinIsNotEmpty(level.getBlockState(o.offset(4, 0, 1)).getCollisionShape(level, o.offset(4, 0, 1)),
                Shapes.box(0.55, 0.55, 0.55, 0.95, 0.95, 0.95), BooleanOp.AND), "the outer corner's collision is cut on both fronts");
        // a different cut alongside doesn't join: bevel the east edge's end and the outer corner goes straight
        knock(p, level, o.offset(4, 0, 0), Direction.EAST, 1.0, 0.9, 0.5);
        assertCorner(h, level, o.offset(4, 0, 1), StairsShape.STRAIGHT, Direction.SOUTH);
        // taking the neighbour away straightens it the same way
        level.setBlock(o.offset(1, 0, 2), ashlar.defaultBlockState(), Block.UPDATE_ALL);
        assertCorner(h, level, o.offset(1, 0, 1), StairsShape.STRAIGHT, Direction.SOUTH);
        level.setBlock(o.offset(0, 0, 4), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertCorner(h, level, o.offset(1, 0, 4), StairsShape.STRAIGHT, Direction.EAST);
        h.succeed();
    }

    /**
     * Cuts and real stairs join both ways. A real stair whose partner is a cut can't see it, so it stands as a cut of
     * itself (dropping the stair), and is the real stair again once it no longer needs the cut: at once on an undo, and a
     * tick after a neighbour is changed by other means. A deck of a block with stairs is all real stairs, cornered by
     * vanilla's own rule.
     */
    static void cornersStairs(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ServerLevel level = h.getLevel();
        Block ashlar = BuiltInRegistries.BLOCK.getValue(Awaken.id("ashlar"));
        // all oak: every notch is a real oak stair, and vanilla turns the corners
        BlockPos oak = h.absolutePos(new BlockPos(0, 2, 0));
        deck(level, oak, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS);
        knockDeck(h, p, level, oak);
        h.assertTrue(level.getBlockState(oak.offset(4, 0, 1)).is(Blocks.OAK_STAIRS) && level.getBlockState(oak.offset(1, 0, 1)).is(Blocks.OAK_STAIRS),
                "an oak deck's corners are real oak stairs");
        assertCorner(h, level, oak.offset(4, 0, 1), StairsShape.OUTER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, oak.offset(1, 0, 1), StairsShape.INNER_LEFT, Direction.SOUTH, Direction.EAST);

        // ashlar cuts at the corners, real stone brick stairs along the runs: the cuts see the stairs
        BlockPos cut = h.absolutePos(new BlockPos(0, 4, 0));
        deck(level, cut, ashlar, Blocks.STONE_BRICKS);
        knockDeck(h, p, level, cut);
        h.assertTrue(level.getBlockState(cut.offset(3, 0, 1)).is(Blocks.STONE_BRICK_STAIRS), "the runs are real stairs");
        assertCorner(h, level, cut.offset(4, 0, 1), StairsShape.OUTER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, cut.offset(1, 0, 1), StairsShape.INNER_LEFT, Direction.SOUTH, Direction.EAST);

        // stone brick corners, ashlar runs: each corner's stair stands as a cut of itself to turn the corner
        BlockPos real = h.absolutePos(new BlockPos(0, 6, 0));
        deck(level, real, Blocks.STONE_BRICKS, ashlar);
        knockDeck(h, p, level, real);
        BlockPos outer = real.offset(4, 0, 1), inner = real.offset(1, 0, 1);
        h.assertTrue(level.getBlockState(outer).is(ShapingContent.SHAPED.get()), "a stair turning a corner with a cut is a cut of itself");
        assertCorner(h, level, outer, StairsShape.OUTER_LEFT, Direction.SOUTH, Direction.EAST);
        assertCorner(h, level, inner, StairsShape.INNER_LEFT, Direction.SOUTH, Direction.EAST);
        List<ItemStack> drops = Block.getDrops(level.getBlockState(outer), level, outer, level.getBlockEntity(outer));
        h.assertTrue(drops.size() == 1 && drops.getFirst().is(Items.STONE_BRICK_STAIRS), "and it still drops the stair: " + drops);
        // undoing the last knock (the east edge's end) leaves the outer corner straight, and a real stair again at once
        h.assertTrue(Shaping.undo(p) == 1, "the last knock undoes");
        h.assertTrue(level.getBlockState(outer).is(Blocks.STONE_BRICK_STAIRS) && Corners.of(level.getBlockState(outer)) == StairsShape.STRAIGHT,
                "with its partner gone the corner is a straight real stair again, got " + level.getBlockState(outer));
        // a neighbour changed by other means: the inner corner straightens, and is the real stair a tick later
        level.setBlock(real.offset(1, 0, 2), ashlar.defaultBlockState(), Block.UPDATE_ALL);
        assertCorner(h, level, inner, StairsShape.STRAIGHT, Direction.SOUTH);
        h.succeedWhen(() -> h.assertTrue(level.getBlockState(inner).is(Blocks.STONE_BRICK_STAIRS),
                "the inner corner should be a real stair again, got " + level.getBlockState(inner)));
    }
}
