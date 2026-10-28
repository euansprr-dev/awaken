package dev.awaken.turbo.packetcap;

import dev.awaken.Awaken;
import net.minecraft.SharedConstants;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.handling.QueuedPacket;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Queue;

/**
 * The packet cap (SPEC §28.9, Cost; Turbo): 26.3 handles every queued packet before each tick
 * ({@code PacketProcessor.processQueuedPackets}, {@code while (!queue.isEmpty())}), and a move packet into land not
 * generated waits on the server thread for its chunk, so a client outrunning world generation kept the one tick going
 * for minutes: no mobs, no time, no tasks (runs 40715105903 and 40716396298). With this on, the server's packets get
 * {@link #BUDGET_NS} of each tick (at least one packet a tick), and the rest wait for the next tick, in order. The edge
 * hold keeps our own clients from outrunning the land; this is the backstop for anything else.
 *
 * <p>It also closes the quit race that such a tick opened: the client's {@code IntegratedServer.halt} queues a task for the
 * server thread and joins it, but after a tick that overran, the server only runs a fresh task once it is three ticks old
 * or it has time ({@code MinecraftServer.shouldRun}), so a server stopping on the host's disconnect left it queued and the
 * client waited forever. At the start of {@code stopServer} (every level still open) the tasks still queued run, all of
 * them, for at most {@link #STOP_DRAIN_NS}; an integrated server runs any that came during the save once more at its end.
 *
 * <p>A Turbo feature: common mixins at the head of {@code PacketProcessor.processQueuedPackets} (the server's processor
 * only; the client's is never capped) and at the head and tail of {@code MinecraftServer.stopServer}
 * (dev.awaken.turbo.packetcap.mixin, its own config, not required). On only for the game and NeoForge versions it was
 * read against, once its probe has seen it run, and with the server setting {@code packetCap} on
 * ({@code config/awaken-packetcap-server.toml}); otherwise, and from the first thing that goes wrong, vanilla's.
 */
public final class PacketCap {
    private PacketCap() {}

    /** The versions the hook was read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95";
    /** A tick's packets get this long (a tick's time; packets take microseconds in play, so only a flood or a wait meets it). */
    public static final long BUDGET_NS = 50_000_000L;
    /** The stop's drain of queued tasks gives up after this long (a task that queues another forever can't hold the stop). */
    static final long STOP_DRAIN_NS = 2_000_000_000L;

    /** Set the first time the hook runs: the probe. */
    private static volatile boolean applied;
    private static volatile boolean wanted = true, versionOk, failed;
    /** Ticks since the last log line that left packets for the next tick, and the longest handling among them. */
    private static int held;
    private static long longestNs, toldAt;
    /** Ticks that left packets for the next, ever (the test). */
    static long heldEver;

    public static void init(IEventBus modBus) {
        PacketCapConfig.register();
        modBus.addListener(ModConfigEvent.Loading.class, e -> refresh());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> refresh());
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, e -> refresh());
    }

    private static void refresh() {
        try {
            wanted = PacketCapConfig.on();
            versionOk = versionsMatch();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /** Whether the hook ran on this game's packet handling (the mixin applied). */
    public static boolean applied() {
        return applied;
    }

    /** Whether the server's packets are capped now. */
    public static boolean active() {
        return applied && wanted && versionOk && !failed;
    }

    /** One line for the log and the tests. */
    public static String status() {
        return (active() ? "on" : "off") + (applied ? "" : ", the hook never ran") + (wanted ? "" : ", switched off")
                + (versionOk ? "" : ", other versions") + (failed ? ", failed" : "") + "; " + heldEver + " ticks left packets for the next";
    }

    /**
     * The hook (from the mixin, at the head of {@code processQueuedPackets}): true when it handled this tick's packets
     * itself, false to let vanilla's loop run (another processor, or off).
     */
    public static boolean process(PacketProcessor processor, Queue<QueuedPacket> queue, boolean closed) {
        applied = true;
        if (!active() || closed) return false;
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || server.packetProcessor() != processor) return false;
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
        long t0 = System.nanoTime();
        boolean left = drain(queue, BUDGET_NS);
        if (left) tell(System.nanoTime() - t0);
        return true;
    }

    /**
     * Handles queued packets in order, as vanilla's loop does, until {@code budgetNs} is spent (the first always): whether
     * some were left for the next tick. A packet's own failure goes where vanilla's would.
     */
    public static boolean drain(Queue<? extends QueuedPacket> queue, long budgetNs) {
        long until = System.nanoTime() + budgetNs;
        boolean first = true;
        while (!queue.isEmpty()) {
            if (!first && System.nanoTime() - until >= 0) return true;
            queue.poll().handle();
            first = false;
        }
        return false;
    }

    /** At most a line a minute while ticks leave packets for the next (a player's log says the land lagged behind someone). */
    private static void tell(long ns) {
        heldEver++;
        held++;
        longestNs = Math.max(longestNs, ns);
        long now = System.nanoTime();
        if (now - toldAt < 60_000_000_000L) return;
        toldAt = now;
        Awaken.LOG.info("[packetcap] {} ticks left packets for the next tick (the longest handling {} ms)", held, longestNs / 1_000_000L);
        held = 0;
        longestNs = 0;
    }

    /** The server begins to stop (the mixin, head of {@code stopServer}): every task still queued runs, levels still open. */
    public static void stopping(MinecraftServer server) {
        if (active()) runQueued(server, "as the server stops");
    }

    /** The server has stopped (the mixin, tail of {@code stopServer}): an integrated server runs any task that came meanwhile. */
    public static void stopped(MinecraftServer server) {
        if (active() && !server.isDedicatedServer()) runQueued(server, "after the server stopped");
    }

    private static void runQueued(MinecraftServer server, String when) {
        try {
            int queued = server.getPendingTasksCount();
            if (queued == 0) return;
            long until = System.nanoTime() + STOP_DRAIN_NS;
            // inside managedBlock every queued task runs, however fresh (MinecraftServer.shouldRun asks no more)
            server.managedBlock(() -> server.getPendingTasksCount() == 0 || System.nanoTime() - until >= 0);
            Awaken.LOG.info("[packetcap] ran {} tasks still queued {}{}", queued, when,
                    server.getPendingTasksCount() == 0 ? "" : " (" + server.getPendingTasksCount() + " left at the deadline)");
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        Awaken.LOG.warn("[packetcap] off: the hook failed; the server handles every queued packet before each tick from now on", e);
    }

    private static boolean versionsMatch() {
        String mc = SharedConstants.getCurrentVersion().name();
        String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("-");
        if (MINECRAFT.equals(mc) && NEOFORGE.equals(neo)) return true;
        Awaken.LOG.warn("[packetcap] off: verified on Minecraft {}, NeoForge {}; this is {}, {}", MINECRAFT, NEOFORGE, mc, neo);
        return false;
    }
}
