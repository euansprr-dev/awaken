package dev.awaken.living.combat;

import dev.awaken.living.LivingModule;
import dev.awaken.living.LivingNet;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.server.level.ServerPlayer;

/**
 * The level moment (SPEC §21.1): the player's client plays it (waiting until a fight ends); players within 48 blocks see
 * the rings and the column. In a fight progression's toast still goes, so the news is never late.
 */
public final class LevelMoments {
    private LevelMoments() {}

    static final double SEEN_RANGE = 48;

    /** {@code LivingHooks.levelMoment}: true when the moment replaces progression's toast. */
    public static boolean offer(ServerPlayer p, int level, int gained) {
        if (!LivingModule.shown(p.level().getServer())) return false;
        Profile prof = Progression.profile(p);
        Rank rank = prof.rank();
        Rank next = rank.ordinal() + 1 < Rank.values().length ? Rank.values()[rank.ordinal() + 1] : null;
        String line = next == null ? rank.label + "-RANK"
                : rank.label + "-RANK · " + (prof.level() >= next.levelRequired ? "REASSESSMENT READY"
                : (next.levelRequired - prof.level()) + " LEVELS TO " + next.label);
        var msg = new LivingNet.LevelUp(p.getId(), level, gained * Curve.POINTS_PER_LEVEL, line);
        dev.awaken.core.AwakenNet.send(p, msg);
        var others = new LivingNet.LevelUp(p.getId(), level, 0, "");
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o != p && o.level() == p.level() && o.distanceToSqr(p) < SEEN_RANGE * SEEN_RANGE) dev.awaken.core.AwakenNet.send(o, others);
        }
        // in a fight the toast still lands now; the moment itself waits on the client for the fight to end
        return !Hits.inFight(p);
    }
}
