package dev.awaken.core;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Run something on a later server tick. {@code server.execute(...)} runs immediately when called from the server
 * thread, so it can't be used to step out of an event that is still in progress (a block break, a damage event).
 */
public final class Later {
    private Later() {}

    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> ADDING = new ArrayList<>();
    private static boolean running;

    /** Runs {@code r} after {@code ticks} server ticks (at least 1). */
    public static void run(MinecraftServer server, int ticks, Runnable r) {
        Task t = new Task(server.getTickCount() + Math.max(1, ticks), r);
        if (running) ADDING.add(t); else TASKS.add(t);
    }

    static void tick(ServerTickEvent.Post e) {
        if (TASKS.isEmpty() && ADDING.isEmpty()) return;
        long now = e.getServer().getTickCount();
        running = true;
        try {
            var it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.at() > now) continue;
                it.remove();
                try {
                    t.run().run();
                } catch (RuntimeException ex) {
                    dev.awaken.Awaken.LOG.error("AWAKEN deferred task failed", ex);
                }
            }
        } finally {
            running = false;
            TASKS.addAll(ADDING);
            ADDING.clear();
        }
    }

    static void stopping(ServerStoppingEvent e) {
        TASKS.clear();
        ADDING.clear();
    }
}
