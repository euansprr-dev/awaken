package dev.awaken.lucidium.dream;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.lucidium.world.Cells;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for the Lucidium on a shared server (SPEC "Together"): each player's client-sent actions and dream builds
 * have their own rate limits, and a dream cell comes back for reuse whether its dream ended or its plan was never
 * dreamed, except while a sleeper is about to dream it.
 */
public final class TogetherDreamTests {
    private TogetherDreamTests() {}

    /** A mock player with one remembered place, so the planner has something to dream of. */
    private static ServerPlayer dreamer(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Dreams.dreamer(p).snapshots.add(new Snapshot("missing_file", Snapshot.Kind.MANUAL, 1, "minecraft:overworld", BlockPos.ZERO, "nowhere", "", 0));
        return p;
    }

    private static void leave(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            Sleep.forget(p);
            Planner.invalidate(p);
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    public static void register() {
        // Two players press keys as fast as a client can send: each gets their own allowance, and no more.
        AwakenTests.registerIsolated("together_dream_rate_limits", 40, h -> {
            ServerPlayer a = dreamer(h), b = dreamer(h);
            try {
                int ja = 0, jb = 0;
                for (int i = 0; i < 10; i++) {
                    if (LucidiumNet.journalAction(a, new LucidiumNet.JournalAction("none", ""))) ja++;
                    if (LucidiumNet.journalAction(b, new LucidiumNet.JournalAction("none", ""))) jb++;
                }
                h.assertTrue(ja == 4 && jb == 4, "journal actions: 4 a second each, got " + ja + " and " + jb);

                long ra = Progression.profile(a).counter(Counters.REALITY_CHECKS), rb = Progression.profile(b).counter(Counters.REALITY_CHECKS);
                for (int i = 0; i < 5; i++) {
                    LucidiumNet.realityCheck(a);
                    LucidiumNet.realityCheck(b);
                }
                h.assertTrue(Progression.profile(a).counter(Counters.REALITY_CHECKS) == ra + 1
                        && Progression.profile(b).counter(Counters.REALITY_CHECKS) == rb + 1, "reality checks: one per 10 ticks each");

                // changing one's mind (each change throws the plan away) builds at most 3 dreams per 1000 ticks
                int builds = 0;
                for (int i = 0; i < 5; i++) {
                    Planner.invalidate(a);
                    Planner.request(a, "test");
                    if (Planner.building(a)) builds++;
                }
                h.assertTrue(builds == Planner.BUILDS_PER_WINDOW, "dream builds: " + Planner.BUILDS_PER_WINDOW + " per window, got " + builds);
                Planner.request(b, "test");
                h.assertTrue(Planner.building(b), "the other player's dream still builds: their own allowance");
            } finally {
                leave(h, a, b);
            }
            h.succeed();
        });

        // Two players' plans: a cell comes back when a plan is thrown away, and stays while a sleeper is about to dream it.
        AwakenTests.registerIsolated("together_dream_cells_come_back", 2400, h -> {
            var server = h.getLevel().getServer();
            // cells other tests gave back wait aside, so the order cells come back in is this test's own
            List<Integer> aside = new ArrayList<>();
            while (Cells.freeCount() > 0) aside.add(Cells.allocate(server));
            ServerPlayer a = dreamer(h), b = dreamer(h);
            Runnable cleanup = () -> {
                leave(h, a, b);
                for (int c : aside) Cells.release(server, c);
            };
            int building;
            try {
                building = cellsComeBack(h, server, a, b);
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(Cells.isFree(building), "the cancelled build's cell came back"))
                    .thenExecute(cleanup)
                    .thenSucceed();
        });
    }

    /** The synchronous part: returns the cell of a build that was cancelled while it compiles in the background. */
    private static int cellsComeBack(GameTestHelper h, net.minecraft.server.MinecraftServer server, ServerPlayer a, ServerPlayer b) {
        Planner.Prepared pa = Planner.prepareNow(a), pb = Planner.prepareNow(b); // tonight's plans, as built at dusk
        int ca = pa.blueprint().cell, cb = pb.blueprint().cell;
        h.assertTrue(ca != cb, "each plan has its own cell");

        Planner.invalidate(a); // a changed their mind
        h.assertTrue(Cells.isFree(ca), "a's thrown-away plan gives its cell back");

        Sleep.Session night = new Sleep.Session();
        night.roll = true;
        night.bed = b.blockPosition();
        night.prepared = pb;
        Sleep.lieDown(b, night);
        Planner.morning(server); // the planner lets every unused plan go
        h.assertFalse(Cells.isFree(cb), "but not the plan b is about to dream");
        h.assertTrue(night.ownsPlan, "b's night holds it now");
        Sleep.forget(b); // b gets up without dreaming
        h.assertTrue(Cells.isFree(cb), "then it comes back");

        int issued = Cells.issued();
        int again = Cells.allocate(server);
        h.assertTrue(again == ca && Cells.issued() == issued, "the next dream takes a returned cell, not a new one");
        Cells.release(server, again);

        // a build thrown away while it is still compiling gives its cell back when it completes (in the background)
        Planner.request(a, "test");
        h.assertTrue(Planner.building(a) && !Cells.isFree(cb), "a new build takes b's returned cell");
        Planner.invalidate(a);
        return cb;
    }
}
