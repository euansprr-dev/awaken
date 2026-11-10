package dev.awaken.finds;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The finds on the Tasks page (SPEC §58.7): the leads a player holds, newest two, under "Out there". A lead is a way
 * to walk and nothing more: no place, no distance, no name for what's at the end of it. Finds themselves are never
 * listed; they are for coming across.
 */
final class FindsGoals {
    private FindsGoals() {}

    static void register() {
        Goals.register(FindsGoals::steps);
    }

    static List<Step> steps(ServerPlayer p) {
        List<String> leads = p.getData(FindsContent.FINDS).leads();
        List<Step> out = new ArrayList<>(2);
        for (int i = leads.size() - 1; i >= 0 && out.size() < 2; i--) {
            String[] f = leads.get(i).split("\\|");
            if (f.length < 5) continue;
            out.add(Step.of("finds/lead/" + f[4], Goal.OUT, "Something lies " + f[3] + " of " + f[0])
                    .why("You learned it at " + f[0] + " (" + f[1] + ", " + f[2] + "). It's further out than that, and it isn't marked.")
                    .priority(20).optional().lead().build());
        }
        return out;
    }
}
