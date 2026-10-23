package dev.awaken.progression.client;

import dev.awaken.progression.Profile;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import dev.awaken.signal.ui.SignalText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The level road on the Status screen: what the last level gave you and what the next few bring, with every step one
 * click away. Steps come from the synced content (`Content.LEVEL_ROAD`), sorted once per reload, not per frame.
 */
final class RoadSection extends UiSection {
    private static final int AHEAD = 4;

    private List<Defs.LevelRoadDef> sorted = List.of();
    private int sortedFrom = -1;
    private boolean all;

    public String title() { return "THE ROAD"; }
    public int order() { return 15; }

    private List<Defs.LevelRoadDef> steps() {
        var map = Content.LEVEL_ROAD.map;
        if (map.size() != sortedFrom) {
            List<Defs.LevelRoadDef> list = new ArrayList<>(map.values());
            list.sort(Comparator.comparingInt(Defs.LevelRoadDef::level).thenComparing(Defs.LevelRoadDef::title));
            sorted = List.copyOf(list);
            sortedFrom = map.size();
        }
        return sorted;
    }

    @Override
    protected void build(Ui ui) {
        List<Defs.LevelRoadDef> steps = steps();
        if (steps.isEmpty()) {
            ui.text("The road appears once you awaken.", Glass.LABEL);
            return;
        }
        Profile p = CoreSections.profile();
        int level = p.level();
        int last = -1, firstAhead = steps.size();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).level() <= level) last = i;
            else if (firstAhead == steps.size()) firstAhead = i;
        }
        int from = all ? 0 : Math.max(0, last);
        int to = all ? steps.size() : Math.min(steps.size(), firstAhead + AHEAD);
        for (int i = from; i < to; i++) {
            Defs.LevelRoadDef s = steps.get(i);
            boolean reached = s.level() <= level;
            boolean next = i == firstAhead;
            int colour = reached ? Glass.GOOD : next ? Glass.AMBER : Glass.TEXT;
            ui.pair((reached ? "✓ " : "") + "Level " + s.level() + " · " + s.title(), next ? "next" : "", colour, Glass.AMBER);
            if (!s.text().isBlank() && (next || all || i == last)) ui.text(SignalText.firstSentence(s.text()), Glass.LABEL, 4, false);
        }
        ui.gap(2);
        Ui.Flow row = ui.flow();
        if (row.button(all ? "Show less" : "Show all " + steps.size(), Glass.LABEL, true)) all = !all;
        row.end();
    }
}
