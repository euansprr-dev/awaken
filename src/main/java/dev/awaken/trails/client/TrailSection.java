package dev.awaken.trails.client;

import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import dev.awaken.trails.net.TrailPayloads;

import java.util.ArrayList;
import java.util.List;

/**
 * K, Journal, TRAILS (docs/trails/PLAN.md §3.4): what you can do now (the three best next steps, each with Follow), and a
 * browse view inside the same page (areas, every trail you have opened with its steps, silhouettes of the ones that wait on a
 * rank or a level). Nothing here puts anything on the HUD: only Follow does, and only for the one trail.
 */
final class TrailSection extends UiSection {
    private static final String[][] AREAS = {{"home", "Home"}, {"weapons", "Weapons"}, {"gates", "Gates"}, {"powers", "Powers"}, {"body", "Body"},
            {"world", "World"}, {"deep", "Deep"}, {"story", "Story"}};

    static void register() {
        StatusSections.add(StatusSections.Tab.JOURNAL, new TrailSection());
    }

    @Override
    public String title() {
        return "TRAILS";
    }

    @Override
    public int order() {
        return -38; // the Journal page: under what you track, over NOW
    }

    @Override
    public boolean visible() {
        var v = TrailCache.view;
        return v != null && !v.trails().isEmpty();
    }

    @Override
    protected void build(Ui ui) {
        var v = TrailCache.view;
        if (v == null) return;
        if (TrailCache.browse) browse(ui, v);
        else now(ui, v);
    }

    private static void now(Ui ui, TrailPayloads.View v) {
        ui.text("WHAT YOU CAN DO NOW", Glass.LABEL);
        if (v.now().isEmpty()) ui.text("Nothing open to follow right now. New trails open as you play.", Glass.LABEL);
        for (var r : v.now()) {
            ui.card(r.followed() ? Glass.GOOD : Glass.EDGE, body -> {
                body.text(r.title(), Glass.TITLE);
                body.text(r.step(), Glass.TEXT);
                Ui.Flow f = body.flow();
                if (r.followed()) {
                    f.chip("FOLLOWING", Glass.GOOD);
                    if (f.button("Stop", Glass.EDGE, true)) TrailCache.act(TrailPayloads.UNFOLLOW, r.trail(), "");
                } else if (f.button("Follow", Glass.EDGE, true)) {
                    TrailCache.act(TrailPayloads.FOLLOW, r.trail(), "");
                }
                f.end();
            });
        }
        int open = 0, finished = 0, hidden = 0;
        for (var t : v.trails()) {
            if (t.state() == TrailPayloads.OPEN) open++;
            else if (t.state() == TrailPayloads.FINISHED) finished++;
            else if (t.state() == TrailPayloads.HIDDEN) hidden++;
        }
        Ui.Flow f = ui.flow();
        if (f.button("All trails ›", Glass.EDGE, true)) {
            TrailCache.browse = true;
            if (TrailCache.area.isEmpty()) TrailCache.area = firstArea(v);
        }
        f.text(open + " open · " + finished + " finished" + (hidden > 0 ? " · " + hidden + " hidden" : ""), Glass.LABEL);
        f.end();
    }

    private static String firstArea(TrailPayloads.View v) {
        for (var a : AREAS) for (var t : v.trails()) if (t.area().equals(a[0]) && t.state() == TrailPayloads.OPEN) return a[0];
        return AREAS[0][0];
    }

    private static void browse(Ui ui, TrailPayloads.View v) {
        Ui.Flow top = ui.flow();
        if (top.button("‹ Back", Glass.EDGE, true)) TrailCache.browse = false;
        top.end();
        Ui.Flow chips = ui.flow();
        for (var a : AREAS) {
            boolean any = false;
            for (var t : v.trails()) any |= t.area().equals(a[0]);
            if (!any) continue;
            if (chips.button(a[1], a[0].equals(TrailCache.area) ? Glass.GOOD : Glass.EDGE, true)) TrailCache.area = a[0];
        }
        chips.end();
        ui.gap(2);
        List<TrailPayloads.TrailRow> rows = new ArrayList<>();
        for (var t : v.trails()) if (t.area().equals(TrailCache.area)) rows.add(t);
        rows.sort((a, b) -> Integer.compare(rank(a.state()), rank(b.state())));
        if (rows.isEmpty()) ui.text("Nothing here yet.", Glass.LABEL);
        for (var t : rows) trail(ui, t);
    }

