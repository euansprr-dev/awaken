package dev.awaken.tasks.client;

import dev.awaken.core.api.Goals.Goal;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import dev.awaken.tasks.net.TaskPayloads;
import org.jspecify.annotations.Nullable;

/**
 * The Hearth page's next step for your home (SPEC §27.13): the Tasks page's first open Hearth step in full, with its
 * pieces and where each comes from, and a way to every step for the home. Under the home's banner, over its rooms.
 */
final class HearthNext extends UiSection {
    @Override
    public String title() {
        return "NEXT FOR YOUR HOME";
    }

    @Override
    public int order() {
        return -250;
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null;
    }

    private static TaskPayloads.@Nullable StepRow next() {
        for (var s : TaskCache.view.steps()) if (s.goal() == Goal.HEARTH.ordinal() && !s.optional()) return s;
        return null;
    }

    @Override
    public boolean visible() {
        return next() != null;
    }

    @Override
    protected void build(Ui ui) {
        TaskPayloads.StepRow s = next();
        if (s == null) return;
        TaskPage.step(ui, TaskCache.view, s, false);
        int more = -1;
        for (var r : TaskCache.view.steps()) if (r.goal() == Goal.HEARTH.ordinal()) more++;
        Ui.Flow f = ui.flow();
        if (f.button(more > 0 ? "Everything for your home (" + more + " more) ›" : "Your home on Tasks ›", Glass.EDGE, true)) {
            TaskCache.pick(1);
            StatusSections.go(StatusSections.Tab.TASKS);
        }
        f.end();
    }
}
