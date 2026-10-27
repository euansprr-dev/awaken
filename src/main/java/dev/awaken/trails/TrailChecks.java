package dev.awaken.trails;

import dev.awaken.core.api.TrailsApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a trail must satisfy before it ships (the GameTest {@code trail_defs_valid} runs this over every trail in the data
 * and over a deliberately broken set, so the check cannot pass by being empty): known tokens, a place for every
 * {@code where}, unique step ids, lines short enough for the task card and the Journal, no cycle in {@code after}.
 */
final class TrailChecks {
    private TrailChecks() {}

    static final int TITLE = 28, BLURB = 120, STEP = 76, HINT = 140;

    static List<String> problems(Map<String, TrailDef> defs) {
        List<String> out = new ArrayList<>();
        for (var e : defs.entrySet()) {
            String id = e.getKey();
            TrailDef d = e.getValue();
            if (d.schemaVersion() != 1) out.add(id + ": schemaVersion");
            if (d.title().isBlank() || d.title().length() > TITLE) out.add(id + ": title must be 1 to " + TITLE + " characters");
            if (d.blurb().length() > BLURB) out.add(id + ": blurb over " + BLURB);
            if (!TrailDef.AREAS.contains(d.area())) out.add(id + ": unknown area " + d.area());
            if (!d.order().equals("free") && !d.order().equals("linear")) out.add(id + ": order must be free or linear");
            if (d.steps().isEmpty()) out.add(id + ": no steps");
            for (String t : d.opens()) if (!Conditions.known(t)) out.add(id + ": unknown opens token " + t);
            for (String a : d.after()) if (!defs.containsKey(a)) out.add(id + ": after names a trail that does not exist: " + a);
            if (cycle(defs, id, new HashSet<>())) out.add(id + ": after loops back to itself");
            Set<String> seen = new HashSet<>();
            for (TrailDef.Step s : d.steps()) {
                String at = id + "/" + s.id();
                if (s.id().isBlank() || !seen.add(s.id())) out.add(at + ": step ids must be unique");
                if (s.text().isBlank() || s.text().length() > STEP) out.add(at + ": text must be 1 to " + STEP + " characters");
                if (s.hint().length() > HINT) out.add(at + ": hint over " + HINT);
                if (s.done().isEmpty() && !s.manual()) out.add(at + ": a step with no done tokens must be manual");
                for (String t : s.done()) if (!Conditions.known(t)) out.add(at + ": unknown done token " + t);
                for (String t : s.show()) if (!Conditions.known(t)) out.add(at + ": unknown show token " + t);
                if (!s.where().isEmpty() && !TrailsApi.hasPlace(s.where())) out.add(at + ": no place called " + s.where());
            }
        }
        return out;
    }

    private static boolean cycle(Map<String, TrailDef> defs, String id, Set<String> path) {
        if (!path.add(id)) return true;
        TrailDef d = defs.get(id);
        if (d != null) for (String a : d.after()) if (cycle(defs, a, new HashSet<>(path))) return true;
        return false;
    }
}
