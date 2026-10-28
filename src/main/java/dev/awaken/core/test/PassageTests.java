package dev.awaken.core.test;

import dev.awaken.core.api.Passage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * The passage (SPEC §28.9, "Arrivals"): a teleport through {@link Passage} to land far past anything made never waits
 * on the server thread. The call comes straight back with the player where they were, the land is made off the tick,
 * and the move runs only once it's there, so the teleport itself finds every chunk it touches loaded.
 */
final class PassageTests {
    private PassageTests() {}

    static void register() {
        // the test server ticks back to back, so the land's time is wall-clock: a high tick limit and a deadline of its own
        AwakenTests.register("core_passage_far", 200_000, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            AwakenTests.onEnd(h, () -> level.getServer().getPlayerList().remove(p));
            // far past anything made, and a new spot each run (a rerun shares the world)
            double x = 1_200_000 + (Util.getMillis() / 1000 % 997) * 64 + 8.5, z = -1_150_000 + 8.5;
            h.assertFalse(Passage.ready(level, x, z), "the far land isn't made yet");
            Vec3 start = p.position();
            boolean[] moved = {false}, readyAtMove = {false};
            long[] moveMs = {0};
            long t0 = System.nanoTime();
            Passage.go(p, level, x, z, () -> {
                readyAtMove[0] = Passage.ready(level, x, z);
                long m0 = System.nanoTime();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
                p.teleportTo(level, x, y, z, Set.of(), 0F, 0F, true);
                moveMs[0] = (System.nanoTime() - m0) / 1_000_000;
                moved[0] = true;
            });
            long goMs = (System.nanoTime() - t0) / 1_000_000;
            h.assertFalse(moved[0], "the move waits for its land");
            h.assertTrue(p.position().distanceTo(start) < 1e-6, "the player stays where they were meanwhile");
            h.assertTrue(Passage.waiting(p), "the player is in a passage");
            h.assertTrue(goMs < 250, "the call came straight back (" + goMs + " ms)");
            // the move comes when the land does, or at the passage's own limit (a minute) with the land not made: then this fails
            h.startSequence().thenWaitUntil(() -> h.assertTrue(moved[0], "moved")).thenExecute(() -> {
                h.assertTrue(readyAtMove[0], "the move found its land made (the teleport didn't wait for it), not the passage's time running out");
                h.assertTrue(moveMs[0] < 250, "the teleport itself was quick (" + moveMs[0] + " ms)");
                h.assertTrue(Math.abs(p.getX() - x) < 0.01 && Math.abs(p.getZ() - z) < 0.01, "at the far spot: " + p.position());
                h.assertFalse(Passage.waiting(p), "the passage is over");
            }).thenSucceed();
        });
    }
}
