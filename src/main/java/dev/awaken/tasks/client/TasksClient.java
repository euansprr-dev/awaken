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

    private static final TasksPlace PLACE = new TasksPlace();

    public static void init(IEventBus modBus) {
        StatusSections.add(StatusSections.Tab.TASKS, new TaskTabs());
        StatusSections.add(StatusSections.Tab.TASKS, new TaskPage());
        StatusSections.add(StatusSections.Tab.HEARTH, new HearthNext());
        // the band's stage (SPEC §57): Tasks is a place of its own; the two sections above are the old band's
        dev.awaken.core.client.ui.BandPlaces.set(StatusSections.Tab.TASKS.ordinal(), PLACE);
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
        if (a.startsWith("focus ")) { // tours: tasks focus <step key>, on the band's stage
            PLACE.debugFocus(arg.trim().substring(6).trim());
            return;
        }
        if (a.equals("later")) { // tours: open this tab's Later fold
            TaskCache.laterOpen = true;
            PLACE.debugLater();
            return;
        }
        if (a.startsWith("pick ")) { // tours: tasks pick <step key> <choice id>
            String[] p = a.split(" ");
            TaskCache.act(TaskPayloads.CHOOSE, p[1] + "|" + p[2]);
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
