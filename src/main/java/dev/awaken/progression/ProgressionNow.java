package dev.awaken.progression;

import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.NowHooks.Suggestion;

import java.util.ArrayList;
import java.util.List;

/** Progression's lines for the Now tracker: points to spend, a Reassessment waiting, the next level-road unlock. */
final class ProgressionNow {
    private ProgressionNow() {}

    static void register() {
        NowHooks.register(player -> {
            Profile p = Progression.profile(player);
            if (!p.awakened()) return List.of();
            List<Suggestion> out = new ArrayList<>(3);
            if (p.reassessmentReady() && p.rank().next() != null) {
                out.add(Suggestion.of("progression:reassessment", 72,
                        "Reassessment ready · " + p.rank().next().label + "-rank trial"));
            }
            if (p.unspent() > 0) {
                out.add(Suggestion.of("progression:points", 55,
                        "Spend " + p.unspent() + " point" + (p.unspent() == 1 ? "" : "s") + " · K"));
            }
            LevelRoad.Step next = LevelRoad.next(p);
            if (next != null) {
                out.add(Suggestion.of("progression:road/" + next.id(), 15,
                        "Level " + next.def().level() + ": " + next.def().title()));
            }
            return out;
        });
    }
}
