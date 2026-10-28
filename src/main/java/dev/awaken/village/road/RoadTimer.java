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
 * 30 s while anything happened, with the roads, plans and noise columns made in that time. A watch on the server thread logs
 * what it was doing when a tick, or a task between ticks (a player's packet, a command), ran over two seconds (one second
 * on a tour, where it also samples how much of the ticking went on planning roads or settlements), and any chunk it had
 * to wait for far from every player, with who asked: to tell a stall of ours from anyone else's.
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

    /** When the server's tick under way began (0 between ticks). */
    private static volatile long tickFrom;
    /** A tick that has run this long is late: the planners' jobs ahead of the players wait for it. */
    static final long LATE_NS = 40_000_000L;

    /** The server's tick begins and ends (the planners ahead hold back while one is late). */
    static void tickBegins() {
        tickFrom = System.nanoTime();
    }

    static void tickEnds() {
        tickFrom = 0;
    }

    static boolean serverLate() {
        long t = tickFrom;
        return t != 0 && System.nanoTime() - t > LATE_NS;
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
            serverWaitsEver++;
            serverNs += ns;
        }
        if (ns >= STALL_NS) RoadNet.log("the server thread waited {} ms for {}", ms(ns), what);
    }

    /** Every wait of the server thread's for the roads or settlements since the server started (the tests). */
    private static long serverWaitsEver;

    static synchronized long serverWaits() {
        return serverWaitsEver;
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
        FAR_TOLD.clear();
        tickFrom = 0;
    }

    // ---- the server thread watched -------------------------------------------------------------------------------------

    /** On a tour, a tick (or a task between ticks) this long is logged with the server thread's stack (once a tick). */
    static final long LONG_TICK_NS = 1_000_000_000L;
    /** In play the watch only looks every half second and takes a stack once a tick has run this long. */
    static final long LONG_TICK_PLAY_NS = 2_000_000_000L;

    /**
     * From the server's start until it stops: on a tour the server thread sampled 20 times a second (how much of its
     * ticking went on planning, and the stack of any tick over a second); in play only the tick counter, read twice a
     * second, and one stack when a tick has run two seconds, so a player's log says what held the server.
     */
    static void watch(net.minecraft.server.MinecraftServer s) {
        boolean tour = System.getProperty("awaken.autopilot") != null;
        Thread st = s.getRunningThread();
        long every = tour ? 50_000_000L : 500_000_000L, after = tour ? LONG_TICK_NS : LONG_TICK_PLAY_NS;
        Thread t = new Thread(() -> {
            int seen = -1;
            long since = System.nanoTime();
            boolean told = false;
            while (s.isRunning()) {
                java.util.concurrent.locks.LockSupport.parkNanos(every);
                StackTraceElement[] stack = null;
                if (tour) {
                    stack = st.getStackTrace();
                    boolean ticking = calls(stack, "net.minecraft.server.MinecraftServer", "tickServer");
                    synchronized (RoadTimer.class) {
                        if (ticking) samples++;
                        if (ticking && planning(stack)) planning++;
                    }
                }
                int tick = s.getTickCount();
                long now = System.nanoTime();
                if (tick != seen) {
                    seen = tick;
                    since = now;
                    told = false;
                } else if (!told && now - since >= after && !s.isPaused() && s.getPlayerCount() > 0) {
                    if (stack == null) stack = st.getStackTrace();
                    boolean ticking = calls(stack, "net.minecraft.server.MinecraftServer", "tickServer");
                    if (!ticking && !calls(stack, "net.minecraft.util.thread.BlockableEventLoop", "doRunTask")) continue;
                    told = true;
                    int gen = 0;
                    for (var e : Thread.getAllStackTraces().entrySet())
                        if (e.getKey() != st && calls(e.getValue(), RoadNet.class.getName(), "startsIn")) gen++;
                    RoadNet.log("tick {} has run {} ms{}: {} (roads being laid for {} other threads, {} lattice chunks set aside){}", tick,
                            ms(now - since), ticking ? "" : " (a task between ticks)", top(stack), gen, RoadNet.setAside(),
                            planning(stack) ? " (road or settlement planning)" : "");
                }
            }
        }, "Awaken stall watch");
        t.setDaemon(true);
        t.start();
    }

    /** When each caller of a far chunk was last told (server thread only). */
    private static final java.util.Map<String, Long> FAR_TOLD = new java.util.HashMap<>();
    /** Far chunks the server thread waited for, and the last of them (the tests). */
    static int farLoads;
    static long lastFar = Long.MIN_VALUE;

    /**
     * A chunk the server thread waited for (a {@code getChunk} that had to load it) further than view distance from every
     * player in its level: who asked, once a minute a caller. Such a read waits for whatever that level's world generation
     * is doing, and a lattice chunk still being given its roads holds all of it (SPEC §28.9, Cost), so this is how a
     * freeze names its cause in a player's log. Chunks loaded for a ticket, or near a player, cost nothing to check. While
     * it waits the server thread runs other chunks' last steps too, so a chunk without a getChunk's own ticket ("unknown")
     * is named with the tickets it has: the stack then shows whose wait it rode in, not who asked for it.
     */
    static void loaded(net.neoforged.neoforge.event.level.ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread()) return;
        var c = e.getChunk().getPos();
        int near = Integer.MAX_VALUE;
        for (var p : level.players())
            near = Math.min(near, Math.max(Math.abs(p.chunkPosition().x() - c.x()), Math.abs(p.chunkPosition().z() - c.z())));
        if (near <= level.getServer().getPlayerList().getViewDistance() + 2) return;
        var frames = StackWalker.getInstance().walk(f -> f.limit(120).toList());
        String cache = net.minecraft.server.level.ServerChunkCache.class.getName();
        int k = -1;
        for (int i = 0; i < frames.size(); i++) if (frames.get(i).getClassName().equals(cache) && frames.get(i).getMethodName().equals("getChunk")) k = i;
        if (k < 0) return;
        farLoads++;
        lastFar = c.pack();
        StringBuilder b = new StringBuilder();
        String who = null;
        int n = 0;
        for (int i = k + 1; i < frames.size() && n < 12; i++) {
            var f = frames.get(i);
            String cls = f.getClassName();
            if (n == 0 && (cls.startsWith("net.minecraft.world.level.") || cls.equals(net.minecraft.server.level.ServerLevel.class.getName()))) continue;
            if (f.getMethodName().equals("tickServer")) break;
            if (who == null && !cls.startsWith("net.minecraft.")) who = cls + "." + f.getMethodName();
            if (n++ > 0) b.append(" < ");
            b.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':').append(f.getLineNumber());
        }
        String key = who != null ? who : b.toString();
        long now = level.getServer().getTickCount();
        Long last = FAR_TOLD.get(key);
        if (last != null && now - last < 1200) return;
        FAR_TOLD.put(key, now);
        RoadNet.log("the server thread waited for {} chunk {},{} ({}, {}; {}): {}", level.dimension().identifier(), c.x(), c.z(), e.isNewChunk() ? "new" : "from disk",
                near == Integer.MAX_VALUE ? "no player in that world" : near + " chunks from the nearest player", tickets(level, c), b);
    }

    /** A chunk's own tickets by type ("unknown" is a getChunk's), or that it is held by a neighbour's. */
    private static String tickets(net.minecraft.server.level.ServerLevel level, net.minecraft.world.level.ChunkPos c) {
        var all = level.getChunkSource().getDataStorage().computeIfAbsent(net.minecraft.world.level.TicketStorage.TYPE).getTickets(c.pack());
        if (all.isEmpty()) return "held by a neighbour's ticket";
        StringBuilder t = new StringBuilder("tickets");
        for (var ticket : all) t.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.TICKET_TYPE.getKey(ticket.getType()));
        return t.toString();
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
