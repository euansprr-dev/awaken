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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

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
    }

    /** Every cut at every facing is a closed solid of the right size, faces outward, and has collision. */
    static void meshIsSolid(GameTestHelper h) {
        for (Shape s : Shape.values()) {
            for (Direction f : Direction.Plane.HORIZONTAL) {
                double sx = 0, sy = 0, sz = 0, volume = 0;
                for (Shape.Face q : s.faces(f)) {
                    double ax = 0, ay = 0, az = 0;
                    for (int i = 1; i <= 2; i++) {
                        double ux = q.x(i) - q.x(0), uy = q.y(i) - q.y(0), uz = q.z(i) - q.z(0);
                        double vx = q.x(i + 1) - q.x(0), vy = q.y(i + 1) - q.y(0), vz = q.z(i + 1) - q.z(0);
                        ax += (uy * vz - uz * vy) / 2;
                        ay += (uz * vx - ux * vz) / 2;
                        az += (ux * vy - uy * vx) / 2;
                    }
                    h.assertTrue(ax * q.nx() + ay * q.ny() + az * q.nz() > 0, s + " " + f + ": a face points inward");
                    sx += ax;
                    sy += ay;
                    sz += az;
                    volume += (q.x(0) * ax + q.y(0) * ay + q.z(0) * az) / 3;
                }
                h.assertTrue(Math.abs(sx) + Math.abs(sy) + Math.abs(sz) < 1.0E-3, s + " " + f + " is not closed");
                h.assertTrue(volume > 0.4 && volume < 1.001, s + " " + f + " has volume " + volume);
                h.assertTrue(!s.voxel(f).isEmpty(), s + " " + f + " has no collision");
            }
        }
        h.succeed();
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
}
