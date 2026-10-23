package dev.awaken.hearth;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Tiny delayed-task queue for set pieces (celebration fireworks, staged Signal lines). Server thread only. */
public final class Later {
    private Later() {}

    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick;

    static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            tick = e.getServer().getTickCount();
            if (TASKS.isEmpty()) return;
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(t -> {
                if (t.at <= tick) {
                    due.add(t);
                    return true;
                }
                return false;
            });
            for (Task t : due) {
                try {
                    t.run.run();
                } catch (RuntimeException ex) {
                    dev.awaken.Awaken.LOG.warn("Hearth task failed", ex);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TASKS.clear());
    }

    public static void run(MinecraftServer server, int delayTicks, Runnable r) {
        TASKS.add(new Task(server.getTickCount() + Math.max(1, delayTicks), r));
    }
}
