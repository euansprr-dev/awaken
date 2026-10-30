package dev.awaken.core.test;

import dev.awaken.Awaken;
import dev.awaken.core.MotionTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every Awaken block that stops motion is in 26.3's motion tags, by vanilla's own rule (SPEC §1, "Blocks that stop
 * motion"), and a house of Carillon's city bricks under clay tiles keeps the rain out, in a chunk saved before the
 * tags too.
 */
final class BlocksMotionTests {
    private BlocksMotionTests() {}

    /**
     * Awaken blocks the rule would tag that stay out on purpose, each with its reason. None today: the rule's own
     * kinds (webs, layered drifts, signs) cover every block that should let motion through.
     */
    private static final Set<String> EXCEPT = Set.of();

    static void register() {
        AwakenTests.register("core_blocks_motion", 20, BlocksMotionTests::motion);
    }

    private static void motion(GameTestHelper h) {
        List<String> untagged = new ArrayList<>(), leaves = new ArrayList<>(), vanilla = new ArrayList<>();
        for (var e : BuiltInRegistries.BLOCK.entrySet()) {
            Identifier id = e.getKey().identifier();
            Block b = e.getValue();
            boolean rule = MotionTags.blocksMotion(b);
            if (id.getNamespace().equals("minecraft")) {
                // the rule is vanilla's: it gives vanilla's own list back
                if (rule != b.defaultBlockState().is(BlockTags.BLOCKS_MOTION)) vanilla.add(id.toString());
            } else if (id.getNamespace().equals(Awaken.MODID) && rule && !EXCEPT.contains(id.getPath())) {
                if (b instanceof LeavesBlock) {
                    if (!b.defaultBlockState().is(BlockTags.LEAVES)) leaves.add(id.getPath());
                } else if (!b.defaultBlockState().is(BlockTags.BLOCKS_MOTION_NO_LEAVES)) {
                    untagged.add(id.getPath());
                }
            }
        }
        h.assertTrue(vanilla.isEmpty(), "the rule no longer gives vanilla's #minecraft:blocks_motion back; it differs on " + vanilla);
        h.assertTrue(untagged.isEmpty(), untagged.size() + " Awaken blocks stand solid outside #minecraft:blocks_motion_no_leaves"
                + " (add each to its module's src/main/merge/<module>/data/minecraft/tags/block/blocks_motion_no_leaves.json): " + untagged);
        h.assertTrue(leaves.isEmpty(), "Awaken leaves outside #minecraft:leaves: " + leaves);
        roof(h);
        h.succeed();
    }

    /** A 5 by 5 house, city-brick walls under a clay-tile roof: the motion heightmaps sit on its roof, the rain stays out. */
    private static void roof(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 2, 0));
        Block bricks = BuiltInRegistries.BLOCK.getValue(Awaken.id("city_bricks"));
        Block tiles = BuiltInRegistries.BLOCK.getValue(Awaken.id("clay_tiles"));
        h.assertTrue(bricks != Blocks.AIR && tiles != Blocks.AIR, "Carillon's city bricks and clay tiles are registered");
        for (int dx = 0; dx < 5; dx++) for (int dy = 0; dy < 10; dy++) for (int dz = 0; dz < 5; dz++) {
            boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4;
            Block b = dy == 0 ? Blocks.STONE : dy == 4 ? tiles : dy < 4 && wall ? bricks : Blocks.AIR;
            level.setBlock(o.offset(dx, dy, dz), b.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockPos floor = o.offset(2, 1, 2);
        int roof = o.getY() + 5;
        for (int dx = 1; dx <= 3; dx++) for (int dz = 1; dz <= 3; dz++) {
            BlockPos in = o.offset(dx, 1, dz);
            h.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING, in.getX(), in.getZ()), roof, "rain stops on the clay tiles over " + in);
            h.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, in.getX(), in.getZ()), roof, "the roof without leaves too, over " + in);
        }
        // a chunk saved before the tags: its heightmaps went through the roof to the floor
        LevelChunk chunk = level.getChunkAt(floor);
        int lx = floor.getX() & 15, lz = floor.getZ() & 15;
        for (Heightmap.Types t : new Heightmap.Types[]{Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}) {
            chunk.getOrCreateHeightmapUnprimed(t).update(lx, roof - 1, lz, Blocks.AIR.defaultBlockState());
        }
        h.assertValueEqual(chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz) + 1, floor.getY(), "the old save's heightmap stands on the floor");
        chunk.setData(MotionTags.HEIGHTS_SEEN.get(), 0);
        MotionTags.reprime(chunk);
        h.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING, floor.getX(), floor.getZ()), roof, "re-primed once, rain stops on the roof again");
        h.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, floor.getX(), floor.getZ()), roof, "and the roof without leaves");
        h.assertValueEqual(chunk.getData(MotionTags.HEIGHTS_SEEN.get()), MotionTags.HEIGHTS, "the chunk remembers it re-primed");
    }
}
