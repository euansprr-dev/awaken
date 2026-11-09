package dev.awaken.riding;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.core.api.RidingHooks;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Riding Along on the Tasks page (SPEC §55): shown only while someone else is near enough to do it with, until you've done
 * it once. It's optional: nobody has to ride along with anyone.
 */
final class RidingGoals {
    private RidingGoals() {}

    static final String KEY = "riding/together";

    static void register() {
        Goals.register(RidingGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        Profile profile = Progression.profile(p);
        if (!profile.awakened() || Bonds.bonded(p) || profile.counter(Bonds.RODE) > 0 || !someoneNear(p)) return List.of();
        return List.of(Goals.Step.of(KEY, Goal.RANK, "Go through a gate with someone")
                .why("One of you can ride along with the other. The rider can't go down while they stay close, but their hits count for 40% and they earn nothing towards their rank.")
                .need(Need.of("Press Ride or Bring on the K menu's Status page, or right-click a gate's door", false))
                .optional()
                .build());
    }

    private static boolean someoneNear(ServerPlayer p) {
        double range = RidingHooks.RANGE * RidingHooks.RANGE;
        for (ServerPlayer o : p.level().players()) if (o != p && !o.isSpectator() && o.distanceToSqr(p) <= range) return true;
        return false;
    }
}
