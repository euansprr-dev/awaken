package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.village.world.GenTerrain;
import dev.awaken.village.world.Plans;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What waiting on the roads costs (SPEC §28.9, Cost): every lattice chunk whose legs weren't known yet when world
 * generation or the planner ahead of the players asked (how long, on which thread, planned or waited for), the planner's
 * queue, and any wait on the server's own thread, which is a stall and is logged at once. Summed into one log line every
 * 30 s while anything happened, with the roads, plans and noise columns made in that time.
 */
final class RoadTimer {
    private RoadTimer() {}

    /** The server's own thread (set when the roads' world opens). */
    static volatile @Nullable Thread server;
    /** A wait on the server thread longer than this is logged at once. */
    static final long STALL_NS = 50_000_000L;

    private static int cold, waited, onServer;
    private static long coldNs, waitNs, serverNs, worstNs, roads0, plans0, columns0;
    private static String worst = "";

    static boolean onServer() {
        return Thread.currentThread() == server;
    }

    /** A lattice chunk's legs, asked before they were known: planned on this thread, or waited for (another was planning them). */
    static void startsIn(int chunkX, int chunkZ, long ns, boolean planned) {
        String who = Thread.currentThread().getName();
        synchronized (RoadTimer.class) {
            if (planned) {
                cold++;
                coldNs += ns;
            } else {
                waited++;
                waitNs += ns;
            }
            if (ns > worstNs) {
                worstNs = ns;
                worst = chunkX + "," + chunkZ + " on " + who + (planned ? "" : " (waiting)");
            }
        }
        if (onServer()) server(ns, "chunk " + chunkX + "," + chunkZ + "'s roads");
    }

    /** The server thread waited this long for something the roads or settlements had to work out. */
    static void server(long ns, String what) {
        synchronized (RoadTimer.class) {
            onServer++;
            serverNs += ns;
        }
        if (ns >= STALL_NS) RoadNet.log("the server thread waited {} ms for {}", ms(ns), what);
    }

    /** Every 30 s while anything happened: the window's sums. */
    static void tick(net.minecraft.server.MinecraftServer s, int queued, int unknown) {
        if (s.getTickCount() % 600 != 0) return;
        long roads, plans, columns = GenTerrain.COLUMNS.sum();
        String line;
        synchronized (RoadTimer.class) {
            roads = RoadNet.planned;
            plans = Plans.made();
            if (cold + waited + onServer == 0 && roads == roads0 && plans == plans0 && queued == 0) return;
            line = String.format(Locale.ROOT, "timer 30 s: %d chunks planned (%s ms), %d waited for (%s ms), worst %s ms (%s); server thread %d waits (%s ms); "
                            + "planner queue %d, %d lattice chunks unknown ahead; %d roads, %d plans, %d noise columns",
                    cold, ms(coldNs), waited, ms(waitNs), ms(worstNs), worst, onServer, ms(serverNs), queued, unknown, roads - roads0, plans - plans0,
                    columns - columns0);
            cold = waited = onServer = 0;
            coldNs = waitNs = serverNs = worstNs = 0;
            worst = "";
            roads0 = roads;
            plans0 = plans;
            columns0 = columns;
        }
        Awaken.LOG.info("[road] " + line);
    }

    static void reset() {
        synchronized (RoadTimer.class) {
            cold = waited = onServer = 0;
            coldNs = waitNs = serverNs = worstNs = roads0 = plans0 = 0;
            columns0 = GenTerrain.COLUMNS.sum();
            worst = "";
        }
    }

    static String ms(long ns) {
        return String.format(Locale.ROOT, "%.0f", ns / 1e6);
    }
}
