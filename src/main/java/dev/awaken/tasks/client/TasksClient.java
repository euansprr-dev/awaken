package dev.awaken.tasks.client;

import dev.awaken.progression.client.StatusSections;
import dev.awaken.tasks.net.TaskPayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of Tasks (SPEC §27.13): the page on the band, its tabs, the sets worn, and the badge on the rail. */
public final class TasksClient {
    private TasksClient() {}

    public static void init(IEventBus modBus) {
        StatusSections.add(StatusSections.Tab.TASKS, new TaskTabs());
        StatusSections.add(StatusSections.Tab.TASKS, new TaskPage());
        StatusSections.add(StatusSections.Tab.HEARTH, new HearthNext());
        dev.awaken.core.api.UiHooks.installTasksNew(() -> TaskCache.fresh(null));
        modBus.addListener(TasksClient::onPayloads);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> TaskCache.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            TaskCache.reset();
            WhereFrom.reset();
        });
    }

    /** The autopilot's hook (tools/autopilot/tasks-*.txt): "now", or a goal tab by name ("hearth", "armour"...), or "worn". */
    public static void debug(String arg) {
        String a = arg.trim().toLowerCase(java.util.Locale.ROOT);
        if (a.equals("worn")) {
            for (var s : TaskCache.view.steps()) {
                if (s.choices().size() > 1) {
                    SetPreviewScreen.open(s.key());
                    return;
                }
            }
            return;
        }
        for (int i = 0; i < TaskCache.TABS.length; i++) {
            String name = TaskCache.TABS[i] == dev.awaken.core.api.Goals.Goal.RANK ? "gates" : TaskCache.TABS[i].label.toLowerCase(java.util.Locale.ROOT);
            if (name.equals(a)) {
                TaskCache.pick(i + 1);
                return;
            }
        }
        TaskCache.pick(0);
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(TaskPayloads.View.TYPE, (msg, ctx) -> TaskCache.onView(msg));
    }
}
