package dev.awaken.tasks.client;

import dev.awaken.core.api.Goals.Goal;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import dev.awaken.tasks.net.TaskPayloads;
import dev.awaken.tasks.net.TaskPayloads.NeedRow;
import dev.awaken.tasks.net.TaskPayloads.StepRow;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * K, Tasks (SPEC §27.13). Now: the one step to do next with everything it needs and where each comes from, then one
 * line per goal for what else is open, what's out there, and the upgrades you can take when you want. A goal's tab: every
 * step of that goal in order, each need with its line of where-from. Pin puts a step on the HUD card.
 */
final class TaskPage extends UiSection {
    @Override
    public String title() {
        return "";
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public StatusSections.@Nullable Shelf shelf() {
        return null;
    }

    @Override
    public boolean visible() {
        return !TaskCache.view.steps().isEmpty();
    }

    @Override
    protected void build(Ui ui) {
        if (ui.drawing()) TaskCache.drawn();
        TaskPayloads.View v = TaskCache.view;
        Goal g = TaskCache.goal();
        if (g == null) now(ui, v);
        else goal(ui, v, g);
    }

    // ---- Now -------------------------------------------------------------------------------------------------------

    private static void now(Ui ui, TaskPayloads.View v) {
        StepRow top = TaskCache.step(v.top());
        if (top != null) {
            heading(ui, "DO THIS NEXT");
            step(ui, v, top, true);
            ui.gap(6);
        }
        List<StepRow> also = new ArrayList<>(), out = new ArrayList<>(), later = new ArrayList<>();
        for (Goal g : TaskCache.TABS) {
            for (StepRow s : v.steps()) {
                if (s == top || s.goal() != g.ordinal() || s.optional()) continue;
                also.add(s); // the first open step of each goal: the rest wait on its tab
                break;
            }
        }
        for (StepRow s : v.steps()) {
            if (s == top) continue;
            if (s.optional()) later.add(s);
            else if (TaskCache.out(s)) out.add(s);
        }
        if (!also.isEmpty()) {
            heading(ui, "ALSO OPEN TO YOU");
            for (StepRow s : also) brief(ui, v, s, true);
            ui.gap(4);
        }
        if (!out.isEmpty()) {
            heading(ui, "OUT THERE");
            for (StepRow s : out) brief(ui, v, s, false);
            ui.gap(4);
        }
        if (!later.isEmpty()) {
            heading(ui, "WHEN YOU WANT");
            for (StepRow s : later) brief(ui, v, s, true);
        }
    }

    private static void heading(Ui ui, String text) {
        ui.text(text, Glass.a(Glass.LABEL, 0.9f));
        ui.gap(1);
    }

    /** One line for a step on Now: its goal, its words, how far along it is, and a way to its tab. */
    private static void brief(Ui ui, TaskPayloads.View v, StepRow s, boolean open) {
        if (s.fresh() && ui.drawing()) TaskCache.drawnFresh(s.key());
        boolean pinned = s.key().equals(v.pinned());
        ui.card(pinned ? Glass.GOOD : s.optional() ? Glass.LABEL : Glass.EDGE, b -> {
            Ui.Flow tags = b.flow();
            tags.chip(goalName(s).toUpperCase(java.util.Locale.ROOT), Glass.LABEL);
            if (pinned) tags.chip("PINNED", Glass.GOOD);
            if (s.fresh()) tags.chip("NEW", Glass.AMBER);
            tags.end();
            b.text(s.title(), s.optional() ? Glass.TEXT : Glass.TITLE);
            if (!s.why().isEmpty()) b.textAtMost(s.why(), Glass.a(Glass.LABEL, 0.9f), 2);
            Ui.Flow f = b.flow();
            int ready = ready(s.needs());
            if (!s.needs().isEmpty()) f.text(ready + " of " + s.needs().size() + " ready", ready == s.needs().size() ? Glass.GOOD : Glass.LABEL);
            if (open && !TaskCache.out(s) && f.button("Open ›", Glass.EDGE, true)) TaskTabs.pick(tabOf(s));
            pin(f, v, s);
            f.end();
        });
        ui.gap(3);
    }

    // ---- a goal's tab ----------------------------------------------------------------------------------------------

    private static void goal(Ui ui, TaskPayloads.View v, Goal g) {
        List<StepRow> rows = new ArrayList<>(), later = new ArrayList<>();
        for (StepRow s : v.steps()) if (s.goal() == g.ordinal()) (s.optional() ? later : rows).add(s);
        rows.addAll(later);
        if (rows.isEmpty()) {
            ui.text(switch (g) {
                case HEARTH -> "Nothing to build at home right now. Your home's next steps show here as it grows.";
                case ARMOUR -> "Nothing to make right now. Your next armour shows here when your rank opens it.";
                case WEAPONS -> "Nothing to make right now. Weapons you can make show here.";
                case BELLS -> "No bell to find right now. When the Bell Book hears one, it shows here.";
                default -> "Nothing open here right now.";
            }, Glass.LABEL);
            return;
        }
        for (StepRow s : rows) {
            step(ui, v, s, false);
            ui.gap(4);
        }
    }

    // ---- one step in full ------------------------------------------------------------------------------------------

    static void step(Ui ui, TaskPayloads.View v, StepRow s, boolean lead) {
        if (s.fresh() && ui.drawing()) TaskCache.drawnFresh(s.key());
        boolean pinned = s.key().equals(v.pinned()), top = s.key().equals(v.top());
        int accent = pinned ? Glass.GOOD : top ? Glass.AMBER : s.optional() ? Glass.LABEL : Glass.EDGE;
        ui.card(accent, b -> {
            Ui.Flow tags = b.flow();
            if (lead) tags.chip(goalName(s).toUpperCase(java.util.Locale.ROOT), Glass.LABEL);
            else if (top) tags.chip("NEXT", Glass.AMBER);
            if (pinned) tags.chip("PINNED", Glass.GOOD);
            if (s.fresh()) tags.chip("NEW", Glass.AMBER);
            if (s.optional()) tags.chip("WHEN YOU WANT", Glass.LABEL);
            tags.end();
            b.text(s.title(), Glass.TITLE);
            if (!s.why().isEmpty()) b.text(s.why(), Glass.a(Glass.LABEL, 0.95f));
            if (!s.needs().isEmpty()) {
                b.gap(2);
                needs(b, s.needs());
            }
            if (!s.choices().isEmpty()) {
                b.gap(2);
                choices(b, s);
            }
            String dir = WhereFrom.direction(s.where().orElse(null));
            if (!dir.isEmpty()) b.text(dir, Glass.a(Glass.EDGE, 0.9f));
            Ui.Flow f = b.flow();
            pin(f, v, s);
            if (s.choices().size() > 1 && f.button("See them worn ›", Glass.EDGE, true)) SetPreviewScreen.open(s.key());
            f.end();
        });
    }

    /** Each need on its line: its icon and name, a count or a tick, and under what's missing, where it comes from. */
    private static void needs(Ui b, List<NeedRow> needs) {
        for (NeedRow n : needs) {
            boolean met = n.met();
            if (n.item().isEmpty()) {
                b.text((met ? "✓ " : "· ") + n.label(), met ? Glass.GOOD : Glass.TEXT);
                continue;
            }
            String count = n.need() > 1 ? "  " + Math.min(n.have(), n.need()) + "/" + n.need() : "";
            Ui.Flow f = b.flow();
            f.item(WhereFrom.icon(n.item()), (met ? "✓ " : "") + WhereFrom.name(n.item(), n.label()) + count, met ? Glass.GOOD : Glass.TEXT);
            f.end();
            if (met) continue;
            String where = WhereFrom.line(n.item());
            if (!where.isEmpty()) b.text(where, Glass.a(Glass.LABEL, 0.8f), Ui.ICON + 3, false);
        }
    }

    /** The sets on offer: each one's pieces, name and role; the one you chose lists what it still takes. */
    private static void choices(Ui b, StepRow s) {
        for (TaskPayloads.ChoiceRow c : s.choices()) {
            boolean chosen = c.id().equals(s.chosen());
            b.card(chosen ? Glass.GOOD : Glass.EDGE, cb -> {
                int ix = cb.x, iy = cb.y();
                for (int i = 0; i < c.show().size(); i++) cb.itemAt(WhereFrom.icon(c.show().get(i)), ix + i * 18, iy, 16);
                cb.gap(18);
                cb.pair(c.title(), c.sub(), chosen ? Glass.GOOD : Glass.TITLE, Glass.LABEL);
                if (chosen) {
                    if (c.needs().isEmpty()) cb.text("You have every piece. Wear them.", Glass.GOOD);
                    else needs(cb, c.needs());
                    return;
                }
                Ui.Flow f = cb.flow();
                int ready = ready(c.needs());
                f.text(c.needs().isEmpty() ? "You have every piece." : ready + " of " + c.needs().size() + " materials ready", Glass.LABEL);
                if (f.button("Choose", Glass.EDGE, true)) choose(s, c);
                f.end();
            });
            b.gap(3);
        }
    }

    static void choose(StepRow s, TaskPayloads.ChoiceRow c) {
        TaskCache.act(TaskPayloads.CHOOSE, s.key() + "|" + c.id());
        StatusSections.cue(StatusSections.Cue.CONFIRM);
    }

    private static void pin(Ui.Flow f, TaskPayloads.View v, StepRow s) {
        if (s.key().equals(v.pinned())) {
            if (f.button("Unpin", Glass.EDGE, true)) {
                TaskCache.act(TaskPayloads.UNPIN, s.key());
                StatusSections.cue(StatusSections.Cue.UNDO);
            }
        } else if (f.button("Pin", Glass.EDGE, true)) {
            TaskCache.act(TaskPayloads.PIN, s.key());
            StatusSections.cue(StatusSections.Cue.CONFIRM);
        }
    }

    private static int ready(List<NeedRow> needs) {
        int n = 0;
        for (NeedRow r : needs) if (r.met()) n++;
        return n;
    }

    private static String goalName(StepRow s) {
        Goal[] all = Goal.values();
        return s.goal() >= 0 && s.goal() < all.length ? (all[s.goal()] == Goal.RANK ? "Gates" : all[s.goal()].label) : "";
    }

    private static int tabOf(StepRow s) {
        for (int i = 0; i < TaskCache.TABS.length; i++) if (TaskCache.TABS[i].ordinal() == s.goal()) return i + 1;
        return 0;
    }
}
