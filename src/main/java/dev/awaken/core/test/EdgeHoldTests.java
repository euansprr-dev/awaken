package dev.awaken.core.test;

import dev.awaken.core.EdgeHold;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;
import java.util.function.LongPredicate;

/**
 * The edge hold's math (SPEC §28.9, "The edge hold"): the path's edge found chunk by chunk (straight, diagonal through a
 * corner, negative coordinates, the player's own chunk missing), and a flight at 40 blocks a second toward land not
 * there yet, tick by tick as the client runs it: it eases down (never more than 12% a tick), nears the edge at a crawl
 * and never stops, and speeds up again when the land comes.
 */
final class EdgeHoldTests {
    private EdgeHoldTests() {}

    static void register() {
        AwakenTests.register("core_edge_hold_math", 20, h -> {
            LongPredicate all = k -> true;
            // the land ends at chunk x 3 (block 48)
            LongPredicate east = k -> ChunkPos.getX(k) < 3;
            h.assertTrue(EdgeHold.edge(8, 8, 2, 0, 50, all) == Double.POSITIVE_INFINITY, "every chunk there: no edge");
            h.assertTrue(Math.abs(EdgeHold.edge(8, 8, 2, 0, 50, east) - 40) < 1e-6, "40 blocks to the border of chunk 3: " + EdgeHold.edge(8, 8, 2, 0, 50, east));
            h.assertTrue(EdgeHold.edge(8, 8, 2, 0, 30, east) == Double.POSITIVE_INFINITY, "past the reach it isn't read");
            h.assertTrue(EdgeHold.edge(8, 8, -2, 0, 50, east) == Double.POSITIVE_INFINITY, "flying away from it: no edge");
            double diag = EdgeHold.edge(8, 8, 1, 1, 50, k -> !(ChunkPos.getX(k) == 1 && ChunkPos.getZ(k) == 1));
            h.assertTrue(Math.abs(diag - 8 * Math.sqrt(2)) < 1e-6, "through the corner into chunk 1,1: " + diag);
            h.assertTrue(Math.abs(EdgeHold.edge(-8, 3, -1, 0, 50, k -> ChunkPos.getX(k) > -2) - 8) < 1e-6, "west of the origin, chunk -2 starts at -16");
            h.assertTrue(EdgeHold.edge(50, 8, 1, 0, 50, east) == 0, "the player's own chunk missing: at the edge");
            h.assertTrue(EdgeHold.cap(2, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY) == Double.POSITIVE_INFINITY, "no edge, no cap");
            h.assertTrue(EdgeHold.cap(2, 2, Double.POSITIVE_INFINITY) >= 2 * (1 - EdgeHold.EASE) - 1e-9, "land missing close ahead: an ease, never a wall");

            // a flight east at 2 blocks a tick from x 8; the land beyond 48 comes in after 60 ticks
            double x = 8, last = Double.POSITIVE_INFINITY, speed = 2, slowest = speed;
            boolean[] land = {false};
            LongPredicate has = k -> land[0] || ChunkPos.getX(k) < 3;
            int crossedAt = -1, back = -1;
            String trace = "";
            for (int t = 0; t < 120; t++) {
                if (t == 60) land[0] = true;
                double want = 2; // the mover asks for its full speed every tick
                double cap = EdgeHold.cap(want, EdgeHold.edge(x, 8, want, 0, want * EdgeHold.LOOK_TICKS, has), last);
                last = cap;
                double v = Math.min(want, cap);
                h.assertTrue(v >= speed * (1 - EdgeHold.EASE) - 1e-9, "tick " + t + ": eased, not braked (" + speed + " to " + v + ")");
                h.assertTrue(v >= EdgeHold.FLOOR - 1e-9, "tick " + t + ": never a stop (" + v + ")");
                speed = v;
                slowest = Math.min(slowest, v);
                x += v;
                if (crossedAt < 0 && x > 48) crossedAt = t;
                if (t > 60 && back < 0 && v >= want) back = t;
                if (t % 10 == 0) trace += String.format(Locale.ROOT, " t%d x%.1f v%.2f", t, x, v);
            }
            h.assertTrue(crossedAt < 0 || crossedAt >= 60, "never past the edge before the land came:" + trace);
            h.assertTrue(slowest < 0.5, "it slowed right down near the edge (slowest " + slowest + "):" + trace);
            h.assertTrue(back >= 0 && back <= 62, "full speed again once the land came (tick " + back + "):" + trace);
            h.succeed();
        });
    }
}
