package dev.awaken.powers;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Tiny delayed-task queue on the server thread (chain reactions, delayed second hits). In memory only. */
public final class Scheduler {
    private Scheduler() {}

    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick;

    public static void after(int ticks, Runnable run) {
        TASKS.add(new Task(tick + Math.max(1, ticks), run));
    }

    static void tick(MinecraftServer server) {
        tick++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at() <= tick) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.run().run();
            } catch (RuntimeException e) {
                Casting.errors++;
                dev.awaken.Awaken.LOG.error("Delayed power task failed", e);
            }
        }
    }

    static void clear() {
        TASKS.clear();
    }
}
