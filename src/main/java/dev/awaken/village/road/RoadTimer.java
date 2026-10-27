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
 * 30 s while anything happened, with the roads, plans and noise columns made in that time. On a tour (the autopilot) a
 * watch also samples the server thread 20 times a second: how much of its ticking was spent planning roads or
 * settlements, and what it was doing when a tick, or a task between ticks (a player's packet, a command), ran over a
 * second (to tell a stall of ours from anyone else's).
 */
final class RoadTimer {
    private RoadTimer() {}

    /** The server's own thread (set when the roads' world opens). */
    static volatile @Nullable Thread server;
    /** A wait on the server thread longer than this is logged at once. */
    static final long STALL_NS = 50_000_000L;

    private static int cold, waited, onServer, samples, planning;
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
                            + "planner queue %d, %d lattice chunks unknown ahead; %d roads, %d plans, %d noise columns%s",
                    cold, ms(coldNs), waited, ms(waitNs), ms(worstNs), worst, onServer, ms(serverNs), queued, unknown, roads - roads0, plans - plans0,
                    columns - columns0, samples == 0 ? "" : "; server thread planning in " + planning + " of " + samples + " tick samples");
            cold = waited = onServer = samples = planning = 0;
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
            cold = waited = onServer = samples = planning = 0;
            coldNs = waitNs = serverNs = worstNs = roads0 = plans0 = 0;
            columns0 = GenTerrain.COLUMNS.sum();
            worst = "";
        }
    }

    // ---- the server thread watched (tours) ----------------------------------------------------------------------------

    /** A tick (or a task between ticks) this long is logged with the server thread's stack (once a tick). */
    static final long LONG_TICK_NS = 1_000_000_000L;

    /** On a tour, from the server's start until it stops: the server thread sampled 20 times a second. */
    static void watch(net.minecraft.server.MinecraftServer s) {
        if (System.getProperty("awaken.autopilot") == null) return;
        Thread st = s.getRunningThread();
        Thread t = new Thread(() -> {
            int seen = -1;
            long since = System.nanoTime();
            boolean told = false;
            while (s.isRunning()) {
                java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
                StackTraceElement[] stack = st.getStackTrace();
                boolean ticking = calls(stack, "net.minecraft.server.MinecraftServer", "tickServer");
                synchronized (RoadTimer.class) {
                    if (ticking) samples++;
                    if (ticking && planning(stack)) planning++;
                }
                int tick = s.getTickCount();
                long now = System.nanoTime();
                if (tick != seen) {
                    seen = tick;
                    since = now;
                    told = false;
                } else if (!told && (ticking || calls(stack, "net.minecraft.util.thread.BlockableEventLoop", "doRunTask")) && now - since >= LONG_TICK_NS) {
                    told = true;
                    int gen = 0;
                    for (var e : Thread.getAllStackTraces().entrySet())
                        if (e.getKey() != st && calls(e.getValue(), RoadNet.class.getName(), "startsIn")) gen++;
                    RoadNet.log("tick {} has run {} ms{}: {} (roads being laid for {} other threads){}", tick, ms(now - since),
                            ticking ? "" : " (a task between ticks)", top(stack), gen, planning(stack) ? " (road or settlement planning)" : "");
                }
            }
        }, "Awaken stall watch");
        t.setDaemon(true);
        t.start();
    }

    private static boolean calls(StackTraceElement[] stack, String cls, String method) {
        for (StackTraceElement f : stack) if (f.getMethodName().equals(method) && f.getClassName().equals(cls)) return true;
        return false;
    }

    /** In the roads', the settlements' or the land's planning code. */
    private static boolean planning(StackTraceElement[] stack) {
        for (StackTraceElement f : stack) {
            String c = f.getClassName();
            if (c.startsWith("dev.awaken.village.road.RoadNet") || c.startsWith("dev.awaken.village.road.Highroads")
                    || c.startsWith("dev.awaken.village.road.RoadPlanner") || c.startsWith("dev.awaken.village.plan.")
                    || c.startsWith("dev.awaken.village.world.Plans") || c.startsWith("dev.awaken.village.world.GenTerrain")
                    || c.startsWith("dev.awaken.village.world.SharedLand")) return true;
        }
        return false;
    }

    /** The stack's first frames, our own and the game's, down to the tick. */
    private static String top(StackTraceElement[] stack) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (StackTraceElement f : stack) {
            if (f.getMethodName().equals("tickServer")) break;
            if (n++ == 24) {
                b.append(" < ...");
                break;
            }
            String c = f.getClassName();
            if (n > 1) b.append(" < ");
            b.append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());
        }
        return b.toString();
    }

    static String ms(long ns) {
        return String.format(Locale.ROOT, "%.0f", ns / 1e6);
    }
}
