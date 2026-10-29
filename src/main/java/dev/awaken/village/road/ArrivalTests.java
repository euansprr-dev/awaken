package dev.awaken.village.road;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code village_far_arrival} ({@link Arrivals}): a player standing on a block here, sent to another level far out, ticks
 * there without that level being asked for a chunk at the spot it left, and back here without this level being asked for
 * one under the far spot: each level is read only where the player is. The GameTest server has no gate or trial levels,
 * so the Nether stands in for them (the same teleport and the same first tick).
 */
final class ArrivalTests {
    private ArrivalTests() {}

    static void register() {
        AwakenTests.register("village_far_arrival", 60, ArrivalTests::farArrival);
    }

    private static void farArrival(GameTestHelper h) {
        ServerLevel here = h.getLevel();
        ServerLevel far = here.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(far != null, "the Nether exists");
        h.setBlock(1, 1, 1, Blocks.STONE);
        AwakenTests.Live live = null;
        try {
            live = AwakenTests.survivalPlayer(h, "Arriver", new BlockPos(1, 2, 1));
            ServerPlayer p = live.player();
            for (int i = 0; i < 4; i++) p.doTick();
            BlockPos stood = h.absolutePos(new BlockPos(1, 1, 1));
            h.assertTrue(p.mainSupportingBlockPos.isPresent() && p.mainSupportingBlockPos.get().equals(stood), "the player stands on the stone: " + p.mainSupportingBlockPos);
            ChunkPos left = ChunkPos.containing(stood);
            h.assertTrue(far.getChunkSource().getChunkNow(left.x(), left.z()) == null, "the Nether holds nothing at " + left + " yet");
            // a spot far out there (as a gate's or a trial's cell is), floored so the player stands on it
            BlockPos cell = new BlockPos(16000 + 8, 120, 8);
            far.getChunk(cell.getX() >> 4, cell.getZ() >> 4);
            far.setBlockAndUpdate(cell.below(), Blocks.STONE.defaultBlockState());
            far.setBlockAndUpdate(cell, Blocks.AIR.defaultBlockState());
            far.setBlockAndUpdate(cell.above(), Blocks.AIR.defaultBlockState());
            p.teleportTo(far, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, java.util.Set.<Relative>of(), 0, 0, true);
            h.assertTrue(p.level() == far, "the player is in the Nether");
            for (int i = 0; i < 4; i++) p.doTick();
            h.assertTrue(far.getChunkSource().getChunkNow(left.x(), left.z()) == null,
                    "the Nether was never asked for chunk " + left + " where the player stood before");
            h.assertTrue(p.mainSupportingBlockPos.isPresent(), "the player stands on the far stone");
            ChunkPos there = ChunkPos.containing(cell);
            h.assertTrue(here.getChunkSource().getChunkNow(there.x(), there.z()) == null, "this level holds nothing at the far spot's chunk " + there + " yet");
            BlockPos back = h.absolutePos(new BlockPos(1, 2, 1));
            p.teleportTo(here, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, java.util.Set.<Relative>of(), 0, 0, true);
            for (int i = 0; i < 4; i++) p.doTick();
            h.assertTrue(here.getChunkSource().getChunkNow(there.x(), there.z()) == null,
                    "back here, this level was never asked for chunk " + there + " under the far spot the player left");
        } finally {
            if (live != null) live.leave();
        }
        h.succeed();
    }
}