    /** Open ones first, then the silhouettes, the finished and the hidden. */
    private static int rank(int state) {
        return switch (state) {
            case TrailPayloads.OPEN -> 0;
            case TrailPayloads.SILHOUETTE -> 1;
            case TrailPayloads.FINISHED -> 2;
            default -> 3;
        };
    }

    private static void trail(Ui ui, TrailPayloads.TrailRow t) {
        switch (t.state()) {
            case TrailPayloads.SILHOUETTE -> ui.card(Glass.LABEL, body -> {
                body.text(t.title(), Glass.LABEL);
                body.text(t.need(), Glass.LABEL);
            });
            case TrailPayloads.FINISHED -> ui.card(Glass.GOOD, body -> {
                body.text(t.title() + "  ✓", Glass.GOOD);
                body.text(t.done() + " of " + t.total() + " steps", Glass.LABEL);
            });
            default -> ui.card(t.followed() ? Glass.GOOD : t.state() == TrailPayloads.HIDDEN ? Glass.LABEL : Glass.EDGE, body -> {
                body.text(t.title() + "   " + t.done() + "/" + t.total(), t.state() == TrailPayloads.HIDDEN ? Glass.LABEL : Glass.TITLE);
                if (!t.blurb().isEmpty()) body.text(t.blurb(), Glass.LABEL);
                boolean open = TrailCache.expanded.contains(t.id());
                if (open) steps(body, t);
                Ui.Flow f = body.flow();
                if (t.state() == TrailPayloads.HIDDEN) {
                    if (f.button("Bring back", Glass.EDGE, true)) TrailCache.act(TrailPayloads.UNHIDE, t.id(), "");
                } else {
                    if (t.followed()) {
                        f.chip("FOLLOWING", Glass.GOOD);
                        if (f.button("Stop", Glass.EDGE, true)) TrailCache.act(TrailPayloads.UNFOLLOW, t.id(), "");
                    } else if (f.button("Follow", Glass.EDGE, true)) {
                        TrailCache.act(TrailPayloads.FOLLOW, t.id(), "");
                    }
                    if (f.button(open ? "Hide steps" : "Steps", Glass.EDGE, true)) {
                        if (!TrailCache.expanded.remove(t.id())) TrailCache.expanded.add(t.id());
                    }
                    if (f.button("Not for me", Glass.EDGE, true)) TrailCache.act(TrailPayloads.HIDE, t.id(), "");
                }
                f.end();
            });
        }
    }

    /** A free trail lists up to five open steps and folds the rest; every step shows ticked, current or waiting. */
    private static void steps(Ui body, TrailPayloads.TrailRow t) {
        int open = 0, folded = 0;
        for (var s : t.steps()) {
            if (s.done()) {
                body.text("✓ " + s.text(), Glass.GOOD);
                continue;
            }
            if (open >= 5) {
                folded++;
                continue;
            }
            open++;
            body.text((s.current() ? "▸ " : "· ") + s.text(), s.current() ? Glass.AMBER : Glass.TEXT);
            if (s.current() && !s.hint().isEmpty()) body.text(s.hint(), Glass.LABEL, 8, false);
            if (s.current() && s.manual()) {
                Ui.Flow f = body.flow();
                if (f.button("I did this", Glass.EDGE, true)) TrailCache.act(TrailPayloads.MARK, t.id(), s.id());
                f.end();
            }
        }
        if (folded > 0) body.text("and " + folded + " more", Glass.LABEL);
    }
}
