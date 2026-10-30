package dev.awaken.trails;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.core.api.TrailsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Trails on the Tasks page (SPEC §27.13): each open trail you haven't finished or hidden gives its next step, under the
 * goal its area belongs to (home to the Hearth, weapons to Weapons, gates, powers and the body to Gates and rank, the rest
 * Out there), as something to do when you want. Pin stands in for Follow: an old save's followed trail becomes the pin.
 * Opening, ticking and finishing (with its share of a level) are the engine's as before.
 */
final class TrailGoals {
    private TrailGoals() {}

    static final int PRIORITY = 30;

    static void register() {
        Goals.register(TrailGoals::steps);
    }

    static List<Step> steps(ServerPlayer p) {
        TrailState st = TrailState.of(p);
        List<Step> out = new ArrayList<>();
        for (String id : st.opened) {
            if (st.finished.contains(id) || st.hidden.contains(id)) continue;
            TrailDef def = Trails.get(id);
            if (def == null) continue;
            TrailDef.Step cur = TrailEngine.current(p, st, id, def);
            if (cur == null) continue;
            int done = 0;
            for (TrailDef.Step s : def.steps()) if (TrailEngine.done(st, id, s)) done++;
            String why = def.title() + ", " + done + " of " + def.steps().size() + " done." + (cur.hint().isEmpty() ? "" : " " + cur.hint());
            Goals.Builder b = Step.of(key(id), goal(def.area()), cur.text()).why(why).priority(PRIORITY).optional()
                    .covers("trail:" + id + ":");
            BlockPos at = cur.where().isEmpty() ? null : TrailsApi.placeOf(cur.where(), p);
            if (at != null) b.at(at);
            out.add(b.build());
        }
        return out;
    }

    static String key(String trail) {
        return "trail/" + trail;
    }

    static Goal goal(String area) {
        return switch (area) {
            case "home" -> Goal.HEARTH;
            case "weapons" -> Goal.WEAPONS;
            case "gates", "powers", "body" -> Goal.RANK;
            default -> Goal.OUT;
        };
    }

    /** An old save's followed trail becomes the pin (unless something is pinned already), and Follow is gone. */
    static void followedToPin(ServerPlayer p) {
        TrailState st = TrailState.of(p);
        if (st.followed.isEmpty()) return;
        if (!st.finished.contains(st.followed)) Goals.pinIfFree(p, key(st.followed));
        st.followed = "";
        st.save(p);
    }
}
